package utils.communication.message;

public class ExpectedMessageSize
{
    public static final int HEADER_SIZE                         = 1;
    public static final int KRYO_SMALL_MESSAGE_SIZE             = 64;
    public static final int INSTANCE_ID_HEADER_SIZE             = 20;
    public static final int KRYO_SMALL_MESSAGE_SIZE_WITH_HEADER =
            KRYO_SMALL_MESSAGE_SIZE + HEADER_SIZE + INSTANCE_ID_HEADER_SIZE;
}
