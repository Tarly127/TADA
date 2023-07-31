package core;

import Interface.communication.communicationHandler.CommunicationManager;
import Interface.communication.groupConstitution.Subscription;
import Interface.consensus.async.AsynchronousPrimitive;
import Interface.consensus.synch.AtomicApproximateValue;
import Interface.consensus.synch.SynchronousPrimitive;
import utils.communication.address.Address;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.groupConstitution.Process;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.communication.serializer.MessageSerializer;
import utils.consensus.ids.InstanceID;
import utils.consensus.synchConsensusUtils.BSOInstance;
import utils.prof.ConsensusMetrics;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import utils.math.atomicExtensions.AtomicDouble;
import utils.consensus.snapshot.ConsensusState;
import utils.consensus.ids.RequestID;
import utils.consensus.exception.MinimumProcessesNotReachedException;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public final class AtomicMixedModeApproximateDouble
        implements AtomicApproximateValue<Double>, SynchronousPrimitive
{
    // Class constants
    public  static final int MINIMUM_PROCESSES          = 4;
    private static final int STARTING_N_THREADS         = 1;
    private static final Set<Byte> WANTED_MESSAGE_TYPES = new TreeSet<>(Arrays.asList(
            MessageType.SYNCH_INITIALIZATION,
            MessageType.SYNCH_APPROXIMATION,
            MessageType.SYNCH_HALTED
    ));

    // Communication
    private Address myAddress;
    private final GroupConstitution groupCon;
    private final CommunicationManager msgManager;
    private final MessageSerializer<ApproximationMessage> approximationMessageSerializer;
    private final LinkedList<Pair<ProcessAttachment, ApproximationMessage>> unhandledMessages;
    private final Subscription wantedTypesSubscription;
    private final InstanceID instanceID;

    // Requests
    private final AtomicInteger internalRequestID;
    private final Set<RequestID> outdatedRequests;
    private final Map<RequestID, SynchConsensusInstance<Double>>  activeRequests;

    // Consensus
    private final AtomicDouble consensusV;
    private AtomicDouble myV;
    private final Integer n;
    private final Integer b;
    private final Integer s;
    private final Integer o;
    private Double epsilon;

    // Concurrency control
    private final ReentrantLock vLock;
    private final ReentrantLock requestsLock;
    private final ReentrantLock globalLock;
    private final Condition voteReady;
    private final ExecutorService es;

    // Synchronous Characteristics
    private long timeout;
    private TimeUnit unit;
    private double defaultValue = 0.0;

    // Testing
    private final Map<RequestID, ConsensusMetrics> metrics;



    // AUXILIARY PRIVATE CLASSES

    private enum Action
    {
        IGNORE,
        HANDLE_NOW,
        HANDLE_LATER,
        BYZANTINE
    }

    private final static class ProcessAttachment
    {
        Address    procAddress;
        Process process;
        ByteBuffer buffer;
    }

    // CONSTRUCTORS

    /**
     * Create a new, fully connected instance of an object containing a double updated atomically and with
     * synchronous approximate consensus algorithms
     * @param groupCon Constitution of a fully connected group of communicating processes
     * @param epsilon Initial precision of approximation
     * @param b Maximum number of byzantine faults
     * @param s Maximum number of symmetric faults
     * @param o Maximum number of omissive faults
     * @param initialVote Initial vote for stored value
     * @param timeout Timeout value
     * @param unit Unit of timeout value
     */
    AtomicMixedModeApproximateDouble(CommunicationManager groupCon,
                            String name,
                            double epsilon,
                            int b,
                            int s,
                            int o,
                            double initialVote,
                            long timeout,
                            TimeUnit unit)
    {
        // Communication
        this.groupCon                       = groupCon.getGroupConstitution();
        this.approximationMessageSerializer = new MessageSerializer<>(ApproximationMessage.class);
        this.unhandledMessages              = new LinkedList<>();
        this.msgManager                     = groupCon;
        this.instanceID                     = new InstanceID(name);
        this.wantedTypesSubscription = this.msgManager.getRegistration(WANTED_MESSAGE_TYPES, instanceID);

        groupCon.getGroupConstitution().forEach((address, process)->
        {
            if(!process.isOther()) this.myAddress = address;
        });

        // Consensus
        this.n          = groupCon.getGroupConstitution().size();
        this.b = b;
        this.s = s;
        this.o = o;
        this.epsilon    = epsilon;
        this.myV        = new AtomicDouble(initialVote);
        this.consensusV = new AtomicDouble();

        // Requests
        this.internalRequestID   = new AtomicInteger();
        this.activeRequests      = new HashMap<>(this.n);
        this.outdatedRequests    = new HashSet<>();

        // Concurrency Control
        this.requestsLock          = new ReentrantLock(true);
        this.vLock                 = new ReentrantLock(true);
        this.globalLock            = new ReentrantLock(true);
        this.voteReady             = this.vLock.newCondition();
        this.es                    = Executors.newFixedThreadPool(STARTING_N_THREADS, Executors.defaultThreadFactory());

        // Synchronous characteristics
        this.timeout = timeout;
        this.unit    = unit;

        // testing
        this.metrics = Collections.synchronizedMap(new HashMap<>());

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }

    /**
     * Create a new, fully connected instance of an object containing a double updated atomically and with
     * approximate consensus algorithms
     * @param groupCon Constitution of a fully connected group of communicating processes
     * @param epsilon Initial precision of approximation
     * @param b Maximum number of byzantine faults
     * @param s Maximum number of symmetric faults
     * @param o Maximum number of omissive faults
     * @param timeout Timeout value
     * @param unit Unit of timeout value
     */
    AtomicMixedModeApproximateDouble(CommunicationManager groupCon,
                                   String name,
                                   double epsilon,
                                   int b,
                                   int s,
                                   int o,
                                   long timeout,
                                   TimeUnit unit)
    {
        // Communication
        this.groupCon                       = groupCon.getGroupConstitution();
        this.approximationMessageSerializer = new MessageSerializer<>(ApproximationMessage.class);
        this.unhandledMessages              = new LinkedList<>();
        this.msgManager                     = groupCon;
        this.instanceID                     = new InstanceID(name);
        this.wantedTypesSubscription = this.msgManager.getRegistration(WANTED_MESSAGE_TYPES, this.instanceID);

        groupCon.getGroupConstitution().forEach((address, process)->
        {
            if(!process.isOther()) this.myAddress = address;
        });

        // Consensus
        this.n          = groupCon.getGroupConstitution().size();
        this.b = b;
        this.s = s;
        this.o = o;
        this.epsilon    = epsilon;
        this.myV        = null;
        this.consensusV = new AtomicDouble();

        // Requests
        this.internalRequestID   = new AtomicInteger();
        this.activeRequests      = new HashMap<>(this.n);
        this.outdatedRequests    = new HashSet<>();

        // Concurrency Control
        this.requestsLock          = new ReentrantLock(true);
        this.vLock                 = new ReentrantLock(true);
        this.globalLock            = new ReentrantLock(true);
        this.voteReady             = this.vLock.newCondition();
        this.es                    = Executors.newFixedThreadPool(STARTING_N_THREADS, Executors.defaultThreadFactory());

        // Synchronous characteristics
        this.timeout = timeout;
        this.unit    = unit;

        // testing
        this.metrics = Collections.synchronizedMap(new HashMap<>());

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }


    // MAIN PUBLIC METHODS (GETTERS, SETTERS, ETC.)

    @Override
    public Double get()
            throws MinimumProcessesNotReachedException, ExecutionException, InterruptedException
    {
        // if the minimum number of processes has not been reached, throw an exception
        if (this.n < MINIMUM_PROCESSES)
            throw new MinimumProcessesNotReachedException();

        this.vLock.lock();
        // if no vote has been cast yet, wait for vote to be cast (instead of throwing exception)
        try
        {
            if(this.myV == null)
                this.voteReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.vLock.unlock();
        }

        // if all checks done, commence consensus and return future containing the result of the operation
        return requestConsensus().get();
    }

    @Override
    public void   set(Double newV)
            throws MinimumProcessesNotReachedException, InterruptedException, ExecutionException
    {
        // if the minimum number of processes has not been reached, throw an exception
        if(this.n < MINIMUM_PROCESSES)
            throw new MinimumProcessesNotReachedException();

        // if a vote has not been cast yet, we cast the first vote and warn any and all threads waiting to read first
        // vote with signal all
        this.vLock.lock();

        if(this.myV == null)
        {
            this.myV = new AtomicDouble(newV);
            this.voteReady.signalAll();
        }
        // else, we just set the new vote
        else
            this.myV.setDouble(newV);

        this.vLock.unlock();

        // perform consensus and return when finished (with future)
        requestConsensus().thenApply(this.consensusV::setDouble).get();
    }

    @Override
    public void   lazySet(Double newV)
    {
        this.vLock.lock();

        if(this.myV == null)
        {
            this.myV = new AtomicDouble(newV);
            this.voteReady.signalAll();
        }
        else
            this.myV.setDouble(newV);

        this.vLock.unlock();
    }

    @Override
    public Double lazyGet()
    {
        Double v = null;

        this.vLock.lock();

        try
        {
            if(this.myV == null)

                this.voteReady.await();

            v = this.myV.getDouble();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.vLock.unlock();
        }

        return v;
    }

    @Override
    public boolean compareAndSet(Double expectedValue, Double newValue)
            throws MinimumProcessesNotReachedException, ExecutionException, InterruptedException
    {
        // if the minimum number of processes has not been reached, throw an exception
        if(this.n < MINIMUM_PROCESSES)
            throw new MinimumProcessesNotReachedException();

        // if a vote has not been cast yet, we cast the first vote and warn any and all threads waiting to read first
        // vote with signal all
        this.vLock.lock();

        if(this.myV == null)
        {
            this.myV = new AtomicDouble(newValue);
            this.voteReady.signalAll();

            this.vLock.unlock();

            // perform consensus and return when finished (with future)
            return requestConsensus().thenApply(this.consensusV::setDouble).thenApply(v -> false).get();
        }
        // else, we just set the new vote
        else
        {
            if(this.myV.getDouble().equals(expectedValue))
            {
                this.myV.setDouble(newValue);

                this.vLock.unlock();

                // perform consensus and return when finished (with future)
                return requestConsensus().thenApply(this.consensusV::setDouble).thenApply(v -> true).get();
            }
            else
            {
                return false;
            }
        }
    }

    @Override
    public Double getAndSet(Double newValue)
            throws MinimumProcessesNotReachedException, InterruptedException, ExecutionException
    {
        Double previousValue;

        // if the minimum number of processes has not been reached, throw an exception
        if (this.n < MINIMUM_PROCESSES)
            throw new MinimumProcessesNotReachedException();

        this.vLock.lock();
        // if no vote has been cast yet, wait for vote to be cast (instead of throwing exception)
        try
        {
            if(this.myV == null)
                this.voteReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            previousValue = (this.myV == null) ? null : this.myV.getDouble();

            this.vLock.unlock();
        }

        // if all checks done, commence consensus and return future containing the result of the operation
        return requestConsensus().thenApply(v -> {
            this.consensusV.setDouble(v);
            return previousValue;
        }).get();
    }

    @Override
    public Double lazyGetAndSet(Double newValue)
    {
        Double previousValue = null;

        this.vLock.lock();

        if (this.myV == null)
        {
            this.myV = new AtomicDouble(newValue);
            this.voteReady.signalAll();
        }
        else
        {
            previousValue = this.myV.getDouble();
            this.myV.setDouble(newValue);
        }

        this.vLock.unlock();

        return previousValue;
    }

    public double getPrecision ()
    {
        this.globalLock.lock();
        double precision = this.epsilon;
        this.globalLock.unlock();
        return precision;
    }

    public void   setPrecision (double newEpsilon)
    {
        this.globalLock.lock();
        this.epsilon = newEpsilon;
        this.globalLock.unlock();
    }

    @Override
    public int getCommunicationGroupSize()
    {
        return this.n;
    }

    @Override
    public int getMaxFaults()
    {
        return this.b + this.s + this.o;
    }

    //
    @Override
    public AsynchronousPrimitive async()
    {
        return null;
    }

    @Override
    public void setTimeout(long timeout, TimeUnit unit)
    {
        this.globalLock.lock();
        this.timeout = timeout;
        this.unit    = unit;
        this.globalLock.lock();
    }

    @Override
    public Pair<Long, TimeUnit> getTimeout()
    {
        this.globalLock.lock();
        var timeoutPacket = new Pair<>(this.timeout, this.unit);
        this.globalLock.unlock();

        return timeoutPacket;
    }

    @Override
    public void setDefaultValue(Double defaultValue)
    {
        this.globalLock.lock();
        this.defaultValue = defaultValue;
        this.globalLock.unlock();
    }

    // Important consensus methods

    /**
     * Deal with approximate consensus transaction started by us
     * @return Future containing v at completion
     */
    private CompletableFuture<Double> requestConsensus()
    {
        return approximateConsensusS(startNewConsensus());
    }

    /**
     * Deal with approximate consensus transaction NOT started by this process
     * @param reqID ID of the consensus transaction
     * @return Future containing v at completion
     */
    private CompletableFuture<Double> approximateConsensusO(ApproximationMessage msg, RequestID reqID)
    {
        return Objects
                .requireNonNull(this.activeRequests.get(reqID))
                .approximateConsensus_other(msg)
                .thenApply(v->{cleanUp(reqID); return v;})
                .thenApply(consensusV::setDouble);
    }

    /**
     * Deal with approximate consensus transaction started by this process
     * @param reqID ID of the consensus transaction
     * @return Future containing v at completion
     */
    private CompletableFuture<Double> approximateConsensusS(RequestID reqID)
    {
        return Objects
                .requireNonNull(this.activeRequests.get(reqID))
                .approximateConsensus_self()
                .thenApply(v->{
                    this.metrics.put(reqID, this.activeRequests.get(reqID).getMetrics());
                    cleanUp(reqID);
                    return v;
                })
                .thenApply(consensusV::setDouble);
    }

    private Triplet<Long, TimeUnit, Double> getTimeoutAndDefaultValues()
    {
        this.globalLock.lock();
        var triplet = new Triplet<Long, TimeUnit, Double>(this.timeout, this.unit, this.defaultValue);
        this.globalLock.unlock();

        return triplet;
    }

    /**
     * Starts new consensus request
     * @return Future containing RequestID for new consensus
     */
    private RequestID startNewConsensus    ()
    {
        // Generate requestID
        RequestID myReqId = generateRequestID();
        // Snapshot global state
        ConsensusState snapshot = snapshotGlobalState(myReqId);
        // Get timeout and default values
        var timeoutAndDefault = getTimeoutAndDefaultValues();
        // Generate new consensus instance
        SynchConsensusInstance<Double> consensus = new BSOInstance(snapshot,
                                                                    timeoutAndDefault.getValue0(),
                                                                    timeoutAndDefault.getValue1(),
                                                                    timeoutAndDefault.getValue2(),
                                                                    this.b,
                                                                    this.s,
                                                                    this.o);
        // Store new snapshot
        this.requestsLock.lock();
        this.activeRequests  .put(myReqId, consensus);
        this.requestsLock.unlock();
        // Broadcast request intent
        return myReqId;
    }


    // Message handlers

    private void supplyNextMessage()
    {
        // Get next packet that interests us
        Triplet<Address, byte[], Byte> nextPacket = this.msgManager.dequeue(this.wantedTypesSubscription);

        // handle the next packet, recursively, possibly in a new thread, so we don't create a function stack overflow
        // exception. Call this first, so that, in case something breaks while handling a message, we don't stop
        // handling messages in the queue
        this.es.submit(this::supplyNextMessage);

        // handle the new packet concurrently
        // check if we registered the address we received
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

                        request.synchExchange(nextPacket.getValue2(), msg,
                                processAttachment.procAddress);
                    }
                    else
                    {
                        handleNew(msg, processAttachment);

                        this.requestsLock.unlock();
                    }
                }
                case HANDLE_LATER ->
                {
                    storeMessage(msg, processAttachment);
                    requestsLock.unlock();
                }
                case BYZANTINE ->
                {
                    processAttachment.process.markAsFaulty();
                    requestsLock.unlock();
                }
                case IGNORE ->
                {
                    requestsLock.unlock();
                }
            }
        }
        else
            groupCon.unlock();
    }

    private void handleNew            (ApproximationMessage msg, ProcessAttachment attachment)
    {
        this.vLock.lock();
        // wait for a value to be placed in startingV, if there is none
        try
        {
            if(this.myV == null)
                this.voteReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.vLock.unlock();
        }

        // register the new ID
        Address addr = new Address(msg.reqID.requesterPort);

        this.requestsLock.lock();

        // get the timeout and default values
        var timeoutAndDefault = getTimeoutAndDefaultValues();

        // make the new consensus instance
        var consensusInstance = new BSOInstance(snapshotGlobalState(msg.reqID),
                timeoutAndDefault.getValue0(),
                timeoutAndDefault.getValue1(),
                timeoutAndDefault.getValue2(),
                this.b,
                this.s,
                this.o);

        this.activeRequests  .put(msg.reqID, consensusInstance);

        // deal out stored messages relative to this request
        handleStoredMessages(consensusInstance);

        this.requestsLock.unlock();

        // start consensus
        approximateConsensusO(msg, msg.reqID).thenAccept(this.consensusV::setDouble);
    }



    // AUXILIARY FUNCTIONS

    private RequestID generateRequestID()
    {
        return new RequestID(this.myAddress, this.internalRequestID.getAndIncrement());
    }

    private ConsensusState snapshotGlobalState(RequestID requestID)
    {
        // lock global state so we can have a consistent snapshot
        this.globalLock.lock();
        // generate new ConsensusParameters snapshot with current global state
        ConsensusState snapshot = new ConsensusState(
                requestID,
                this.instanceID,
                this.n,
                -1,
                this.epsilon,
                this.myV.getDouble(),
                this.groupCon);
        // unlock global state
        this.globalLock.unlock();

        return snapshot;
    }

    private Action  nextAction(ApproximationMessage msg)
    {
        Action actionToTake;

        if (msg.type == MessageType.SYNCH_INITIALIZATION)
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

    private void storeMessage(ApproximationMessage msg, ProcessAttachment attachment)
    {
        this.unhandledMessages.add(new Pair<>(attachment, msg));
    }

    private void handleStoredMessages(SynchConsensusInstance<Double> consensusInstance)
    {
        if(consensusInstance != null)
        {
            // handle each unhandled message  with requestID
            this.unhandledMessages
                    .stream()
                    .filter(p -> p.getValue1().reqID.equals(consensusInstance.getReqID()))
                    .forEach(m -> consensusInstance.synchExchange(m.getValue1().getType(),
                                                                   m.getValue1(),
                                                                   m.getValue0().procAddress));
            // Remove after handling
            this.unhandledMessages.removeAll(this.unhandledMessages
                    .stream()
                    .filter(p -> p.getValue1().reqID.equals(consensusInstance.getReqID()))
                    .collect(Collectors.toSet()));
        }
    }

    private void cleanUp(RequestID id)
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
        }

        this.requestsLock.unlock();
    }

    public List<ConsensusMetrics> getMetrics()
    {
        Comparator<Map.Entry<RequestID, ConsensusMetrics>> comparator = (entry1, entry2)->{
            return entry1.getKey().internalID - entry2.getKey().internalID;
        };

        return this.metrics.entrySet().stream().sorted(comparator).map(Map.Entry::getValue).collect(Collectors.toList());
    }



    // Pretty printers for debugging purposes

    private void showAction(Action action)
    {
        switch (action)
        {
            case HANDLE_LATER: {System.out.print("Later"); break;}
            case HANDLE_NOW:   {System.out.print("Now"); break;}
            case BYZANTINE:    {System.out.print("Byzantine"); break;}
            case IGNORE:      {System.out.print("Ignore");}
        }
    }

    private void showType(ApproximationMessage msg)
    {
        switch (msg.type)
        {
            case MessageType.SYNCH_APPROXIMATION:{System.out.print("approx"); break;}
            case MessageType.SYNCH_HALTED: {System.out.print("halted");break;}
            case MessageType.SYNCH_INITIALIZATION: {System.out.print("init"); break;}
            case MessageType.UNDEFINED: {System.out.print("undef");}
        }
    }

    private void showAll(ApproximationMessage msg, ProcessAttachment attachment, Action action)
    {
        System.out.print(attachment.procAddress.getPort() + " | " + msg.round + " | ");
        showType(msg);
        System.out.print(" | ");
        showAction(action);
        System.out.println(" | " + msg.reqID.internalID);
    }
}