package utils.communication.communicationHandler.Broadcast.byzantineBroadcast;

import AtomicInterface.communication.address.AddressInterface;
import AtomicInterface.communication.communicationHandler.Broadcast;
import AtomicInterface.communication.groupConstitution.ProcessInterface;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

// broadcast that sends nothing
public class OmissiveSymmetricBroadcast implements Broadcast
{
    public CompletableFuture<Void> broadcast(byte[] msgPayload,
                                             Map<? extends AddressInterface, ? extends ProcessInterface> groupCon)
    {
        return CompletableFuture.completedFuture(null);
    }
}
