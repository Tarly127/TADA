package utils.communication.communicationHandler.MessageQueue;

import Interface.communication.address.AddressInterface;
import Interface.communication.communicationHandler.MessageQueue;
import Interface.communication.communicationHandler.Subscription;
import utils.consensus.ids.InstanceID;
import org.javatuples.Triplet;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleMessageQueue implements MessageQueue
{
    public final class WantedTypeSubscription implements Subscription
    {
        private final int regID;
        private final Condition queueLockCondition;

        WantedTypeSubscription(int nextRegId)
        {
            this.queueLockCondition = generateNewWatcher();
            this.regID  = nextRegId;
        }

        @Override
        public void waitForMessage()
        {
            try
            {
                this.queueLockCondition.await();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }


        @Override
        public void signalNewMessage()
        {
            this.queueLockCondition.signalAll();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WantedTypeSubscription that = (WantedTypeSubscription) o;
            return this.regID == that.regID;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(regID);
        }

    }

    private final Deque<MessagePacket>                  msgQueue;
    private final ReentrantLock                         queueLock;
    private final Condition                             emptyCondition;
    private final Map<Subscription, Collection<Byte>>   registeredAcceptableTypes;
    private final Map<Subscription, byte[]>             registeredInstanceIDs;

    public SimpleMessageQueue()
    {
        this.msgQueue                  = new LinkedList<>();
        this.queueLock                 = new ReentrantLock(true);
        this.emptyCondition            = this.queueLock.newCondition();
        this.registeredAcceptableTypes = Collections.synchronizedMap(new HashMap<>());
        this.registeredInstanceIDs     = Collections.synchronizedMap(new HashMap<>());
    }

    public Subscription getRegistration(final Collection<Byte> acceptableTypes, final int regId)
    {
        // only register if none of the types have been registered before, else we are prone to deadlocks in the
        // supplyWithTypes/feed loop
        if(!acceptableTypes.isEmpty() && noRepeatedTypesRegistered(acceptableTypes))
        {
            Subscription subscription = new WantedTypeSubscription(regId);

            this.registeredAcceptableTypes.put(subscription, acceptableTypes);

            return subscription;
        }
        else
            return null;
    }

    public Subscription getRegistration(final Collection<Byte> acceptableTypes,
                                        final InstanceID instanceID,
                                        final int regId)
     {
        if(!acceptableTypes.isEmpty()
                && noRepeatedInstanceIDRegistered(instanceID))
        {
            Subscription subscription = new WantedTypeSubscription(regId);

            this.registeredAcceptableTypes.put(subscription, acceptableTypes);
            this.registeredInstanceIDs.put(subscription, instanceID.getPayload());

            return subscription;
        }
        else
            return null;
    }

    private boolean noRepeatedTypesRegistered(Collection<Byte> acceptableTypes)
    {
        return this.registeredAcceptableTypes
                .values()
                .stream()
                .noneMatch(types -> types.stream().anyMatch(acceptableTypes::contains));
    }

    private boolean noRepeatedInstanceIDRegistered(InstanceID instanceID)
    {
        return this.registeredInstanceIDs
                .values()
                .stream()
                .noneMatch(payload -> Arrays.equals(payload, instanceID.getPayload()));
    }

    public void enqueue(Triplet<AddressInterface, byte[], Byte> packet)
    {
        this.queueLock.lock();
        
        // store the new message packet
        this.msgQueue.push(MessagePacket.fromTriplet(packet));

        // signal all that the queue is no longer empty
        this.emptyCondition.signalAll();

        // signal all registrations that included this message's type
        this.registeredAcceptableTypes.forEach((registration, types)->
        {
            if(types.contains(packet.getValue2()))
            {
                registration.signalNewMessage();
            }
        });

        this.queueLock.unlock();
    }

    public Triplet<AddressInterface, byte[], Byte> dequeue()
    {
        try
        {
            this.queueLock.lock();

            while(this.msgQueue.isEmpty())
                this.emptyCondition.await();

            var packet = this.msgQueue.removeFirst().toTriplet();

            this.queueLock.unlock();

            return packet;
        }
        catch (InterruptedException e)
        {
            return null;
        }
        finally
        {
            if(this.queueLock.isLocked())
                this.queueLock.unlock();
        }
    }

    public Triplet<AddressInterface, byte[], Byte> dequeue(Subscription subscription)
    {
        Triplet<AddressInterface, byte[], Byte> target = null;

        // check if registration is valid
        if(this.registeredAcceptableTypes.containsKey(subscription))
        {
            // if it is, get the associated set
            Collection<Byte> acceptableTypes = this.registeredAcceptableTypes.get(subscription);
            InstanceID instanceID            = this.registeredInstanceIDs.get(subscription) != null ?
                                               new InstanceID(this.registeredInstanceIDs.get(subscription)) : null;

            // make sure it's not null
            if (acceptableTypes != null)
            {
                // lock the queue
                this.queueLock.lock();

                // if it is empty, wait for there to be a message in it
                while(this.msgQueue.isEmpty())
                    try
                    {
                        //System.out.println(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli() + " : awating (empty)");
                        this.emptyCondition.await();
                        //System.out.println(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli() + " : no longer awating (empty)");
                    }
                    catch (InterruptedException e)
                    {
                        this.queueLock.unlock();
                    }


                // if there are no relevant messages in the queue, wait until there is at least one
                while (this.msgQueue
                        .stream()
                        .noneMatch(messagePacket -> acceptableTypes.contains(messagePacket.getMsgType()))) {
                    //System.out.println(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli() + " : awating (no intersting message)");
                    subscription.waitForMessage();
                    //System.out.println(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli() + " : no longer awating (no interesting message)");
                }

                // get the earliest message with any of the wanted types from the queue
                for (var packet : msgQueue)
                {
                    if (acceptableTypes.contains(packet.getMsgType()))
                    {
                        if(instanceID == null
                            || Arrays.equals(instanceID.getPayload(), packet.getInstanceID()))
                        {
                            // remove the relevant packet
                            msgQueue.removeFirstOccurrence(packet);
                            // return a triplet containing the relevant information
                            target = packet.toTriplet();
                            // stop searching
                            break;
                        }
                    }
                }
                // unlock the queue
                this.queueLock.unlock();
            }
        }
        // return whatever we got
        return target;
    }

    public Triplet<AddressInterface, byte[], Byte> dequeue(Byte acceptableType)
    {
        Triplet<AddressInterface, byte[], Byte> target = null;

        try
        {
            this.queueLock.lock();

            while(this.msgQueue.isEmpty())
                this.emptyCondition.await();

            for(var packet : msgQueue)
            {
                if (acceptableType == packet.getMsgType())
                {
                    // remove the relevant packet
                    msgQueue.removeFirstOccurrence(packet);
                    // return a triplet containing the relevant information
                    target = packet.toTriplet();
                    // stop searching
                    break;
                }
            }

            this.queueLock.unlock();

            return target;

        }
        catch (InterruptedException e)
        {
            return null;
        }
        finally
        {
            if(this.queueLock.isLocked())
                this.queueLock.unlock();
        }
    }

    public Condition generateNewWatcher()
    {
        return this.queueLock.newCondition();
    }

    public int size()
    {
        return this.msgQueue.size();
    }
}
