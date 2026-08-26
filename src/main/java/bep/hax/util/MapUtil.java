package bep.hax.util;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import org.jetbrains.annotations.Nullable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;
import xaero.map.mods.SupportMods;
import xaeroplus.settings.Settings;

public class MapUtil {
    public static void addWaypoint(BlockPos pos, String name, String initials, MapUtil.Purpose purpose, MapUtil.WpColor color, boolean temp, String dimension) {
        if (Utils.XAERO_AVAILABLE) {
            MapUtil.XaeroIntegration.addWaypoint(pos, name, initials, purpose, color, temp, dimension);
        }
    }

    public static void removeWaypoints(String name, Predicate<BlockPos> posPredicate, Optional<Integer> yOverride) {
        if (Utils.XAERO_AVAILABLE) {
            MapUtil.XaeroIntegration.removeWaypoints(name, posPredicate, yOverride);
        }
    }

    public static void removeWaypointsInArea(String namePrefix, int minX, int minZ, int maxX, int maxZ, @Nullable String dimension) {
        if (Utils.XAERO_AVAILABLE) {
            MapUtil.XaeroIntegration.removeWaypointsInArea(namePrefix, minX, minZ, maxX, maxZ, dimension);
        }
    }

    public enum Purpose {
        Normal,
        Destination;
    }

    public enum WpColor {
        Black,
        Dark_Blue,
        Dark_Green,
        Dark_Aqua,
        Dark_Red,
        Dark_Purple,
        Gold,
        Gray,
        Dark_Gray,
        Blue,
        Green,
        Aqua,
        Red,
        Purple,
        Yellow,
        White,
        Random;
    }

    private static class XaeroIntegration {
        static void addWaypoint(
            BlockPos pos, String name, String initials, MapUtil.Purpose purpose, MapUtil.WpColor color, boolean temp, @Nullable String dimension
        ) {
            try {
                int waypointX = pos.getX();
                int waypointY = pos.getY();
                int waypointZ = pos.getZ();
                MinimapWorld targetWorld = getWaypointWorld();
                if (dimension != null && dimension.equals("the_nether")) {
                    try {
                        if (Settings.REGISTRY.owAutoWaypointDimension.get()) {
                            waypointX = pos.getX() * 8;
                            waypointZ = pos.getZ() * 8;
                            MinimapWorld overworld = findOverworldWorld();
                            if (overworld != null) {
                                targetWorld = overworld;
                            }
                        }
                    } catch (Exception var14) {
                    }
                }

                if (targetWorld == null) {
                    LogUtil.warn("Cancelling waypoint with name \"" + name + "\" because the waypoint world is null..!", "MapUtil");
                    return;
                }

                WaypointSet set = targetWorld.getCurrentWaypointSet();
                if (set == null) {
                    LogUtil.warn("Cancelling waypoint with name \"" + name + "\" because the waypoint set is null..!", "MapUtil");
                    return;
                }

                for (Waypoint wp : set.getWaypoints()) {
                    if (wp.getX() == waypointX && wp.getZ() == waypointZ) {
                        LogUtil.warn("Cancelling duplicate waypoint with name: \"" + name + "\"..!", "MapUtil");
                        return;
                    }
                }

                Waypoint waypoint = new Waypoint(waypointX, waypointY, waypointZ, name, initials, getColor(color), getPurpose(purpose), temp);
                set.add(waypoint);
                SupportMods.xaeroMinimap.requestWaypointsRefresh();
                saveWaypoints(targetWorld);
            } catch (Exception err) {
                LogUtil.error("Error while trying to add waypoint to Xaero map! Why - " + err, "MapUtil");
            }
        }

        @Nullable
        static MinimapSession getMinimapSession() {
            return BuiltInHudModules.MINIMAP.getCurrentSession();
        }

        @Nullable
        static MinimapWorld getWaypointWorld() {
            MinimapSession session = getMinimapSession();
            return session == null ? null : session.getWorldManager().getCurrentWorld();
        }

        @Nullable
        static MinimapWorld findOverworldWorld() {
            MinimapSession session = getMinimapSession();
            if (session == null) {
                return null;
            }

            MinimapWorldRootContainer rootContainer = session.getWorldManager().getCurrentRootContainer();
            if (rootContainer == null) {
                return null;
            }

            for (MinimapWorld world : rootContainer.getWorlds()) {
                try {
                    if ("overworld".equals(world.getDimId().identifier().getPath())) {
                        return world;
                    }
                } catch (Exception var5) {
                }
            }

            return null;
        }

        @Nullable
        static WaypointSet getWaypointSet() {
            MinimapWorld currentWorld = getWaypointWorld();
            return currentWorld == null ? null : currentWorld.getCurrentWaypointSet();
        }

        static void removeWaypointsInArea(String namePrefix, int minX, int minZ, int maxX, int maxZ, @Nullable String dimension) {
            try {
                long scale = 1L;
                MinimapWorld targetWorld = getWaypointWorld();
                if (dimension != null && dimension.equals("the_nether")) {
                    try {
                        if (Settings.REGISTRY.owAutoWaypointDimension.get()) {
                            scale = 8L;
                            MinimapWorld overworld = findOverworldWorld();
                            if (overworld != null) {
                                targetWorld = overworld;
                            }
                        }
                    } catch (Exception var21) {
                    }
                }

                if (targetWorld == null) {
                    return;
                }

                WaypointSet set = targetWorld.getCurrentWaypointSet();
                if (set == null) {
                    return;
                }

                long sMinX = minX * scale;
                long sMaxX = maxX * scale;
                long sMinZ = minZ * scale;
                long sMaxZ = maxZ * scale;
                List<Waypoint> toRemove = new ObjectArrayList<>();

                for (Waypoint wp : set.getWaypoints()) {
                    if (wp.getName().startsWith(namePrefix) && wp.getX() >= sMinX && wp.getX() <= sMaxX && wp.getZ() >= sMinZ && wp.getZ() <= sMaxZ) {
                        toRemove.add(wp);
                    }
                }

                if (toRemove.isEmpty()) {
                    return;
                }

                for (Waypoint wp : toRemove) {
                    set.remove(wp);
                }

                SupportMods.xaeroMinimap.requestWaypointsRefresh();
                saveWaypoints(targetWorld);
            } catch (Exception err) {
                LogUtil.error("Error while trying to remove waypoints from Xaero map! Why - " + err, "MapUtil");
            }
        }

        static void removeWaypoints(String name, Predicate<BlockPos> posPredicate, Optional<Integer> yOverride) {
            try {
                WaypointSet set = getWaypointSet();
                if (set == null) {
                    return;
                }

                MutableBlockPos mPos = new MutableBlockPos();
                List<Waypoint> toRemove = new ObjectArrayList<>();

                for (Waypoint wp : set.getWaypoints()) {
                    if (wp.getName().trim().startsWith(name.trim())) {
                        int y = yOverride.orElseGet(wp::getY);
                        mPos.set(wp.getX(), y, wp.getZ());
                        if (posPredicate.test(mPos)) {
                            toRemove.add(wp);
                        }
                    }
                }

                for (Waypoint wp : toRemove) {
                    set.remove(wp);
                }

                saveWaypoints();
            } catch (Exception err) {
                LogUtil.error("Error while trying to remove waypoints from Xaero map! Why - " + err, "MapUtil");
            }
        }

        static void saveWaypoints() {
            saveWaypoints(getWaypointWorld());
        }

        static void saveWaypoints(@Nullable MinimapWorld world) {
            try {
                MinimapSession session = getMinimapSession();
                if (world != null && session != null) {
                    session.getWorldManagerIO().saveWorld(world);
                }
            } catch (Exception err) {
                LogUtil.error("Failed saving minimap waypoints! Why: " + err, "MapUtil");
            }
        }

        static WaypointPurpose getPurpose(MapUtil.Purpose purpose) {
            return switch (purpose) {
                case Normal -> WaypointPurpose.NORMAL;
                case Destination -> WaypointPurpose.DESTINATION;
            };
        }

        static WaypointColor getColor(MapUtil.WpColor color) {
            return switch (color) {
                case Black -> WaypointColor.BLACK;
                case Dark_Blue -> WaypointColor.DARK_BLUE;
                case Dark_Green -> WaypointColor.DARK_GREEN;
                case Dark_Aqua -> WaypointColor.DARK_AQUA;
                case Dark_Red -> WaypointColor.DARK_RED;
                case Dark_Purple -> WaypointColor.DARK_PURPLE;
                case Gold -> WaypointColor.GOLD;
                case Gray -> WaypointColor.GRAY;
                case Dark_Gray -> WaypointColor.DARK_GRAY;
                case Blue -> WaypointColor.BLUE;
                case Green -> WaypointColor.GREEN;
                case Aqua -> WaypointColor.AQUA;
                case Red -> WaypointColor.RED;
                case Purple -> WaypointColor.PURPLE;
                case Yellow -> WaypointColor.YELLOW;
                case White -> WaypointColor.WHITE;
                case Random -> WaypointColor.getRandom();
            };
        }
    }
}
