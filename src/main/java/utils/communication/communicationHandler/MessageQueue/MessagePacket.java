package utils.communication.communicationHandler.MessageQueue;

import AtomicInterface.communication.address.AddressInterface;
import org.javatuples.Triplet;
import utils.communication.message.ExpectedMessageSize;

import java.util.Arrays;
import java.util.Objects;

public final class MessagePacket
{
    private final AddressInterface senderAddress;
    private final byte[] msgPayload;
    private final byte[] instanceID;
    private final byte msgType;

    private final static int INSTANCE_HEADER_SIZE = ExpectedMessageSize.INSTANCE_ID_HEADER_SIZE;

    MessagePacket(byte[] msgPayload, byte[] instanceID, byte msgType, AddressInterface senderAddress)
    {
        this.msgPayload = msgPayload;
        this.msgType = msgType;
        this.senderAddress = senderAddress;
        this.instanceID = instanceID;
    }


    Triplet<AddressInterface, byte[], Byte> toTriplet()
    {
        return new Triplet<>(senderAddress, msgPayload, msgType);
    }

    static MessagePacket fromTriplet(Triplet<AddressInterface, byte[], Byte> triplet)
    {
        byte[] header = Arrays.copyOfRange(triplet.getValue1(), 0, INSTANCE_HEADER_SIZE);
        byte[] payload = Arrays.copyOfRange(triplet.getValue1(), INSTANCE_HEADER_SIZE, triplet.getValue1().length);

        return new MessagePacket(payload, header, triplet.getValue2(), triplet.getValue0());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessagePacket that = (MessagePacket) o;
        return msgType == that.msgType
                && senderAddress.equals(that.senderAddress)
                && Arrays.equals(msgPayload, that.msgPayload)
                && Arrays.equals(this.instanceID, that.instanceID);
    }

    @Override
    public int hashCode()
    {
        int result = Objects.hash(senderAddress, msgType);
        result = 31 * result + Arrays.hashCode(msgPayload);
        result = 31 * result + Arrays.hashCode(instanceID);
        return result;
    }

    public byte[] getInstanceID()
    {
        return instanceID;
    }

    public byte getMsgType()
    {
        return msgType;
    }
}
