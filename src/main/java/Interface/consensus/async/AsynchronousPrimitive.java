package Interface.consensus.async;

import Interface.consensus.synch.SynchronousAlgorithm;

public interface AsynchronousPrimitive {
    /**
     * Return the equivalent synchronous primitive of the object
     * @return Equivalent synchronous primitive
     */
    SynchronousAlgorithm sync();
}
