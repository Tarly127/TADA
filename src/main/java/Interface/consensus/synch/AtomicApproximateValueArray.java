package Interface.consensus.synch;


import Interface.consensus.utils.ApproximatePrimitive;

/**
<<<<<<< HEAD
 * Interface for numerical fixed-size array atomically updated through approximate consensus algorithms. Contained
 * values must be an extension of Number and implement Comparable. Extends ApproximatePrimitive. Every method that
 * queries the internal value of the array throws a MinimumProcessesNotReachedException if is called when the
 * associated communication group hasn't reached the necessary number of participants. Each method is also non-blocking,
 * returning a promise (Future) that will eventually contain the result of the operation.
 * @param <V> Type of the encapsulated value.
=======
 * A fixed-size array of elements of type V whose elements can be updated atomically using Approximate Consensus.
 * Follows a similar interface to java.lang.concurrent.atomic.AtomicIntegerArray
 * Not implemented.
 * @param <V> Type of elements in array. Should always be a number
>>>>>>> FixingFinalDissertationVersion
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
<<<<<<< HEAD
     * Get the element at index without triggering synchronisation
     * @param index Position
     * @return Element at position index
     */
    V lazyGet(int index);

    /**
=======
>>>>>>> FixingFinalDissertationVersion
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
<<<<<<< HEAD
    V lazyGetAndSet(int index, V newValue);

    /**
     * Set the element at index to newValue if the element at index is equal to expectedValue (Strong)
     *
     * @param index         Position of the element to be compared and updated
     * @param expectedValue Expected value of element at index
     * @param newValue      New value at index
     * @return Value at index after possible update
     */
    boolean compareAndSet(int index, V expectedValue, V newValue);
=======
    V getAndLazySet(int index, V newValue);
>>>>>>> FixingFinalDissertationVersion

    /**
     * Get number of elements in array
     * @return Number of elements in array
     */
    int length();

}
