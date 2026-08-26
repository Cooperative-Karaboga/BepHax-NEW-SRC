package bep.hax.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class XaeroWaypointManager {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Map<String, Set<XaeroWaypointManager.WaypointKey>> categoryWaypoints = new HashMap<>();

    public static boolean isAvailable() {
        return Utils.XAERO_AVAILABLE;
    }

    public static boolean addWaypoint(
        String category, BlockPos pos, String name, String initials, MapUtil.WpColor color, boolean temporary, @Nullable String dimension
    ) {
        if (!isAvailable()) {
            return false;
        }

        XaeroWaypointManager.WaypointKey key = new XaeroWaypointManager.WaypointKey(pos.getX(), pos.getY(), pos.getZ(), category);
        Set<XaeroWaypointManager.WaypointKey> existing = categoryWaypoints.computeIfAbsent(category, k -> new HashSet<>());
        if (existing.contains(key)) {
            return false;
        }

        String fullName = category + " - " + name;
        MapUtil.addWaypoint(pos, fullName, initials, MapUtil.Purpose.Normal, color, temporary, dimension);
        existing.add(key);
        return true;
    }

    public static boolean addWaypoint(String category, BlockPos pos, String name, String initials, MapUtil.WpColor color, boolean temporary) {
        return addWaypoint(category, pos, name, initials, color, temporary, getCurrentDimension());
    }

    public static boolean addWaypoint(String category, Vec3 entityPos, String name, String initials, MapUtil.WpColor color, boolean temporary) {
        BlockPos blockPos = BlockPos.containing(entityPos);
        return addWaypoint(category, blockPos, name, initials, color, temporary);
    }

    public static void removeWaypoint(String category, BlockPos pos) {
        if (isAvailable()) {
            XaeroWaypointManager.WaypointKey key = new XaeroWaypointManager.WaypointKey(pos.getX(), pos.getY(), pos.getZ(), category);
            Set<XaeroWaypointManager.WaypointKey> existing = categoryWaypoints.get(category);
            if (existing != null) {
                existing.remove(key);
            }

            MapUtil.removeWaypoints(
                category,
                p -> p.getX() == pos.getX() && p.getY() == pos.getY() && p.getZ() == pos.getZ(),
                Optional.empty()
            );
        }
    }

    public static void removeWaypointXZ(String category, int x, int z) {
        if (isAvailable()) {
            Set<XaeroWaypointManager.WaypointKey> existing = categoryWaypoints.get(category);
            if (existing != null) {
                existing.removeIf(key -> key.x == x && key.z == z);
            }

            MapUtil.removeWaypoints(category, p -> p.getX() == x && p.getZ() == z, Optional.empty());
        }
    }

    public static void removeWaypointsInArea(String category, int minX, int minZ, int maxX, int maxZ, @Nullable String dimension) {
        if (isAvailable()) {
            Set<XaeroWaypointManager.WaypointKey> existing = categoryWaypoints.get(category);
            if (existing != null) {
                existing.removeIf(key -> key.x >= minX && key.x <= maxX && key.z >= minZ && key.z <= maxZ);
            }

            MapUtil.removeWaypointsInArea(category, minX, minZ, maxX, maxZ, dimension);
        }
    }

    public static int getWaypointCount(String category) {
        Set<XaeroWaypointManager.WaypointKey> existing = categoryWaypoints.get(category);
        return existing == null ? 0 : existing.size();
    }

    public static void resetCategoryTracking(String category) {
        categoryWaypoints.remove(category);
    }

    @Nullable
    public static String getCurrentDimension() {
        return mc.level == null ? null : mc.level.dimension().identifier().getPath();
    }

    private record WaypointKey(int x, int y, int z, String category) {
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o != null && this.getClass() == o.getClass()) {
                XaeroWaypointManager.WaypointKey that = (XaeroWaypointManager.WaypointKey)o;
                return this.x == that.x && this.y == that.y && this.z == that.z && Objects.equals(this.category, that.category);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.x, this.y, this.z, this.category);
        }
    }
}
