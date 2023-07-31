package utils.communication.groupConstitution;


import Interface.communication.groupConstitution.OtherNodeInterface;
import utils.communication.message.ExpectedMessageSize;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class OtherNode implements OtherNodeInterface
{
    private static final int BYTE_BUFFER_SIZE =
            ExpectedMessageSize.KRYO_SMALL_MESSAGE_SIZE_WITH_HEADER;

    private static class OutgoingPacket
    {
        final CompletableFuture<Integer> writeFuture;
        final byte[] payload;

        OutgoingPacket(byte[] payload, CompletableFuture<Integer> future)
        {
            this.payload     = payload;
            this.writeFuture = future;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof OutgoingPacket that)) return false;
            return writeFuture.equals(that.writeFuture) && Arrays.equals(payload, that.payload);
        }

        @Override
        public int hashCode()
        {
            int result = Objects.hash(writeFuture);
            result = 31 * result + Arrays.hashCode(payload);
            return result;
        }
    }
    private static class OutgoingMessageQueue
    {
        private final Queue<OutgoingPacket> outgoingPacketQueue;
        private final ReentrantLock queueLock;
        private final Condition notEmpty;

        OutgoingMessageQueue()
        {
            this.outgoingPacketQueue = new LinkedList<>();
            this.queueLock           = new ReentrantLock(true);
            this.notEmpty            = this.queueLock.newCondition();
        }

        public void enqueue(byte[] payload, CompletableFuture<Integer> writeFuture)
        {
            this.queueLock.lock();
            this.outgoingPacketQueue.offer(new OutgoingPacket(payload, writeFuture));
            this.notEmpty.signalAll();
            this.queueLock.unlock();
        }

        public OutgoingPacket dequeue()
        {
            this.queueLock.lock();

            if(this.outgoingPacketQueue.isEmpty())
            {
                try
                {
                    this.notEmpty.await();
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

            var packet = this.outgoingPacketQueue.poll();

            this.queueLock.unlock();

            return packet;
        }
    }
    private static class WriteAttachment
    {
        OutgoingPacket packet;
        boolean isWriteable;

        WriteAttachment(OutgoingPacket packet)
        {
            this.packet      = packet;
            this.isWriteable = packet != null
                            && packet.payload != null
                            && packet.payload.length <= BYTE_BUFFER_SIZE;
        }

    }
    private final CompletionHandler<Integer, WriteAttachment> writeHandler = new CompletionHandler<>()
    {
        @Override
        public void completed(Integer result, WriteAttachment attachment)
        {
            if (isOther())
            {
                try
                {
                    if (attachment != null)
                        // complete future
                        attachment.packet.writeFuture.complete(result);

                    if(writerThread != null && unwrittenMessages != null)
                    {
                        writerThread.submit(() -> {
                            // fetch next write packet
                            var packet = unwrittenMessages.dequeue();
                            // prepare the attachment
                            WriteAttachment newAttachment = new WriteAttachment(packet);

                            if (newAttachment.isWriteable && writeBuffer != null && clientChannel != null)
                            {
                                // prepare the buffer
                                writeBuffer.clear();
                                writeBuffer.put(newAttachment.packet.payload);
                                writeBuffer.flip();
                                // write to the socket
                                clientChannel.write(writeBuffer, newAttachment, this);
                            }
                            else
                                writeHandler.failed(null, newAttachment);
                        });
                    }
                }
                catch (Throwable e)
                {
                    e.printStackTrace();
                }
            }
        }
        @Override
        public void failed(Throwable exc, WriteAttachment attachment)
        {
            if(exc == null)
            {
                // means the payload was too big for our buffer size
                attachment.packet.writeFuture.complete(-1);
            }
            else
            {
                exc.printStackTrace();
                if(attachment != null)
                    // complete the future
                    attachment.packet.writeFuture.completeExceptionally(exc);
                // continue writing other packets
                writeHandler.completed(0, null);
            }
        }
    };



    private boolean isFaulty;
    private final OutgoingMessageQueue      unwrittenMessages;
    private final AsynchronousSocketChannel clientChannel;
    private final ByteBuffer                writeBuffer;
    private final ExecutorService           writerThread;
    private final ExecutorService           completerThread;

    public OtherNode()
    {
        this.isFaulty = false;
        this.clientChannel = null;
        this.writeBuffer = null;
        this.unwrittenMessages = null;
        this.writerThread = null;
        this.completerThread = null;
    }

    public OtherNode(AsynchronousSocketChannel clientChannel)
    {
        this.isFaulty          = false;
        this.clientChannel     = clientChannel;
        this.unwrittenMessages = new OutgoingMessageQueue();
        this.writeBuffer       = ByteBuffer.allocateDirect(BYTE_BUFFER_SIZE);
        this.writerThread      = Executors.newSingleThreadExecutor();
        this.completerThread   = Executors.newSingleThreadExecutor();
        
        this.writerThread.submit(this::writeNextMessage);
    }

    public OtherNode(OtherNode other)
    {
        this.isFaulty          = other.isFaulty;
        this.clientChannel     = other.clientChannel;
        this.unwrittenMessages = other.unwrittenMessages;
        this.writeBuffer       = other.writeBuffer;
        this.writerThread      = other.writerThread;
        this.completerThread   = other.completerThread;
    }



    private void writeNextMessage()
    {
        try
        {
            // get the oldest packet in the queue
            var packet = this.unwrittenMessages.dequeue();
            // prepare the attachment
            var attachment = new WriteAttachment(packet);
            // check that we can indeed write this packet
            if (attachment.isWriteable)
            {
                // prepare the byteBuffer
                this.writeBuffer.put(packet.payload);
                this.writeBuffer.flip();
                // write to the socket
                this.clientChannel.write(this.writeBuffer, attachment, writeHandler);
            }
            else
                // if it can't be written, go to failure
                writeHandler.failed(null, attachment);
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
    }

    public boolean isOther()
    {
        return this.clientChannel != null && writeBuffer != null && unwrittenMessages != null;
    }

    public boolean isFaulty()
    {
        return isFaulty;
    }

    public void markAsFaulty()
    {
        this.isFaulty = true;
    }

    public void markAsFaulty(boolean isFaulty)
    {
        this.isFaulty = isFaulty;
    }

    private byte[] getArray(ByteBuffer bf)
    {
        if(bf.isDirect())
        {
            byte[] data = new byte[BYTE_BUFFER_SIZE];
            bf.clear();
            bf.get(0, data);

            return data;
        }
        else return bf.array();
    }

    public <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler)
    {
        if(this.isOther())
            this.clientChannel.connect(remote, attachment, handler);
    }

    public <A> void safeWrite(ByteBuffer bf, A attachment, CompletionHandler<Integer, ? super A> handler)
    {
        if(this.clientChannel != null && this.isOther() && this.isWriteable())
        {
            
            // create new future corresponding to this write;
            CompletableFuture<Integer> future = new CompletableFuture<>();
            // push the new packet to the queue
            // check if it is a DirectByteBuffer and retrieve data accordingly (DirectBytBuffer does not have
            // underlying array and will produce UnsupportedOperationException on invocation of array())
            this.unwrittenMessages.enqueue(getArray(bf), future);
            // call handler completed/failed on future completion
            future.handleAsync((bytesWritten, exception)->
            {
                // failed
                if(exception != null)
                    handler.failed(exception, attachment);
                // success
                else
                    handler.completed(bytesWritten, attachment);

                return null;
            }, this.completerThread);
        }
        else
            handler.completed(0, attachment);
    }

    public void safeWrite(ByteBuffer bf)
    {
        if(this.clientChannel != null && this.isOther() && this.isWriteable())
        {
            try
            {
                // create new future corresponding to this write;
                CompletableFuture<Integer> future = new CompletableFuture<>();
                // push the new packet to the queue
                // check if it is a DirectByteBuffer and retrieve data accordingly (DirectBytBuffer does not have
                // underlying array and will produce UnsupportedOperationException on invocation of array())
                this.unwrittenMessages.enqueue(getArray(bf), future);
                // block until done
                future.get();
            }
            catch (Throwable e)
            {
                e.printStackTrace();
            }
        }
    }

    public <A> void safeRead(ByteBuffer bf, A attachment, CompletionHandler<Integer, ? super A> handler)
    {
        if(this.clientChannel != null && this.isOther() && clientChannel.isOpen())
        {
            clientChannel.read(bf, attachment, handler);
        }
        else
        {
            handler.completed(0, attachment);
        }
    }

    @Override
    public void close() throws IOException
    {
        this.clientChannel.close();
    }

    @Override
    public boolean isOpen()
    {
        return this.clientChannel.isOpen();
    }

    public boolean isReadable()
    {
        return this.clientChannel != null && this.clientChannel.isOpen();
    }

    public boolean isWriteable()
    {
        return this.clientChannel != null && this.clientChannel.isOpen();
    }

    public InetSocketAddress getRemoteAddress()
    {
        try
        {
            return this.clientChannel != null ? (InetSocketAddress) this.clientChannel.getRemoteAddress() : null;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    public OtherNode shallowClone()
    {
        return this;
    }
}
