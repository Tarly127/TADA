package test;

import Interface.consensus.utils.ApproximateConsensusHandler;
import core.Processor;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BasicTest
{
    public static void main(String[] args) throws IOException
    {
        try
        {
            if(args.length < 3) return;

            // Processor creation
            var processor = new Processor(
                    args[0], // own addr
                    args[1],
                    Integer.parseInt(args[2])); // broker addr


            // blocking interface
            var atomicPrimitive = processor
                    .newAtomicApproximateDouble(
                            "atomicPrimitive",
                            0.005,
                            1, TimeUnit.SECONDS);

            atomicPrimitive.lazySet(1.5);

            System.out.println(atomicPrimitive.get()); // should be 1.5

            // non-blocking interface
            var asyncAtomicPrimitive = processor
                    .newAsyncAtomicApproximateDouble(
                            "asyncAtomicPrimitive",
                            0.005);

            CompletableFuture<Void> setRes = asyncAtomicPrimitive.set(3.0);
            System.out.println(setRes.get()); // should be null

            // template variable
            var atomicPrimitiveTmpl = processor
                    .newAtomicApproximateDouble(
                            "atomicPrimitive",
                            0.005,
                            1, TimeUnit.SECONDS,
                            new ApproximateConsensusHandler<Void> () {
                                    // method implementations go here
                            });

            processor.terminate();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
