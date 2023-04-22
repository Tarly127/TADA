package utils.communication.serializer.utils;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/*
*
* Due to the way java generic types work, the class of T must still be passed to the Serializer constructor because
* of the way Java Generics work.
*
* IMPORTANT: TO BE USED ONLY FOR ADDRESS AND APPROXIMATIONMESSAGE SERIALIZATION
*
* */

public class ByteArraySerializer<T>
{
    private final Kryo kr;
    private final Class<T> serializableType;
    private final ByteArrayOutputStream bos;
    private final Output output;

    private final boolean variableLengthEncoding;

    public ByteArraySerializer(Class<T> serializableType)
    {
        this.kr = new Kryo();
        this.bos = new ByteArrayOutputStream();
        this.output = new Output(this.bos);
        this.serializableType = serializableType;

        this.variableLengthEncoding = true;

        this.kr.register(serializableType);

    }

    public byte[] encode(T object)
    {
        byte[] serializedPayload;

        synchronized (this.bos)
        {
            this.output.reset();
            this.kr.writeObject(this.output, object);
            this.output.flush(); // changed from close

            serializedPayload = this.bos.toByteArray();

            this.bos.reset();
        }

        return serializedPayload;
    }

    public T decode(byte[] object)
    {
        Input input = new Input(new ByteArrayInputStream(object));

        input.setVariableLengthEncoding(this.variableLengthEncoding);

        return kr.readObject(input, serializableType);
    }
}
