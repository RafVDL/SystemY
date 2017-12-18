package be.ac.ua.dist.systemy.node;

import be.ac.ua.dist.systemy.Constants;
import be.ac.ua.dist.systemy.namingServer.NamingServerInterface;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collection;
import java.util.*;

public class FailureAgent implements Runnable, Serializable {

    private int hashFailed;
    private int hashStart;
    private InetAddress currNode;
    private InetAddress nsAddress;
    private InetAddress ownerAddressForThisFile;
    private int ownerHashForThisFile;
    private Collection<FileHandle> localFiles;
    private Collection<FileHandle> replicatedFiles;
    private String currFile;



    public FailureAgent(int hashFailed, int hashStart, InetAddress currNode) { //integer is hash of node that is downloading file
        this.hashFailed = hashFailed;
        this.hashStart = hashStart;
        this.currNode = currNode;
    }

    public void run() {
        //initialize rmi connection
        try {
            Registry currNodeRegistry = LocateRegistry.getRegistry(currNode.getHostAddress(), Constants.RMI_PORT);
            NodeInterface currNodeStub = (NodeInterface) currNodeRegistry.lookup("Node");
            localFiles = currNodeStub.getLocalFiles().values();
            replicatedFiles = currNodeStub.getReplicatedFiles().values();

            nsAddress= currNodeStub.getNamingServerAddress();
            Registry namingServerRegistry = LocateRegistry.getRegistry(nsAddress.getHostAddress(), Constants.RMI_PORT);
            NamingServerInterface namingServerStub = (NamingServerInterface) namingServerRegistry.lookup("NamingServer");

            //Bestanden die op de gefaalde node gerepliceerd zijn, moeten verplaatst worden naar zijn vorige node en de eigenaar moet verwittigd zodat deze downloadlocaties kan updaten
            //Bestanden die op de gefaalde node lokaal zijn, de node die deze heeft gerepliceerd wordt de nieuwe eigenaar

            //Stap 1: We bekijken of een of meerdere lokale bestanden van deze node gerepliceerd zijn op de gefaalde node.
            for (FileHandle fileHandle : localFiles) {
                currFile = fileHandle.getFile().getName();
                //Kijken of file naar failed node verwijst
                ownerAddressForThisFile = namingServerStub.getOwner(currFile);
                ownerHashForThisFile = namingServerStub.getHashOfAddress(ownerAddressForThisFile);
                //Bestand gerepliceerd op failed node dus naar nieuwe eigenaar sturen
                if (ownerHashForThisFile == hashFailed) {
                    //Bestand sturen naar nieuwe node ??? currNodeStub.replicateToNewNode() ???
                }
            }


            //Stap 2: We bekijken of een of meerdere gerepliceerde bestanden van deze node lokaal zijn op de gefaalde node.
            for (FileHandle fileHandle : replicatedFiles) {
                currFile = fileHandle.getFile().getName();
                //Kijken of file naar failed node verwijst
                //Local eigenaar krijgen van NS
                //Indien Local = failed node --> deze node eigenaar maken van bestand en op nieuwe node repliceren + ns informeren
                /*
                ownerAddressForThisFile = namingServerStub.getOwner(currFile);
                ownerHashForThisFile = namingServerStub.getHashOfAddress(ownerAddressForThisFile);
                //Bestand gerepliceerd op failed node dus naar nieuwe eigenaar sturen
                if (ownerHashForThisFile == hashFailed) {


                }
                */
            }


        } catch (IOException | NotBoundException e) {
            e.printStackTrace();

        }
    }
}
