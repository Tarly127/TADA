package utils.communication.address;

import Interface.communication.address.AddressInterface;

import java.io.*;
import java.util.Objects;

public class Address implements Serializable, AddressInterface
{
    private int    port;
    private String host;

    public Address()
    {
        this.port = -1;
        this.host = "127.0.0.1";
    }

    public Address(int port)
    {
        this.port = port;
        this.host = "127.0.0.1";
    }

    public Address(String host, int port)
    {
        this.port = port;
        this.host = host;
    }



    public Address(Address other)
    {
        this.port = other.port;
        this.host = other.host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost()
    {
        return this.host;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return port == address.port && Objects.equals(host, address.host);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(port, host);
    }

    @Override
    public String toString()
    {
        return "Address{" +
                "host=" + host +
                "port=" + port +
                '}';
    }
}
