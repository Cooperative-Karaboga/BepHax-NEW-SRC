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

public class Connections2b2t extends Command {
    private static final String API_ENDPOINT = "/connections?playerName=";

    public Connections2b2t() {
        super("connections2b2t", "View join/leave history for a 2b2t player.", "connections", "conn");
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
                    this.executeConnections(ctx.getArgument("player", String.class), 0);
                    return 1;
                })
                .then(argument("page", IntegerArgumentType.integer(1)).executes(ctx -> {
                    this.executeConnections(ctx.getArgument("player", String.class), ctx.getArgument("page", Integer.class) - 1);
                    return 1;
                }))
        );
        builder.executes(ctx -> {
            ApiHandler.sendResponse("§dUsage: §5/connections <player> [page]");
            return 1;
        });
    }

    private void executeConnections(String playerName, int page) {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/connections?playerName=" + playerName.trim() + "&pageSize=8&page=" + page);
            if (response != null) {
                if (response.equals("204 Undocumented")) {
                    ApiHandler.sendNotFoundResponse(playerName);
                } else {
                    try {
                        Gson gson = new Gson();
                        Connections2b2t.ConnectionsResponse resp = gson.fromJson(response, Connections2b2t.ConnectionsResponse.class);
                        if (resp == null || resp.connections == null || resp.connections.isEmpty()) {
                            ApiHandler.sendResponse("§d" + playerName + " §7has no connections" + (page > 0 ? " on page " + (page + 1) : ""));
                            return;
                        }

                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US);
                        StringBuilder sb = new StringBuilder();
                        sb.append("§d").append(playerName).append("§7's Activity §8(Page ").append(page + 1).append("/").append(resp.pageCount).append(")\n");

                        for (Connections2b2t.ConnEntry c : resp.connections) {
                            String time = this.formatTime(c.time, fmt);
                            boolean isJoin = c.connection != null && c.connection.equalsIgnoreCase("JOIN");
                            String action = isJoin ? "§aJoin" : "§cLeft";
                            sb.append("§8[").append(time).append("] ").append(action).append(" §8| ");
                        }

                        String result = sb.toString();
                        if (result.endsWith(" §8| ")) {
                            result = result.substring(0, result.length() - 5);
                        }

                        if (page < resp.pageCount - 1) {
                            result = result + "\n§8More: /conn " + playerName + " " + (page + 2);
                        }

                        ApiHandler.sendResponse(result, playerName);
                    } catch (Exception e) {
                        ApiHandler.sendErrorResponse();
                        LogUtil.error("Parse error: " + e.getMessage(), "Connections2b2t");
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

    private record ConnEntry(String time, String connection) {
    }

    private record ConnectionsResponse(List<Connections2b2t.ConnEntry> connections, int total, int pageCount) {
    }
}
