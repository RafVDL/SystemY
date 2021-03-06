package be.ac.ua.dist.systemy.namingServer;

import be.ac.ua.dist.systemy.Constants;
import be.ac.ua.dist.systemy.networking.Client;
import be.ac.ua.dist.systemy.networking.Communications;
import be.ac.ua.dist.systemy.networking.Server;
import be.ac.ua.dist.systemy.networking.packet.*;
import be.ac.ua.dist.systemy.networking.udp.MulticastServer;
import be.ac.ua.dist.systemy.node.NodeInterface;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Scanner;
import java.util.TreeMap;

public class NamingServer implements NamingServerInterface {
    private final InetAddress serverIP;
    private TreeMap<Integer, InetAddress> ipAddresses = new TreeMap<>();

    private static int latestAgentcount = -1;
    private static int currentAgentcount;
    private static InetAddress fileAgentAddress;
    private static int fileAgentHash;
    private static int nextFileAgentHash;

    private boolean running = true;
    private Server multicastServer;

    public NamingServer(InetAddress serverIP) {
        this.serverIP = serverIP;
    }

    public int calculateHash(String nodeName) {
        return Math.abs(nodeName.hashCode() % 32768);
    }

    public void addNodeToNetwork(int hash, InetAddress ip) {
        System.out.println("Adding " + hash + " to IP-table");

        if (!ipAddresses.containsKey(hash)) {
            ipAddresses.put(hash, ip);
        } else {
            System.out.println(hash + " already exists in ipAddresses");
        }
    }

    public void removeNodeFromNetwork(int hash) {
        System.out.println("Removing " + hash + " from IP-table");
        if (ipAddresses.containsKey(hash)) {
            ipAddresses.remove(hash);
        } else {
            System.out.println(hash + " does not exist in ipAddresses");
        }
    }

    public InetAddress getOwner(String fileName) {
        int hashFileName = calculateHash(fileName);
        Integer currentHash = ipAddresses.floorKey(hashFileName);

        if (currentHash == null)
            currentHash = ipAddresses.lastKey();

        InetAddress currentIP = ipAddresses.get(currentHash);

        System.out.println("Owner of '" + fileName + "' (hash=" + hashFileName + ") is " + currentHash);
        return currentIP;
    }

    public void printIPadresses() {
        System.out.println("\nPrinting IP-addresses to Console:");
        ipAddresses.forEach((key, value) -> {
            System.out.println("Hash: " + key + " - IP: " + value);
        });
        System.out.println("");
    }

    public void exportIPadresses() {
        System.out.println("Exporting IP-addressess ...");
        String writeThis;
        Iterator<HashMap.Entry<Integer, InetAddress>> it = ipAddresses.entrySet().iterator();
        BufferedWriter outputWriter = null;
        try {
            File outputFile = new File("test.txt");
            outputWriter = new BufferedWriter(new FileWriter(outputFile));
            while (it.hasNext()) {
                HashMap.Entry pair = it.next();
                writeThis = "Hash: " + pair.getKey() + "  IP: " + pair.getValue();
                outputWriter.write(writeThis);
                outputWriter.newLine();
            }
            outputWriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                outputWriter.close();
            } catch (Exception e) {
                //
            }
        }
        System.out.println("Export completed \n");
    }

    public InetAddress getIPNode(int hashNode) {
        return ipAddresses.getOrDefault(hashNode, null);
    }

    public int[] getNeighbours(int hashNode) {
        int[] neighbours = new int[2];

        // no point in trying if there are no known addresses or the given hash is not known to the naming server
        if (ipAddresses.size() == 0 || !ipAddresses.containsKey(hashNode))
            return neighbours;

        neighbours[0] = ipAddresses.lowerKey(hashNode) != null ? ipAddresses.lowerKey(hashNode) : ipAddresses.lastKey(); // previous hash
        neighbours[1] = ipAddresses.higherKey(hashNode) != null ? ipAddresses.higherKey(hashNode) : ipAddresses.firstKey(); // next hash

        return neighbours;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public void initializeRMI() {
        try {
            System.setProperty("java.rmi.server.hostname", serverIP.getHostAddress());
            Registry registry = LocateRegistry.createRegistry(Constants.RMI_PORT);
            NamingServerInterface stub = (NamingServerInterface) UnicastRemoteObject.exportObject(this, 0);
            registry.bind("NamingServer", stub);
        } catch (AlreadyBoundException | RemoteException e) {
            System.err.println("NamingServer exception: " + e.toString());
            e.printStackTrace();
        }
    }

    public void setupMulticastServer() throws UnknownHostException {
        multicastServer = new MulticastServer();

        multicastServer.registerListener(HelloPacket.class, ((packet, client) -> {
            try {
                client.close();

                Client tcpClient = Communications.getTCPClient(client.getAddress(), Constants.TCP_PORT);
                NodeCountPacket nodeCountPacket = new NodeCountPacket(ipAddresses.size());
                tcpClient.sendPacket(nodeCountPacket);

                addNodeToNetwork(packet.getSenderHash(), client.getAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        multicastServer.registerListener(GetIPPacket.class, ((packet, client) -> {
            try {
                InetAddress address = packet.getHash() == 0 ? serverIP : ipAddresses.get(packet.getHash());
                IPResponsePacket ipResponsePacket = new IPResponsePacket(address);
                client.sendPacket(ipResponsePacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        multicastServer.registerListener(QuitPacket.class, ((packet, client) -> removeNodeFromNetwork(packet.getSenderHash())));

        multicastServer.startServer(InetAddress.getByName(Constants.MULTICAST_ADDRESS), Constants.MULTICAST_PORT);
    }

    public int getHashOfAddress(InetAddress thisAddress) {
        int returnHash = 0;
        int found = 0;
        Iterator<HashMap.Entry<Integer, InetAddress>> it = ipAddresses.entrySet().iterator();
        while (it.hasNext() && found == 0) {
            HashMap.Entry<Integer, InetAddress> pair = it.next();
            int key = pair.getKey();
            InetAddress value = pair.getValue();
            if (value.equals(thisAddress)) {
                returnHash = key;
                found = 1;
            }
        }
        return returnHash;
    }

    public void latestFileAgent(InetAddress thisAddress, int thisHash, int nextHash, int number) {
        fileAgentHash = thisHash;
        fileAgentAddress = thisAddress;
        nextFileAgentHash = nextHash;
        currentAgentcount = number;
    }


    public static void checkRunningFileAgent() throws InterruptedException, RemoteException, NotBoundException {
        //TimeUnit.SECONDS.sleep(1);
        //fileAgent not updated thus failed
        if (latestAgentcount == currentAgentcount) {
            Registry currNodeRegistry = LocateRegistry.getRegistry(fileAgentAddress.getHostAddress(), Constants.RMI_PORT);
            NodeInterface currNodeStub = (NodeInterface) currNodeRegistry.lookup("Node");
            currNodeStub.runFailureAgent(nextFileAgentHash, fileAgentHash, fileAgentAddress);
            System.out.println("Unresponding FileAgent! Starting FailureAgent");
        } else {
            latestAgentcount = currentAgentcount;
        }

    }

    public static void main(String[] args) throws UnknownHostException, RemoteException, InterruptedException, NotBoundException {
        Scanner sc = new Scanner(System.in);
        InetAddress detectedHostAddress = InetAddress.getLocalHost();
        System.out.println("(Detected localHost is: " + detectedHostAddress + ")");
        System.out.print("Enter IP: ");
        String ip = sc.nextLine();
        if (ip.isEmpty()) {
            ip = detectedHostAddress.getHostAddress();
        }

        NamingServer namingServer = new NamingServer(InetAddress.getByName(ip));
        namingServer.initializeRMI();

        Communications.setSenderHash(0); // NamingServer exclusive!

        namingServer.setupMulticastServer();

        System.out.println("Namingserver started @" + ip);

        while (namingServer.running) {
            String cmd = sc.nextLine().toLowerCase();
            checkRunningFileAgent();
            switch (cmd) {
                case "debug":
                    Communications.setDebugging(true);
                    System.out.println("Debugging enabled");
                    break;

                case "undebug":
                    Communications.setDebugging(false);
                    System.out.println("Debugging disabled");
                    break;

                case "clear":
                    namingServer.ipAddresses.clear();
                    latestAgentcount = -1;
                    currentAgentcount = 0;
                    System.out.println("----------Cleared network table----------\n");
                    break;

                case "shutdown":
                case "shut":
                case "sh":
                    namingServer.setRunning(false);
                    namingServer.multicastServer.stop();
                    UnicastRemoteObject.unexportObject(namingServer, true);
                    System.out.println("Left the network+");
                    System.exit(0);
                    break;

                case "table":
                case "tab":
                case "tb":
                    namingServer.printIPadresses();
                    break;

                case "nb":
                    System.out.println("FileAgentNumber: " + latestAgentcount);
                    break;
            }
        }
    }
}