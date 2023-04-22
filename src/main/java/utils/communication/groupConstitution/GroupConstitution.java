package utils.communication.groupConstitution;

import AtomicInterface.communication.address.AddressInterface;
import AtomicInterface.communication.groupConstitution.ProcessInterface;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

public class GroupConstitution extends HashMap<AddressInterface, ProcessInterface>
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

    public GroupConstitution(Map<AddressInterface, ProcessInterface> m)
    {
        super(m);
        this.groupLock = new ReentrantLock(true);
    }

    @Override
    public ProcessInterface put(AddressInterface key, ProcessInterface value)
    {
        this.groupLock.lock();
        var p = super.put(key, value);
        this.groupLock.unlock();
        return p;
    }

    @Override
    public ProcessInterface get(Object key)
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
    public void forEach(BiConsumer<? super AddressInterface, ? super ProcessInterface> action)
    {
        this.groupLock.lock();
        super.forEach(action);
        this.groupLock.unlock();
    }
}
