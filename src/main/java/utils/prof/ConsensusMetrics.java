package utils.prof;

import utils.communication.address.Address;
import utils.consensus.ids.RequestID;

import java.util.concurrent.atomic.AtomicInteger;

public class ConsensusMetrics
{
    public RequestID     reqID;
    public int           expectedH;
    public int           realH;
    public int           timedOutRounds;
    public long          texecNanos;
    public AtomicInteger neededMsgs;
    public AtomicInteger processedMsgs;
    public double        finalV;
    public double        startingV;


    public ConsensusMetrics()
    {
        this.reqID          = new RequestID(new Address(-1), 0);
        this.expectedH      = 0;
        this.realH          = 0;
        this.texecNanos     = 0;
        this.timedOutRounds = 0;
        this.startingV      = 0;
        this.finalV          = 0;
        this.neededMsgs     = new AtomicInteger(0);
        this.processedMsgs  = new AtomicInteger(0);



    }
}
