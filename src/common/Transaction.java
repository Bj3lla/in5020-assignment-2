package common;

import java.io.Serializable;

public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String command;    // e.g., "deposit NOK 500"
    private final String uniqueId;   // e.g., "Replica1 3"
    private final long timestamp;    // when it was created

    public Transaction(String command, String uniqueId, long timestamp) {
        this.command = command;
        this.uniqueId = uniqueId;
        this.timestamp = timestamp;
    }

    public String getCommand() { return command; }
    public String getUniqueId() { return uniqueId; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return timestamp + " " + command + " (" + uniqueId + ")";
    }
}
