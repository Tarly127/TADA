package Interface.consensus.async;

import Interface.consensus.utils.ApproximatePrimitive;
import utils.consensus.exception.MinimumProcessesNotReachedException;

import java.util.concurrent.Future;

/**
 * Interface for numerical value atomically updated through approximate consensus algorithms. Contained value must be
 * an extension of number and implement Comparable. Extends ApproximatePrimitive. Every method that queries the
 * internal value of the variable throws a MinimumProcessesNotReachedException if is called when the associated
 * communication group hasn't reached the necessary number of participants. Each method is also non-blocking,
 * returning a promise (Future) that will eventually contain the result of the operation.
 * @param <V> Type of the encapsulated value.
 */
public interface AsyncAtomicApproximateValue<V extends Number & Comparable<V>> extends ApproximatePrimitive
{
    /**
     * Set value to newValue (eager set)
     *
     * @param newValue New value it should be updated to
     */
    Future<Void> set(V newValue) throws MinimumProcessesNotReachedException;

    /**
     * Eventually set value to newValue
     *
     * @param newValue New value it should be updated to
     */
    Future<Void> lazySet(V newValue);

    /**
     * Get value and set to newValue (eager)
     *
     * @param newValue New value it should be updated to
     * @return Old value
     */
    Future<V> getAndSet(V newValue) throws MinimumProcessesNotReachedException;

    /**
     * Get value and set to newValue (eager)
     *
     * @param newValue New value it should be updated to
     * @return Old value
     */
    Future<V> lazyGetAndSet(V newValue);

    /**
     * Get the stored value (eager)
     *
     * @return Stored value
     */
    Future<V> get() throws MinimumProcessesNotReachedException;

    /**
     * Get the stored value (lazy)
     *
     * @return Stored value
     */
    Future<V> lazyGet();

    /**
     * Set the stored value to newValue if it's equal to expectedValue (Strong)
     *
     * @param expectedValue Expected value
     * @param newValue      New value
     * @return Stored value after possible update
     */
    Future<Boolean> compareAndSet(V expectedValue, V newValue) throws MinimumProcessesNotReachedException;
}

