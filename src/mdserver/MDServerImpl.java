package mdserver;

import bankserver.BankServerInterface;
import common.GroupInfo;
import common.Message;
import common.Transaction;
import mdserver.utils.TimerUtils;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MDServerImpl manages multiple groups of bank server replicas.
 * It ensures that messages are broadcast only to the members of the correct group
 * and maintains sequential consistency within each group.
 */
public class MDServerImpl extends UnicastRemoteObject implements MDServerInterface {
    private static final long serialVersionUID = 1L;

    /**
     * Main data structure to hold all replica groups.
     * Key: groupName (e.g., "group01")
     * Value: A map of unique replica instance names to their remote stubs.
     */
    private final Map<String, Map<String, BankServerInterface>> groups = new ConcurrentHashMap<>();

    /**
     * A message queue for each group to ensure serialized broadcasting.
     * Key: groupName
     * Value: A queue of messages waiting to be broadcast to that group.
     */
    private final Map<String, Queue<Message>> messageQueues = new ConcurrentHashMap<>();

    /**
     * Tracks if a broadcast is currently in progress for a specific group.
     * Key: groupName
     * Value: boolean flag
     */
    private final Map<String, Boolean> isBroadcasting = new ConcurrentHashMap<>();

    /**
     * Tracks pending ACKs for a given transaction.
     * Key: transaction uniqueId
     * Value: A set of replica names that still need to send an ACK.
     */
    private final Map<String, Set<String>> pendingAcks = new ConcurrentHashMap<>();

    public MDServerImpl() throws RemoteException {
        super();
    }

    @Override
    public synchronized void registerReplica(BankServerInterface replica) throws RemoteException {
        String uniqueName = replica.getinstanceName();
        // Assumption: The replica's unique name is in the format "groupName_someIdentifier"
        // This is a robust way to associate a replica with its group.
        String groupName = uniqueName.split("_")[0];

        groups.computeIfAbsent(groupName, _ -> new ConcurrentHashMap<>()).put(uniqueName, replica);
        System.out.println("Replica registered: " + uniqueName + " to group " + groupName);

        // Notify all members of that group about the new membership list.
        updateMembershipForGroup(groupName);
    }

    @Override
    public List<String> getGroupMembers(String groupName) throws RemoteException {
        Map<String, BankServerInterface> members = groups.get(groupName);
        if (members == null) {
            return Collections.emptyList(); // Group does not exist yet.
        }
        return new ArrayList<>(members.keySet());
    }

    @Override
    public void broadcastMessage(Message msg) throws RemoteException {
        String senderId = msg.getSenderId();
        String groupName = findGroupForReplica(senderId);

        if (groupName == null) {
            System.err.println("Could not find group for sender: " + senderId + ". Message dropped.");
            return;
        }

        // Add the message to the correct group's queue.
        Queue<Message> queue = messageQueues.computeIfAbsent(groupName, _ -> new LinkedList<>());
        synchronized (queue) {
            queue.add(msg);
        }

        // Start broadcasting if not already in progress for this group.
        processNextMessage(groupName);
    }

    private void processNextMessage(String groupName) {
        // Ensure only one broadcast happens at a time per group.
        synchronized (isBroadcasting) {
            if (isBroadcasting.getOrDefault(groupName, false)) {
                return; // Another broadcast is already in progress for this group.
            }
            isBroadcasting.put(groupName, true);
        }

        Queue<Message> queue = messageQueues.get(groupName);
        Message msg;
        if (queue != null) {
            synchronized (queue) {
                msg = queue.poll();
            }
        } else {
            msg = null;
        }

        if (msg == null) {
            isBroadcasting.put(groupName, false); // No more messages, stop broadcasting.
            return;
        }

        // Assuming one transaction per message as per BankServerImpl's current logic.
        Transaction tx = msg.getTransactions().get(0);
        String txId = tx.getUniqueId();

        Map<String, BankServerInterface> members = groups.get(groupName);
        if (members == null || members.isEmpty()) {
            System.err.println("Group " + groupName + " has no members. Message dropped.");
            isBroadcasting.put(groupName, false);
            processNextMessage(groupName); // Try the next message
            return;
        }

        Set<String> waitingReplicas = ConcurrentHashMap.newKeySet();
        waitingReplicas.addAll(members.keySet());
        pendingAcks.put(txId, waitingReplicas);

        System.out.println("Broadcasting tx " + txId + " to group " + groupName);
        List<String> targets = new ArrayList<>(waitingReplicas);
        for (String replicaName : targets) {
            sendWithRetry(replicaName, msg, txId, 0);
        }

        // Schedule a check to see if all ACKs have arrived.
        TimerUtils.schedule(() -> checkAcksAndContinue(groupName, txId), 100);
    }

    private void sendWithRetry(String replicaName, Message msg, String txId, int attempt) {
        String groupName = findGroupForReplica(replicaName);
        if (groupName == null) return;

        BankServerInterface replica = groups.get(groupName).get(replicaName);
        if (replica == null) return; // Replica was removed.

        try {
            replica.receiveMessage(msg);
        } catch (RemoteException e) {
            // If the call fails, retry after 2 seconds.
            System.err.println("Failed to send tx " + txId + " to " + replicaName + " (Attempt " + (attempt + 1) + ")");
            if (attempt < 2) { // Total attempts: 1 initial + 2 retries = 3
                TimerUtils.schedule(() -> sendWithRetry(replicaName, msg, txId, attempt + 1), 2000L);
            } else {
                // After ~5 seconds of failures, remove the replica.
                System.err.println("Replica " + replicaName + " failed. Removing from group " + groupName + ".");
                removeReplica(groupName, replicaName);
                // Mark as acknowledged to not block the broadcast.
                ack(txId, replicaName);
            }
        }
    }
    
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

    private void checkAcksAndContinue(String groupName, String txId) {
        Set<String> waiting = pendingAcks.get(txId);
        if (waiting != null && !waiting.isEmpty()) {
            // Still waiting for ACKs, check again shortly.
            TimerUtils.schedule(() -> checkAcksAndContinue(groupName, txId), 100);
        } else {
            // All ACKs received, proceed to the next message for this group.
            pendingAcks.remove(txId);
            isBroadcasting.put(groupName, false);
            processNextMessage(groupName);
        }
    }

    private void removeReplica(String groupName, String replicaName) {
        Map<String, BankServerInterface> members = groups.get(groupName);
        if (members != null) {
            members.remove(replicaName);
            // Notify remaining members of the change.
            updateMembershipForGroup(groupName);
        }
    }

    private void updateMembershipForGroup(String groupName) {
        Map<String, BankServerInterface> members = groups.get(groupName);
        if (members == null) return;

        GroupInfo info = new GroupInfo(new ArrayList<>(members.keySet()));
        // Iterate over a copy to avoid ConcurrentModificationException if a member fails during update.
        for (BankServerInterface replica : new ArrayList<>(members.values())) {
            try {
                replica.updateMembership(info);
            } catch (RemoteException e) {
                try {
                    System.err.println("Failed to update membership for " + replica.getinstanceName() + ". It might be down.");
                } catch (RemoteException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    @Override
    public synchronized void updateMembership() throws RemoteException {
        for (String groupName : new ArrayList<>(groups.keySet())) {
            updateMembershipForGroup(groupName);
        }
    }

    private String findGroupForReplica(String replicaName) {
        for (Map.Entry<String, Map<String, BankServerInterface>> groupEntry : groups.entrySet()) {
            if (groupEntry.getValue().containsKey(replicaName)) {
                return groupEntry.getKey();
            }
        }
        return null;
    }
}
