package bep.hax.commands;

import bep.hax.util.LogUtil;
import bep.hax.util.commands.ApiHandler;
import com.google.gson.Gson;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.concurrent.TimeUnit;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.commands.SharedSuggestionProvider;

public class TopPlaytime2b2t extends Command {
    private static final String API_TOP = "/playtime/top";
    private static final String API_TOP_MONTH = "/playtime/top/month";

    public TopPlaytime2b2t() {
        super("topplaytime2b2t", "View playtime leaderboard for 2b2t.", "toppt", "leaderboard");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("month").executes(ctx -> {
            this.executeTop(true);
            return 1;
        }));
        builder.then(literal("alltime").executes(ctx -> {
            this.executeTop(false);
            return 1;
        }));
        builder.executes(ctx -> {
            this.executeTop(false);
            return 1;
        });
    }

    private void executeTop(boolean monthly) {
        MeteorExecutor.execute(() -> {
            String endpoint = monthly ? "/playtime/top/month" : "/playtime/top";
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc" + endpoint);
            if (response != null) {
                try {
                    Gson gson = new Gson();
                    TopPlaytime2b2t.PlaytimeResponse resp = gson.fromJson(response, TopPlaytime2b2t.PlaytimeResponse.class);
                    if (resp == null || resp.players == null || resp.players.isEmpty()) {
                        ApiHandler.sendResponse("§7No playtime data available");
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("§eTop Playtime §8(").append(monthly ? "Monthly" : "All-Time").append(")\n");
                    int rank = 1;

                    for (TopPlaytime2b2t.PlaytimeEntry e : resp.players) {
                        if (rank > 5) {
                            break;
                        }

                        String c = rank == 1 ? "§6" : (rank == 2 ? "§f" : (rank == 3 ? "§c" : "§7"));
                        String time = this.formatPlaytime(e.playtimeSeconds);
                        sb.append(c).append(rank).append(". §f").append(e.playerName).append(" §e").append(time);
                        if (rank < 5) {
                            sb.append(" §8| ");
                        }

                        rank++;
                    }

                    ApiHandler.sendResponse(sb.toString());
                } catch (Exception e) {
                    ApiHandler.sendErrorResponse();
                    LogUtil.error("Parse error: " + e.getMessage(), "TopPlaytime2b2t");
                }
            }
        });
    }

    private String formatPlaytime(long seconds) {
        if (seconds <= 0L) {
            return "0m";
        } else {
            long days = TimeUnit.SECONDS.toDays(seconds);
            long hours = TimeUnit.SECONDS.toHours(seconds) % 24L;
            if (days > 0L) {
                return days + "d " + hours + "h";
            } else {
                return hours > 0L ? hours + "h" : TimeUnit.SECONDS.toMinutes(seconds) + "m";
            }
        }
    }

    private record PlaytimeEntry(String playerName, long playtimeSeconds) {
    }

    private record PlaytimeResponse(List<TopPlaytime2b2t.PlaytimeEntry> players) {
    }
}
