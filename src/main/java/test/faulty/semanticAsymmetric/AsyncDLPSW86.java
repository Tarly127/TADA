package test.faulty.semanticAsymmetric;

import test.other.FaultyTests;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.consensus.types.faultDescriptors.FaultClass;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class AsyncDLPSW86
{
    public static void main(String[] args)
        throws InterruptedException, ExecutionException, MinimumProcessesNotReachedException, IOException
    {
        FaultyTests.FaultyAsynchDLPSW86(args, FaultClass.SEMANTIC_ASYMMETRIC);
    }
}
