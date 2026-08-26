package bep.hax.commands;

import bep.hax.util.LogUtil;
import bep.hax.util.commands.ApiHandler;
import com.google.gson.Gson;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.commands.SharedSuggestionProvider;

public class Kills2b2t extends Command {
    private static final String API_ENDPOINT = "/kills?playerName=";
    private static final String API_TOP = "/kills/top/month";

    public Kills2b2t() {
        super("kills2b2t", "View kill history or top killers on 2b2t.", "kills");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("top").executes(ctx -> {
            this.executeTop();
            return 1;
        }));
        builder.then(
            argument("player", StringArgumentType.word())
                .suggests(
                    (ctx, suggestionsBuilder) -> mc.getConnection() != null
                        ? SharedSuggestionProvider.suggest(mc.getConnection().getOnlinePlayers().stream().map(entry -> entry.getProfile().name()), suggestionsBuilder)
                        : suggestionsBuilder.buildFuture()
                )
                .executes(ctx -> {
                    this.executeKills(ctx.getArgument("player", String.class), 0);
                    return 1;
                })
                .then(argument("page", IntegerArgumentType.integer(1)).executes(ctx -> {
                    this.executeKills(ctx.getArgument("player", String.class), ctx.getArgument("page", Integer.class) - 1);
                    return 1;
                }))
        );
        builder.executes(ctx -> {
            ApiHandler.sendResponse("§4Usage: §c/kills <player> [page] §8or §c/kills top");
            return 1;
        });
    }

    private void executeTop() {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/kills/top/month");
            if (response != null) {
                try {
                    Gson gson = new Gson();
                    Kills2b2t.TopResponse resp = gson.fromJson(response, Kills2b2t.TopResponse.class);
                    if (resp == null || resp.players == null || resp.players.isEmpty()) {
                        ApiHandler.sendResponse("§7No kill data available");
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("§4Top Killers §8(Monthly)\n");
                    int rank = 1;

                    for (Kills2b2t.TopEntry e : resp.players) {
                        if (rank > 5) {
                            break;
                        }

                        String c = rank == 1 ? "§6" : (rank == 2 ? "§f" : (rank == 3 ? "§c" : "§7"));
                        sb.append(c).append(rank).append(". §f").append(e.playerName).append(" §c").append(e.count);
                        if (rank < 5) {
                            sb.append(" §8| ");
                        }

                        rank++;
                    }

                    ApiHandler.sendResponse(sb.toString());
                } catch (Exception e) {
                    ApiHandler.sendErrorResponse();
                    LogUtil.error("Parse error: " + e.getMessage(), "Kills2b2t");
                }
            }
        });
    }

    private void executeKills(String playerName, int page) {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/kills?playerName=" + playerName.trim() + "&pageSize=5&page=" + page);
            if (response != null) {
                if (response.equals("204 Undocumented")) {
                    ApiHandler.sendNotFoundResponse(playerName);
                } else {
                    try {
                        Gson gson = new Gson();
                        Kills2b2t.KillsResponse resp = gson.fromJson(response, Kills2b2t.KillsResponse.class);
                        if (resp == null || resp.kills == null || resp.kills.isEmpty()) {
                            ApiHandler.sendResponse("§c" + playerName + " §7has no kills" + (page > 0 ? " on page " + (page + 1) : ""));
                            return;
                        }

                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US);
                        StringBuilder sb = new StringBuilder();
                        sb.append("§c").append(playerName).append("§7's Kills §8(Page ").append(page + 1).append("/").append(resp.pageCount).append(")\n");

                        for (Kills2b2t.KillEntry k : resp.kills) {
                            String time = this.formatTime(k.time, fmt);
                            String displayMessage;
                            if (k.message != null && !k.message.isEmpty()) {
                                displayMessage = k.message;
                            } else {
                                String killer = k.killerName != null && !k.killerName.isEmpty() ? k.killerName : playerName;
                                String victim = k.victimName != null && !k.victimName.isEmpty() ? k.victimName : "?";
                                displayMessage = killer + " killed " + victim;
                            }

                            sb.append("§8[").append(time).append("] §f").append(displayMessage).append(" §8| ");
                        }

                        String result = sb.toString();
                        if (result.endsWith(" §8| ")) {
                            result = result.substring(0, result.length() - 5);
                        }

                        if (page < resp.pageCount - 1) {
                            result = result + "\n§7Next: §f/kills " + playerName + " " + (page + 2);
                        }

                        ApiHandler.sendResponse(result, playerName);
                    } catch (Exception e) {
                        ApiHandler.sendErrorResponse();
                        LogUtil.error("Parse error: " + e.getMessage(), "Kills2b2t");
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

    private record KillEntry(String killerName, String victimName, String message, String time) {
    }

    private record KillsResponse(List<Kills2b2t.KillEntry> kills, int total, int pageCount) {
    }

    private record TopEntry(String playerName, String uuid, int count) {
    }

    private record TopResponse(List<Kills2b2t.TopEntry> players) {
    }
}
