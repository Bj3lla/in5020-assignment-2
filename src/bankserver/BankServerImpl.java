package bankserver;

import common.*;
import mdserver.MDServerInterface;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class BankServerImpl extends UnicastRemoteObject implements BankServerInterface {
    private static final long serialVersionUID = 1L;

    // --- State Variables ---
    private final String instanceName;
    private final String groupName;
    private final CurrencyConverter converter;
    private final int initialReplicas;
    private final String syncBalanceMode; // "naive" or "correct"

    private Map<String, Double> balances = new ConcurrentHashMap<>();
    private List<Transaction> executedList = Collections.synchronizedList(new ArrayList<>());
    private List<Transaction> outstandingCollection = Collections.synchronizedList(new ArrayList<>());
    private int orderCounter = 0;
    private int outstandingCounter = 0;

    private MDServerInterface mdServer;
    private final List<String> members = Collections.synchronizedList(new ArrayList<>());
    private final CountDownLatch initialSyncLatch = new CountDownLatch(1);
    private final Timer broadcastTimer = new Timer();

    // Used for the "correct" getSyncedBalance implementation
    private final Map<String, CompletableFuture<Double>> pendingSyncBalanceRequests = new ConcurrentHashMap<>();


    public BankServerImpl(String instanceName, String groupName, CurrencyConverter converter, String mdServerHostPort, int replicas, String syncBalanceMode) throws RemoteException {
        super();
        this.instanceName = instanceName;
        this.groupName = groupName;
        this.converter = converter;
        this.initialReplicas = replicas;
        this.syncBalanceMode = syncBalanceMode;

        try {
            initializeStateAndRegister(mdServerHostPort);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RemoteException("Failed to initialize bank server state.", e);
        }

        // Schedule a task to broadcast outstanding transactions every 10 seconds
        this.startBroadcastingTask();
    }

    private void initializeStateAndRegister(String mdServerHostPort) throws Exception {
        // Connect to MDServer to see if other replicas already exist
        String mdServerURL = "rmi://" + mdServerHostPort + "/MDServer";
        mdServer = (mdserver.MDServerInterface) java.rmi.Naming.lookup(mdServerURL);

        List<String> currentMembers = mdServer.getGroupMembers(this.groupName);

        if (currentMembers.isEmpty()) {
            // This is the first replica, initialize with a clean state
            System.out.println(instanceName + " is the first replica. Initializing with empty state.");
            for (String currency : converter.supportedCurrencies()) {
                balances.put(currency, 0.0);
            }
        } else {
            // This is a new replica joining an existing group. Perform state transfer.
            System.out.println(instanceName + " is joining an existing group. Performing state transfer.");
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
        System.out.println(instanceName + " is waiting for " + initialReplicas + " replicas to join...");
        initialSyncLatch.await(); // This line will block until the latch is released
        System.out.println(instanceName + " initial sync complete. Starting command processing.");
    }

    private void startBroadcastingTask() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    broadcastOutstandingTransactions();
                } catch (RemoteException e) {
                    System.err.println("Error during periodic broadcast: " + e.getMessage());
                }
            }
        };
        broadcastTimer.schedule(task, 10000L, 10000L); // Delay 10s, repeat every 10s
    }

    private synchronized void broadcastOutstandingTransactions() throws RemoteException {
        if (outstandingCollection.isEmpty() || mdServer == null) {
            return;
        }

        // Create a copy of the list to broadcast. This batch of transactions will be sent.
        List<Transaction> transactionsToBroadcast = new ArrayList<>(outstandingCollection);
        
        // Clears the collection. 
        // New transactions can arrive after this, but they will wait for the next broadcast cycle.
        outstandingCollection.clear();

        System.out.println(instanceName + " broadcasting and clearing " + transactionsToBroadcast.size() + " transactions.");
        
        // Send all the transactions that were in the collection in a single message.
        Message message = new Message(instanceName, transactionsToBroadcast);
        mdServer.broadcastMessage(message);
    }


    // --- Transaction commands ---

    @Override
    public synchronized String deposit(String currency, double amount) throws RemoteException {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive.");
        }
        String command = "deposit " + currency + " " + amount;
        // Unique ID format: "<Bank server_instance_name> <outstanding_counter>"
        String txId = instanceName + " " + outstandingCounter++;
        Transaction tx = new Transaction(command, txId, System.currentTimeMillis());
        outstandingCollection.add(tx);
        return txId;
    }

    @Override
    public synchronized String addInterest(String currency, double percent) throws RemoteException {
        String command = "addInterest " + (currency == null ? "ALL" : currency) + " " + percent;
        String txId = instanceName + " " + outstandingCounter++;
        Transaction tx = new Transaction(command, txId, System.currentTimeMillis());
        outstandingCollection.add(tx);
        return txId;
    }

    // --- Balance Queries ---

    @Override
    public double getQuickBalance(String currency) throws RemoteException {
        
        double totalBalanceInUSD = 0.0;
        
        // Use a synchronized block to prevent concurrent modification issues while reading balances.
        synchronized (this.balances) {
            for (Map.Entry<String, Double> entry : balances.entrySet()) {
                String currentCurrency = entry.getKey();
                Double currentBalance = entry.getValue();
                
                // Convert the balance of the current currency to USD and add to the total.
                totalBalanceInUSD += converter.toUSD(currentCurrency, currentBalance);
            }
        }
        
        // Convert the final total from USD to the target currency specified in the parameter.
        return converter.fromUSD(currency.toUpperCase(), totalBalanceInUSD);
    }

    
    @Override
    public String getSyncedBalance(String currency) throws RemoteException {
        if ("naive".equalsIgnoreCase(syncBalanceMode)) {
            // --- NAIVE IMPLEMENTATION ---
            // This version waits until the outstanding transaction list is empty before
            // calculating the balance. The PDF warns this can lead to deadlock if
            // new transactions are always being added.
            System.out.println("Executing getSyncedBalance (NAIVE MODE): Waiting for outstanding transactions to clear...");
            
            while (!outstandingCollection.isEmpty()) {
                try {
                    // Wait for a short period before checking again to avoid busy-waiting.
                    Thread.sleep(100); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "Naive getSyncedBalance was interrupted.";
                }
            }
            
            double balance = getQuickBalance(currency);
            System.out.println("Naive Synced Balance for " + currency + ": " + balance);
            return "Naive balance for " + currency + " is: " + balance;

        } else {
            // --- CORRECT IMPLEMENTATION ---
            // This version creates a special 'getSyncedBalance' transaction and sends it through
            // the same ordering system as deposit and addInterest.
            // The client call will block and wait until that specific transaction is processed.
            System.out.println("Executing getSyncedBalance (CORRECT MODE) for " + currency);
            String command = "getSyncedBalance " + currency;
            String txId = instanceName + " " + outstandingCounter++;
            Transaction tx = new Transaction(command, txId, System.currentTimeMillis());

            CompletableFuture<Double> future = new CompletableFuture<>();
            pendingSyncBalanceRequests.put(txId, future);
            
            outstandingCollection.add(tx);

            try {
                // Block and wait for the transaction to be processed by the applyTransaction method.
                // A timeout is included to prevent waiting forever.
                Double balance = future.get(30, TimeUnit.SECONDS); 
                System.out.println("Correct Synced Balance for " + currency + ": " + balance);
                return "Correct synced balance for " + currency + " is: " + balance;
            } catch (Exception e) {
                pendingSyncBalanceRequests.remove(txId); // Clean up on failure.
                System.err.println("Error getting synced balance: " + e.getMessage());
                throw new RemoteException("Failed to get synced balance: " + e.getMessage());
            }
        }
    }

    // --- Message Handling & State Machine ---
    
    @Override
    public void receiveMessage(Message msg) throws RemoteException {
        // This method is called by the MDServer
        for (Transaction tx : msg.getTransactions()) {
            synchronized (this) {
                // Ensure a transaction is not applied more than once
                boolean alreadyExecuted = executedList.stream().anyMatch(t -> t.getUniqueId().equals(tx.getUniqueId()));
                if (!alreadyExecuted) {
                    applyTransaction(tx);
                    // Remove from outstanding after it has been ordered and applied
                    outstandingCollection.removeIf(t -> t.getUniqueId().equals(tx.getUniqueId()));
                }
            }
            // ACK each transaction individually to satisfy the MDServer's logic.
            mdServer.ack(tx.getUniqueId(), this.instanceName);
        }
    }
    
    private void applyTransaction(Transaction tx) {
        String[] parts = tx.getCommand().split("\\s+");
        String command = parts[0];

        if ("getSyncedBalance".equals(command)) {
            if (tx.getUniqueId().startsWith(this.instanceName)) {
                String currency = parts[1];
                try {
                    double balance = getQuickBalance(currency);
                    CompletableFuture<Double> future = pendingSyncBalanceRequests.remove(tx.getUniqueId());
                    if (future != null) {
                        future.complete(balance);
                    }
                } catch (RemoteException e) {
                    System.err.println("Error getting synced balance: " + e.getMessage());
                }
            }
            return;
        }

        System.out.println("Applying state change for command: " + tx.getCommand());
        switch (command) {
            case "deposit": {
                String currency = parts[1].toUpperCase();
                double amount = Double.parseDouble(parts[2]);
                
                // Add the amount directly to the specific currency's balance
                balances.merge(currency, amount, Double::sum);
                System.out.println("Deposited " + amount + " " + currency + ". New balance: " + balances.get(currency));
                break;
            }
            case "addInterest": {
                double percent = Double.parseDouble(parts[parts.length - 1]);
                double factor = 1.0 + (percent / 100.0);
                
                // Case 1: A specific currency is provided
                if (parts.length == 3) {
                    String currency = parts[1].toUpperCase();
                    balances.computeIfPresent(currency, (k, v) -> v * factor);
                    System.out.println("Applied " + percent + "% interest to " + currency + ". New balance: " + balances.get(currency));
                } 
                // Case 2: No currency is specified, apply to all 
                else {
                    System.out.println("Applying " + percent + "% interest to ALL currencies.");
                    balances.replaceAll((currency, balance) -> balance * factor);
                }
                break;
            }
        }
        
        orderCounter++;
        executedList.add(tx);
    }
    
    // --- History and Status ---
    
    @Override
    public void getHistory() throws RemoteException {
        // Correctly formatted history output
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- Transaction History for ").append(instanceName).append(" ---\n");
        
        sb.append("Executed Transactions (orderCounter: ").append(orderCounter).append("):\n");
        if (!executedList.isEmpty()) {
            int startOrder = orderCounter - executedList.size() + 1;
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            for (int i = 0; i < executedList.size(); i++) {
                Transaction tx = executedList.get(i);
                sb.append(String.format("%d. [%s] %s%n",
                        startOrder + i,
                        sdf.format(new Date(tx.getTimestamp())),
                        tx.getCommand()));
            }
        } else {
            sb.append(" (empty)\n");
        }

        sb.append("\nOutstanding Transactions:\n");
        if (!outstandingCollection.isEmpty()) {
            for (Transaction tx : outstandingCollection) {
                sb.append("- ").append(tx.getCommand()).append(" (ID: ").append(tx.getUniqueId()).append(")\n");
            }
        } else {
            sb.append(" (empty)\n");
        }
        sb.append("------------------------------------------\n");
        System.out.println(sb.toString());
    }
    
    @Override
    public synchronized void cleanHistory() {
        executedList.clear();
        // Note: order_counter and outstanding_counter are not reset
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
        System.out.println(instanceName + " membership updated: " + members);

        // Check if the initial group has formed
        if (members.size() >= initialReplicas && initialSyncLatch.getCount() > 0) {
            System.out.println("Initial replica count of " + initialReplicas + " reached. Releasing sync latch.");
            initialSyncLatch.countDown(); // Release the latch
        }
    }

    @Override
    public String getinstanceName()  {
        return this.instanceName;
    }

    @Override
    public void ack(String messageId) {
        System.out.println(instanceName + " ACK received for " + messageId);
    }
}
