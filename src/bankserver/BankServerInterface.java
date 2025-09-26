// Interface: defines the methods that remote clients can call.
package bankserver; 

import common.GroupInfo;
import common.Message;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BankServerInterface extends Remote {
    void receiveMessage(Message msg) throws RemoteException;
    void updateMembership(GroupInfo groupInfo) throws RemoteException;
    String getServerName() throws RemoteException;
    void ack(String messageId) throws RemoteException;
}
