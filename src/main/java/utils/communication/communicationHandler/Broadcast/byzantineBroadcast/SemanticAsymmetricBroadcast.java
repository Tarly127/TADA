package utils.communication.communicationHandler.Broadcast.byzantineBroadcast;

import AtomicInterface.communication.address.AddressInterface;
import AtomicInterface.communication.communicationHandler.Broadcast;
import AtomicInterface.communication.groupConstitution.ProcessInterface;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.ExpectedMessageSize;
import utils.communication.serializer.MessageSerializer;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * For fault tolerance testing
 * Simulates a broadcast
 */
public class SemanticAsymmetricBroadcast implements Broadcast
{
    private static final int BYTE_BUFFER_CAPACITY = ExpectedMessageSize.KRYO_SMALL_MESSAGE_SIZE_WITH_HEADER;
    private static final int CHANCE_TO_SCRAMBLE       = 3;

    public CompletableFuture<Void> broadcast(byte[] msgPayload,
                                             Map<? extends AddressInterface, ? extends ProcessInterface> groupCon)
    {
        try
        {
            // random to scramble the message contents
            Random r = new Random();
            // serializer
            MessageSerializer<ApproximationMessage> serializer = new MessageSerializer<>(ApproximationMessage.class);
            // get the contents of the message that was attempted to be sent
            ApproximationMessage msg = serializer.decodeWithHeader(msgPayload);
            // Completion stage of this broadcast
            CompletableFuture<Void> completeBroadcast = new CompletableFuture<>();
            // Byte Buffer to store broadcast message
            ByteBuffer buffer = ByteBuffer.allocateDirect(BYTE_BUFFER_CAPACITY);
            // Get a list containing every pair of addresses and processes to send to
            var receivers = new ArrayList<>(groupCon.entrySet());
            // Establish the recursive broadcast loop's write handler
            final CompletionHandler<Integer, Integer> broadcastHandler = new CompletionHandler<>()
            {
                @Override
                public void completed(Integer result, Integer index)
                {
                    if (result >= 0)
                    {
                        // check if we completed the broadcast and, if we did, complete the future
                        if (index >= groupCon.size())
                            completeBroadcast.complete(null);
                        else
                        {

                            // write to buffer
                            buffer.clear();
                            // scramble the message's contents before sending
                            msg.v = (r.nextInt() % CHANCE_TO_SCRAMBLE == 0) ? r.nextDouble() : msg.v;
                            // encode it
                            byte[] scrambledPayload = serializer.encodeWithHeader(msg, msg.getType());
                            // write the first element to buffer
                            buffer.put(scrambledPayload);
                            buffer.flip();
                            // send
                            receivers.get(index).getValue().safeWrite(buffer, index + 1, this);
                        }
                    }
                }

                @Override
                public void failed(Throwable exc, Integer index)
                {
                    // check if we completed the broadcast and, if we did, complete the future
                    if (index >= groupCon.size())
                    {
                        completeBroadcast.complete(null);
                    }
                    // if not, continue sending to each process, one by one
                    else
                    {
                        // write to buffer
                        buffer.clear();
                        // scramble the message's contents before sending
                        msg.v = (r.nextInt() % CHANCE_TO_SCRAMBLE == 0) ? r.nextDouble() : msg.v;
                        // encode it
                        byte[] scrambledPayload = serializer.encodeWithHeader(msg, msg.getType());
                        // write the first element to buffer
                        buffer.put(scrambledPayload);
                        buffer.flip();
                        // send
                        receivers.get(index).getValue().safeWrite(buffer, index + 1, this);
                    }
                }
            };
            // scramble the message's contents before sending
            msg.v = (r.nextInt() % CHANCE_TO_SCRAMBLE == 0) ? r.nextDouble() : msg.v;
            // encode it
            byte[] scrambledPayload = serializer.encodeWithHeader(msg, msg.getType());
            // write the first element to buffer
            buffer.put(scrambledPayload);
            buffer.flip();

            // send to first element
            if (groupCon.size() > 0)
                receivers.get(0).getValue().safeWrite(buffer, 1, broadcastHandler);
            else
                completeBroadcast.complete(null);

            return completeBroadcast;
        }
        catch (Throwable e)
        {
            return CompletableFuture.completedFuture(null);
        }
    }
}
