package utils.math.atomicExtensions;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wrapper to AtomicInteger to simulate expected AtomicFloat behaviour
 */
public class AtomicFloat extends AtomicInteger
{
    public AtomicFloat(float initialValue)
    {
        super(Float.floatToIntBits(initialValue));
    }

    public AtomicFloat()
    {
        super();
    }

    public Float getFloat()
    {
        return Float.intBitsToFloat(super.get());
    }

    public void setFloat(Float newValue)
    {
        super.set(Float.floatToIntBits(newValue));
    }
}
