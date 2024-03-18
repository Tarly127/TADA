package test.other;

import core.AsyncAtomicApproximateDouble;
import core.AtomicApproximateDouble;
import core.AtomicInexactDouble;
import core.Processor;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.consensus.types.faultDescriptors.FaultClass;
import utils.prof.Stopwatch;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static test.other.TestAux.byzantine;
import static test.other.TestAux.timeout;
import static test.other.TestConsts.MAX_ITER;

public class FaultyTests
{
    public static void FaultySynchDLPSW86(String[] args, FaultClass faultClass)
            throws MinimumProcessesNotReachedException, InterruptedException, ExecutionException, IOException
    {
        System.out.println("SynchDLPSW86 (" + FaultClass.toString(faultClass) + ") " + ProcessHandle.current().pid());

        if (args.length < 2)
        {
            System.out.println("Not enough arguments");
            System.exit(0);
        }

        int N = args.length >= 3 ? Integer.parseInt(args[2]) : 4;

        Random r = new Random();

        List<Double> execTimes = new ArrayList<>();
        List<LocalDateTime> execWallTimes = new ArrayList<>();

        Processor processor = args.length >= 3 ?
                              new Processor(args[0], args[1], Integer.parseInt(args[2])) :
                              new Processor(args[0], args[1]);

        int maxFaultyProcs = args.length >= 3 ? (Integer.parseInt(args[2]) - 1) / 3 : 0;

        AtomicApproximateDouble temperature = byzantine(args[0], args[1], args[2], maxFaultyProcs) ?
                                              processor.newAtomicApproximateDouble(
                                                      "Temperature",
                                                      TestConsts.EPSILON,
                                                      r.nextDouble(),
                                                      timeout(N),
                                                      TimeUnit.MILLISECONDS,
                                                      faultClass) :
                                              processor.newAtomicApproximateDouble(
                                                      "Temperature",
                                                      TestConsts.EPSILON,
                                                      r.nextDouble(),
                                                      timeout(N),
                                                      TimeUnit.MILLISECONDS);

        if (processor.isLeader())
            System.out.println("- Starting tests...");

        var start_timer = Stopwatch.time();

        if (processor.isLeader())
        {
            for (long i = 0; i < MAX_ITER; i++)
            {
                // sleep for 100 millseconds
                Thread.sleep(100);

                long start = Stopwatch.time();
                LocalDateTime startDate = LocalDateTime.now();
                temperature.get();
                double timeTemperature = (Stopwatch.time() - start) / 1000000.0;

                System.out.println("Finished " + i);

                execTimes.add(timeTemperature);
                execWallTimes.add(startDate);
            }

            System.out.println("- Finished Tests (" + (Stopwatch.time() - start_timer) / 1000000000.0 + "s).");

            var metrics = temperature.getMetrics();

            // store texecs
            TestAux.store(execTimes, execWallTimes, metrics,
                    TestConsts.FAULTY_OUTPUT_DIR +  "SynchDLPSW86/SynchDLPSW86_leader_" + args[0] + "_" + args[2] +
                            ".csv");

            System.out.println("- Finished logging.");

            // terminate
            processor.terminate();
        }

        else
        {
            while (temperature.getNumFinishedRequests() < MAX_ITER);

            System.out.println("- (Non Leader) Finished Tests (" + (Stopwatch.time() - start_timer) / 1000000000.0 +
                    "s).");

            var metrics = temperature.getMetrics();

            TestAux.store(metrics, TestConsts.FAULTY_OUTPUT_DIR +
                    "SynchDLPSW86/SynchDLPSW86_" + args[0] + "_" + args[2] + ".csv");

            System.out.println("- (Non Leader) Finished logging.");

            // terminate
            processor.terminate();
        }
    }

    public static void FaultyAsynchDLPSW86(String[] args, FaultClass faultClass)
            throws InterruptedException, ExecutionException, MinimumProcessesNotReachedException, IOException
    {
        System.out.println("AsyncDLPSW86 (" + FaultClass.toString(faultClass) + ") " + ProcessHandle.current().pid());

        if (args.length < 2)
        {
            System.out.println("Not enough arguments");
            System.exit(0);
        }

        int N = args.length >= 3 ? Integer.parseInt(args[2]) : 6;

        Random r = new Random();

        List<Double> execTimes = new ArrayList<>();
        List<LocalDateTime> execWallTimes = new ArrayList<>();
        Processor processor = args.length >= 3 ?
                              new Processor(args[0], args[1], Integer.parseInt(args[2])) :
                              new Processor(args[0], args[1]);

        int maxFaultyProcs = args.length >= 3 ? (Integer.parseInt(args[2]) - 1) / 5 : 0;

        AsyncAtomicApproximateDouble temperature = byzantine(args[0], args[1], args[2], maxFaultyProcs) ?
                                                   processor.newAsynchAtomicApproximateDouble(
                                                      "Temperature",
                                                      TestConsts.EPSILON,
                                                      r.nextDouble(),
                                                      faultClass) :
                                                   processor.newAsynchAtomicApproximateDouble(
                                                      "Temperature",
                                                      TestConsts.EPSILON,
                                                      r.nextDouble());


        if (processor.isLeader()) System.out.println("Before consensus: " + temperature.lazyGet().get());

        if (processor.isLeader()) System.out.println("- Starting tests...");

        var start_timer   = Stopwatch.time();

        if(processor.isLeader())
        {
            for (long i = 0; i < MAX_ITER; i++)
            {
                // sleep for 100 milliseconds
                Thread.sleep(100);

                long start = Stopwatch.time();
                LocalDateTime startDate = LocalDateTime.now();
                temperature.get().get();
                double timeTemperature = (Stopwatch.time() - start) / 1000000.0;

                System.out.println("Finished " + i);

                execTimes.add(timeTemperature);
                execWallTimes.add(startDate);
            }

            System.out.println("- Finished Tests (" + (Stopwatch.time() - start_timer) / 1000000000.0 + "s).");

            var metrics        = temperature.getMetrics();

            TestAux.store(execTimes, execWallTimes, metrics,
                    TestConsts.FAULTY_OUTPUT_DIR + "AsyncDLPSW86/AsyncDLPSW86_leader_" + args[0] + "_" + args[2] +
                            ".csv");

            System.out.println("- Finished logging.");

            // terminate
            processor.terminate();
        }
        else
        {
            while(temperature.getNumFinishedRequests() < MAX_ITER);

            System.out.println("Non leader finished");

            var metrics = temperature.getMetrics();

            TestAux.store(metrics,
                    TestConsts.FAULTY_OUTPUT_DIR +
                            "AsyncDLPSW86/AsynchDLPSW86_" + args[0] + "_" + args[2] + ".csv");

             System.out.println("- (Non Leader) Finished logging.");

            // terminate
            processor.terminate();
        }
    }

    public static void FaultyFCA(String[] args, FaultClass faultClass)
            throws InterruptedException, ExecutionException, MinimumProcessesNotReachedException, IOException
    {
        System.out.println("FCA (" + FaultClass.toString(faultClass) + ") " + ProcessHandle.current().pid());

        if (args.length < 2)
        {
            System.out.println("Not enough arguments");
            System.exit(0);
        }

        Random r = new Random();

        List<Double> execTimes = new ArrayList<>();
        List<LocalDateTime> execWallTimes = new ArrayList<>();

        Processor processor = args.length >= 3 ?
                              new Processor(args[0], args[1], Integer.parseInt(args[2])) :
                              new Processor(args[0], args[1]);

        int maxFaultyProcs = args.length >= 3 ? (Integer.parseInt(args[2]) - 1) / 3 : 0;

        AtomicInexactDouble temperature = byzantine(args[0], args[1], args[2], maxFaultyProcs) ?
                                          processor.newAtomicInexactDouble(
                                                      "Temperature",
                                                      TestConsts.EPSILON,
                                                      r.nextDouble(),
                                                      1000,
                                                      TimeUnit.MILLISECONDS,
                                                      faultClass) :
                                          processor.newAtomicInexactDouble(
                                                      "Temperature",
                                                      TestConsts.EPSILON,
                                                      r.nextDouble(),
                                                      1000,
                                                      TimeUnit.MILLISECONDS);


        if (processor.isLeader())
            System.out.println("- Starting tests...");

        var start_timer = Stopwatch.time();

        if (processor.isLeader())
        {
            for (long i = 0; i < MAX_ITER; i++)
            {
                // sleep for 100 millseconds
                Thread.sleep(100);

                long start = Stopwatch.time();
                LocalDateTime startDate = LocalDateTime.now();
                double c = temperature.get();
                double timeTemperature = (Stopwatch.time() - start) / 1000000.0;

                System.out.println("Finished " + i + " : " + c);

                execTimes.add(timeTemperature);
                execWallTimes.add(startDate);
            }

            System.out.println("- Finished Tests (" + (Stopwatch.time() - start_timer) / 1000000000.0 + "s).");

            var metrics = temperature.getMetrics();

            // store texecs
            TestAux.store(execTimes, execWallTimes, metrics,
                    TestConsts.FAULTY_OUTPUT_DIR +  "FCA/FCA_leader_"
                            + args[0] + "_" + args[2] + ".csv");

            System.out.println("- Finished logging.");

            // terminate
            processor.terminate();
        }

        else
        {
            while (temperature.getNumFinishedRequests() < MAX_ITER) ;

            System.out.println("- (Non Leader) Finished Tests (" + (Stopwatch.time() - start_timer) / 1000000000.0 +
                    "s).");

            var metrics = temperature.getMetrics();

            TestAux.store(metrics,
                    TestConsts.FAULTY_OUTPUT_DIR + "FCA/FCA_"
                    + args[0] + "_" + args[2] + ".csv");

            System.out.println("- (Non Leader) Finished logging.");

            // terminate
            processor.terminate();
        }
    }
}
