package bep.hax.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public class PositionUtil {
    public static List<BlockPos> getAllInBox(AABB box) {
        List<BlockPos> intersections = new ArrayList<>();

        for (int x = (int)Math.floor(box.minX); x < Math.ceil(box.maxX); x++) {
            for (int y = (int)Math.floor(box.minY); y < Math.ceil(box.maxY); y++) {
                for (int z = (int)Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
                    intersections.add(new BlockPos(x, y, z));
                }
            }
        }

        return intersections;
    }
}
