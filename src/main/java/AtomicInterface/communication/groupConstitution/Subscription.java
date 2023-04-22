package AtomicInterface.communication.groupConstitution;

/**
 * Object that defines a subscription to a particular set of message types. It should be able to signal that new
 * messages of the wanted types have been received and also to block until said messages are received.
 */
public interface Subscription
{
    /**
     * Block until a signal is received showing that a message of one of the wanted types as been received.
     */
    void waitForMessage  ();

    /**
     * Signal the reception of a message of one of the wanted types-
     */
    void signalNewMessage();

}
