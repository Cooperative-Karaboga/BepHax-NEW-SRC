package bep.hax.modules.livemessage.irc;

import java.util.Objects;

public class IrcUser {
    private final String nickname;
    private IrcUser.Role role;
    private boolean away = false;

    public IrcUser(String nickname, IrcUser.Role role) {
        this.nickname = nickname;
        this.role = role;
    }

    public String getNickname() {
        return this.nickname;
    }

    public IrcUser.Role getRole() {
        return this.role;
    }

    public void setRole(IrcUser.Role role) {
        this.role = role;
    }

    public boolean isAway() {
        return this.away;
    }

    public String getDisplayName() {
        return this.role.prefix + this.nickname;
    }

    public int getNameColor() {
        return this.role.color | 0xFF000000;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            IrcUser ircUser = (IrcUser)o;
            return this.nickname.equalsIgnoreCase(ircUser.nickname);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.nickname.toLowerCase());
    }

    @Override
    public String toString() {
        return this.getDisplayName();
    }

    public enum Role {
        OWNER("[ADM] ", 2, 16733525),
        MODERATOR("[MOD] ", 1, 5636095),
        NORMAL("", 0, 11184810);

        public final String prefix;
        public final int level;
        public final int color;

        Role(String prefix, int level, int color) {
            this.prefix = prefix;
            this.level = level;
            this.color = color;
        }

        public boolean canModerate() {
            return this.level >= MODERATOR.level;
        }
    }
}
