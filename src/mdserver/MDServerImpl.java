// Implementation Class: implements the interface and contains the actual logic.
package mdserver;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

import bankserver.BankServerInterface;
import common.Message;

public class MDServerImpl extends UnicastRemoteObject implements MDServerInterface {
    private static final long serialVersionUID = 1L;

    private final List<BankServerInterface> replicas = new ArrayList<>();

    public MDServerImpl() throws RemoteException {
        super();
    }

    @Override
    public synchronized void registerReplica(BankServerInterface replica) throws RemoteException {
        replicas.add(replica);
        System.out.println("Replica registered: " + replica.getServerName());
        // TODO: notify group membership
    }

    @Override
    public synchronized void broadcastMessage(Message msg) throws RemoteException {
        for (BankServerInterface replica : replicas) {
            try {
                replica.receiveMessage(msg);
                // TODO: wait for ACKs with timeout & retry
            } catch (Exception e) {
                System.err.println("Replica failed: " + e.getMessage());
            }
        }
    }
}
