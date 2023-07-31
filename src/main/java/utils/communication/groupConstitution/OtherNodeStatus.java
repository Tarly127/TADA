package utils.communication.groupConstitution;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import org.javatuples.Pair;

public final class OtherNodeStatus extends OtherNode
{
    private boolean completed;
    private boolean active;
    private Double  vOnCompletion;
    private Integer completionRound;

    public OtherNodeStatus(OtherNode process)
    {
        super(process);
        this.completed = false;
        this.active = true;
        this.vOnCompletion = null;
    }

    public OtherNodeStatus(OtherNodeStatus processStatus)
    {
        super(processStatus);
        this.completed = false;
        this.active = processStatus.active;
        this.vOnCompletion = null;
        this.completionRound = null;
    }

    public OtherNode process() {return super.shallowClone();}

    public boolean isCompleted() {return completed;}

    public boolean isActive() {return active && !super.isFaulty();}

    public void setActive(boolean active) {this.active = active;}

    public Pair<Double, Integer> getvOnCompletion()
    {
        if(vOnCompletion != null && completionRound != null)
            return new Pair<>(vOnCompletion, completionRound);
        else
            return null;
    }

    public boolean isOther() {return super.isOther();}

    public void complete(double vOnCompletion, int completionRound)
    {
        this.completionRound = completionRound;
        this.completed = true;
        this.vOnCompletion = vOnCompletion;
    }

    public void reset()
    {
        this.completed = false;
        this.vOnCompletion = null;
        this.completionRound = null;
    }

    public <T> void safeWrite(ByteBuffer bf, T attachment, CompletionHandler<Integer, ? super T> handler)
    {
        super.safeWrite(bf, attachment, handler);
    }

    public <T> void safeRead(ByteBuffer bf, T attachment, CompletionHandler<Integer, ? super T> handler)
    {
        super.safeRead(bf, attachment, handler);
    }
}
