package mdserver;

import bankserver.BankServerInterface;
import common.Message;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface MDServerInterface extends Remote {
    void registerReplica(BankServerInterface replica) throws RemoteException;
    List<String> getGroupMembers(String groupName) throws RemoteException;
    void broadcastMessage(Message msg) throws RemoteException;
    
    // Explicit ACK from BankServer
    void ack(String txId, String replicaName) throws RemoteException;
    void updateMembership() throws RemoteException;
}
