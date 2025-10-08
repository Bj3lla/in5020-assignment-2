package mdserver;

import bankserver.BankServerInterface;
import common.GroupInfo;
import common.Message;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import mdserver.utils.TimerUtils;

public class MDServerImpl extends UnicastRemoteObject implements MDServerInterface {
    private static final long serialVersionUID = 1L;

    private final Map<String, BankServerInterface> replicas = new ConcurrentHashMap<>();
    private final Queue<Message> messageQueue = new LinkedList<>();
    private boolean broadcasting = false;

    // Track pending ACKs: txId -> set of replica names
    private final Map<String, Set<String>> pendingAcks = new ConcurrentHashMap<>();

    public MDServerImpl() throws RemoteException {
        super();
    }

    @Override
    public synchronized void registerReplica(BankServerInterface replica) throws RemoteException {
        String name = replica.getServerName();
        replicas.put(name, replica);
        System.out.println("Replica registered: " + name);
        updateMembership();
    }

    @Override
    public synchronized void broadcastMessage(Message msg) throws RemoteException {
        messageQueue.add(msg);
        if (!broadcasting) {
            broadcasting = true;
            processNextMessage();
        }
    }

    private void processNextMessage() {
        Message msg;
        synchronized (messageQueue) {
            msg = messageQueue.poll();
            if (msg == null) {
                broadcasting = false;
                return;
            }
        }

        String txId = msg.getTransactions().get(0).getUniqueId(); //use the transaction ID
        Set<String> waitingReplicas = new HashSet<>(replicas.keySet());
        pendingAcks.put(txId, waitingReplicas);

        for (String replicaName : new HashSet<>(waitingReplicas)) {
            sendWithRetry(replicaName, msg, txId, 0);
        }

        // Periodically check until all ACKs are in
        TimerUtils.schedule(() -> checkPendingAcks(txId, msg), 100);
    }

    private void sendWithRetry(String replicaName, Message msg, String txId, int attempt) {
        BankServerInterface replica = replicas.get(replicaName);
        if (replica == null) return;

        TimerUtils.schedule(() -> {
            try {
                replica.receiveMessage(msg); // send message to replica
                // Replica will call ack(txId, replicaName)
            } catch (RemoteException e) {
                if (attempt < 3) { // retry up to 3 times (2s intervals)
                    System.out.println("Retrying " + replicaName + " for tx " + txId);
                    sendWithRetry(replicaName, msg, txId, attempt + 1);
                } else {
                    System.err.println("Replica " + replicaName + " failed, removing from group.");
                    replicas.remove(replicaName);
                    pendingAcks.getOrDefault(txId, new HashSet<>()).remove(replicaName);
                    try {
                        updateMembership();
                    } catch (RemoteException ex) {
                        // ignore
                    }
                }
            }
        }, attempt == 0 ? 0 : 2000L); // first attempt immediate, retries every 2s
    }

    private void checkPendingAcks(String txId, Message msg) {
        Set<String> waiting = pendingAcks.getOrDefault(txId, new HashSet<>());
        if (!waiting.isEmpty()) {
            // Still waiting → recheck in 100ms
            TimerUtils.schedule(() -> checkPendingAcks(txId, msg), 100);
        } else {
            // All ACKs received
            pendingAcks.remove(txId);
            broadcasting = false;
            processNextMessage();
        }
    }

    @Override
    public synchronized void updateMembership() throws RemoteException {
        GroupInfo info = new GroupInfo(new ArrayList<>(replicas.keySet()));
        for (BankServerInterface replica : replicas.values()) {
            try {
                replica.updateMembership(info);
            } catch (RemoteException e) {
                System.err.println("Failed to update membership for " + replica.getServerName());
            }
        }
    }

    // Called by replicas when they’ve applied the message
    @Override
    public synchronized void ack(String txId, String replicaName) {
        Set<String> waiting = pendingAcks.get(txId);
        if (waiting != null) {
            waiting.remove(replicaName);
            if (waiting.isEmpty()) {
                System.out.println("All ACKs received for tx " + txId);
            }
        }
    }
}
