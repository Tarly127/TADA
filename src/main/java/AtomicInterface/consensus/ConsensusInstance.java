package AtomicInterface.consensus;

import AtomicInterface.communication.address.AddressInterface;
import AtomicInterface.communication.groupConstitution.ProcessInterface;
import utils.communication.message.ApproximationMessage;
import utils.consensus.ids.RequestID;
import utils.measurements.ConsensusMetrics;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Representation of the execution of an Approximate Agreement Algorithm, from inception to termination. Should not
 * be reused.
 * @param <T> Type of the variable on whose value agreement is trying to be achieved. Must be a number.
 */
public interface ConsensusInstance<T extends Number & Comparable<T>>
{
    /**
     * Method called when a new approximate consensus instance is started by another process.
     * @param msg The first message to be processed.
     * @return Completable Future containing the result of this instance of consensus
     */
    CompletableFuture<T> approximateConsensus_other(ApproximationMessage msg);

    /**
     * Method called when new approximate consensus instance is started by the calling process.
     * @return Completable Future containing the result of this instance of consensus
     */
    CompletableFuture<T> approximateConsensus_self();

    /**
     * Check if necessary conditions to guarantee correctness in the execution of the algorithm are still met.
     * @return True if conditions are met, false, otherwise.
     */
    boolean consensusStillPossible();

    /**
     * Getter for the consensus instance's snapshot of the group's constitution
     * @return Group Constitution as a mapping of group members to their respective address.
     */
    Map<? extends AddressInterface, ? extends ProcessInterface> getGroupState();

    /**
     * Getter for the unique identifier of this consensus instance.
     * @return Unique identifier of the consensus instance.
     */
    RequestID getReqID();

    /**
     * Get the object containing several relevant metrics to profile the execution of this instance of consensus
     * @return Object storing metrics relative to an execution of an approximate consensus algorithm.
     */
    ConsensusMetrics getMetrics();

    /**
     * Method called as handler of an approximation message. Consumes the message, which may advance the state of the
     * consensus instance.
     * @param msgType Type of message (initialization, approximation, etc.)
     * @param msg Object containing the payload of the message
     * @param q Address of the message's sender
     */
    void exchange(Byte msgType, ApproximationMessage msg, AddressInterface q);


}
