package utils.communication.serializer;

import utils.communication.message.ExpectedMessageSize;
import utils.consensus.ids.InstanceID;

import java.util.Arrays;

public class MessageSerializer<T> extends BasicSerializer<T>
{
    private static final int BUFFER_SIZE      = ExpectedMessageSize.KRYO_SMALL_MESSAGE_SIZE_WITH_HEADER;
    private static final int PAYLOAD_POSITION = ExpectedMessageSize.INSTANCE_ID_HEADER_SIZE + ExpectedMessageSize.HEADER_SIZE;

    public MessageSerializer(Class<T> serializable)
    {
        super(serializable);
    }

    public final byte[] encodeWithHeader(T object, Byte header)
    {
        byte[] fullPayload = new byte[BUFFER_SIZE];
        // add header
        fullPayload[0] = header;
        // no instance ID header
        // get object payload
        byte[] objPayload = super.encode(object);
        // add main body
        System.arraycopy(objPayload, 0, fullPayload, PAYLOAD_POSITION, objPayload.length);

        return fullPayload;
    }

    public final byte[] encodeWithHeader(T object, Byte header, InstanceID instanceID)
    {
        byte[] fullPayload = new byte[BUFFER_SIZE];
        // add header
        fullPayload[0] = header;
        // add the instanceID header
        System.arraycopy(instanceID.getPayload(), 0, fullPayload, 1, ExpectedMessageSize.INSTANCE_ID_HEADER_SIZE);
        // get object payload
        byte[] objPayload = super.encode(object);
        // add main body
        System.arraycopy(objPayload, 0, fullPayload, PAYLOAD_POSITION, objPayload.length);

        return fullPayload;
    }

    public final byte[] encodeWithHeader(T object, Byte header, byte[] instanceID)
    {
        byte[] fullPayload = new byte[BUFFER_SIZE];
        // add header [0,0]
        fullPayload[0] = header;
        // add the instanceID header [1,20]
        System.arraycopy(instanceID, 0, fullPayload, 1, ExpectedMessageSize.INSTANCE_ID_HEADER_SIZE);
        // get object payload
        byte[] objPayload = super.encode(object);
        // add main body [21, 84]
        System.arraycopy(objPayload, 0, fullPayload, PAYLOAD_POSITION, objPayload.length);

        return fullPayload;
    }

    public final T decodeWithHeader(byte[] object)
    {
        int position = object.length - ExpectedMessageSize.KRYO_SMALL_MESSAGE_SIZE;

        return super.decode(Arrays.copyOfRange(object, position, object.length));
    }
}
