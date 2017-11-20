package be.ac.ua.dist.systemy.networking.packet;

import be.ac.ua.dist.systemy.networking.Client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class NodeCountPacket extends Packet {

    private int nodeCount;

    public NodeCountPacket(Client client) {
        super(0x01, client);
    }

    @Override
    public void receive(DataInputStream dis) throws IOException {
        nodeCount = dis.readInt();
    }

    @Override
    public void send(DataOutputStream dos) throws IOException {
        dos.writeInt(nodeCount);
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

}
