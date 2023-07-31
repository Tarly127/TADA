package core;

import Interface.communication.communicationHandler.CommunicationManager;
import Interface.consensus.utils.ConsensusInstance;
import Interface.consensus.async.AsyncAtomicApproximateValue;
import Interface.consensus.async.AsynchronousPrimitive;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.consensus.asynchConsensusUtils.AsynchDLPSW86Instance;
import utils.consensus.ids.RequestID;
import utils.consensus.snapshot.ConsensusState;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.consensus.types.faultDescriptors.FaultClass;
import utils.math.atomicExtensions.AtomicDouble;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public final class AsyncAtomicApproximateDouble
        extends AtomicApproximateVariableCore
        implements AsyncAtomicApproximateValue<Double>, AsynchronousPrimitive
{
    // Class constants
    public  static final int MINIMUM_PROCESSES  = 6;
    private static final Set<Byte> WANTED_MESSAGE_TYPES = new TreeSet<>(Arrays.asList(
            MessageType.ASYNCH_INITIALIZATION,
            MessageType.ASYNCH_APPROXIMATION,
            MessageType.ASYNCH_HALTED
    ));

    // CONSTRUCTORS

    /**
     * Create a new, fully connected instance of an object containing a double updated atomically and with
     * approximate consensus algorithms
     * @param groupCon Constitution of a fully connected group of communicating processes
     * @param name Name of the primitive
     * @param epsilon Initial precision of approximation
     * @param initialVote Initial vote for stored value
     */
    public AsyncAtomicApproximateDouble(CommunicationManager groupCon,
                                        String name,
                                        double epsilon,
                                        double initialVote)
    {
        super(groupCon, name, epsilon, initialVote, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // set up listener to supply messages -> they have to use the subclass implementation of supplyNextMessage
        this.es.submit(this::supplyNextMessage);
    }

    AsyncAtomicApproximateDouble(CommunicationManager groupCon, String name, double epsilon,
                                 double initialVote, FaultClass faultClass)
    {
        super(groupCon, name, epsilon, initialVote, faultClass, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // set up listener to supply messages -> they have to use the subclass implementation of supplyNextMessage
        this.es.submit(this::supplyNextMessage);
    }

    /**
     * Create a new, fully connected instance of an object containing a double updated atomically and with
     * approximate consensus algorithms
     * @param groupCon Constitution of a fully connected group of communicating processes
     * @param epsilon Initial precision of approximation
     */
    public AsyncAtomicApproximateDouble(CommunicationManager groupCon, String name, double epsilon)
    {
        super(groupCon, name, epsilon, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // set up listener to supply messages -> they have to use the subclass implementation of supplyNextMessage
        this.es.submit(this::supplyNextMessage);
    }

    public <T extends AtomicApproximateVariableCore> AsyncAtomicApproximateDouble(T that)
    {
        super(that);
    }

    // MAIN PUBLIC METHODS (GETTERS, SETTERS, ETC.)

    @Override
    public Future<Double> get()
            throws MinimumProcessesNotReachedException
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
        return consensusPossible.get() ? requestConsensus().thenApply(v -> {
            this.consensusV.setDouble(v);
            return v;
        }) : CompletableFuture.completedFuture(null);
    }

    @Override
    public Future<Void> set(Double newV)
            throws MinimumProcessesNotReachedException
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
        return this.consensusPossible.get() ?
               requestConsensus().thenApply(this.consensusV::setDouble).thenApply(null) :
               CompletableFuture.completedFuture(null);
    }

    @Override
    public Future<Void> lazySet(Double newV)
    {
        CompletableFuture<Void> futureSet = new CompletableFuture<>();

        CompletableFuture.runAsync(() ->
        {
            this.vLock.lock();

            if (this.myV == null)
            {
                this.myV = new AtomicDouble(newV);
                this.voteReady.signalAll();
            }
            else
                this.myV.setDouble(newV);

            this.vLock.unlock();

            futureSet.complete(null);

        }, this.es);

        return futureSet;
    }

    @Override
    public Future<Double> lazyGet()
    {
        CompletableFuture<Double> futureGet = new CompletableFuture<>();

        CompletableFuture.runAsync(()->
        {
            this.vLock.lock();

            while(this.myV == null)
                try
                {
                    this.voteReady.await();
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();

                    this.vLock.unlock();
                }

            double presentV = this.myV.getDouble();

            this.vLock.unlock();

            futureGet.complete(presentV);
        }, this.es);

        return futureGet;
    }

    @Override
    public Future<Boolean> compareAndSet(Double expectedValue, Double newValue)
            throws MinimumProcessesNotReachedException
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
                   requestConsensus().thenApply(this.consensusV::setDouble).thenApply(v -> false)
                   : CompletableFuture.completedFuture(false);
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
                       requestConsensus().thenApply(this.consensusV::setDouble).thenApply(v -> true) :
                       CompletableFuture.completedFuture(false);
            }
            else
            {
                return CompletableFuture.completedFuture(false);
            }
        }




    }

    @Override
    public Future<Double> getAndSet(Double newValue)
            throws MinimumProcessesNotReachedException
    {
        Double previousValue;

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
            previousValue = (this.myV == null) ? null : this.myV.getDouble();

            if(this.myV != null) this.myV.setDouble(newValue);

            this.vLock.unlock();
        }

        // if all checks done, commence consensus and return future containing the result of the operation
        return this.consensusPossible.get() ? requestConsensus().thenApply(v -> {
            this.consensusV.setDouble(v);
            return previousValue;
        }) : CompletableFuture.completedFuture(null);
    }

    @Override
    public Future<Double> lazyGetAndSet(Double newValue)
    {
        CompletableFuture<Double> futureGetAndSet = new CompletableFuture<>();

        CompletableFuture.runAsync(() ->
        {
            Double previousValue;

            this.vLock.lock();

            if (this.myV == null)
            {
                this.myV = new AtomicDouble(newValue);
                this.voteReady.signalAll();
                previousValue = null;
            }
            else
            {
                previousValue = this.myV.getDouble();
                this.myV.setDouble(newValue);
            }

            this.vLock.unlock();

            futureGetAndSet.complete(previousValue);

        }, this.es);

        return futureGetAndSet;
    }

    @Override
    public AtomicApproximateDouble sync()
    {
        return new AtomicApproximateDouble(this);
    }


    // IMPLEMENTATIONS OF AUXILIARY FUNCTIONS FROM CORE

    protected void handleNew            (ApproximationMessage msg)
    {
        this.vLock.lock();
        // wait for a value to be placed in startingV, if there is none
        while (this.myV == null)
            try
            {
                this.voteReady.await();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();

                if(this.vLock.isLocked())
                    this.vLock.unlock();
            }

        if(this.vLock.isLocked())
            this.vLock.unlock();

        // register the new ID
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

        var consensusInstance
                = new AsynchDLPSW86Instance(snapshot, msg.reqID, this.myV.getDouble(),
                this.messageLogger);

        this.activeRequests  .put(msg.reqID, consensusInstance);

        // deal out stored messages relative to this request
        handleStoredMessages(consensusInstance);

        this.requestsLock.unlock();

        // start consensus
        new Thread(() -> approximateConsensusO(msg, msg.reqID).thenAccept(this.consensusV::setDouble))
                .start();
    }

    protected byte initializationType()
    {
        return MessageType.ASYNCH_INITIALIZATION;
    }

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
        // Generate new consensus instance
        ConsensusInstance<Double> consensus
                = new AsynchDLPSW86Instance(snapshot, myReqId, this.myV.getDouble(), this.messageLogger);
        // Store new snapshot
        this.requestsLock.lock();
        this.activeRequests  .put(myReqId, consensus);
        this.requestsLock.unlock();
        // Broadcast request intent
        return myReqId;
    }






}