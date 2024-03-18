package core;

import Interface.communication.communicationHandler.CommunicationManager;
import Interface.consensus.utils.ConsensusInstance;
import Interface.consensus.async.AsynchronousPrimitive;
import Interface.consensus.synch.AtomicApproximateValue;
import Interface.consensus.synch.SynchronousAlgorithm;
import Interface.consensus.synch.SynchronousPrimitive;
import utils.consensus.types.faultDescriptors.FaultClass;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import utils.consensus.synchConsensusUtils.FCAInstance;
import utils.math.atomicExtensions.AtomicDouble;
import utils.consensus.snapshot.ConsensusState;
import utils.consensus.ids.RequestID;
import utils.communication.message.ApproximationMessage;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.communication.message.MessageType;

import java.util.*;
import java.util.concurrent.*;

public final class AtomicInexactDouble
        extends AtomicApproximateVariableCore
        implements AtomicApproximateValue<Double>, SynchronousPrimitive, SynchronousAlgorithm<Double>
{
    // Class constants
    public  static final int MINIMUM_PROCESSES          = 4;
    private static final Set<Byte> WANTED_MESSAGE_TYPES = new TreeSet<>(Arrays.asList(
            MessageType.FCA_INITIALIZATION,
            MessageType.FCA_APPROXIMATION,
            MessageType.FCA_HALTED
    ));

    // Synchronous Characteristics
    private long timeout;
    private TimeUnit unit;

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
    public AtomicInexactDouble(CommunicationManager groupCon,
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

    AtomicInexactDouble(CommunicationManager groupCon,
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
    public AtomicInexactDouble(CommunicationManager groupCon,
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
        if(this.consensusPossible.get()) requestConsensus().thenApply(this.consensusV::setDouble).get();
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
                   requestConsensus().thenApply(this.consensusV::setDouble).thenApply(v -> false).get()
                   : false;
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
                       requestConsensus().thenApply(this.consensusV::setDouble).thenApply(v -> true).get()
                       : false;
            }
            else return false;
        }
    }

    @Override
    public void setDefaultValue(Double defaultValue) throws UnsupportedOperationException {
        // because there is no default value in FCA
        throw new UnsupportedOperationException();
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
        return this.consensusPossible.get() ? requestConsensus().thenApply(v -> {
            this.consensusV.setDouble(v);
            return previousValue;
        }).get() : null;
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
    public AsynchronousPrimitive async()
    {
        // no equivalent async primitive implemented
        throw new UnsupportedOperationException();
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

    // Implementation of superclass abstract methods

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
        ConsensusInstance<Double> consensus = new FCAInstance(snapshot,
                myReqId,
                this.myV.getDouble(),
                timeoutAndDefault.getValue0(),
                timeoutAndDefault.getValue1());
        // Store new snapshot
        this.requestsLock.lock();
        this.activeRequests  .put(myReqId, consensus);
        this.requestsLock.unlock();

        // Broadcast request intent
        return myReqId;
    }

    protected void handleNew            (ApproximationMessage msg)
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

        this.requestsLock.lock();

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

        // get the timeout and default values
        var timeoutAndDefault = getTimeoutAndDefaultValues();

        // make the new consensus instance
        var consensusInstance = new FCAInstance(snapshot,
                msg.reqID,
                this.myV.getDouble(),
                timeoutAndDefault.getValue0(),
                timeoutAndDefault.getValue1());

        this.activeRequests  .put(msg.reqID, consensusInstance);

        // deal out stored messages relative to this request
        handleStoredMessages(consensusInstance);

        this.requestsLock.unlock();

        // start consensus (in a new thread)
        new Thread( () -> approximateConsensusO(msg, msg.reqID).thenAccept(this.consensusV::setDouble) ).start();
    }

    protected byte initializationType()
    {
        return MessageType.FCA_INITIALIZATION;
    }

    // Auxiliary functions

    private Triplet<Long, TimeUnit, Double> getTimeoutAndDefaultValues()
    {
        this.globalLock.lock();
        double defaultValue = 0.0;
        var triplet = new Triplet<>(this.timeout, this.unit, defaultValue);
        this.globalLock.unlock();

        return triplet;
    }






}