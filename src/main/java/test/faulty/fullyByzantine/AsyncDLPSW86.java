package test.faulty.fullyByzantine;

import primitives.AsynchAtomicApproximateDouble;
import primitives.Processor;
import test.other.FaultyTests;
import test.other.TestAux;
import test.other.TestConsts;
import utils.consensus.exception.MinimumProcessesNotReachedException;
import utils.consensus.types.faultDescriptors.FaultClass;
import utils.measurements.Stopwatch;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static test.other.TestAux.byzantine;
import static test.other.TestConsts.MAX_ITER;

public class AsyncDLPSW86
{
    public static void main(String[] args)
            throws InterruptedException, MinimumProcessesNotReachedException, ExecutionException, IOException
    {
        FaultyTests.FaultyAsynchDLPSW86(args, FaultClass.FULLY_BYZANTINE);
    }
}
