// class that both banserver and mdserver need access to
package common;

import java.io.Serializable;
import java.util.List;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String senderId;
    private final List<Transaction> transactions;

    public Message(String senderId, List<Transaction> transactions) {
        this.senderId = senderId;
        this.transactions = transactions;
    }

    public String getSenderId() { return senderId; }
    public List<Transaction> getTransactions() { return transactions; }
}
