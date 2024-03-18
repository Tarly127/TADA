package Interface.consensus.synch;

import Interface.consensus.utils.ApproximatePrimitive;
import utils.consensus.exception.MinimumProcessesNotReachedException;

import java.util.concurrent.ExecutionException;

/**
 * An object encapsulating a value of type V whose reads and writes are atomic, using Approximate Consensus
 * @param <V> Type of object being kept. Should always be a number.
 */
public interface AtomicApproximateValue<V extends Number & Comparable<V>> extends ApproximatePrimitive
{

    /**
     * Set value to newValue (eager set)
     * @param newValue New value it should be updated to
     */
    void set(V newValue) throws MinimumProcessesNotReachedException, InterruptedException, ExecutionException;

    /**
     * Eventually set value to newValue
     * @param newValue New value it should be updated to
     */
    void lazySet(V newValue);

    /**
     * Get value and set to newValue (eager)
     * @param newValue New value it should be updated to
     * @return Old value
     */
    V getAndSet(V newValue) throws MinimumProcessesNotReachedException, InterruptedException, ExecutionException;

    /**
     * Get value and set to newValue (eager)
     * @param newValue New value it should be updated to
     * @return Old value
     */
    V lazyGetAndSet(V newValue);

    /**
     * Get the stored value (eager)
     * @return Stored value
     */
    V get() throws MinimumProcessesNotReachedException, InterruptedException, ExecutionException;

    /**
     * Get the stored value (lazy)
     * @return Stored value
     */
    V lazyGet();

    /**
     * Set the stored value to newValue if it's equal to expectedValue (Strong)
     * @param expectedValue Expected value
     * @param newValue New value
     * @return Stored value after possible update
     */
    boolean compareAndSet(V expectedValue, V newValue)
            throws MinimumProcessesNotReachedException, InterruptedException, ExecutionException;

    /**
     * Set default value to count as vote for processes that may be faulty
     * @param defaultValue New default value
     */
    void setDefaultValue(V defaultValue);
}
