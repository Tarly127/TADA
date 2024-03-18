package utils.communication.communicationHandler.Broadcast.byzantineBroadcast;

import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.Broadcast;
import Interface.communication.groupConstitution.OtherNodeInterface;
import utils.communication.message.ExpectedMessageSize;


import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class OmissiveAsymmetricBroadcast implements Broadcast
{
    private static final int BYTE_BUFFER_CAPACITY = ExpectedMessageSize.KRYO_SMALL_MESSAGE_SIZE_WITH_HEADER;
    private static final int CHANCE_TO_SEND       = 3;

    public  CompletableFuture<Void> broadcast(byte[] msgPayload,
                                              Map<? extends AddressInterface, ? extends OtherNodeInterface> groupCon)
    {
        try
        {
            // create a new random to decide whether to send a message to a process or not
            Random r = new Random();
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
                            buffer.put(msgPayload);
                            buffer.flip();
                            // send
                            if(r.nextInt() % CHANCE_TO_SEND == 0)
                                receivers.get(index).getValue().safeWrite(buffer, index + 1, this);
                            else
                                this.completed(1, index + 1);
                        }
                    }
                }

                @Override
                public void failed(Throwable exc, Integer index)
                {
                    // check if we completed the broadcast and, if we did, complete the future
                    if (index >= groupCon.size())
                        completeBroadcast.complete(null);
                    // if not, continue sending to each process, one by one
                    else
                    {
                        // write to buffer
                        buffer.clear();
                        buffer.put(msgPayload);
                        buffer.flip();
                        // send
                        if(r.nextInt() % CHANCE_TO_SEND == 0)
                            receivers.get(index).getValue().safeWrite(buffer, index + 1, this);
                        else
                                this.completed(1, index + 1);
                    }
                }
            };

            // write the first element to buffer
            buffer.put(msgPayload);
            buffer.flip();
            // send to first element
            if (groupCon.size() > 0)
                if(r.nextInt() % CHANCE_TO_SEND == 0)
                    receivers.get(0).getValue().safeWrite(buffer, 1, broadcastHandler);
                else
                    broadcastHandler.completed(1,1);
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
