package core;

import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.Broadcast;
import Interface.communication.communicationHandler.CommunicationManager;
import Interface.consensus.utils.ApproximateConsensusHandler;
import Interface.consensus.utils.ConsensusInstance;
import Interface.consensus.async.AsyncAtomicApproximateValue;
import Interface.consensus.async.AsynchronousPrimitive;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.consensus.asynchConsensusUtils.AsynchConsensusInstanceSkeleton;
import utils.consensus.ids.RequestID;
import utils.consensus.snapshot.ConsensusState;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.consensus.types.faultDescriptors.FaultClass;
import utils.math.atomicExtensions.AtomicDouble;
import org.javatuples.Triplet;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

public final class AsyncAtomicApproximateDoubleTemplate<ConsensusAttachment>
        extends AtomicApproximateVariableCore
        implements AsyncAtomicApproximateValue<Double>, AsynchronousPrimitive
{
    // Class constants
    public  static final int MINIMUM_PROCESSES  = 6;
    private static final Set<Byte> WANTED_MESSAGE_TYPES = new TreeSet<>(Arrays.asList(
            MessageType.GCS_INITIALIZATION,
            MessageType.GCS_APPROXIMATION,
            MessageType.GCS_HALTED,
            MessageType.GCS_RETRANSMISSION
    ));

    // Generic Constructor
    final ApproximateConsensusHandler<ConsensusAttachment> consensusHandler;
    final ReentrantLock       consensusAttachmentLock;
    ConsensusAttachment defaultAttachment;

    // CONSTRUCTORS

    AsyncAtomicApproximateDoubleTemplate(CommunicationManager groupCon,
                                         String name,
                                         double epsilon,
                                         double initialVote,
                                         ApproximateConsensusHandler<ConsensusAttachment> handler)
    {
        super(groupCon, name, epsilon, initialVote, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // consensus handler
        this.consensusHandler        = handler;
        this.defaultAttachment       = null;
        this.consensusAttachmentLock = new ReentrantLock(true);

        // set up listeners -> they have to use the subclass implementation of supplyNextMessage
        this.es.submit(this::supplyNextMessage);
    }

    AsyncAtomicApproximateDoubleTemplate(CommunicationManager groupCon,
                                         String name,
                                         double epsilon,
                                         double initialVote,
                                         ApproximateConsensusHandler<ConsensusAttachment> handler,
                                         FaultClass faultClass)
    {
        super(groupCon, name, epsilon, initialVote, faultClass, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // consensus handler
        this.consensusHandler        = handler;
        this.defaultAttachment       = null;
        this.consensusAttachmentLock = new ReentrantLock(true);

        // set up listener to supply messages -> they have to use the subclass implementation of supplyNextMessage
        this.es.submit(this::supplyNextMessage);
    }

    AsyncAtomicApproximateDoubleTemplate(CommunicationManager groupCon,
                                         String name,
                                         double epsilon,
                                         double initialVote,
                                         ApproximateConsensusHandler<ConsensusAttachment> handler,
                                         Broadcast broadcast)
    {
        super(groupCon, name, epsilon, initialVote, broadcast, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // consensus handler
        this.consensusHandler        = handler;
        this.defaultAttachment       = null;
        this.consensusAttachmentLock = new ReentrantLock(true);

        // set up listener to supply messages -> they have to use the subclass implementation of supplyNextMessage
        this.es.submit(this::supplyNextMessage);
    }

    AsyncAtomicApproximateDoubleTemplate(CommunicationManager groupCon,
                                         String name,
                                         double epsilon,
                                         ApproximateConsensusHandler<ConsensusAttachment> handler)
    {
        super(groupCon, name, epsilon, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // consensus handler
        this.consensusHandler        = handler;
        this.defaultAttachment       = null;
        this.consensusAttachmentLock = new ReentrantLock(true);

        // set up listener to supply messages -> they have to use the subclass implementation of supplyNextMessage
        this.es.submit(this::supplyNextMessage);
    }

    AsyncAtomicApproximateDoubleTemplate(CommunicationManager groupCon,
                                         String name,
                                         double epsilon,
                                         ApproximateConsensusHandler<ConsensusAttachment> handler,
                                         Broadcast broadcast)
    {
        super(groupCon, name, epsilon, broadcast, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // consensus handler
        this.consensusHandler        = handler;
        this.defaultAttachment       = null;
        this.consensusAttachmentLock = new ReentrantLock(true);

        // set up listener to supply messages -> they have to use the subclass implementation of supplyNextMessage
        this.es.submit(this::supplyNextMessage);
    }

    AsyncAtomicApproximateDoubleTemplate(AtomicApproximateDoubleTemplate<ConsensusAttachment> synchThat)
    {
        super(synchThat);

        this.defaultAttachment       = synchThat.defaultAttachment;
        this.consensusHandler        = synchThat.consensusHandler;
        this.consensusAttachmentLock = synchThat.consensusAttachmentLock;
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

        this.consensusAttachmentLock.lock();

        var ca = this.defaultAttachment;

        this.consensusAttachmentLock.unlock();

        // if all checks done, commence consensus and return future containing the result of the operation
        return this.consensusPossible.get() ? requestConsensus(ca).thenApply(v -> {
            this.consensusV.setDouble(v);
            return v;
        }) : CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Double> get(ConsensusAttachment ca)
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
        return this.consensusPossible.get() ? requestConsensus(ca).thenApply(v -> {
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
                requestConsensus(getDefaultAttachment()).thenApply(this.consensusV::setDouble).thenApply(null)
               : CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void>   set(Double newV, ConsensusAttachment ca)
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
                requestConsensus(ca).thenApply(this.consensusV::setDouble).thenApply(null)
               : CompletableFuture.completedFuture(null);
    }

    @Override
    public Future<Void> lazySet(Double newV)
    {
        CompletableFuture<Void> futureSet = new CompletableFuture<>();

        new Thread(() ->
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

        }).start();

        return futureSet;
    }

    @Override
    public Future<Double> lazyGet()
    {
        CompletableFuture<Double> futureGet = new CompletableFuture<>();

        new Thread(()->
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
        }).start();

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
                   requestConsensus(getDefaultAttachment()).thenApply(this.consensusV::setDouble).thenApply(v -> false)
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
                        requestConsensus(getDefaultAttachment()).thenApply(this.consensusV::setDouble).thenApply(v -> true)
                       : CompletableFuture.completedFuture(false);
            }
            else
            {
                return CompletableFuture.completedFuture(false);
            }
        }
    }


    public Future<Boolean> compareAndSet(Double expectedValue, Double newValue, ConsensusAttachment ca)
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
                   requestConsensus(ca).thenApply(this.consensusV::setDouble).thenApply(v -> false)
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
                        requestConsensus(ca).thenApply(this.consensusV::setDouble).thenApply(v -> true)
                       : CompletableFuture.completedFuture(false);
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
        return this.consensusPossible.get() ?
               requestConsensus(getDefaultAttachment()).thenApply(v -> {
                    this.consensusV.setDouble(v);
                    return previousValue;
               }) : CompletableFuture.completedFuture(null);
    }

    public Future<Double> getAndSet(Double newValue, ConsensusAttachment ca)
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

            this.vLock.unlock();
        }

        this.myV.setDouble(newValue);

        // if all checks done, commence consensus and return future containing the result of the operation
        return this.consensusPossible.get() ?
               requestConsensus(ca).thenApply(v -> {
                    this.consensusV.setDouble(v);
                    return previousValue;
               }) : CompletableFuture.completedFuture(null);
    }

    @Override
    public Future<Double> lazyGetAndSet(Double newValue)
    {
        CompletableFuture<Double> futureGetAndSet = new CompletableFuture<>();

        new Thread(() ->
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

        }).start();

        return futureGetAndSet;
    }

    @Override
    public AtomicApproximateDoubleTemplate<ConsensusAttachment> sync()
    {
        return new AtomicApproximateDoubleTemplate<>(this);
    }

    /**
     * Register additional types of interest
     * @param additionalTypes set with types
     * @return True if at least one of the types was added, false otherwise
     */
    public boolean setAdditionalMessageTypes(Collection<Byte> additionalTypes)
    {
        return this.msgManager.addTypesToRegistration(additionalTypes, wantedTypesSubscription);
    }


    // Implementations that differ from super methods because of some function's signatures

    protected void supplyNextMessage()
    {
        // Get next packet that interests us
        Triplet<AddressInterface, byte[], Byte> nextPacket = this.msgManager.getNextMessage(this.wantedTypesSubscription);

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

    private CompletableFuture<Double> requestConsensus(ConsensusAttachment attachment)
    {
        return approximateConsensusS(startNewConsensus(attachment));
    }

    private RequestID startNewConsensus    (ConsensusAttachment attachment)
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
                = new AsynchConsensusInstanceSkeleton<>(snapshot, myReqId, this.myV.getDouble(),
                this.messageLogger, getHandlerInstance(), attachment);
        // Store new snapshot
        this.requestsLock.lock();
        this.activeRequests  .put(myReqId, consensus);
        this.requestsLock.unlock();
        // Broadcast request intent
        return myReqId;
    }



    // Implementation of abstract methods from super
    protected void   handleNew  (ApproximationMessage msg)
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
                = new AsynchConsensusInstanceSkeleton<>(snapshot,
                msg.reqID,
                this.myV.getDouble(),
                this.messageLogger,
                getHandlerInstance(),
                this.defaultAttachment);

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
        return MessageType.GCS_INITIALIZATION;
    }

    protected RequestID startNewConsensus()
    {
        throw new UnsupportedOperationException();
    }

    // Auxiliary functions

    private ApproximateConsensusHandler<ConsensusAttachment> getHandlerInstance()
    {
        try
        {
            var constructors = Objects.requireNonNull(this.consensusHandler).getClass().getConstructors();
            var handler = this.consensusHandler;

            if(constructors.length != 0)
                // TODO: this is bad and I don't like it but I don't know how to do it better and it sucks
                handler = (ApproximateConsensusHandler<ConsensusAttachment>) constructors[0].newInstance();

            handler.init();

            return handler;
        }
        catch (IllegalAccessException | InvocationTargetException | NullPointerException e)
        {
            return this.consensusHandler;
        }
        catch (InstantiationException e)
        {
            e.printStackTrace();

            return this.consensusHandler;
        }
    }

    private ConsensusAttachment getDefaultAttachment()
    {
        this.consensusAttachmentLock.lock();

        var ca = this.defaultAttachment;

        this.consensusAttachmentLock.unlock();

        return ca;
    }
}