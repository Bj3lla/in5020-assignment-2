// Interface: defines the methods that remote clients (here, bank servers) can call.
package mdserver;

import java.rmi.Remote;
import java.rmi.RemoteException;
import common.Message;
import bankserver.BankServerInterface;

public interface MDServerInterface extends Remote {
    void registerReplica(BankServerInterface replica) throws RemoteException;
    void broadcastMessage(Message msg) throws RemoteException;
}
