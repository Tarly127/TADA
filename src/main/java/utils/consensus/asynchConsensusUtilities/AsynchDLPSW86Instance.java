package utils.consensus.asynchConsensusUtilities;

import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.Broadcast;
import Interface.communication.groupConstitution.OtherNodeInterface;
import Interface.consensus.utils.ConsensusInstance;
import utils.communication.address.Address;
import utils.communication.communicationHandler.Broadcast.AsynchBroadcast;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.groupConstitution.ProcessStatus;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.communication.serializer.MessageSerializer;
import utils.consensus.ids.InstanceID;
import utils.math.ApproximationFunctions;
import utils.prof.ConsensusMetrics;
import org.javatuples.Pair;
import utils.consensus.snapshot.ConsensusState;
import utils.consensus.ids.RequestID;
import utils.prof.MessageLogger;
import utils.prof.Stopwatch;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

public final class AsynchDLPSW86Instance
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

    /* ************ */
    /* Constructors */
    /* ************ */

    public AsynchDLPSW86Instance()
    {
        super(1, 0, 0.0, null, new AsynchBroadcast(), null, null);

        this.multisetLock = new ReentrantLock(true);
        this.multisetPerRound = new HashMap<>();
        this.multisetFuturePerRound = new HashMap<>();
        this.startingV = 0.0;
        this.reqID = null;
        this.metrics = null;
        this.logger = null;
    }

    public AsynchDLPSW86Instance(RequestID reqID,
                                 InstanceID instanceID,
                                 int n,
                                 int t,
                                 double epsilon,
                                 double startingV,
                                 GroupConstitution groupState,
                                 Broadcast broadcast)
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
    }

    public <T extends ConsensusState> AsynchDLPSW86Instance(T snapshot,
                                                            RequestID reqID,
                                                            Double startingV,
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
    }

    public  <T extends AsynchDLPSW86Instance> AsynchDLPSW86Instance(T other)
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
    }


    /* ********************** */
    /* Main Consensus Methods */
    /* ********************** */

    public CompletableFuture<Double> approximateConsensus_other(ApproximationMessage msg)
    {
        // increase the number of messages processed for this instance of consensus
        this.metrics.processedMsgs.getAndIncrement();
        asynchExchange_handleInitialization(msg, new Address(msg.reqID.requesterHash));

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
        this.multisetLock.lock();
        // add this round's future and multiset to the map (in case it doesn't exist)
        insertRoundInfo(0);
        this.multisetLock.unlock();

        // broadcast own value at round 0
        return broadcast(this.startingV, 0, MessageType.ASYNCH_INITIALIZATION, reqID)
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
                        this.H = Math.max(0, ApproximationFunctions.AsyncH(V, epsilon, n, t));

                        this.metrics.realH     = this.H;
                        this.metrics.expectedH = this.H;

                        // v <- mean(reduce^2t(V))
                        return ApproximationFunctions.mean(ApproximationFunctions.reduce(V, 2 * t));
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
        return broadcast(vAtCurrentRound, round, MessageType.ASYNCH_APPROXIMATION, this.reqID)
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
                        return ApproximationFunctions.f(V, 2 * this.t, this.t);
                    });
                })
                .thenCompose(v -> {
                    if(round < this.H)
                        if(exchangeStillNecessary(round))
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
        return broadcast(this.endingV, this.H + 1, MessageType.ASYNCH_HALTED, reqID)
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

            vNext = ApproximationFunctions.f(V, 2 * this.t, this.t);
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
                    checkAndComplete(msg.round);
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
                if (!checkAndComplete(msg.round))
                {
                    // add new value
                    this.multisetPerRound.get(msg.round).add(msg.v);
                    // count this message
                    this.metrics.neededMsgs.getAndIncrement();
                    // check again if completed
                    checkAndComplete(msg.round);
                }
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
                    checkAndComplete(msg.round);
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
                if(!checkAndComplete(msg.round))
                {
                    // add new value
                    this.multisetPerRound.get(msg.round).add(msg.v);
                    // count this message
                    this.metrics.neededMsgs.getAndIncrement();
                    // check again if completed
                    checkAndComplete(msg.round);
                }
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
                    checkAndComplete(msg.round);
                }

                // add this new value to every round whose h > round, that has already been inserted, and check if
                // each one has been completed yet.
                this.multisetPerRound.forEach((h, V)->
                {
                    if(h > msg.round && !this.multisetFuturePerRound.get(h).isDone())
                    {
                        V.add(msg.v);
                        checkAndComplete(h);
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
                checkAndComplete(msg.round);
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
        // only consider this message if the address is registered and doesn't correspond to a faulty process
        if(this.groupState.containsKey(q) && !this.groupState.get(q).isFaulty())
        {
            switch (msgType)
            {
                case MessageType.ASYNCH_APPROXIMATION  -> asynchExchange_handleApproximation(msg);
                case MessageType.ASYNCH_INITIALIZATION -> asynchExchange_handleInitialization(msg, q);
                case MessageType.ASYNCH_HALTED         -> asynchExchange_handleTermination(msg, q);
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
            checkAndComplete(round);
        }
    }
    /**
     * Check if round is ready to complete and, if it is, complete it. Assumes it's always called by a thread with
     * exclusive access to multisetPerRound and multisetFuturePerRound
     * @param round Relevant round
     * @return Whether the round was completed or not
     */
    private boolean checkAndComplete(int round)
    {
        // if we finally arrive at list.size == n - t, finish async exchange for round h
        if (this.multisetPerRound.containsKey(round) &&
                this.multisetPerRound.get(round).size() >= this.n - this.t - 1)
        {
            // we collected n-t-1 values but create V with n-t to account for our own vote
            List<Double> VAsList = this.multisetPerRound.get(round);
            double[] V           = new double[VAsList.size() + 1];

            for (int i = 0; i < V.length - 1; i++)
                V[i] = VAsList.get(i);

            // finish asynchExchange at APPROXIMATION round h
            if(this.multisetFuturePerRound.containsKey(round))
                this.multisetFuturePerRound.get(round).complete(V);
            else
                this.multisetFuturePerRound.put(round, CompletableFuture.completedFuture(V));

            return true;
        }
        else return false;
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

        return Broadcast(msg)
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