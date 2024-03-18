package core;

import Interface.communication.address.AddressInterface;
<<<<<<< HEAD
import Interface.communication.communicationHandler.Broadcast;
=======
>>>>>>> FixingFinalDissertationVersion
import Interface.communication.communicationHandler.CommunicationManager;
import Interface.communication.groupConstitution.OtherNodeInterface;
import Interface.communication.communicationHandler.Subscription;
import Interface.consensus.utils.ApproximateConsensusHandler;
import org.javatuples.Triplet;
import utils.communication.communicationHandler.AsynchMessageManager;
import utils.communication.address.Address;
import utils.communication.communicationHandler.Broadcast.AsyncBroadcast;
import utils.communication.groupConstitution.OtherNode;
import utils.communication.message.ExpectedMessageSize;
import utils.communication.message.IntegrationMessage;
import utils.communication.message.MessageType;
import utils.communication.serializer.MessageSerializer;
import utils.communication.groupConstitution.GroupConstitution;
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

/**
 * Processor class acts as an entry point to the toolkit. Serves as a node in the communication grouo, with the task
 * of maintaining a knowledge of the communication network and creating new atomic approximate variables. On
 * instantiation, the Processor will attempt to connect to a known leader/broker, which will bootstrap it into the
 * network, by providing it with a list of the network's participants. On instantiating new atomic approximate
 * variables, every method will block until the minimum number of group participants is reached. This number is
 * either established prior as being 4 for synchronous variables and 6 for asynchronous ones, or it can be defined on
 * instantiation. Once the Processor is created, the minimum number of participants cannot be changed.
 */
public class Processor
{
    private static final int STARTING_NO_THREADS      = Runtime.getRuntime().availableProcessors();
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
    private OtherNodeInterface broker;
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
        OtherNodeInterface otherProcess;
        ByteBuffer       buffer;

        Attachment()
        {
            this.buffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
        }

        public Attachment(AddressInterface otherProcessAddress, OtherNodeInterface otherProcess)
        {
            this.otherProcessAddress = otherProcessAddress;
            this.otherProcess        = otherProcess;
            this.buffer              = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
        }
    }

    // for new process to broadcast ready to every process
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
                        (new AsyncBroadcast()).broadcast(payload, groupConstitution)
                                .thenRun(Processor.this::openState);
                    }
                }

                @Override
                public void failed(Throwable exc, Attachment attachment)
                {
                    exc.printStackTrace();
                }
            };

    // for the broker, sends each member's address
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
                    byte[] payload = serializer.encodeWithHeader(
                            new IntegrationMessage(address, MessageType.INTEGRATION),
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

    // for broker to read the new process's address
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

                if (msg.address != null)
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
                    attachment.otherProcessAddress = msg.address;
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

    // for already integrated process to read from new process
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

                attachment.otherProcessAddress = msg.address;
                if(attachment.otherProcessAddress != null)
                    attachment.otherProcessAddress.setHost(
                            Objects.requireNonNullElse(
                                    attachment.otherProcess.getRemoteAddress(),
                                    new InetSocketAddress("localhost", 1)).getHostName());

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


    /* ************ */
    /* CONSTRUCTORS */
    /* ************ */

    /**
     * Create new Processor running on localhost, connecting him immediatly to the leader Processor. If the leader
     * port and the Processor's own coincide, then this Processor is the leader.
     * @param port Processor port
     * @param brokerPort leader Processor port
     * @throws IOException throws exception if any is thrown on opening server socket, connecting to leader or
     * connecting to any other Processors
     */
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

    /**
     * Create new Processor running on localhost, connecting him immediatly to the leader Processor. If the leader port
     * and the Processor's own coincide, then this Processor is the leader.
     * @param port Processor port
     * @param brokerPort leader Processor port
     * @param minimumProcesses minimum number of elements in group needed for new consensus instance to be created.
     * @throws IOException throws exception if any is thrown on opening server socket, connecting to leader or
     * connecting to any other Processors
     */
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

    /**
     * Create new Processor, connecting him immediatly to the leader Processor, with a defined
     * minimum number of elements the network needs to have before any atomic approximate variables can be
     * instantiated. If the leader address and the Processor's own coincide, then this Processor is the leader.
     * @param port Processor port
     * @param host Processor host
     * @param brokerPort leader Processor port
     * @param brokerHost leader Processor host
     * @throws IOException throws exception if any is thrown on opening server socket, connecting to leader or
     * connecting to any other Processors
     */
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
        this.minimumProcesses = Math.min(SYNCH_MIN_PROCS, ASYNC_MIN_PROCS);

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

    /**
     * Create new Processor, connecting him immediatly to the leader Processor, with a defined
     * minimum number of elements the network needs to have before any atomic approximate variables can be
     * instantiated. If the leader address and the Processor's own coincide, then this Processor is the leader.
     * @param port Processor port
     * @param host Processor host
     * @param brokerPort leader Processor port
     * @param brokerHost leader Processor host
     * @param minimumProcesses minimum number of elements in group needed for new consensus instance to be created.
     * @throws IOException throws exception if any is thrown on opening server socket, connecting to leader or
     * connecting to any other Processors
     */
    public Processor(String port, String host, String brokerPort, String brokerHost, int minimumProcesses) throws IOException
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
        this.minimumProcesses = minimumProcesses;

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
        this.broker = new OtherNode();
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
                attachment.otherProcess = new OtherNode(newConnection);

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
        this.msgManager.addToGroup(myAddress, new OtherNode());

        try
        {
            // Open socket for broker connection
            this.broker =
                    new OtherNode(AsynchronousSocketChannel.open(AsynchronousChannelGroup.withThreadPool(this.threadPool)));

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
                attachment.otherProcess = new OtherNode(newConnection);
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
        Triplet<AddressInterface, byte[], Byte> msgPacket = this.msgManager.getNextMessage(this.wantedTypesSubscription);
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
    private void handleJoinMsg(AddressInterface address, OtherNodeInterface process)
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
    private void handleReadyMsg(AddressInterface address, OtherNodeInterface process)
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

        if(msg.address == null) // receiving number of processes in group
        {
            // set group size
            this.totalProcesses.addAndGet(msg.groupSize);
            // if receivedMembers == groupSize
            if(this.memberAddressesReceived.get() == this.totalProcesses.get())
                (new AsyncBroadcast()).broadcast(this.serializer.encodeWithHeader(
                                new IntegrationMessage(MessageType.READY),
                                MessageType.READY),
                        this.groupConstitution)
                        .thenRun(this::openState);
        }
        else // receiving the address of one of the processes in the group
        {
            try
            {
                Attachment attachment = new Attachment(msg.address,
                        new OtherNode(AsynchronousSocketChannel.open(AsynchronousChannelGroup.withThreadPool(this.threadPool))));

                attachment.otherProcess.connect(
                        new InetSocketAddress(msg.address.getHost(), msg.address.getPort()),
                        attachment,
                        new CompletionHandler<>() {
                            @Override
                            public void completed(Void result, Attachment attachment)
                            {
                                // add to group
                                msgManager.addToGroup(msg.address, attachment.otherProcess);

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

    // after joining, check if we're finally ready for consensus
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
            for(var channel : this.groupConstitution.values())
                if(channel.isOther())
                    channel.close();

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

    /**
     * Create new AtomicApproximateDouble (updated with synchDLPSW86) with initial vote
     * @param name name of the atomic variable
     * @param epsilon target precision
     * @param v initial vote
     * @param timeout timeout value
     * @param unit timeout unit
     * @return new instance of an AtomicApproximateDouble
     */
    public AtomicApproximateDouble newAtomicApproximateDouble(String name,
                                                              double epsilon,
                                                              double v,
                                                              long timeout,
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

    /**
     * Create new AtomicApproximateDoubleTemplate (updated with the given algorithm) with initial vote
     * @param name name of the atomic variable
     * @param epsilon target precision
     * @param v initial vote
     * @param timeout timeout value
     * @param unit timeout unit
     * @param handler implementation of ApproximateConsensusHandler interface
     * @return new instance of AtomicApproximateDoubleTemplate
     * @param <T> ConsensusAttachment for the handler
     */
    public <T> AtomicApproximateDoubleTemplate<T> newAtomicApproximateDouble(String name,
                                                                             double epsilon,
                                                                             double v,
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

    /**
     * Create new AtomicApproximateDoubleTemplate (updated with the given algorithm) with initial vote and whose
     * broadcast primitive is the given one
     * @param name name of the atomic variable
     * @param epsilon target precision
     * @param v initial vote
     * @param timeout timeout value
     * @param unit timeout unit
     * @param handler implementation of ApproximateConsensusHandler interface
     * @param broadcast instance of specific broadcast implementation
     * @return new instance of AtomicApproximateDoubleTemplate
     * @param <T> ConsensusAttachment for the handler
     */
    public <T> AtomicApproximateDoubleTemplate<T> newAtomicApproximateDouble(String name,
                                                                             double epsilon,
                                                                             double v,
                                                                             long timeout,
                                                                             TimeUnit unit,
                                                                             ApproximateConsensusHandler<T> handler,
                                                                             Broadcast broadcast)
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

        return new AtomicApproximateDoubleTemplate<>(this.msgManager, name, epsilon, v, timeout, unit, handler, broadcast);
    }

    /**
     * Create new AtomicApproximateDoubleTemplate (updated with the given algorithm)
     * @param name name of the atomic variable
     * @param epsilon taret precision
     * @param timeout timeout value
     * @param unit timeout unit
     * @param handler implementation of ApproximateConsensusHandler interface
     * @return new instance of AtomicApproximateDoubleTemplate
     * @param <T> ConsensusAttachment for the handler
     */
    public <T> AtomicApproximateDoubleTemplate<T> newAtomicApproximateDouble(String name,
                                                                             double epsilon,
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

    /**
     * Create new AtomicApproximateDoubleTemplate (updated with the given algorithm) whose
     * broadcast primitive is the given one
     * @param name name of the atomic variable
     * @param epsilon taret precision
     * @param timeout timeout value
     * @param unit timeout unit
     * @param handler implementation of ApproximateConsensusHandler interface
     * @param broadcast instance of specific broadcast implementation
     * @return new instance of AtomicApproximateDoubleTemplate
     * @param <T> ConsensusAttachment for the handler
     */
    public <T> AtomicApproximateDoubleTemplate<T> newAtomicApproximateDouble(String name,
                                                                             double epsilon,
                                                                             long timeout,
                                                                             TimeUnit unit,
                                                                             ApproximateConsensusHandler<T> handler,
                                                                             Broadcast broadcast)
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

        return new AtomicApproximateDoubleTemplate<>(this.msgManager, name, epsilon, timeout, unit, handler, broadcast);
    }

    /**
     * Create new AsyncAtomicApproximateDoubleTemplate (updated with the given algorithm) with initial vote
     * @param name name of the async atomic variable
     * @param epsilon target precision
     * @param v initial vote
     * @param handler implementation of ApproximateConsensusHandler interface
     * @return new instance of AsyncAtomicApproximateDoubleTemplate
     * @param <T> ConsensusAttachment for the handler
     */
    public <T> AsyncAtomicApproximateDoubleTemplate<T> newAsynchAtomicApproximateDouble(String name,
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

        return new AsyncAtomicApproximateDoubleTemplate<>(this.msgManager, name, epsilon, v, handler);
    }

    /**
     * Create new AsyncAtomicApproximateDoubleTemplate (updated with the given algorithm) with initial vote and whose
     * broadcast primitive is the one given
     * @param name name of the async atomic variable
     * @param epsilon target precision
     * @param v initial vote
     * @param handler implementation of ApproximateConsensusHandler interface
     * @param broadcast instance of specific broadcast implementation
     * @return new instance of AsyncAtomicApproximateDoubleTemplate
     * @param <T> ConsensusAttachment for the handler
     */
    public <T> AsyncAtomicApproximateDoubleTemplate<T> newAsynchAtomicApproximateDouble(String name,
                                                                                        double epsilon,
                                                                                        double v,
                                                                                        ApproximateConsensusHandler<T> handler,
                                                                                        Broadcast broadcast)
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

        return new AsyncAtomicApproximateDoubleTemplate<>(this.msgManager, name, epsilon, v, handler, broadcast);
    }

    /**
     * Create new AtomicApproximateDouble (updated with synchDLPSW86)
     * @param name name of the atomic variable
     * @param epsilon target precision
     * @param timeout timeout value
     * @param unit timeout unit
     * @return new instance of AtomicApproximateDouble
     */
    public AtomicApproximateDouble newAtomicApproximateDouble(String name,
                                                              double epsilon,
                                                              long timeout,
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

    /**
     * Create new AsyncAtomicApproximateDouble (updated with asyncDLPSW86) with initial vote
     * @param name name of the async atomic variable
     * @param epsilon target precision
     * @param v initial vote
     * @return new instance of AsyncAtomicApproximateDouble
     */
    public AsyncAtomicApproximateDouble newAsynchAtomicApproximateDouble(String name,
                                                                         double epsilon,
                                                                         double v)
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

        return new AsyncAtomicApproximateDouble(this.msgManager, name, epsilon, v);
    }

    /**
     * Create new AtomicInexactDouble (updated with FCA) with initial vote
     * @param name name of the atomic variable
     * @param epsilon target precision
     * @param v initial vote
     * @param timeout timeout value
     * @param unit timeout unit
     * @return new instance of AtomicInexactDouble
     */
    public AtomicInexactDouble newAtomicInexactDouble(String name,
                                                      double epsilon,
                                                      double v,
                                                      long timeout,
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

        return new AtomicInexactDouble(this.msgManager, name, epsilon, v, timeout, unit);
    }

    /**
     * Create new AtomicApproximateDouble (updated with synchDLPSW86) with initial vote and with a fault class
     * defining the faulty behaviour of the algorithm (for testing purposes)
     * @param name name of the atomic variable
     * @param epsilon target precision
     * @param v initial vote
     * @param timeout timeout value
     * @param unit timeout unit
     * @param faultClass enum defining the fault class associated with this faulty variable
     * @return new faulty instance of AtomicApproximateDouble
     */
    public AtomicApproximateDouble newAtomicApproximateDouble(String name,
                                                              double epsilon,
                                                              double v,
                                                              long timeout,
                                                              TimeUnit unit,
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

        return new AtomicApproximateDouble(this.msgManager, name, epsilon, v, timeout, unit, faultClass);
    }

    /**
     * Create new AsyncAtomicApproximateDouble (updated with asyncDLPSW86) with initial vote and with a fault class
     * defining the faulty behaviour of the algorithm (for testing purposes)
     * @param name name of the async atomic variable
     * @param epsilon target precision
     * @param v initial vote
     * @param faultClass enum defining the fault class associated with this faulty variable
     * @return new faulty instance of AsyncAtomicApproximateDouble
     */
    public AsyncAtomicApproximateDouble newAsynchAtomicApproximateDouble(String name,
                                                                         double epsilon,
                                                                         double v,
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

        return new AsyncAtomicApproximateDouble(this.msgManager, name, epsilon, v, faultClass);
    }

    /**
     * Create new AtomicInexactDouble (updated with FCA) with initial vote and with a fault class defining the faulty
     * behaviour of the algorithm (for testing purposes)
     * @param name name of the atomic variable
     * @param epsilon target precision
     * @param v initial vote
     * @param timeout timeout value
     * @param unit timeout unit
     * @param faultClass enum defining the falut class associated with this faulty variable
     * @return new faulty instance of AtomicInexactDouble
     */
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

    /**
     * Check if processor is broker/leader
     * @return true if processor is broker, false otherwise
     */
    public final boolean isLeader()
    {
        return this.isBroker;
    }

    /**
     * Get the number of Processors' in this Processor's communication network
     * @return number of Processors' in this Processor's communication network
     */
    public final int n()
    {
        return this.msgManager.getGroupConstitution().size();
    }

    /**
     * Send request to terminate (asynchronous)
     */
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

    /**
     * Close this Processor's server socket, impeding new connections
     */
    public void close()
    {
        this.end();
    }
}
