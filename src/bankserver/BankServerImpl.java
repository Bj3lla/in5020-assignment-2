// Implementation Class: implements the interface and contains the actual logic.
package bankserver;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

import common.Message;
import common.GroupInfo;
import common.Transaction;

public class BankServerImpl extends UnicastRemoteObject implements BankServerInterface {
    private static final long serialVersionUID = 1L;
    private final String serverName;
    private final CurrencyConverter converter;
    private final String mdServerAddr;
    private final int replicas;
    private double balanceUSD;

    // default state is 0.0 for all currencies
    private Map<String, Double> balances = new HashMap<>();

    // for replication
    private List<Transaction> executedList = new ArrayList<>();
    private List<Transaction> outstandingCollection = new ArrayList<>();
    private int orderCounter = 0;
    private int outstandingCounter = 0;

    public BankServerImpl(String serverName, CurrencyConverter converter, String mdServerAddr, int replicas) throws RemoteException {
        super();
        this.serverName = serverName;
        this.converter = converter; //Injected, not created inside
        this.mdServerAddr = mdServerAddr;
        this.replicas = replicas;
        
        // Initialize balances for all currencies known from the converter
        for (String currency : converter.supportedCurrencies()) {
            balances.put(currency, 0.0);
        }
    }

    // TODO: connect to MDServer via RMI lookup here
    //@Override
    public synchronized void deposit(String currency, double amount) throws RemoteException {
        String command = "deposit" + currency + " " + amount;
        String txId = serverName + "_" + outstandingCounter++;
        Transaction tx = new Transaction(command, txId, System.currentTimeMillis());
        outstandingCollection.add(tx);
        // The actual balances will be updated only when the transaction is applied

    }

        public synchronized void applyTransaction(Transaction tx) {
        String[] parts = tx.getCommand().split("\\s+");
        switch (parts[0]) {
            case "deposit" -> {
                String currency = parts[1].toUpperCase();
                double amount = Double.parseDouble(parts[2]);
                balances.put(currency, balances.getOrDefault(currency, 0.0) + amount);
            }

            case "addInterest" -> {
                if (parts.length == 2) { // global interest
                    double percent = Double.parseDouble(parts[1]);
                    for (String cur : balances.keySet()) {
                        balances.put(cur, balances.get(cur) * (1 + percent / 100.0));
                    }
                } else { // per-currency
                    String cur = parts[1].toUpperCase();
                    double pct = Double.parseDouble(parts[2]);
                    balances.put(cur, balances.get(cur) * (1 + pct / 100.0));
                }
            }
        }

        orderCounter++;
        executedList.add(tx);
        outstandingCollection.remove(tx);
    }
    
    // Get balance in a specific currency (converted if necessary)
    public synchronized double getQuickBalance(String currency) {
        String cur = currency.toUpperCase();
        return balances.getOrDefault(cur, 0.0);
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
