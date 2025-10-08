// Implementation Class: implements the interface and contains the actual logic.
package bankserver;

import common.*;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import mdserver.MDServerInterface;

public class BankServerImpl extends UnicastRemoteObject implements BankServerInterface {
    private static final long serialVersionUID = 1L;

    private final String serverName;
    private final CurrencyConverter converter;
    private final int initialReplicas;
    private final CountDownLatch initialSyncLatch = new CountDownLatch(1);

    private Map<String, Double> balances = new ConcurrentHashMap<>();
    private final List<Message> history = Collections.synchronizedList(new ArrayList<>());

    private List<Transaction> executedList = new ArrayList<>();
    private List<Transaction> outstandingCollection = new ArrayList<>();
    private int orderCounter = 0;
    private int outstandingCounter = 0;

    private MDServerInterface mdServer;
    private final List<String> members = new ArrayList<>();

    public BankServerImpl(String serverName, CurrencyConverter converter, String mdServerHostPort, int replicas) throws RemoteException {
        super();
        this.serverName = serverName;
        this.converter = converter;
        this.initialReplicas = replicas;

        // Initialize balances for all supported currencies
        for (String currency : converter.supportedCurrencies()) {
            balances.put(currency, 0.0);
        }

        // Connect to MDServer
        try {
            initializeState(mdServerHostPort);
        } catch (Exception e) {
            throw new RemoteException("Failed to initialize bank server state.", e);
        }
    }

    private void initializeState(String mdServerHostPort) throws Exception {
        // Connect to MDServer to see if other replicas already exist
        String mdServerURL = "rmi://" + mdServerHostPort + "/MDServer";
        mdServer = (mdserver.MDServerInterface) java.rmi.Naming.lookup(mdServerURL);

        List<String> currentMembers = mdServer.getGroupMembers(this.serverName);

        if (currentMembers.isEmpty()) {
            // This is the first replica, initialize with a clean state
            System.out.println(serverName + " is the first replica. Initializing with empty state.");
            for (String currency : converter.supportedCurrencies()) {
                balances.put(currency, 0.0);
            }
        } else {
            // This is a new replica joining an existing group. Perform state transfer.
            System.out.println(serverName + " is joining an existing group. Performing state transfer.");
            String existingMemberName = currentMembers.get(0); // Pick the first member
            
            BankServerInterface existingReplica = (BankServerInterface) java.rmi.Naming.lookup("rmi://" + mdServerHostPort + "/" + existingMemberName);
            AccountState state = existingReplica.getAccountState();

            // Apply the state
            synchronized(this) {
                this.balances = new ConcurrentHashMap<>(state.balances);
                this.executedList = Collections.synchronizedList(new ArrayList<>(state.executedList));
                this.outstandingCollection = Collections.synchronizedList(new ArrayList<>(state.outstandingCollection));
                this.orderCounter = state.orderCounter;
            }
            System.out.println("State transfer complete. Synced with " + existingMemberName);
        }
        
        // Now, officially register with the MD server
        mdServer.registerReplica(this);
        System.out.println("Connected to MDServer at " + mdServerURL);
    }

    @Override
    public synchronized AccountState getAccountState() throws RemoteException {
        // Create a snapshot of the current state to send to a new replica
        return new AccountState(new HashMap<>(balances), new ArrayList<>(executedList), new ArrayList<>(outstandingCollection), orderCounter);
    }

    public void awaitInitialSync() throws InterruptedException {
        System.out.println(serverName + " is waiting for " + initialReplicas + " replicas to join...");
        initialSyncLatch.await(); // This line will block until the latch is released
        System.out.println(serverName + " initial sync complete. Starting command processing.");
    }

    // --- Transaction commands ---
    @Override
    public synchronized String deposit(String currency, double amount) throws RemoteException {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive.");
        }

        // Convert the deposit amount to USD
        double amountInUSD = converter.toUSD(currency, amount);

        // Update the balance in USD
        balances.put("USD", balances.getOrDefault("USD", 0.0) + amountInUSD);

        // Create and broadcast the transaction
        String command = "deposit " + currency + " " + amount;
        String txId = serverName + "_" + outstandingCounter++;
        Transaction tx = new Transaction(command, txId, System.currentTimeMillis());
        outstandingCollection.add(tx);
        mdServer.broadcastMessage(new Message(serverName, List.of(tx)));
        return txId;
    }

    @Override
    public synchronized String addInterest(String currency, double percent) throws RemoteException {
        if (currency == null || currency.isEmpty()) {
            // Apply interest to all currencies
            applyInterestToAll(percent);
        } else {
            // Convert the balance of the specified currency to USD
            String upperCurrency = currency.toUpperCase();
            double balanceInUSD = balances.getOrDefault("USD", 0.0);
            double balanceInCurrency = converter.fromUSD(upperCurrency, balanceInUSD);

            // Apply interest to the balance in the specified currency
            double newBalanceInCurrency = balanceInCurrency * (1 + percent / 100);
            double newBalanceInUSD = converter.toUSD(upperCurrency, newBalanceInCurrency);

            // Update the balance in USD
            balances.put("USD", newBalanceInUSD);
        }

        // Create and broadcast the transaction
        String command = "addInterest " + (currency == null ? "ALL" : currency) + " " + percent;
        String txId = serverName + "_" + outstandingCounter++;
        Transaction tx = new Transaction(command, txId, System.currentTimeMillis());
        outstandingCollection.add(tx);
        mdServer.broadcastMessage(new Message(serverName, List.of(tx)));
        return txId;
    }

    @Override
    public synchronized String addInterestAll(double percent) throws RemoteException {
        String command = "addInterestAll " + percent;
        String txId = serverName + "_" + outstandingCounter++;
        Transaction tx = new Transaction(command, txId, System.currentTimeMillis());
        outstandingCollection.add(tx);
        mdServer.broadcastMessage(new Message(serverName, List.of(tx)));
        return txId;
    }

    // Helper method to apply interest to all currencies
    private synchronized void applyInterestToAll(double percent) {
        double totalInUSD = balances.getOrDefault("USD", 0.0);

        for (String currency : converter.supportedCurrencies()) {
            if (!currency.equals("USD")) {
                double balanceInCurrency = converter.fromUSD(currency, totalInUSD);
                double newBalanceInCurrency = balanceInCurrency * (1 + percent / 100);
                totalInUSD += converter.toUSD(currency, newBalanceInCurrency - balanceInCurrency);
            }
        }

        // Update the total balance in USD
        balances.put("USD", totalInUSD);
    }

    @Override
    public synchronized String getSyncedBalance(String currency) throws RemoteException {
        if (!converter.supportedCurrencies().contains(currency.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }

        // Apply all outstanding transactions locally
        applyOutstandingTransactions();

        // Convert the total balance in USD to the specified currency
        double balanceInUSD = balances.getOrDefault("USD", 0.0);
        double balanceInCurrency = converter.fromUSD(currency.toUpperCase(), balanceInUSD);

        System.out.println("Synchronized balance for " + currency + ": " + balanceInCurrency);

        // Generate a transaction ID for tracking
        String txId = serverName + "_" + outstandingCounter++;
        return txId;
    }

    // Helper method to apply all outstanding transactions
    private synchronized void applyOutstandingTransactions() {
        // Create a copy of the outstandingCollection to iterate over
        List<Transaction> transactionsToApply = new ArrayList<>(outstandingCollection);

        for (Transaction tx : transactionsToApply) {
            applyTransaction(tx); // Apply the transaction
            executedList.add(tx); // Move it to the executed list
        }

        // Clear the original outstandingCollection after processing
        outstandingCollection.removeAll(transactionsToApply);
    }

    // --- Apply transactions received from MDServer ---
    @Override
    public synchronized void receiveMessage(Message msg) throws RemoteException {
        for (Transaction tx : msg.getTransactions()) {
            applyTransaction(tx);

            // ACK for each transaction after applying
            if (mdServer != null) {
                try {
                    mdServer.ack(tx.getUniqueId(), serverName);
                } catch (RemoteException e) {
                    System.err.println("Failed to ACK tx " + tx.getUniqueId() + " from " + serverName);
                }
            }
        }
    }

    // Helper method to apply a single transaction
    private synchronized void applyTransaction(Transaction tx) {
        String[] parts = tx.getCommand().split("\\s+");
        String command = parts[0];

        switch (command) {
            case "deposit" -> {
                String currency = parts[1].toUpperCase();
                double amount = Double.parseDouble(parts[2]);

                // Convert the deposit amount to USD and update the balance
                double amountInUSD = converter.toUSD(currency, amount);
                balances.put("USD", balances.getOrDefault("USD", 0.0) + amountInUSD);
            }
            case "addInterest" -> {
                String currency = parts[1].toUpperCase();
                double percent = Double.parseDouble(parts[2]);

                if (currency.equals("ALL")) {
                    applyInterestToAll(percent);
                } else {
                    // Apply interest to the specified currency
                    double balanceInUSD = balances.getOrDefault("USD", 0.0);
                    double balanceInCurrency = converter.fromUSD(currency, balanceInUSD);
                    double newBalanceInCurrency = balanceInCurrency * (1 + percent / 100);
                    double newBalanceInUSD = converter.toUSD(currency, newBalanceInCurrency);

                    // Update the balance in USD
                    balances.put("USD", newBalanceInUSD);
                }
            }
            case "addInterestAll" -> {
                double percent = Double.parseDouble(parts[1]);
                applyInterestToAll(percent);
            }
            default -> System.err.println("Unknown transaction command: " + command);
        }
        orderCounter++;
        executedList.add(tx);
        outstandingCollection.remove(tx);
    }

    // --- Balance queries ---
    @Override
    public synchronized double getQuickBalance(String currency) {
        if (!converter.supportedCurrencies().contains(currency.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }

        // Convert the total balance in USD to the specified currency
        double balanceInUSD = balances.getOrDefault("USD", 0.0);
        return converter.fromUSD(currency.toUpperCase(), balanceInUSD);
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
    }

    @Override
    public synchronized void checkTxStatus(String txId) {
        boolean executed = executedList.stream().anyMatch(tx -> tx.getUniqueId().equals(txId));
        boolean outstanding = outstandingCollection.stream().anyMatch(tx -> tx.getUniqueId().equals(txId));

        if (executed) {
            System.out.println("Transaction " + txId + " has been executed.");
        } else if (outstanding) {
            System.out.println("Transaction " + txId + " is outstanding.");
        } else {
            System.out.println("Transaction " + txId + " not found.");
        }
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

        // Check if the initial group has formed
        if (members.size() >= initialReplicas && initialSyncLatch.getCount() > 0) {
            System.out.println("Initial replica count of " + initialReplicas + " reached. Releasing sync latch.");
            initialSyncLatch.countDown(); // Release the latch
        }
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
