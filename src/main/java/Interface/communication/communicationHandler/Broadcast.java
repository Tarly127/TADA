package Interface.communication.communicationHandler;

import Interface.communication.address.AddressInterface;
import Interface.communication.groupConstitution.OtherNodeInterface;

import java.util.Map;
import java.util.concurrent.CompletableFuture;


/**
 * Object that should encapsulate the business logic underlying a broadcast algorithm
 */
public interface Broadcast
{
    /**
     * Send the message encoded in payload to every Process in groupConstitution asynchronously. Completes when every
     * write operation for each process connection completes.
     * @param payload Encoded message
     * @param groupConstitution Group constituion, each process and their address
     * @return A future that is completed when broadcasting is done
     */
    CompletableFuture<Void> broadcast(byte[] payload, Map<? extends AddressInterface,
                                      ? extends OtherNodeInterface> groupConstitution);
}
