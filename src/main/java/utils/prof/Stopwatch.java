package utils.prof;

import java.util.concurrent.TimeUnit;

public class Stopwatch
{
    public static long time()
    {
        return System.nanoTime();
    }

    public static double elapsed(long start, long end, TimeUnit resultUnit)
    {
        long delta = end - start;

        switch (resultUnit)
        {
            case MICROSECONDS -> { return delta / 1000.0; }
            case MILLISECONDS -> { return delta / 1000000.0; }
            case SECONDS      -> { return delta / 1000000000.0; }
            default           -> { return delta; }
        }
    }
}
