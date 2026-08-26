package bep.hax.commands;

import bep.hax.modules.StashMover;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.commands.SharedSuggestionProvider;

public class StashStatus extends Command {
    public StashStatus() {
        super("stashstatus", "Check StashMover areas and configuration");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                return 0;
            }

            StashMover module = Modules.get().get(StashMover.class);
            if (module != null) {
                String prefix = Config.get().prefix.get();
                this.info("§6=== StashMover Status ===");
                if (module.isActive()) {
                    this.info("§aModule: §fACTIVE");
                    this.info("§7State: §f" + module.getCurrentState());
                    this.info("§7Items moved: §f" + module.getItemsTransferred());
                    this.info("§7Containers processed: §f" + module.getContainersProcessed());
                } else {
                    this.info("§cModule: §fINACTIVE");
                    this.info("§7Use §f" + prefix + "stash-mover §7to activate");
                }

                this.info("");
                boolean hasInput = module.hasInputArea();
                boolean hasOutput = module.hasOutputArea();
                if (hasInput) {
                    this.info("§aInput Area: §fSET");
                    this.info("§7  Containers: §f" + module.getInputContainerCount());
                } else {
                    this.info("§cInput Area: §fNOT SET");
                    this.info("§7  Use §f" + prefix + "setinput §7to select");
                }

                if (hasOutput) {
                    this.info("§bOutput Area: §fSET");
                    this.info("§7  Containers: §f" + module.getOutputContainerCount());
                } else {
                    this.info("§cOutput Area: §fNOT SET");
                    this.info("§7  Use §f" + prefix + "setoutput §7to select");
                }

                if (hasInput && hasOutput) {
                    this.info("");
                    this.info("§aReady to use! §7Enable module to start.");
                }
            } else {
                this.error("StashMover module not found!");
            }

            return 1;
        });
    }
}
