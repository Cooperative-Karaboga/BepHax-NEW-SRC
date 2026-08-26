package bep.hax.modules.livemessage.irc;

import bep.hax.emoji.EmojiData;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IrcClient implements Listener {
    private static final Logger LOG = LoggerFactory.getLogger("BepHax-IRC");
    private WebSocket webSocket;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private String serverUrl;
    private String nickname;
    private String channel;
    private String authToken;
    private boolean connected = false;
    private boolean authenticated = false;
    private Consumer<IrcMessage> onMessage;
    private Consumer<IrcUserList> onUserListUpdate;
    private Consumer<String> onError;
    private Consumer<Boolean> onConnectionChange;
    private Runnable onClearChat;
    private final IrcUserList userList = new IrcUserList();
    private final StringBuilder messageBuffer = new StringBuilder();
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 5000L;
    private ScheduledFuture<?> reconnectTask;
    private boolean shouldReconnect = true;
    private ScheduledFuture<?> pingTask;
    private long lastPongTime = System.currentTimeMillis();
    private static final long PING_INTERVAL_MS = 30000L;
    private static final long PONG_TIMEOUT_MS = 60000L;

    public IrcClient() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public void connectOfficial(String serverUrl, String email, String password, String nickname, String channel) {
        this.authToken = email + ":" + password;
        this.connect(serverUrl, nickname, channel);
    }

    public void connectCustom(String serverUrl, String nickname, String channel, String password) {
        this.authToken = password;
        this.connect(serverUrl, nickname, channel);
    }

    private void connect(String serverUrl, String nickname, String channel) {
        this.serverUrl = serverUrl;
        this.nickname = nickname;
        this.channel = channel.startsWith("#") ? channel : "#" + channel;
        this.shouldReconnect = true;
        this.reconnectAttempts = 0;
        this.userList.clear();
        this.doConnect();
    }

    private void doConnect() {
        if (this.webSocket != null) {
            try {
                this.webSocket.abort();
            } catch (Exception var3) {
            }
        }

        LOG.info("Connecting to IRC: {}", this.serverUrl);

        try {
            this.httpClient
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10L))
                .buildAsync(URI.create(this.serverUrl), this)
                .whenComplete((ws, error) -> {
                    if (error != null) {
                        LOG.error("Failed to connect to IRC: {}", error.getMessage());
                        this.handleConnectionError(error.getMessage());
                    } else {
                        this.webSocket = ws;
                        this.onConnected();
                    }
                });
        } catch (Exception e) {
            LOG.error("Error initiating IRC connection", e);
            this.handleConnectionError(e.getMessage());
        }
    }

    private void onConnected() {
        LOG.info("WebSocket connected to IRC server");
        this.connected = true;
        this.reconnectAttempts = 0;
        this.lastPongTime = System.currentTimeMillis();
        this.startPingTask();
        this.sendRaw("CAP REQ :server-time");
        if (this.authToken != null && !this.authToken.isEmpty()) {
            this.sendRaw("PASS " + this.authToken);
        }

        this.sendRaw("NICK " + this.nickname);
        this.sendRaw("USER " + this.nickname + " 0 * :" + this.nickname);
        if (this.onConnectionChange != null) {
            this.runOnMainThread(() -> this.onConnectionChange.accept(true));
        }
    }

    private void handleConnectionError(String error) {
        this.connected = false;
        this.authenticated = false;
        if (this.onError != null) {
            this.runOnMainThread(() -> this.onError.accept(error));
        }

        if (this.onConnectionChange != null) {
            this.runOnMainThread(() -> this.onConnectionChange.accept(false));
        }

        this.scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (this.shouldReconnect && this.reconnectAttempts < 5) {
            this.reconnectAttempts++;
            LOG.info("Scheduling reconnect attempt {} in {}ms", this.reconnectAttempts, 5000L);
            this.reconnectTask = this.scheduler.schedule(this::doConnect, 5000L, TimeUnit.MILLISECONDS);
        } else {
            LOG.warn("Max reconnection attempts reached or reconnect disabled");
        }
    }

    private void startPingTask() {
        if (this.pingTask != null) {
            this.pingTask.cancel(false);
        }

        this.pingTask = this.scheduler.scheduleAtFixedRate(() -> {
            if (this.connected) {
                if (System.currentTimeMillis() - this.lastPongTime > 60000L) {
                    LOG.warn("PONG timeout, reconnecting...");
                    this.handleConnectionError("Connection timeout");
                    return;
                }

                this.sendRaw("PING :" + System.currentTimeMillis());
            }
        }, 30000L, 30000L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        LOG.info("IRC WebSocket opened, requesting messages");
        webSocket.request(1L);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            this.messageBuffer.append(data);
            if (last) {
                String fullMessage = this.messageBuffer.toString();
                this.messageBuffer.setLength(0);
                String[] lines = fullMessage.split("\r?\n");

                for (String line : lines) {
                    if (!line.isEmpty()) {
                        LOG.info("IRC received: {}", line);

                        try {
                            this.processIrcMessage(line);
                        } catch (Exception e) {
                            LOG.error("Error processing IRC message: {}", line, e);
                        }
                    }
                }
            }
        } finally {
            webSocket.request(1L);
        }

        return null;
    }

    @Override
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        this.lastPongTime = System.currentTimeMillis();
        webSocket.request(1L);
        return null;
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1L);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        webSocket.request(1L);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        LOG.info("IRC WebSocket closed: {} - {}", statusCode, reason);
        this.connected = false;
        this.authenticated = false;
        if (this.onConnectionChange != null) {
            this.runOnMainThread(() -> this.onConnectionChange.accept(false));
        }

        if (this.shouldReconnect) {
            this.scheduleReconnect();
        }

        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        LOG.error("IRC WebSocket error", error);
        this.handleConnectionError(error.getMessage());
    }

    private void processIrcMessage(String raw) {
        LOG.debug("IRC < {}", raw);
        IrcParser.ParsedMessage parsed = IrcParser.parse(raw);
        if (parsed != null) {
            switch (parsed.command) {
                case "CAP":
                    this.handleCap(parsed);
                    break;
                case "PING":
                    this.sendRaw("PONG :" + parsed.trailing);
                    break;
                case "PONG":
                    this.lastPongTime = System.currentTimeMillis();
                    break;
                case "001":
                    this.authenticated = true;
                    if (parsed.params.length > 0 && parsed.params[0] != null) {
                        this.nickname = parsed.params[0];
                        LOG.info("IRC server assigned nickname: {}", this.nickname);
                    }

                    LOG.info("IRC authenticated, joining channel: {}", this.channel);
                    if (this.onConnectionChange != null) {
                        this.runOnMainThread(() -> this.onConnectionChange.accept(true));
                    }

                    this.sendRaw("JOIN " + this.channel);
                    break;
                case "353":
                    this.handleNamesReply(parsed);
                    break;
                case "366":
                    if (this.onUserListUpdate != null) {
                        this.runOnMainThread(() -> this.onUserListUpdate.accept(this.userList));
                    }
                    break;
                case "JOIN":
                    this.handleJoin(parsed);
                    break;
                case "PART":
                case "QUIT":
                    this.handleLeave(parsed);
                    break;
                case "PRIVMSG":
                    this.handlePrivmsg(parsed);
                    break;
                case "NOTICE":
                    this.handleNotice(parsed);
                    break;
                case "KICK":
                    this.handleKick(parsed);
                    break;
                case "MODE":
                    this.handleMode(parsed);
                    break;
                case "464":
                    LOG.warn("IRC authentication failed");
                    this.shouldReconnect = false;
                    if (this.onError != null) {
                        this.runOnMainThread(() -> this.onError.accept("Authentication failed - check email/password"));
                    }
                    break;
                case "ERROR":
                    LOG.warn("IRC server error: {}", parsed.trailing);
                    if (this.onError != null) {
                        String errorMsg = parsed.trailing != null ? parsed.trailing : "Server error";
                        this.runOnMainThread(() -> this.onError.accept(errorMsg));
                    }
                    break;
                case "433":
                    this.nickname = this.nickname + "_";
                    this.sendRaw("NICK " + this.nickname);
                    break;
                case "474":
                    if (this.onError != null) {
                        this.runOnMainThread(() -> this.onError.accept("You are banned from this channel"));
                    }
                    break;
                default:
                    if (parsed.command.matches("\\d{3}")) {
                        LOG.debug("IRC numeric reply {}: {}", parsed.command, parsed.trailing);
                    }
            }
        }
    }

    private void handleCap(IrcParser.ParsedMessage parsed) {
        if (parsed.params.length >= 2) {
            String subCommand = parsed.params[1].toUpperCase();
            switch (subCommand) {
                case "ACK":
                    LOG.info("IRC capabilities acknowledged: {}", parsed.trailing);
                    this.sendRaw("CAP END");
                    break;
                case "NAK":
                    LOG.info("IRC capabilities rejected: {}", parsed.trailing);
                    this.sendRaw("CAP END");
                    break;
                case "LS":
                    LOG.debug("IRC available capabilities: {}", parsed.trailing);
                    this.sendRaw("CAP END");
            }
        }
    }

    private void handleNamesReply(IrcParser.ParsedMessage parsed) {
        if (parsed.params.length >= 3) {
            String names = parsed.trailing;
            if (names != null) {
                for (String name : names.split(" ")) {
                    if (!name.isEmpty()) {
                        IrcUser.Role role = IrcUser.Role.NORMAL;
                        String cleanName = name;
                        if (name.startsWith("~")) {
                            role = IrcUser.Role.OWNER;
                            cleanName = name.substring(1);
                        } else if (name.startsWith("@")) {
                            role = IrcUser.Role.MODERATOR;
                            cleanName = name.substring(1);
                        } else if (name.startsWith("&") || name.startsWith("%") || name.startsWith("+")) {
                            cleanName = name.substring(1);
                        }

                        this.userList.addUser(new IrcUser(cleanName, role));
                    }
                }
            }
        }
    }

    private void handleJoin(IrcParser.ParsedMessage parsed) {
        String nick = parsed.getNick();
        if (nick != null) {
            IrcUser user = new IrcUser(nick, IrcUser.Role.NORMAL);
            this.userList.addUser(user);
            if (this.onUserListUpdate != null) {
                this.runOnMainThread(() -> this.onUserListUpdate.accept(this.userList));
            }

            if (this.onMessage != null) {
                long timestamp = parsed.getServerTimestamp();
                IrcMessage joinMsg = IrcMessage.systemMessage(nick + " joined the channel", timestamp);
                this.runOnMainThread(() -> this.onMessage.accept(joinMsg));
            }
        }
    }

    private void handleLeave(IrcParser.ParsedMessage parsed) {
        String nick = parsed.getNick();
        if (nick != null) {
            this.userList.removeUser(nick);
            if (this.onUserListUpdate != null) {
                this.runOnMainThread(() -> this.onUserListUpdate.accept(this.userList));
            }

            String reason = parsed.trailing != null ? " (" + parsed.trailing + ")" : "";
            if (this.onMessage != null) {
                long timestamp = parsed.getServerTimestamp();
                IrcMessage leaveMsg = IrcMessage.systemMessage(nick + " left" + reason, timestamp);
                this.runOnMainThread(() -> this.onMessage.accept(leaveMsg));
            }
        }
    }

    private void handlePrivmsg(IrcParser.ParsedMessage parsed) {
        String nick = parsed.getNick();
        String message = parsed.trailing;
        if (nick != null && message != null) {
            IrcUser user = this.userList.getUser(nick);
            if (user == null) {
                user = new IrcUser(nick, IrcUser.Role.NORMAL);
            }

            long serverTime = parsed.getServerTimestamp();
            long timestamp = serverTime > 0L ? serverTime : System.currentTimeMillis();
            boolean sentByMe = nick.equalsIgnoreCase(this.nickname);
            IrcMessage ircMsg = new IrcMessage(user, message, timestamp, sentByMe);
            LOG.debug("IRC PRIVMSG from {}: {} (time={}, callback={})", nick, message, timestamp, this.onMessage != null);
            if (this.onMessage != null) {
                IrcMessage finalMsg = ircMsg;
                this.runOnMainThread(() -> {
                    LOG.debug("Delivering message to callback on main thread");
                    this.onMessage.accept(finalMsg);
                });
            }
        } else {
            LOG.warn("IRC PRIVMSG with null nick or message: nick={}, msg={}", nick, message);
        }
    }

    private void handleNotice(IrcParser.ParsedMessage parsed) {
        String message = parsed.trailing;
        if (message != null) {
            if (message.equals("CLEARCHAT")) {
                LOG.info("Chat cleared by moderator");
                if (this.onClearChat != null) {
                    this.runOnMainThread(this.onClearChat);
                }

                return;
            }

            if (this.onMessage != null) {
                long timestamp = parsed.getServerTimestamp();
                IrcMessage noticeMsg = IrcMessage.noticeMessage(message, timestamp);
                this.runOnMainThread(() -> this.onMessage.accept(noticeMsg));
            }
        }
    }

    private void handleKick(IrcParser.ParsedMessage parsed) {
        String kicker = parsed.getNick();
        String target = parsed.params.length > 1 ? parsed.params[1] : null;
        String reason = parsed.trailing != null ? parsed.trailing : "No reason";
        if (target != null) {
            this.userList.removeUser(target);
            if (this.onUserListUpdate != null) {
                this.runOnMainThread(() -> this.onUserListUpdate.accept(this.userList));
            }

            if (target.equalsIgnoreCase(this.nickname) && this.onError != null) {
                String errorMsg = "You were kicked by " + kicker + ": " + reason;
                this.runOnMainThread(() -> this.onError.accept(errorMsg));
            }

            if (this.onMessage != null) {
                long timestamp = parsed.getServerTimestamp();
                IrcMessage kickMsg = IrcMessage.systemMessage(target + " was kicked by " + kicker + " (" + reason + ")", timestamp);
                this.runOnMainThread(() -> this.onMessage.accept(kickMsg));
            }
        }
    }

    private void handleMode(IrcParser.ParsedMessage parsed) {
        if (parsed.params.length >= 3) {
            String modes = parsed.params[1];
            String target = parsed.params[2];
            boolean adding = true;

            for (char c : modes.toCharArray()) {
                if (c == '+') {
                    adding = true;
                } else if (c == '-') {
                    adding = false;
                } else {
                    IrcUser.Role newRole = null;
                    switch (c) {
                        case 'm':
                            newRole = adding ? IrcUser.Role.MODERATOR : IrcUser.Role.NORMAL;
                        default:
                            if (newRole != null) {
                                IrcUser user = this.userList.getUser(target);
                                if (user != null) {
                                    user.setRole(newRole);
                                    if (this.onUserListUpdate != null) {
                                        this.runOnMainThread(() -> this.onUserListUpdate.accept(this.userList));
                                    }
                                }
                            }
                    }
                }
            }
        }
    }

    public void sendMessage(String message) {
        if (this.connected && this.authenticated) {
            if (EmojiData.enabled()) {
                message = EmojiData.expand(message);
            }

            this.sendRaw("PRIVMSG " + this.channel + " :" + message);
            if (this.onMessage != null) {
                IrcUser self = this.userList.getUser(this.nickname);
                if (self == null) {
                    self = new IrcUser(this.nickname, IrcUser.Role.NORMAL);
                }

                IrcMessage localMsg = new IrcMessage(self, message, System.currentTimeMillis(), true);
                IrcMessage finalMsg = localMsg;
                this.runOnMainThread(() -> this.onMessage.accept(finalMsg));
            }
        } else {
            LOG.warn("Cannot send message: not connected/authenticated");
        }
    }

    public void sendCommand(String command) {
        if (this.connected) {
            this.sendRaw(command);
        }
    }

    public void kick(String target, String reason) {
        this.sendRaw("KICK " + this.channel + " " + target + " :" + (reason != null ? reason : "Kicked"));
    }

    public void ban(String target) {
        this.sendRaw("MODE " + this.channel + " +b " + target);
    }

    public void unban(String target) {
        this.sendRaw("MODE " + this.channel + " -b " + target);
    }

    public void mod(String target) {
        this.sendRaw("MODE " + this.channel + " +m " + target);
    }

    public void demod(String target) {
        this.sendRaw("MODE " + this.channel + " -m " + target);
    }

    public void clearChat() {
        this.sendRaw("PRIVMSG " + this.channel + " :!clear");
    }

    public void disconnect() {
        this.shouldReconnect = false;
        if (this.reconnectTask != null) {
            this.reconnectTask.cancel(false);
        }

        if (this.pingTask != null) {
            this.pingTask.cancel(false);
        }

        if (this.webSocket != null && this.connected) {
            this.sendRaw("QUIT :Leaving");
            this.webSocket.sendClose(1000, "Goodbye");
        }

        this.connected = false;
        this.authenticated = false;
        this.userList.clear();
        if (this.onConnectionChange != null) {
            this.runOnMainThread(() -> this.onConnectionChange.accept(false));
        }
    }

    public void shutdown() {
        this.disconnect();
        this.scheduler.shutdown();
    }

    private void sendRaw(String message) {
        if (this.webSocket != null && this.connected) {
            LOG.debug("IRC > {}", message);
            this.webSocket.sendText(message + "\r\n", true);
        }
    }

    public boolean isConnected() {
        return this.connected;
    }

    public boolean isAuthenticated() {
        return this.authenticated;
    }

    public String getNickname() {
        return this.nickname;
    }

    public String getChannel() {
        return this.channel;
    }

    public IrcUserList getUserList() {
        return this.userList;
    }

    public void setOnMessage(Consumer<IrcMessage> handler) {
        this.onMessage = handler;
    }

    public void setOnUserListUpdate(Consumer<IrcUserList> handler) {
        this.onUserListUpdate = handler;
    }

    public void setOnError(Consumer<String> handler) {
        this.onError = handler;
    }

    public void setOnConnectionChange(Consumer<Boolean> handler) {
        this.onConnectionChange = handler;
    }

    public void setOnClearChat(Runnable handler) {
        this.onClearChat = handler;
    }

    private void runOnMainThread(Runnable task) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(task);
        }
    }
}
