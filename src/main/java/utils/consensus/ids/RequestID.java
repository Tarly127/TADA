package utils.consensus.ids;

import AtomicInterface.communication.address.AddressInterface;

import java.io.Serializable;
import java.util.Objects;


public class RequestID implements Serializable
{
    public int requesterHash;
    public int internalID;

    public RequestID(AddressInterface myAddress, int internalID)
    {
        this.internalID    = internalID;
        this.requesterHash = myAddress.hashCode();
    }

    public RequestID()
    {
        this.internalID    = -1;
        this.requesterHash = -1;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof RequestID that)) return false;

        return internalID == that.internalID &&
                requesterHash == that.requesterHash;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(requesterHash, internalID);
    }

    @Override
    public String toString()
    {
        return "RequestID{" +
                "requesterPort=" + requesterHash +
                ", internalID=" + internalID +
                '}';
    }
}
