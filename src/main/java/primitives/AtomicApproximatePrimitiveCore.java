package primitives;

import AtomicInterface.communication.address.AddressInterface;
import AtomicInterface.communication.communicationHandler.Broadcast;
import AtomicInterface.communication.communicationHandler.CommunicationManager;
import AtomicInterface.communication.groupConstitution.ProcessInterface;
import AtomicInterface.communication.groupConstitution.Subscription;
import AtomicInterface.consensus.ConsensusInstance;
import org.javatuples.Pair;
import utils.communication.communicationHandler.Broadcast.AsynchBroadcast;
import utils.communication.communicationHandler.Broadcast.byzantineBroadcast.*;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.message.ApproximationMessage;
import utils.communication.serializer.MessageSerializer;
import utils.consensus.ids.InstanceID;
import utils.consensus.ids.RequestID;
import utils.consensus.types.faultDescriptors.FaultClass;
import utils.math.atomicExtensions.AtomicDouble;
import utils.measurements.ConsensusMetrics;
import utils.measurements.MessageLogger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AtomicApproximatePrimitiveCore
{
    private static final int STARTING_N_THREADS = 10;

    protected final static class ProcessAttachment
    {
        AddressInterface procAddress;
        ProcessInterface process;
        ByteBuffer buffer;
    }

    protected enum Action
    {
        IGNORE,
        HANDLE_NOW,
        HANDLE_LATER,
        BYZANTINE
    }

    // Communication
    protected      AddressInterface myAddress;
    protected final GroupConstitution groupCon;
    protected final CommunicationManager msgManager;
    protected final MessageSerializer<ApproximationMessage> approximationMessageSerializer;
    protected final LinkedList<Pair<ProcessAttachment, ApproximationMessage>> unhandledMessages;
    protected final Subscription wantedTypesSubscription;
    protected final InstanceID instanceID;
    protected final Broadcast consensusBroadcast;

    // Requests
    protected final AtomicInteger internalRequestID;
    protected final Set<RequestID> outdatedRequests;
    protected final AtomicInteger finishedRequestsCount;
    protected final Map<RequestID, ConsensusInstance<Double>>  activeRequests;

    // Consensus
    protected final AtomicDouble consensusV;
    protected      AtomicDouble myV;
    protected final Integer n;
    protected final Integer t;
    protected      Double epsilon;
    protected final AtomicBoolean consensusPossible;

    // Concurrency control
    protected final ReentrantLock vLock;
    protected final ReentrantLock requestsLock;
    protected final ReentrantLock unhandledMessagesLock;
    protected final ReentrantLock globalLock;
    protected final Condition voteReady;
    protected final ExecutorService es;

    // testing
    protected final Map<RequestID, ConsensusMetrics> metrics;
    protected final MessageLogger messageLogger;

    public AtomicApproximatePrimitiveCore(CommunicationManager groupCon,
                                          String name,
                                          Double epsilon,
                                          Double initialVote,
                                          final Set<Byte> wantedMessageTypes,
                                          final int MINIMUM_PROCS)
    {
        // Communication
        this.groupCon                       = groupCon.getGroupConstitution();
        this.approximationMessageSerializer = new MessageSerializer<>(ApproximationMessage.class);
        this.unhandledMessages              = new LinkedList<>();
        this.msgManager                     = groupCon;
        this.instanceID                     = new InstanceID(name);
        this.wantedTypesSubscription         = this.msgManager.getRegistration(wantedMessageTypes, instanceID);

        groupCon.getGroupConstitution().forEach((address, process)->
        {
            if(!process.isOther()) this.myAddress = address;
        });

        // Consensus
        this.n                  = groupCon.getGroupConstitution().size();
        this.t                  = (this.n - 1) / MINIMUM_PROCS;
        this.epsilon            = epsilon;
        this.myV                = new AtomicDouble(initialVote);
        this.consensusV         = new AtomicDouble();
        this.consensusBroadcast = new AsynchBroadcast();
        this.consensusPossible = new AtomicBoolean(
                groupCon.getGroupConstitution().values().stream().filter(ProcessInterface::isFaulty).count() <= this.t
        );

        // Requests
        this.internalRequestID    = new AtomicInteger();
        this.outdatedRequests     = new HashSet<>();
        this.finishedRequestsCount = new AtomicInteger(0);
        this.activeRequests       = new HashMap<>(this.n);

        // Concurrency Control
        this.requestsLock          = new ReentrantLock(true);
        this.vLock                 = new ReentrantLock(true);
        this.globalLock            = new ReentrantLock(true);
        this.unhandledMessagesLock = new ReentrantLock(true);
        this.voteReady             = this.vLock.newCondition();
        this.es                    = Executors.newFixedThreadPool(STARTING_N_THREADS, Executors.defaultThreadFactory());

        // testing
        this.metrics       = Collections.synchronizedMap(new HashMap<>());
        this.messageLogger = new MessageLogger();
    }

    public AtomicApproximatePrimitiveCore(CommunicationManager groupCon,
                                          String name,
                                          Double epsilon,
                                          Double initialVote,
                                          FaultClass faultClass,
                                          final Set<Byte> wantedMessageTypes,
                                          final int MINIMUM_PROCS)
    {
        // Communication
        this.groupCon                       = groupCon.getGroupConstitution();
        this.approximationMessageSerializer = new MessageSerializer<>(ApproximationMessage.class);
        this.unhandledMessages              = new LinkedList<>();
        this.msgManager                     = groupCon;
        this.instanceID                     = new InstanceID(name);
        this.wantedTypesSubscription = this.msgManager.getRegistration(wantedMessageTypes, instanceID);


        groupCon.getGroupConstitution().forEach((address, process)->
        {
            if(!process.isOther()) this.myAddress = address;
        });

        // Consensus
        this.n                  = groupCon.getGroupConstitution().size();
        this.t                  = (this.n - 1) / MINIMUM_PROCS;
        this.epsilon            = epsilon;
        this.myV                = new AtomicDouble(initialVote);
        this.consensusV         = new AtomicDouble();
        this.consensusBroadcast = getFaultyBroadcast(faultClass);
        this.consensusPossible = new AtomicBoolean(
                groupCon.getGroupConstitution().values().stream().filter(ProcessInterface::isFaulty).count() <= this.t
        );

        // Requests
        this.internalRequestID    = new AtomicInteger();
        this.outdatedRequests     = new HashSet<>();
        this.finishedRequestsCount = new AtomicInteger(0);
        this.activeRequests       = new HashMap<>(this.n);

        // Concurrency Control
        this.requestsLock          = new ReentrantLock(true);
        this.vLock                 = new ReentrantLock(true);
        this.globalLock            = new ReentrantLock(true);
        this.unhandledMessagesLock = new ReentrantLock(true);
        this.voteReady             = this.vLock.newCondition();
        this.es                    = Executors.newFixedThreadPool(STARTING_N_THREADS, Executors.defaultThreadFactory());

        // testing
        this.metrics       = Collections.synchronizedMap(new HashMap<>());
        this.messageLogger = new MessageLogger();
    }

    public AtomicApproximatePrimitiveCore(CommunicationManager groupCon,
                                          String name,
                                          Double epsilon,
                                          final Set<Byte> wantedMessageTypes,
                                          final int MINIMUM_PROCS)
    {
        // Communication
        this.groupCon                       = groupCon.getGroupConstitution();
        this.approximationMessageSerializer = new MessageSerializer<>(ApproximationMessage.class);
        this.unhandledMessages              = new LinkedList<>();
        this.msgManager                     = groupCon;
        this.instanceID                     = new InstanceID(name);
        this.wantedTypesSubscription = this.msgManager.getRegistration(wantedMessageTypes, this.instanceID);

        groupCon.getGroupConstitution().forEach((address, process)->
        {
            if(!process.isOther()) this.myAddress = address;
        });

        // Consensus
        this.n                  = groupCon.getGroupConstitution().size();
        this.t                  = (this.n - 1) / MINIMUM_PROCS;
        this.epsilon            = epsilon;
        this.myV                = null;
        this.consensusV         = new AtomicDouble();
        this.consensusBroadcast = new AsynchBroadcast();
        this.consensusPossible = new AtomicBoolean(
                groupCon.getGroupConstitution().values().stream().filter(ProcessInterface::isFaulty).count() <= this.t
        );

        // Requests
        this.internalRequestID   = new AtomicInteger();
        this.activeRequests      = new HashMap<>(this.n);
        this.outdatedRequests    = new HashSet<>();
        this.finishedRequestsCount = new AtomicInteger(0);

        // Concurrency Control
        this.requestsLock          = new ReentrantLock(true);
        this.vLock                 = new ReentrantLock(true);
        this.globalLock            = new ReentrantLock(true);
        this.unhandledMessagesLock = new ReentrantLock(true);
        this.voteReady             = this.vLock.newCondition();
        this.es                    = Executors.newFixedThreadPool(STARTING_N_THREADS, Executors.defaultThreadFactory());

        // testing
        this.metrics       = Collections.synchronizedMap(new HashMap<>());
        this.messageLogger = new MessageLogger();
    }

    // Ao fazer isto, temos basicamente 2 primitivas que encapsulam a mesma variável
    public <T extends AtomicApproximatePrimitiveCore> AtomicApproximatePrimitiveCore(T that)
    {
        // communication
        this.myAddress  = that.myAddress;
        this.groupCon   = that.groupCon;
        this.msgManager = that.msgManager;
        this.approximationMessageSerializer = that.approximationMessageSerializer;
        // TODO: rethink this?
        // *******************
        this.unhandledMessages       = new LinkedList<>();
        this.wantedTypesSubscription = that.wantedTypesSubscription;
        this.instanceID              = that.instanceID;
        // *******************
        this.consensusBroadcast      = that.consensusBroadcast;

        // requests
        this.internalRequestID    = that.internalRequestID;
        this.outdatedRequests     = that.outdatedRequests;
        this.finishedRequestsCount = new AtomicInteger(0); // we can keep trakc of this separately
        this.activeRequests       = that.activeRequests;

        // consensus
        this.consensusV        = that.consensusV;
        this.myV               = that.myV;
        this.n                 = that.n;
        this.t                 = that.t;
        this.epsilon           = that.epsilon;
        this.consensusPossible = that.consensusPossible;

        // concurrency control
        this.vLock                 = that.vLock;
        this.requestsLock          = that.requestsLock;
        this.unhandledMessagesLock = that.unhandledMessagesLock;
        this.globalLock            = that.globalLock;
        this.voteReady             = that.voteReady;
        this.es                    = that.es;

        // testing
        this.metrics       = Collections.synchronizedMap(new HashMap<>());
        this.messageLogger = new MessageLogger();
    }

    public abstract int getMaxFaults();

    private static Broadcast getFaultyBroadcast(FaultClass faultType)
    {
        return switch (faultType)
                {
                    case OMISSIVE_ASYMMETRIC -> new OmissiveAsymmetricBroadcast();
                    case OMISSIVE_SYMMETRIC  -> new OmissiveSymmetricBroadcast();
                    case SEMANTIC_ASYMMETRIC -> new SemanticAsymmetricBroadcast();
                    case SEMANTIC_SYMMETRIC  -> new SemanticSymmetricBroadcast();
                    case FULLY_BYZANTINE     -> new FullyByzantineBroadcast();
                };
    }


}
