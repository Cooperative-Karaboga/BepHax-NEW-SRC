package bep.hax.modules.livemessage.irc;

import java.text.SimpleDateFormat;
import java.util.Date;

public class IrcMessage {
    private final IrcMessage.Type type;
    private final IrcUser sender;
    private final String content;
    private final long timestamp;
    private final boolean sentByMe;

    public IrcMessage(IrcUser sender, String content, long timestamp) {
        this(sender, content, timestamp, false);
    }

    public IrcMessage(IrcUser sender, String content, long timestamp, boolean sentByMe) {
        this.type = this.detectType(content);
        this.sender = sender;
        this.content = this.processContent(content);
        this.timestamp = timestamp;
        this.sentByMe = sentByMe;
    }

    private IrcMessage(IrcMessage.Type type, IrcUser sender, String content, long timestamp) {
        this.type = type;
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp;
        this.sentByMe = false;
    }

    private IrcMessage.Type detectType(String content) {
        return content.startsWith("\u0001ACTION ") && content.endsWith("\u0001") ? IrcMessage.Type.ACTION : IrcMessage.Type.CHAT;
    }

    private String processContent(String content) {
        return content.startsWith("\u0001ACTION ") && content.endsWith("\u0001") ? content.substring(8, content.length() - 1) : content;
    }

    public static IrcMessage systemMessage(String content) {
        return new IrcMessage(IrcMessage.Type.SYSTEM, null, content, System.currentTimeMillis());
    }

    public static IrcMessage systemMessage(String content, long timestamp) {
        return new IrcMessage(IrcMessage.Type.SYSTEM, null, content, timestamp > 0L ? timestamp : System.currentTimeMillis());
    }

    public static IrcMessage noticeMessage(String content, long timestamp) {
        return new IrcMessage(IrcMessage.Type.NOTICE, null, content, timestamp > 0L ? timestamp : System.currentTimeMillis());
    }

    public static IrcMessage errorMessage(String content) {
        return new IrcMessage(IrcMessage.Type.ERROR, null, content, System.currentTimeMillis());
    }

    public IrcMessage.Type getType() {
        return this.type;
    }

    public IrcUser getSender() {
        return this.sender;
    }

    public String getContent() {
        return this.content;
    }

    public boolean isSentByMe() {
        return this.sentByMe;
    }

    public boolean isJoinLeaveMessage() {
        if (this.type != IrcMessage.Type.SYSTEM) {
            return false;
        }

        String lower = this.content.toLowerCase();
        return lower.contains(" joined the channel") || lower.contains(" left") || lower.contains(" was kicked");
    }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return sdf.format(new Date(this.timestamp));
    }

    public int getMessageColor() {
        switch (this.type) {
            case ACTION:
                return -3372852;
            case SYSTEM:
                return -7829368;
            case NOTICE:
                return -22016;
            case ERROR:
                return -43691;
            default:
                return -1;
        }
    }

    @Override
    public String toString() {
        switch (this.type) {
            case ACTION:
                return "* " + (this.sender != null ? this.sender.getNickname() : "???") + " " + this.content;
            case SYSTEM:
                return "*** " + this.content;
            case NOTICE:
                return "[Notice] " + this.content;
            case ERROR:
                return "[Error] " + this.content;
            default:
                return (this.sender != null ? this.sender.getDisplayName() : "???") + ": " + this.content;
        }
    }

    public enum Type {
        CHAT,
        ACTION,
        SYSTEM,
        NOTICE,
        ERROR;
    }
}
