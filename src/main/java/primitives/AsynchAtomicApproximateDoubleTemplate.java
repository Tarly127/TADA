package primitives;

import AtomicInterface.communication.address.AddressInterface;
import AtomicInterface.communication.communicationHandler.CommunicationManager;
import AtomicInterface.communication.groupConstitution.ProcessInterface;
import AtomicInterface.consensus.ApproximateConsensusHandler;
import AtomicInterface.consensus.ConsensusInstance;
import AtomicInterface.consensus.async.AsyncAtomicApproximateValue;
import AtomicInterface.consensus.async.AsynchronousPrimitive;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.consensus.asynchConsensusUtilities.AsynchConsensusInstanceSkeleton;
import utils.consensus.ids.RequestID;
import utils.consensus.snapshot.ConsensusState;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.consensus.types.faultDescriptors.FaultClass;
import utils.math.atomicExtensions.AtomicDouble;
import utils.measurements.ConsensusMetrics;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import utils.measurements.MessageLogger;
import utils.measurements.Stopwatch;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public final class AsynchAtomicApproximateDoubleTemplate<ConsensusAttachment>
        extends AtomicApproximatePrimitiveCore
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

    /**
     * Create a new, fully connected instance of an object containing a double updated atomically and with
     * approximate consensus algorithms
     * @param groupCon Constitution of a fully connected group of communicating processes
     * @param epsilon Initial precision of approximation
     * @param initialVote Initial vote for stored value
     */
    AsynchAtomicApproximateDoubleTemplate(CommunicationManager groupCon,
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

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }

    AsynchAtomicApproximateDoubleTemplate(CommunicationManager groupCon,
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

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }

    /**
     * Create a new, fully connected instance of an object containing a double updated atomically and with
     * approximate consensus algorithms
     * @param groupCon Constitution of a fully connected group of communicating processes
     * @param epsilon Initial precision of approximation
     */
    AsynchAtomicApproximateDoubleTemplate(CommunicationManager groupCon,
                                          String name,
                                          double epsilon,
                                          ApproximateConsensusHandler<ConsensusAttachment> handler)
    {
        super(groupCon, name, epsilon, WANTED_MESSAGE_TYPES, MINIMUM_PROCESSES-1);

        // consensus handler
        this.consensusHandler        = handler;
        this.defaultAttachment       = null;
        this.consensusAttachmentLock = new ReentrantLock(true);

        // set up listener to supply messages
        this.es.submit(this::supplyNextMessage);
    }

    AsynchAtomicApproximateDoubleTemplate(AtomicApproximateDoubleTemplate<ConsensusAttachment> synchThat)
    {
        super(synchThat);

        this.defaultAttachment       = synchThat.defaultAttachment;
        this.consensusHandler        = synchThat.consensusHandler;
        this.consensusAttachmentLock = synchThat.consensusAttachmentLock;
    }

    // MAIN PUBLIC METHODS (GETTERS, SETTERS, ETC.)
    @Override
    public CompletableFuture<Double> get()
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
    public CompletableFuture<Void>   set(Double newV)
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
    public CompletableFuture<Void>   lazySet(Double newV)
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
    public CompletableFuture<Double> lazyGet()
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
    public CompletableFuture<Boolean> compareAndSet(Double expectedValue, Double newValue)
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


    public CompletableFuture<Boolean> compareAndSet(Double expectedValue, Double newValue, ConsensusAttachment ca)
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
    public CompletableFuture<Double> getAndSet(Double newValue)
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

    public CompletableFuture<Double> getAndSet(Double newValue, ConsensusAttachment ca)
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
    public CompletableFuture<Double> lazyGetAndSet(Double newValue)
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
        return this.t;
    }

    @Override
    public AtomicApproximateDoubleTemplate<ConsensusAttachment> sync()
    {
        return new AtomicApproximateDoubleTemplate<>(this);
    }


    // Important consensus methods

    /**
     * Deal with approximate consensus transaction started by us
     * @return Future containing v at completion
     */
    private CompletableFuture<Double> requestConsensus(ConsensusAttachment attachment)
    {
        return approximateConsensusS(startNewConsensus(attachment));
    }

    /**
     * Deal with approximate consensus transaction NOT started by this process
     * @param reqID ID of the consensus transaction
     * @return Future containing v at completion
     */
    private CompletableFuture<Double> approximateConsensusO(ApproximationMessage msg, RequestID reqID, Long start)
    {
        return Objects
                .requireNonNull(this.activeRequests.get(reqID))
                .approximateConsensus_other(msg)
                .thenApply(v->{

                    var metrics = this.activeRequests.get(reqID).getMetrics();

                    metrics.reqID = reqID;
                    metrics.texecNanos = Stopwatch.time() - start;

                    this.metrics.put(reqID, metrics);

                    finishedRequestsCount.incrementAndGet();

                    cleanUp(reqID); return v;
                })
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
                    finishedRequestsCount.incrementAndGet();
                    cleanUp(reqID);
                    return v;
                })
                .thenApply(consensusV::setDouble);
    }

    /**
     * Starts new consensus request
     * @return Future containing RequestID for new consensus
     */
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
                this.consensusBroadcast);
        // unlock global state
        this.globalLock.unlock();
        // Generate new consensus instance
        ConsensusInstance<Double> consensus
                = new AsynchConsensusInstanceSkeleton<>(snapshot, myReqId, this.instanceID, this.myV.getDouble(),
                this.messageLogger, getHandlerInstance(), attachment);
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
        Triplet<AddressInterface, byte[], Byte> nextPacket = this.msgManager.dequeue(this.wantedTypesSubscription);

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
                case BYZANTINE ->
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

    private void handleNew            (ApproximationMessage msg)
    {
        var start = Stopwatch.time();

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
                this.consensusBroadcast);
        // unlock global state
        this.globalLock.unlock();

        var consensusInstance
                = new AsynchConsensusInstanceSkeleton<>(snapshot,
                msg.reqID,
                this.instanceID,
                this.myV.getDouble(),
                this.messageLogger,
                getHandlerInstance(),
                this.defaultAttachment);

        this.activeRequests  .put(msg.reqID, consensusInstance);

        // deal out stored messages relative to this request
        handleStoredMessages(consensusInstance);

        this.requestsLock.unlock();

        // start consensus
        new Thread(() -> approximateConsensusO(msg, msg.reqID, start).thenAccept(this.consensusV::setDouble))
                .start();
    }


    private ApproximateConsensusHandler<ConsensusAttachment> getHandlerInstance()
    {
        try
        {
            var constructors = Objects.requireNonNull(this.consensusHandler).getClass().getConstructors();

            if(constructors.length != 0)
                return (ApproximateConsensusHandler<ConsensusAttachment>) constructors[0].newInstance();
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

    private RequestID generateRequestID()
    {
        return new RequestID(this.myAddress, this.internalRequestID.getAndIncrement());
    }

    private Action  nextAction(ApproximationMessage msg)
    {
        Action actionToTake;

        if (msg.type == MessageType.GCS_INITIALIZATION)
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

    private void handleStoredMessages(ConsensusInstance<Double> consensusInstance)
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
                                m.getValue0().procAddress)
                    );
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
            // check that we can continue with consensus in the future
            if (this.faultyProcessesExceedT())
            {
                System.out.println("Too many faulty processes");

                // TODO: actually do something about this!!!
            }
        }

        this.requestsLock.unlock();
    }

    private boolean faultyProcessesExceedT()
    {
        return this.groupCon.values().stream().filter(ProcessInterface::isFaulty).count() > this.t;
    }

    public List<ConsensusMetrics> getMetrics()
    {
        Comparator<Map.Entry<RequestID, ConsensusMetrics>> comparator
                = Comparator.comparingInt(entry -> entry.getKey().internalID);

        return this.metrics.entrySet().stream().sorted(comparator).map(Map.Entry::getValue).collect(Collectors.toList());
    }

    public MessageLogger getMessageLogger()
    {
        return this.messageLogger;
    }

    public int getNumFinishedRequests()
    {
        return this.finishedRequestsCount.get();
    }

    private ConsensusAttachment getDefaultAttachment()
    {
        this.consensusAttachmentLock.lock();

        var ca = this.defaultAttachment;

        this.consensusAttachmentLock.unlock();

        return ca;
    }
}