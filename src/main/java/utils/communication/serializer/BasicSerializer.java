package utils.communication.serializer;

import Interface.communication.address.AddressInterface;
import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import utils.communication.address.Address;
import utils.communication.message.ExpectedMessageSize;
import utils.consensus.ids.RequestID;

import java.util.Arrays;

/*
*
* Due to the way java generic types work, the class of T must still be passed to the Serializer constructor because
* Java Generics just work like that.
*
* IMPORTANT: TO BE USED ONLY FOR ADDRESS AND APPROXIMATIONMESSAGE SERIALIZATION
*
*
* */

public class BasicSerializer<T>
{
    private static final int BUFFER_SIZE = ExpectedMessageSize.KRYO_SMALL_MESSAGE_SIZE;
    private static final int HEADER_SIZE = ExpectedMessageSize.INSTANCE_ID_HEADER_SIZE;

    private final Kryo kr;
    private final Class<T> serializableType;
    private final Output output;
    private final Input input;
    //private final Set<Class<?>> registeredClasses;

    public BasicSerializer(Class<T> serializableType)
    {
        this.kr     = new Kryo();
        this.output = new Output(BUFFER_SIZE);
        this.input  = new Input (BUFFER_SIZE);

        this.serializableType = serializableType;

        this.kr.register(this.serializableType);

        //this.registeredClasses = null;
        //(this.registeredClasses = FieldRegister.visitFields(this.serializableType)).forEach(kr::register);

        // Work in progress: had some trouble on serialization that was solved by hard coding this. Can and should be
        // made generic with the two lines commented out in the future.
        this.kr.register(RequestID.class);
        this.kr.register(Address.class);
        this.kr.register(AddressInterface.class);
    }



    public synchronized byte[] encode(T object)
    {
        try
        {
            this.output.reset();

            this.kr.writeObjectOrNull(this.output, object, this.serializableType);

            this.output.flush(); // changed from close

            return this.output.getBuffer();
        }
        catch (Throwable e)
        {
            e.printStackTrace();

            return null;
        }
    }

    public synchronized T decode(byte[] object)
    {
        try
        {
            if (object.length == BUFFER_SIZE + HEADER_SIZE)
                this.input.setBuffer(Arrays.copyOfRange(object, HEADER_SIZE, object.length));
            else
                this.input.setBuffer(object);

            return this.kr.readObjectOrNull(this.input, this.serializableType);
        }
        catch (Throwable e)
        {
            e.printStackTrace();

            return null;
        }
    }
}
