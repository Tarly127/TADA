package Interface.communication;

import Interface.communication.address.AddressInterface;

<<<<<<< HEAD
/**
 * Basic message interface.
 */
public interface Message
{
    /**
     * Get the address of the message's sender. May not include full host +  address AND it may come hashed
     * @return Address of the sender
     */
    AddressInterface getSender();

    /**
     * Get the Byte representing the type of the message
     * @return message type
     */
=======
public interface Message
{
    AddressInterface getSender();
>>>>>>> FixingFinalDissertationVersion
    Byte getType();
}
