package utils.communication.message;

import Interface.communication.Message;
import Interface.communication.address.AddressInterface;
import utils.consensus.ids.RequestID;

import java.io.*;
import java.util.Objects;

public class ApproximationMessage implements Serializable, Message
{
    public Double           v;
    public Integer          round;
    public Byte             type;
    public RequestID        reqID;
    public AddressInterface sender;

    public ApproximationMessage()
    {
        this.v      = - 1.0;
        this.round  = - 1;
        this.type   = MessageType.UNDEFINED;
        this.reqID  = null;
        this.sender = null;
    }

    public ApproximationMessage(byte type, AddressInterface sender)
    {
        this.v      = - 1.0;
        this.round  = - 1;
        this.type   = type;
        this.reqID  = null;
        this.sender = sender;
    }

    public ApproximationMessage(Double v, Integer round, Byte type)
    {
        this.v      = v;
        this.round  = round;
        this.type   = type;
        this.reqID  = null;
        this.sender = null;
    }

    public ApproximationMessage(Double v, Integer round, Byte type, RequestID reqID)
    {
        this.v      = v;
        this.round  = round;
        this.type   = type;
        this.reqID  = reqID;
        this.sender = null;
    }

    public ApproximationMessage(Double v, Integer round, Byte type, RequestID reqID, AddressInterface sender)
    {
        this.v      = v;
        this.round  = round;
        this.type   = type;
        this.reqID  = reqID;
        this.sender = sender;
    }

    public Byte getType()
    {
        return this.type;
    }

    @Override
    public AddressInterface getSender()
    {
        return this.sender;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof ApproximationMessage that)) return false;
        return Double.compare(that.v, v) == 0 &&
                Objects.equals(round, that.round) &&
                Objects.equals(type, that.type) && (
                this.sender == null ||
                that.sender == null ||
                this.sender.equals(that.sender));
    }

    public String toString()
    {

        String sb = "Value: " + v + ";\n" +
                "Round: " + round + ";\n" +
                "Type: " +
                MessageType.typeString(this.type) + ";\n";
        return sb;
    }




}
