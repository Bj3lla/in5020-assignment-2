package mdserver;

import bankserver.BankServerInterface;
import common.Message;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface MDServerInterface extends Remote {
    void registerReplica(BankServerInterface replica) throws RemoteException;
    void broadcastMessage(Message msg) throws RemoteException;

    // Explicit ACK from BankServer
    void ack(String txId, String replicaName) throws RemoteException;
}
