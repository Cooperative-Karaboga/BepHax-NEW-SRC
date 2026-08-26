package bep.hax.commands;

import bep.hax.util.EnemyManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.commands.SharedSuggestionProvider;

public class EnemyCommand extends Command {
    public EnemyCommand() {
        super("enemy", "Manage friends marked as enemies");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("add").then(argument("name", StringArgumentType.word()).executes(context -> {
            String name = context.getArgument("name", String.class);
            if (EnemyManager.get().isEnemy(name)) {
                this.error("(highlight)%s(default) is already marked as an enemy.", name);
                return 1;
            }

            if (EnemyManager.get().add(name)) {
                this.info("Marked (highlight)%s(default) as an enemy.", name);
            } else {
                this.error("Failed to mark (highlight)%s(default) as an enemy.", name);
            }

            return 1;
        })));
        builder.then(literal("remove").then(argument("name", StringArgumentType.word()).executes(context -> {
            String name = context.getArgument("name", String.class);
            if (!EnemyManager.get().isEnemy(name)) {
                this.error("(highlight)%s(default) is not marked as an enemy.", name);
                return 1;
            }

            if (EnemyManager.get().remove(name)) {
                this.info("Removed (highlight)%s(default) from enemies.", name);
            } else {
                this.error("Failed to remove (highlight)%s(default) from enemies.", name);
            }

            return 1;
        })));
        builder.then(literal("list").executes(context -> {
            if (EnemyManager.get().count() == 0) {
                this.info("No enemies marked.");
                return 1;
            }

            this.info("--- Enemies (highlight)(%d)(default) ---", EnemyManager.get().count());

            for (String name : EnemyManager.get().getEnemyNames()) {
                this.info(" - (highlight)%s", name);
            }

            return 1;
        }));
        builder.then(literal("clear").executes(context -> {
            int count = EnemyManager.get().count();
            if (count == 0) {
                this.info("No enemies to clear.");
                return 1;
            } else {
                EnemyManager.get().clear();
                this.info("Cleared (highlight)%d(default) enemies.", count);
                return 1;
            }
        }));
    }
}
