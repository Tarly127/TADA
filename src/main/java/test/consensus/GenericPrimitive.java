package test.consensus;

import AtomicInterface.consensus.ApproximateConsensusHandler;
import primitives.Processor;
import test.other.TestAux;
import test.other.TestConsts;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.measurements.Stopwatch;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static test.other.TestAux.timeout;
import static test.other.TestConsts.MAX_ITER;

public class GenericPrimitive
{
    public static void main(String[] args)
            throws InterruptedException, MinimumProcessesNotReachedException, ExecutionException, IOException
    {
        System.out.println("GenericPrimitive " + ProcessHandle.current().pid());

        if(args.length < 2)
        {
            System.out.println("Not enough arguments");
            System.exit(0);
        }

        Random r = new Random();

        List<Double> execTimes            = new ArrayList<>();
        List<LocalDateTime> execWallTimes = new ArrayList<>();

        int N = args.length >= 3 ? Integer.parseInt(args[2]) : 4;

        Processor processor = args.length >= 3 ?
                              new Processor(args[0], args[1], N) :
                              new Processor(args[0], args[1]);
        
        var temperature = processor.newAtomicApproximateDouble(
                "Temperature",
                TestConsts.EPSILON,
                r.nextDouble(),
                timeout(N),
                TestConsts.UNIT,
                new ApproximateConsensusHandler<Void>() {}
        );

        if(processor.isLeader())
            System.out.println("- Starting tests...");

        var start_timer = Stopwatch.time();

        if(processor.isLeader())
        {
            for (long i = 0; i < MAX_ITER; i++)
            {
                // sleep for 100 millseconds
                Thread.sleep(100);

                long start = Stopwatch.time();
                LocalDateTime startDate = LocalDateTime.now();
                temperature.get();//.get();
                double timeTemperature = (Stopwatch.time() - start) / 1000000.0;

                System.out.println("Finished " + i);

                execTimes.add(timeTemperature);
                execWallTimes.add(startDate);
            }

            System.out.println("- Finished Tests (" + (Stopwatch.time() - start_timer) / 1000000000.0 + "s).");

            var metrics        = temperature.getMetrics();

            // store texecs
            TestAux.store(execTimes, execWallTimes, metrics,
                    TestConsts.OUTPUT_DIR + "Generic_Synch/Generic_Synch_leader_" + args[0] + "_" + args[2] +
                            ".csv");

            System.out.println("- Finished logging.");

            // terminate
            processor.terminate();
        }

        else
        {
            while(temperature.getNumFinishedRequests() < MAX_ITER);

            System.out.println("- (Non Leader) Finished Tests (" + (Stopwatch.time() - start_timer) / 1000000000.0 +
                    "s).");

            var metrics        = temperature.getMetrics();

            TestAux.store(metrics, TestConsts.OUTPUT_DIR + "Generic_Synch/Generic_Synch_" + args[0] + "_" + args[2] +
                    ".csv");

            System.out.println("- (Non Leader) Finished logging.");

            // terminate
            processor.terminate();
        }
    }
}
