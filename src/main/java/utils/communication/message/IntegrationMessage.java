package utils.communication.message;

import AtomicInterface.communication.Message;
import AtomicInterface.communication.address.AddressInterface;

import java.io.Serializable;

public class IntegrationMessage implements Serializable, Message
{
    public final Byte type;
    public final int groupSize;
    public final AddressInterface senderAddress;

    public IntegrationMessage(AddressInterface myAddress, Byte type)
    {
        this.senderAddress = myAddress;
        this.type          = type;
        this.groupSize     = -1;
    }

    public IntegrationMessage(int groupSize, Byte type)
    {
        this.senderAddress = null;
        this.type          = type;
        this.groupSize     = groupSize;
    }

    public IntegrationMessage(Byte type)
    {
        this.senderAddress = null;
        this.type          = type;
        this.groupSize     = -1;
    }

    public IntegrationMessage()
    {
        this.senderAddress = null;
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
        return this.senderAddress;
    }

    @Override
    public String toString()
    {
        return "IntegrationMessage{" +
                "type=" + type +
                ", groupSize=" + groupSize +
                ", senderAddress=" + senderAddress +
                '}';
    }
}
