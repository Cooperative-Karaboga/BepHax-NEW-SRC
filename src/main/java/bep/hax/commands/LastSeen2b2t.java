package bep.hax.commands;

import bep.hax.util.LogUtil;
import bep.hax.util.commands.ApiHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.commands.SharedSuggestionProvider;

public class LastSeen2b2t extends Command {
    private static final String API_ENDPOINT = "/seen?playerName=";

    public LastSeen2b2t() {
        super("lastseen2b2t", "Check when a player was last seen on 2b2t.", "ls", "seen", "lastseen");
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
                    this.executeLastSeen(ctx.getArgument("player", String.class));
                    return 1;
                })
        );
        builder.executes(ctx -> {
            ApiHandler.sendResponse("§3Usage: §b/lastseen <player>");
            return 1;
        });
    }

    private void executeLastSeen(String playerName) {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/seen?playerName=" + playerName.trim());
            if (response != null) {
                if (!response.equals("204 Undocumented") && !response.contains("\"lastSeen\":null")) {
                    try {
                        JsonElement json = JsonParser.parseString(response);
                        if (!json.getAsJsonObject().has("lastSeen") || json.getAsJsonObject().get("lastSeen").isJsonNull()) {
                            ApiHandler.sendResponse("§3" + playerName + " §7has never been seen on 2b2t");
                            return;
                        }

                        String lastSeen = json.getAsJsonObject().get("lastSeen").getAsString();
                        Instant instant = Instant.parse(lastSeen);
                        ZonedDateTime zonedTime = instant.atZone(ZoneId.systemDefault());
                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
                        String dateStr = zonedTime.format(fmt);
                        String timeAgo = this.formatTimeAgo(instant);
                        ApiHandler.sendResponse("§3" + playerName + " §7last seen §b" + dateStr + " §8(" + timeAgo + " ago)", playerName);
                    } catch (Exception e) {
                        ApiHandler.sendErrorResponse();
                        LogUtil.error("Parse error: " + e.getMessage(), "LastSeen2b2t");
                    }
                } else {
                    ApiHandler.sendResponse("§3" + playerName + " §7has never been seen on 2b2t");
                }
            }
        });
    }

    private String formatTimeAgo(Instant instant) {
        long totalMinutes = ChronoUnit.MINUTES.between(instant, Instant.now());
        if (totalMinutes < 1L) {
            return "now";
        }

        if (totalMinutes < 60L) {
            return totalMinutes + "m";
        }

        long hours = totalMinutes / 60L;
        if (hours < 24L) {
            return hours + "h";
        }

        long days = hours / 24L;
        if (days < 30L) {
            return days + "d";
        }

        long months = days / 30L;
        if (months < 12L) {
            return months + "mo";
        }

        long years = months / 12L;
        return years + "y " + months % 12L + "mo";
    }
}
