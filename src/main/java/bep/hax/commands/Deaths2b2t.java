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

public class Deaths2b2t extends Command {
    private static final String API_ENDPOINT = "/deaths?playerName=";
    private static final String API_TOP = "/deaths/top/month";

    public Deaths2b2t() {
        super("deaths2b2t", "View death history or most deaths on 2b2t.", "deaths");
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
                    this.executeDeaths(ctx.getArgument("player", String.class), 0);
                    return 1;
                })
                .then(argument("page", IntegerArgumentType.integer(1)).executes(ctx -> {
                    this.executeDeaths(ctx.getArgument("player", String.class), ctx.getArgument("page", Integer.class) - 1);
                    return 1;
                }))
        );
        builder.executes(ctx -> {
            ApiHandler.sendResponse("§4Usage: §c/deaths <player> [page] §8or §c/deaths top");
            return 1;
        });
    }

    private void executeTop() {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/deaths/top/month");
            if (response != null) {
                try {
                    Gson gson = new Gson();
                    Deaths2b2t.TopResponse resp = gson.fromJson(response, Deaths2b2t.TopResponse.class);
                    if (resp == null || resp.players == null || resp.players.isEmpty()) {
                        ApiHandler.sendResponse("§7No death data available");
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("§4Most Deaths §8(Monthly)\n");
                    int rank = 1;

                    for (Deaths2b2t.TopEntry e : resp.players) {
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
                    LogUtil.error("Parse error: " + e.getMessage(), "Deaths2b2t");
                }
            }
        });
    }

    private void executeDeaths(String playerName, int page) {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/deaths?playerName=" + playerName.trim() + "&pageSize=5&page=" + page);
            if (response != null) {
                if (response.equals("204 Undocumented")) {
                    ApiHandler.sendNotFoundResponse(playerName);
                } else {
                    try {
                        Gson gson = new Gson();
                        Deaths2b2t.DeathsResponse resp = gson.fromJson(response, Deaths2b2t.DeathsResponse.class);
                        if (resp == null || resp.deaths == null || resp.deaths.isEmpty()) {
                            ApiHandler.sendResponse("§c" + playerName + " §7has no deaths" + (page > 0 ? " on page " + (page + 1) : ""));
                            return;
                        }

                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US);
                        StringBuilder sb = new StringBuilder();
                        sb.append("§c").append(playerName).append("§7's Deaths §8(Page ").append(page + 1).append("/").append(resp.pageCount).append(")\n");

                        for (Deaths2b2t.DeathEntry d : resp.deaths) {
                            String time = this.formatTime(d.time, fmt);
                            String displayMessage;
                            if (d.message != null && !d.message.isEmpty()) {
                                displayMessage = d.message;
                            } else {
                                String victim = d.victimName != null && !d.victimName.isEmpty() ? d.victimName : playerName;
                                String killer = d.killerName != null && !d.killerName.isEmpty() ? d.killerName : "Unknown";
                                displayMessage = victim + " killed by " + killer;
                            }

                            sb.append("§8[").append(time).append("] §f").append(displayMessage).append(" §8| ");
                        }

                        String result = sb.toString();
                        if (result.endsWith(" §8| ")) {
                            result = result.substring(0, result.length() - 5);
                        }

                        if (page < resp.pageCount - 1) {
                            result = result + "\n§7Next: §f/deaths " + playerName + " " + (page + 2);
                        }

                        ApiHandler.sendResponse(result, playerName);
                    } catch (Exception e) {
                        ApiHandler.sendErrorResponse();
                        LogUtil.error("Parse error: " + e.getMessage(), "Deaths2b2t");
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

    private record DeathEntry(String victimName, String killerName, String message, String time) {
    }

    private record DeathsResponse(List<Deaths2b2t.DeathEntry> deaths, int total, int pageCount) {
    }

    private record TopEntry(String playerName, String uuid, int count) {
    }

    private record TopResponse(List<Deaths2b2t.TopEntry> players) {
    }
}
