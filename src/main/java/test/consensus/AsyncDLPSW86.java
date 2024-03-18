package test.consensus;

import core.Processor;
import test.other.TestAux;
import test.other.TestConsts;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.prof.Stopwatch;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static test.other.TestConsts.MAX_ITER;

public class AsyncDLPSW86
{
    public static void main(String[] args)
            throws InterruptedException, MinimumProcessesNotReachedException, ExecutionException, IOException
    {
        System.out.println("AsyncDLPSW86 " + ProcessHandle.current().pid());

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

        var temperature = processor.newAsynchAtomicApproximateDouble(
                "Temperature",
                TestConsts.EPSILON,
                r.nextDouble());

        if (processor.isLeader()) System.out.println("Before consensus: " + temperature.lazyGet().get());

        if (processor.isLeader()) System.out.println("- Starting tests...");


        var uptimeAtStart = TestAux.getUptime();
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
                    TestConsts.OUTPUT_DIR + "AsyncDLPSW86/AsynchDLPSW86_leader_" + args[0] + "_" + args[2] +
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
                    TestConsts.OUTPUT_DIR + "AsyncDLPSW86/AsynchDLPSW86_" + args[0] + "_" + args[2] + ".csv");

             System.out.println("- (Non Leader) Finished logging.");

            // terminate
            processor.terminate();
        }
    }
}
