package Interface.communication.groupConstitution;


import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannel;
import java.nio.channels.CompletionHandler;

/**
 * Represents connection to another member of the communication group, along with its status (whether it is open,
 * writeable, readable, known/suspected faulty, etc.). Similar interface to AsynchronousSocketChannels.
 */
public interface OtherNodeInterface extends AsynchronousChannel
{
    /**
     * Whether this GroupMember represents another process or ourselves.
     * @return True if it is not the calling process, false otherwise
     */
    boolean isOther();

    /**
     * Whether this GroupMember is known/suspected faulty or not.
     * @return True if at least suspected faulty, false otherwise
     */
    boolean isFaulty();

    /**
     * Mark a process as being faulty
     */
    void markAsFaulty();

    /**
     * Change a process's faultiness.
     * @param faultiness Whether the process is faulty or not.
     */
    void markAsFaulty(boolean faultiness);

    /**
     * Asynchronous thread-safe write method. Guarantees no WritePendingException occurs. Does nothing in the case
     * that isOther is false.
     * @param bf The buffer from which bytes are to be transferred
     * @param attachment The object to attach to the I/O operation
     * @param handler The handler for consuming the result
     * @param <A> Type of attachment
     */
    <A> void safeWrite(ByteBuffer bf, A attachment, CompletionHandler<Integer, ? super A> handler);

    /**
     * Blocking thread-safe write method. Guarantees no WritePendingException occurs. Does nothing in the case
     * that isOther is false.
     * @param bf The buffer from which bytes are to be transferred
     */
    void safeWrite(ByteBuffer bf);

    /**
     * Asynchronous thread-safe read method. Does nothing in the case that isOther is false.
     * @param bf The buffer to which bytes are to be transferred
     * @param attachment The object to attach to the I/O operation
     * @param handler The handler for consuming the result
     * @param <A> Type of attachment
     */
    <A> void safeRead(ByteBuffer bf, A attachment, CompletionHandler<Integer, ? super A> handler);

    /**
     * Asynchronous connect.
     * @param remote Address of the remote entity to connect to.
     * @param attachment The object to attach to the I/O operation
     * @param handler The handler for consuming the result
     * @param <A> Type of attachment
     */
    <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler);

    /**
     * Whether a read operation can be completed successfully.
     * @return True is a read operation can be completed, false otherwise.
     */
    boolean isReadable();

    /**
     * Whether a write operation can be completed successfully.
     * @return True is a write operation can be completed, false otherwise.
     */
    boolean isWriteable();

    /**
     * Get the Address associated with the "other end" of a connection
     * @return InetAddress of other end of client socket
     */
    InetSocketAddress getRemoteAddress();
}
