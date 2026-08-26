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

public class FirstSeen2b2t extends Command {
    private static final String API_ENDPOINT = "/seen?playerName=";

    public FirstSeen2b2t() {
        super("firstseen2b2t", "Check when a player was first seen on 2b2t.", "fs", "firstseen");
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
                    this.executeFirstSeen(ctx.getArgument("player", String.class));
                    return 1;
                })
        );
        builder.executes(ctx -> {
            ApiHandler.sendResponse("§9Usage: §b/firstseen <player>");
            return 1;
        });
    }

    private void executeFirstSeen(String playerName) {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/seen?playerName=" + playerName.trim());
            if (response != null) {
                if (!response.equals("204 Undocumented") && !response.contains("\"firstSeen\":null")) {
                    try {
                        JsonElement json = JsonParser.parseString(response);
                        if (!json.getAsJsonObject().has("firstSeen") || json.getAsJsonObject().get("firstSeen").isJsonNull()) {
                            ApiHandler.sendResponse("§9" + playerName + " §7has never joined 2b2t");
                            return;
                        }

                        String firstSeen = json.getAsJsonObject().get("firstSeen").getAsString();
                        Instant instant = Instant.parse(firstSeen);
                        ZonedDateTime zonedTime = instant.atZone(ZoneId.systemDefault());
                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
                        String dateStr = zonedTime.format(fmt);
                        String timeAgo = this.formatTimeAgo(instant);
                        ApiHandler.sendResponse("§9" + playerName + " §7first joined §b" + dateStr + " §8(" + timeAgo + " ago)", playerName);
                    } catch (Exception e) {
                        ApiHandler.sendErrorResponse();
                        LogUtil.error("Parse error: " + e.getMessage(), "FirstSeen2b2t");
                    }
                } else {
                    ApiHandler.sendResponse("§9" + playerName + " §7has never joined 2b2t");
                }
            }
        });
    }

    private String formatTimeAgo(Instant instant) {
        long days = ChronoUnit.DAYS.between(instant, Instant.now());
        long years = days / 365L;
        long months = days % 365L / 30L;
        long remainingDays = days % 30L;
        if (years > 0L) {
            return years + "y " + months + "mo";
        } else if (months > 0L) {
            return months + "mo " + remainingDays + "d";
        } else {
            return remainingDays > 0L ? remainingDays + "d" : "today";
        }
    }
}
