package Interface.consensus.synch;

import org.javatuples.Pair;

import java.util.concurrent.TimeUnit;

/**
 * Interface defining methods related to the synchronous model, as it affects approximate consensus algorithms. This
 * means that it defined methods for the timeout and for default values that should be filled in, in the case that
 * timeout is reached.
 * @param <V> Type of the default value on timeout
 */
public interface SynchronousAlgorithm<V extends Number & Comparable<V>>
{
    /**
     * Get the Timeout for the synchronous algorithm
     * @return Pair contanining the timeout value and unit
     */
    Pair<Long, TimeUnit> getTimeout();

    /**
     * Set the timeout for the synchronous algorithm
     * @param timeout Timeout value
     * @param unit Timeout unit
     */
    void setTimeout(long timeout, TimeUnit unit);

    /**
     * Set default value to count as vote for processes that may be faulty
     * @param defaultValue New default value
     */
    void setDefaultValue(V defaultValue);
}
