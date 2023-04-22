package AtomicInterface.communication.groupConstitution;

import AtomicInterface.communication.address.AddressInterface;
import org.javatuples.Triplet;
import utils.consensus.ids.InstanceID;

import java.util.Collection;


/**
 * Queue of incoming messages. Each message is tagged with a specific type, and some may be additionally tagged with
 * an instance ID, defining the specific consumer the message is meant for. Enqueueing should be an indiferentiated
 * operation, while dequeueing should be done in regard to a subscription, i.e. a message is only dequeued and fed
 * to a consumer if it is of a wanted type and proper instance ID, if applicable.
 */
public interface MessageQueue
{
    /**
     * Create a new registration, demonstrating interest in incoming messages that contain one of the types
     * provided in acceptableTypes, identified by the registration ID. If any one of the types present in
     * accpetableTypes has been registered before, then no new subscription is created.
     * @param acceptableTypes Set containing the message types the new subsciption should be keeping track.
     * @param regID Integer defining a unique key for the new subscription.
     * @return New subscription, provided no repeated message types are present. If they are, then NULL is returned.
     */
    Subscription getRegistration(final Collection<Byte> acceptableTypes, final int regID);

    /**
     * Create a new registration, demonstrating interest in incoming messages that contain one of the types
     * provided in acceptableTypes, identified by the registration ID, for a specific instance ID. If a previous
     * subscription as been created for the same instance ID, then no new subscription is created.
     * @param acceptableTypes Set containing the message types the new subsciption should be keeping track.
     * @param instanceID Object defining the unique identifier of the consumer (subscriber). Only messages tagged
     *                   with this instance ID will be delivered to this consumer.
     * @param regID Integer defining a unique key for the new subscription.
     * @return New subscription, provided this consumer hasn't made a subscription before. If they have, then NULL is
     * returned.
     */
    Subscription getRegistration(final Collection<Byte> acceptableTypes, final InstanceID instanceID, final int regID);

    /**
     * Add a message, separated into sender address, payload and type, as a triplet, to the queue of messages.
     * @param packet Triplet containing the sender address, payload and message type.
     */
    void enqueue(Triplet<AddressInterface, byte[], Byte> packet);

    /**
     * Retrieve a message from the queue. Should retrieve the oldest message that fits into the needs of a given
     * subscription. If no message is available in the queue that satisfies the subscription, then the method blocks
     * until such a message is available to dequeue.
     * @param subscription Object that describes the type of message wanted, as well as the consumer.
     * @return A message packet, containing the sender address, payload and message type, that fits the
     * subscription's requirements.
     */
    Triplet<AddressInterface, byte[], Byte> dequeue(Subscription subscription);
}
