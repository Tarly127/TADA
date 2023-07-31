package Interface.consensus.synch;

import Interface.consensus.utils.ApproximatePrimitive;
import utils.consensus.exception.MinimumProcessesNotReachedException;

import java.util.concurrent.ExecutionException;

/**
 * Interface for numerical value atomically updated through approximate consensus algorithms. Contained value must be
 * an extension of Number and implement Comparable. Extends ApproximatePrimitive. Every method that queries the
 * internal value of the variable throws a MinimumProcessesNotReachedException if is called when the associated
 * communication group hasn't reached the necessary number of participants. Each method blocks until completed.
 * @param <V> Type of the encapsulated value.
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
}
