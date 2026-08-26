package bep.hax.commands;

import bep.hax.modules.WaypointFollower;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.commands.SharedSuggestionProvider;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;

public class WaypointFollowerCommand extends Command {
    public WaypointFollowerCommand() {
        super("wf", "Waypoint Follower commands", "waypointfollower");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("add").then(argument("x", IntegerArgumentType.integer()).then(argument("z", IntegerArgumentType.integer()).executes(context -> {
            int x = context.getArgument("x", Integer.class);
            int z = context.getArgument("z", Integer.class);
            WaypointFollower module = Modules.get().get(WaypointFollower.class);
            if (module == null) {
                this.error("WaypointFollower module not found.");
                return 1;
            }

            try {
                MinimapWorld waypointWorld = this.getWaypointWorld();
                if (waypointWorld == null) {
                    this.error("Could not get waypoint world. Try opening Xaero's world map first.");
                    return 1;
                }

                int storedX = module.toWaypointWorldCoord(x, waypointWorld);
                int storedZ = module.toWaypointWorldCoord(z, waypointWorld);
                int y = WaypointFollower.getWaypointY(waypointWorld);
                String prefix = module.getWaypointPrefix();
                int number = module.getNextWaypointNumber(waypointWorld);
                String waypointName = prefix + number;
                WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
                if (currentSet == null) {
                    this.error("Could not get waypoint set.");
                    return 1;
                }

                Waypoint waypoint = new Waypoint(storedX, y, storedZ, waypointName, "H", WaypointFollower.HUNT_COLOR, WaypointPurpose.NORMAL, false);
                currentSet.add(waypoint);
                this.saveWaypoints(waypointWorld);
                SupportMods.xaeroMinimap.requestWaypointsRefresh();
                if (module.isActive()) {
                    module.addWaypointToTrack(storedX, y, storedZ);
                }

                this.info("Created waypoint (highlight)%s(default) at (highlight)%d, %d", waypointName, x, z);
            } catch (Exception e) {
                this.error("Failed to create waypoint: " + e.getMessage());
            }

            return 1;
        }))));
        builder.then(literal("clear").executes(context -> {
            WaypointFollower module = Modules.get().get(WaypointFollower.class);
            if (module == null) {
                this.error("WaypointFollower module not found.");
                return 1;
            } else {
                module.clearAllFollowWaypoints();
                module.clearTrackedWaypoints();
                this.info("Cleared all hunt waypoints.");
                return 1;
            }
        }));
        builder.then(literal("skip").executes(context -> {
            WaypointFollower module = Modules.get().get(WaypointFollower.class);
            if (module == null) {
                this.error("WaypointFollower module not found.");
                return 1;
            }

            if (!module.isActive()) {
                this.error("WaypointFollower is not active.");
                return 1;
            }

            int remaining = module.skipCurrentWaypoint();
            if (remaining >= 0) {
                this.info("Skipped waypoint. (highlight)%d(default) remaining.", remaining);
            } else {
                this.warning("No waypoints to skip.");
            }

            return 1;
        }));
        builder.then(literal("pause").executes(context -> {
            WaypointFollower module = Modules.get().get(WaypointFollower.class);
            if (module == null) {
                this.error("WaypointFollower module not found.");
                return 1;
            }

            if (!module.isActive()) {
                this.error("WaypointFollower is not active.");
                return 1;
            }

            if (module.isPaused()) {
                this.warning("Already paused.");
            } else {
                module.pause();
                this.info("Waypoint following (highlight)paused(default).");
            }

            return 1;
        }));
        builder.then(literal("resume").executes(context -> {
            WaypointFollower module = Modules.get().get(WaypointFollower.class);
            if (module == null) {
                this.error("WaypointFollower module not found.");
                return 1;
            }

            if (!module.isActive()) {
                this.error("WaypointFollower is not active.");
                return 1;
            }

            if (!module.isPaused()) {
                this.warning("Already running.");
            } else {
                module.resume();
                this.info("Waypoint following (highlight)resumed(default).");
            }

            return 1;
        }));
        builder.then(
            literal("list")
                .executes(
                    context -> {
                        WaypointFollower module = Modules.get().get(WaypointFollower.class);
                        if (module == null) {
                            this.error("WaypointFollower module not found.");
                            return 1;
                        }

                        try {
                            String prefix = module.getWaypointPrefix();
                            MinimapWorld waypointWorld = this.getWaypointWorld();
                            if (waypointWorld == null) {
                                this.error("Could not get waypoint world.");
                                return 1;
                            }

                            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
                            if (currentSet == null) {
                                this.error("Could not get waypoint set.");
                                return 1;
                            }

                            List<Waypoint> huntWaypoints = new ArrayList<>();

                            for (Waypoint wp : currentSet.getWaypoints()) {
                                if (wp.getName().startsWith(prefix)) {
                                    huntWaypoints.add(wp);
                                }
                            }

                            if (huntWaypoints.isEmpty()) {
                                this.info("No hunt waypoints found.");
                                return 1;
                            }

                            this.info("--- Hunt Waypoints (highlight)(%d)(default) ---", huntWaypoints.size());

                            for (Waypoint wp : huntWaypoints) {
                                this.info(
                                    " - (highlight)%s(default): %d, %d",
                                    wp.getName(),
                                    module.toPlayerDimensionCoord(wp.getX()),
                                    module.toPlayerDimensionCoord(wp.getZ())
                                );
                            }
                        } catch (Exception e) {
                            this.error("Failed to list waypoints: " + e.getMessage());
                        }

                        return 1;
                    }
                )
        );
    }

    private MinimapWorld getWaypointWorld() {
        WaypointFollower module = Modules.get().get(WaypointFollower.class);
        if (module != null) {
            MinimapWorld world = module.getWaypointWorld();
            if (world != null) {
                return world;
            }
        }

        return SupportMods.xaeroMinimap.getWaypointWorld();
    }

    private void saveWaypoints(MinimapWorld waypointWorld) {
        try {
            MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (minimapSession != null) {
                minimapSession.getWorldManagerIO().saveWorld(waypointWorld);
            }
        } catch (Exception var3) {
        }
    }
}
