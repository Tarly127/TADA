package AtomicInterface.consensus.synch;

import AtomicInterface.consensus.async.AsynchronousPrimitive;
import org.javatuples.Pair;

import java.util.concurrent.TimeUnit;

/**
 * Interface detailing functions to interact with the expected communication timeout in synchronous communication groups
 */
public interface SynchronousPrimitive {

    /**
     * Set the expected timeout value
     * @param timeout Timeout value
     * @param unit Timeout unit
     */
    void setTimeout(long timeout, TimeUnit unit);

    /**
     * Get the expected timeout value
     * @return Expected timeout value and unit
     */
    Pair<Long, TimeUnit> getTimeout();

    /**
     * Return the equivalent asynchronous primitive of the object
     * @return Equivalent asynchronous primitive
     */
    AsynchronousPrimitive async();

}
