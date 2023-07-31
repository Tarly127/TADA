package Interface.communication.communicationHandler;

import Interface.communication.address.AddressInterface;
import Interface.communication.groupConstitution.OtherNodeInterface;
import org.javatuples.Triplet;
import utils.communication.groupConstitution.GroupConstitution;
import utils.consensus.ids.InstanceID;

import java.util.Collection;

/**
 * Object that should keep "track" of the constitution of the communication group, while also serving as the manager
 * for incoming messages. To an object that calls upon the CommunicationManager in order to be fed messages we call a
 * "subscriber".
 */
public interface CommunicationManager
{
    /**
     * Get the next message tagged with any of the wanted types. If no such message exists, the method should block
     * until one is available. This consumes the message.
     * @param acceptableTypesReg Subscription defining the wanted types
     * @return Triplet containing the address of the sender, the payload of the message, and the actual type of the
     * message
     */
    Triplet<AddressInterface, byte[], Byte> getNextMessage(Subscription acceptableTypesReg);

    /**
     * Add a new process to the group, identified by its address
     * @param address Address of the process
     * @param process Actual process
     */
    void addToGroup(AddressInterface address, OtherNodeInterface process);

    /**
     * Register a new group of types of messages that this manager should keep track of. If any of the types are
     * already registered, then no valid registration is made.
     * @param wantedTypes Set containing the wanted message types.
     * @return New registration. If no valid registration was made, then it should return NULL.
     */
    Subscription getRegistration(Collection<Byte> wantedTypes);

    /**
     * Add further types to an existing subscription
     * @param newTypes types to add to subscription
     * @param subscription Existing subscription
     * @return True if at least one type was added, false otherwise
     */
    boolean addTypesToRegistration(Collection<Byte> newTypes, Subscription subscription);

    /**
     * Register a new group of types of messages that this manager shoudl keep track of, associated to a specific
     * InstanceID. If this instanceID already has a registration, then no new registration is made. If any of the new
     * message types are present in a previous registration, as long as the instanceID is new, then a new
     * valid registration must be produced.
     * @param wantedTypes Set containing the wanted message types
     * @param instanceID Object defining a unique identifier for the insterested subscriber.
     * @return New registration. If no valid registration was made, then it should return NULL.
     */
    Subscription getRegistration(Collection<Byte> wantedTypes, InstanceID instanceID);

    /**
     * Get the object that defines the communication group
     * @return the group constitution object
     */
    GroupConstitution getGroupConstitution();
}
