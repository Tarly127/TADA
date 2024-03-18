package test.consensus;

import Interface.consensus.utils.ApproximateConsensusHandler;
import core.AtomicApproximateDoubleTemplate;
import core.Processor;
import test.other.TestAux;
import test.other.TestConsts;
import utils.communication.message.ApproximationMessage;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.consensus.snapshot.ConsensusState;
import utils.math.ApproximationFunctions;
import utils.prof.Stopwatch;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static utils.io.ObjectPrinter.printArray;

class FCAClockSynchAttachment
{
    // these results can only be obtained through experimentation
    // all values here SHOULD in nanoseconds

    public static final double lambda   = 1.0e5; // valor mínimo para o delay na entrega de uma mensagem
    public static final double gamma    = 5.0e5; // tempo esperado de processamento de mensagem (mediana ou máximo)
    public static final double epsilon  = 2.0e5; // delta entre os valores máximos e mínimos de delay na entrega de
                                                // mensagens ( delay in [lambda, lambda + epsilon] )
    public static final double rho      = 0.0;   // temporarily
    public static final double L        = 1.0;   // temporarily
    public static final double R        = 1e9;   // meaning every 3s we re-synchronize clocks
    public static final double E        = epsilon + ( (2.0 * L * rho) / (2.0 - rho) );
    public static final long   timeout  = (long)(2.0 * (lambda + epsilon) + gamma);

}

class ClockSynchFCA implements ApproximateConsensusHandler<FCAClockSynchAttachment>
{

    private double delta;

    private double clock() {return (double)System.nanoTime();}

    @Override
    public Double onNewConsensus(ConsensusState cs, Double latestVote, FCAClockSynchAttachment ca)
    {
        // while the target of consensus is a correction factor for our clock, we must exchange on the first round
        // our actual perceived clock and not our correction factor
        return clock();
    }

    @Override
    public int rounds(ConsensusState cs, double[] V0, FCAClockSynchAttachment ca)
    {
        return Math.max(0, ApproximationFunctions.InexactH(ApproximationFunctions.InexactDelta(V0), cs.epsilon));
        //return 0; // only needs the init round, but can be changed to achieve target precision
    }

    @Override
    public Double onReceive(ConsensusState cs, ApproximationMessage msg, FCAClockSynchAttachment ca)
    {
        // delta_qp = c_p(now) - lambda - c_qp
        return clock() - FCAClockSynchAttachment.lambda - msg.v;
    }

    @Override
    public double approximationRound(ConsensusState cs, double[] V, double v, int round, FCAClockSynchAttachment ca)
    {
        // basically the FCA approximation round
        if(round == 0) this.delta = ApproximationFunctions.sortedDelta(V);

        // our vote could be very outdated, replace with more current one
        for(int i = 0; i < V.length; i++)
                V[i] = v == V[i] ? clock() : clock() - V[i];

        if(V.length != cs.n)
        {
            double[] tmp = new double[cs.n];

            for(int i = 0; i < cs.n; i++)
                tmp[i] = i < V.length ? V[i] : Double.MAX_VALUE;

            V = tmp;
        }

        double[] Acceptable = ApproximationFunctions.Acceptable(V, delta, cs.n, cs.t);

        double est = ApproximationFunctions.estimator(Acceptable);

        if(Acceptable.length != cs.n)
        {
            double[] tmp = new double[cs.n];

            for(int i = 0; i < cs.n; i++)
                tmp[i] = i < Acceptable.length ? Acceptable[i] : est;

            V = tmp;
        }

        var v_h = ApproximationFunctions.mean(V);

        //System.out.println(round + " : " + v_h);

        return v_h;
    }

    @Override
    public boolean endExchangeCondition(ConsensusState cs, double[] multiset, int round, FCAClockSynchAttachment ca)
    {
        return multiset.length >= cs.n - 1;
    }
}

class FaultyClockSynchFCA implements ApproximateConsensusHandler<FCAClockSynchAttachment>
{

    private double delta;
    private boolean minus = false;

    private double clock() {return (double)System.nanoTime();}

    @Override
    public Double onNewConsensus(ConsensusState cs, Double latestVote, FCAClockSynchAttachment ca)
    {
        // while the target of consensus is a correction factor for our clock, we must exchange on the first round
        // our actual perceived clock and not our correction factor
        return clock();
    }

    @Override
    public int rounds(ConsensusState cs, double[] V0, FCAClockSynchAttachment ca)
    {
        return Math.max(0, ApproximationFunctions.InexactH(ApproximationFunctions.InexactDelta(V0), cs.epsilon));
        //return 0; // only needs the init round, but can be changed to achieve target precision
    }

    @Override
    public Double onReceive(ConsensusState cs, ApproximationMessage msg, FCAClockSynchAttachment ca)
    {
        // delta_qp = c_p(now) - lambda - c_qp
        return clock() - FCAClockSynchAttachment.lambda - msg.v;
    }

    @Override
    public double approximationRound(ConsensusState cs, double[] V, double v, int round, FCAClockSynchAttachment ca)
    {
        // basically the FCA approximation round
        if(round == 0) {this.delta = ApproximationFunctions.sortedDelta(V);}

        // our vote could be very outdated, replace with more current one
        for(int i = 0; i < V.length; i++)
        {
            if (V[i] == v)
                V[i] = clock();
            else
                V[i] = clock() - V[i];
        }


        // fill values for timed out connections as inf
        // do not fill with default values because we need delta with only the possibly correct processes
        if(V.length != cs.n)
        {
            double[] tmp = new double[cs.n];

            for(int i = 0; i < cs.n; i++)
                tmp[i] = i < V.length ? V[i] : Double.MAX_VALUE;

            V = tmp;
        }

        double[] Acceptable = ApproximationFunctions.Acceptable(V, delta, cs.n, cs.t);

        double est = ApproximationFunctions.estimator(Acceptable);

        if(Acceptable.length != cs.n)
        {
            double[] tmp = new double[cs.n];

            for(int i = 0; i < cs.n; i++)
                tmp[i] = i < Acceptable.length ? Acceptable[i] : est;

            V = tmp;
        }

        var v_next =  ApproximationFunctions.mean(V);

        //System.out.println("v = " + (v_next/1e9) + "s");

        this.minus = !this.minus;

        //return v_next;

        return round == cs.H ? v_next : v_next + 10e9 * (this.minus ? -1 : 1);
    }

    @Override
    public boolean endExchangeCondition(ConsensusState cs, double[] multiset, int round, FCAClockSynchAttachment ca)
    {
        return multiset.length >= cs.n - 1;
    }
}

class ApproximateClock
{
    private static final double precision = 5e5; // máximo de 0,5 milissegundos de diferença no relógio de cada processo

    private final Processor                                                processor;
    private final AtomicApproximateDoubleTemplate<FCAClockSynchAttachment> clockCorrector;
    private      long                                                     clockValue;
    private final ReentrantLock                                            correctionFactorLock;
    private      long                                                     lastClockUpdateTime;

    // metrics
    private final List<Double>        texecs;
    private final List<Double>        realTexecs;
    private final List<LocalDateTime> wallTimes;

    private final String myPort;

    private boolean multiplier = false;

    ApproximateClock(String brokerPort, String myPort, int N) throws IOException
    {
        this.clockValue = 0L;
        this.correctionFactorLock  = new ReentrantLock(true);
        this.processor             = new Processor(myPort, brokerPort, N);
        this.myPort                = myPort;
        this.texecs                = new ArrayList<>();
        this.wallTimes             = new ArrayList<>();
        this.realTexecs            = new ArrayList<>();
        this.clockCorrector        = this.processor.newAtomicApproximateDouble("clock",
                precision,
                (double)(Stopwatch.time()), // initially the correction factor is 0
                FCAClockSynchAttachment.timeout,
                TimeUnit.MILLISECONDS,
                !this.processor.isLeader() ? new ClockSynchFCA() : new FaultyClockSynchFCA());

        // FCA doesn't use a default value
        this.clockCorrector.setDefaultValue      (null);
        this.clockCorrector.setDefaultAttachment (null);

        // run the clock update loop
        runClockUpdateLoopAsync();
    }

    // get an approximation of the current time
    private long updateCorrection()
    {
        try
        {
            double now = (double) Stopwatch.time();

            this.correctionFactorLock.lock();

            this.clockCorrector.lazySet(now - 1e10 * (multiplier ? -1 : 1));

            this.multiplier = !this.multiplier;

            Double correction = this.clockCorrector.get();

            this.realTexecs.add((double)Stopwatch.time());

            if (correction != null)
            {
                // update the correction factor
                this.clockValue          = Math.round(correction);
                this.lastClockUpdateTime = Stopwatch.time();
                this.correctionFactorLock.unlock();
                // return the time now
                return this.clockValue;
            }

            this.correctionFactorLock.unlock();

            return -1;
        }
        catch (MinimumProcessesNotReachedException | InterruptedException | ExecutionException e)
        {
            e.printStackTrace();

            if(this.correctionFactorLock.isLocked() && this.correctionFactorLock.isHeldByCurrentThread())
                this.correctionFactorLock.unlock();

            return -1;
        }
    }

    private void storeMetrics()
    {
        TestAux.store(this.texecs, this.wallTimes, this.realTexecs, this.clockCorrector.getMetrics(),
                TestConsts.CS_OUTPUT_DIR + "FCAClockSynch/FCAClockSynch_leader_" + this.myPort + "_"
                        + this.processor.n() + ".csv");

        System.out.println("- Finished logging.");

        // terminate
        this.processor.terminate();
    }

    private void runClockUpdateLoopAsync()
    {
        Runnable updateExec = () ->
        {
            try
            {
                for (int i = 0; i < TestConsts.MAX_CS_ITER; i++)
                {
                    Thread.sleep((long) (FCAClockSynchAttachment.R / 1e6));

                    LocalDateTime now = LocalDateTime.now();

                    long myTime = Stopwatch.time();
                    long time   = this.updateCorrection();

                    this.texecs   .add((double)(Stopwatch.time() - myTime));
                    this.wallTimes.add(now);

                    System.out.println(i + ": Finished synchronization;");

                    System.out.println(i + ": My Clock: " + (myTime / 1e9) + "s; \nCorrected:   " + (time/ 1e9) +
                        "s;");
                }
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        };

        Runnable updateAndStore = () ->
        {
            // run the repeated updates
            updateExec.run();
            // store metrics
            storeMetrics();
        };

        // only the leader runs the update loop, as oonly one processor needs to do this
        if (this.processor.isLeader())
            (new Thread(updateAndStore)).start();

        else
        {
            while(this.clockCorrector.getNumFinishedRequests() < TestConsts.MAX_CS_ITER);

            TestAux.store(this.clockCorrector.getMetrics(),
                TestConsts.CS_OUTPUT_DIR + "FCAClockSynch/FCAClockSynch_" + this.myPort + "_"
                        + this.processor.n() + ".csv");

            this.processor.terminate();

        }


    }

    long now()
    {
        this.correctionFactorLock.lock();

        // get the current correction factor for the clock
        long correctionFactor = this.clockValue;

        this.correctionFactorLock.unlock();

        // return our time plus the correction factor
        return (Stopwatch.time() - lastClockUpdateTime) + correctionFactor;
    }

}

public class FCAClockSynch
{
    public static void main(String[] args)
    {
        System.out.println("FCA-Based Clock Synch");

        if (args.length < 3)
        {
            System.out.println("Not enough arguments!");
            System.exit(0);
        }

        try
        {
            new ApproximateClock(args[1], args[0], Integer.parseInt(args[2]));
        }
        catch (IOException e)
        {
            System.out.println("Unable to create Clock, exiting...");
            System.exit(0);
        }
    }
}
