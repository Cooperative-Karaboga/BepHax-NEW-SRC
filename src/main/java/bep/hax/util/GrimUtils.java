package bep.hax.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GrimUtils {
    private GrimUtils() {
    }

    public static double[] getPossibleEyeHeights(Player player) {
        float scale = (float)player.getAttributeValue(Attributes.SCALE);
        double standing = 1.62 * scale;
        double sneaking = 1.27 * scale;
        double swimming = 0.4 * scale;

        return switch (player.getPose()) {
            case FALL_FLYING, SPIN_ATTACK, SWIMMING -> new double[]{swimming, standing, sneaking};
            case CROUCHING -> new double[]{sneaking, standing, swimming};
            default -> new double[]{standing, sneaking, swimming};
        };
    }

    public static double closestEyeDistanceSqTo(Player player, Vec3 target) {
        Vec3 base = player.position();
        double best = Double.MAX_VALUE;

        for (double h : getPossibleEyeHeights(player)) {
            double d = base.add(0.0, h, 0.0).distanceToSqr(target);
            if (d < best) {
                best = d;
            }
        }

        return best;
    }

    public static double closestEyeDistanceSqTo(Player player, AABB box) {
        Vec3 base = player.position();
        double best = Double.MAX_VALUE;

        for (double h : getPossibleEyeHeights(player)) {
            Vec3 eye = base.add(0.0, h, 0.0);
            double cx = clamp(eye.x, box.minX, box.maxX);
            double cy = clamp(eye.y, box.minY, box.maxY);
            double cz = clamp(eye.z, box.minZ, box.maxZ);
            double d = eye.distanceToSqr(cx, cy, cz);
            if (d < best) {
                best = d;
            }
        }

        return best;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int nextSequence() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0;
        }

        BlockStatePredictionHandler manager = mc.level.getBlockStatePredictionHandler();
        int seq = manager.startPredicting().currentSequence();
        manager.close();
        return seq;
    }
}
