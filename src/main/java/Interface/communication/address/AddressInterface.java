package Interface.communication.address;

/**
 * Simple wrapper for an IPv4 Address
 */
public interface AddressInterface
{
    int    getPort();
    String getHost();
    void setHost(String host);
    void setPort(int port);
}
