package core;

import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.Broadcast;
import Interface.communication.communicationHandler.CommunicationManager;
import Interface.consensus.utils.ApproximateConsensusHandler;
import Interface.consensus.utils.ConsensusInstance;
import Interface.consensus.synch.AtomicApproximateValue;
import Interface.consensus.synch.SynchronousAlgorithm;
import Interface.consensus.synch.SynchronousPrimitive;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.consensus.ids.RequestID;
import utils.consensus.snapshot.ConsensusState;
import utils.consensus.synchConsensusUtils.ConsensusInstanceSkeleton;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.consensus.types.faultDescriptors.FaultClass;
import utils.math.atomicExtensions.AtomicDouble;
import org.javatuples.Pair;
import org.javatuples.Triplet;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public final class AtomicApproximateDoubleTemplate<ConsensusAttachment>
        extends AtomicApproximateVariableCore
        implements AtomicApproximateValue<Double>, SynchronousPrimitive, SynchronousAlgorithm<Double>
{
    // Class constants
    public  static final int MINIMUM_PROCESSES          = 4;
    private static final Set<Byte> WANTED_MESSAGE_TYPES = new TreeSet<>(Arrays.asList(
            MessageType.GCS_INITIALIZATION,
            MessageType.GCS_APPROXIMATION,
            MessageType.GCS_HALTED,
            MessageType.GCS_RETRANSMISSION
    ));

    // Synchronous Characteristics
    private long     timeout;
    private TimeUnit unit;
    private Double   defaultValue = 0.0;

    // Generic Constructor
    final ApproximateConsensusHandler<ConsensusAttachment> consensusHandler;
    final ReentrantLock       consensusAttachmentLock;
         ConsensusAttachment defaultAttachment;

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
    public AtomicApproximateDoubleTemplate(CommunicationManager groupCon,
                                           String name,
                                           double epsilon,
                                           double initialVote,
                                           long timeout,
                                           TimeUnit unit,
                                           ApproximateConsensusHandler<ConsensusAttachment> handler)
    {
        super(groupCon, name, epsilon, initialVote, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // Synchronous characteristics
        this.timeout = timeout;
        this.unit    = unit;

        // consensus handler
        this.consensusHandler = handler;
        this.consensusAttachmentLock = new ReentrantLock(true);
        this.defaultAttachment = null;

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }

    AtomicApproximateDoubleTemplate(CommunicationManager groupCon,
                                              String name,
                                              double epsilon,
                                              double initialVote,
                                              long timeout,
                                              TimeUnit unit,
                                              ApproximateConsensusHandler<ConsensusAttachment> handler,
                                              FaultClass faultClass)
    {
        super(groupCon, name, epsilon, initialVote, faultClass, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // Synchronous characteristics
        this.timeout = timeout;
        this.unit    = unit;

        // consensus handler
        this.consensusHandler = handler;
        this.consensusAttachmentLock = new ReentrantLock(true);
        this.defaultAttachment = null;

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }

    AtomicApproximateDoubleTemplate(CommunicationManager groupCon,
                                              String name,
                                              double epsilon,
                                              long timeout,
                                              TimeUnit unit,
                                              ApproximateConsensusHandler<ConsensusAttachment> handler,
                                              Broadcast broadcast)
    {
        super(groupCon, name, epsilon, broadcast, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // Synchronous characteristics
        this.timeout = timeout;
        this.unit    = unit;

        // consensus handler
        this.consensusHandler = handler;
        this.consensusAttachmentLock = new ReentrantLock(true);
        this.defaultAttachment = null;

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }

    AtomicApproximateDoubleTemplate(CommunicationManager groupCon,
                                              String name,
                                              double epsilon,
                                              double initialVote,
                                              long timeout,
                                              TimeUnit unit,
                                              ApproximateConsensusHandler<ConsensusAttachment> handler,
                                              Broadcast broadcast)
    {
        super(groupCon, name, epsilon, initialVote, broadcast, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // Synchronous characteristics
        this.timeout = timeout;
        this.unit    = unit;

        // consensus handler
        this.consensusHandler = handler;
        this.consensusAttachmentLock = new ReentrantLock(true);
        this.defaultAttachment = null;

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
    public AtomicApproximateDoubleTemplate(CommunicationManager groupCon,
                                           String name,
                                           double epsilon,
                                           long timeout,
                                           TimeUnit unit,
                                           ApproximateConsensusHandler<ConsensusAttachment> handler)
    {
        super(groupCon, name, epsilon, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // Synchronous characteristics
        this.timeout = timeout;
        this.unit    = unit;

        // consensus handler
        this.consensusHandler = handler;
        this.consensusAttachmentLock = new ReentrantLock(true);
        this.defaultAttachment = null;

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }

    public AtomicApproximateDoubleTemplate(CommunicationManager groupCon,
                                           String name,
                                           double epsilon,
                                           long timeout,
                                           TimeUnit unit,
                                           ApproximateConsensusHandler<ConsensusAttachment> handler,
                                           ConsensusAttachment attachment)
    {
        super(groupCon, name, epsilon, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // Synchronous characteristics
        this.timeout = timeout;
        this.unit    = unit;

        // consensus handler
        this.consensusHandler        = handler;
        this.consensusAttachmentLock = new ReentrantLock(true);
        this.defaultAttachment       = attachment;

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }

    public AtomicApproximateDoubleTemplate(final AsyncAtomicApproximateDoubleTemplate<ConsensusAttachment> asyncThat)
    {
        super(asyncThat);

        this.consensusHandler  = asyncThat.consensusHandler;
        this.defaultAttachment = asyncThat.defaultAttachment;
        this.consensusAttachmentLock = asyncThat.consensusAttachmentLock;
        // do not setup listener!
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

        // get the default attachment
        this.consensusAttachmentLock.lock();

        var ca = this.defaultAttachment;

        this.consensusAttachmentLock.unlock();

        // if all checks done, commence consensus and return future containing the result of the operation
        return this.consensusPossible.get() ? requestConsensus(ca).get() : null;
    }

    public Double get(ConsensusAttachment ca)
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
            {
                this.voteReady.await();
            }
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
        return this.consensusPossible.get() ? requestConsensus(ca).get() : null;
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

        // get the default attachment
        this.consensusAttachmentLock.lock();

        var ca = this.defaultAttachment;

        this.consensusAttachmentLock.unlock();

        // perform consensus and return when finished (with future)
        if(this.consensusPossible.get())
            requestConsensus(ca).thenApply(this.consensusV::setDouble).get();
    }

    public void   set(Double newV, ConsensusAttachment ca)
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
            requestConsensus(ca).thenApply(this.consensusV::setDouble).get();
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

        // get the default attachment
        this.consensusAttachmentLock.lock();

        var ca = this.defaultAttachment;

        this.consensusAttachmentLock.unlock();

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
                   requestConsensus(ca).thenApply(this.consensusV::setDouble).thenApply(v -> false).get() :
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
                       requestConsensus(ca).thenApply(this.consensusV::setDouble).thenApply(v -> true).get() :
                       false;
            }
            else return false;
        }
    }

    public boolean compareAndSet(Double expectedValue, Double newValue, ConsensusAttachment ca)
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
                   requestConsensus(ca).thenApply(this.consensusV::setDouble).thenApply(v -> false).get() :
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
                       requestConsensus(ca).thenApply(this.consensusV::setDouble).thenApply(v -> true).get() :
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

        // get the default attachment
        this.consensusAttachmentLock.lock();

        var ca = this.defaultAttachment;

        this.consensusAttachmentLock.unlock();

        // if all checks done, commence consensus and return future containing the result of the operation
        return this.consensusPossible.get() ?
               requestConsensus(ca)
                       .thenApply(v -> { this.consensusV.setDouble(v); return previousValue; })
                       .get() :
               null;
    }

    public Double getAndSet(Double newValue, ConsensusAttachment ca)
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
               requestConsensus(ca).thenApply(v -> { this.consensusV.setDouble(v); return previousValue; }).get() :
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
    public AsyncAtomicApproximateDoubleTemplate<ConsensusAttachment> async()
    {
        return new AsyncAtomicApproximateDoubleTemplate<>(this);
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

    public void setDefaultAttachment(ConsensusAttachment attachment)
    {
        this.consensusAttachmentLock.lock();

        this.defaultAttachment = attachment;

        this.consensusAttachmentLock.unlock();
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

        // handle the new packet concurrently
        this.es.submit(this::supplyNextMessage);

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

    private CompletableFuture<Double> requestConsensus(ConsensusAttachment ca)
    {
        return approximateConsensusS(startNewConsensus(ca));
    }

    private RequestID startNewConsensus    (ConsensusAttachment ca)
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
        ConsensusInstance<Double> consensus = new ConsensusInstanceSkeleton<>(snapshot,
                myReqId,
                this.myV.getDouble(),
                timeoutAndDefault.getValue0(),
                timeoutAndDefault.getValue1(),
                timeoutAndDefault.getValue2(),
                getHandlerInstance(),
                ca,
                this.messageLogger);
        // Store new snapshot
        this.requestsLock.lock();
        this.activeRequests  .put(myReqId, consensus);
        this.requestsLock.unlock();
        // Broadcast request intent
        return myReqId;
    }


    // Implementation of abstract methods from super

    protected void    handleNew  (ApproximationMessage msg)
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
        var consensusInstance = new ConsensusInstanceSkeleton<>(snapshot,
                msg.reqID,
                this.myV.getDouble(),
                timeoutAndDefault.getValue0(),
                timeoutAndDefault.getValue1(),
                timeoutAndDefault.getValue2(),
                getHandlerInstance(),
                this.defaultAttachment,
                this.messageLogger);

        this.activeRequests  .put(msg.reqID, consensusInstance);

        // deal out stored messages relative to this request
        handleStoredMessages(consensusInstance);

        this.requestsLock.unlock();

        // start consensus (in a new thread?)
        new Thread( () -> approximateConsensusO(msg, msg.reqID).thenAccept(this.consensusV::setDouble) ).start();
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

    private Triplet<Long, TimeUnit, Double> getTimeoutAndDefaultValues()
    {
        this.globalLock.lock();
        var triplet = new Triplet<>(this.timeout, this.unit, this.defaultValue);
        this.globalLock.unlock();

        return triplet;
    }

    private ApproximateConsensusHandler<ConsensusAttachment> getHandlerInstance()
    {
        try
        {
            var constructors = Objects.requireNonNull(this.consensusHandler).getClass().getConstructors();

            if(constructors.length != 0)
            {
                var handler = (ApproximateConsensusHandler<ConsensusAttachment>) constructors[0].newInstance();

                handler.init();

                return handler;
            }
            else return this.consensusHandler;
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



    // AUXILIARY FUNCTIONS


}