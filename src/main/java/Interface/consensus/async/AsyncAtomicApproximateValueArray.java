package Interface.consensus.async;

import Interface.consensus.utils.ApproximatePrimitive;

import java.util.concurrent.CompletableFuture;

public interface AsyncAtomicApproximateValueArray<V extends Number & Comparable<V>> extends ApproximatePrimitive
{
    /**
     * Set the element at position index to newValueAtIndex
     * @param index Position of the element
     * @param newValueAtIndex New value it should be updated to
     */
    CompletableFuture<Void> set(int index, V newValueAtIndex);

    /**
     * Get the element at index
     * @param index Position
     * @return Element at position index
     */
    CompletableFuture<V> get(int index);

    /**
     * Eventually set value at index to newValue
     * @param index Position of the element
     * @param newValue New value it should be updated to
     */
    CompletableFuture<Void> lazySet(int index, V newValue);

    /**
     * Get value at index and set to newValue (eager)
     * @param index Position of the element
     * @param newValue New value it should be updated to
     * @return Old value
     */
    CompletableFuture<V> getAndSet(int index, V newValue);

    /**
     * Get value at index and set to newValue (eager)
     * @param index Position of the element
     * @param newValue New value it should be updated to
     * @return Old value
     */
    CompletableFuture<V> getAndLazySet(int index, V newValue);

    /**
     * Get number of elements in array
     * @return Number of elements in array
     */
    CompletableFuture<Integer> length();

    /**
     * Set the element at index to newValue if the element at index is equal to expectedValue (Strong)
     * @param index Position of the element to be compared and updated
     * @param expectedValue Expected value of element at index
     * @param newValue New value at index
     * @return Value at index after possible update
     */
    CompletableFuture<Boolean> compareAndSet(int index, V expectedValue, V newValue);
}
