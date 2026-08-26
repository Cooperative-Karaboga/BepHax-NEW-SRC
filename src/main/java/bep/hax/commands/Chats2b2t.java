package bep.hax.commands;

import bep.hax.util.LogUtil;
import bep.hax.util.commands.ApiHandler;
import com.google.gson.Gson;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.commands.SharedSuggestionProvider;

public class Chats2b2t extends Command {
    private static final String API_ENDPOINT = "/chats?playerName=";
    private static final String API_SEARCH = "/chats?word=";

    public Chats2b2t() {
        super("chats2b2t", "View chat history for a player on 2b2t.", "chats", "chatlog");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("search").then(argument("word", StringArgumentType.word()).executes(ctx -> {
            this.executeSearch(ctx.getArgument("word", String.class), 0);
            return 1;
        }).then(argument("page", IntegerArgumentType.integer(1)).executes(ctx -> {
            this.executeSearch(ctx.getArgument("word", String.class), ctx.getArgument("page", Integer.class) - 1);
            return 1;
        }))));
        builder.then(
            argument("player", StringArgumentType.word())
                .suggests(
                    (ctx, suggestionsBuilder) -> mc.getConnection() != null
                        ? SharedSuggestionProvider.suggest(mc.getConnection().getOnlinePlayers().stream().map(entry -> entry.getProfile().name()), suggestionsBuilder)
                        : suggestionsBuilder.buildFuture()
                )
                .executes(ctx -> {
                    this.executePlayerChats(ctx.getArgument("player", String.class), 0);
                    return 1;
                })
                .then(argument("page", IntegerArgumentType.integer(1)).executes(ctx -> {
                    this.executePlayerChats(ctx.getArgument("player", String.class), ctx.getArgument("page", Integer.class) - 1);
                    return 1;
                }))
        );
        builder.executes(ctx -> {
            ApiHandler.sendResponse("§7Usage: §f/chats <player> [page] §8or §f/chats search <word> [page]");
            return 1;
        });
    }

    private void executePlayerChats(String playerName, int page) {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/chats?playerName=" + playerName.trim() + "&pageSize=5&page=" + page);
            if (response != null) {
                if (response.equals("204 Undocumented")) {
                    ApiHandler.sendNotFoundResponse(playerName);
                } else {
                    try {
                        Gson gson = new Gson();
                        Chats2b2t.ChatsResponse resp = gson.fromJson(response, Chats2b2t.ChatsResponse.class);
                        if (resp == null || resp.chats == null || resp.chats.isEmpty()) {
                            ApiHandler.sendResponse("§f" + playerName + " §7has no messages" + (page > 0 ? " on page " + (page + 1) : ""));
                            return;
                        }

                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US);
                        StringBuilder sb = new StringBuilder();
                        sb.append("§fMessages by §7").append(playerName).append(" §8(Page ").append(page + 1).append("/").append(resp.pageCount).append(")\n");

                        for (Chats2b2t.ChatEntry c : resp.chats) {
                            String time = this.formatTime(c.time, fmt);
                            String msg = c.chat != null ? c.chat : "";
                            if (msg.length() > 40) {
                                msg = msg.substring(0, 37) + "...";
                            }

                            sb.append("§8[").append(time).append("] §f").append(msg).append(" §8| ");
                        }

                        String result = sb.toString();
                        if (result.endsWith(" §8| ")) {
                            result = result.substring(0, result.length() - 5);
                        }

                        if (page < resp.pageCount - 1) {
                            result = result + "\n§8More: /chats " + playerName + " " + (page + 2);
                        }

                        ApiHandler.sendResponse(result, playerName);
                    } catch (Exception e) {
                        ApiHandler.sendErrorResponse();
                        LogUtil.error("Parse error: " + e.getMessage(), "Chats2b2t");
                    }
                }
            }
        });
    }

    private void executeSearch(String word, int page) {
        MeteorExecutor.execute(() -> {
            String encoded = URLEncoder.encode(word, StandardCharsets.UTF_8);
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/chats?word=" + encoded + "&pageSize=5&page=" + page);
            if (response != null) {
                if (response.equals("204 Undocumented")) {
                    ApiHandler.sendResponse("§7No messages with '§f" + word + "§7'");
                } else {
                    try {
                        Gson gson = new Gson();
                        Chats2b2t.ChatsResponse resp = gson.fromJson(response, Chats2b2t.ChatsResponse.class);
                        if (resp == null || resp.chats == null || resp.chats.isEmpty()) {
                            ApiHandler.sendResponse("§7No messages with '§f" + word + "§7'" + (page > 0 ? " on page " + (page + 1) : ""));
                            return;
                        }

                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US);
                        StringBuilder sb = new StringBuilder();
                        sb.append("§7Search: '§f").append(word).append("§7' §8(Page ").append(page + 1).append("/").append(resp.pageCount).append(")\n");

                        for (Chats2b2t.ChatEntry c : resp.chats) {
                            String time = this.formatTime(c.time, fmt);
                            String player = c.playerName != null ? c.playerName : "?";
                            String msg = c.chat != null ? c.chat : "";
                            if (msg.length() > 30) {
                                msg = msg.substring(0, 27) + "...";
                            }

                            sb.append("§8[").append(time).append("] §a").append(player).append("§7: §f").append(msg).append(" §8| ");
                        }

                        String result = sb.toString();
                        if (result.endsWith(" §8| ")) {
                            result = result.substring(0, result.length() - 5);
                        }

                        if (page < resp.pageCount - 1) {
                            result = result + "\n§8More: /chats search " + word + " " + (page + 2);
                        }

                        ApiHandler.sendResponse(result);
                    } catch (Exception e) {
                        ApiHandler.sendErrorResponse();
                        LogUtil.error("Parse error: " + e.getMessage(), "Chats2b2t");
                    }
                }
            }
        });
    }

    private String formatTime(String iso, DateTimeFormatter fmt) {
        if (iso == null) {
            return "??:??";
        }

        try {
            return Instant.parse(iso).atZone(ZoneId.systemDefault()).format(fmt);
        } catch (Exception e) {
            return "??:??";
        }
    }

    private record ChatEntry(String playerName, String chat, String time) {
    }

    private record ChatsResponse(List<Chats2b2t.ChatEntry> chats, int total, int pageCount) {
    }
}
