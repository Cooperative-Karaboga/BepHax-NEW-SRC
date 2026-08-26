package bep.hax.modules.livemessage.irc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class IrcUserList {
    private final Map<String, IrcUser> users = new ConcurrentHashMap<>();

    public void addUser(IrcUser user) {
        this.users.put(user.getNickname().toLowerCase(), user);
    }

    public IrcUser removeUser(String nickname) {
        return this.users.remove(nickname.toLowerCase());
    }

    public IrcUser getUser(String nickname) {
        return this.users.get(nickname.toLowerCase());
    }

    public void clear() {
        this.users.clear();
    }

    public int size() {
        return this.users.size();
    }

    public List<IrcUser> getSortedUsers() {
        return this.users.values().stream().sorted((a, b) -> {
            int roleCompare = Integer.compare(b.getRole().level, a.getRole().level);
            return roleCompare != 0 ? roleCompare : a.getNickname().compareToIgnoreCase(b.getNickname());
        }).collect(Collectors.toList());
    }
}
