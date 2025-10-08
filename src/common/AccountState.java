package common;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class AccountState implements Serializable {
    private static final long serialVersionUID = 1L;

    public final Map<String, Double> balances;
    public final List<Transaction> executedList;
    public final List<Transaction> outstandingCollection;
    public final int orderCounter;

    public AccountState(Map<String, Double> balances, List<Transaction> executed, List<Transaction> outstanding, int orderCounter) {
        this.balances = balances;
        this.executedList = executed;
        this.outstandingCollection = outstanding;
        this.orderCounter = orderCounter;
    }
}
