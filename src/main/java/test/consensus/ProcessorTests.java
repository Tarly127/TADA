package test.consensus;

import core.Processor;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class ProcessorTests
{
    public static void main(String[] args)
            throws InterruptedException, IOException, ExecutionException
    {
        System.out.println("Processor Test " + ProcessHandle.current().pid());

        if(args.length < 2)
        {
            System.out.println("Not enough arguments");
            System.exit(0);
        }

        int N = args.length >= 3 ? Integer.parseInt(args[2]) : 4;

        Processor processor = args.length >= 3 ?
                              new Processor(args[0], args[1], N) :
                              new Processor(args[0], args[1]);


        while(processor.n() < N);

        if(processor.isLeader())
            processor.close();
    }
}
