package bep.hax.commands;

import bep.hax.modules.StashMover;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.commands.SharedSuggestionProvider;

public class SetInput extends Command {
    public SetInput() {
        super("setinput", "Start input area selection for StashMover module");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                this.error("Player is null!");
                return 0;
            }

            StashMover module = Modules.get().get(StashMover.class);
            if (module != null) {
                if (module.isSelecting()) {
                    module.cancelSelection();
                    this.info("Previous selection cancelled");
                }

                module.startInputSelection();
                this.info("§aInput area selection started!");
                this.info("§eLeft-click the first corner block");
                this.info("§7Press §cESC §7to cancel selection");
            } else {
                this.error("StashMover module not found!");
            }

            return 1;
        });
    }
}
