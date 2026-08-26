package bep.hax.commands;

import bep.hax.modules.chesttracker.ChestTrackerModule;
import bep.hax.modules.chesttracker.TrackedContainer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ChestTrackerCommand extends Command {
    public ChestTrackerCommand() {
        super("chesttracker", "Search for items in tracked containers.", "ct", "track");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("search").then(literal("hand").executes(context -> {
            ChestTrackerModule module = Modules.get().get(ChestTrackerModule.class);
            if (module == null || !module.isActive()) {
                this.error("§cModule off!");
                return 1;
            } else if (mc.player == null) {
                this.error("§cNot in-game!");
                return 1;
            } else {
                ItemStack held = mc.player.getMainHandItem();
                if (held.isEmpty()) {
                    this.error("§cHand empty!");
                    return 1;
                } else {
                    this.searchAndDisplay(module, held.getItem());
                    return 1;
                }
            }
        })).then(argument("item", StringArgumentType.greedyString()).executes(context -> {
            ChestTrackerModule module = Modules.get().get(ChestTrackerModule.class);
            if (module != null && module.isActive()) {
                String itemName = StringArgumentType.getString(context, "item");
                Item item = this.findItem(itemName);
                if (item == null) {
                    this.error("§cUnknown: " + itemName);
                    return 1;
                } else {
                    this.searchAndDisplay(module, item);
                    return 1;
                }
            } else {
                this.error("§cModule off!");
                return 1;
            }
        })));
        builder.then(literal("clear").then(literal("all").executes(context -> {
            ChestTrackerModule module = Modules.get().get(ChestTrackerModule.class);
            if (module != null && module.isActive()) {
                module.getSharedData().clearAll();
                this.info("§aCleared all");
                return 1;
            } else {
                this.error("§cModule off!");
                return 1;
            }
        })).then(literal("dimension").executes(context -> {
            ChestTrackerModule module = Modules.get().get(ChestTrackerModule.class);
            if (module != null && module.isActive()) {
                module.getSharedData().clearCurrentDimension();
                this.info("§aCleared dimension");
                return 1;
            } else {
                this.error("§cModule off!");
                return 1;
            }
        })));
        builder.then(literal("export").executes(context -> {
            ChestTrackerModule module = Modules.get().get(ChestTrackerModule.class);
            if (module != null && module.isActive()) {
                try {
                    String fn = "export_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".json";
                    module.getSharedData().exportData(fn);
                    this.info("§aExported: §f" + fn);
                } catch (Exception e) {
                    this.error("§cFailed: " + e.getMessage());
                }

                return 1;
            } else {
                this.error("§cModule off!");
                return 1;
            }
        }));
        builder.then(
            literal("nearby")
                .then(
                    argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(
                            context -> {
                                ChestTrackerModule module = Modules.get().get(ChestTrackerModule.class);
                                if (module != null && module.isActive()) {
                                    if (mc.player == null) {
                                        this.error("§cNot in-game!");
                                        return 1;
                                    }

                                    int r = IntegerArgumentType.getInteger(context, "radius");
                                    List<TrackedContainer> all = module.getSharedData().getAllContainers();
                                    int found = 0;
                                    this.info("§e§lNearby (<" + r + "m):");

                                    for (TrackedContainer c : all) {
                                        BlockPos p = c.getPosition();
                                        double d = Math.sqrt(mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
                                        if (d <= r) {
                                            String col = d <= module.getRenderDistance() ? "§a" : "§7";
                                            this.info(
                                                String.format(
                                                    "%s[%d,%d,%d] §e%.0fm §b%d§7items%s",
                                                    col,
                                                    p.getX(),
                                                    p.getY(),
                                                    p.getZ(),
                                                    d,
                                                    c.getItems().size(),
                                                    c.isEmpty() ? " §c✗" : ""
                                                )
                                            );
                                            found++;
                                        }
                                    }

                                    this.info(found > 0 ? "§a" + found + " found" : "§cNone found");
                                    return 1;
                                } else {
                                    this.error("§cModule off!");
                                    return 1;
                                }
                            }
                        )
                )
        );
    }

    private void searchAndDisplay(ChestTrackerModule module, Item item) {
        List<TrackedContainer> results = module.getSharedData().searchItem(item);
        String name = item.getName().getString();
        if (results.isEmpty()) {
            this.info("§cNone found: §f" + name);
        } else {
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            int total = 0;
            int near = 0;
            double rd = module.getRenderDistance();
            double rdSq = rd * rd;

            for (TrackedContainer c : results) {
                total += c.getItemCount(itemId);
                if (mc.player != null) {
                    BlockPos p = c.getPosition();
                    if (mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= rdSq) {
                        near++;
                    }
                }
            }

            this.info(String.format("§a%,d §f%s §7in §e%d §7box%s", total, name, results.size(), results.size() > 1 ? "es" : ""));
            if (near < results.size()) {
                this.info(String.format("§7Lit: §e%d §7Far: §c%d", near, results.size() - near));
            }

            module.searchItem(item);
        }
    }

    private Item findItem(String query) {
        query = query.toLowerCase().replace(" ", "_");
        Identifier id = Identifier.tryParse("minecraft:" + query);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            return BuiltInRegistries.ITEM.getValue(id);
        }

        for (Identifier itemId : BuiltInRegistries.ITEM.keySet()) {
            String path = itemId.getPath();
            if (path.contains(query)) {
                return BuiltInRegistries.ITEM.getValue(itemId);
            }
        }

        return null;
    }
}
