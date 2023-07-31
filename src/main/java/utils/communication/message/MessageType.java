package utils.communication.message;

public class MessageType
{
    // Integration Message types
    public static final byte INTEGRATION = 1;
    public static final byte JOIN        = 2;
    public static final byte READY       = 3;
    // Approximation Message types (asynch)
    public static final byte ASYNCH_INITIALIZATION = 5;
    public static final byte ASYNCH_APPROXIMATION  = 6;
    public static final byte ASYNCH_HALTED         = 7;
    // Approximation Message types (synch)
    public static final byte SYNCH_INITIALIZATION = 15;
    public static final byte SYNCH_APPROXIMATION  = 16;
    public static final byte SYNCH_HALTED         = 17;
    // Approximation Message types (fca)
    public static final byte FCA_INITIALIZATION = 25;
    public static final byte FCA_APPROXIMATION  = 26;
    public static final byte FCA_HALTED         = 27;
    // Approximation Message types (bso)
    // public static final byte BSO_INITIALIZATION = 35;
    // public static final byte BSO_APPROXIMATION  = 36;
    // public static final byte BSO_HALTED         = 37;
    // Generic Consensus Skeleton Types
    public static final byte GCS_INITIALIZATION = 115;
    public static final byte GCS_APPROXIMATION  = 116;
    public static final byte GCS_HALTED         = 117;
    public static final byte GCS_RETRANSMISSION = 118;
    // Generic message types that can be used by the programmer
    public static final byte USER_1             = 85;
    public static final byte USER_2             = 86;
    public static final byte USER_3             = 87;
    public static final byte USER_4             = 88;
    public static final byte USER_5             = 89;
    public static final byte USER_6             = 90;
    public static final byte USER_7             = 91;
    public static final byte USER_8             = 92;
    public static final byte USER_9             = 93;
    public static final byte USER_0             = 94;

    // Other
    public static final byte UNDEFINED               = 0;
    public static final byte ECHO                    = 126;
    public static final byte CRUSADER_RETRANSMISSION = 127;
    public static final byte TERMINATE               = -128;

    public static String typeString(byte type)
    {
        return switch (type)
                {
                    case INTEGRATION ->
                            "INTEGRATION";
                    case JOIN ->
                            "JOIN";
                    case READY ->
                            "READY";
                    case SYNCH_INITIALIZATION, FCA_INITIALIZATION/*, BSO_INITIALIZATION*/, ASYNCH_INITIALIZATION,
                            GCS_INITIALIZATION ->
                            "INITIALIZATION";
                    case SYNCH_APPROXIMATION, FCA_APPROXIMATION/*, BSO_APPROXIMATION*/, ASYNCH_APPROXIMATION,
                            GCS_APPROXIMATION ->
                            "APPROXIMATION";
                    case SYNCH_HALTED, FCA_HALTED/*, BSO_HALTED*/, ASYNCH_HALTED, GCS_HALTED ->
                            "HALTED";
                    case ECHO ->
                        "ECHO";
                    case CRUSADER_RETRANSMISSION, GCS_RETRANSMISSION ->
                            "RETRANSMISSION";
                    case UNDEFINED ->
                            "UNDEFINED";
                    case TERMINATE ->
                            "TERMINATE";
                    default ->
                            "N/A";
                };
    }

}
