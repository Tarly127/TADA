package Interface.consensus.async;

import Interface.consensus.utils.ApproximatePrimitive;

import java.util.concurrent.Future;

/**
 * Interface for numerical fixed-size array atomically updated through approximate consensus algorithms. Contained
 * values
 * must be
 * an extension of Number and implement Comparable. Extends ApproximatePrimitive. Every method that queries the
 * internal value of the variable throws a MinimumProcessesNotReachedException if is called when the associated
 * communication group hasn't reached the necessary number of participants. Each method is also non-blocking,
 * returning a promise (Future) that will eventually contain the result of the operation. It is not defined that each
 * instance of consensus should update all values as a batch or only update the relevant value called by the method.
 * @param <V> Type of the encapsulated value.
 */
public interface AsyncAtomicApproximateValueArray<V extends Number & Comparable<V>> extends ApproximatePrimitive
{
    /**
     * Set the element at position index to newValueAtIndex
     *
     * @param index           Position of the element
     * @param newValueAtIndex New value it should be updated to
     */
    Future<Void> set(int index, V newValueAtIndex);

    /**
     * Get the element at index
     *
     * @param index Position
     * @return Element at position index
     */
    Future<V> get(int index);

    /**
     * Eventually set value at index to newValue
     *
     * @param index    Position of the element
     * @param newValue New value it should be updated to
     */
    Future<Void> lazySet(int index, V newValue);

    /**
     * Get the element at index without initiating synchronisation
     *
     * @param index Position
     * @return Element at position index
     */
    Future<V> lazyGet(int index);

    /**
     * Get value at index and set to newValue (eager)
     *
     * @param index    Position of the element
     * @param newValue New value it should be updated to
     * @return Old value
     */
    Future<V> getAndSet(int index, V newValue);

    /**
     * Get value at index and set to newValue (eager)
     *
     * @param index    Position of the element
     * @param newValue New value it should be updated to
     * @return Old value
     */
    Future<V> lazyGetAndSet(int index, V newValue);

    /**
     * Get number of elements in array
     *
     * @return Number of elements in array
     */
    Future<Integer> length();

    /**
     * Set the element at index to newValue if the element at index is equal to expectedValue (Strong)
     *
     * @param index         Position of the element to be compared and updated
     * @param expectedValue Expected value of element at index
     * @param newValue      New value at index
     * @return Value at index after possible update
     */
    Future<Boolean> compareAndSet(int index, V expectedValue, V newValue);
}
