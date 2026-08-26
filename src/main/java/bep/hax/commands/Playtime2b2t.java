package bep.hax.commands;

import bep.hax.util.LogUtil;
import bep.hax.util.commands.ApiHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.concurrent.TimeUnit;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.commands.SharedSuggestionProvider;

public class Playtime2b2t extends Command {
    private static final String API_ENDPOINT = "/playtime?playerName=";

    public Playtime2b2t() {
        super("playtime2b2t", "Check the total playtime of a 2b2t player.", "pt", "playtime");
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
                    this.executePlaytime(ctx.getArgument("player", String.class));
                    return 1;
                })
        );
        builder.executes(ctx -> {
            ApiHandler.sendResponse("§2Usage: §a/playtime <player>");
            return 1;
        });
    }

    private void executePlaytime(String playerName) {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/playtime?playerName=" + playerName.trim());
            if (response != null) {
                if (response.equals("204 Undocumented")) {
                    ApiHandler.sendNotFoundResponse(playerName);
                } else {
                    try {
                        JsonElement json = JsonParser.parseString(response);
                        if (!json.getAsJsonObject().has("playtimeSeconds")) {
                            ApiHandler.sendErrorResponse();
                            return;
                        }

                        long totalSeconds = json.getAsJsonObject().get("playtimeSeconds").getAsLong();
                        if (totalSeconds <= 0L) {
                            ApiHandler.sendResponse("§2" + playerName + " §7has no recorded playtime");
                            return;
                        }

                        long days = TimeUnit.SECONDS.toDays(totalSeconds);
                        long hours = TimeUnit.SECONDS.toHours(totalSeconds) % 24L;
                        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60L;
                        StringBuilder time = new StringBuilder();
                        if (days > 0L) {
                            time.append("§a").append(days).append("§7d ");
                        }

                        if (hours > 0L || days > 0L) {
                            time.append("§a").append(hours).append("§7h ");
                        }

                        time.append("§a").append(minutes).append("§7m");
                        double totalHours = totalSeconds / 3600.0;
                        ApiHandler.sendResponse("§2" + playerName + "§7: " + time + " §8(" + String.format("%.0f", totalHours) + "h total)", playerName);
                    } catch (Exception e) {
                        ApiHandler.sendErrorResponse();
                        LogUtil.error("Parse error: " + e.getMessage(), "Playtime2b2t");
                    }
                }
            }
        });
    }
}
