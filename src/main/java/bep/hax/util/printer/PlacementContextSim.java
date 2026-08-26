package bep.hax.util.printer;

import bep.hax.util.RotationUtils;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class PlacementContextSim extends BlockPlaceContext {
    private final float simYaw;
    private final Vec3 look;

    public PlacementContextSim(Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit, float yaw, float pitch) {
        super(player, hand, stack, hit);
        this.simYaw = yaw;
        this.look = RotationUtils.getRotationVector(pitch, yaw);
    }

    private static Vec3 normal(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    private boolean ready() {
        return this.look != null;
    }

    private Direction[] orderedByNearest() {
        if (!this.ready()) {
            return Direction.orderedByNearest(this.getPlayer());
        }

        Direction[] dirs = (Direction[])Direction.values().clone();
        Arrays.sort(dirs, Comparator.comparingDouble(d -> -this.look.dot(normal(d))));
        return dirs;
    }

    @Override
    public float getRotation() {
        return this.ready() ? this.simYaw : super.getRotation();
    }

    @Override
    public Direction getHorizontalDirection() {
        return this.ready() ? Direction.fromYRot(this.simYaw) : super.getHorizontalDirection();
    }

    @Override
    public Direction getNearestLookingDirection() {
        return this.orderedByNearest()[0];
    }

    @Override
    public Direction getNearestLookingVerticalDirection() {
        if (!this.ready()) {
            return super.getNearestLookingVerticalDirection();
        } else {
            return this.look.y > 0.0 ? Direction.UP : Direction.DOWN;
        }
    }

    @Override
    public Direction[] getNearestLookingDirections() {
        if (!this.ready()) {
            return super.getNearestLookingDirections();
        }

        Direction[] dirs = this.orderedByNearest();
        if (this.replacingClickedOnBlock()) {
            return dirs;
        }

        Direction opposite = this.getClickedFace().getOpposite();
        int i = 0;

        while (i < dirs.length && dirs[i] != opposite) {
            i++;
        }

        if (i > 0) {
            System.arraycopy(dirs, 0, dirs, 1, i);
            dirs[0] = opposite;
        }

        return dirs;
    }
}
