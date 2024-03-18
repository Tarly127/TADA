package Interface.consensus.async;

<<<<<<< HEAD
import Interface.consensus.synch.SynchronousPrimitive;
=======
import Interface.consensus.synch.SynchronousAlgorithm;
>>>>>>> FixingFinalDissertationVersion

/**
 * Interface defining an asynchronous primitive that can be converted to a synchronous one.
 */
public interface AsynchronousPrimitive {
    /**
     * Return the equivalent synchronous primitive of the object
     * @return Equivalent synchronous primitive
     */
    SynchronousAlgorithm sync();
}
