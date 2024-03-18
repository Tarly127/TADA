package utils.consensus.synchConsensusUtilities;

import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.Broadcast;
import Interface.communication.groupConstitution.OtherNodeInterface;
import Interface.consensus.utils.ApproximateConsensusHandler;
import Interface.consensus.utils.ConsensusInstance;
import utils.communication.communicationHandler.Broadcast.AsynchBroadcast;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.groupConstitution.ProcessStatus;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.communication.serializer.MessageSerializer;
import utils.consensus.ids.InstanceID;
import utils.prof.ConsensusMetrics;
import org.javatuples.Pair;
import utils.consensus.snapshot.ConsensusState;
import utils.consensus.ids.RequestID;
import utils.prof.MessageLogger;
import utils.prof.Stopwatch;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import static utils.io.ObjectPrinter.printArray;

public final class ConsensusInstanceSkeleton<ConsensusAttachment>
        extends ConsensusState implements ConsensusInstance<Double>
{
    public final Map<Integer, List<Double>> multisetPerRound;
    public final Map<Integer, CompletableFuture<double[]>> multisetFuturePerRound;
    public final ReentrantLock multisetLock;
    public      Double startingV;
    public      Double endingV;
    public final RequestID reqID;
    // metrics
    public final ConsensusMetrics metrics;
    public final MessageLogger    logger;
    // Instance variables
    private final Long     timeout;
    private final TimeUnit unit;
    private final Double   defaultValue;

    private final ApproximateConsensusHandler<ConsensusAttachment> handler;
    private final ConsensusAttachment                              attachment;



    /* ************ */
    /* Constructors */
    /* ************ */

    public ConsensusInstanceSkeleton()
    {
        super(1, 0, 0.0, null, new AsynchBroadcast(),  new MessageSerializer<>(ApproximationMessage.class), null);

        this.multisetLock = new ReentrantLock(true);
        this.multisetPerRound = new HashMap<>();
        this.multisetFuturePerRound = new HashMap<>();
        this.startingV = 0.0;
        this.reqID = null;
        this.metrics = null;
        this.logger = null;

        this.timeout      = Long.MAX_VALUE;
        this.unit         = TimeUnit.DAYS;
        this.defaultValue = 0.0;

        this.handler    = new ApproximateConsensusHandler<>() {};
        this.attachment = null;
    }

    public ConsensusInstanceSkeleton(RequestID reqID,
                                     InstanceID instanceID,
                                     int n,
                                     int t,
                                     double epsilon,
                                     double startingV,
                                     GroupConstitution groupState,
                                     Broadcast broadcast,
                                     Long timeout,
                                     TimeUnit unit,
                                     Double defaultValue,
                                     ApproximateConsensusHandler<ConsensusAttachment> handler,
                                     final ConsensusAttachment attachment)
    {
        super(n, t, epsilon, groupState, broadcast,  new MessageSerializer<>(ApproximationMessage.class), instanceID);

        this.reqID      = reqID;
        this.startingV  = startingV;
        this.endingV    = null;
        this.logger     = null;
        this.metrics    = null;

        this.multisetLock           = new ReentrantLock(true);
        this.multisetPerRound       = new HashMap<>();
        this.multisetFuturePerRound = new HashMap<>();

        this.timeout      = timeout;
        this.unit         = unit;
        this.defaultValue = defaultValue;

        this.handler    = handler;
        this.attachment = attachment;
    }

    public  <T extends ConsensusState> ConsensusInstanceSkeleton(T snapshot,
                                                                 RequestID reqID,
                                                                 Double startingV,
                                                                 Long timeout,
                                                                 TimeUnit unit,
                                                                 Double defaultValue,
                                                                 ApproximateConsensusHandler<ConsensusAttachment> handler,
                                                                 final ConsensusAttachment attachment,
                                                                 MessageLogger logger)
    {
        super(snapshot);

        this.reqID      = reqID;
        this.startingV  = startingV;
        this.endingV    = null;
        this.logger     = logger;
        this.metrics    = new ConsensusMetrics();

        this.multisetLock           = new ReentrantLock(true);
        this.multisetPerRound       = new HashMap<>();
        this.multisetFuturePerRound = new HashMap<>();

        this.timeout      = timeout;
        this.unit         = unit;
        this.defaultValue = defaultValue;

        this.handler    = handler;
        this.attachment = attachment;
    }

    /* ********************** */
    /* Main Consensus Methods */
    /* ********************** */

    public CompletableFuture<Double> approximateConsensus_other(ApproximationMessage msg)
    {
        this.startingV  = handler.onNewConsensus(this, startingV, this.attachment);

        synchExchange_handleInitialization(msg, msg.getSender());

        return initializationRound()
                .thenCompose(this::approximationRounds)
                .thenCompose(this::terminationRound);
    }

    public CompletableFuture<Double> approximateConsensus_self()
    {
        return initializationRound()
                .thenCompose(this::approximationRounds)
                .thenCompose(this::terminationRound);
    }

    private CompletableFuture<Double> initializationRound ()
    {
        this.metrics.startingV = this.startingV;

        this.multisetLock.lock();
        insertRoundInfo(0);
        this.multisetLock.unlock();

        // broadcast own value at round 0
        return broadcast(this.startingV, 0, MessageType.GCS_INITIALIZATION, reqID)
                .thenApply(nothing ->
                {
                    this.multisetLock.lock();
                    var future = this.multisetFuturePerRound.get(0);
                    this.multisetLock.unlock();

                    double[] V;

                    try
                    {
                        // get the multiset for the current round
                        // only wait until timeout, not indefinitely
                        V = future.get(this.timeout, this.unit);
                    }
                    catch (InterruptedException | ExecutionException | TimeoutException e)
                    {
                        this.multisetLock.lock();
                        // if there was a timeout (or otherwise) error, fill values for halted processes
                        fillValuesForTimedOutConnections(0);
                        // check if future is complete
                        if(endExchange(0))
                            complete(0);
                        // get the appropriate double[]
                        V = this.multisetPerRound.get(0).stream().mapToDouble(v -> v).toArray();

                        future.complete(V);

                        this.multisetLock.unlock();
                    }

                    // add own vote at round h to V
                    V[V.length - 1] = this.startingV;
                    // sort V
                    Arrays.sort(V);

                    // calculate the number of rounds necessary to achieve consensus
                    this.H = handler.rounds(this, V, attachment);

                    this.metrics.expectedH = this.H;
                    this.metrics.realH     = this.H;

                    // perform the round calculations in the handler
                    return handler.approximationRound(this, V, this.startingV, 0, attachment);
                });
    }

    private CompletableFuture<Double> approximationRound  (Double vAtCurrentRound, int round)
    {
        this.multisetLock.lock();
        // insert round info if not already available
        insertRoundInfo(round);
        this.multisetLock.unlock();

        // broadcast own value at round h
        return broadcast(vAtCurrentRound, round, MessageType.GCS_APPROXIMATION, this.reqID)
                .thenApply(nothing ->
                {
                    this.multisetLock.lock();
                    var future = this.multisetFuturePerRound.get(round);
                    this.multisetLock.unlock();

                    double[] V;

                    try
                    {
                        V = future.get(this.timeout, this.unit);
                    }
                    catch (InterruptedException | ExecutionException | TimeoutException e)
                    {
                        this.metrics.timedOutRounds ++;

                        this.multisetLock.lock();

                        // if there was a timeout (or otherwise) error, fill values for halted processes
                        fillValuesForTimedOutConnections(round);
                        // check if future is complete
                        if(endExchange(round))
                            complete(round);
                        // get the appropriate double[]
                        V = this.multisetPerRound.get(round).stream().mapToDouble(v -> v).toArray();

                        this.multisetLock.unlock();
                    }

                    // add own vote at round h to V
                    V[V.length - 1] = vAtCurrentRound;
                    // sort
                    Arrays.sort(V);

                    // perform the round's calculations at the current round
                    return handler.approximationRound(this, V, vAtCurrentRound, round, attachment);
                })
                .thenCompose(v ->
                {
                    if(round < this.H)
                        if(exchangeStillNecessary(round))
                            return approximationRound(v, round + 1);
                        else
                            return simpleApproximationRounds(v, round + 1);
                    else
                    {
                        return CompletableFuture.completedFuture(this.endingV = v);
                    }
                });


    }

    private CompletableFuture<Double> approximationRounds (Double v)
    {
        // call approximation rounds recursively
        return this.H > 0 ? approximationRound(v, 1) : CompletableFuture.completedFuture(this.endingV = v);
    }

    private CompletableFuture<Double> terminationRound    (Double v)
    {
        this.metrics.finalV = this.endingV;

        return this.H == 0 ? CompletableFuture.completedFuture(this.endingV) :
               broadcast(this.endingV, this.H + 1, MessageType.GCS_HALTED, reqID)
                .thenApply(nothing -> this.endingV);
    }

    private CompletableFuture<Double> simpleApproximationRounds (Double v, int round)
    {
        double[] othersV = collectHaltedVotes(round);
        double vNext     = v;

        printArray(othersV);

        for(int h = round; h <= this.H; h++)
        {
            double[] V = Arrays.copyOf(othersV, othersV.length + 1);

            V[V.length - 1] = vNext;

            Arrays.sort(V);

            vNext = handler.approximationRound(this, V, vNext, h, attachment);
        }

        this.H             = round - 1;
        this.metrics.realH = this.H;

        return CompletableFuture.completedFuture(vNext);
    }

    private void synchExchange_handleInitialization(ApproximationMessage msg, AddressInterface q)
    {
        this.multisetLock.lock();

        if (msg.round == 0)
        {
            // check if the round's multiset already exists
            if (this.multisetPerRound.containsKey(msg.round))
            {
                // check that it hasn't been completed yet
                if (!this.multisetFuturePerRound.get(msg.round).isDone())
                {
                    // add new value
                    this.multisetPerRound.get(msg.round).add(msg.v);
                    // count this message
                    this.metrics.neededMsgs.getAndIncrement();
                    // check if completed
                    if (endExchange(msg.round))
                        complete(msg.round);
                }
            }
            else
            {
                // if it doesn't, register it
                this.multisetPerRound.put(msg.round, new ArrayList<>(this.n - this.t - 1));
                this.multisetFuturePerRound.put(msg.round, new CompletableFuture<>());
                // fill values from processes that halted in rounds previous to this one
                fillValuesForHaltedProcesses(msg.round);
                // check if finished and, if not, add the new value and check again
                if (!endExchange(msg.round))
                {
                    // add new value
                    this.multisetPerRound.get(msg.round).add(msg.v);
                    // count this message
                    this.metrics.neededMsgs.getAndIncrement();
                    // check again if completed
                    if (endExchange(msg.round))
                        complete(msg.round);
                }
                else
                    complete(msg.round);
            }
        }
        else
            // if we received an INITIALIZATION message not tagged with round 0, it's a violation of the algorithm
            // and, as such, can be considered faulty behaviour
            this.groupState.get(q).markAsFaulty();

        this.multisetLock.unlock();
    }
    private void synchExchange_handleApproximation(ApproximationMessage msg)
    {
        this.multisetLock.lock();

        // don't discard if we don't know how many rounds there'll be OR if we do know and this one is relevant
        if (this.H == null || msg.round <= this.H)
        {
            // check if the round's multiset already exists
            if (this.multisetPerRound.containsKey(msg.round))
            {
                // check that it hasn't been completed yet
                if (!this.multisetFuturePerRound.get(msg.round).isDone())
                {
                    // add new value
                    this.multisetPerRound.get(msg.round).add(msg.v);
                    // count this message
                    this.metrics.neededMsgs.getAndIncrement();
                    // check if completed
                    if (endExchange(msg.round))
                        complete(msg.round);
                }
            }
            else
            {
                // if it doesn't, register it
                this.multisetPerRound.put(msg.round, new ArrayList<>(this.n - this.t - 1));
                this.multisetFuturePerRound.put(msg.round, new CompletableFuture<>());
                // fill values from processes that halted in rounds previous to this one
                fillValuesForHaltedProcesses(msg.round);
                // check if finished and, if not, add the new value and check again
                if (!endExchange(msg.round))
                {
                    // add new value
                    this.multisetPerRound.get(msg.round).add(msg.v);
                    // count this message
                    this.metrics.neededMsgs.getAndIncrement();
                    // check again if completed
                    if (endExchange(msg.round))
                        complete(msg.round);
                }
                else
                    complete(msg.round);
            }
        }

        this.multisetLock.unlock();
    }
    private void synchExchange_handleTermination(ApproximationMessage msg, AddressInterface q)
    {
        this.multisetLock.lock();

        // mark the process as finished
        this.groupState.get(q).complete(msg.v, msg.round);
        // don't discard if we don't know how many rounds there'll be OR if we do know and this one is relevant
        if (this.H == null || msg.round <= this.H)
        {
            // check if the round has already been registered
            if (this.multisetPerRound.containsKey(msg.round))
            {
                // check if the round hasn't been finished yet (if it has, we don't care to handle this message)
                if (!this.multisetFuturePerRound.get(msg.round).isDone())
                {
                    // add the value for said round
                    this.multisetPerRound.get(msg.round).add(msg.v);
                    // count this message
                    this.metrics.neededMsgs.getAndIncrement();
                    // check if finished
                    if (endExchange(msg.round))
                        complete(msg.round);
                }

                // add this new value to every round whose h > round, that has already been inserted, and check if
                // each one has been completed yet.
                this.multisetPerRound.forEach((h, V) ->
                {
                    if (h > msg.round && !this.multisetFuturePerRound.get(h).isDone())
                    {
                        // add the value to the multiset
                        V.add(msg.v);
                        // check if the round can be completed
                        if (endExchange(msg.round)) complete(h);
                    }
                });
            }
            else
            {
                // add future for said round
                this.multisetPerRound.put(msg.round, new ArrayList<>(this.n - 1));
                this.multisetFuturePerRound.put(msg.round, new CompletableFuture<>());
                // fill with values from halted processes (including this one, of course)
                fillValuesForHaltedProcesses(msg.round);
                // count this message
                this.metrics.neededMsgs.getAndIncrement();
                // check if finished
                if (endExchange(msg.round))
                    complete(msg.round);
                // we don't need to make additional checks for rounds (round, H], because they will be eventually added
                // and our own HALTED value will be added to them then
            }
        }

        this.multisetLock.unlock();
    }
    public  void exchange(Byte msgType, ApproximationMessage msg, AddressInterface q)
    {
        // count this message toward the total of processed messages, regardless of whether the message was actually
        // used or not
        this.metrics.processedMsgs.getAndIncrement();
        // only consider this message if the address is registered and doesn't correspond to a faulty process

        // process message before adding it to any multiset
        msg.v = handler.onReceive(this, msg, attachment);

        if(msg.v != null && this.groupState.containsKey(q))
        {
            switch (msgType)
            {
                case MessageType.GCS_APPROXIMATION  -> synchExchange_handleApproximation (msg);
                case MessageType.GCS_INITIALIZATION -> synchExchange_handleInitialization(msg, q);
                case MessageType.GCS_HALTED         -> synchExchange_handleTermination   (msg, q);
                case MessageType.GCS_RETRANSMISSION -> {}
            }
        }
    }

    /**
     * Insert round info and complete round, if needed
     * @param round relevant round
     */
    private void insertRoundInfo(int round)
    {
        if (!this.multisetPerRound.containsKey(round))
        {
            // if not, add it and the necessary future
            this.multisetPerRound      .put(round, new ArrayList<>(this.n - this.t - 1));
            this.multisetFuturePerRound.put(round, new CompletableFuture<>());

            fillValuesForHaltedProcesses(round);

            // check if completed
            if(endExchange(round))
                complete(round);
        }
    }
    /**
     * Fills values for V_round for processes that finished in round <= (arg) round, up to n - t - 1 values
     * @param round Current round
     */
    private void fillValuesForHaltedProcesses(int round)
    {
        this.groupState.forEach((address, process) ->
        {
            // only fill up to n - t - 1
            if(process.isCompleted() && this.multisetPerRound.get(round).size() < this.n - 1)
            {
                // get v on completion and the completion round for this process
                Pair<Double, Integer> vAndRound = process.getvOnCompletion();
                // if they exist and are of rounds older than or equal to us, use it
                if (vAndRound != null && vAndRound.getValue1() <= round)
                    this.multisetPerRound.get(round).add(vAndRound.getValue0());
            }
        });
    }
    /**
     * Fill values for unresponsive processes/processes that did not send a message for that round within the
     * expected time frame
     * @param round Current round
     */
    private void fillValuesForTimedOutConnections(int round)
    {
        var multiset = this.multisetPerRound.get(round);
        // only fill for timed out processes if we didn't receive enough responses
        // also only fill with default value if it exists, as it may not exist
        if(multiset.size() < this.n - 1 && this.defaultValue != null)
        {
            // add default value for every process
            // also add one for this process to make a double[] that fits
            for(int i = multiset.size(); i < this.n; i++)
                multiset.add(this.defaultValue);
        }
    }
    /**
     * Broadcasts an Approximation message to the group
     * @param v vote at current round
     * @param round current algorithm round
     * @param type type of message (can be INITIALIZATION, APPROXIMATION and HALTED)
     * @param requestID ID of current approximate consensus request
     * @return future pertaining to the conclusion of the broadcast (when n writes have been done, successful or not)
     */
    private CompletableFuture<Void> broadcast(double v, int round, Byte type, RequestID requestID)
    {
        final ApproximationMessage msg = new ApproximationMessage(v, round, type, requestID);
        final long startTime           = Stopwatch.time();
        final LocalDateTime wallTime   = LocalDateTime.now();

        return Broadcast(new ApproximationMessage(v, round, type, requestID))
                .thenApply(nothing ->
                {
                    if(logger != null)
                        logger.registerMetric(msg, wallTime, Stopwatch.time() - startTime,
                                MessageLogger.MessageEvent.SEND);

                    return nothing;
                });
    }

    /**
     * Collect votes for the relevant round for processes that terminated execution of the algorithm in said round or
     * on previous rounds.
     * @param round Relevant round
     * @return Array with votes from halted processes
     */
    private double[] collectHaltedVotes(int round)
    {
        return this.groupState
                .values()
                .stream()
                .filter(p -> p.isCompleted() && p.getvOnCompletion() != null && p.getvOnCompletion().getValue1() <= round)
                .mapToDouble(p -> p.getvOnCompletion().getValue0())
                .toArray();
    }

    /**
     * Check if exchange is still necessary for a given round. That means checking if all processes have terminated
     * in that round or previously and, if so, then exchange is no longer necessary.
     * @param round Relevant round
     * @return True if still necessary, false otherwise
     */
    private boolean exchangeStillNecessary(int round)
    {
        return !(this.groupState
                .values()
                .stream()
                .filter(p -> p.isCompleted() && p.getvOnCompletion() != null && p.getvOnCompletion().getValue1() <= round)
                .count() >= this.n - 1);
    }

    /**
     * Complete the future of a given round
     * @param round Relevant round
     */
    private void complete(int round)
    {
        // we collected n-t-1 values but create V with n-t to account for our own vote
        List<Double> VAsList = this.multisetPerRound.get(round);

        double[] V = new double[VAsList.size() + 1];

        for (int i = 0; i < V.length - 1; i++)
            V[i] = VAsList.get(i);

        // finish exchange at round h
        if (this.multisetFuturePerRound.containsKey(round))
            this.multisetFuturePerRound.get(round).complete(V);
        else
            this.multisetFuturePerRound.put(round, CompletableFuture.completedFuture(V));
    }

    /**
     * Check if more messages are necessary to complete exchange in a given round
     * @param round Relevant round
     * @return True if exchange can be ended, false otherwise.
     */
    private boolean endExchange(int round)
    {
        return this.multisetPerRound.containsKey(round)
                && this.handler.endExchangeCondition(
                this, this.multisetPerRound.get(round).stream().mapToDouble(v -> v).toArray(), round, attachment
        );
    }


    /* ******************************* */
    /* Other Consensus Related Methods */
    /* ******************************* */

    public boolean consensusStillPossible()
    {
        return this.groupState
                .values()
                .stream()
                .filter(ProcessStatus::isActive)
                .count() <= t;
    }

    public Map<? extends AddressInterface, ? extends OtherNodeInterface> getGroupState()
    {
        return this.groupState;
    }

    public RequestID getReqID()
    {
        return this.reqID;
    }

    public ConsensusMetrics getMetrics()
    {
        return this.metrics;
    }
}