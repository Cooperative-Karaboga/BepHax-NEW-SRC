package bep.hax.commands;

import bep.hax.util.LogUtil;
import bep.hax.util.commands.ApiHandler;
import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.commands.SharedSuggestionProvider;

public class Stats2b2t extends Command {
    private static final String API_ENDPOINT = "/stats/player?playerName=";

    public Stats2b2t() {
        super("stats2b2t", "View comprehensive stats for a 2b2t player.", "stats", "profile");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(
            argument("player", StringArgumentType.word())
                .suggests(
                    (ctx, suggestionsBuilder) -> mc.getConnection() != null
                        ? SharedSuggestionProvider.suggest(mc.getConnection().getOnlinePlayers().stream().map(entry -> entry.getProfile().name()), suggestionsBuilder)
                        : suggestionsBuilder.buildFuture()
                )
                .executes(ctx -> {
                    this.executeStats(ctx.getArgument("player", String.class));
                    return 1;
                })
        );
        builder.executes(ctx -> {
            ApiHandler.sendResponse("§6Usage: §e/stats <player>");
            return 1;
        });
    }

    private void executeStats(String playerName) {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/stats/player?playerName=" + playerName.trim());
            if (response != null) {
                if (response.equals("204 Undocumented")) {
                    ApiHandler.sendNotFoundResponse(playerName);
                } else {
                    try {
                        Gson gson = new Gson();
                        Stats2b2t.PlayerStats s = gson.fromJson(response, Stats2b2t.PlayerStats.class);
                        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
                        String kd = s.deathCount > 0 ? String.format("%.2f", (double)s.killCount / s.deathCount) : "N/A";
                        String pt = this.formatPlaytime(s.playtimeSeconds);
                        StringBuilder sb = new StringBuilder();
                        sb.append("§6").append(playerName).append("§e Stats\n");
                        sb.append("§6Joins: §e").append(nf.format(s.joinCount));
                        sb.append(" §8| §6Kills: §e").append(nf.format(s.killCount));
                        sb.append(" §8| §6Deaths: §e").append(nf.format(s.deathCount));
                        sb.append(" §8| §6K/D: §e").append(kd).append("\n");
                        sb.append("§6Messages: §e").append(nf.format(s.chatsCount));
                        sb.append(" §8| §6Playtime: §e").append(pt);
                        sb.append(" §8| §6Prio: §e").append(s.prio ? "Yes" : "No");
                        ApiHandler.sendResponse(sb.toString(), playerName);
                    } catch (Exception e) {
                        ApiHandler.sendErrorResponse();
                        LogUtil.error("Parse error: " + e.getMessage(), "Stats2b2t");
                    }
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

    private record PlayerStats(
        int joinCount,
        int leaveCount,
        int deathCount,
        int killCount,
        String firstSeen,
        String lastSeen,
        long playtimeSeconds,
        long playtimeSecondsMonth,
        int chatsCount,
        boolean prio
    ) {
    }
}
