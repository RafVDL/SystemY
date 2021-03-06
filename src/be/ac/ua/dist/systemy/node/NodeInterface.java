package be.ac.ua.dist.systemy.node;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public interface NodeInterface extends Remote {

    int getOwnHash() throws RemoteException;

    int getPrevHash() throws RemoteException;

    int getNextHash() throws RemoteException;

    InetAddress getPrevAddress() throws RemoteException;

    InetAddress getNextAddress() throws RemoteException;

    Map<String, FileHandle> getLocalFiles() throws RemoteException;

    Map<String, FileHandle> getReplicatedFiles() throws RemoteException;

    Map<String, FileHandle> getOwnerFiles() throws RemoteException;

    void addLocalFileList(FileHandle fileHandle) throws RemoteException;

    void addReplicatedFileList(FileHandle fileHandle) throws RemoteException;

    void addOwnerFileList(FileHandle fileHandle) throws RemoteException;

    void removeLocalFile(FileHandle fileHandle) throws RemoteException;

    void removeReplicatedFile(FileHandle fileHandle) throws RemoteException;

    void removeOwnerFile(FileHandle fileHandle) throws RemoteException;

    void downloadFile(String sourceFileName, String targetFileName, InetAddress remoteAddress) throws RemoteException;

    void deleteFileFromNode(FileHandle fileHandle) throws RemoteException;

    void deleteFileFromNetwork(String filename) throws RemoteException;

    void addToAvailableNodes(String fileName, int hashToAdd) throws RemoteException;

    void removeFromAvailableNodes(String fileName, int hashToRemove) throws RemoteException;

    void increaseDownloads(String fileName) throws RemoteException;

    void updateNext(InetAddress newAddress, int newHash) throws RemoteException;

    void updatePrev(InetAddress newAddress, int newHash) throws RemoteException;

    void runFileAgent(HashMap<String, Integer> files) throws RemoteException, InterruptedException, NotBoundException;

    void runFailureAgent(int hashFailed, int hashStart, InetAddress currNode) throws RemoteException, InterruptedException, NotBoundException;

    void addAllFileList(String file, int value) throws RemoteException;

    String getFileLockRequest() throws RemoteException;

    void setDownloadFileGranted(String download) throws RemoteException;

    String getDownloadFileGranted() throws RemoteException;

    Set getDownloadingFiles() throws RemoteException;

    void setFileAgentFiles(HashMap<String, Integer> files) throws RemoteException;

    int calculateHash(String name) throws RemoteException;

    InetAddress getNamingServerAddress() throws RemoteException;

    void replicateFailed(FileHandle fileHandle, InetAddress receiveAddress) throws RemoteException, NotBoundException, UnknownHostException;

    void replicateWhenJoining(FileHandle fileHandle) throws RemoteException, NotBoundException, UnknownHostException;

    Map<String, Integer> getAllFiles() throws RemoteException;

    void removeAllFileList(String file) throws RemoteException;

    InetAddress getLocalAddressOfFile(String filename) throws RemoteException;


}