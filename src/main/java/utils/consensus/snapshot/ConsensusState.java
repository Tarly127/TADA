package utils.consensus.snapshot;

import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.Broadcast;
import utils.communication.communicationHandler.Broadcast.AsyncBroadcast;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.groupConstitution.OtherNode;
import utils.communication.groupConstitution.OtherNodeStatus;
import utils.communication.message.ApproximationMessage;
import utils.communication.serializer.MessageSerializer;
import utils.consensus.ids.InstanceID;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ConsensusState
{
    public         Integer H;
    public    final Integer n;
    public    final Integer t;
    public    final Double epsilon;
    protected final Map<AddressInterface, OtherNodeStatus> groupState;
    protected final Broadcast broadcast;

    private final MessageSerializer<ApproximationMessage> serializer;
    private final InstanceID instanceID;


    public ConsensusState(int n,
                          int t,
                          double epsilon,
                          GroupConstitution groupConstitution,
                          MessageSerializer<ApproximationMessage> serializer,
                          InstanceID instanceID)
    {
        this.H          = null;
        this.n          = n;
        this.t          = t;
        this.epsilon    = epsilon;
        this.groupState = new HashMap<>();
        this.broadcast  = new AsyncBroadcast();
        this.serializer = serializer;
        this.instanceID = instanceID;

        if(groupConstitution != null)
            groupConstitution.forEach((address, process)->
                    this.groupState.put(address, new OtherNodeStatus((OtherNode) process)));
    }

    public ConsensusState(int n,
                          int t,
                          double epsilon,
                          GroupConstitution groupConstitution,
                          Broadcast broadcast,
                          MessageSerializer<ApproximationMessage> serializer,
                          InstanceID instanceID)
    {
        this.H          = null;
        this.n          = n;
        this.t          = t;
        this.epsilon    = epsilon;
        this.groupState = new HashMap<>();
        this.broadcast  = broadcast;
        this.serializer = serializer;
        this.instanceID = instanceID;


        if(groupConstitution != null)
            groupConstitution.forEach((address, process)->
                    this.groupState.put(address, new OtherNodeStatus((OtherNode) process)));
    }

    protected <T extends ConsensusState> ConsensusState(T other)
    {
        if(other != null)
        {
            this.H          = other.H;
            this.n          = other.n;
            this.t          = other.t;
            this.epsilon    = other.epsilon;
            this.groupState = other.groupState;
            this.broadcast  = other.broadcast;
            this.serializer = other.getSerializer();
            this.instanceID = other.getInstanceID();


            other.groupState.forEach((address, process) ->
                    this.groupState.put(address, new OtherNodeStatus((OtherNode) process)));
        }
        else
        {
            this.H          = null;
            this.n          = 1;
            this.t          = 0;
            this.epsilon    = 0.0;
            this.groupState = null;
            this.broadcast  = null;
            this.instanceID = null;
            this.serializer = null;
        }
    }

    protected MessageSerializer<ApproximationMessage> getSerializer()
    {
        return this.serializer;
    }

    protected InstanceID getInstanceID()
    {
        return this.instanceID;
    }

    public final CompletableFuture<Void> Broadcast(ApproximationMessage msg)
    {
        return this.broadcast.broadcast(
                this.serializer.encodeWithHeader(msg, msg.getType(), this.instanceID),
                this.groupState);
    }

}
