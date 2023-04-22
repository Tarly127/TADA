package utils.math.atomicExtensions;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Wrapper to AtomicLong to simulate expected AtomicDouble behaviour
 */
public class AtomicDouble extends AtomicLong
{
    public AtomicDouble(double initialValue)
    {
        super(Double.doubleToLongBits(initialValue));
    }

    public AtomicDouble()
    {
        super();
    }

    public Double getDouble()
    {
        return Double.longBitsToDouble(super.get());
    }

    public final Double setDouble(Double newValue)
    {
        super.set(Double.doubleToLongBits(newValue));

        return newValue;
    }
}
