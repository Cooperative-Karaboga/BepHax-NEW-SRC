package bep.hax.commands;

import bep.hax.util.LogUtil;
import bep.hax.util.commands.ApiHandler;
import com.google.gson.Gson;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.commands.SharedSuggestionProvider;

public class SessionLimit2b2t extends Command {
    private static final String API_ENDPOINT = "/limits/session-time-limit";

    public SessionLimit2b2t() {
        super("sessionlimit2b2t", "Check non-priority session kick time on 2b2t.", "sessionlimit", "kicktime", "limit");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(ctx -> {
            this.executeSessionLimit();
            return 1;
        });
    }

    private void executeSessionLimit() {
        MeteorExecutor.execute(() -> {
            String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/limits/session-time-limit");
            if (response != null) {
                try {
                    Gson gson = new Gson();
                    SessionLimit2b2t.LimitResponse limit = gson.fromJson(response, SessionLimit2b2t.LimitResponse.class);
                    if (limit == null) {
                        ApiHandler.sendResponse("§7Unable to fetch session limit");
                        return;
                    }

                    double hours = limit.sessionTimeLimit;
                    int wholeHours = (int)hours;
                    int minutes = (int)((hours - wholeHours) * 60.0);
                    String time;
                    if (wholeHours > 0 && minutes > 0) {
                        time = wholeHours + "h " + minutes + "m";
                    } else if (wholeHours > 0) {
                        time = wholeHours + "h";
                    } else if (minutes > 0) {
                        time = minutes + "m";
                    } else {
                        time = String.format("%.1f", hours) + "h";
                    }

                    ApiHandler.sendResponse("§3Non-prio kick limit: §b" + time + " §8(players without priority get kicked)");
                } catch (Exception e) {
                    ApiHandler.sendErrorResponse();
                    LogUtil.error("Parse error: " + e.getMessage(), "SessionLimit2b2t");
                }
            }
        });
    }

    private record LimitResponse(double sessionTimeLimit) {
    }
}
