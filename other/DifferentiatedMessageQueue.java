package utils.communication.communicationHandler.MessageQueue;

import AtomicInterface.communication.groupConstitution.MessageQueue;
import AtomicInterface.communication.groupConstitution.Subscription;
import utils.communication.message.ExpectedMessageSize;
import utils.consensus.ids.InstanceID;
import org.javatuples.Triplet;
import utils.communication.address.Address;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class DifferentiatedMessageQueue implements MessageQueue
{
    public final static class WantedTypesSubscription implements Subscription
    {
        private final int myRegID;
        private final Collection<Byte> acceptableIDs;
        private final byte[] instanceID;

        public WantedTypesSubscription(int myRegID, final Collection<Byte> acceptableIDs)
        {
            this.myRegID       = myRegID;
            this.acceptableIDs = acceptableIDs;
            this.instanceID    = null;
        }

        public WantedTypesSubscription(int myRegID, Collection<Byte> acceptableIDs, byte[] instanceID)
        {
            this.myRegID       = myRegID;
            this.acceptableIDs = acceptableIDs;
            this.instanceID    = instanceID;
        }

        // TODO
        @Override
        public void waitForMessage()
        {

        }

        // TODO
        @Override
        public void signalNewMessage()
        {

        }

        // TODO
        @Override
        public int getRegID()
        {
            return this.myRegID;
        }

        public Collection<Byte> getAcceptableIDs()
        {
            return acceptableIDs;
        }

        @Override
        public byte[] getInstanceIDPayload()
        {
            return this.instanceID;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof WantedTypesSubscription that)) return false;
            return myRegID == that.myRegID
                    && Objects.equals(acceptableIDs, that.acceptableIDs)
                    && Arrays.equals(instanceID, that.instanceID);
        }

        @Override
        public int hashCode()
        {
            int result = Objects.hash(myRegID, acceptableIDs);
            result = 31 * result + Arrays.hashCode(instanceID);
            return result;
        }
    }

    private final ConcurrentMap<Subscription, BlockingQueue<MessagePacket>> specializedQueues;
    private final ReentrantLock                                   globalQueueLock;
    private final BlockingQueue<MessagePacket>                    undifferentiatedQueue;


    public DifferentiatedMessageQueue()
    {
        this.specializedQueues     = new ConcurrentHashMap<>();
        this.globalQueueLock       = new ReentrantLock(true);
        this.undifferentiatedQueue = new LinkedBlockingQueue<>();
    }

    private boolean noRepeatedTypesRegistered(Collection<Byte> acceptableTypes)
    {
        return this.specializedQueues
                .keySet()
                .stream()
                .map(Subscription::getAcceptableIDs)
                .noneMatch(types -> types.stream().anyMatch(acceptableTypes::contains));
    }

    private boolean noRepeatedInstanceID(InstanceID instanceID)
    {
        return this.specializedQueues
                .keySet()
                .stream()
                .noneMatch(reg -> Arrays.equals(instanceID.getPayload(), reg.getInstanceIDPayload()));
    }

    @Override
    public Subscription getRegistration(Collection<Byte> acceptableTypes, int regID)
    {
        // if no such types have yet been registered
        if(this.noRepeatedTypesRegistered(acceptableTypes))
        {
            // create new registration
            Subscription newSubscription = new WantedTypesSubscription(regID, acceptableTypes);

            this.globalQueueLock.lock();

            // generate the new queue for said registration
            this.specializedQueues.put(newSubscription, new LinkedBlockingQueue<>());

            // move all messages of this type from the undifferentiated queue to the specialied one
            for (var packet : this.undifferentiatedQueue)
            {
                // only remove the packet if it's of a wanted type and it doesn't have an instanceID
                if (newSubscription.getAcceptableIDs().contains(packet.getMsgType())
                        && packet.getInstanceID() == null)
                {
                    // remove the packet from the undif queue
                    if (this.undifferentiatedQueue.remove(packet))
                        // add it to the new queue
                        this.specializedQueues.get(newSubscription).add(packet);
                }
            }

            this.globalQueueLock.unlock();

            // return the new subscription
            return newSubscription;
        }
        return null;
    }

    @Override
    public Subscription getRegistration(Collection<Byte> acceptableTypes, InstanceID instanceID, int regID)
    {
        if(instanceID != null)
        {
            // check if there isn't another subscription with this instanceID (each instanceID should only have one
            // subscription)
            if(noRepeatedInstanceID(instanceID))
            {
                // generate new subscription
                Subscription newSubscription = new WantedTypesSubscription(regID, acceptableTypes, instanceID.getPayload());

                this.globalQueueLock.lock();

                // create the queue for this subscription
                this.specializedQueues.put(newSubscription, new LinkedBlockingQueue<>());

                // move all relevant messages from undifferentiated queue to the specialized queue
                for (var msgPacket : this.undifferentiatedQueue)
                {
                    // check if the packet is of one of the wanted types, the instance ID is not NULL and it is the
                    // wanted one
                    if (newSubscription.getAcceptableIDs().contains(msgPacket.getMsgType())
                            && msgPacket.getInstanceID() != null
                            && Arrays.equals(newSubscription.getInstanceIDPayload(), msgPacket.getInstanceID()))
                    {
                        // remove the packet from the undif queue
                        if (this.undifferentiatedQueue.remove(msgPacket))
                            // add it to the specialized queue
                            this.specializedQueues.get(newSubscription).add(msgPacket);
                    }
                }

                this.globalQueueLock.unlock();

                // return the new registration
                return newSubscription;
            }
        }
        return null;
    }

    @Override
    public void enqueue(Triplet<Address, byte[], Byte> packet)
    {
        // THIS IS NOT VERY GOOD AND IT BREAKS ENCAPSULATION
        InstanceID tmpID = packet.getValue1().length == ExpectedMessageSize.KRYO_SMALL_MESSAGE_SIZE_WITH_HEADER - 1 ?
                           new InstanceID(Arrays.copyOfRange(packet.getValue1(), 0,20)) :
                           null;
        // get the type
        Byte type        = packet.getValue2();

        BlockingQueue<MessagePacket> relevantQueue = null;

        // search for the relevant queue
        for(var subs : this.specializedQueues.keySet())
        {
            if(subs.getAcceptableIDs().contains(type) &&
                    (tmpID == null || Arrays.equals(tmpID.getPayload(), subs.getInstanceIDPayload())))
            {
                // get the relevant queue
                relevantQueue = this.specializedQueues.get(subs);
                break;
            }
        }

        try
        {
            // add to the relevant queue
            // if a queue of the relevant subscription doesn't exist, put the new packet in the undif queue
            Objects.requireNonNullElse(relevantQueue, this.undifferentiatedQueue).put(MessagePacket.fromTriplet(packet));
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    // Returns null if the subscription is invalid or the dequeue is interrupted
    @Override
    public Triplet<Address, byte[], Byte> dequeue(Subscription subscription)
    {
        try
        {
            return this.specializedQueues.get(subscription).take().toTriplet();
        }
        catch (InterruptedException e)
        {
            return null;
        }
    }
}
