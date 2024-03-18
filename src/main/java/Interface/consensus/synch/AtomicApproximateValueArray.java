package Interface.consensus.synch;


import Interface.consensus.utils.ApproximatePrimitive;

/**
 * A fixed-size array of elements of type V whose elements can be updated atomically using Approximate Consensus.
 * Follows a similar interface to java.lang.concurrent.atomic.AtomicIntegerArray
 * Not implemented.
 * @param <V> Type of elements in array. Should always be a number
 */
public interface AtomicApproximateValueArray<V extends Number & Comparable<V>> extends ApproximatePrimitive
{

    /**
     * Set the element at position index to newValueAtIndex
     * @param index Position of the element
     * @param newValueAtIndex New value it should be updated to
     */
    void set(int index, V newValueAtIndex);

    /**
     * Get the element at index
     * @param index Position
     * @return Element at position index
     */
    V get(int index);

    /**
     * Eventually set value at index to newValue
     * @param index Position of the element
     * @param newValue New value it should be updated to
     */
    void lazySet(int index, V newValue);

    /**
     * Get value at index and set to newValue (eager)
     * @param index Position of the element
     * @param newValue New value it should be updated to
     * @return Old value
     */
    V getAndSet(int index, V newValue);

    /**
     * Get value at index and set to newValue (eager)
     * @param index Position of the element
     * @param newValue New value it should be updated to
     * @return Old value
     */
    V getAndLazySet(int index, V newValue);

    /**
     * Get number of elements in array
     * @return Number of elements in array
     */
    int length();

}
