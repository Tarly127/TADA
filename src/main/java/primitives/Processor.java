package primitives;

import AtomicInterface.communication.address.AddressInterface;
import AtomicInterface.communication.communicationHandler.CommunicationManager;
import AtomicInterface.communication.groupConstitution.ProcessInterface;
import AtomicInterface.communication.groupConstitution.Subscription;
import AtomicInterface.consensus.ApproximateConsensusHandler;
import org.javatuples.Triplet;
import utils.communication.communicationHandler.AsynchMessageManager;
import utils.communication.address.Address;
import utils.communication.communicationHandler.Broadcast.AsynchBroadcast;
import utils.communication.message.ExpectedMessageSize;
import utils.communication.message.IntegrationMessage;
import utils.communication.message.MessageType;
import utils.communication.serializer.MessageSerializer;
import utils.communication.groupConstitution.GroupConstitution;
import utils.communication.groupConstitution.Process;
import utils.consensus.types.faultDescriptors.FaultClass;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class Processor
{
    private static final int STARTING_NO_THREADS      = 32; //Runtime.getRuntime().availableProcessors();
    private static final int SYNCH_MIN_PROCS          = 4;
    private static final int ASYNC_MIN_PROCS          = 6;
    private static final Set<Byte> wantedMessageTypes = new TreeSet<>(Arrays.asList(
            MessageType.JOIN,
            MessageType.INTEGRATION,
            MessageType.READY,
            MessageType.TERMINATE
    ));


    // Addresses
    private final AddressInterface brokerAddress;
    private final AddressInterface myAddress;
    // Communication
    private ProcessInterface broker;
    private final AsynchronousServerSocketChannel serverChannel;
    private final CommunicationManager msgManager;
    private final ExecutorService threadPool;
    private final Subscription wantedTypesSubscription;
    private final GroupConstitution groupConstitution;
    private final AtomicInteger totalProcesses;
    private final AtomicInteger connectedProcesses;
    private final AtomicInteger memberAddressesReceived;
    private final ReentrantLock joinReadyLock;
    private final MessageSerializer<IntegrationMessage> serializer;
    private final Semaphore groupIntegrationSem;
    // Consensus
    private      boolean synchConsensus;
    private      boolean asyncConsensus;
    private final ReentrantLock consensusLock;
    private final Condition synchReady;
    private final Condition asyncReady;
    // Other
    private final boolean               isBroker;
    private final AtomicBoolean         isReady;
    private final Map<AddressInterface, Boolean> states; // should be a synchronized map
    private final Set<AddressInterface>          wantTermination;

    private final int minimumProcesses;

    private static class Attachment
    {
        private final int BUFFER_CAPACITY = ExpectedMessageSize.KRYO_SMALL_MESSAGE_SIZE_WITH_HEADER; // to account for the
        // message type

        AddressInterface otherProcessAddress;
        ProcessInterface otherProcess;
        ByteBuffer       buffer;

        Attachment()
        {
            this.buffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
        }

        public Attachment(AddressInterface otherProcessAddress, ProcessInterface otherProcess)
        {
            this.otherProcessAddress = otherProcessAddress;
            this.otherProcess        = otherProcess;
            this.buffer              = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
        }
    }

    private final CompletionHandler<Integer, Attachment> SendReadyAfterIntegrationHandler
            = new CompletionHandler<>() {
                @Override
                public void completed(Integer result, Attachment attachment)
                {
                    // increment connected processes
                    connectedProcesses.incrementAndGet();
                    // if received members == group size
                    if (memberAddressesReceived.incrementAndGet() == totalProcesses.get())
                    {
                        byte[] payload = serializer.encodeWithHeader(new IntegrationMessage(MessageType.READY),
                                MessageType.READY);
                        // broadcast READY
                        (new AsynchBroadcast()).broadcast(payload, groupConstitution)
                                .thenRun(Processor.this::openState);
                    }
                }

                @Override
                public void failed(Throwable exc, Attachment attachment)
                {
                    exc.printStackTrace();
                }
            };

    private final CompletionHandler<Integer, Attachment> WriteGroupConstitutionHandler
            = new CompletionHandler<>() {
        @Override
        public void completed(Integer result, Attachment attachment)
        {
            groupConstitution.forEach((address, process)->
            {
                // check that the address doesn't correspond to my own (as the broker) or his own
                if(!address.equals(brokerAddress) && !address.equals(attachment.otherProcessAddress))
                {
                    Address simplifiedAddress = new Address(address.getPort());

                    byte[] payload = serializer.encodeWithHeader(
                            new IntegrationMessage(simplifiedAddress, MessageType.INTEGRATION),
                            MessageType.INTEGRATION
                    );

                    attachment.buffer.clear();
                    attachment.buffer.put(payload);
                    attachment.buffer.flip();

                    // send the address
                    attachment.otherProcess.safeWrite(attachment.buffer);
                }
            });

            groupIntegrationSem.release();
        }

        @Override
        public void failed(Throwable exc, Attachment attachment)
        {
            exc.printStackTrace();
        }
    };

    private final CompletionHandler<Integer, Attachment> ReadAddressHandlerBroker
            = new CompletionHandler<>() {
        @Override
        public void completed(Integer result, Attachment attachment)
        {
            if (result > 0)
            {
                byte[] msgPayload = new byte[result];

                attachment.buffer.flip();
                attachment.buffer.get(msgPayload);

                // decode the message
                IntegrationMessage msg = serializer.decodeWithHeader(msgPayload);

                if (msg.senderAddress != null)
                {
                    try
                    {
                        groupIntegrationSem.acquire();
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }

                    joinReadyLock.lock();

                    // register this process's address
                    attachment.otherProcessAddress = msg.senderAddress;
                    attachment.otherProcessAddress.setHost(
                            Objects.requireNonNullElse(
                                    attachment.otherProcess.getRemoteAddress(),
                                    new InetSocketAddress("localhost", 1))
                                    .getHostName());
                    // set up listener for the process in the message manager (also adds to GroupConstitution)
                    msgManager.addToGroup(attachment.otherProcessAddress, attachment.otherProcess);
                    // set this process's state as true
                    states.put(attachment.otherProcessAddress, true);
                    // Increment total processes and member addresses received
                    totalProcesses         .incrementAndGet();
                    memberAddressesReceived.incrementAndGet();

                    attachment.buffer.clear();
                    attachment.buffer.put(serializer.encodeWithHeader(
                            new IntegrationMessage(groupConstitution.size() - 2, MessageType.INTEGRATION),
                            MessageType.INTEGRATION));
                    attachment.buffer.flip();

                    joinReadyLock.unlock();

                    attachment.otherProcess.safeWrite(attachment.buffer, attachment, WriteGroupConstitutionHandler);
                }
            }
        }

        @Override
        public void failed(Throwable exc, Attachment attachment)
        {
            exc.printStackTrace();
        }
    };

    private final CompletionHandler<Integer, Attachment> ReadAddressHandler
            = new CompletionHandler<>() {

        @Override
        public void completed(Integer result, Attachment attachment)
        {
            if(result > 0)
            {
                byte[] msgPayload = new byte[result];

                attachment.buffer.flip();
                attachment.buffer.get(msgPayload);

                // decode the message
                IntegrationMessage msg = serializer.decodeWithHeader(msgPayload);

                attachment.otherProcessAddress = msg.senderAddress;
                if(attachment.otherProcessAddress != null)
                    attachment.otherProcessAddress.setHost(
                            Objects.requireNonNullElse(
                                    attachment.otherProcess.getRemoteAddress(),
                                    new InetSocketAddress("localhost", 1))
                                    .getHostName());

                switch (msg.type)
                {
                    case MessageType.JOIN  -> handleJoinMsg(attachment.otherProcessAddress, attachment.otherProcess);
                    case MessageType.READY -> handleReadyMsg(attachment.otherProcessAddress, attachment.otherProcess);
                    default -> System.out.println("Message Sequence Error!");
                }
            }
        }

        @Override
        public void failed(Throwable exc, Attachment attachment)
        {
            exc.printStackTrace();
        }
    };

    private final CompletionHandler<Integer, Attachment> WriteTerminationHandler
            = new CompletionHandler<>() {
        @Override
        public void completed(Integer result, Attachment attachment)
        {}

        @Override
        public void failed(Throwable exc, Attachment attachment)
        { exc.printStackTrace(); }
    };

    // constructor
    public Processor(String port, String brokerPort) throws IOException
    {
        this.myAddress = new Address(Integer.parseInt(port));
        this.brokerAddress = new Address(Integer.parseInt(brokerPort));
        this.isBroker = this.brokerAddress.getPort() == this.myAddress.getPort();
        this.groupConstitution = new GroupConstitution();
        this.isReady = new AtomicBoolean(false);
        this.serializer = new MessageSerializer<>(IntegrationMessage.class);
        this.totalProcesses = new AtomicInteger(isBroker ? 1 : 2);
        this.connectedProcesses = new AtomicInteger(1);
        this.memberAddressesReceived = new AtomicInteger(isBroker ? 1 : 2);
        this.msgManager = new AsynchMessageManager(this.groupConstitution);
        this.threadPool = Executors.newFixedThreadPool(STARTING_NO_THREADS, Executors.defaultThreadFactory());
        this.states = Collections.synchronizedMap(new HashMap<>());
        this.wantTermination = Collections.synchronizedSet(new HashSet<>());
        this.wantedTypesSubscription = this.msgManager.getRegistration(wantedMessageTypes);
        this.groupIntegrationSem = new Semaphore(1, true);
        this.joinReadyLock = new ReentrantLock(true);
        this.consensusLock = new ReentrantLock(true);
        this.synchConsensus = false;
        this.asyncConsensus = false;
        this.asyncReady = this.consensusLock.newCondition();
        this.synchReady = this.consensusLock.newCondition();
        this.minimumProcesses = Math.min(ASYNC_MIN_PROCS, SYNCH_MIN_PROCS);

        // setup Server Socket Channel
        AsynchronousChannelGroup channelGroup = AsynchronousChannelGroup.withThreadPool(this.threadPool);
        this.serverChannel = AsynchronousServerSocketChannel.open(channelGroup);
        this.serverChannel.bind(new InetSocketAddress("127.0.0.1", this.myAddress.getPort()));

        System.out.println("Server listening at " + this.serverChannel.getLocalAddress());

        if (this.isBroker)
        {
            // Do things as broker
            handleBrokerJoin();
            handleNewConnectionAsBroker();
        }
        else
        {
            // Do things as non broker
            handleGroupJoin();
            handleNewConnection();
        }

        this.threadPool.submit(this::supplyNextMessage);
    }

    public Processor(String port, String brokerPort, int minimumProcesses) throws IOException
    {
        this.myAddress = new Address(Integer.parseInt(port));
        this.brokerAddress = new Address(Integer.parseInt(brokerPort));
        this.isBroker = this.brokerAddress.getPort() == this.myAddress.getPort();
        this.groupConstitution = new GroupConstitution();
        this.isReady = new AtomicBoolean(false);
        this.serializer = new MessageSerializer<>(IntegrationMessage.class);
        this.totalProcesses = new AtomicInteger(isBroker ? 1 : 2);
        this.connectedProcesses = new AtomicInteger(1);
        this.memberAddressesReceived = new AtomicInteger(isBroker ? 1 : 2);
        this.msgManager = new AsynchMessageManager(this.groupConstitution);
        this.threadPool = Executors.newFixedThreadPool(STARTING_NO_THREADS, Executors.defaultThreadFactory());
        this.states = Collections.synchronizedMap(new HashMap<>());
        this.wantTermination = Collections.synchronizedSet(new HashSet<>());
        this.wantedTypesSubscription = this.msgManager.getRegistration(wantedMessageTypes);
        this.groupIntegrationSem = new Semaphore(1, true);
        this.joinReadyLock = new ReentrantLock(true);
        this.consensusLock = new ReentrantLock(true);
        this.synchConsensus = false;
        this.asyncConsensus = false;
        this.asyncReady = this.consensusLock.newCondition();
        this.synchReady = this.consensusLock.newCondition();
        this.minimumProcesses = minimumProcesses;

        // setup Server Socket Channel
        AsynchronousChannelGroup channelGroup = AsynchronousChannelGroup.withThreadPool(this.threadPool);
        this.serverChannel = AsynchronousServerSocketChannel.open(channelGroup);
        this.serverChannel.bind(new InetSocketAddress("127.0.0.1", this.myAddress.getPort()));

        System.out.println("Server listening at " + this.serverChannel.getLocalAddress());

        if (this.isBroker)
        {
            // Do things as broker
            handleBrokerJoin();
            handleNewConnectionAsBroker();
        }
        else
        {
            // Do things as non broker
            handleGroupJoin();
            handleNewConnection();
        }

        this.threadPool.submit(this::supplyNextMessage);
    }

    // constructor
    public Processor(String port, String host, String brokerPort, String brokerHost) throws IOException
    {
        this.myAddress = new Address(host, Integer.parseInt(port));
        this.brokerAddress = new Address(brokerHost, Integer.parseInt(brokerPort));
        this.isBroker = this.brokerAddress.equals(this.myAddress);
        this.groupConstitution = new GroupConstitution();
        this.isReady = new AtomicBoolean(false);
        this.serializer = new MessageSerializer<>(IntegrationMessage.class);
        this.totalProcesses = new AtomicInteger(isBroker ? 1 : 2);
        this.connectedProcesses = new AtomicInteger(1);
        this.memberAddressesReceived = new AtomicInteger(isBroker ? 1 : 2);
        this.msgManager = new AsynchMessageManager(this.groupConstitution);
        this.threadPool = Executors.newFixedThreadPool(STARTING_NO_THREADS, Executors.defaultThreadFactory());
        this.states = Collections.synchronizedMap(new HashMap<>());
        this.wantTermination = Collections.synchronizedSet(new HashSet<>());
        this.wantedTypesSubscription = this.msgManager.getRegistration(wantedMessageTypes);
        this.groupIntegrationSem = new Semaphore(1, true);
        this.joinReadyLock = new ReentrantLock(true);
        this.consensusLock = new ReentrantLock(true);
        this.synchConsensus = false;
        this.asyncConsensus = false;
        this.asyncReady = this.consensusLock.newCondition();
        this.synchReady = this.consensusLock.newCondition();
        this.minimumProcesses = Math.min(ASYNC_MIN_PROCS, SYNCH_MIN_PROCS);

        // setup Server Socket Channel
        AsynchronousChannelGroup channelGroup = AsynchronousChannelGroup.withThreadPool(this.threadPool);
        this.serverChannel = AsynchronousServerSocketChannel.open(channelGroup);
        this.serverChannel.bind(new InetSocketAddress(this.myAddress.getHost(), this.myAddress.getPort()));

        System.out.println("Server listening at " + this.serverChannel.getLocalAddress());

        if (this.isBroker)
        {
            // Do things as broker
            handleBrokerJoin();
            handleNewConnectionAsBroker();
        }
        else
        {
            // Do things as non broker
            handleGroupJoin();
            handleNewConnection();
        }

        this.threadPool.submit(this::supplyNextMessage);
    }





    // handle group join as broker
    private void handleBrokerJoin()
    {
        // register broker connection as NULL
        this.broker = new Process();
        // add broker to communication group
        this.msgManager.addToGroup(this.brokerAddress, this.broker);
        this.states    .put(this.myAddress, true);
        // broker is ready (until someone else connects)
        this.isReady.set(true);
    }

    // handle new connection as broker
    private void handleNewConnectionAsBroker()
    {
        // accept new connection
        this.serverChannel.accept(new Attachment(), new CompletionHandler<>() {
            @Override
            public void completed(AsynchronousSocketChannel newConnection, Attachment attachment)
            {
                attachment.otherProcess = new Process(newConnection);

                try
                {
                    newConnection.setOption(StandardSocketOptions.TCP_NODELAY, true);
                }
                catch(IOException e)
                {
                    e.printStackTrace();
                }

                // read his address
                attachment.otherProcess.safeRead(attachment.buffer, attachment, ReadAddressHandlerBroker);

                // accept more connections
                serverChannel.accept(new Attachment(), this);
            }

            @Override
            public void failed(Throwable exc, Attachment attachment)
            {
                exc.printStackTrace();
            }
        });
    }

    // handle connection to broker as non broker
    private void handleGroupJoin()
    {
        // add myself to group
        this.msgManager.addToGroup(myAddress, new Process());

        try
        {
            // Open socket for broker connection
            this.broker =
                    new Process(AsynchronousSocketChannel.open(AsynchronousChannelGroup.withThreadPool(this.threadPool)));

            // Connect to Broker
            this.broker
                    .connect(new InetSocketAddress("127.0.0.1", this.brokerAddress.getPort()),
                            new Attachment(this.brokerAddress, this.broker),
                            new CompletionHandler<>() {
                @Override
                public void completed(Void result, Attachment attachment)
                {
                    // add broker to group
                    msgManager.addToGroup(brokerAddress, broker);

                    Address simpleAddress = new Address(myAddress.getPort());

                    // prepare to send own address to broker
                    byte[] addressMessage = serializer.encodeWithHeader(
                            new IntegrationMessage(simpleAddress, MessageType.JOIN),
                            MessageType.JOIN);

                    attachment.buffer.put(addressMessage);
                    attachment.buffer.flip();

                    // send it
                    attachment.otherProcess.safeWrite(attachment.buffer, attachment, new CompletionHandler<>()
                    {
                        @Override
                        public void completed(Integer result, Attachment attachment)
                        {
                            // increment number of connected processes
                            connectedProcesses.incrementAndGet();
                        }

                        @Override
                        public void failed(Throwable exc, Attachment attachment)
                        {
                            exc.printStackTrace();
                        }
                    });
                }
                @Override
                public void failed(Throwable exc, Attachment attachment)
                {
                    System.out.println("Unable to connect to broker, exiting...");
                    end();
                }
            });
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    // handle new connection as non broker after integration
    private void handleNewConnection()
    {
        this.serverChannel.accept(new Attachment(), new CompletionHandler<>() {
            @Override
            public void completed(AsynchronousSocketChannel newConnection, Attachment attachment)
            {
                attachment.otherProcess = new Process(newConnection);
                try
                {
                    newConnection.setOption(StandardSocketOptions.TCP_NODELAY, true);
                }
                catch(IOException e)
                {
                    e.printStackTrace();
                }

                // await new address
                attachment.otherProcess.safeRead(attachment.buffer, attachment, ReadAddressHandler);

                // accept new connections
                serverChannel.accept(new Attachment(), this);
            }

            @Override
            public void failed(Throwable exc, Attachment attachment)
            {
                exc.printStackTrace();
            }
        });
    }


    // get and handle the next message in queue (called recursively)
    private void supplyNextMessage()
    {
        // get the next queued message with any of the wanted types
        Triplet<AddressInterface, byte[], Byte> msgPacket = this.msgManager.dequeue(this.wantedTypesSubscription);
        // handle it
        handleMessage(msgPacket);
        // get next
        this.threadPool.submit(this::supplyNextMessage);
    }

    // supplied with a message packet, handle the message based on its type
    private void handleMessage(Triplet<AddressInterface, byte[], Byte> msgPacket)
    {
        // triplet[2] -> msgType
        switch (msgPacket.getValue2())
        {
            case MessageType.JOIN        -> handleJoinMsg           (msgPacket.getValue0());
            case MessageType.READY       -> handleReadyMsg          (msgPacket.getValue0());
            case MessageType.INTEGRATION -> handleIntegrationMessage(msgPacket.getValue0(), msgPacket.getValue1());
            case MessageType.TERMINATE   -> handleTermintate        (msgPacket.getValue0());
        }
    }

    // handle messages with type JOIN
    private void handleJoinMsg(AddressInterface address)
    {
        this.joinReadyLock.lock();

        // if already received READY
        if(this.states.containsKey(address))
        {
            // add to group
            this.msgManager.addToGroup(address, this.groupConstitution.get(address));
            this.states    .replace(address, true);
            // increment connected
            this.connectedProcesses     .incrementAndGet();
            this.memberAddressesReceived.incrementAndGet();

            this.joinReadyLock.unlock();

            // goto OPEN STATE
            openState();
        }
        else
        {
            // add to group
            this.msgManager.addToGroup(address, this.groupConstitution.get(address));
            // mark as received JOIN
            this.states.put(address, false);
            // as no other message from this process was received, count him toward group size
            this.totalProcesses         .incrementAndGet();
            this.memberAddressesReceived.incrementAndGet();

            this.joinReadyLock.unlock();
        }
    }
    private void handleJoinMsg(AddressInterface address, ProcessInterface process)
    {
        this.joinReadyLock.lock();

        // if already received READY
        if(this.states.containsKey(address))
        {
            // add to group
            this.msgManager.addToGroup(address, process);
            this.states    .replace(address, true);
            // increment connected
            this.connectedProcesses.incrementAndGet();
            this.memberAddressesReceived.incrementAndGet();

            this.joinReadyLock.unlock();

            // goto OPEN STATE
            openState();
        }
        else
        {
            // add to group
            this.msgManager.addToGroup(address, process);
            // mark as received JOIN
            this.states.put(address, false);
            // as no other message from this process was received, count him toward group size
            this.totalProcesses         .incrementAndGet();
            this.memberAddressesReceived.incrementAndGet();

            this.joinReadyLock.unlock();
        }
    }

    // handle messages with type READY
    private void handleReadyMsg(AddressInterface address)
    {
        this.joinReadyLock.lock();

        // if already received JOIN
        if(this.states.containsKey(address))
        {
            // add to group
            this.msgManager.addToGroup(address, this.groupConstitution.get(address));
            this.states    .replace(address, true);
            // increment connected
            this.connectedProcesses.incrementAndGet();

            this.joinReadyLock.unlock();

            // goto OPEN STATE
            openState();
        }
        else
        {
            // mark as received READY
            this.states.put(address, false);
            // as no other message from this process was received, count him toward group size
            this.totalProcesses.incrementAndGet();

            this.joinReadyLock.unlock();
        }
    }
    private void handleReadyMsg(AddressInterface address, ProcessInterface process)
    {
        this.joinReadyLock.lock();

        // if already received JOIN
        if(this.states.containsKey(address))
        {
            // add to group
            this.msgManager.addToGroup(address, process);
            this.states    .replace(address, true);
            // increment connected
            this.connectedProcesses.incrementAndGet();

            this.joinReadyLock.unlock();

            // goto OPEN STATE
            openState();
        }
        else
        {
            // mark as received READY
            this.states.put(address, false);
            // as no other message from this process was received, count him toward group size
            this.totalProcesses.incrementAndGet();

            this.joinReadyLock.unlock();
        }
    }

    // handle messages with type INTEGRATION
    private void handleIntegrationMessage(AddressInterface address, byte[] payload)
    {
        IntegrationMessage msg = serializer.decodeWithHeader(payload);

        if(msg.senderAddress == null) // receiving number of processes in group
        {
            // set group size
            this.totalProcesses.addAndGet(msg.groupSize);
            // if receivedMembers == groupSize
            if(this.memberAddressesReceived.get() == this.totalProcesses.get())
                (new AsynchBroadcast()).broadcast(this.serializer.encodeWithHeader(
                                new IntegrationMessage(MessageType.READY),
                                MessageType.READY),
                        this.groupConstitution)
                        .thenRun(this::openState);
        }
        else // receiving the address of one of the processes in the group
        {
            try
            {
                Attachment attachment = new Attachment(msg.senderAddress,
                        new Process(AsynchronousSocketChannel.open(AsynchronousChannelGroup.withThreadPool(this.threadPool))));

                attachment.otherProcess.connect(
                        new InetSocketAddress("127.0.0.1", msg.senderAddress.getPort()),
                        attachment,
                        new CompletionHandler<>() {
                            @Override
                            public void completed(Void result, Attachment attachment)
                            {
                                // add to group
                                msgManager.addToGroup(msg.senderAddress, attachment.otherProcess);

                                // send JOIN message
                                attachment.buffer.put(serializer.encodeWithHeader(new IntegrationMessage(myAddress,
                                        MessageType.JOIN), MessageType.JOIN));
                                attachment.buffer.flip();

                                attachment.otherProcess.safeWrite(attachment.buffer, attachment, SendReadyAfterIntegrationHandler);
                            }

                            @Override
                            public void failed(Throwable exc, Attachment attachment)
                            {
                                exc.printStackTrace();
                            }
                        });

            }
            catch (IOException e)
            {
                System.out.println("Unable to connect to 127.0.0.1:" + address.getPort());
            }
        }
    }

    // handle message of type TERMINATE
    private void handleTermintate(AddressInterface address)
    {
        // only treat this kind of messages if we're the broker
        if (this.isBroker)
        {
            this.wantTermination.add(address);
            // if everyone agrees it's time to terminate, kill everyone
            if(this.wantTermination.size() == this.n())
                this.killAll();
        }
    }

    private void openState()
    {
        System.out.println(this.totalProcesses.get() + "/" + this.minimumProcesses);

        if (this.totalProcesses.get() >= this.minimumProcesses
                    && this.memberAddressesReceived.get() >= this.minimumProcesses
                    && this.connectedProcesses.get() >= this.minimumProcesses)
        {
            if (this.totalProcesses.get() >= SYNCH_MIN_PROCS
                    && this.memberAddressesReceived.get() >= SYNCH_MIN_PROCS
                    && this.connectedProcesses.get() >= SYNCH_MIN_PROCS)
            {
                this.consensusLock.lock();

                this.synchConsensus = true;
                this.synchReady.signalAll();

                if (this.totalProcesses.get() >= ASYNC_MIN_PROCS
                        && this.memberAddressesReceived.get() >= ASYNC_MIN_PROCS
                        && this.connectedProcesses.get() >= ASYNC_MIN_PROCS)
                {
                    this.asyncConsensus = true;
                    this.asyncReady.signalAll();
                }
                this.consensusLock.unlock();
            }
        }
    }



    // cease operations
    private void end()
    {
        try
        {
            this.serverChannel.close();
            this.threadPool.shutdownNow();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void killAll()
    {
        try
        {
            (new ProcessBuilder("/usr/bin/pkill", "-9","-f","java"))
                    .start()
                    .waitFor();
            System.exit(0);
        }
        catch (IOException | InterruptedException  e)
        {
            e.printStackTrace();
        }
    }

    public AtomicApproximateDouble newAtomicApproximateDouble(String name, double epsilon, double v, long timeout,
                                                                    TimeUnit unit)
    {
        this.consensusLock.lock();

        try
        {
            if (!synchConsensus)
                this.synchReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.consensusLock.unlock();
        }

        return new AtomicApproximateDouble(this.msgManager, name, epsilon, v, timeout, unit);
    }

    public <T> AtomicApproximateDoubleTemplate<T> newAtomicApproximateDouble(String name, double epsilon, double v,
                                                                             long timeout,
                                                                             TimeUnit unit,
                                                                             ApproximateConsensusHandler<T> handler)
    {
        this.consensusLock.lock();

        try
        {
            if (!synchConsensus)
                this.synchReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.consensusLock.unlock();
        }

        return new AtomicApproximateDoubleTemplate<>(this.msgManager, name, epsilon, v, timeout, unit, handler);
    }

    public <T> AtomicApproximateDoubleTemplate<T> newAtomicApproximateDouble(String name, double epsilon,
                                                                             long timeout,
                                                                             TimeUnit unit,
                                                                             ApproximateConsensusHandler<T> handler)
    {
        this.consensusLock.lock();

        try
        {
            if (!synchConsensus)
                this.synchReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.consensusLock.unlock();
        }

        return new AtomicApproximateDoubleTemplate<>(this.msgManager, name, epsilon, timeout, unit, handler);
    }

    public <T> AsynchAtomicApproximateDoubleTemplate<T> newAsynchAtomicApproximateDouble(String name,
                                                                                         double epsilon,
                                                                                         double v,
                                                                                         ApproximateConsensusHandler<T> handler)
    {
        this.consensusLock.lock();

        try
        {
            if (!asyncConsensus)
                this.asyncReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.consensusLock.unlock();
        }

        return new AsynchAtomicApproximateDoubleTemplate<>(this.msgManager, name, epsilon, v, handler);
    }

    public AtomicApproximateDouble newAtomicApproximateDouble(String name, double epsilon, long timeout,
                                                                    TimeUnit unit)
    {
        this.consensusLock.lock();

        try
        {
            if (!synchConsensus)
                this.synchReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.consensusLock.unlock();
        }

        return new AtomicApproximateDouble(this.msgManager, name, epsilon, timeout, unit);
    }

    public AsynchAtomicApproximateDouble newAsynchAtomicApproximateDouble(String name, double epsilon, double v)
    {
        this.consensusLock.lock();

        try
        {
            if (!asyncConsensus)
                this.asyncReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.consensusLock.unlock();
        }

        return new AsynchAtomicApproximateDouble(this.msgManager, name, epsilon, v);
    }

    public AtomicInexactDouble newAtomicInexactDouble(String name, double epsilon, double v,
                                                                                long timeout, TimeUnit unit)
    {
        this.consensusLock.lock();

        try
        {
            if (!synchConsensus)
                this.synchReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.consensusLock.unlock();
        }

        return new AtomicInexactDouble(this.msgManager, name, epsilon, v, timeout, unit);
    }

    public AtomicApproximateDouble newAtomicApproximateDouble(String name, double epsilon, double v, long timeout,
                                                                    TimeUnit unit, FaultClass faultClass)
    {
        this.consensusLock.lock();

        try
        {
            if (!synchConsensus)
                this.synchReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.consensusLock.unlock();
        }

        return new AtomicApproximateDouble(this.msgManager, name, epsilon, v, timeout, unit, faultClass);
    }

    public AsynchAtomicApproximateDouble newAsynchAtomicApproximateDouble(String name, double epsilon, double v,
                                                                          FaultClass faultClass)
    {
        this.consensusLock.lock();

        try
        {
            if (!asyncConsensus)
                this.asyncReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.consensusLock.unlock();
        }

        return new AsynchAtomicApproximateDouble(this.msgManager, name, epsilon, v, faultClass);
    }

    public AtomicInexactDouble newAtomicInexactDouble(String name, double epsilon, double v,
                                                      long timeout, TimeUnit unit,
                                                      FaultClass faultClass)
    {
        this.consensusLock.lock();

        try
        {
            if (!synchConsensus)
                this.synchReady.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.consensusLock.unlock();
        }

        return new AtomicInexactDouble(this.msgManager, name, epsilon, v, timeout, unit, faultClass);
    }

    public final boolean isLeader()
    {
        return this.isBroker;
    }

    public final int n()
    {
        return this.connectedProcesses.get();
    }

    // send OK to terminate
    // asynchronous operation, be careful!
    public void terminate()
    {
        // send leader OK to terminate
        if(!this.isLeader())
        {
            // encode termination message
            byte[] encodedMsg = this.serializer.encodeWithHeader(new IntegrationMessage(MessageType.TERMINATE),
                MessageType.TERMINATE);

            // prepare new message attachment
            Attachment attachment = new Attachment();
            // write msg
            attachment.buffer.put(encodedMsg);
            attachment.buffer.flip();

            this.broker.safeWrite(attachment.buffer, attachment, WriteTerminationHandler);
        }
        else
        {
            this.wantTermination.add(this.myAddress);

            if(this.wantTermination.size() == this.n())
                this.killAll();
        }
    }
}
