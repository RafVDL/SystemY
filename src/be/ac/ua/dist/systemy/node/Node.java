package be.ac.ua.dist.systemy.node;

import be.ac.ua.dist.systemy.Constants;
import be.ac.ua.dist.systemy.namingServer.NamingServerInterface;
import be.ac.ua.dist.systemy.networking.Client;
import be.ac.ua.dist.systemy.networking.Communications;
import be.ac.ua.dist.systemy.networking.Server;
import be.ac.ua.dist.systemy.networking.packet.*;
import be.ac.ua.dist.systemy.networking.tcp.TCPServer;
import be.ac.ua.dist.systemy.networking.udp.MulticastServer;
import be.ac.ua.dist.systemy.networking.udp.UnicastServer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static be.ac.ua.dist.systemy.Constants.RMI_PORT;

public class Node implements NodeInterface {

    private final InetAddress ownAddress;
    private final int ownHash;
    private String grantedDownloadFile;
    private Map<String, FileHandle> localFiles;
    private Map<String, FileHandle> replicatedFiles;
    private Map<String, FileHandle> ownerFiles;
    private Set<String> downloadingFiles;
    private Map<String, Integer> allFiles;
    private ObservableList<String> allFilesObservable;
    private HashMap<String, Integer> fileAgentFiles;


    private volatile InetAddress namingServerAddress;
    private NamingServerInterface namingServerStub;
    private InetAddress prevAddress;
    private InetAddress nextAddress;
    private InetAddress ownerAddress;

    private volatile int prevHash;
    private volatile int nextHash;

    private boolean isRunning = true;
    private boolean isGUIStarted;
    private boolean isInitialized = false;
    private boolean shutdown = false;

    private InetAddress multicastGroup;
    private Server multicastServer;
    private Server tcpServer;

    private long joinTimestamp = 0;
    private int retries = 0;

    public Node(String nodeName, InetAddress address, boolean isGUIStarted) throws UnknownHostException {
        this.isGUIStarted = isGUIStarted;

        this.ownAddress = address;
        this.ownerAddress = ownAddress;
        this.ownHash = calculateHash(nodeName);
        this.allFiles = new HashMap<>();
        this.allFilesObservable = FXCollections.observableArrayList(new ArrayList<>());
        this.grantedDownloadFile = "null";

        this.localFiles = new HashMap<>();
        this.replicatedFiles = new HashMap<>();
        this.ownerFiles = new HashMap<>();
        this.downloadingFiles = new HashSet<>();

        multicastGroup = InetAddress.getByName(Constants.MULTICAST_ADDRESS);
    }

    public InetAddress getOwnAddress() {
        return ownAddress;
    }

    @Override
    public InetAddress getPrevAddress() {
        return prevAddress;
    }

    @Override
    public InetAddress getNextAddress() {
        return nextAddress;
    }

    @Override
    public int getOwnHash() {
        return ownHash;
    }

    @Override
    public int getPrevHash() {
        return prevHash;
    }

    @Override
    public int getNextHash() {
        return nextHash;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void setInitialized(boolean initialized) {
        isInitialized = initialized;
    }

    public void setNamingServerAddress(InetAddress ipAddress) {
        this.namingServerAddress = ipAddress;
    }

    public InetAddress getNamingServerAddress() {
        return namingServerAddress;
    }

    @Override
    public Map<String, FileHandle> getLocalFiles() {
        return localFiles;
    }

    @Override
    public Map<String, FileHandle> getReplicatedFiles() {
        return replicatedFiles;
    }

    @Override
    public Map<String, FileHandle> getOwnerFiles() {
        return ownerFiles;
    }

    public HashMap<String, Integer> getFileAgentFiles() {
        return fileAgentFiles;
    }

    public Set getDownloadingFiles() {
        return downloadingFiles;
    }

    public String getFileLockRequest() {
        AtomicReference<String> fileLockRequest = new AtomicReference<>("null");
        allFiles.forEach((String key, Integer value) -> {
            if (ownHash == value) fileLockRequest.set(key);
        });
        return fileLockRequest.get();
    }

    public void downloadAFile(String filename) {
        if (allFiles.containsKey(filename)) {
            allFiles.put(filename, ownHash);
        } else {
            System.out.println("File does not exist or you are the owner, try again");
        }
    }

    @Override
    public Map<String, Integer> getAllFiles() {
        return allFiles;
    }

    public ObservableList<String> getAllFilesObservable() {
        return allFilesObservable;
    }

    @Override
    public void addLocalFileList(FileHandle fileHandle) {
        localFiles.put(fileHandle.getFile().getName(), fileHandle.getAsLocal());
    }

    @Override
    public void addReplicatedFileList(FileHandle fileHandle) {
        if (localFiles.containsKey(fileHandle.getFile().getName())) {
            replicatedFiles.put(fileHandle.getFile().getName(), fileHandle.getAsLocal());
        } else {
            replicatedFiles.put(fileHandle.getFile().getName(), fileHandle);
        }
    }

    @Override
    public void addOwnerFileList(FileHandle fileHandle) {
        if (localFiles.containsKey(fileHandle.getFile().getName())) {
            ownerFiles.put(fileHandle.getFile().getName(), fileHandle.getAsLocal());
        } else {
            ownerFiles.put(fileHandle.getFile().getName(), fileHandle);
        }

    }

    @Override
    public void removeLocalFile(FileHandle fileHandle) {
        localFiles.remove(fileHandle.getFile().getName());
    }

    @Override
    public void removeReplicatedFile(FileHandle fileHandle) {
        replicatedFiles.remove(fileHandle.getFile().getName());
    }

    @Override
    public void removeOwnerFile(FileHandle fileHandle) {
        ownerFiles.remove(fileHandle.getFile().getName());
    }

    public void addAllFileList(String file, int value) {
        this.allFiles.put(file, value);
    }

    public void removeAllFileList(String file) {
        this.allFiles.remove(file);
    }

    public void setFileAgentFiles(HashMap<String, Integer> files) {
        this.fileAgentFiles = files;
    }

    public void setDownloadFileGranted(String download) {
        this.grantedDownloadFile = download;
    }

    public String getDownloadFileGranted() {
        return this.grantedDownloadFile;
    }

    @Override
    public void downloadFile(String remoteFileName, String localFileName, InetAddress remoteAddress) {
        downloadingFiles.add(localFileName.split("/")[1]);
        System.out.println("Adding " + localFileName + " to downloading files");
        //Open tcp socket to server @remoteAddress:port

        try {
            Client client = Communications.getTCPClient(remoteAddress, Constants.TCP_PORT);
            DataInputStream dis = client.getConnection().getDataInputStream();
            FileOutputStream fos = new FileOutputStream(localFileName);

            FileRequestPacket fileRequestPacket = new FileRequestPacket(remoteFileName);
            client.sendPacket(fileRequestPacket);

            int fileSize = dis.readInt();

            byte[] buffer = new byte[4096];
            int read;
            int remaining = fileSize;

            while ((read = dis.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                remaining -= read;
                fos.write(buffer, 0, read);
            }

            fos.close();
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        downloadingFiles.remove(localFileName.split("/")[1]);
        System.out.println("Removing " + localFileName + " from downloading files");
    }

    /**
     * Removes a file from the Node. This includes the actual file on disk as well as the Maps.
     *
     * @param fileHandle the fileHandle of the file to remove
     */
    @Override
    public void deleteFileFromNode(FileHandle fileHandle) {
        ownerFiles.remove(fileHandle.getFile().getName());
        localFiles.remove(fileHandle.getFile().getName());
        replicatedFiles.remove(fileHandle.getFile().getName());
        if (fileHandle.getFile().exists()) {
            fileHandle.getFile().delete();
        } else if (fileHandle.getAsReplicated().getFile().exists()) {
            fileHandle.getAsReplicated().getFile().delete();
        }
    }

    /**
     * Marks a file so it is ready to remove from the entire network
     *
     * @param filename the name of the file to remove
     */
    @Override
    public void deleteFileFromNetwork(String filename) {
        if (allFiles.containsKey(filename)) {
            allFiles.put(filename, -1);
        } else {
            System.out.println("File does not exist or you are the owner, try again");
        }
    }

    /**
     * Deletes a the local copy of a downloaded file and updates the corresponding FileHandle at the owner
     *
     * @param filename the name of the file to remove
     */
    public void deleteDownloadedFile(String filename) {
        try {
            InetAddress ownerAddress = namingServerStub.getOwner(filename);

            Registry ownerNodeRegistry = LocateRegistry.getRegistry(ownerAddress.getHostAddress(), Constants.RMI_PORT);
            NodeInterface ownerNodeStub = (NodeInterface) ownerNodeRegistry.lookup("Node");
            ownerNodeStub.removeFromAvailableNodes(filename, ownHash);

            File file = new File(Constants.DOWNLOADED_FILES_PATH + filename);
            if (file.isFile()) {
                file.delete();
            }

        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add a Node's hash from the list of available hashes. Note that this method only work for updating the FileHandle
     * of a local file (otherwise it is unnecessary anyways).
     *
     * @param fileName  the fileName of corresponding fileHandle to update
     * @param hashToAdd the hash to add
     */
    @Override
    public void addToAvailableNodes(String fileName, int hashToAdd) {
        if (!ownerFiles.containsKey(fileName)) {
            System.err.println("Error: trying to update a FileHandle of a file that this Node does not own.");
            return;
        }
        ownerFiles.get(fileName).getAvailableNodes().add(hashToAdd);
    }

    /**
     * Remove a Node's hash from the list of available hashes. Note that this method only work for updating the FileHandle
     * of a local file (otherwise it is unnecessary anyways).
     *
     * @param fileName     the fileName of corresponding fileHandle to update
     * @param hashToRemove the hash to remove
     */
    @Override
    public void removeFromAvailableNodes(String fileName, int hashToRemove) {
        if (!ownerFiles.containsKey(fileName)) {
            System.out.println("Error: trying to update a FileHandle of a file that this Node does not own.");
            return;
        }
        ownerFiles.get(fileName).getAvailableNodes().remove(hashToRemove);
    }

    @Override
    public void increaseDownloads(String fileName) {
        if (!ownerFiles.containsKey(fileName)) {
            System.out.println("Error: trying to update a FileHandle of a file that this Node does not own.");
            return;
        }
        ownerFiles.get(fileName).increaseDownloads();
    }

    /**
     * Updates the next neighbour of this node
     *
     * @param newAddress of the next neighbour
     * @param newHash    of the next neighbour
     */
    @Override
    public void updateNext(InetAddress newAddress, int newHash) {
        if (newAddress == null) {
            try {
                newAddress = getAddressByHash(newHash);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        nextAddress = newAddress;
        nextHash = newHash;
    }

    /**
     * Updates the previous neighbour of this node
     *
     * @param newAddress of the previous neighbour
     * @param newHash    of the previous neighbour
     */
    @Override
    public void updatePrev(InetAddress newAddress, int newHash) {
        if (newAddress == null) {
            try {
                newAddress = getAddressByHash(newHash);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        prevAddress = newAddress;
        prevHash = newHash;
    }

    /**
     * Starts isRunning the file agent, this method can be run via RMI
     *
     * @param fileAgentFiles: list of all files
     *                        String: filename
     *                        Integer: hash of node that has a lock request on that file
     */
    @Override
    public void runFileAgent(HashMap<String, Integer> fileAgentFiles) throws InterruptedException, RemoteException, NotBoundException {
        // This method can get called (RMI) before the node has finished initializing -> wait
        while (!isInitialized) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.err.println("Interrupted while waiting for Node to initialize.");
            }
        }

        Random rand = new Random();
        int number = rand.nextInt(100000);
        namingServerStub.latestFileAgent(ownAddress, ownHash, nextHash, number);

        ownerAddress = ownAddress;
        Thread t = new Thread(new FileAgent(fileAgentFiles, ownAddress));
        t.start();
        t.join(); //wait for thread to stop

        // Update the observable for the GUI if the node has a GUI
        if (isGUIStarted) {
            Platform.runLater(() -> {
                for (Iterator<String> iterator = fileAgentFiles.keySet().iterator(); iterator.hasNext(); ) {
                    String fileName = iterator.next();
                    if (!allFilesObservable.contains(fileName)) {
                        allFilesObservable.add(fileName);
                    }
                }

                allFilesObservable.retainAll(fileAgentFiles.keySet());
            });
        }


        Thread t2 = new Thread(() -> {
            InetAddress tempAddress = nextAddress;
            int tempHash = nextHash;
            try {
                Registry registry = LocateRegistry.getRegistry(tempAddress.getHostAddress(), RMI_PORT);
                NodeInterface stub = (NodeInterface) registry.lookup("Node");
                stub.runFileAgent(fileAgentFiles);
            } catch (RemoteException | NotBoundException | InterruptedException e) {
                try {
                    //failureAgent wordt voor de eerste keer gestart en zal uitgevoerd vooraleer de fileAgent terug zal worden opgestart
                    runFailureAgent(tempHash, ownHash, ownAddress);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                } catch (RemoteException e1) {
                    e1.printStackTrace();
                } catch (NotBoundException e1) {
                    e1.printStackTrace();
                }
            }

        });
        String fileLockRequest = getFileLockRequest();
        if (!grantedDownloadFile.equals("null") && !grantedDownloadFile.equals("downloading") && !fileLockRequest.equals("null")) { //download file
            Thread t3 = new Thread(() -> {
                try {
                    // Get ownerAddress from NamingServer via RMI.
                    ownerAddress = namingServerStub.getOwner(grantedDownloadFile);
                    if (ownerAddress.equals(ownAddress)) {
                        ownerAddress = getLocalAddressOfFile(grantedDownloadFile);
                    } else {
                        Registry registry = LocateRegistry.getRegistry(ownerAddress.getHostAddress(), RMI_PORT);
                        NodeInterface ownerStub = (NodeInterface) registry.lookup("Node");
                        ownerAddress = ownerStub.getLocalAddressOfFile(grantedDownloadFile);
                    }
                } catch (IOException | NotBoundException e) {
                    e.printStackTrace();
                }

                if (ownerAddress.equals(ownAddress)) {
                    System.out.println("You are the owner");
                    grantedDownloadFile = "null";
                    allFiles.put(fileLockRequest, 0);
                } else if (!grantedDownloadFile.equals("downloading")){
                    String temp = grantedDownloadFile;
                    grantedDownloadFile = "downloading";
                    downloadFile(Constants.LOCAL_FILES_PATH + temp, Constants.DOWNLOADED_FILES_PATH + temp, ownerAddress);
                }
            });
            t3.start();
            Thread.sleep(1000);
        }
        if (shutdown) {
            shutdown();
        }
        t2.start();
    }

    @Override
    public void runFailureAgent(int hashFailed, int hashStart, InetAddress currNode) throws InterruptedException, RemoteException, NotBoundException {
        //don't start FailureAgent if only two nodes in network of which one is failed
        if (!(hashFailed == prevHash && hashFailed == nextHash)) {
            Thread t = new Thread(new FailureAgent(hashFailed, hashStart, currNode));
            t.start();
            t.join(); //wait for thread to stop
            //Check if not alone in network and if next neighbour isn't the failed node or the start node
            if (ownHash != nextHash && hashFailed != nextHash && hashStart != nextHash) {
                Thread t4 = new Thread(() -> {
                    try {
                        Registry registry = LocateRegistry.getRegistry(nextAddress.getHostAddress(), RMI_PORT);
                        NodeInterface stub = (NodeInterface) registry.lookup("Node");
                        stub.runFailureAgent(hashFailed, hashStart, nextAddress);
                    } catch (RemoteException | NotBoundException | InterruptedException e) {
                        System.out.println("ERROR1: Not able to run FailureAgent on next node!");
                        e.printStackTrace();
                    }
                });
                t4.start();
            }
            //Check if next neighbour is the failed node
            if (hashFailed == nextHash) {
                Thread t5 = new Thread(() -> {
                    try {
                        //Need to skip next neighbour cause this one is the failed node. Need to start FailureAgent on next neighbour of failed node
                        int[] neighboursOfFailed = namingServerStub.getNeighbours(hashFailed);
                        int hashOfNextNeighbour = neighboursOfFailed[1];
                        InetAddress addressOfNextNeighbour = namingServerStub.getIPNode(hashOfNextNeighbour);

                        Registry registry = LocateRegistry.getRegistry(addressOfNextNeighbour.getHostAddress(), RMI_PORT);
                        NodeInterface stub = (NodeInterface) registry.lookup("Node");
                        //Check if next neighbour of failed node isn't the start node
                        if (hashOfNextNeighbour != hashStart) {
                            stub.runFailureAgent(hashFailed, hashStart, addressOfNextNeighbour);
                        } else {
                            System.out.println("Starting failureHandler...");
                            FailureHandler failureHandler = new FailureHandler(hashFailed, this);
                            failureHandler.repairFailedNode();
                            System.out.println("Failure handled! Restarting fileAgent...");
                            stub.runFileAgent(fileAgentFiles);
                            System.out.println("FileAgent restarted.");
                        }
                    } catch (RemoteException | NotBoundException | InterruptedException e) {
                        System.out.println("ERROR2: Not able to run FailureAgent on next node or restart the FileAgent!");
                        e.printStackTrace();
                    }
                });
                t5.start();
            }
            //Agent is around the network and we can now remove the failed node from the network and afterwards restart FileAgent
            if (nextHash == hashStart) {
                Thread t6 = new Thread(() -> {
                    try {
                        Registry registry = LocateRegistry.getRegistry(nextAddress.getHostAddress(), RMI_PORT);
                        NodeInterface stub = (NodeInterface) registry.lookup("Node");
                        System.out.println("Starting failureHandler...");
                        FailureHandler failureHandler = new FailureHandler(hashFailed, this);
                        failureHandler.repairFailedNode();
                        System.out.println("Failure handled! Restarting fileAgent...");
                        stub.runFileAgent(fileAgentFiles);
                        System.out.println("FileAgent restarted.");
                    } catch (RemoteException | NotBoundException | InterruptedException e) {
                        System.out.println("ERROR3: Not able to restart FileAgent!");
                        e.printStackTrace();
                    }
                });
                t6.start();
            }
        }
        //Node is only working node in network, we may delete failed node and restart FileAgent
        else {
            System.out.println("Starting failureHandler...");
            FailureHandler failureHandler = new FailureHandler(hashFailed, this);
            failureHandler.repairFailedNode();
            System.out.println("Failure handled! Restarting fileAgent...");
            runFileAgent(fileAgentFiles);
            System.out.println("FileAgent restarted.");
        }
    }

    /**
     * Gets invoked when a new Node is joining the network.
     * <p>
     * Existing Node checks if the new Node becomes a new neighbour of this Node. If so, it checks whether the new node
     * becomes a previous or next neighbour. If it is a previous, the existing Node only updates its own neighbours.
     * Else, the Node also sends an update (via tcp) to the new node.
     * <p>
     * In the special case that there is only one existing Node in the network, its neighbours are the Node itself. In
     * this case the existing Node should always update the joining Node.
     *
     * @param newAddress the IP-address of the joining node
     * @param newHash    the hash of the joining node
     */
    public void updateNeighbours(InetAddress newAddress, int newHash) {
        if ((ownHash == prevHash) && (ownHash == nextHash)) {
            // NodeCount is currently 0, always update self and the joining Node.

            try {
                Client client = Communications.getTCPClient(newAddress, Constants.TCP_PORT);
                UpdateNeighboursPacket packet = new UpdateNeighboursPacket(ownHash, ownHash);
                client.sendPacket(packet);
            } catch (IOException e) {
                handleFailure(newHash);
                e.printStackTrace();
            }

            updatePrev(newAddress, newHash);
            updateNext(newAddress, newHash);


        } else if ((prevHash < ownHash && newHash < ownHash && newHash > prevHash) // 1, 13
                || (prevHash > ownHash && newHash < ownHash) // 6
                || (prevHash > ownHash && newHash > prevHash) // 5
                || ((prevHash == nextHash) && ((prevHash > ownHash && newHash > prevHash) // 15
                || (prevHash > ownHash && newHash < ownHash)))) { // 16
            // Joining Node sits between previous neighbour and this Node.

            updatePrev(newAddress, newHash);
        } else if ((nextHash > ownHash && newHash > ownHash && newHash < nextHash) // 2, 8
                || (nextHash < ownHash && newHash > ownHash) // 10
                || (nextHash < ownHash && newHash < nextHash) // 11
                || ((prevHash == nextHash) && (prevHash > ownHash && newHash < prevHash && newHash > ownHash) // 14
                || (prevHash < ownHash && newHash < prevHash) // 18
                || (prevHash < ownHash && newHash > ownHash) // 19
                || (prevHash < ownHash && newHash < ownHash && newHash < prevHash))) {
            // Joining Node sits between this Node and next neighbour.

            try {
                Client client = Communications.getTCPClient(newAddress, Constants.TCP_PORT);
                UpdateNeighboursPacket packet = new UpdateNeighboursPacket(ownHash, nextHash);
                client.sendPacket(packet);
            } catch (IOException e) {
                handleFailure(newHash);
                e.printStackTrace();
            }

            updateNext(newAddress, newHash);
        }
    }

    private InetAddress getAddressByHash(int hash) throws IOException {
        Client client = Communications.getUDPClient(multicastGroup, Constants.MULTICAST_PORT);

        final InetAddress[] address = new InetAddress[1];

        CountDownLatch latch = new CountDownLatch(1);

        Server unicastServer = new UnicastServer();
        unicastServer.registerListener(IPResponsePacket.class, ((packet, client1) -> {
            address[0] = packet.getAddress();
            unicastServer.stop();
            latch.countDown();
        }));
        unicastServer.startServer(ownAddress, Constants.UNICAST_PORT);

        GetIPPacket packet = new GetIPPacket(hash);
        client.sendPacket(packet);

        try {
            latch.await(10, TimeUnit.SECONDS);
            return address[0];
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void joinNetwork() throws IOException {
        joinTimestamp = System.currentTimeMillis();
        HelloPacket helloPacket = new HelloPacket();
        Client client = Communications.getUDPClient(multicastGroup, Constants.MULTICAST_PORT);
        client.sendPacket(helloPacket);
    }

    public void leaveNetwork() throws IOException {
        QuitPacket quitPacket = new QuitPacket();
        Client client = Communications.getUDPClient(multicastGroup, Constants.MULTICAST_PORT);
        client.sendPacket(quitPacket);

        multicastServer.stop();

        if (ownHash == nextHash && ownHash == prevHash)
            return;

        if (prevHash == nextHash) {
            Client clientPrev = Communications.getTCPClient(prevAddress, Constants.TCP_PORT);
            clientPrev.sendPacket(new UpdateNeighboursPacket(nextHash, nextHash));
            return;
        }

        Client clientPrev = Communications.getTCPClient(prevAddress, Constants.TCP_PORT);
        clientPrev.sendPacket(new UpdateNeighboursPacket(-1, nextHash));

        Client clientNext = Communications.getTCPClient(nextAddress, Constants.TCP_PORT);
        clientNext.sendPacket(new UpdateNeighboursPacket(prevHash, -1));
    }

    public void handleFailure(int hashFailedNode) {
        try {
            FailureHandler failureHandler = new FailureHandler(hashFailedNode, this);
            failureHandler.repairFailedNode();
        } catch (RemoteException | NotBoundException e) {
            e.printStackTrace();
        }
    }

    public int calculateHash(String name) {
        return Math.abs(name.hashCode() % 32768);
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
    }

    public InetAddress getLocalAddressOfFile(String filename) {
        return ownerFiles.get(filename).getLocalAddress();
    }

    /**
     * Iterates through all files in the LOCAL_FILES_PATH and introduces each one in the network.
     */
    public void discoverLocalFiles() {
        File folder = new File(Constants.LOCAL_FILES_PATH);
        if (!folder.exists())
            folder.mkdir();
        File[] listOfFiles = folder.listFiles();

        for (File file : listOfFiles) {
            if (file.isFile()) {
                System.out.println("Found file " + file.getName());
                FileHandle fileHandle = new FileHandle(file.getName(), true);
                addFileToNetwork(fileHandle);
            } else if (file.isDirectory()) {
                System.out.println("Not checking files in nested folder " + file.getName());
            }
        }

        System.out.println("Finished discovery of " + folder.getName());
    }

    /**
     * Introduces a new local file in the network.
     * <p>
     * The NamingServer gets asked who the owner of the file should be. If this Node should
     * be the owner, the file gets duplicated to the previous neighbour via RMI. If this node should not be the owner, the
     * file gets duplicated to the owner.
     *
     * @param fileHandle enclosing the file to introduce in the network
     */
    public void addFileToNetwork(FileHandle fileHandle) {
        File file = fileHandle.getFile();
        if (!file.isFile()) {
            System.out.println("Trying to add something that is not a file (skipping)");
            return;
        }

        try {
            // Put in local copy in localFiles and update the FileHandle
            localFiles.put(fileHandle.getFile().getName(), fileHandle);
            fileHandle.setLocal(true);
            fileHandle.setLocalAddress(ownAddress);
            fileHandle.getAvailableNodes().add(ownHash);

            replicateWhenJoining(fileHandle);
        } catch (IOException | NotBoundException e) {
            e.printStackTrace();
        }
    }

    public void replicateWhenJoining(FileHandle fileHandle) throws RemoteException, NotBoundException, UnknownHostException {
        File file = fileHandle.getFile();
        InetAddress ownerAddress = namingServerStub.getOwner(file.getName());

        if (ownerAddress == null) // no owner
            return;

        if (ownAddress.equals(nextAddress)) {
            ownerFiles.put(fileHandle.getFile().getName(), fileHandle);
            return;
        }

        Registry nodeRegistry;
        if (ownerAddress.equals(ownAddress)) {
            // Replicate to previous node
            nodeRegistry = LocateRegistry.getRegistry(prevAddress.getHostAddress(), Constants.RMI_PORT);
        } else {
            // Replicate to owner node
            nodeRegistry = LocateRegistry.getRegistry(ownerAddress.getHostAddress(), Constants.RMI_PORT);
        }

        NodeInterface nodeStub = (NodeInterface) nodeRegistry.lookup("Node");
        nodeStub.downloadFile(file.getPath(), Constants.REPLICATED_FILES_PATH + file.getName(), ownAddress);

        fileHandle.getAvailableNodes().add(ownerAddress.equals(ownAddress) ? prevHash : nodeStub.getOwnHash());

        FileHandle newFileHandle = fileHandle.getAsReplicated();

        if (ownerAddress.equals(ownAddress)) {
            ownerFiles.put(fileHandle.getFile().getName(), fileHandle);
            nodeStub.addReplicatedFileList(newFileHandle);
        } else {
            nodeStub.addOwnerFileList(newFileHandle);
        }
    }

    /**
     * Checks if this node is still the owner of the file and moves it around if necessary
     *
     * @param fileHandle to check/update
     */
    public void replicateToNewNode(FileHandle fileHandle) throws RemoteException, NotBoundException, UnknownHostException {
        File file = fileHandle.getFile();

        InetAddress ownerAddress = namingServerStub.getOwner(file.getName());

        if (ownerAddress == null) // No owner
            return;

        if (ownerAddress.equals(ownAddress) && prevHash == nextHash) {
            // Still the owner but only two Nodes in the network -> replicate to second Node.
            Registry nextNodeRegistry = LocateRegistry.getRegistry(nextAddress.getHostAddress(), Constants.RMI_PORT);
            NodeInterface nextNodeStub = (NodeInterface) nextNodeRegistry.lookup("Node");
            nextNodeStub.downloadFile(file.getPath(), Constants.REPLICATED_FILES_PATH + file.getName(), ownAddress);

            fileHandle.getAvailableNodes().add(nextHash);
            FileHandle newFileHandle = fileHandle.getAsReplicated();
            nextNodeStub.addReplicatedFileList(newFileHandle);
            replicatedFiles.remove(fileHandle.getFile().getName());
        } else if (ownerAddress.equals(ownAddress) && prevHash != nextHash) {
            // Still the owner -> no need to update.
        } else if (!ownerAddress.equals(ownAddress)) {
            // Next neighbour is new owner -> move to new owner
            Registry nextNodeRegistry = LocateRegistry.getRegistry(nextAddress.getHostAddress(), Constants.RMI_PORT);
            NodeInterface nextNodeStub = (NodeInterface) nextNodeRegistry.lookup("Node");
            nextNodeStub.downloadFile(file.getPath(), Constants.REPLICATED_FILES_PATH + file.getName(), ownAddress);

            fileHandle.getAvailableNodes().add(nextHash);

            if (prevHash == nextHash) {
                // Only two Nodes in the network -> keep copy as replicated.
                FileHandle newFileHandle = fileHandle.getAsReplicated();
                nextNodeStub.addOwnerFileList(newFileHandle);
                replicatedFiles.put(newFileHandle.getFile().getName(), newFileHandle);
                if (!fileHandle.isLocal()) {
                    try {
                        Files.move(fileHandle.getFile().toPath(), newFileHandle.getFile().toPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                // Just move to new owner
                fileHandle.getAvailableNodes().remove(ownHash);
                FileHandle newFileHandle = fileHandle.getAsReplicated();
                nextNodeStub.addOwnerFileList(newFileHandle);
                replicatedFiles.remove(fileHandle.getFile().getName());
                if (!fileHandle.isLocal()) {
                    try {
                        Files.deleteIfExists(fileHandle.getFile().toPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            ownerFiles.remove(fileHandle.getFile().getName());
        }
    }

    public void replicateFailed(FileHandle fileHandle, InetAddress receiveAddress) throws RemoteException, NotBoundException {
        File file = fileHandle.getFile();

        if (receiveAddress == null) // no owner
            return;

        Registry nodeRegistry = LocateRegistry.getRegistry(receiveAddress.getHostAddress(), Constants.RMI_PORT);
        NodeInterface nodeStub = (NodeInterface) nodeRegistry.lookup("Node");
        nodeStub.downloadFile(file.getPath(), Constants.REPLICATED_FILES_PATH + file.getName(), ownAddress);

        fileHandle.getAvailableNodes().add(receiveAddress.equals(ownAddress) ? prevHash : nodeStub.getOwnHash());

        FileHandle newFileHandle = fileHandle.getAsReplicated();
        nodeStub.addReplicatedFileList(newFileHandle);

        if (receiveAddress.equals(ownAddress)) {
            ownerFiles.put(newFileHandle.getFile().getName(), fileHandle);
        } else {
            nodeStub.addOwnerFileList(newFileHandle);
        }
    }

    public void initializeShutdown() {
        this.shutdown = true;
    }

    /**
     * Transfer all replicated and process all local files. Then leave the network.
     */
    private void shutdown() {
        multicastServer.stop();

        if (!(prevHash == ownHash)) {
            // If alone in the network, just leave

            NodeInterface prevNodeStub = null;
            try {
                Registry prevNodeRegistry = LocateRegistry.getRegistry(prevAddress.getHostAddress(), Constants.RMI_PORT);
                prevNodeStub = (NodeInterface) prevNodeRegistry.lookup("Node");
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }

            if (prevNodeStub != null) {
                // Only one other Node in the network -> always update the other Node and make it owner of the file
                if (prevHash == nextHash) {
                    // Remove ownHash from availableNodes for all replicatedFiles
                    for (Map.Entry<String, FileHandle> entry : replicatedFiles.entrySet()) {
                        try {
                            prevNodeStub.addOwnerFileList(entry.getValue());
                            prevNodeStub.removeFromAvailableNodes(entry.getKey(), ownHash);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    // Process all local files
                    for (Map.Entry<String, FileHandle> localEntry : (new HashMap<>(localFiles)).entrySet()) {
                        try {
                            int downloads;
                            // If this Node is the owner of the file -> check downloads and proceed
                            if (ownerFiles.containsKey(localEntry.getKey())) {
                                downloads = localEntry.getValue().getDownloads();
                            } else {
                                // The other Node is the owner -> check downloads there and proceed
                                downloads = prevNodeStub.getOwnerFiles().get(localEntry.getKey()).getDownloads();
                            }

                            // If downloads = 0 -> delete local copy and copy of owner
                            if (downloads == 0) {
                                prevNodeStub.deleteFileFromNetwork(localEntry.getKey());
                            } else {
                                // Else update download locations in the FileHandle
                                prevNodeStub.addOwnerFileList(localEntry.getValue());
                                prevNodeStub.removeFromAvailableNodes(localEntry.getKey(), ownHash);
                            }
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                } else { // More than two Nodes in the network
                    // Transfer all replicated files
                    for (Map.Entry<String, FileHandle> entry : replicatedFiles.entrySet()) {
                        try {
                            FileHandle replicatedFileHandle = entry.getValue().getAsReplicated();

                            if (prevNodeStub.getLocalFiles().containsValue(entry.getValue())) {
                                // Previous Node has the file as local file -> replicate to previous' previous neighbour and make it owner of the file
                                replicatedFileHandle.getAvailableNodes().remove(ownHash);
                                replicatedFileHandle.getAvailableNodes().add(prevNodeStub.getPrevHash());
                                prevNodeStub.addOwnerFileList(replicatedFileHandle.getAsLocal());

                                Registry prevPrevNodeRegistry = LocateRegistry.getRegistry(prevNodeStub.getPrevAddress().getHostAddress(), Constants.RMI_PORT);
                                NodeInterface prevPrevNodeStub = (NodeInterface) prevPrevNodeRegistry.lookup("Node");

                                prevPrevNodeStub.downloadFile(Constants.REPLICATED_FILES_PATH + entry.getKey(), Constants.REPLICATED_FILES_PATH + entry.getKey(), ownAddress);
                                prevPrevNodeStub.addReplicatedFileList(replicatedFileHandle);
                            } else {
                                // Replicate to previous neighbour, it becomes the new owner of the file
                                prevNodeStub.downloadFile(Constants.REPLICATED_FILES_PATH + entry.getKey(), Constants.REPLICATED_FILES_PATH + entry.getKey(), ownAddress);
                                prevNodeStub.addReplicatedFileList(replicatedFileHandle);
                                prevNodeStub.addOwnerFileList(replicatedFileHandle);
                                prevNodeStub.removeFromAvailableNodes(entry.getKey(), ownHash);
                                prevNodeStub.addToAvailableNodes(entry.getKey(), prevHash);
                            }
                        } catch (RemoteException | NotBoundException e) {
                            e.printStackTrace();
                        }
                    }

                    // Process all local files
                    for (Map.Entry<String, FileHandle> localEntry : (new HashMap<>(localFiles)).entrySet()) {
                        try {
                            NodeInterface ownerNodeStub;

                            if (ownerFiles.containsKey(localEntry.getKey())) {
                                // If this Node is the owner of the file -> check downloads and proceed
                                int downloads = localEntry.getValue().getDownloads();

                                if (downloads == 0) {
                                    // If download count = 0 -> delete local copy and copy of the replicator
                                    prevNodeStub.deleteFileFromNetwork(localEntry.getKey());
                                } else {
                                    // Else update download locations in the FileHandle, make replicator the new owner and re-replicate the file to it's previous neighbour
                                    FileHandle newFileHandle = localEntry.getValue().getAsReplicated();
                                    newFileHandle.removeAvailable(ownHash);
                                    newFileHandle.getAvailableNodes().add(prevNodeStub.getPrevHash());

                                    prevNodeStub.removeReplicatedFile(localEntry.getValue());
                                    prevNodeStub.addOwnerFileList(newFileHandle);

                                    Registry prevPrevNodeRegistry = LocateRegistry.getRegistry(prevNodeStub.getPrevAddress().getHostAddress(), Constants.RMI_PORT);
                                    NodeInterface prevPrevNodeStub = (NodeInterface) prevPrevNodeRegistry.lookup("Node");

                                    prevPrevNodeStub.downloadFile(Constants.REPLICATED_FILES_PATH + localEntry.getKey(), Constants.REPLICATED_FILES_PATH + localEntry.getKey(), ownAddress);
                                    prevPrevNodeStub.addReplicatedFileList(newFileHandle);
                                }
                            } else {
                                // If this Node is not the owner, contact the owner -> check downloads there and proceed
                                InetAddress ownerAddress = namingServerStub.getOwner(localEntry.getKey());
                                Registry ownerNodeRegistry = LocateRegistry.getRegistry(ownerAddress.getHostAddress(), Constants.RMI_PORT);
                                ownerNodeStub = (NodeInterface) ownerNodeRegistry.lookup("Node");
                                int downloads = ownerNodeStub.getOwnerFiles().get(localEntry.getKey()).getDownloads();

                                if (downloads == 0) {
                                    // If download count = 0 -> delete local copy and copy of owner
                                    ownerNodeStub.deleteFileFromNetwork(localEntry.getKey());
                                } else {
                                    // Else update download locations in the FileHandle at the owner and re-replicate the file to it's previous neighbour of the owner
                                    ownerNodeStub.removeFromAvailableNodes(localEntry.getKey(), ownHash);
                                    ownerNodeStub.addToAvailableNodes(localEntry.getKey(), ownerNodeStub.getPrevHash());

                                    Registry ownerPrevNodeRegistry = LocateRegistry.getRegistry(ownerNodeStub.getPrevAddress().getHostAddress(), Constants.RMI_PORT);
                                    NodeInterface ownerPrevNodeStub = (NodeInterface) ownerPrevNodeRegistry.lookup("Node");

                                    ownerPrevNodeStub.downloadFile(Constants.REPLICATED_FILES_PATH + localEntry.getKey(), Constants.REPLICATED_FILES_PATH + localEntry.getKey(), ownerAddress);
                                    ownerPrevNodeStub.addReplicatedFileList(ownerNodeStub.getOwnerFiles().get(localEntry.getKey()).getAsReplicated());
                                }
                            }
                        } catch (RemoteException | UnknownHostException | NotBoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        // Actually leave the network
        try {
            tcpServer.stop();
            setRunning(false);
            leaveNetwork();
            //insert run file agent here?
            UnicastRemoteObject.unexportObject(this, true);
            System.out.println("Left the network+");
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Should be run at Node startup.
     * <p>
     * Export self to local RMI registry.
     */
    public void initializeRMI() {
        try {
            System.setProperty("java.rmi.server.hostname", ownAddress.getHostAddress());
            Registry registry = LocateRegistry.createRegistry(Constants.RMI_PORT);
            NodeInterface nodeStub = (NodeInterface) UnicastRemoteObject.exportObject(this, 0);
            registry.bind("Node", nodeStub);
        } catch (AlreadyBoundException | RemoteException e) {
            e.printStackTrace();
            //TODO: failure?
        }
    }

    /**
     * Clears all files and subfolders in a specified folder.
     *
     * @param folderPath to clear
     */
    public void clearDir(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists())
            folder.mkdir();
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                clearDir(file.getPath());
            }
            file.delete();
        }
        System.out.println("Cleared dir " + folderPath);
    }

    public void setupMulticastServer() {
        multicastServer = new MulticastServer();

        multicastServer.registerListener(HelloPacket.class, ((packet, client) -> {
            if (packet.getSenderHash() != getOwnHash()) {
                updateNeighbours(client.getAddress(), packet.getSenderHash());

                // wait until NamingServer knows new Node
                while (namingServerStub.getIPNode(packet.getSenderHash()) == null) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                // Loop through all ownerfiles and check if they need to be moved around
                Map<String, FileHandle> originalOwnerFiles = new HashMap<>(ownerFiles);
                originalOwnerFiles.forEach(((s, fileHandle) -> {
                    try {
                        replicateToNewNode(fileHandle);
                    } catch (RemoteException | NotBoundException | UnknownHostException e) {
                        e.printStackTrace();
                    }
                }));
            }
        }));

        multicastServer.startServer(multicastGroup, Constants.MULTICAST_PORT);
    }

    public void setupTCPServer() {
        tcpServer = new TCPServer();

        tcpServer.registerListener(NodeCountPacket.class, ((packet, client) -> {
            setNamingServerAddress(client.getAddress());
            if (packet.getNodeCount() < 1) {
                updatePrev(getOwnAddress(), getOwnHash());
                updateNext(getOwnAddress(), getOwnHash());
            }
            client.close();
        }));

        tcpServer.registerListener(UpdateNeighboursPacket.class, (((packet, client) -> {
            if (packet.getPreviousNeighbour() != -1) {
                updatePrev(null, packet.getPreviousNeighbour());
            }

            if (packet.getNextNeighbour() != -1) {
                updateNext(null, packet.getNextNeighbour());
            }
            client.close();
        })));

        tcpServer.registerListener(FileRequestPacket.class, ((packet, client) -> sendFile(packet.getFileName(), client)));

        tcpServer.startServer(ownAddress, Constants.TCP_PORT);
    }

    private void sendFile(String fileName, Client client) {
        try {
            int fileSize = (int) new File(fileName).length();
            FileInputStream fis = new FileInputStream(fileName);
            byte[] buffer = new byte[4096];
            DataOutputStream dos = client.getConnection().getDataOutputStream();

            int read;

            dos.writeInt(fileSize);

            while ((read = fis.read(buffer)) > 0) {
                dos.write(buffer, 0, read);
            }

            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Node startNode(String nodeName, InetAddress address, boolean isGUIStarted) {
        Node node;
        try {
            node = new Node(nodeName, address, isGUIStarted);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.err.println("Unknown multicast address, cannot create Node.");
            return null;
        }

        node.clearDir(Constants.REPLICATED_FILES_PATH);
        node.clearDir(Constants.DOWNLOADED_FILES_PATH);
        node.initializeRMI();
        System.out.println("Hash: " + node.getOwnHash());
        Communications.setSenderHash(node.getOwnHash());
        node.setupMulticastServer();
        node.setupTCPServer();
        try {
            node.joinNetwork();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Unable to join network.");
            return null;
        }


        // Discover local files
        while (node.namingServerAddress == null || node.prevHash == 0 || node.nextHash == 0 || node.prevAddress == null || node.nextAddress == null) {
            if (System.currentTimeMillis() - node.joinTimestamp >= 1000) {
                // try to rejoin at most 5 times
                if (++node.retries > 5) {
                    System.err.println("Failed to join network. Naming server is not responding");
                    node.multicastServer.stop();
                    node.tcpServer.stop();
                    try {
                        UnicastRemoteObject.unexportObject(node, true);
                    } catch (NoSuchObjectException e) {
                        e.printStackTrace();
                    }
                    System.exit(0);
                    return null;
                }
                System.out.println("Retrying to join the network (" + node.retries + ")");
                node.joinTimestamp = System.currentTimeMillis();
                try {
                    node.joinNetwork();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.err.println("Unable to join network.");
                    return null;
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.err.println("Interrupted while waiting for Node to initialize.");
                return null;
            }
        }
        try {
            Registry namingServerRegistry = LocateRegistry.getRegistry(node.namingServerAddress.getHostAddress(), Constants.RMI_PORT);
            node.namingServerStub = (NamingServerInterface) namingServerRegistry.lookup("NamingServer");
        } catch (RemoteException | NotBoundException e) {
            System.err.println("Failed to connect to NamingServer");
            e.printStackTrace();
            return null;
        }
        node.setInitialized(true);
        node.discoverLocalFiles();

        // Start the FileAgent if this is the first Node in the network
        if (node.prevHash == node.ownHash) {
            HashMap<String, Integer> files = new HashMap<>();
            try {
                node.runFileAgent(files);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (NotBoundException e) {
                e.printStackTrace();
            }
        }


        // Start file update watcher
        FileUpdateWatcher fileUpdateWatcherThread = new FileUpdateWatcher(node, Constants.LOCAL_FILES_PATH);
        Thread thread = new Thread(fileUpdateWatcherThread);
        thread.start();

        return node;
    }

    public static void main(String[] args) throws IOException {
        // Get IP and hostname
        Scanner sc = new Scanner(System.in);
        System.out.println("(Detected localHostName is: " + InetAddress.getLocalHost() + ")");
        System.out.print("Enter hostname: ");
        String hostname = sc.nextLine();
        if (hostname.isEmpty()) {
            hostname = InetAddress.getLocalHost().getHostName();
        }
        System.out.println("(Detected localHostAddress is: " + InetAddress.getLocalHost() + ")");
        System.out.print("Enter IP: ");
        String ip = sc.nextLine();
        if (ip.isEmpty()) {
            ip = InetAddress.getLocalHost().getHostAddress();
        }


        // Start the Node
        Node node = Node.startNode(hostname, InetAddress.getByName(ip), false);
        if (node == null) {
            return;
        }

        // Listen for commands
        while (node.isRunning) {
            String cmd = sc.nextLine().toLowerCase();
            switch (cmd) {
                case "debug":
                    Communications.setDebugging(true);
                    System.out.println("Debugging enabled");
                    break;

                case "undebug":
                    Communications.setDebugging(false);
                    System.out.println("Debugging disabled");
                    break;

                case "shutdown":
                case "shut":
                case "sh":
                    node.setRunning(false);
                    node.leaveNetwork();
                    node.tcpServer.stop();
                    node.multicastServer.stop();
                    UnicastRemoteObject.unexportObject(node, true);
                    System.out.println("Left the network");
                    System.exit(0);
                    break;

                case "shutdown+":
                case "shut+":
                case "sh+":
                    node.initializeShutdown();
                    break;

                case "neighbours":
                case "neighbors":
                case "neigh":
                case "nb":
                    System.out.println("Prev: " + node.prevHash + " === Next: " + node.nextHash);
                    break;

                case "localFiles":
                case "lf":
                    System.out.println("Local files: " + node.localFiles);
                    break;

                case "replicatedFiles":
                case "rf":
                    System.out.println("Replicated files: " + node.replicatedFiles);
                    break;

                case "ownerFiles":
                case "of":
                    System.out.println("Owner files: " + node.ownerFiles);
                    break;

                case "allfiles":
                    System.out.println("All files: " + node.allFiles);
                    break;

                case "fafiles":
                    System.out.println("All fileagentfiles: " + node.fileAgentFiles);
                    break;

                case "dl":
                    System.out.print("Enter filename to download: ");
                    node.downloadAFile(sc.nextLine());
                    break;

                case "delnetw":
                    System.out.println("All files: " + node.allFiles);
                    System.out.print("Enter filename to delete from the network: ");
                    node.deleteFileFromNetwork(sc.nextLine());
                    break;
            }
        }
    }
}
