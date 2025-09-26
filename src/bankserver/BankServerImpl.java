// Implementation Class: implements the interface and contains the actual logic.
package bankserver;

import common.GroupInfo;
import common.Message;
import common.Transaction;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class BankServerImpl extends UnicastRemoteObject implements BankServerInterface {
    private static final long serialVersionUID = 1L;

    private final String serverName;
    private double balance = 0.0;

    private List<Transaction> executedList = new ArrayList<>();
    private List<Transaction> outstandingCollection = new ArrayList<>();
    private int orderCounter = 0;
    private int outstandingCounter = 0;

    public BankServerImpl(String serverName) throws RemoteException {
        super();
        this.serverName = serverName;
    }

    @Override
    public void receiveMessage(Message msg) throws RemoteException {
        // TODO: Apply transactions in order
        System.out.println(serverName + " received: " + msg.getTransactions());
    }

    @Override
    public void updateMembership(GroupInfo groupInfo) throws RemoteException {
        // TODO: handle membership changes
        System.out.println(serverName + " updated membership: " + groupInfo.getMembers());
    }

    @Override
    public String getServerName() throws RemoteException {
        return serverName;
    }

    @Override
    public void ack(String messageId) throws RemoteException {
        System.out.println(serverName + " ACK received for " + messageId);
    }
}
