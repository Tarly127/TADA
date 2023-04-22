package test.faulty.semanticSymmetric;

import test.other.FaultyTests;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.consensus.types.faultDescriptors.FaultClass;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class FCA
{
    public static void main(String[] args)
        throws InterruptedException, ExecutionException, MinimumProcessesNotReachedException, IOException
    {
        FaultyTests.FaultyFCA(args, FaultClass.SEMANTIC_SYMMETRIC);
    }

}
