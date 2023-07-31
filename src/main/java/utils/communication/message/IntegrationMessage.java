package utils.communication.message;

import Interface.communication.Message;
import Interface.communication.address.AddressInterface;

import java.io.Serializable;

public class IntegrationMessage implements Serializable, Message
{
    public final Byte type;
    public final int groupSize;
    public final AddressInterface address;

    public IntegrationMessage(AddressInterface myAddress, Byte type)
    {
        this.address = myAddress;
        this.type          = type;
        this.groupSize     = -1;
    }

    public IntegrationMessage(int groupSize, Byte type)
    {
        this.address = null;
        this.type          = type;
        this.groupSize     = groupSize;
    }

    public IntegrationMessage(Byte type)
    {
        this.address = null;
        this.type          = type;
        this.groupSize     = -1;
    }

    public IntegrationMessage()
    {
        this.address = null;
        this.groupSize     = -1;
        this.type          = MessageType.UNDEFINED;
    }

    public Byte getType()
    {
        return this.type;
    }

    @Override
    public AddressInterface getSender()
    {
        return this.address;
    }

    @Override
    public String toString()
    {
        return "IntegrationMessage{" +
                "type=" + type +
                ", groupSize=" + groupSize +
                ", senderAddress=" + address +
                '}';
    }
}
