package bep.hax.commands;

import bep.hax.util.LogUtil;
import bep.hax.util.commands.ApiHandler;
import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.commands.SharedSuggestionProvider;

public class WordCount2b2t extends Command {
    private static final String API_ENDPOINT = "/chats/word-count?word=";

    public WordCount2b2t() {
        super("wordcount2b2t", "Check how many times a word was said on 2b2t.", "wordcount", "wc");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(argument("word", StringArgumentType.word()).executes(ctx -> {
            this.executeWordCount(ctx.getArgument("word", String.class));
            return 1;
        }));
        builder.executes(ctx -> {
            ApiHandler.sendResponse("§5Usage: §d/wordcount <word> §8(4-30 chars)");
            return 1;
        });
    }

    private void executeWordCount(String word) {
        MeteorExecutor.execute(() -> {
            if (word.length() < 4) {
                ApiHandler.sendResponse("§cWord must be at least 4 characters");
            } else if (word.length() > 30) {
                ApiHandler.sendResponse("§cWord must be at most 30 characters");
            } else {
                String encoded = URLEncoder.encode(word, StandardCharsets.UTF_8);
                String response = new ApiHandler().fetchResponse("https://api.2b2t.vc/chats/word-count?word=" + encoded);
                if (response != null) {
                    if (response.equals("204 Undocumented")) {
                        ApiHandler.sendResponse("§5'" + word + "' §7never said in chat");
                    } else {
                        try {
                            Gson gson = new Gson();
                            WordCount2b2t.CountResponse count = gson.fromJson(response, WordCount2b2t.CountResponse.class);
                            if (count == null) {
                                ApiHandler.sendResponse("§7Unable to fetch word count");
                                return;
                            }

                            NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
                            ApiHandler.sendResponse("§5'" + word + "' §7said §d" + nf.format(count.count) + "§7 times");
                        } catch (Exception e) {
                            ApiHandler.sendErrorResponse();
                            LogUtil.error("Parse error: " + e.getMessage(), "WordCount2b2t");
                        }
                    }
                }
            }
        });
    }

    private record CountResponse(long count) {
    }
}
