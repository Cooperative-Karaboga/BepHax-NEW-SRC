package bep.hax.commands;

import bep.hax.modules.livemessage.irc.IrcClient;
import bep.hax.modules.livemessage.irc.IrcConfig;
import bep.hax.modules.livemessage.irc.IrcUser;
import bep.hax.modules.livemessage.irc.IrcWindow;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.commands.SharedSuggestionProvider;

public class IrcCommand extends Command {
    public IrcCommand() {
        super("irc", "IRC chat commands for LiveMessage", "liveirc");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("connect").executes(ctx -> {
            IrcWindow window = IrcWindow.getInstance();
            if (window == null) {
                this.error("IRC window not open. Open LiveMessage GUI first.");
                return 1;
            } else if (window.getIrcClient().isConnected()) {
                this.warning("Already connected to IRC.");
                return 1;
            } else {
                window.connectToIrc();
                this.info("Connecting to IRC...");
                return 1;
            }
        }));
        builder.then(literal("disconnect").executes(ctx -> {
            IrcWindow window = IrcWindow.getInstance();
            if (window != null && window.getIrcClient().isConnected()) {
                window.disconnect();
                this.info("Disconnected from IRC.");
                return 1;
            } else {
                this.error("Not connected to IRC.");
                return 1;
            }
        }));
        builder.then(literal("say").then(argument("message", StringArgumentType.greedyString()).executes(ctx -> {
            IrcWindow window = IrcWindow.getInstance();
            if (window != null && window.getIrcClient().isAuthenticated()) {
                String message = StringArgumentType.getString(ctx, "message");
                window.getIrcClient().sendMessage(message);
                return 1;
            } else {
                this.error("Not connected to IRC.");
                return 1;
            }
        })));
        builder.then(literal("kick").then(argument("user", StringArgumentType.word()).executes(ctx -> {
            IrcClient client = this.getConnectedClient();
            if (client == null) {
                return 1;
            }

            String user = StringArgumentType.getString(ctx, "user");
            client.kick(user, "Kicked by moderator");
            this.info("Kicked (highlight)%s", user);
            return 1;
        }).then(argument("reason", StringArgumentType.greedyString()).executes(ctx -> {
            IrcClient client = this.getConnectedClient();
            if (client == null) {
                return 1;
            }

            String user = StringArgumentType.getString(ctx, "user");
            String reason = StringArgumentType.getString(ctx, "reason");
            client.kick(user, reason);
            this.info("Kicked (highlight)%s(default): %s", user, reason);
            return 1;
        }))));
        builder.then(literal("ban").then(argument("user", StringArgumentType.word()).executes(ctx -> {
            IrcClient client = this.getConnectedClient();
            if (client == null) {
                return 1;
            }

            String user = StringArgumentType.getString(ctx, "user");
            client.ban(user);
            this.info("Banned (highlight)%s", user);
            return 1;
        })));
        builder.then(literal("unban").then(argument("user", StringArgumentType.word()).executes(ctx -> {
            IrcClient client = this.getConnectedClient();
            if (client == null) {
                return 1;
            }

            String user = StringArgumentType.getString(ctx, "user");
            client.unban(user);
            this.info("Unbanned (highlight)%s", user);
            return 1;
        })));
        builder.then(literal("mod").then(argument("user", StringArgumentType.word()).executes(ctx -> {
            IrcClient client = this.getConnectedClient();
            if (client == null) {
                return 1;
            }

            String user = StringArgumentType.getString(ctx, "user");
            client.mod(user);
            this.info("Promoted (highlight)%s(default) to moderator", user);
            return 1;
        })));
        builder.then(literal("demod").then(argument("user", StringArgumentType.word()).executes(ctx -> {
            IrcClient client = this.getConnectedClient();
            if (client == null) {
                return 1;
            }

            String user = StringArgumentType.getString(ctx, "user");
            client.demod(user);
            this.info("Demoted (highlight)%s(default) from moderator", user);
            return 1;
        })));
        builder.then(literal("clear").executes(ctx -> {
            IrcClient client = this.getConnectedClient();
            if (client == null) {
                return 1;
            }

            client.clearChat();
            this.info("Chat cleared.");
            return 1;
        }));
        builder.then(literal("users").executes(ctx -> {
            IrcWindow window = IrcWindow.getInstance();
            if (window != null && window.getIrcClient().isAuthenticated()) {
                List<IrcUser> users = window.getIrcClient().getUserList().getSortedUsers();
                if (users.isEmpty()) {
                    this.info("No users in channel.");
                } else {
                    StringBuilder sb = new StringBuilder("Users (").append(users.size()).append("): ");

                    for (int i = 0; i < users.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }

                        sb.append(users.get(i).getDisplayName());
                    }

                    this.info(sb.toString());
                }

                return 1;
            } else {
                this.error("Not connected to IRC.");
                return 1;
            }
        }));
        builder.then(
            literal("config")
                .then(literal("official").executes(ctx -> {
                    this.info("Official server uses your BepHax credentials.");
                    this.info("Make sure you're logged in to BepHax to use the official IRC.");
                    IrcConfig.resetToOfficial();
                    this.info("Configured for official BepHax IRC server.");
                    return 1;
                }))
                .then(
                    literal("custom")
                        .then(
                            argument("nick", StringArgumentType.word())
                                .then(argument("channel", StringArgumentType.word()).then(argument("url", StringArgumentType.greedyString()).executes(ctx -> {
                                    String nick = StringArgumentType.getString(ctx, "nick");
                                    String channel = StringArgumentType.getString(ctx, "channel");
                                    String url = StringArgumentType.getString(ctx, "url").trim();
                                    IrcConfig.configureCustom(url, channel, nick, "", "");
                                    this.info("Configured for custom IRC server: %s %s as %s", url, channel, nick);
                                    return 1;
                                })))
                        )
                )
                .then(literal("url").then(argument("url", StringArgumentType.greedyString()).executes(ctx -> {
                    String url = StringArgumentType.getString(ctx, "url").trim();
                    IrcConfig.setServerUrl(url);
                    IrcConfig.setUseCustomServer(true);
                    this.info("Set IRC server URL: %s", url);
                    return 1;
                })))
                .then(literal("channel").then(argument("channel", StringArgumentType.word()).executes(ctx -> {
                    String channel = StringArgumentType.getString(ctx, "channel");
                    IrcConfig.setChannel(channel);
                    this.info("Set IRC channel: %s", IrcConfig.getChannel());
                    return 1;
                })))
                .then(literal("nick").then(argument("nick", StringArgumentType.word()).executes(ctx -> {
                    String nick = StringArgumentType.getString(ctx, "nick");
                    IrcConfig.setNickname(nick);
                    this.info("Set IRC nickname: %s", nick);
                    return 1;
                })))
                .then(literal("password").then(argument("password", StringArgumentType.greedyString()).executes(ctx -> {
                    String password = StringArgumentType.getString(ctx, "password").trim();
                    IrcConfig.setServerPassword(password);
                    this.info("Set server password.");
                    return 1;
                })))
                .then(literal("show").executes(ctx -> {
                    this.info("IRC Configuration:");
                    this.info("  Server: %s", IrcConfig.isOfficialServer() ? "Official (bep.dek.to)" : IrcConfig.getServerUrl());
                    this.info("  Channel: %s", IrcConfig.getChannel());
                    this.info("  Nickname: %s", IrcConfig.getNickname());
                    this.info("  Custom server: %s", IrcConfig.isCustomServer() ? "Yes" : "No");
                    return 1;
                }))
        );
        builder.executes(ctx -> {
            IrcWindow window = IrcWindow.getInstance();
            if (window == null) {
                this.info("IRC: (gray)Not initialized(default). Open LiveMessage GUI to use IRC.");
            } else if (!window.getIrcClient().isConnected()) {
                this.info("IRC: (red)Disconnected(default). Use (highlight).irc connect(default) to connect.");
            } else if (!window.getIrcClient().isAuthenticated()) {
                this.info("IRC: (yellow)Connecting...");
            } else {
                IrcClient client = window.getIrcClient();
                this.info("IRC: (green)Connected(default) to %s as %s (%d users)", client.getChannel(), client.getNickname(), client.getUserList().size());
            }

            return 1;
        });
    }

    private IrcClient getConnectedClient() {
        IrcWindow window = IrcWindow.getInstance();
        if (window != null && window.getIrcClient().isAuthenticated()) {
            return window.getIrcClient();
        }

        this.error("Not connected to IRC.");
        return null;
    }
}
