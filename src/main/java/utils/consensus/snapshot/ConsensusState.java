package utils.consensus.snapshot;

import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.Broadcast;
import utils.communication.communicationHandler.Broadcast.AsynchBroadcast;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.groupConstitution.Process;
import utils.communication.groupConstitution.ProcessStatus;
import utils.communication.message.ApproximationMessage;
import utils.communication.serializer.MessageSerializer;
import utils.consensus.ids.InstanceID;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ConsensusState
{
    public      Integer H;
    public final Integer n;
    public final Integer t;
    public final Double epsilon;
    public final Map<AddressInterface, ProcessStatus> groupState;
    public final Broadcast broadcast;

    private final MessageSerializer<ApproximationMessage> serializer;
    private final InstanceID instanceID;


    public ConsensusState(int n,
                          int t,
                          double epsilon,
                          GroupConstitution groupConstitution,
                          MessageSerializer<ApproximationMessage> serializer,
                          InstanceID instanceID)
    {
        this.H = null;
        this.n = n;
        this.t = t;
        this.epsilon = epsilon;
        this.groupState = new HashMap<>();
        this.broadcast = new AsynchBroadcast();
        this.serializer = serializer;
        this.instanceID = instanceID;


        if(groupConstitution != null)
            groupConstitution.forEach((address, process)->
                    this.groupState.put(address, new ProcessStatus((Process) process)));
    }

    public ConsensusState(int n,
                          int t,
                          double epsilon,
                          GroupConstitution groupConstitution,
                          Broadcast broadcast,
                          MessageSerializer<ApproximationMessage> serializer,
                          InstanceID instanceID)
    {
        this.H = null;
        this.n = n;
        this.t = t;
        this.epsilon = epsilon;
        this.groupState = new HashMap<>();
        this.broadcast = broadcast;
        this.instanceID = instanceID;
        this.serializer = serializer;


        if(groupConstitution != null)
            groupConstitution.forEach((address, process)->
                    this.groupState.put(address, new ProcessStatus((Process) process)));
    }

    public <T extends ConsensusState> ConsensusState(T other)
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
                    this.groupState.put(address, new ProcessStatus((Process) process)));
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


    public MessageSerializer<ApproximationMessage> getSerializer() {
        return serializer;
    }

    public InstanceID getInstanceID() {
        return instanceID;
    }

    public final CompletableFuture<Void> Broadcast(ApproximationMessage msg)
    {
        return this.broadcast.broadcast(
                this.serializer.encodeWithHeader(msg, msg.getType(), this.instanceID),
                this.groupState);
    }
}
