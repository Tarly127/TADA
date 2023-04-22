package utils.consensus.snapshot;

import AtomicInterface.communication.address.AddressInterface;
import AtomicInterface.communication.communicationHandler.Broadcast;
import utils.communication.communicationHandler.Broadcast.AsynchBroadcast;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.groupConstitution.Process;
import utils.communication.groupConstitution.ProcessStatus;

import java.util.HashMap;
import java.util.Map;

public class ConsensusState
{
    public      Integer H;
    public final Integer n;
    public final Integer t;
    public final Double epsilon;
    public final Map<AddressInterface, ProcessStatus> groupState;
    public final Broadcast broadcast;

    public ConsensusState(int n,
                          int t,
                          double epsilon,
                          GroupConstitution groupConstitution)
    {
        this.H = null;
        this.n = n;
        this.t = t;
        this.epsilon = epsilon;
        this.groupState = new HashMap<>();
        this.broadcast = new AsynchBroadcast();


        if(groupConstitution != null)
            groupConstitution.forEach((address, process)->
                    this.groupState.put(address, new ProcessStatus((Process) process)));
    }

    public ConsensusState(int n,
                          int t,
                          double epsilon,
                          GroupConstitution groupConstitution,
                          Broadcast broadcast)
    {
        this.H = null;
        this.n = n;
        this.t = t;
        this.epsilon = epsilon;
        this.groupState = new HashMap<>();
        this.broadcast = broadcast;


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
        }
    }


}
