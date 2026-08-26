package bep.hax.commands;

import bep.hax.modules.Proximity;
import bep.hax.util.prox.EmoteManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.UUID;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.commands.SharedSuggestionProvider;

public class EmoteCommand extends Command {
    public EmoteCommand() {
        super("emote", "Play an emote, visible to nearby ProxChat users.", "e");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("list").executes(ctx -> {
            this.list();
            return 1;
        }));
        builder.then(literal("stop").executes(ctx -> {
            UUID uuid = EmoteManager.getInstance().stopLocal();
            Proximity.onLocalEmote(2, uuid, 0);
            this.noticeIfProxOff();
            return 1;
        }));
        builder.then(literal("reload").executes(ctx -> {
            EmoteManager.getInstance().reload();
            this.info("Reloaded (highlight)%d(default) emotes.", EmoteManager.getInstance().count());
            return 1;
        }));
        builder.then(
            argument("name", StringArgumentType.greedyString())
                .suggests((ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggest(EmoteManager.getInstance().emoteNames(), suggestionsBuilder))
                .executes(ctx -> {
                    this.play(ctx.getArgument("name", String.class));
                    return 1;
                })
        );
        builder.executes(
            ctx -> {
                this.list();
                this.info(
                    "Usage: (highlight)%s <name>(default), (highlight)stop(default), (highlight)list(default) or (highlight)reload(default).", this.toString()
                );
                return 1;
            }
        );
    }

    private void list() {
        List<String> names = EmoteManager.getInstance().emoteNames();
        if (names.isEmpty()) {
            this.info("No emotes loaded.");
        } else {
            this.info("Emotes (highlight)(%d)(default): %s", names.size(), String.join(", ", names));
        }
    }

    private void play(String name) {
        if (mc.player == null) {
            this.error("Not in a world.");
        } else if (!EmoteManager.getInstance().hasEmote(name)) {
            this.error("Unknown emote (highlight)%s(default). Use %s.", name, this.toString("list"));
        } else {
            UUID uuid = EmoteManager.getInstance().playLocal(name);
            if (uuid == null) {
                this.error("Could not play emote (highlight)%s(default).", name);
            } else {
                Proximity.onLocalEmote(0, uuid, 0);
                this.noticeIfProxOff();
            }
        }
    }

    private void noticeIfProxOff() {
        Proximity prox = Modules.get().get(Proximity.class);
        if (prox == null || !prox.isActive()) {
            this.info("Proximity is off — emote played locally only.");
        }
    }
}
