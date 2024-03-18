package core;

<<<<<<< HEAD
import Interface.communication.communicationHandler.CommunicationManager;
import Interface.consensus.utils.ConsensusInstance;
import Interface.consensus.synch.AtomicApproximateValue;
import Interface.consensus.synch.SynchronousAlgorithm;
import Interface.consensus.synch.SynchronousPrimitive;
=======
import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.CommunicationManager;
import Interface.communication.groupConstitution.OtherNodeInterface;
import Interface.consensus.synch.SynchronousPrimitive;
import Interface.consensus.utils.ConsensusInstance;
import Interface.consensus.async.AsynchronousPrimitive;
import Interface.consensus.synch.AtomicApproximateValue;
import Interface.consensus.synch.SynchronousAlgorithm;
>>>>>>> FixingFinalDissertationVersion
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.consensus.ids.RequestID;
import utils.consensus.snapshot.ConsensusState;
import utils.consensus.synchConsensusUtils.SynchDLPSW86Instance;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.consensus.types.faultDescriptors.FaultClass;
import utils.math.atomicExtensions.AtomicDouble;
<<<<<<< HEAD
import org.javatuples.Pair;
import org.javatuples.Triplet;
=======
import utils.prof.ConsensusMetrics;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import utils.prof.MessageLogger;
import utils.prof.Stopwatch;

>>>>>>> FixingFinalDissertationVersion
import java.util.*;
import java.util.concurrent.*;

public final class AtomicApproximateDouble
<<<<<<< HEAD
        extends AtomicApproximateVariableCore
        implements AtomicApproximateValue<Double>, SynchronousPrimitive, SynchronousAlgorithm<Double>
=======
        extends AtomicApproximatePrimitiveCore
        implements AtomicApproximateValue<Double>, SynchronousAlgorithm<Double>, SynchronousPrimitive
>>>>>>> FixingFinalDissertationVersion
{
    // Class constants
    public  static final int MINIMUM_PROCESSES          = 4;
    private static final Set<Byte> WANTED_MESSAGE_TYPES = new TreeSet<>(Arrays.asList(
            MessageType.SYNCH_INITIALIZATION,
            MessageType.SYNCH_APPROXIMATION,
            MessageType.SYNCH_HALTED
    ));

    // Synchronous Characteristics
    private long timeout;
    private TimeUnit unit;
    private double defaultValue = 0.0;

    // CONSTRUCTORS

    /**
     * Create a new, fully connected instance of an object containing a double updated atomically and with
     * synchronous approximate consensus algorithms
     * @param groupCon Constitution of a fully connected group of communicating processes
     * @param epsilon Initial precision of approximation
     * @param initialVote Initial vote for stored value
     * @param timeout Timeout value
     * @param unit Unit of timeout value
     */
    public AtomicApproximateDouble(CommunicationManager groupCon,
                            String name,
                            double epsilon,
                            double initialVote,
                            long timeout,
                            TimeUnit unit)
    {
       super(groupCon, name, epsilon, initialVote, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // Synchronous characteristics
        this.timeout = timeout;
        this.unit    = unit;

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }

    AtomicApproximateDouble(CommunicationManager groupCon,
                                      String name,
                                      double epsilon,
                                      double initialVote,
                                      long timeout,
                                      TimeUnit unit,
                                      FaultClass faultClass)
    {
        super(groupCon, name, epsilon, initialVote, faultClass, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // Synchronous characteristics
        this.timeout = timeout;
        this.unit    = unit;

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }

    /**
     * Create a new, fully connected instance of an object containing a double updated atomically and with
     * approximate consensus algorithms
     * @param groupCon Constitution of a fully connected group of communicating processes
     * @param epsilon Initial precision of approximation
     * @param timeout Timeout value
     * @param unit Unit of timeout value
     */
    public AtomicApproximateDouble(CommunicationManager groupCon,
                                   String name,
                                   double epsilon,
                                   long timeout,
                                   TimeUnit unit)
    {

        super(groupCon, name, epsilon, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // Synchronous characteristics
        this.timeout = timeout;
        this.unit    = unit;

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }

    public <T extends AtomicApproximateVariableCore> AtomicApproximateDouble(T other)
    {
        super(other);
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
            while(this.myV == null)
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
        return this.consensusPossible.get() ? requestConsensus().get() : null;
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
        if(this.consensusPossible.get())
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
            return this.consensusPossible.get() ?
                   requestConsensus().thenApply(this.consensusV::setDouble).thenApply(v -> false).get() :
                   false;
        }
        // else, we just set the new vote
        else
        {
            if(this.myV.getDouble().equals(expectedValue))
            {
                this.myV.setDouble(newValue);

                this.vLock.unlock();

                // perform consensus and return when finished (with future)
                return this.consensusPossible.get() ?
                       requestConsensus().thenApply(this.consensusV::setDouble).thenApply(v -> true).get() :
                       false;
            }
            else return false;
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

            if(this.myV != null) this.myV.setDouble(newValue);

            this.vLock.unlock();
        }

        // if all checks done, commence consensus and return future containing the result of the operation
        return this.consensusPossible.get() ?
               requestConsensus().thenApply(v -> { this.consensusV.setDouble(v); return previousValue; }).get() :
               null;
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

    @Override
    public AsyncAtomicApproximateDouble async()
    {
        return new AsyncAtomicApproximateDouble(this);
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

    // Implementation of abstract methods from superclass

    protected RequestID startNewConsensus    ()
    {
        // Generate requestID
        RequestID myReqId = generateRequestID();
        // Snapshot global state
        // lock global state so we can have a consistent snapshot
        this.globalLock.lock();
        // generate new ConsensusParameters snapshot with current global state
        ConsensusState snapshot = new ConsensusState(
                this.n,
                this.t,
                this.epsilon,
                this.groupCon,
                this.consensusBroadcast,
                this.approximationMessageSerializer,
                this.instanceID);
        // unlock global state
        this.globalLock.unlock();
        // Get timeout and default values
        var timeoutAndDefault = getTimeoutAndDefaultValues();
        // Generate new consensus instance
        ConsensusInstance<Double> consensus = new SynchDLPSW86Instance(snapshot,
<<<<<<< HEAD
                myReqId,
                this.myV.getDouble(),
                timeoutAndDefault.getValue0(),
                timeoutAndDefault.getValue1(),
                timeoutAndDefault.getValue2(),
                this.messageLogger);
=======
                                                                    myReqId,
                                                                    this.myV.getDouble(),
                                                                    timeoutAndDefault.getValue0(),
                                                                    timeoutAndDefault.getValue1(),
                                                                    timeoutAndDefault.getValue2(),
                                                                    this.messageLogger);
>>>>>>> FixingFinalDissertationVersion
        // Store new snapshot
        this.requestsLock.lock();
        this.activeRequests  .put(myReqId, consensus);
        this.requestsLock.unlock();
        // Broadcast request intent
        return myReqId;
    }

    protected byte initializationType() {
        return MessageType.SYNCH_INITIALIZATION;
    }

    protected void handleNew (ApproximationMessage msg)
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
        this.requestsLock.lock();

        // get the timeout and default values
        var timeoutAndDefault = getTimeoutAndDefaultValues();

        // lock global state so we can have a consistent snapshot
        this.globalLock.lock();
        // generate new ConsensusParameters snapshot with current global state
        ConsensusState snapshot = new ConsensusState(
                this.n,
                this.t,
                this.epsilon,
                this.groupCon,
                this.consensusBroadcast,
                this.approximationMessageSerializer,
                this.instanceID);
        // unlock global state
        this.globalLock.unlock();

        // make the new consensus instance
        var consensusInstance = new SynchDLPSW86Instance(snapshot,
                msg.reqID,
                this.myV.getDouble(),
                timeoutAndDefault.getValue0(),
                timeoutAndDefault.getValue1(),
                timeoutAndDefault.getValue2(),
                this.messageLogger);

        this.activeRequests  .put(msg.reqID, consensusInstance);

        // deal out stored messages relative to this request
        handleStoredMessages(consensusInstance);

        this.requestsLock.unlock();

        // start consensus (in a new thread?)
        new Thread( () -> approximateConsensusO(msg, msg.reqID).thenAccept(this.consensusV::setDouble) ).start();
    }

    // Auxiliary functions

    private Triplet<Long, TimeUnit, Double> getTimeoutAndDefaultValues()
    {
        this.globalLock.lock();
        var triplet = new Triplet<>(this.timeout, this.unit, this.defaultValue);
        this.globalLock.unlock();

        return triplet;
    }




<<<<<<< HEAD
=======
    private void storeMessage(ApproximationMessage msg, ProcessAttachment attachment)
    {
        this.unhandledMessages.add(new Pair<>(attachment, msg));
    }

    private void handleStoredMessages(ConsensusInstance<Double> consensusInstance)
    {
        if(consensusInstance != null)
        {
            // handle each unhandled message  with requestID
            this.unhandledMessages
                    .stream()
                    .filter(p -> p.getValue1().reqID.equals(consensusInstance.getReqID()))
                    .forEach(m -> consensusInstance.exchange(m.getValue1().getType(),
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
            this.groupCon.forEach((address, process) ->
            {
                if (request.getGroupState().containsKey(address))
                    process.markAsFaulty(process.isFaulty());
            });
            // check that we can continue with consensus in the future
            if (this.faultyProcessesExceedT())
                this.consensusPossible.set(false);
        }

        this.requestsLock.unlock();
    }

    private boolean faultyProcessesExceedT()
    {
        return this.groupCon.values().stream().filter(OtherNodeInterface::isFaulty).count() > this.t;
    }

    // FOR TESTING PURPOSES ONLY
    // *************************************************************************************
    public List<ConsensusMetrics> getMetrics()
    {
        return this.metrics
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey().internalID))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    public MessageLogger getMessageLogger()
    {
        return this.messageLogger;
    }

    public int getNumFinishedRequests()
    {
        return this.finishedRequestsCount.get();
    }
    // *************************************************************************************
>>>>>>> FixingFinalDissertationVersion
}