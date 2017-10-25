package be.ac.ua.dist.systemy.nameserver.NameServerPackage;

import java.net.UnknownHostException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.net.InetAddress;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Iterator;

public class NamingServer implements Nameserver{

    public NamingServer(){
        super();
    }

    HashMap<Integer, InetAddress> IpAdresses = new HashMap<Integer, InetAddress>();

    public void addMeToNetwork(String computerName, InetAddress IP){
        int hashComputername = Math.abs(computerName.hashCode() % 32768);
        IpAdresses.put(hashComputername, IP);
    }

    public void removeMeFromNetwork(String computerName){
        IpAdresses.remove(Math.abs(computerName.hashCode() % 32768));
    }

    public InetAddress getOwner(String fileName) throws UnknownHostException {
        int hashFileName = Math.abs(fileName.hashCode() % 32768);
        InetAddress currentIP;
        currentIP = InetAddress.getByAddress(new byte[] {0, 0, 0, 0});
        int currentHash = 0;
        InetAddress highestIP;
        highestIP = InetAddress.getByAddress(new byte[] {0, 0, 0, 0});
        int highestHash = 0;


        Iterator<HashMap.Entry<Integer, InetAddress>> it = IpAdresses.entrySet().iterator();

        while(it.hasNext()){
            HashMap.Entry<Integer, InetAddress> pair = it.next();
            if(pair.getKey() < hashFileName){
                if(currentHash == 0){
                    currentHash = pair.getKey();
                    currentIP = pair.getValue();
                } else if (pair.getKey() > currentHash) {
                    currentHash = pair.getKey();
                    currentIP = pair.getValue();
                }
            } else if (pair.getKey() > highestHash) {
                highestHash = pair.getKey();
                highestIP = pair.getValue();
            }

        }

        if(currentIP.equals(InetAddress.getByAddress(new byte[] {0, 0, 0, 0}))){
            return highestIP;
        } else {
            return currentIP;
        }

    }

    void printIPadresses(){
        Iterator<HashMap.Entry<Integer, InetAddress>> it = IpAdresses.entrySet().iterator();
        while(it.hasNext()){
            HashMap.Entry pair = (HashMap.Entry)it.next();
            System.out.println("Hash: " + pair.getKey());
            System.out.println("IP: " + pair.getValue() + "\n");
        }

    }

    void exportIPadresses(){
        String writeThis;
        Iterator<HashMap.Entry<Integer, InetAddress>> it = IpAdresses.entrySet().iterator();
        int i=0;
        BufferedWriter outputWriter = null;
        try {
            File outputFile = new File("test.txt");
            outputWriter = new BufferedWriter(new FileWriter(outputFile));
            while(it.hasNext()) {
                HashMap.Entry pair = (HashMap.Entry)it.next();
                writeThis = "Hash: " + pair.getValue() + "  IP: " + pair.getKey();
                outputWriter.write(writeThis);
                outputWriter.newLine();
            }
            outputWriter.flush();
        } catch (Exception e) {
            e.printStackTrace();;
        } finally{
            try {
                outputWriter.close();
            } catch(Exception e) {

            }
        }
    }






    public static void main(String[] args) {

        try {
            NamingServer obj = new NamingServer();
            Nameserver stub = (Nameserver) UnicastRemoteObject.exportObject(obj, 0);

            Registry registry = LocateRegistry.getRegistry();
            registry.bind("be.ac.ua.dist.systemy.nameserver.NameServerPackage.NamingServer", stub);

            System.err.println("be.ac.ua.dist.systemy.nameserver.NameServerPackage.NamingServer Ready");

        }catch (Exception e) {
            System.err.println("be.ac.ua.dist.systemy.nameserver.NameServerPackage.NamingServer exception: " + e.toString());
            e.printStackTrace();
        }
    }


}