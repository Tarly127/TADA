package AtomicInterface.communication;

import AtomicInterface.communication.address.AddressInterface;

public interface Message
{
    AddressInterface getSender();
    Byte getType();
}
