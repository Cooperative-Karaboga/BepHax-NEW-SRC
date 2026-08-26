package bep.hax.commands;

import bep.hax.util.LogUtil;
import bep.hax.util.commands.ApiHandler;
import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.commands.SharedSuggestionProvider;

public class IsBot2b2t extends Command {
    private static final String API_ENDPOINT = "/bots/month";

    public IsBot2b2t() {
        super("isbot2b2t", "Check if a player is a suspected bot on 2b2t.", "isbot", "bot", "bots");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("list").executes(ctx -> {
            this.executeBotsList();
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
                    String player = ctx.getArgument("player", String.class);
                    if (player.equalsIgnoreCase("list")) {
                        this.executeBotsList();
                    } else {
                        this.executeIsBot(player);
                    }

                    return 1;
                })
        );
        builder.executes(ctx -> {
            ApiHandler.sendResponse("§7Usage: §c/isbot <player> §8or §c/isbot list");
            return 1;
        });
    }

    private void executeBotsList() {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/bots/month");
            if (response != null) {
                try {
                    Gson gson = new Gson();
                    IsBot2b2t.BotResponse resp = gson.fromJson(response, IsBot2b2t.BotResponse.class);
                    if (resp == null || resp.players == null || resp.players.isEmpty()) {
                        ApiHandler.sendResponse("§7No suspected bots in last 30 days");
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("§cSuspected Bots §8(").append(resp.players.size()).append(" found)\n");
                    int count = 0;

                    for (IsBot2b2t.BotEntry b : resp.players) {
                        if (count >= 10) {
                            sb.append("§8+").append(resp.players.size() - 10).append(" more");
                            break;
                        }

                        sb.append("§c").append(b.playerName);
                        if (count < 9 && count < resp.players.size() - 1) {
                            sb.append(" §8| ");
                        }

                        count++;
                    }

                    ApiHandler.sendResponse(sb.toString());
                } catch (Exception e) {
                    ApiHandler.sendErrorResponse();
                    LogUtil.error("Parse error: " + e.getMessage(), "IsBot2b2t");
                }
            }
        });
    }

    private void executeIsBot(String playerName) {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/bots/month");
            if (response != null) {
                try {
                    Gson gson = new Gson();
                    IsBot2b2t.BotResponse resp = gson.fromJson(response, IsBot2b2t.BotResponse.class);
                    if (resp == null || resp.players == null || resp.players.isEmpty()) {
                        ApiHandler.sendResponse("§a" + playerName + " §7is §anot§7 a suspected bot");
                        return;
                    }

                    IsBot2b2t.BotEntry found = null;

                    for (IsBot2b2t.BotEntry b : resp.players) {
                        if (b.playerName != null && b.playerName.equalsIgnoreCase(playerName)) {
                            found = b;
                            break;
                        }
                    }

                    if (found != null) {
                        ApiHandler.sendResponse("§c" + playerName + " §7is a §csuspected bot", playerName);
                    } else {
                        ApiHandler.sendResponse("§a" + playerName + " §7is §anot§7 a suspected bot", playerName);
                    }
                } catch (Exception e) {
                    ApiHandler.sendErrorResponse();
                    LogUtil.error("Parse error: " + e.getMessage(), "IsBot2b2t");
                }
            }
        });
    }

    private record BotEntry(String playerName, String uuid) {
    }

    private record BotResponse(List<IsBot2b2t.BotEntry> players) {
    }
}
