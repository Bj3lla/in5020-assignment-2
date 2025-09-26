// class that both banserver and mdserver need access to
package common;

import java.io.Serializable;
import java.util.List;

public class GroupInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<String> members;

    public GroupInfo(List<String> members) {
        this.members = members;
    }

    public List<String> getMembers() { return members; }
}
