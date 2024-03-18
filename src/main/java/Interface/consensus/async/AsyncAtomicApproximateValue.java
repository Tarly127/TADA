package Interface.consensus.async;

import Interface.consensus.utils.ApproximatePrimitive;
import utils.consensus.exception.MinimumProcessesNotReachedException;

<<<<<<< HEAD
import java.util.concurrent.Future;

/**
 * Interface for numerical value atomically updated through approximate consensus algorithms. Contained value must be
 * an extension of number and implement Comparable. Extends ApproximatePrimitive. Every method that queries the
 * internal value of the variable throws a MinimumProcessesNotReachedException if is called when the associated
 * communication group hasn't reached the necessary number of participants. Each method is also non-blocking,
 * returning a promise (Future) that will eventually contain the result of the operation.
 * @param <V> Type of the encapsulated value.
 */
=======
import java.util.concurrent.CompletableFuture;

>>>>>>> FixingFinalDissertationVersion
public interface AsyncAtomicApproximateValue<V extends Number & Comparable<V>> extends ApproximatePrimitive
{
    /**
     * Set value to newValue (eager set)
     *
     * @param newValue New value it should be updated to
     */
<<<<<<< HEAD
    Future<Void> set(V newValue) throws MinimumProcessesNotReachedException;
=======
    CompletableFuture<Void> set(V newValue) throws MinimumProcessesNotReachedException;
>>>>>>> FixingFinalDissertationVersion

    /**
     * Eventually set value to newValue
     *
     * @param newValue New value it should be updated to
     */
<<<<<<< HEAD
    Future<Void> lazySet(V newValue);
=======
    CompletableFuture<Void> lazySet(V newValue);
>>>>>>> FixingFinalDissertationVersion

    /**
     * Get value and set to newValue (eager)
     *
     * @param newValue New value it should be updated to
     * @return Old value
     */
<<<<<<< HEAD
    Future<V> getAndSet(V newValue) throws MinimumProcessesNotReachedException;
=======
    CompletableFuture<V> getAndSet(V newValue) throws MinimumProcessesNotReachedException;
>>>>>>> FixingFinalDissertationVersion

    /**
     * Get value and set to newValue (eager)
     *
     * @param newValue New value it should be updated to
     * @return Old value
     */
<<<<<<< HEAD
    Future<V> lazyGetAndSet(V newValue);
=======
    CompletableFuture<V> lazyGetAndSet(V newValue);
>>>>>>> FixingFinalDissertationVersion

    /**
     * Get the stored value (eager)
     *
     * @return Stored value
     */
<<<<<<< HEAD
    Future<V> get() throws MinimumProcessesNotReachedException;
=======
    CompletableFuture<V> get() throws MinimumProcessesNotReachedException;
>>>>>>> FixingFinalDissertationVersion

    /**
     * Get the stored value (lazy)
     *
     * @return Stored value
     */
<<<<<<< HEAD
    Future<V> lazyGet();
=======
    CompletableFuture<V> lazyGet();
>>>>>>> FixingFinalDissertationVersion

    /**
     * Set the stored value to newValue if it's equal to expectedValue (Strong)
     *
     * @param expectedValue Expected value
     * @param newValue      New value
     * @return Stored value after possible update
     */
<<<<<<< HEAD
    Future<Boolean> compareAndSet(V expectedValue, V newValue) throws MinimumProcessesNotReachedException;
=======
    CompletableFuture<Boolean> compareAndSet(V expectedValue, V newValue) throws MinimumProcessesNotReachedException;
>>>>>>> FixingFinalDissertationVersion
}

