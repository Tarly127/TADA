package utils.communication.groupConstitution;

import Interface.communication.address.AddressInterface;
import Interface.communication.groupConstitution.OtherNodeInterface;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

public class GroupConstitution extends HashMap<AddressInterface, OtherNodeInterface>
{
    private final ReentrantLock groupLock;

    public GroupConstitution(int initialCapacity, float loadFactor)
    {
        super(initialCapacity, loadFactor);
        this.groupLock = new ReentrantLock();
    }

    public GroupConstitution(int initialCapacity)
    {
        super(initialCapacity);
        this.groupLock = new ReentrantLock();
    }

    public GroupConstitution()
    {
        super();
        this.groupLock = new ReentrantLock(true);
    }

    public GroupConstitution(Map<AddressInterface, OtherNodeInterface> m)
    {
        super(m);
        this.groupLock = new ReentrantLock(true);
    }

    @Override
    public OtherNodeInterface put(AddressInterface key, OtherNodeInterface value)
    {
        this.groupLock.lock();
        var p = super.put(key, value);
        this.groupLock.unlock();
        return p;
    }

    @Override
    public OtherNodeInterface get(Object key)
    {
        this.groupLock.lock();
        var p = super.get(key);
        this.groupLock.unlock();
        return p;
    }

    public void lock()
    {
        this.groupLock.lock();
    }

    public void unlock()
    {
        this.groupLock.unlock();
    }

    @Override
    public void forEach(BiConsumer<? super AddressInterface, ? super OtherNodeInterface> action)
    {
        this.groupLock.lock();
        super.forEach(action);
        this.groupLock.unlock();
    }
}
