package mdserver;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

import bankserver.BankServerInterface;
import common.GroupInfo;
import common.Message;
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

        String txId = UUID.randomUUID().toString(); // unique ID for ACK tracking
        Set<String> waitingReplicas = new HashSet<>(replicas.keySet());
        pendingAcks.put(txId, waitingReplicas);

        for (String replicaName : new HashSet<>(waitingReplicas)) {
            sendWithRetry(replicaName, msg, txId, 0);
        }

        // Wait until all ACKs received
        TimerUtils.schedule(() -> checkPendingAcks(txId, msg), 100);
    }

    private void sendWithRetry(String replicaName, Message msg, String txId, int attempt) {
        BankServerInterface replica = replicas.get(replicaName);
        if (replica == null) return;

        TimerUtils.schedule(() -> {
            try {
                replica.receiveMessage(msg); // message sent
                // Replica must call ack(txId) to confirm
            } catch (Exception e) {
                if (attempt < 3) { // retry up to 3 times (2s intervals)
                    System.out.println("Retrying " + replicaName + " for tx " + txId);
                    sendWithRetry(replicaName, msg, txId, attempt + 1);
                } else {
                    System.err.println("Replica " + replicaName + " failed, removing from group.");
                    replicas.remove(replicaName);
                    pendingAcks.getOrDefault(txId, new HashSet<>()).remove(replicaName);
                    updateMembership();
                }
            }
        }, attempt == 0 ? 0 : 2000L); // first attempt immediate, retries every 2s
    }

    private void checkPendingAcks(String txId, Message msg) {
        Set<String> waiting = pendingAcks.getOrDefault(txId, new HashSet<>());
        if (!waiting.isEmpty()) {
            // Schedule next check in 100ms
            TimerUtils.schedule(() -> checkPendingAcks(txId, msg), 100);
        } else {
            // All ACKs received, process next message
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
            } catch (Exception e) {
                System.err.println("Failed to update membership for " + replica.getServerName());
            }
        }
    }

    // Called by replicas to acknowledge receipt
    public synchronized void ack(String txId, String replicaName) {
        Set<String> waiting = pendingAcks.get(txId);
        if (waiting != null) {
            waiting.remove(replicaName);
        }
    }
}
