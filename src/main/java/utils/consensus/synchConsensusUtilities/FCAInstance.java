package utils.consensus.synchConsensusUtilities;

import AtomicInterface.communication.address.AddressInterface;
import AtomicInterface.communication.communicationHandler.Broadcast;
import AtomicInterface.communication.groupConstitution.ProcessInterface;
import AtomicInterface.consensus.ConsensusInstance;
import utils.communication.address.Address;
import utils.communication.communicationHandler.Broadcast.AsynchBroadcast;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.groupConstitution.ProcessStatus;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.communication.serializer.MessageSerializer;
import utils.consensus.ids.InstanceID;
import utils.consensus.ids.RequestID;
import utils.consensus.snapshot.ConsensusState;
import utils.measurements.ConsensusMetrics;
import org.javatuples.Pair;
import utils.math.Functions;
import utils.measurements.MessageLogger;
import utils.measurements.Stopwatch;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;


public final class FCAInstance extends ConsensusState implements ConsensusInstance<Double>
{
    public final Map<Integer, List<Double>> multisetPerRound;
    public final Map<Integer, CompletableFuture<double[]>> multisetFuturePerRound;
    public final ReentrantLock multisetLock;
    public final Double startingV;
    public final RequestID reqID;
    public final InstanceID instanceID;
    // metrics
    public final ConsensusMetrics metrics;
    public final MessageLogger    logger;
    // Instance variables
    private final MessageSerializer<ApproximationMessage> serializer;
    private final long timeout;
    private final TimeUnit unit;
    public       Double endingV;

    private double initialDelta;


    /* ************ */
    /* Constructors */
    /* ************ */

    public FCAInstance()
    {
        super(1, 0, 0.0, null, new AsynchBroadcast());

        this.multisetLock = new ReentrantLock(true);
        this.multisetPerRound = new HashMap<>();
        this.multisetFuturePerRound = new HashMap<>();
        this.startingV = 0.0;
        this.reqID = null;
        this.instanceID = null;
        this.metrics = null;
        this.logger = null;

        this.serializer   = new MessageSerializer<>(ApproximationMessage.class);
        this.timeout      = Long.MAX_VALUE;
        this.unit         = TimeUnit.DAYS;
    }

    public FCAInstance(RequestID reqID,
                       InstanceID instanceID,
                       int n,
                       int t,
                       double epsilon,
                       double startingV,
                       GroupConstitution groupState,
                       long timeout,
                       TimeUnit unit,
                       Broadcast broadcast)
    {
        super(n, t, epsilon, groupState, broadcast);

        this.reqID      = reqID;
        this.instanceID = instanceID;
        this.startingV  = startingV;
        this.endingV    = null;
        this.logger     = null;
        this.metrics    = null;

        this.multisetLock           = new ReentrantLock(true);
        this.multisetPerRound       = new HashMap<>();
        this.multisetFuturePerRound = new HashMap<>();

        this.serializer   = new MessageSerializer<>(ApproximationMessage.class);
        this.timeout      = timeout;
        this.unit         = unit;
    }

    public  <T extends ConsensusState> FCAInstance(T snapshot,
                                                            RequestID reqID,
                                                            InstanceID instanceID,
                                                            Double startingV,
                                                            long timeout,
                                                            TimeUnit unit)
    {
        super(snapshot);

        this.reqID      = reqID;
        this.instanceID = instanceID;
        this.startingV  = startingV;
        this.endingV    = null;
        this.logger     = null;
        this.metrics    = new ConsensusMetrics();

        this.multisetLock           = new ReentrantLock(true);
        this.multisetPerRound       = new HashMap<>();
        this.multisetFuturePerRound = new HashMap<>();

        this.serializer   = new MessageSerializer<>(ApproximationMessage.class);
        this.timeout      = timeout;
        this.unit         = unit;
    }


    /* ********************** */
    /* Main Consensus Methods */
    /* ********************** */

    public CompletableFuture<Double> approximateConsensus_other(ApproximationMessage msg)
    {
        // increase the number of messages processed for this instance of consensus
        this.metrics.processedMsgs.getAndIncrement();
        synchExchange_handleInitialization(msg, new Address(msg.reqID.requesterHash));

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
        return broadcast(this.startingV, 0, MessageType.FCA_INITIALIZATION, reqID)
                .thenApply(nothing ->
                {
                    boolean timedOut = false;

                    this.multisetLock.lock();
                    var future = this.multisetFuturePerRound.get(0);
                    this.multisetLock.unlock();

                    double[] V;

                    try
                    {
                        V = future.get(this.timeout, this.unit);
                    }
                    catch (InterruptedException | ExecutionException | TimeoutException e)
                    {
                        this.metrics.timedOutRounds ++;

                        timedOut = true;

                        this.multisetLock.lock();

                        // add our own vote to the multiset
                        this.multisetPerRound.get(0).add(this.startingV);
                        // if there was a timeout (or otherwise) error, fill values for halted processes
                        fillValuesForTimedOutConnections(0);
                        // check if future is complete
                        checkAndComplete(0);
                        // get the appropriate double[]
                        V = this.multisetPerRound.get(0).stream().mapToDouble(v -> v).toArray();

                        future.complete(V);

                        this.multisetLock.unlock();
                    }
                    // add own vote at round h to V (if we didn't already, in the case of a timeout)
                    if(!timedOut) V[V.length - 1] = startingV;
                    // calculate starting delta (upper bound for "initial precision")
                    this.initialDelta = Functions.InexactDelta(V);
                    // calculate H
                    this.H = Math.max(0, Functions.InexactH(this.initialDelta, this.epsilon));

                    this.metrics.expectedH = this.H;
                    this.metrics.realH     = this.H;

                    // v <- f_t,t(V)
                    return Functions.mean(V);
                });
    }

    private CompletableFuture<Double> approximationRound  (Double vAtCurrentRound, int round)
    {
        this.multisetLock.lock();
        // insert round info if not already available
        insertRoundInfo(round);
        this.multisetLock.unlock();

        // broadcast own value at round h
        return broadcast(vAtCurrentRound, round, MessageType.FCA_APPROXIMATION, this.reqID)
                .thenApply(nothing ->
                {
                    boolean timedOut = false;

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
                        timedOut = true;

                        this.multisetLock.lock();
                        // add our own vote to the round's multiset, prematurely
                        this.multisetPerRound.get(round).add(vAtCurrentRound);
                        // if there was a timeout (or otherwise) error, fill values for halted processes
                        fillValuesForTimedOutConnections(round);
                        // check if future is complete
                        checkAndComplete(round);
                        // get the appropriate double[]
                        V = this.multisetPerRound.get(round).stream().mapToDouble(v -> v).toArray();
                        // complete the future
                        future.complete(V);

                        this.multisetLock.unlock();
                    }
                    // add own vote at round h to V (if we didn't already)
                    // if we timed out this round, then these calculations have already been performed, and we don't
                    // have to repeat them
                    if(!timedOut)
                    {
                        V[V.length - 1] = vAtCurrentRound;
                        // A <- Acceptable(V)
                        double[] A = Functions.Acceptable(V, this.initialDelta, this.n, this.t);
                        // e_p <- e(a)
                        double e = Functions.estimator(A);
                        // swap unacceptable values for new estimate
                        if (A.length != V.length)
                        {
                            double[] tmp = new double[this.n];

                            for (int i = 0; i < this.n; i++)
                                tmp[i] = i < A.length ? A[i] : e;

                            V = tmp; // after this, V should be A + { e forall i where i in |V| - |A|}
                        }
                    }
                    // v <- f_t,t(V)
                    return Functions.mean(V);
                })
                .thenCompose(v -> {
                    if (round < this.H)
                        if(exchangeStillNecessary(round))
                            return approximationRound(v, round + 1);
                        else
                            return simpleApproximationRounds(v, round);
                    else
                        return CompletableFuture.completedFuture(this.endingV = v);
                });


    }

    private CompletableFuture<Double> approximationRounds (Double v)
    {
        // call approximation rounds recursively
        return this.H > 0 ? approximationRound(v, 1) : CompletableFuture.completedFuture(this.endingV = v);
    }

    private CompletableFuture<Double> terminationRound    (Double v)
    {
        return broadcast(this.endingV, this.H + 1, MessageType.FCA_HALTED, reqID)
                .thenApply(nothing -> this.endingV);
    }

    /* ********************** */
    /* AsyncExchange Handlers */
    /* ********************** */

    private void synchExchange_handleInitialization(ApproximationMessage msg, AddressInterface q)
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
    private void synchExchange_handleApproximation(ApproximationMessage msg)
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
                this.multisetPerRound      .put(msg.round, new ArrayList<>(this.n  - 1));
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
        // count this message toward the total of processed messages, regardless of whether the message was actually
        // used or not
        this.metrics.processedMsgs.getAndIncrement();
        // only consider this message if the address is registered and doesn't correspond to a faulty process
        if(this.groupState.containsKey(q))
        {
            switch (msgType)
            {
                case MessageType.FCA_APPROXIMATION  -> synchExchange_handleApproximation(msg);
                case MessageType.FCA_INITIALIZATION -> synchExchange_handleInitialization(msg, q);
                case MessageType.FCA_HALTED         -> synchExchange_handleTermination(msg, q);
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
                this.multisetPerRound.get(round).size() >= this.n - 1)
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
     * expected time frame. ASSUMES THE MULTISET FOR THE CURRENT ROUND ALREADY CONTAINS OUR OWN VOTE
     * @param round Current round
     */
    private void fillValuesForTimedOutConnections(int round)
    {
        var multiset = this.multisetPerRound.get(round);

        // only fill for timed out processes if we didn't receive enough responses
        if(multiset.size() < this.n)
        {
            // list of acceptable values
            List<Double> acceptable;

            if(round != 0)
                // calculate the set of acceptable values
                acceptable = Functions.Acceptable(multiset, this.initialDelta, this.n, this.t);
            else
                // if it's the first round then every value received will be in the acceptable set!
                acceptable = new ArrayList<>(multiset);

            // generate the list consisting of n - acceptable instances of e(acceptable)
            var eValuesSet = Collections.nCopies(this.n - acceptable.size(), Functions.estimator(acceptable));
            // set the round multiset to the union of acceptable and eValuesSet
            multiset.clear();
            multiset.addAll(acceptable);
            multiset.addAll(eValuesSet);
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

        return this.broadcast
                .broadcast(this.serializer.encodeWithHeader(msg,type,this.instanceID), this.groupState)
                .thenApply(nothing -> {
                    if(logger != null)
                        logger.registerMetric(msg, wallTime, Stopwatch.time() - startTime,
                                MessageLogger.MessageEvent.SEND);
                    return nothing;
                });
    }

    private CompletableFuture<Double> simpleApproximationRounds (Double v, int round)
    {
        double[] othersV = collectHaltedVotes(round);
        double vNext = v;

        for(int h = round; h <= this.H; h++)
        {
            double[] V = Arrays.copyOf(othersV, othersV.length + 1);

            // add own vote at round h to V
            V[V.length - 1] = vNext;
            // A <- Acceptable(V)
            double[] A = Functions.Acceptable(V, this.initialDelta, this.n, this.t);
            // e_p <- e(a)
            double e = Functions.estimator(A);
            // swap unacceptable values for new estimate
            if (A.length != V.length)
            {
                double[] tmp = new double[V.length];

                for (int i = 0; i < V.length; i++)
                    tmp[i] = i < A.length ? A[i] : e;

                V = tmp;
            }
            // v <- mean(V)
            vNext = Functions.mean(V);
        }

        this.H = round - 1;

        this.metrics.realH = this.H;

        return CompletableFuture.completedFuture(vNext);
    }

    private boolean exchangeStillNecessary(int round)
    {
        return !(this.groupState
                .values()
                .stream()
                .filter(p -> p.isCompleted() && p.getvOnCompletion() != null && p.getvOnCompletion().getValue1() <= round)
                .count() >= this.n - 1);
    }

    private double[] collectHaltedVotes(int round)
    {
        return this.groupState
                .values()
                .stream()
                .filter(p -> p.isCompleted() && p.getvOnCompletion() != null && p.getvOnCompletion().getValue1() <= round)
                .mapToDouble(p -> p.getvOnCompletion().getValue0())
                .toArray();
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

    public Map<? extends AddressInterface, ? extends ProcessInterface> getGroupState()
    {
        return this.groupState;
    }

    public RequestID getReqID()
    {
        return this.reqID;
    }

    @Override
    public ConsensusMetrics getMetrics()
    {
        return this.metrics;
    }
}