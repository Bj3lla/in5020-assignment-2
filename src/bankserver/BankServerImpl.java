// Implementation Class: implements the interface and contains the actual logic.
package bankserver;

import common.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import mdserver.MDServerInterface;

public class BankServerImpl extends UnicastRemoteObject implements BankServerInterface {
    private static final long serialVersionUID = 1L;

    private final String serverName;
    private final CurrencyConverter converter;
    private final int replicas;

    private final Map<String, Double> balances = new HashMap<>();
    private final List<Transaction> executedList = new ArrayList<>();
    private final List<Transaction> outstandingCollection = new ArrayList<>();
    private int orderCounter = 0;
    private int outstandingCounter = 0;

    private MDServerInterface mdServer;
    private final List<String> members = new ArrayList<>();

    public BankServerImpl(String serverName, CurrencyConverter converter, String mdServerHost, int replicas) throws RemoteException {
        super();
        this.serverName = serverName;
        this.converter = converter;
        this.replicas = replicas;

        // Initialize balances for all supported currencies
        for (String currency : converter.supportedCurrencies()) {
            balances.put(currency, 0.0);
        }

        // Connect to MDServer on host with hardcoded port 1099
        try {
            String mdServerURL = "rmi://" + mdServerHost + ":1099/MDServer"; 
            mdServer = (MDServerInterface) Naming.lookup(mdServerURL);
            mdServer.registerReplica(this);
            System.out.println("Connected to MDServer at " + mdServerURL);
        } catch (Exception e) {
            System.err.println("Failed to connect to MDServer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Transaction commands ---
    @Override
    public synchronized void deposit(String currency, double amount) throws RemoteException {
        String command = "deposit " + currency + " " + amount;
        String txId = serverName + "_" + outstandingCounter++;
        Transaction tx = new Transaction(command, txId, System.currentTimeMillis());
        outstandingCollection.add(tx);
        mdServer.broadcastMessage(new Message(serverName, List.of(tx)));
    }

    @Override
    public synchronized void addInterest(String currency, double percent) throws RemoteException {
        String command = "addInterest " + currency + " " + percent;
        String txId = serverName + "_" + outstandingCounter++;
        Transaction tx = new Transaction(command, txId, System.currentTimeMillis());
        outstandingCollection.add(tx);
        mdServer.broadcastMessage(new Message(serverName, List.of(tx)));
    }

    @Override
    public synchronized void addInterestAll(double percent) throws RemoteException {
        String command = "addInterestAll " + percent;
        String txId = serverName + "_" + outstandingCounter++;
        Transaction tx = new Transaction(command, txId, System.currentTimeMillis());
        outstandingCollection.add(tx);
        mdServer.broadcastMessage(new Message(serverName, List.of(tx)));
    }

    @Override
    public synchronized void getSyncedBalance(String currency) throws RemoteException {
        String txId = serverName + "_" + outstandingCounter++;
        String command = "getSyncedBalance " + currency.toUpperCase();
        Transaction tx = new Transaction(command, txId, System.currentTimeMillis());
        outstandingCollection.add(tx);
        mdServer.broadcastMessage(new Message(serverName, List.of(tx)));
    }

    // --- Apply transactions received from MDServer ---
    @Override
    public synchronized void receiveMessage(Message msg) throws RemoteException {
        for (Transaction tx : msg.getTransactions()) {
            applyTransaction(tx);
        }
        // Explicit ACK to MDServer
        mdServer.ack(msg.getSenderId(), serverName);
    }

    private synchronized void applyTransaction(Transaction tx) {
        String[] parts = tx.getCommand().split("\\s+");
        switch (parts[0]) {
            case "deposit" -> {
                String currency = parts[1].toUpperCase();
                double amount = Double.parseDouble(parts[2]);
                balances.put(currency, balances.getOrDefault(currency, 0.0) + amount);
            }
            case "addInterest" -> {
                String cur = parts[1].toUpperCase();
                double pct = Double.parseDouble(parts[2]);
                balances.put(cur, balances.get(cur) * (1 + pct / 100.0));
            }
            case "addInterestAll" -> {
                double pct = Double.parseDouble(parts[1]);
                for (String cur : balances.keySet()) {
                    balances.put(cur, balances.get(cur) * (1 + pct / 100.0));
                }
            }
            case "getSyncedBalance" -> {
                if (tx.getUniqueId().startsWith(serverName)) {
                    String cur = parts[1].toUpperCase();
                    double value = balances.getOrDefault(cur, 0.0);
                    System.out.println("Synced balance for " + cur + ": " + value);
                }
            }
        }
        orderCounter++;
        executedList.add(tx);
        outstandingCollection.remove(tx);
    }

    // --- Balance queries ---
    @Override
    public synchronized double getQuickBalance(String currency) {
        return balances.getOrDefault(currency.toUpperCase(), 0.0);
    }

    // --- History / members / status ---
    @Override
    public synchronized void getHistory() {
        System.out.println("Executed transactions:");
        for (Transaction tx : executedList) System.out.println(tx);
        System.out.println("Outstanding transactions:");
        for (Transaction tx : outstandingCollection) System.out.println(tx);
    }

    @Override
    public synchronized void cleanHistory() {
        executedList.clear();
        outstandingCollection.clear();
    }

    @Override
    public synchronized void checkTxStatus(String txId) {
        boolean executed = executedList.stream().anyMatch(tx -> tx.getUniqueId().equals(txId));
        boolean outstanding = outstandingCollection.stream().anyMatch(tx -> tx.getUniqueId().equals(txId));
        if (executed) System.out.println("Transaction " + txId + " has been executed.");
        else if (outstanding) System.out.println("Transaction " + txId + " is outstanding.");
        else System.out.println("Transaction " + txId + " not found.");
    }

    @Override
    public synchronized void printMembers() {
        System.out.println("Current members: " + members);
    }

    @Override
    public synchronized void updateMembership(GroupInfo groupInfo) throws RemoteException {
        members.clear();
        members.addAll(groupInfo.getMembers());
        System.out.println(serverName + " membership updated: " + members);
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public void ack(String messageId) {
        System.out.println(serverName + " ACK received for " + messageId);
    }
}
