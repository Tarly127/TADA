package utils.consensus.synchConsensusUtilities;

import org.javatuples.Pair;
import utils.communication.groupConstitution.ProcessStatus;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.address.Address;
import utils.consensus.ids.InstanceID;
import utils.math.BSOFunctions;
import utils.math.Functions;
import utils.consensus.snapshot.ConsensusState;
import utils.consensus.ids.RequestID;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.communication.serializer.MessageSerializer;
import utils.prof.ConsensusMetrics;
import utils.prof.MessageLogger;
import utils.prof.Stopwatch;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import static utils.io.ObjectPrinter.printArray;
import static utils.io.PrettyPrintColours.printRed;


public final class BSOInstance extends ConsensusState implements SynchConsensusInstance<Double>
{
    public final Map<Integer, List<Double>> multisetPerRound;
    public final Map<Integer, CompletableFuture<double[]>> multisetFuturePerRound;
    public final ReentrantLock multisetLock;
    public final Double startingV;
    public final RequestID reqID;
    public final InstanceID instanceID;
    // metrics
    public final ConsensusMetrics metrics;
    public final MessageLogger logger;
    // Instance variables
    private final MessageSerializer<ApproximationMessage> serializer;
    private final long timeout;
    private final TimeUnit unit;
    private final double defaultValue;

    private final int b, s, o;
    public      Double endingV;

    private double delta;

    /* ************ */
    /* Constructors */
    /* ************ */

    public BSOInstance()
    {
        super(null, null, 0, 0, 0.0, 0.0, null);

        this.serializer   = new MessageSerializer<>(ApproximationMessage.class);
        this.timeout      = Long.MAX_VALUE;
        this.unit         = TimeUnit.DAYS;
        this.defaultValue = 0.0;

        this.b = 0;
        this.s = 0;
        this.o = 0;
    }

    public BSOInstance(RequestID reqID,
                                InstanceID instanceID,
                                int n,
                                int t,
                                double epsilon,
                                double startingV,
                                GroupConstitution groupState,
                                long timeout,
                                TimeUnit unit,
                                double defaultValue,
                                int b,
                                int s,
                                int o
                            )
    {
        super(reqID, instanceID, n, t, epsilon, startingV, groupState);

        this.serializer   = new MessageSerializer<>(ApproximationMessage.class);
        this.timeout      = timeout;
        this.unit         = unit;
        this.defaultValue = defaultValue;

        this.b = b;
        this.s = s;
        this.o = o;
    }

    public  <T extends ConsensusState> BSOInstance(T snapshot,
                                                   long timeout,
                                                   TimeUnit unit,
                                                   double defaultValue,
                                                   int b,
                                                   int s,
                                                   int o
                                                    )
    {
        super(snapshot);

        this.serializer   = new MessageSerializer<>(ApproximationMessage.class);
        this.timeout      = timeout;
        this.unit         = unit;
        this.defaultValue = defaultValue;

        this.b = b;
        this.s = s;
        this.o = o;
    }


    /* ********************** */
    /* Main Consensus Methods */
    /* ********************** */

    public CompletableFuture<Double> approximateConsensus_other(ApproximationMessage msg)
    {
        synchExchange_handleInitialization(msg, new Address(msg.reqID.requesterPort));

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
        super.multisetLock.lock();
        // add this round's future and multiset to the map (in case it doesn't exist)
        insertRoundInfo(0);
        super.multisetLock.unlock();

        // broadcast own value at round 0
        return broadcast(super.startingV, 0, MessageType.SYNCH_INITIALIZATION, reqID)
                .thenApply(nothing ->
                {
                    super.multisetLock.lock();
                    var future = super.multisetFuturePerRound.get(0);
                    super.multisetLock.unlock();

                    double[] V;

                    try
                    {
                        V = future.get(this.timeout, this.unit);
                    }
                    catch (InterruptedException | ExecutionException | TimeoutException e)
                    {
                        printRed("Timed out on initialization round");

                        super.multisetLock.lock();

                        // DO NOT add the timed out values yet

                        // get the appropriate double[]
                        V = super.multisetPerRound.get(0).stream().mapToDouble(v -> v).toArray();

                        future.complete(V);

                        super.multisetLock.unlock();
                    }

                    Arrays.sort(V);

                    super.H = Math.max(0, BSOFunctions.BSO_H(V, n, b, s, o, epsilon, true));
                    super.metrics.realH = super.H;
                    super.metrics.expectedH = super.H;

                    long start = Stopwatch.time();
                    var V1 = BSOFunctions.fillWithNull(V, super.startingV, super.n);
                    var V2 = BSOFunctions.replaceExcess(V1, this.o);
                    var V3 = BSOFunctions.reduce(V2, this.b + this.s);
                    var j_m = BSOFunctions.nillInstances(V1);
                    var V4 = BSOFunctions.doubleArray(V3);
                    var V5 = BSOFunctions.removeFirstKNill(V4, j_m);
                    var V6 = BSOFunctions.reduce(V5, this.o - j_m);
                    long end = Stopwatch.time();

                    System.out.println("Approximation round functions exec time: " + (double)(end - start) / 1000000.0 + "ms");

                    return BSOFunctions.mean(BSOFunctions.select(V6, 2 * this.b + o));
                });
    }

    private CompletableFuture<Double> approximationRound  (Double vAtCurrentRound, int round)
    {
        super.multisetLock.lock();
        // insert round info if not already available
        insertRoundInfo(round);
        super.multisetLock.unlock();

        // broadcast own value at round h
        return broadcast(vAtCurrentRound, round, MessageType.SYNCH_APPROXIMATION, super.reqID)
                .thenApply(nothing ->
                {
                    super.multisetLock.lock();
                    var future = super.multisetFuturePerRound.get(round);
                    super.multisetLock.unlock();

                    double[] V;

                    try
                    {
                        V = future.get(this.timeout, this.unit);
                    }
                    catch (InterruptedException | ExecutionException | TimeoutException e)
                    {
                        printRed("Timed out on approximation round " + round + " of " + super.H);

                        super.multisetLock.lock();

                        // DO NOT add the timed out values yet

                        // get the appropriate double[]
                        V = super.multisetPerRound.get(round).stream().mapToDouble(v -> v).toArray();

                        future.complete(V);

                        super.multisetLock.unlock();
                    }

                    return roundCalculations(V, vAtCurrentRound, round);
                })
                .thenCompose(v -> {
                    if (round < super.H)
                        if(exchangeStillNecessary(round))
                        {
                            return approximationRound(v, round + 1);
                        }
                        else
                        {
                            return simpleApproximationRounds(v, round);
                        }
                    else
                    {
                        return CompletableFuture.completedFuture(super.endingV = v);
                    }
                });
    }

    private CompletableFuture<Double> approximationRounds (Double v)
    {
        // call approximation rounds recursively
        return super.H > 0 ? approximationRound(super.startingV, 1) : CompletableFuture.completedFuture(v);
    }

    private CompletableFuture<Double> terminationRound    (Double v)
    {
        return broadcast(super.endingV, super.H + 1, MessageType.SYNCH_HALTED, reqID)
                .thenApply(nothing -> super.endingV);
    }

    /* ********************** */
    /* AsyncExchange Handlers */
    /* ********************** */

    private void synchExchange_handleInitialization(ApproximationMessage msg, Address q)
    {
        super.multisetLock.lock();

        if(msg.round == 0)
        {
            // check if the round's multiset already exists
            if (super.multisetPerRound.containsKey(msg.round))
            {
                // check that it hasn't been completed yet
                if (!super.multisetFuturePerRound.get(msg.round).isDone())
                {
                    // add new value
                    super.multisetPerRound.get(msg.round).add(msg.v);
                    // check if completed
                    checkAndComplete(msg.round);
                }
            }
            else
            {
                // if it doesn't, register it
                super.multisetPerRound      .put(msg.round, new ArrayList<>(super.n - super.t - 1));
                super.multisetFuturePerRound.put(msg.round, new CompletableFuture<>());
                // fill values from processes that halted in rounds previous to this one
                fillValuesForHaltedProcesses(msg.round);
                // check if finished and, if not, add the new value and check again
                if (!checkAndComplete(msg.round))
                {
                    // add new value
                    super.multisetPerRound.get(msg.round).add(msg.v);
                    // check again if completed
                    checkAndComplete(msg.round);
                }
            }
        }
        else
            // if we received an INITIALIZATION message not tagged with round 0, it's a violation of the algorithm
            // and, as such, can be considered faulty behaviour
            super.groupState.get(q).markAsFaulty();

        //System.out.println("Received message (" + MessageType.typeString(msg.type) + ", " + msg.round + ", " + msg
        // .reqID.internalID + ") - Completed? " + checkAndComplete(msg.round));

        super.multisetLock.unlock();
    }
    private void synchExchange_handleApproximation(ApproximationMessage msg, Address q)
    {
        super.multisetLock.lock();

        // don't discard if we don't know how many rounds there'll be OR if we do know and this one is relevant
        if(super.H == null || msg.round <= super.H)
        {
            // check if the round's multiset already exists
            if(super.multisetPerRound.containsKey(msg.round))
            {
                // check that it hasn't been completed yet
                if(!super.multisetFuturePerRound.get(msg.round).isDone())
                {
                    // add new value
                    super.multisetPerRound.get(msg.round).add(msg.v);
                    // check if completed
                    checkAndComplete(msg.round);
                }
            }
            else
            {
                // if it doesn't, register it
                super.multisetPerRound      .put(msg.round, new ArrayList<>(super.n - 1));
                super.multisetFuturePerRound.put(msg.round, new CompletableFuture<>());
                // fill values from processes that halted in rounds previous to this one
                fillValuesForHaltedProcesses(msg.round);
                // check if finished and, if not, add the new value and check again
                if(!checkAndComplete(msg.round))
                {
                    // add new value
                    super.multisetPerRound.get(msg.round).add(msg.v);
                    // check again if completed
                    checkAndComplete(msg.round);
                }
            }
        }

        //System.out.println("Received message (" + MessageType.typeString(msg.type) + ", " + msg.round + ", " + msg
        // .reqID.internalID + ") - Completed? " + checkAndComplete(msg.round));

        super.multisetLock.unlock();
    }
    private void synchExchange_handleTermination(ApproximationMessage msg, Address q)
    {
        super.multisetLock.lock();

        // mark the process as finished
        super.groupState.get(q).complete(msg.v, msg.round);
        // don't discard if we don't know how many rounds there'll be OR if we do know and this one is relevant
        if (super.H == null || msg.round <= super.H)
        {
            // check if the round has already been registered
            if (super.multisetPerRound.containsKey(msg.round))
            {
                // check if the round hasn't been finished yet (if it has, we don't care to handle this message)
                if (!super.multisetFuturePerRound.get(msg.round).isDone())
                {
                    // add the value for said round
                    super.multisetPerRound.get(msg.round).add(msg.v);
                    // check if finished
                    checkAndComplete(msg.round);
                }

                // add this new value to every round whose h > round, that has already been inserted, and check if
                // each one has been completed yet.
                super.multisetPerRound.forEach((h, V)->
                {
                    if(h > msg.round && !super.multisetFuturePerRound.get(h).isDone())
                    {
                        V.add(msg.v);

                        checkAndComplete(h);
                    }
                });
            }
            else
            {
                // add future for said round
                super.multisetPerRound      .put(msg.round, new ArrayList<>(super.n - super.t - 1));
                super.multisetFuturePerRound.put(msg.round, new CompletableFuture<>());
                // fill with values from halted processes (including this one, of course)
                fillValuesForHaltedProcesses(msg.round);
                // check if finished
                checkAndComplete(msg.round);
                // we don't need to make additional checks for rounds (round, H], because they will be eventually added
                // and our own HALTED value will be added to them then
            }
        }

        //System.out.println("Received message (" + MessageType.typeString(msg.type) + ", " + msg.round + ", " + msg
        // .reqID.internalID + ") - Completed? " + checkAndComplete(msg.round));

        super.multisetLock.unlock();
    }
    public  void synchExchange(Byte msgType, ApproximationMessage msg, Address q)
    {
        // only consider this message if the address is registered and doesn't correspond to a faulty process
        if(super.groupState.containsKey(q))
        {
            switch (msgType)
            {
                case MessageType.SYNCH_APPROXIMATION  -> synchExchange_handleApproximation(msg, q);
                case MessageType.SYNCH_INITIALIZATION -> synchExchange_handleInitialization(msg, q);
                case MessageType.SYNCH_HALTED         -> synchExchange_handleTermination(msg, q);
            }
        }
    }

    /* ************************* */
    /* Auxiliary Private Methods */
    /* ************************* */


    /**
     * Insert round info and complete round, if needed
     * @param round relevant round
     * @return whether the round has been completed after insertion or not
     */
    private boolean insertRoundInfo(int round)
    {
        if (!super.multisetPerRound.containsKey(round))
        {
            // if not, add it and the necessary future
            super.multisetPerRound      .put(round, new ArrayList<>(super.n - super.t - 1));
            super.multisetFuturePerRound.put(round, new CompletableFuture<>());

            fillValuesForHaltedProcesses(round);

            // check if completed
            return checkAndComplete(round);
        }
        else return false;
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
        if (super.multisetPerRound.containsKey(round) &&
                super.multisetPerRound.get(round).size() >= this.n - 1)
        {
            // we collected n-t-1 values but create V with n-t to account for our own vote
            List<Double> VAsList = super.multisetPerRound.get(round);
            double[] V           = new double[VAsList.size() + 1];

            for (int i = 0; i < V.length - 1; i++)
                V[i] = VAsList.get(i);

            // finish asynchExchange at APPROXIMATION round h
            if(super.multisetFuturePerRound.containsKey(round))
                super.multisetFuturePerRound.get(round).complete(V);
            else
                super.multisetFuturePerRound.put(round, CompletableFuture.completedFuture(V));

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
        super.groupState.forEach((address, process) ->
        {
            // only fill up to n - t - 1
            if(process.isCompleted() && super.multisetPerRound.get(round).size() < super.n - 1)
            {
                // get v on completion and the completion round for this process
                Pair<Double, Integer> vAndRound = process.getvOnCompletion();
                // if they exist and are of rounds older than or equal to us, use it
                if (vAndRound != null && vAndRound.getValue1() <= round)
                    super.multisetPerRound.get(round).add(vAndRound.getValue0());
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
        var multiset = super.multisetPerRound.get(round);

        // only fill for timed out processes if we didn't receive enough responses
        if(multiset.size() < this.n - 1)
        {
            // add default value for every process
            // also add one for this process to make a double[] that fits
            for(int i = multiset.size(); i < this.n; i++)
                multiset.add(null);
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
        return super.broadcast
                .broadcast(this.serializer.encodeWithHeader(
                                new ApproximationMessage(v, round, type, requestID), type, super.instanceID),
                        super.groupState
                );
    }
    private double roundCalculations(double[] V, double v, int round)
    {
        Arrays.sort(V);

        var V1 = BSOFunctions.fillWithNull(V, v, super.n);
        var V2 = BSOFunctions.replaceExcess(V1, this.o);
        var j_m   = BSOFunctions.nillInstances(V1);
        var V4 = BSOFunctions.doubleArray(BSOFunctions.reduce(V2, this.b + this.s));
        var V6 = BSOFunctions.reduce(BSOFunctions.removeFirstKNill(V4, j_m), this.o - j_m);

        var vNext = BSOFunctions.mean(BSOFunctions.select(V6, 2 * this.b + o));

        if(Functions.InexactDelta(V) < super.epsilon)
        {
            super.H = round;
            super.metrics.realH = super.H;
            super.endingV = vNext;
        }

        return vNext;
    }

    private CompletableFuture<Double> simpleApproximationRounds (Double v, int round)
    {

        double[] othersV = collectHaltedVotes(round);
        double vNext = v;

        for(int h = round; h <= super.H; h++)
        {
            double[] V = Arrays.copyOf(othersV, othersV.length + 1);

            V[V.length - 1] = vNext;

            vNext = roundCalculations(V, vNext, h);
        }

        super.H = round - 1;
        super.metrics.realH = super.H;

        return CompletableFuture.completedFuture(vNext);
    }

    private boolean exchangeStillNecessary(int round)
    {
        return !(super.groupState
                .values()
                .stream()
                .filter(p -> p.isCompleted() && p.getvOnCompletion() != null && p.getvOnCompletion().getValue1() <= round)
                .count() >= super.n - 1);
    }

    private double[] collectHaltedVotes(int round)
    {
        return super.groupState
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
        return super.groupState
                .values()
                .stream()
                .filter(ProcessStatus::isActive)
                .count() <= t;
    }

    public Map<? extends Address, ? extends ProcessStatus> getGroupState()
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
        return super.metrics;
    }
}
