package utils.prof;

import Interface.communication.address.AddressInterface;
import utils.communication.address.Address;
import utils.communication.message.ApproximationMessage;
import utils.communication.message.MessageType;
import utils.consensus.ids.RequestID;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class MessageLogger
{
    public enum MessageEvent
    {
        SEND,
        RECEIVE,
        PROCESS,
        ENQUEUE,
        DEQUEUE
    }

    private static final String CSV_HEAD_SEND = "tid;type;receiverPort;reqIdInternal;Round;startWalltime;duration";
    private static final String CSV_HEAD_RECV = "tid;type;senderPort;reqIdInternal;Round;startWalltime;duration";
    private static final String CSV_HEAD_PROC = CSV_HEAD_RECV;
    private static final String CSV_HEAD_ENQ  = CSV_HEAD_RECV;
    private static final String CSV_HEAD_DEQ  = CSV_HEAD_RECV;

    private static final int IGNORE = 0;


    private static class MessageTreatmentMetrics
    {
        String tid;
        Byte type;
        AddressInterface sender;
        LocalDateTime startOfTreatment;
        Long elapsedTimeNanos; // NANOS BUT WRITTEN AS MILLISECONDS
        RequestID reqID;
        Double vote;
        Integer round;

        String toCSVString()
        {
            if (this.reqID == null)
                return this.tid +
                        ";" +
                        MessageType.typeString(type) +
                        ";" +
                        sender.getPort() +
                        ";;;" +
                        startOfTreatment.toInstant(ZoneOffset.UTC).toEpochMilli() +
                        ";" +
                        (this.elapsedTimeNanos / 1000000.0) +
                        "\n";
            else
                return this.tid +
                        ";" +
                         MessageType.typeString(type) +
                        ";"
                        +
                        sender.getPort() +
                        ";"
                        +
                        reqID.internalID
                        +
                        ";"
                        +
                        round
                        +
                        ";"
                        +
                        startOfTreatment.toInstant(ZoneOffset.UTC).toEpochMilli() +
                        ";"
                        +
                        (this.elapsedTimeNanos / 1000000.0)
                        +
                        "\n";
        }
    }

    private final Map<MessageEvent, List<MessageTreatmentMetrics>> metricsPerEvent;
    private final Map<MessageEvent, ReentrantLock> lockPerEvent;


    public MessageLogger()
    {
        this.metricsPerEvent = new HashMap<>();
        this.lockPerEvent    = new HashMap<>();

        this.metricsPerEvent.put(MessageEvent.RECEIVE, new ArrayList<>());
        this.metricsPerEvent.put(MessageEvent.SEND,    new ArrayList<>());
        this.metricsPerEvent.put(MessageEvent.PROCESS, new ArrayList<>());
        this.metricsPerEvent.put(MessageEvent.ENQUEUE, new ArrayList<>());
        this.metricsPerEvent.put(MessageEvent.DEQUEUE, new ArrayList<>());

        this.lockPerEvent.put(MessageEvent.RECEIVE, new ReentrantLock(true));
        this.lockPerEvent.put(MessageEvent.SEND,    new ReentrantLock(true));
        this.lockPerEvent.put(MessageEvent.PROCESS, new ReentrantLock(true));
        this.lockPerEvent.put(MessageEvent.ENQUEUE, new ReentrantLock(true));
        this.lockPerEvent.put(MessageEvent.DEQUEUE, new ReentrantLock(true));
    }

    public void registerMetric(ApproximationMessage msg,
                                LocalDateTime start,
                                Long elapsedTimeNanos,
                                MessageEvent type)
    {
        if(msg.reqID.internalID >= IGNORE)
        {
            Byte msgType = msg.getType();

            var relevantCollection = this.metricsPerEvent.get(type);
            var relevantLock = this.lockPerEvent.get(type);

            var metrics = new MessageTreatmentMetrics();

            metrics.tid = Thread.currentThread().getName();
            metrics.elapsedTimeNanos = elapsedTimeNanos;
            metrics.sender = msg.getSender() == null ? new Address() : msg.getSender();
            metrics.startOfTreatment = start;
            metrics.type = msgType;
            metrics.reqID = msg.reqID;
            metrics.round = msg.round;
            metrics.vote = msg.v;

            relevantLock.lock();
            relevantCollection.add(metrics);
            relevantLock.unlock();
        }
    }

    public void registerMetric(ApproximationMessage msg,
                                Address otherProcessAddress,
                                LocalDateTime start,
                                Long elapsedTimeNanos,
                                MessageEvent type)
    {
        if(msg.reqID.internalID >= IGNORE)
        {
            Byte msgType = msg.getType();

            var relevantCollection = this.metricsPerEvent.get(type);
            var relevantLock = this.lockPerEvent.get(type);

            var metrics = new MessageTreatmentMetrics();

            metrics.tid = Thread.currentThread().getName();
            metrics.elapsedTimeNanos = elapsedTimeNanos;
            metrics.sender = otherProcessAddress;
            metrics.startOfTreatment = start;
            metrics.type = msgType;
            metrics.reqID = msg.reqID;
            metrics.round = msg.round;
            metrics.vote = msg.v;

            relevantLock.lock();
            relevantCollection.add(metrics);
            relevantLock.unlock();
        }
    }

    private void writeOneToFile(MessageEvent type, String header, String filename)
    {
        var relevantCollection = this.metricsPerEvent.get(type);
        var relevantLock = this.lockPerEvent.get(type);

        try
        {
            BufferedWriter fileWriter = new BufferedWriter(new FileWriter(filename));

            relevantLock.lock();

            fileWriter.write(header);
            fileWriter.write("\n");

            for (var metric : relevantCollection) fileWriter.write(metric.toCSVString());

            fileWriter.flush();
            fileWriter.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if(relevantLock.isLocked() && relevantLock.isHeldByCurrentThread())
                relevantLock.unlock();
        }

    }

    public void logMetricsCSV(String outputFile)
    {
        writeOneToFile(MessageEvent.SEND,    CSV_HEAD_SEND, outputFile + "_send.csv");
        writeOneToFile(MessageEvent.RECEIVE, CSV_HEAD_RECV, outputFile + "_recv.csv");
        writeOneToFile(MessageEvent.PROCESS, CSV_HEAD_PROC, outputFile + "_proc.csv");
        writeOneToFile(MessageEvent.ENQUEUE, CSV_HEAD_ENQ,  outputFile + "_enq.csv");
        writeOneToFile(MessageEvent.DEQUEUE, CSV_HEAD_DEQ,  outputFile + "_deq.csv");
    }


}
