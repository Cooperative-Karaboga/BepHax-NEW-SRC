package bep.hax.commands;

import bep.hax.modules.StashMover;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.commands.SharedSuggestionProvider;

public class SetClear extends Command {
    public SetClear() {
        super("setclear", "Clear all StashMover area selections");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(context -> {
            StashMover module = Modules.get().get(StashMover.class);
            if (module != null) {
                module.clearAreas();
                this.info("§cAll StashMover areas have been cleared");
                String prefix = Config.get().prefix.get();
                this.info("§7Use §f" + prefix + "setinput §7and §f" + prefix + "setoutput §7to select new areas");
            } else {
                this.error("StashMover module not found!");
            }

            return 1;
        });
    }
}
