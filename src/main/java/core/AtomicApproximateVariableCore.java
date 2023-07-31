package core;

import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.Broadcast;
import Interface.communication.communicationHandler.CommunicationManager;
import Interface.communication.groupConstitution.OtherNodeInterface;
import Interface.communication.communicationHandler.Subscription;
import Interface.consensus.utils.ConsensusInstance;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import utils.communication.communicationHandler.Broadcast.AsyncBroadcast;
import utils.communication.communicationHandler.Broadcast.faulty.*;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.message.ApproximationMessage;
import utils.communication.serializer.MessageSerializer;
import utils.consensus.ids.InstanceID;
import utils.consensus.ids.RequestID;
import utils.consensus.types.faultDescriptors.FaultClass;
import utils.math.atomicExtensions.AtomicDouble;
import utils.prof.ConsensusMetrics;
import utils.prof.MessageLogger;
import utils.prof.Stopwatch;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public abstract class AtomicApproximateVariableCore
{

    /* *************** */
    /* CLASS CONSTANTS */
    /* *************** */

    private static final int STARTING_N_THREADS = 10;

    /* ***************** */
    /* AUXILIARY CLASSES */
    /* ***************** */

    protected final static class ProcessAttachment
    {
        AddressInterface procAddress;
        OtherNodeInterface process;
        ByteBuffer buffer;
    }

    protected enum Action
    {
        IGNORE,
        HANDLE_NOW,
        HANDLE_LATER,
        FAULTY
    }

    /* ****************** */
    /* INSTANCE VARIABLES */
    /* ****************** */

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


    /* ************ */
    /* CONSTRUCTORS */
    /* ************ */

    public AtomicApproximateVariableCore(CommunicationManager groupCon,
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
        this.wantedTypesSubscription        = this.msgManager.getRegistration(wantedMessageTypes, instanceID);

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
        this.consensusBroadcast = new AsyncBroadcast();
        this.consensusPossible  = new AtomicBoolean(
                groupCon.getGroupConstitution().values().stream().filter(OtherNodeInterface::isFaulty).count() <= this.t
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

    public AtomicApproximateVariableCore(CommunicationManager groupCon,
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
                groupCon.getGroupConstitution().values().stream().filter(OtherNodeInterface::isFaulty).count() <= this.t
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

     public AtomicApproximateVariableCore(CommunicationManager groupCon,
                                         String name,
                                         Double epsilon,
                                         Double initialVote,
                                         Broadcast broadcast,
                                         final Set<Byte> wantedMessageTypes,
                                         final int MINIMUM_PROCS)
    {
        // Communication
        this.groupCon                       = groupCon.getGroupConstitution();
        this.approximationMessageSerializer = new MessageSerializer<>(ApproximationMessage.class);
        this.unhandledMessages              = new LinkedList<>();
        this.msgManager                     = groupCon;
        this.instanceID                     = new InstanceID(name);
        this.wantedTypesSubscription        = this.msgManager.getRegistration(wantedMessageTypes, instanceID);


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
        this.consensusBroadcast = broadcast;
        this.consensusPossible  = new AtomicBoolean(
                groupCon.getGroupConstitution().values().stream().filter(OtherNodeInterface::isFaulty).count() <= this.t
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

    public AtomicApproximateVariableCore(CommunicationManager groupCon,
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
        this.consensusBroadcast = new AsyncBroadcast();
        this.consensusPossible = new AtomicBoolean(
                groupCon.getGroupConstitution().values().stream().filter(OtherNodeInterface::isFaulty).count() <= this.t
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

    public AtomicApproximateVariableCore(CommunicationManager groupCon,
                                         String name,
                                         Double epsilon,
                                         Broadcast broadcast,
                                         final Set<Byte> wantedMessageTypes,
                                         final int MINIMUM_PROCS)
    {
        // Communication
        this.groupCon                       = groupCon.getGroupConstitution();
        this.approximationMessageSerializer = new MessageSerializer<>(ApproximationMessage.class);
        this.unhandledMessages              = new LinkedList<>();
        this.msgManager                     = groupCon;
        this.instanceID                     = new InstanceID(name);
        this.wantedTypesSubscription        = this.msgManager.getRegistration(wantedMessageTypes, this.instanceID);

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
        this.consensusBroadcast = broadcast;
        this.consensusPossible  = new AtomicBoolean(
                groupCon.getGroupConstitution().values().stream().filter(OtherNodeInterface::isFaulty).count() <= this.t
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
    public <T extends AtomicApproximateVariableCore> AtomicApproximateVariableCore(T that)
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

    /* ************** */
    /* PUBLIC METHODS */
    /* ************** */

    // get the precision of approximate consensus algorithms
    public double getPrecision ()
    {
        this.globalLock.lock();
        double precision = this.epsilon;
        this.globalLock.unlock();
        return precision;
    }

    // set the precision of approximate consensus algorithms
    public void   setPrecision (double newEpsilon)
    {
        this.globalLock.lock();
        this.epsilon = newEpsilon;
        this.globalLock.unlock();
    }

    // get the number of processes in the group
    public int getCommunicationGroupSize()
    {
        return this.n;
    }

    // get the maximum number of faults
    public int getMaxFaults()
    {
        return this.t;
    }

    // Retrieve metrics related to finished instances of consensus
    public List<ConsensusMetrics> getMetrics()
    {
        return this.metrics
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey().internalID))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    // get the message logger of this variable (deprecated)
    public MessageLogger getMessageLogger()
    {
        return this.messageLogger;
    }

    // get the number of finished instances of consensus
    public int getNumFinishedRequests()
    {
        return this.finishedRequestsCount.get();
    }

    /* *********************************** */
    /* GENERIC CONSENSUS-AUXILIARY METHODS */
    /* *********************************** */

    protected CompletableFuture<Double> approximateConsensusO(ApproximationMessage msg, RequestID reqID)
    {
        return Objects
                .requireNonNull(this.activeRequests.get(reqID))
                .start(msg)
                .thenApply(v->
                {
                    // Time measuring stuff - start
                    var metrics = this.activeRequests.get(reqID).getMetrics();

                    metrics.reqID = reqID;
                    metrics.texecNanos = Stopwatch.time();

                    this.metrics.put(reqID, metrics);
                    // Time measuring stuff - end

                    finishedRequestsCount.incrementAndGet();
                    cleanUp(reqID);
                    return v;
                })
                .thenApply(consensusV::setDouble);
    }

    protected CompletableFuture<Double> approximateConsensusS(RequestID reqID)
    {
        return Objects
                .requireNonNull(this.activeRequests.get(reqID))
                .start()
                .thenApply(v->{
                    this.metrics.put(reqID, this.activeRequests.get(reqID).getMetrics());
                    finishedRequestsCount.incrementAndGet();
                    cleanUp(reqID);
                    return v;
                })
                .thenApply(consensusV::setDouble);
    }

    protected RequestID generateRequestID()
    {
        return new RequestID(
                this.myAddress,
                this.internalRequestID.getAndIncrement()
        );
    }

    protected CompletableFuture<Double> requestConsensus()
    {
        return approximateConsensusS(
                startNewConsensus()
        );
    }

    protected void cleanUp(RequestID id)
    {
        this.requestsLock.lock();
        // remove request from active requests
        var request = this.activeRequests.remove(id);

        if(request != null)
        {
            // add it to last active requests
            this.outdatedRequests.add(id);
            // update each process's status, based on the latest execution of the algorithm
            this.groupCon.forEach(((address, process) ->
            {
                if (request.getGroupState().containsKey(address))
                    process.markAsFaulty(process.isFaulty());
            }));
            // check that we can continue with consensus in the future
            if (this.faultyProcessesExceedT())
                this.consensusPossible.set(false);
        }

        this.requestsLock.unlock();
    }

    protected boolean faultyProcessesExceedT()
    {
        return this.groupCon.values().stream().filter(OtherNodeInterface::isFaulty).count() > this.t;
    }

    protected void handleStoredMessages(ConsensusInstance<Double> consensusInstance)
    {
        if(consensusInstance != null)
        {
            // handle each unhandled message  with requestID
            this.unhandledMessages
                    .stream()
                    .filter(p -> p.getValue1().reqID.equals(consensusInstance.getReqID()))
                    .forEach(m ->
                            consensusInstance.exchange(m.getValue1().getType(),
                                    m.getValue1(),
                                    m.getValue0().procAddress));
            // Remove after handling
            this.unhandledMessages.removeAll(this.unhandledMessages
                    .stream()
                    .filter(p -> p.getValue1().reqID.equals(consensusInstance.getReqID()))
                    .collect(Collectors.toSet()));
        }
    }

    protected void storeMessage(ApproximationMessage msg, ProcessAttachment attachment)
    {
        this.unhandledMessages.add(new Pair<>(attachment, msg));
    }

    protected Action nextAction(ApproximationMessage msg)
    {
        Action actionToTake;

        if (msg.type == initializationType())
            if(this.outdatedRequests.contains(msg.reqID))
                actionToTake = Action.IGNORE;
            else
                actionToTake = Action.HANDLE_NOW;
        else
            if (this.activeRequests.containsKey(msg.reqID))
                actionToTake = Action.HANDLE_NOW;
            else
                if(this.outdatedRequests.contains(msg.reqID))
                    actionToTake = Action.IGNORE;
                else
                    actionToTake = Action.HANDLE_LATER;

        return actionToTake;
    }

    protected abstract void      handleNew          (ApproximationMessage msg);
    protected abstract byte      initializationType ();
    protected abstract RequestID startNewConsensus  ();



    protected void supplyNextMessage()
    {
        Triplet<AddressInterface, byte[], Byte> nextPacket = this.msgManager.getNextMessage(this.wantedTypesSubscription);

        this.es.submit(this::supplyNextMessage);

        if (nextPacket != null && this.groupCon.containsKey(nextPacket.getValue0()))
        {
            ProcessAttachment processAttachment = new ProcessAttachment();

            processAttachment.buffer = null;
            processAttachment.process = groupCon.get(nextPacket.getValue0());
            processAttachment.procAddress = nextPacket.getValue0();

            ApproximationMessage msg = approximationMessageSerializer.decode(nextPacket.getValue1());

            this.requestsLock.lock();

            switch (nextAction(msg))
            {
                case HANDLE_NOW ->
                {
                    if (this.activeRequests.containsKey(msg.reqID))
                    {
                        var request = this.activeRequests.get(msg.reqID);

                        this.requestsLock.unlock();

                        request.exchange(nextPacket.getValue2(), msg,
                                processAttachment.procAddress);
                    }
                    else
                    {
                        handleNew(msg);

                        this.requestsLock.unlock();
                    }
                }
                case HANDLE_LATER ->
                {
                    storeMessage(msg, processAttachment);
                    requestsLock.unlock();
                }
                case FAULTY ->
                {
                    processAttachment.process.markAsFaulty();
                    requestsLock.unlock();
                }
                case IGNORE ->
                {
                    // this case only comes up in situations where the message corresponds to an instance of
                    // consensus that already finished
                    // This is the only case where a message may come for a specific consensus instance and is not
                    // dealt with inside the instance class
                    this.metrics.get(msg.reqID).processedMsgs.getAndIncrement();

                    requestsLock.unlock();
                }
            }
        }
    }

    private static Broadcast getFaultyBroadcast(FaultClass faultType)
    {
        return switch (faultType)
                {
                    case OMISSIVE_ASYMMETRIC -> new OmissiveAsymmetricBroadcast();
                    case OMISSIVE_SYMMETRIC  -> new OmissiveSymmetricBroadcast();
                    case SEMANTIC_ASYMMETRIC -> new SemanticAsymmetricBroadcast();
                    case SEMANTIC_SYMMETRIC  -> new SemanticSymmetricBroadcast();
                    case FULLY_BYZANTINE     -> new ByzantineBroadcast();
                };
    }

}
