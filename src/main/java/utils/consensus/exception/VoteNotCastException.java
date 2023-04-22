package utils.consensus.exception;

public class VoteNotCastException extends Exception
{
    public VoteNotCastException()
    {
        super();
    }

    public VoteNotCastException(String msg)
    {
        super(msg);
    }
}
