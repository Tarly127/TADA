package test.faulty.omissiveAsymmetric;

import test.other.FaultyTests;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.consensus.types.faultDescriptors.FaultClass;

import java.io.IOException;
import java.util.concurrent.ExecutionException;


public class SynchDLPSW86
{
    public static void main(String[] args)
            throws InterruptedException, ExecutionException, MinimumProcessesNotReachedException, IOException
    {
        FaultyTests.FaultySynchDLPSW86(args, FaultClass.OMISSIVE_ASYMMETRIC);
    }
}
