package bep.hax.util.printer;

import bep.hax.util.RotationUtils;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class PlacementSolver {
    private static final Set<String> IGNORED = Set.of(
        "waterlogged",
        "powered",
        "power",
        "lit",
        "distance",
        "persistent",
        "note",
        "instrument",
        "age",
        "stage",
        "snowy",
        "bottom",
        "occupied",
        "signal_fire",
        "extended",
        "triggered",
        "locked",
        "delay",
        "in_wall",
        "shape"
    );
    private static final Set<String> CONNECTION = Set.of("north", "south", "east", "west", "up", "down");
    private static final double[] SAMPLES = new double[]{0.5, 0.25, 0.75, 0.1, 0.9};
    private static final float JITTER_MARGIN = 0.25F;
    private static final int[] K_ORDER = new int[]{0, 1, -1, 2, -2, 3, -3, 4};
    private static final int[] K_ORDER_SPRINT = new int[]{0, 1, -1};
    private static final float[] LATTICE_PITCHES = new float[]{30.0F, -30.0F, 75.0F, -75.0F};
    private static final double[] LATTICE_SAMPLES = new double[]{0.5, 0.25, 0.75};
    private static final float LATTICE_MARGIN = 1.0F;
    private static final int LATTICE_SIM_BUDGET = 800;
    private static int latticeBudget;
    private static final float[][] ANY_ROTATION_GRID = new float[][]{
        {0.0F, 30.0F},
        {90.0F, 30.0F},
        {180.0F, 30.0F},
        {-90.0F, 30.0F},
        {0.0F, -30.0F},
        {90.0F, -30.0F},
        {180.0F, -30.0F},
        {-90.0F, -30.0F},
        {0.0F, 75.0F},
        {90.0F, 75.0F},
        {180.0F, 75.0F},
        {-90.0F, 75.0F},
        {0.0F, -75.0F},
        {90.0F, -75.0F},
        {180.0F, -75.0F},
        {-90.0F, -75.0F}
    };

    private PlacementSolver() {
    }

    public static PlacementSolver.Solution solve(BlockPos target, BlockState desired, ItemStack stack, double reach, boolean allowFace, boolean allowAir) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.level == null) {
            return null;
        }

        if (!(stack.getItem() instanceof BlockItem)) {
            return null;
        }

        Vec3 eye = MeteorClient.mc.player.getEyePosition();
        double reachSq = reach * reach;
        if (allowFace) {
            BlockState ws = MeteorClient.mc.level.getBlockState(target);
            if (!ws.isAir() && !clickTriggersAction(ws.getBlock())) {
                PlacementSolver.Solution self = solveFaces(target, desired, stack, eye, reachSq, target, false);
                if (self != null) {
                    return self;
                }
            }

            PlacementSolver.Solution real = solveAgainstFace(target, desired, stack, eye, reachSq);
            if (real != null) {
                return real;
            }
        }

        return allowAir ? solveFaces(target, desired, stack, eye, reachSq, target, true) : null;
    }

    public static PlacementSolver.Solution solveAnyRotation(BlockPos target, BlockState desired, ItemStack stack, double reach) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            if (!(stack.getItem() instanceof BlockItem)) {
                return null;
            }

            Vec3 eye = MeteorClient.mc.player.getEyePosition();
            double reachSq = reach * reach;
            latticeBudget = 400;
            BlockState ws = MeteorClient.mc.level.getBlockState(target);
            if (!ws.isAir() && clickTriggersAction(ws.getBlock())) {
                return null;
            }

            for (Direction face : Direction.values()) {
                Vec3 faceCenter = Vec3.atCenterOf(target).add(normal(face).scale(0.5));

                for (double u : LATTICE_SAMPLES) {
                    for (double v : LATTICE_SAMPLES) {
                        if (latticeBudget <= 0) {
                            return null;
                        }

                        Vec3 sample = faceOffset(faceCenter, face, u - 0.5, v - 0.5);
                        PlacementSolver.Solution s = tryAnyRotation(target, desired, stack, eye, reachSq, target, face, sample);
                        if (s != null) {
                            return s;
                        }
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static PlacementSolver.Solution tryAnyRotation(
        BlockPos target, BlockState desired, ItemStack stack, Vec3 eye, double reachSq, BlockPos hitBlock, Direction clickFace, Vec3 sample
    ) {
        if (!faceVisible(eye, hitBlock, clickFace)) {
            return null;
        }

        if (sample.subtract(eye).lengthSqr() < 1.0E-6) {
            return null;
        }

        if (eye.distanceToSqr(sample) > reachSq) {
            return null;
        }

        BlockHitResult hit = new BlockHitResult(sample, clickFace, hitBlock, true);
        BlockState result = null;

        for (float[] look : ANY_ROTATION_GRID) {
            latticeBudget--;
            result = simulate(hit, stack, look[0], look[1], target);
            if (result == null || !statesMatch(desired, result)) {
                return null;
            }
        }

        return new PlacementSolver.Solution(
            hit, InteractionHand.MAIN_HAND, MeteorClient.mc.player.getYRot(), MeteorClient.mc.player.getXRot(), result, null, true
        );
    }

    public static PlacementSolver.Solution solveMoving(
        BlockPos target, BlockState desired, ItemStack stack, double reach, boolean allowFace, boolean allowAir, boolean sprinting
    ) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            if (!(stack.getItem() instanceof BlockItem)) {
                return null;
            }

            Vec3 eye = MeteorClient.mc.player.getEyePosition();
            double reachSq = reach * reach;
            float realYaw = MeteorClient.mc.player.getYRot();
            latticeBudget = 800;

            for (int k : sprinting ? K_ORDER_SPRINT : K_ORDER) {
                for (float pitch : LATTICE_PITCHES) {
                    float yaw = realYaw + k * 45.0F;
                    if (allowFace) {
                        BlockState ws = MeteorClient.mc.level.getBlockState(target);
                        if (!ws.isAir() && !clickTriggersAction(ws.getBlock())) {
                            PlacementSolver.Solution self = latticeFaces(target, desired, stack, eye, reachSq, target, false, yaw, pitch, k);
                            if (self != null) {
                                return self;
                            }
                        }

                        for (Direction face : Direction.values()) {
                            BlockPos neighbor = target.relative(face);
                            BlockState neighborState = MeteorClient.mc.level.getBlockState(neighbor);
                            if (!neighborState.canBeReplaced()
                                && !neighborState.getCollisionShape(MeteorClient.mc.level, neighbor).isEmpty()
                                && !clickTriggersAction(neighborState.getBlock())) {
                                PlacementSolver.Solution s = latticeSamples(
                                    target, desired, stack, eye, reachSq, neighbor, face.getOpposite(), false, yaw, pitch, k
                                );
                                if (s != null) {
                                    return s;
                                }
                            }
                        }
                    }

                    if (allowAir) {
                        PlacementSolver.Solution s = latticeFaces(target, desired, stack, eye, reachSq, target, true, yaw, pitch, k);
                        if (s != null) {
                            return s;
                        }
                    }

                    if (latticeBudget <= 0) {
                        return null;
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static PlacementSolver.Solution latticeFaces(
        BlockPos target,
        BlockState desired,
        ItemStack stack,
        Vec3 eye,
        double reachSq,
        BlockPos hitBlock,
        boolean inside,
        float yaw,
        float pitch,
        int k
    ) {
        for (Direction face : Direction.values()) {
            PlacementSolver.Solution s = latticeSamples(target, desired, stack, eye, reachSq, hitBlock, face, inside, yaw, pitch, k);
            if (s != null) {
                return s;
            }
        }

        return null;
    }

    private static PlacementSolver.Solution latticeSamples(
        BlockPos target,
        BlockState desired,
        ItemStack stack,
        Vec3 eye,
        double reachSq,
        BlockPos hitBlock,
        Direction clickFace,
        boolean inside,
        float yaw,
        float pitch,
        int k
    ) {
        Vec3 faceCenter = Vec3.atCenterOf(hitBlock).add(normal(clickFace).scale(0.5));

        for (double u : LATTICE_SAMPLES) {
            for (double v : LATTICE_SAMPLES) {
                Vec3 sample = faceOffset(faceCenter, clickFace, u - 0.5, v - 0.5);
                PlacementSolver.Solution s = tryLattice(target, desired, stack, eye, reachSq, hitBlock, clickFace, sample, inside, yaw, pitch, k);
                if (s != null) {
                    return s;
                }
            }
        }

        return null;
    }

    private static PlacementSolver.Solution tryLattice(
        BlockPos target,
        BlockState desired,
        ItemStack stack,
        Vec3 eye,
        double reachSq,
        BlockPos hitBlock,
        Direction clickFace,
        Vec3 sample,
        boolean inside,
        float yaw,
        float pitch,
        int k
    ) {
        if (latticeBudget <= 0) {
            return null;
        }

        if (inside && !faceVisible(eye, hitBlock, clickFace)) {
            return null;
        }

        if (sample.subtract(eye).lengthSqr() < 1.0E-6) {
            return null;
        }

        if (eye.distanceToSqr(sample) > reachSq) {
            return null;
        }

        Vec3 hitVec = validateRay(eye, sample, hitBlock, clickFace, inside);
        if (hitVec != null && !(eye.distanceToSqr(hitVec) > reachSq)) {
            BlockHitResult hit = new BlockHitResult(hitVec, clickFace, hitBlock, inside);
            latticeBudget -= 3;
            BlockState result = simulate(hit, stack, yaw, pitch, target);
            if (result != null && statesMatch(desired, result)) {
                for (float dy : new float[]{-1.0F, 1.0F}) {
                    BlockState drifted = simulate(hit, stack, yaw + dy, pitch, target);
                    if (drifted == null || !statesMatch(desired, drifted)) {
                        return null;
                    }
                }

                return new PlacementSolver.Solution(hit, InteractionHand.MAIN_HAND, yaw, pitch, result, k, false);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static boolean confirmSent(PlacementSolver.Solution s, BlockPos target, BlockState desired, ItemStack stack) {
        if (s.anyRotation()) {
            return true;
        } else if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            RotationUtils rot = RotationUtils.getInstance();
            BlockState result = simulate(s.hit(), stack, rot.getServerYaw(), rot.getServerPitch(), target);
            return result != null && statesMatch(desired, result);
        } else {
            return false;
        }
    }

    public static PlacementSolver.Solution revalidate(
        PlacementSolver.Solution s, BlockPos target, BlockState desired, ItemStack stack, double reach, boolean sprinting
    ) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            Vec3 eye = MeteorClient.mc.player.getEyePosition();
            double reachSq = reach * reach;
            BlockHitResult h = s.hit();
            if (s.anyRotation()) {
                latticeBudget = 400;
                return tryAnyRotation(target, desired, stack, eye, reachSq, h.getBlockPos(), h.getDirection(), h.getLocation());
            }

            if (s.latticeK() == null) {
                return tryCandidate(target, desired, stack, eye, reachSq, h.getBlockPos(), h.getDirection(), h.getLocation(), h.isInside());
            }

            float realYaw = MeteorClient.mc.player.getYRot();
            latticeBudget = 800;

            for (int k : sprinting ? K_ORDER_SPRINT : K_ORDER) {
                for (float pitch : LATTICE_PITCHES) {
                    PlacementSolver.Solution r = tryLattice(
                        target,
                        desired,
                        stack,
                        eye,
                        reachSq,
                        h.getBlockPos(),
                        h.getDirection(),
                        h.getLocation(),
                        h.isInside(),
                        realYaw + k * 45.0F,
                        pitch,
                        k
                    );
                    if (r != null) {
                        return r;
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static PlacementSolver.Solution solveAgainstFace(BlockPos target, BlockState desired, ItemStack stack, Vec3 eye, double reachSq) {
        for (Direction face : Direction.values()) {
            BlockPos neighbor = target.relative(face);
            BlockState neighborState = MeteorClient.mc.level.getBlockState(neighbor);
            if (!neighborState.canBeReplaced()
                && !neighborState.getCollisionShape(MeteorClient.mc.level, neighbor).isEmpty()
                && !clickTriggersAction(neighborState.getBlock())) {
                PlacementSolver.Solution s = trySamples(target, desired, stack, eye, reachSq, neighbor, face.getOpposite(), false);
                if (s != null) {
                    return s;
                }
            }
        }

        return null;
    }

    private static PlacementSolver.Solution solveFaces(
        BlockPos target, BlockState desired, ItemStack stack, Vec3 eye, double reachSq, BlockPos hitBlock, boolean inside
    ) {
        for (Direction face : Direction.values()) {
            PlacementSolver.Solution s = trySamples(target, desired, stack, eye, reachSq, hitBlock, face, inside);
            if (s != null) {
                return s;
            }
        }

        return null;
    }

    private static PlacementSolver.Solution trySamples(
        BlockPos target, BlockState desired, ItemStack stack, Vec3 eye, double reachSq, BlockPos hitBlock, Direction clickFace, boolean inside
    ) {
        Vec3 faceCenter = Vec3.atCenterOf(hitBlock).add(normal(clickFace).scale(0.5));

        for (double u : SAMPLES) {
            for (double v : SAMPLES) {
                Vec3 sample = faceOffset(faceCenter, clickFace, u - 0.5, v - 0.5);
                PlacementSolver.Solution s = tryCandidate(target, desired, stack, eye, reachSq, hitBlock, clickFace, sample, inside);
                if (s != null) {
                    return s;
                }
            }
        }

        return null;
    }

    private static PlacementSolver.Solution tryCandidate(
        BlockPos target,
        BlockState desired,
        ItemStack stack,
        Vec3 eye,
        double reachSq,
        BlockPos hitBlock,
        Direction clickFace,
        Vec3 sample,
        boolean inside
    ) {
        if (inside && !faceVisible(eye, hitBlock, clickFace)) {
            return null;
        }

        if (sample.subtract(eye).lengthSqr() < 1.0E-6) {
            return null;
        }

        if (eye.distanceToSqr(sample) > reachSq) {
            return null;
        }

        Vec3 hitVec = validateRay(eye, sample, hitBlock, clickFace, inside);
        if (hitVec != null && !(eye.distanceToSqr(hitVec) > reachSq)) {
            float[] rot = RotationUtils.getRotationsTo(eye, hitVec);
            BlockHitResult hit = new BlockHitResult(hitVec, clickFace, hitBlock, inside);
            BlockState result = simulate(hit, stack, rot[0], rot[1], target);
            if (result != null && statesMatch(desired, result)) {
                float[] var14 = new float[]{-0.25F, 0.25F};
                int var15 = var14.length;
                int var16 = 0;

                while (var16 < var15) {
                    float d = var14[var16];
                    BlockState jitteredYaw = simulate(hit, stack, rot[0] + d, rot[1], target);
                    if (jitteredYaw != null && statesMatch(desired, jitteredYaw)) {
                        BlockState jitteredPitch = simulate(hit, stack, rot[0], Math.clamp(rot[1] + d, -90.0F, 90.0F), target);
                        if (jitteredPitch != null && statesMatch(desired, jitteredPitch)) {
                            var16++;
                            continue;
                        }

                        return null;
                    }

                    return null;
                }

                return new PlacementSolver.Solution(hit, InteractionHand.MAIN_HAND, rot[0], rot[1], result, null, false);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static Vec3 validateRay(Vec3 eye, Vec3 sample, BlockPos hitBlock, Direction clickFace, boolean inside) {
        if (inside) {
            return sample;
        } else {
            Vec3 dir = sample.subtract(eye);
            Vec3 end = eye.add(dir.scale(1.0 + 0.01 / dir.length()));
            BlockHitResult clip = MeteorClient.mc
                .level
                .clip(new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, MeteorClient.mc.player));
            if (clip.getType() != Type.BLOCK) {
                return null;
            } else {
                return clip.getBlockPos().equals(hitBlock) && clip.getDirection() == clickFace ? clip.getLocation() : null;
            }
        }
    }

    private static boolean faceVisible(Vec3 eye, BlockPos pos, Direction face) {
        return switch (face) {
            case DOWN -> eye.y < pos.getY();
            case UP -> eye.y > pos.getY() + 1.0;
            case NORTH -> eye.z < pos.getZ();
            case SOUTH -> eye.z > pos.getZ() + 1.0;
            case WEST -> eye.x < pos.getX();
            case EAST -> eye.x > pos.getX() + 1.0;
        };
    }

    private static Vec3 faceOffset(Vec3 center, Direction face, double du, double dv) {
        return switch (face.getAxis()) {
            case X -> center.add(0.0, dv, du);
            case Y -> center.add(du, 0.0, dv);
            case Z -> center.add(du, dv, 0.0);
        };
    }

    private static BlockState simulate(BlockHitResult hit, ItemStack stack, float yaw, float pitch, BlockPos target) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            PlacementContextSim ctx = new PlacementContextSim(MeteorClient.mc.player, InteractionHand.MAIN_HAND, stack, hit, yaw, pitch);
            if (!ctx.canPlace()) {
                return null;
            } else {
                return !ctx.getClickedPos().equals(target) ? null : blockItem.getBlock().getStateForPlacement(ctx);
            }
        } else {
            return null;
        }
    }

    private static boolean clickTriggersAction(net.minecraft.world.level.block.Block block) {
        return BlockUtils.isClickable(block)
            || block instanceof RespawnAnchorBlock
            || block instanceof DragonEggBlock
            || block instanceof CakeBlock
            || block instanceof FlowerPotBlock
            || block instanceof ComposterBlock;
    }

    public static boolean statesMatch(BlockState desired, BlockState actual) {
        if (desired.getBlock() != actual.getBlock()) {
            return false;
        }

        boolean faces = faceDefinedByPlacement(desired);

        for (Property<?> prop : desired.getProperties()) {
            String name = prop.getName();
            if (!IGNORED.contains(name) && (faces || !CONNECTION.contains(name)) && !desired.getValue(prop).equals(actual.getValue(prop))) {
                return false;
            }
        }

        return true;
    }

    public static boolean faceDefinedByPlacement(BlockState state) {
        return state.getBlock() instanceof MultifaceBlock;
    }

    private static Vec3 normal(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    public record Solution(BlockHitResult hit, InteractionHand hand, float yaw, float pitch, BlockState predicted, Integer latticeK, boolean anyRotation) {
    }
}
