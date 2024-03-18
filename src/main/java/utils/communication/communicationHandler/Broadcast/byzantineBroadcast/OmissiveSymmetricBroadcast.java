package utils.communication.communicationHandler.Broadcast.byzantineBroadcast;

import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.Broadcast;
import Interface.communication.groupConstitution.OtherNodeInterface;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

// broadcast that sends nothing
public class OmissiveSymmetricBroadcast implements Broadcast
{
    public CompletableFuture<Void> broadcast(byte[] msgPayload,
                                             Map<? extends AddressInterface, ? extends OtherNodeInterface> groupCon)
    {
        return CompletableFuture.completedFuture(null);
    }
}
