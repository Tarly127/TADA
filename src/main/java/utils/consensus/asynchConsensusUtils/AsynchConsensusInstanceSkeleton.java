package utils.consensus.asynchConsensusUtils;

import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.Broadcast;
import Interface.communication.groupConstitution.OtherNodeInterface;
import Interface.consensus.utils.ApproximateConsensusHandler;
import Interface.consensus.utils.ConsensusInstance;
import utils.communication.address.Address;
import utils.communication.communicationHandler.Broadcast.AsyncBroadcast;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.groupConstitution.OtherNodeStatus;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.communication.serializer.MessageSerializer;
import utils.consensus.ids.InstanceID;
<<<<<<< HEAD:src/main/java/utils/consensus/asynchConsensusUtils/AsynchConsensusInstanceSkeleton.java
import utils.math.ApproximationFunctions;
import utils.prof.ConsensusMetrics;
import org.javatuples.Pair;
=======
import utils.prof.ConsensusMetrics;
import org.javatuples.Pair;
import utils.math.ApproximationFunctions;
>>>>>>> FixingFinalDissertationVersion:src/main/java/utils/consensus/asynchConsensusUtilities/AsynchConsensusInstanceSkeleton.java
import utils.consensus.snapshot.ConsensusState;
import utils.consensus.ids.RequestID;
import utils.prof.MessageLogger;
import utils.prof.Stopwatch;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;


public final class AsynchConsensusInstanceSkeleton<ConsensusAttachment>
        extends ConsensusState implements ConsensusInstance<Double>
{
    // consensus
    public final Map<Integer, List<Double>> multisetPerRound;
    public final Map<Integer, CompletableFuture<double[]>> multisetFuturePerRound;
    public final ReentrantLock multisetLock;
    public final Double startingV;
    public      Double endingV;
    // ids
    public final RequestID reqID;
    // metrics
    public final ConsensusMetrics metrics;
    public final MessageLogger    logger;
    // Instance variables
<<<<<<< HEAD:src/main/java/utils/consensus/asynchConsensusUtils/AsynchConsensusInstanceSkeleton.java
    //private final MessageSerializer<ApproximationMessage> serializer;

=======
>>>>>>> FixingFinalDissertationVersion:src/main/java/utils/consensus/asynchConsensusUtilities/AsynchConsensusInstanceSkeleton.java
    public final ApproximateConsensusHandler<ConsensusAttachment> handler;
    public final ConsensusAttachment                              attachment;

    private final ApproximateConsensusHandler<ConsensusAttachment> defaultHandler
            = new ApproximateConsensusHandler<>() {
        @Override
        public int rounds(ConsensusState cs, double[] V0, ConsensusAttachment ca)
        {
<<<<<<< HEAD:src/main/java/utils/consensus/asynchConsensusUtils/AsynchConsensusInstanceSkeleton.java
            return Math.max(cs.t + 1, ApproximationFunctions.AsyncH(V0, cs.epsilon, cs.n, cs.t));
=======
            return Math.max(0, ApproximationFunctions.AsyncH(V0, cs.epsilon, cs.n, cs.t));
        }

        @Override
        public Double onReceive(ConsensusState cs, ApproximationMessage msg, ConsensusAttachment ca)
        {
            return msg.v;
>>>>>>> FixingFinalDissertationVersion:src/main/java/utils/consensus/asynchConsensusUtilities/AsynchConsensusInstanceSkeleton.java
        }

        @Override
        public double approximationRound(ConsensusState cs, double[] V, double v, int round, ConsensusAttachment ca)
        {
            if(round == 0)
                return ApproximationFunctions.mean(ApproximationFunctions.reduce(V, 2 * cs.t));
            else
                return ApproximationFunctions.f(V, 2 * cs.t, cs.t);
        }

        @Override
        public boolean endExchangeCondition(ConsensusState cs, double[] multiset, int round, ConsensusAttachment ca)
        {
            return multiset.length >= cs.n - cs.t - 1;
        }
    };


    /* ************ */
    /* Constructors */
    /* ************ */

    public AsynchConsensusInstanceSkeleton()
    {
<<<<<<< HEAD:src/main/java/utils/consensus/asynchConsensusUtils/AsynchConsensusInstanceSkeleton.java
        super(1, 0, 0.0, null, new AsyncBroadcast(), null, null);
=======
        super(1, 0, 0.0, null, new AsynchBroadcast(), new MessageSerializer<>(ApproximationMessage.class), null);
>>>>>>> FixingFinalDissertationVersion:src/main/java/utils/consensus/asynchConsensusUtilities/AsynchConsensusInstanceSkeleton.java

        this.multisetLock = new ReentrantLock(true);
        this.multisetPerRound = new HashMap<>();
        this.multisetFuturePerRound = new HashMap<>();
        this.startingV = 0.0;
        this.reqID = null;
        this.metrics = null;
        this.logger = null;

        this.handler = defaultHandler;
        this.attachment = null;
    }

    public AsynchConsensusInstanceSkeleton(RequestID reqID,
                                 InstanceID instanceID,
                                 int n,
                                 int t,
                                 double epsilon,
                                 double startingV,
                                 GroupConstitution groupState,
                                 Broadcast broadcast)
    {
        super(n, t, epsilon, groupState, broadcast, new MessageSerializer<>(ApproximationMessage.class), instanceID);

        this.reqID      = reqID;
        this.startingV  = startingV;
        this.endingV    = null;
        this.logger     = null;
        this.metrics    = null;

        this.multisetLock           = new ReentrantLock(true);
        this.multisetPerRound       = new HashMap<>();
        this.multisetFuturePerRound = new HashMap<>();

        this.handler    = this.defaultHandler;
        this.attachment = null;
    }

    public <T extends ConsensusState> AsynchConsensusInstanceSkeleton(T snapshot,
                                                                      RequestID reqID,
                                                                      Double startingV,
                                                                      MessageLogger logger,
                                                                      ApproximateConsensusHandler<ConsensusAttachment> consensusHandler,
                                                                      ConsensusAttachment attachment)
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

        this.handler    = consensusHandler;
        this.attachment = attachment;

    }

    public  <T extends AsynchConsensusInstanceSkeleton<ConsensusAttachment>> AsynchConsensusInstanceSkeleton(T other)
    {
        super(other);

        this.reqID      = other.reqID;
        this.startingV  = other.startingV;
        this.endingV    = other.endingV;
        this.logger     = other.logger;
        this.metrics    = other.metrics;

        this.multisetLock           = other.multisetLock;
        this.multisetPerRound       = other.multisetPerRound;
        this.multisetFuturePerRound = other.multisetFuturePerRound;

        this.handler    = other.handler;
        this.attachment = other.attachment;

    }


    /* ********************** */
    /* Main Consensus Methods */
    /* ********************** */

    public CompletableFuture<Double> start(ApproximationMessage msg)
    {
        // increase the number of messages processed for this instance of consensus
        this.metrics.processedMsgs.getAndIncrement();
        exchange(msg.getType(), msg, new Address(msg.reqID.requesterHash));

        return initializationRound()
                .thenCompose(this::approximationRounds)
                .thenCompose(this::terminationRound);
    }

    public CompletableFuture<Double> start()
    {
        return initializationRound()
                .thenCompose(this::approximationRounds)
                .thenCompose(this::terminationRound);
    }

    private CompletableFuture<Double> initializationRound ()
    {
        this.metrics.startingV = this.startingV;

        this.multisetLock.lock();
        // add this round's future and multiset to the map (in case it doesn't exist)
        insertRoundInfo(0);
        this.multisetLock.unlock();

        // broadcast own value at round 0
        return broadcast(this.startingV, 0, MessageType.GCS_INITIALIZATION, reqID)
                .thenCompose(nothing ->
                {
                    // get the round's future
                    this.multisetLock.lock();
                    var future = this.multisetFuturePerRound.get(0);
                    this.multisetLock.unlock();

                    // return corresponding round future
                    return future.thenApply(V ->
                    {
                        // add my own vote to V
                        V[V.length - 1] = this.startingV;
                        // Sort V
                        Arrays.sort(V);
                        // asyncH <- ceil(log_c(delta(V)/epsilon))
                        this.H = this.handler.rounds(this, V, this.attachment);

                        this.metrics.realH     = this.H;
                        this.metrics.expectedH = this.H;

                        // v <- mean(reduce^2t(V))
                        return this.handler.approximationRound(this, V, this.startingV, 0, this.attachment);
                    });
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
                .thenCompose(nothing ->
                {
                    this.multisetLock.lock();
                    var future = this.multisetFuturePerRound.get(round);
                    this.multisetLock.unlock();

                    // return corresponding round future
                    return future.thenApply(V ->
                    {
                        // add own vote at round h to V
                        V[V.length - 1] = vAtCurrentRound;
                        // sort V
                        Arrays.sort(V);
                        // v <- f_2t,t(V)
                        return this.handler.approximationRound(this, V, vAtCurrentRound, round, this.attachment);
                    });
                })
                .thenCompose(v -> {
                    if(round < this.H)
                        if(exchangeStillNecessary(round) && consensusStillPossible())
                            return approximationRound(v, round + 1);
                        else
                            return simpleApproximationRounds(v, round + 1);
                    else
                        return CompletableFuture.completedFuture(this.endingV = v);
                });
    }

    private CompletableFuture<Double> approximationRounds (Double v)
    {
        // call approximation rounds recursively
        return this.H > 0 ? approximationRound(v, 1) : CompletableFuture.completedFuture(v);
    }

    private CompletableFuture<Double> terminationRound    (Double v)
    {
        this.metrics.finalV = this.endingV;
        return broadcast(this.endingV, this.H + 1, MessageType.GCS_HALTED, reqID)
                .thenApply(nothing -> this.endingV);
    }

    private CompletableFuture<Double> simpleApproximationRounds (Double v, int round)
    {
        double[] othersV = collectHaltedVotes(round);
        double vNext = v;

        for(int h = round; h <= this.H; h++)
        {
            double[] V = Arrays.copyOf(othersV, othersV.length + 1);

            V[V.length - 1] = vNext;

            Arrays.sort(V);

            vNext = handler.approximationRound(this, V, vNext, h, attachment);
        }

        this.H = round - 1;
        this.metrics.realH = this.H;

        return CompletableFuture.completedFuture(vNext);
    }

    /* ********************** */
    /* AsyncExchange Handlers */
    /* ********************** */

    private void asynchExchange_handleInitialization(ApproximationMessage msg, AddressInterface q)
    {
        this.multisetLock.lock();

        if(msg.round == 0)
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
                    if(endExchange(msg.round))
                        complete(msg.round);
                }
            }
            else
            {
                // if it doesn't, register it
                this.multisetPerRound      .put(msg.round, new ArrayList<>(this.n - this.t - 1));
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
                    if(endExchange(msg.round))
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
    private void asynchExchange_handleApproximation(ApproximationMessage msg)
    {
        this.multisetLock.lock();

        // don't discard if we don't know how many rounds there'll be OR if we do know and this one is relevant
        if(this.H == null || msg.round <= this.H)
        {
            // check if the round's multiset already exists
            if(this.multisetPerRound.containsKey(msg.round))
            {
                // check that it hasn't been completed yet
                if(!this.multisetFuturePerRound.get(msg.round).isDone())
                {
                    // add new value
                    this.multisetPerRound.get(msg.round).add(msg.v);
                    // count this message
                    this.metrics.neededMsgs.getAndIncrement();
                    // check if completed
                    if(endExchange(msg.round))
                        complete(msg.round);
                }
            }
            else
            {
                // if it doesn't, register it
                this.multisetPerRound      .put(msg.round, new ArrayList<>(this.n - this.t - 1));
                this.multisetFuturePerRound.put(msg.round, new CompletableFuture<>());
                // fill values from processes that halted in rounds previous to this one
                fillValuesForHaltedProcesses(msg.round);
                // check if finished and, if not, add the new value and check again
                if(!endExchange(msg.round))
                {
                    // add new value
                    this.multisetPerRound.get(msg.round).add(msg.v);
                    // count this message
                    this.metrics.neededMsgs.getAndIncrement();
                    // check again if completed
                    if(endExchange(msg.round))
                        complete(msg.round);
                }
                else
                    complete(msg.round);
            }
        }
        this.multisetLock.unlock();
    }
    private void asynchExchange_handleTermination  (ApproximationMessage msg, AddressInterface q)
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
                    if(endExchange(msg.round))
                        complete(msg.round);
                }

                // add this new value to every round whose h > round, that has already been inserted, and check if
                // each one has been completed yet.
                this.multisetPerRound.forEach((h, V)->
                {
                    if(h > msg.round && !this.multisetFuturePerRound.get(h).isDone())
                    {
                        V.add(msg.v);
                        if(endExchange(msg.round))
                            complete(msg.round);
                    }
                });
            }
            else
            {
                // add future for said round
                this.multisetPerRound      .put(msg.round, new ArrayList<>(this.n - this.t - 1));
                this.multisetFuturePerRound.put(msg.round, new CompletableFuture<>());
                // fill with values from halted processes (including this one, of course)
                fillValuesForHaltedProcesses(msg.round);
                // count this message
                this.metrics.neededMsgs.getAndIncrement();
                // check if finished
                if(endExchange(msg.round))
                    complete(msg.round);
                // we don't need to make additional checks for rounds (round, H], because they will be eventually added
                // and our own HALTED value will be added to them then
            }
        }

        this.multisetLock.unlock();
    }
    public  void exchange(Byte msgType, ApproximationMessage msg, AddressInterface q)
    {
        // increase the number of messages processed for this instance of consensus
        this.metrics.processedMsgs.getAndIncrement();
        // only consider this message if the address is registered and doesn't correspond to a faulty process.
        // process message before adding it to any multiset
        var optV = handler.onReceive(this, msg, q, attachment);

        if (optV.isPresent())
        {
            msg.v = optV.get();

            if (this.groupState.containsKey(q) && !this.groupState.get(q).isFaulty())
            {
                switch (msgType)
                {
                    case MessageType.GCS_APPROXIMATION -> asynchExchange_handleApproximation(msg);
                    case MessageType.GCS_INITIALIZATION -> asynchExchange_handleInitialization(msg, q);
                    case MessageType.GCS_HALTED -> asynchExchange_handleTermination(msg, q);
                    default -> {}
                }
            }
        }
    }

    /* ************************* */
    /* Auxiliary Private Methods */
    /* ************************* */


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

    private boolean endExchange(int round)
    {
        return this.multisetPerRound.containsKey(round)
                && this.handler.endExchangeCondition(
                this, this.multisetPerRound.get(round).stream().mapToDouble(v -> v).toArray(), round, attachment
        );
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
            if(process.isCompleted() && this.multisetPerRound.get(round).size() < this.n - this.t - 1)
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

<<<<<<< HEAD:src/main/java/utils/consensus/asynchConsensusUtils/AsynchConsensusInstanceSkeleton.java
        return this.Broadcast(msg)
=======
        return super.Broadcast(msg)
>>>>>>> FixingFinalDissertationVersion:src/main/java/utils/consensus/asynchConsensusUtilities/AsynchConsensusInstanceSkeleton.java
                .thenApply(nothing ->
                {
                    if(logger != null)
                        logger.registerMetric(msg, wallTime, Stopwatch.time() - startTime,
                                MessageLogger.MessageEvent.SEND);

                    return nothing;
                });
    }

    /* ******************************* */
    /* Other Consensus Related Methods */
    /* ******************************* */

    private double[] collectHaltedVotes(int round)
    {
        return this.groupState
                .values()
                .stream()
                .filter(p -> p.isCompleted() && p.getvOnCompletion() != null && p.getvOnCompletion().getValue1() <= round)
                .mapToDouble(p -> p.getvOnCompletion().getValue0())
                .toArray();
    }

    private boolean exchangeStillNecessary(int round)
    {
        return !(this.groupState
                .values()
                .stream()
                .filter(p -> p.isCompleted() && p.getvOnCompletion() != null && p.getvOnCompletion().getValue1() <= round)
                .count() >= this.n  - this.t - 1);
    }

    public boolean consensusStillPossible()
    {
        return this.groupState
                .values()
                .stream()
                .filter(OtherNodeStatus::isActive)
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