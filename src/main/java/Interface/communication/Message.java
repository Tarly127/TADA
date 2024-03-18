package Interface.communication;

import Interface.communication.address.AddressInterface;

public interface Message
{
    AddressInterface getSender();
    Byte getType();
}
