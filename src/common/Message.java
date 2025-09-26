package common;

import java.io.Serializable;
import java.util.List;

/**
 * Message sent between MDServer and BankServer replicas.
 * Contains a list of transactions to be applied in total order.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String senderId;                 // sender replica ID
    private final List<Transaction> transactions; // transactions in this message

    public Message(String senderId, List<Transaction> transactions) {
        this.senderId = senderId;
        this.transactions = transactions;
    }

    public String getSenderId() {
        return senderId;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    @Override
    public String toString() {
        return "Message from " + senderId + " with " + transactions.size() + " transactions";
    }
}
