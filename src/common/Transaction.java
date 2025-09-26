package common;

import java.io.Serializable;

/**
 * Represents a transaction in the replicated bank system.
 * Can be deposit, addInterest, getSyncedBalance, etc.
 */
public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String command;     // e.g., "deposit USD 100"
    private final String uniqueId;    // e.g., "Replica1_0"
    private final long timestamp;     // creation time in milliseconds

    public Transaction(String command, String uniqueId, long timestamp) {
        this.command = command;
        this.uniqueId = uniqueId;
        this.timestamp = timestamp;
    }

    public String getCommand() {
        return command;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "[" + uniqueId + "] " + command + " @ " + timestamp;
    }
}
