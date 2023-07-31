package test.consensus;

import core.Processor;
import test.other.TestConsts;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.prof.Stopwatch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static test.other.TestAux.store;


public class BSO
{
    public static void main(String[] args)
            throws InterruptedException, MinimumProcessesNotReachedException, ExecutionException
    {
        //Log.TRACE();

        System.out.println("BSO " + ProcessHandle.current().pid());

        if(args.length < 2)
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

        var temperature = processor.newMixedModeApproximateDouble(
                "temperature",
                TestConsts.EPSILON,
                r.nextDouble(),
                (Integer.parseInt(args[2]) - 1) / 3,
                TestConsts.S,
                TestConsts.O,
                TestConsts.TIMEOUT,
                TestConsts.UNIT);

        System.out.println("Before consensus: " + temperature.lazyGet());

        if(processor.isLeader()) System.out.println("- Starting tests...");

        var start_timer = Stopwatch.time();

        for(long i = 0; i < TestConsts.MAX_ITER; i++)
        {
            Double resultTemperature = 0.0, resultRay = 0.0;

            if (processor.isLeader())
            {
                //Thread.sleep(15000);

                long start = Stopwatch.time();
                LocalDateTime wallTime = LocalDateTime.now();
                resultTemperature = temperature.get();
                double timeTemperature = ( Stopwatch.time() - start ) / 1000000.0;

                System.out.println("(" + i + ") After consensus: Temperature = " + resultTemperature
                        + " (" + timeTemperature + "ms);");

                execTimes.add(timeTemperature);
                execWallTimes.add(wallTime);
            }
            //else
            //{
            //    Thread.sleep(25);
            //    temperature.lazySet((new Random()).nextDouble());
            //}
        }

        if (processor.isLeader())
            System.out.println("- Finished Tests (" + (Stopwatch.time() - start_timer) / 1000000000.0 + "s).");

        var metrics = temperature.getMetrics();

        if(processor.isLeader())
            store(execTimes, execWallTimes, metrics,
                    TestConsts.OUTPUT_DIR + "BSO_" + args[0] + "_" +
                            (args.length > 2 ? "_" + args[2] : "") + ".csv");

        if (processor.isLeader())
            System.out.println("- Finished logging.");


    }
}
