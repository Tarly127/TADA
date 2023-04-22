package utils.consensus.ids;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class InstanceID
{
    private final byte[] instancePayload;

    public InstanceID(String type)
    {
        MessageDigest md;

        try
        {
            md = MessageDigest.getInstance("SHA-1");
        }
        catch (Throwable e)
        {
            md = null;
        }
        if(md != null)
            this.instancePayload = md.digest(type.getBytes(StandardCharsets.UTF_8));
        else
            this.instancePayload = new byte[1];
    }

    public InstanceID(byte[] payload)
    {
        this.instancePayload = payload;
    }

    public byte[] getPayload()
    {
        return instancePayload;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof InstanceID that)) return false;
        return Arrays.equals(instancePayload, that.instancePayload);
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(instancePayload);
    }

    public static boolean equal(byte[] arr1, byte[] arr2)
    {
        return Arrays.equals(arr1, arr2);
    }
}
