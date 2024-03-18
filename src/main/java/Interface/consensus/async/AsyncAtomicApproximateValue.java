package Interface.consensus.async;

import Interface.consensus.utils.ApproximatePrimitive;
import utils.consensus.exception.MinimumProcessesNotReachedException;

import java.util.concurrent.CompletableFuture;

public interface AsyncAtomicApproximateValue<V extends Number & Comparable<V>> extends ApproximatePrimitive
{
    /**
     * Set value to newValue (eager set)
     *
     * @param newValue New value it should be updated to
     */
    CompletableFuture<Void> set(V newValue) throws MinimumProcessesNotReachedException;

    /**
     * Eventually set value to newValue
     *
     * @param newValue New value it should be updated to
     */
    CompletableFuture<Void> lazySet(V newValue);

    /**
     * Get value and set to newValue (eager)
     *
     * @param newValue New value it should be updated to
     * @return Old value
     */
    CompletableFuture<V> getAndSet(V newValue) throws MinimumProcessesNotReachedException;

    /**
     * Get value and set to newValue (eager)
     *
     * @param newValue New value it should be updated to
     * @return Old value
     */
    CompletableFuture<V> lazyGetAndSet(V newValue);

    /**
     * Get the stored value (eager)
     *
     * @return Stored value
     */
    CompletableFuture<V> get() throws MinimumProcessesNotReachedException;

    /**
     * Get the stored value (lazy)
     *
     * @return Stored value
     */
    CompletableFuture<V> lazyGet();

    /**
     * Set the stored value to newValue if it's equal to expectedValue (Strong)
     *
     * @param expectedValue Expected value
     * @param newValue      New value
     * @return Stored value after possible update
     */
    CompletableFuture<Boolean> compareAndSet(V expectedValue, V newValue) throws MinimumProcessesNotReachedException;
}

