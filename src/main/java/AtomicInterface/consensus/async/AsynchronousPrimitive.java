package AtomicInterface.consensus.async;

import AtomicInterface.consensus.synch.SynchronousPrimitive;

public interface AsynchronousPrimitive {
    /**
     * Return the equivalent synchronous primitive of the object
     * @return Equivalent synchronous primitive
     */
    SynchronousPrimitive sync();
}
