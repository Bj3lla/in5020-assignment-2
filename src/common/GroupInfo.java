package common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the current membership of a replica group.
 */
public class GroupInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<String> members;

    public GroupInfo(List<String> members) {
        this.members = new ArrayList<>(members); // defensive copy
    }

    public List<String> getMembers() {
        return Collections.unmodifiableList(members);
    }

    @Override
    public String toString() {
        return "GroupInfo: " + members;
    }
}
