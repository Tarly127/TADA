package utils.communication.communicationHandler;

import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.CommunicationManager;
import Interface.communication.groupConstitution.OtherNodeInterface;
import Interface.communication.communicationHandler.MessageQueue;
import Interface.communication.communicationHandler.Subscription;
import utils.communication.communicationHandler.MessageQueue.IncomingMessageQueue;
import utils.consensus.ids.InstanceID;
import org.javatuples.Triplet;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.message.ExpectedMessageSize;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

// Assumes that received processes are already connected
public class AsynchMessageManager implements CommunicationManager
{
    private static final int BUFFER_CAPACITY = ExpectedMessageSize.KRYO_SMALL_MESSAGE_SIZE_WITH_HEADER;


    private final GroupConstitution                     groupConstitution;
    private final MessageQueue                          msgQueue;
    private final AtomicInteger                         regID;

    private final static class ProcessAttachment
    {
        public final OtherNodeInterface otherProcess;
        public final ByteBuffer buffer;
        public final AddressInterface    otherProcessAddress;
        public long startTime;

        ProcessAttachment(OtherNodeInterface otherProcess, AddressInterface otherProcessAddress)
        {
            this.otherProcess        = otherProcess;
            this.otherProcessAddress = otherProcessAddress;
            this.buffer              = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
            this.startTime = 0;
        }
    }

    private final CompletionHandler<Integer, ProcessAttachment> readHandler
            = new CompletionHandler<>()
    {
        @Override
        public void completed(Integer result, ProcessAttachment attachment)
        {
            try
            {
                if (result != null && result > 0)
                {
                    // always flip the buffer!
                    attachment.buffer.flip();
                    // get the message that came through
                    byte[] msgPayload = new byte[result];
                    attachment.buffer.get(msgPayload);
                    // store the message
                    enqueue(new Triplet<>(
                            attachment.otherProcessAddress,
                            Arrays.copyOfRange(msgPayload, 1, result),
                            msgPayload[0]));

                    // clear buffer, else there may problems at next read
                    attachment.buffer.clear();

                    // continue read loop, if the connection still supports reading
                    if (attachment.otherProcess.isReadable())
                        attachment.otherProcess.safeRead(attachment.buffer, attachment, this);
                }
                else
                    remove(attachment);
            }
            catch (Throwable ignored) {}
        }

        @Override
        public void failed(Throwable exc, ProcessAttachment attachment)
        {
            // we will eventually wind up here if a connection is reset! handle accordingly
            if(attachment.otherProcess.isReadable())
                // Continue the read loop for this connection, if it remained open
                attachment.otherProcess.safeRead(attachment.buffer, attachment, this);
        }
    };


    /* ************ */
    /* CONSTRUCTORS */
    /* ************ */

    public AsynchMessageManager(GroupConstitution groupCon)
    {
        this.groupConstitution         = groupCon;
        this.msgQueue                  = new IncomingMessageQueue();
        this.regID                     = new AtomicInteger(0);
        AtomicInteger ignoreTimeout    = new AtomicInteger(0);
    }

    private void setUpListener(AddressInterface addr, OtherNodeInterface process)
    {
        if(process.isOther())
        {
            var attachment = new ProcessAttachment(process, addr);

            process.safeRead(attachment.buffer, attachment, readHandler);
        }
    }

    // get a new registration, if none of the given types have already been registered
    public Subscription getRegistration(Collection<Byte> acceptableTypes)
    {
        return msgQueue.getSubscription(acceptableTypes, regID.getAndIncrement());
    }

    public Subscription getRegistration(Collection<Byte> acceptableTypes, InstanceID instanceID)
    {
        return msgQueue.getSubscription(acceptableTypes, instanceID, regID.getAndIncrement());
    }

    // starts listening for messages in the communication group
    public void open()
    {
        this.groupConstitution.forEach(this::setUpListener);
    }

    // returns the oldest message in queue with any of the given types
    // waits if there are no messages in queue
    public Triplet<AddressInterface, byte[], Byte> getNextMessage(Subscription acceptableTypesSubscription)
    {
        return this.msgQueue.dequeue(acceptableTypesSubscription);
    }

    // registers a new member in the group if the given address is not yet registered, and sets up his listener
    public void addToGroup(AddressInterface address, OtherNodeInterface process)
    {
        // add process to group
        if(!this.groupConstitution.containsKey(address))
        {
            this.groupConstitution.put(address, process);

            // only setup listener for a process different from ourselves
            if(process.isOther())
            {
                setUpListener(address, process);
            }

        }

    }

    // feeds a packet directly into the queue
    private void enqueue(Triplet<AddressInterface, byte[], Byte> packet)
    {
        // feed packet to the queue
        this.msgQueue.enqueue(packet);
    }

    // get group constitution
    public GroupConstitution getGroupConstitution()
    {
        return this.groupConstitution;
    }

    // add more types to the subscription
    public boolean addTypesToRegistration(Collection<Byte> newTypes, Subscription subscription)
    {
        return this.msgQueue.addTypesToRegistration(newTypes, subscription);
    }

    // remove process from group
    private void remove(ProcessAttachment attachment) throws IOException
    {
        if(this.groupConstitution.containsKey(attachment.otherProcessAddress))
        {
            attachment.otherProcess.close();
            this.groupConstitution.remove(attachment.otherProcessAddress);
        }
    }
}
