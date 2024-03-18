package utils.consensus.synchConsensusUtilities;

import org.javatuples.Pair;
import utils.communication.groupConstitution.ProcessStatus;
import utils.communication.groupConstitution.Process;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.address.Address;
import utils.consensus.ids.InstanceID;
import utils.math.Functions;
import utils.consensus.snapshot.ConsensusState;
import utils.consensus.ids.RequestID;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.communication.serializer.MessageSerializer;
import utils.prof.ConsensusMetrics;
import utils.prof.MessageLogger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static utils.io.ObjectPrinter.printList;
import static utils.io.PrettyPrintColours.*;


// THIS IS NOT WORKING, AND HAS BEEN DEPRECATED!!!!


public final class CCAInstance extends ConsensusState implements SynchConsensusInstance<Double>
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
    private final Address myAddress;
    // Inexact Agreement variables
    private final Map<Integer, Map<Address, List<Double>>> crusaderMultisets; // should be protected by multisetLock
    private final Map<Integer, Integer> purifiedVotes;
    public      Double endingV;
    private double delta;
    private final BroadcastQueue broadcastQueue;


    private final class BroadcastQueue
    {
        private final LinkedList<Packet> queue;
        private final ReentrantLock queueLock;
        private final Condition notEmpty;
        private final ExecutorService broadcaster;
        private final Map<? extends Address, ? extends Process> groupCon;
        private static final class Packet
        {
            byte[] payload;
            CompletableFuture<Void> result;
        }

        public BroadcastQueue(Map<? extends Address, ? extends Process> groupCon)
        {
            this.queue     = new LinkedList<>();
            this.queueLock = new ReentrantLock(true);
            this.notEmpty  = this.queueLock.newCondition();
            this.broadcaster = Executors.newFixedThreadPool(1, Executors.defaultThreadFactory());
            this.groupCon = groupCon;

            this.broadcaster.submit(this::dequeue);
        }

        public CompletableFuture<Void> enqueue(byte[] payload)
        {
            Packet packet = new Packet();

            packet.payload = payload;
            packet.result  = new CompletableFuture<>();

            this.queueLock.lock();

            this.queue.offer(packet);

            if(this.queue.size() == 1)
                this.notEmpty.signalAll();

            this.queueLock.unlock();

            return packet.result;
        }

        private void dequeue()
        {
            this.queueLock.lock();

            try
            {
                if(this.queue.size() == 0)
                    this.notEmpty.await();
            }
            catch (InterruptedException e)
            {
                this.queueLock.unlock();
                e.printStackTrace();
            }

            Packet packet = this.queue.remove(0);

            this.queueLock.unlock();

            if(packet != null)
            {
                try
                {
                    broadcast.broadcast(packet.payload, this.groupCon)
                            .thenAccept(nothing -> packet.result.complete(nothing))
                            .get();

                    this.broadcaster.submit(this::dequeue);
                }
                catch (InterruptedException | ExecutionException e)
                {
                    e.printStackTrace();

                    this.broadcaster.submit(this::dequeue);
                }
            }
            else
            {
                this.broadcaster.submit(this::dequeue);
            }
        }
    }

    /* ************ */
    /* Constructors */
    /* ************ */

    public CCAInstance()
    {
        super(null, null, 0, 0, 0.0, 0.0, null);

        this.serializer             = new MessageSerializer<>(ApproximationMessage.class);
        this.timeout                = Long.MAX_VALUE;
        this.unit                   = TimeUnit.DAYS;
        this.defaultValue           = 0.0;
        this.crusaderMultisets      = new HashMap<>();
        this.purifiedVotes = new HashMap<>();
        this.myAddress              = new Address();

        this.broadcastQueue = null;
    }

    public CCAInstance(RequestID reqID,
                                int n,
                                int t,
                                double epsilon,
                                double startingV,
                                GroupConstitution groupState,
                                long timeout,
                                TimeUnit unit,
                                double defaultValue,
                                Address myAddress)
    {
        super(reqID, null, n, t, epsilon, startingV, groupState);

        this.serializer             = new MessageSerializer<>(ApproximationMessage.class);
        this.timeout                = timeout;
        this.unit                   = unit;
        this.defaultValue           = defaultValue;
        this.crusaderMultisets      = new HashMap<>();
        this.purifiedVotes = new HashMap<>();
        this.myAddress              = myAddress;

        this.broadcastQueue = new BroadcastQueue(groupState);
    }

    public  <T extends ConsensusState> CCAInstance(T snapshot,
                                                   long timeout,
                                                   TimeUnit unit,
                                                   double defaultValue,
                                                   Address myAddress)
    {
        super(snapshot);

        this.serializer             = new MessageSerializer<>(ApproximationMessage.class);
        this.timeout                = timeout;
        this.unit                   = unit;
        this.defaultValue           = defaultValue;
        this.crusaderMultisets      = new HashMap<>();
        this.purifiedVotes = new HashMap<>();
        this.myAddress              = myAddress;

        this.broadcastQueue = new BroadcastQueue(snapshot.groupState);
    }


    /* ********************** */
    /* Main Consensus Methods */
    /* ********************** */

    public CompletableFuture<Double> approximateConsensus_other(ApproximationMessage msg)
    {
        crusaderExchange_init_and_approx(msg, new Address(msg.reqID.requesterPort));

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
                        Thread.sleep(3000);

                        V = future.get(this.timeout, this.unit);
                    }
                    catch (InterruptedException | ExecutionException | TimeoutException e)
                    {
                        printRed("Timed out on initialization round");

                        super.multisetLock.lock();
                        // if there was a timeout (or otherwise) error, fill values for halted processes
                        fillValuesForTimedOutConnections(0);
                        // check if future is complete
                        checkAndComplete(0);
                        // get the appropriate double[]
                        V = super.multisetPerRound.get(0).stream().mapToDouble(v -> v).toArray();
                        // complete round future
                        future.complete(V);
                        super.multisetLock.unlock();
                    }

                    System.out.println("Got V");

                    // add own vote at round h to V
                    V[V.length - 1] = startingV;
                    // calculate starting delta ("precision")
                    this.delta = Functions.InexactDelta(V);
                    // calculate H
                    super.H = Functions.InexactH(this.delta, super.epsilon);

                    System.out.println("H = " + super.H);

                    // A <- Acceptable(V)
                    double[] A = Functions.Acceptable(V, this.delta, super.n, super.t);
                    // e_p <- e(a)
                    double e = Functions.estimator(A);
                    // swap unacceptable values for new estimate
                    if (A.length != V.length)
                    {
                        double[] tmp = new double[V.length];

                        for (int i = 0; i < V.length; i++)
                            tmp[i] = i < A.length ? A[i] : e;

                        V = tmp; // after this, V should be A + { e for all i where i in |V| - |A|}
                    }
                    // v <- f_t,t(V)
                    return Functions.mean(V);
                });
    }

    private CompletableFuture<Double> approximationRound  (Double vAtCurrentRound, int round)
    {
        super.multisetLock.lock();
        // insert round info if not already available
        insertRoundInfo(round);
        super.multisetLock.unlock();

        // broadcast own value at round h
        CompletableFuture<Double> broadcastAndLogic =
                broadcast(vAtCurrentRound, round, MessageType.SYNCH_APPROXIMATION, super.reqID)
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

                        // if there was a timeout (or otherwise) error, fill values for halted processes
                        fillValuesForTimedOutConnections(round);
                        // check if future is complete
                        checkAndComplete(round);
                        // get the appropriate double[]
                        V = super.multisetPerRound.get(round).stream().mapToDouble(v -> v).toArray();
                        // complete future
                        future.complete(V);

                        super.multisetLock.unlock();
                    }
                    // add own vote at round h to V
                    V[V.length - 1] = startingV;
                    // A <- Acceptable(V)
                    double[] A = Functions.Acceptable(V, this.delta, super.n, super.t);
                    // e_p <- e(a)
                    double e = Functions.estimator(A);
                    // swap unacceptable values for new estimate
                    if(A.length != V.length)
                    {
                        double[] tmp = new double[V.length];

                        for (int i = 0; i < V.length; i++)
                            tmp[i] = i < A.length ? A[i] : e;

                        V = tmp; // after this, V should be A + { e for all i where i in |V| - |A|}
                    }
                    // v <- f_t,t(V)
                    return Functions.mean(V);
                });

        if(round < super.H)
            return broadcastAndLogic.thenCompose(v -> approximationRound(v, round + 1));
        else
            return broadcastAndLogic.thenApply(v -> super.endingV = v);
    }

    private CompletableFuture<Double> approximationRounds (Double v)
    {
        // call approximation rounds recursively
        return approximationRound(super.startingV, 1);
    }

    private CompletableFuture<Double> terminationRound    (Double v)
    {
        return broadcast(super.endingV, super.H + 1, MessageType.SYNCH_HALTED, reqID)
                .thenApply(nothing -> super.endingV);
    }

    /* ********************** */
    /* SynchExchange Handlers */
    /* ********************** */

    private void crusaderExchange_init_and_approx(ApproximationMessage msg, Address q)
    {
        super.multisetLock.lock();

        //System.out.println("Got lock (init/approx)");

        // msg.sender == null
        if (msg.round == 0 || super.H == null || super.H >= msg.round)
        {
            if (super.multisetPerRound.containsKey(msg.round))
            {
                if (!super.multisetFuturePerRound.get(msg.round).isDone())
                {
                    // insert the value in the multiset
                    insertToCrusaderMultiset(q, msg.round, msg.v);
                    // check and purify
                    if (checkAndPurify(msg.round, q))
                        checkAndComplete(msg.round);
                }
            }
            else
            {
                // insert the round info
                insertRoundInfo(msg.round);

                if(!checkAndPurify(msg.round, q))
                {
                    // insert the value in the multiset
                    insertToCrusaderMultiset(q, msg.round, msg.v);
                    // check and purify
                    if (checkAndPurify(msg.round, q))
                        checkAndComplete(msg.round);
                }
            }
        }

        this.multisetLock.unlock();

        // Retransmit
        broadcast(msg.v, msg.round, this.reqID, msg.sender);

        //System.out.println("finis (init/approx)");
    }

    private void crusaderExchange_retransmission(ApproximationMessage msg, Address q)
    {
        super.multisetLock.lock();

        //System.out.println("Got lock (retransmission)");

        // ignore if retransmitting our own vote
        if(!msg.sender.equals(this.myAddress))
        {
            if (msg.round == 0 || super.H == null || super.H >= msg.round)
            {

                if (super.multisetPerRound.containsKey(msg.round))
                {
                    if (!super.multisetFuturePerRound.get(msg.round).isDone())
                    {
                        // insert the value in the multiset
                        insertToCrusaderMultiset(msg.sender, msg.round, msg.v);
                        // check and purify
                        if (checkAndPurify(msg.round, msg.sender))
                            checkAndComplete(msg.round);
                    }
                }
                else
                {
                    // insert the round info
                    insertRoundInfo(msg.round);

                    if(!checkAndPurify(msg.round, msg.sender))
                    {
                        // insert the value in the multiset
                        insertToCrusaderMultiset(msg.sender, msg.round, msg.v);
                        // check and purify
                        if (checkAndPurify(msg.round, msg.sender))
                            checkAndComplete(msg.round);
                    }
                }
            }
        }
        this.multisetLock.unlock();

        //System.out.println("finis (retransmission)");
    }

    public void crusaderExchange_halted(ApproximationMessage msg, Address q)
    {
        // mark the process as being in its completion round, but only update its vote after purifying
        super.groupState.get(q).markAsCompleting(msg.round);
        // the rest of the process is exactly the same as with INITIALIZATION and APPROXIMATION messages
        crusaderExchange_init_and_approx(msg, q);
    }

    private void crusaderExchange(ApproximationMessage msg, Address q)
    {
        //System.out.println("(" + msg.round + ") Received message of type " + MessageType.typeString(msg.type) + " " +
        //        "from " + q.getPort() +
        //        (msg.type == MessageType.CRUSADER_RETRANSMISSION ? " retransmitting " + msg.sender.getPort() : ""));

        switch (msg.type)
        {
            case MessageType.SYNCH_INITIALIZATION:
            case MessageType.SYNCH_APPROXIMATION:
                crusaderExchange_init_and_approx(msg, q);
                break;
            case MessageType.SYNCH_HALTED:
                crusaderExchange_halted(msg, q);
                break;
            case MessageType.CRUSADER_RETRANSMISSION:
                crusaderExchange_retransmission(msg, q);
        }
    }

    public  void synchExchange(Byte msgType, ApproximationMessage msg, Address q)
    {
        // only consider this message if the address is registered and doesn't correspond to a faulty process
        if(super.groupState.containsKey(q))
            crusaderExchange(msg, q);
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
        if (!super.multisetPerRound.containsKey(round))
        {
            // if not, add it and the necessary future
            super.multisetPerRound.put(round, new ArrayList<>(super.n - 1));
            super.multisetFuturePerRound.put(round, new CompletableFuture<>());

            this.crusaderMultisets.put(round, new HashMap<>(this.n - 1));

            this.purifiedVotes.put(round, 1);

            for (Address address : super.groupState.keySet())
                if (!address.equals(this.myAddress))
                    this.crusaderMultisets.get(round).put(address, new ArrayList<>(this.n - 1));

            fillValuesForHaltedProcesses(round);

            // check if completed
            checkAndComplete(round);
        }
    }
    /**
     * Check if round is ready to complete and, if it is, complete it. Assumes it's always called by a thread with
     * exclusive access to multisetPerRound and multisetFuturePerRound
     * @param round Relevant round
     */
    private void checkAndComplete(int round)
    {
        // if we finally arrive at list.size == n - t, finish async exchange for round h
        if (this.purifiedVotes.containsKey(round) && this.purifiedVotes.get(round) >= this.n)
        {
            System.out.println("Received enough messages!!! (checkAndcomplete)");

            // we collected n-1 values but create V with n to account for our own vote
            List<Double> VAsList = super.multisetPerRound.get(round);
            double[] V           = new double[VAsList.size() + 1];

            printList(VAsList);

            for (int i = 0; i < V.length - 1; i++)
                V[i] = VAsList.get(i);

            // finish Crusader Exchange at APPROXIMATION round h
            if(super.multisetFuturePerRound.containsKey(round))
                super.multisetFuturePerRound.get(round).complete(V);
            else
                super.multisetFuturePerRound.put(round, CompletableFuture.completedFuture(V));

            System.out.println("Finished crusader exchange");
        }
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
                {
                    super.multisetPerRound.get(round).add(vAndRound.getValue0());
                    incrementPurified(round);
                }
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
            {
                multiset.add(this.defaultValue);
            }
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
        return this.broadcastQueue.enqueue(this.serializer.encodeWithHeader(
                                new ApproximationMessage(v, round, type, requestID, this.myAddress), type));
    }
    private void broadcast(double v, int round, RequestID requestID, Address sender)
    {
        byte[] payload = this.serializer.encodeWithHeader(
                new ApproximationMessage(v, round, MessageType.CRUSADER_RETRANSMISSION, requestID, sender),
                MessageType.CRUSADER_RETRANSMISSION);

        this.broadcastQueue.enqueue(payload);
    }



    /* ************************************ */
    /* Crusader Agreement Auxiliary Methods */
    /* ************************************ */

    /**
     * Check if we are ready to purify a multiset of values received from every process after retransmitting process
     * q's vote
     * @param round relevant round
     * @param q relevant process
     * @return whether the set was purified or not
     */
    private boolean checkAndPurify(int round, Address q)
    {
        // check if we received enough values (that is to say, the original, plus all retransmissions, except our own)
        if(this.crusaderMultisets.containsKey(round)
                && this.crusaderMultisets.get(round).containsKey(q)
                && this.crusaderMultisets.get(round).get(q).size() == this.n - 1)
        {
            var purified = purify(round, q);
            // get the purified multiset and add the value(s) to the round's multiset
            super.multisetPerRound.get(round).addAll(purified);
            // if the process was in its last round of the algorithm, complete it
            if(super.groupState.get(q).isCompleting())
            {
                super.groupState.get(q).completePurified(purified);
                // if relevant, add the purified set to all subsequent rounds
                if(super.H != null)
                    for(int h = round; h <= H; h++)
                    {
                        // only do this for rounds with information already registered, as other rounds will update
                        // later
                        if(super.multisetPerRound.containsKey(h) && !super.multisetFuturePerRound.get(h).isDone())
                        {
                            // add the purified multiset to the round's multiset
                            super.multisetPerRound.get(h).addAll(purified);
                            // increment the number of votes received for said round
                            incrementPurified(h);
                            // check if round is completed
                            checkAndComplete(h);
                        }
                    }

            }
            // update number of processes from which we received the value for q
            incrementPurified(round);
            return true;
        }
        else return false;
    }

    private void insertToCrusaderMultiset(Address address, int round, double v)
    {
        // only add if we don't know H or round is lesser than or equal to H
        if (!this.crusaderMultisets.containsKey(round))
        {
            // since no entry for this round exists, better add all entries for the round
            Map<Address, List<Double>> roundEntry = new HashMap<>(this.n);

            for (Address addr : super.groupState.keySet())
                if(!addr.equals(this.myAddress))
                    roundEntry.put(addr, new ArrayList<>(this.n - 1));
            // add this value to the process's multiset
            roundEntry.get(address).add(v);
            // add all the processes' multisets to crusaderMultisets
            this.crusaderMultisets.put(round, roundEntry);
        }
        else
        {
            if (this.crusaderMultisets.get(round).containsKey(address))
                this.crusaderMultisets.get(round).get(address).add(v);
            else if(!address.equals(this.myAddress))
            {
                this.crusaderMultisets.get(round).put(address, new ArrayList<>(this.n - 1));
                this.crusaderMultisets.get(round).get(address).add(v);
            }
        }
    }

    /**
     * Purify the multiset of responses for round h for the process with the given address
     * @param round Relevant round
     * @param address Relevant process's address
     * @return purified list
     */
    private List<Double> purify(int round, Address address)
    {
        if(this.crusaderMultisets.containsKey(round)
                && this.crusaderMultisets.get(round).containsKey(address))
        {
            var unpurified = this.crusaderMultisets.get(round).get(address);

            Map<Double, Long> frequencies = new HashMap<>();

            // calculate frequencies of all values received
            for(double v : unpurified)
                if(!frequencies.containsKey(v))
                    frequencies.put(v, unpurified.stream().filter(d -> d == v).count());

            // get all messages that aren't suspect
            return frequencies
                    .keySet()
                    .stream()
                    .filter(v -> frequencies.get(v) > t)
                    .distinct()
                    .collect(Collectors.toUnmodifiableList());
        }
        else return new ArrayList<>();
    }

    private void incrementPurified(int round)
    {
        // increment the value if it is present
        if(this.purifiedVotes.containsKey(round))
            this.purifiedVotes.replace(round, this.purifiedVotes.get(round) + 1);
        // insert it at 1 if not
        else
            this.purifiedVotes.put(round, 2);
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

    private void showCrusaderMultisetsAndReceivedVotes(int round)
    {
        this.crusaderMultisets.getOrDefault(round, new HashMap<>()).forEach((address, multiset) -> {
            printGreen(address.getPort() + ": ", ""); printList(multiset);
        });
         printGreen("Multiset: ", ""); printList(super.multisetPerRound.getOrDefault(round, new ArrayList<>()));
    }

    @Override
    public ConsensusMetrics getMetrics()
    {
        return null;
    }
}