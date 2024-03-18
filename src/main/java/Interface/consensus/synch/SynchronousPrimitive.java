package Interface.consensus.synch;

import Interface.consensus.async.AsynchronousPrimitive;

/**
 * Interface defining an synchronous primitive that can be converted to an asynchronous one.
 */
public interface SynchronousPrimitive {
    /**
     * Return the equivalent asynchronous primitive of the object
     * @return Equivalent asynchronous primitive
     */
    AsynchronousPrimitive async();

}
