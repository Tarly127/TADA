package Interface.consensus.async;

import Interface.consensus.synch.SynchronousPrimitive;

/**
 * Interface defining an asynchronous primitive that can be converted to a synchronous one.
 */
public interface AsynchronousPrimitive {
    /**
     * Return the equivalent synchronous primitive of the object
     * @return Equivalent synchronous primitive
     */
    SynchronousPrimitive sync();
}
