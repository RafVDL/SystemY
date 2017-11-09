package be.ac.ua.dist.systemy.namingServer;

import be.ac.ua.dist.systemy.Ports;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;

public class NamingServerHelloThread extends Thread {
    private NamingServer namingServer;

    public NamingServerHelloThread(NamingServer namingServer) {
        this.namingServer = namingServer;
    }

    @Override
    public void run() {
        MulticastSocket multicastSocket;
        try {
            multicastSocket = new MulticastSocket(Ports.MULTICAST_PORT);
            DatagramSocket uniSocket = new DatagramSocket(Ports.UNICAST_PORT, namingServer.serverIP);
            InetAddress group = InetAddress.getByName("225.0.113.0");
            multicastSocket.joinGroup(group);

            DatagramPacket packet;
            while (namingServer.isRunning()) {
                byte[] buf = new byte[1024];
                packet = new DatagramPacket(buf, buf.length);
                multicastSocket.receive(packet);

                String received = new String(packet.getData()).trim();
                if (received.startsWith("HELLO")) {
                    String[] split = received.split("\\|");
                    String hostname = split[1];

                    Socket tcpSocket;
                    DataOutputStream dos;
                    PrintWriter out;
                    try {
                        tcpSocket = new Socket();
                        tcpSocket.setSoLinger(true, 5);
                        tcpSocket.connect(new InetSocketAddress(packet.getAddress(), Ports.TCP_PORT), 1000);
                        dos = new DataOutputStream(tcpSocket.getOutputStream());
                        out = new PrintWriter(dos, true);

                        out.println("NODECOUNT");
                        dos.writeInt(namingServer.ipAdresses.size());
                        dos.flush();

                        //Close everything.
                        out.close();
                        tcpSocket.close();
                    } catch (SocketTimeoutException e) {
                        // handle node disconnected
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    namingServer.addNodeToNetwork(hostname, packet.getAddress());

                } else if (received.startsWith("GETIP")) {
                    String[] split = received.split("\\|");
                    String hostname = split[1];
                    if (namingServer.ipAdresses.containsKey(Math.abs(hostname.hashCode() % 32768))) {
                        buf = ("REIP|" + hostname + "|" + namingServer.ipAdresses.get(Math.abs(hostname.hashCode() % 32768)).getHostAddress()).getBytes();
                    } else {
                        buf = ("REIP|" + hostname + "|NOT_FOUND").getBytes();
                    }

                    packet = new DatagramPacket(buf, buf.length, packet.getAddress(), Ports.UNICAST_PORT);
                    uniSocket.send(packet);
                } else if (received.startsWith("QUITNAMING")) {
                    String[] split = received.split("\\|");
                    String hostname = split[1];
                    namingServer.removeNodeFromNetwork(hostname);
                }
            }

            multicastSocket.leaveGroup(group);
            multicastSocket.close();
            uniSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}