package bep.hax.util;

import java.util.Arrays;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class PlacementUtils {
    private static final List<Block> RESISTANT_BLOCKS = Arrays.asList(
        Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.ENDER_CHEST, Blocks.RESPAWN_ANCHOR, Blocks.ENCHANTING_TABLE, Blocks.ANVIL
    );

    public static FindItemResult findResistantBlock() {
        for (Block block : RESISTANT_BLOCKS) {
            FindItemResult result = InvUtils.findInHotbar(block.asItem());
            if (result.found()) {
                return result;
            }
        }

        return InvUtils.findInHotbar(itemStack -> false);
    }

    public static boolean placeBlock(BlockPos pos, boolean rotate, boolean swing, boolean strictDirection) {
        FindItemResult block = findResistantBlock();
        return !block.found() ? false : placeBlock(pos, block, rotate, swing, strictDirection);
    }

    public static boolean placeBlock(BlockPos pos, FindItemResult block, boolean rotate, boolean swing, boolean strictDirection) {
        if (block.found() && canPlace(pos, strictDirection)) {
            Direction side = getPlaceSide(pos);
            if (side == null) {
                return false;
            }

            BlockPos neighbor = pos.relative(side);
            Direction opposite = side.getOpposite();
            Vec3 hitPos = Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(opposite.getUnitVec3i()).scale(0.5));
            if (rotate) {
                Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos));
            }

            if (block.getHand() == null && !InvUtils.swap(block.slot(), false)) {
                return false;
            }

            BlockHitResult hitResult = new BlockHitResult(hitPos, opposite, neighbor, false);
            InteractionHand hand = block.getHand() != null ? block.getHand() : InteractionHand.MAIN_HAND;
            MeteorClient.mc.getConnection().send(new ServerboundUseItemOnPacket(hand, hitResult, 0));
            if (swing) {
                if (hand == InteractionHand.MAIN_HAND) {
                    MeteorClient.mc.player.swing(InteractionHand.MAIN_HAND);
                } else {
                    MeteorClient.mc.getConnection().send(new ServerboundSwingPacket(hand));
                }
            }

            return true;
        } else {
            return false;
        }
    }

    public static BlockHitResult getAirPlaceHit(BlockPos pos, double reach) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            Vec3 eye = MeteorClient.mc.player.getEyePosition();
            double minX = pos.getX();
            double minY = pos.getY();
            double minZ = pos.getZ();
            double maxX = minX + 1.0;
            double maxY = minY + 1.0;
            double maxZ = minZ + 1.0;
            double inset = 0.3;
            double reachSq = reach * reach;
            BlockHitResult best = null;
            double bestDist = Double.MAX_VALUE;

            for (Direction side : Direction.values()) {
                boolean visible;
                Vec3 point;
                switch (side) {
                    case DOWN:
                        visible = eye.y < minY;
                        point = new Vec3(clamp(eye.x, minX + inset, maxX - inset), minY, clamp(eye.z, minZ + inset, maxZ - inset));
                        break;
                    case UP:
                        visible = eye.y > maxY;
                        point = new Vec3(clamp(eye.x, minX + inset, maxX - inset), maxY, clamp(eye.z, minZ + inset, maxZ - inset));
                        break;
                    case NORTH:
                        visible = eye.z < minZ;
                        point = new Vec3(clamp(eye.x, minX + inset, maxX - inset), clamp(eye.y, minY + inset, maxY - inset), minZ);
                        break;
                    case SOUTH:
                        visible = eye.z > maxZ;
                        point = new Vec3(clamp(eye.x, minX + inset, maxX - inset), clamp(eye.y, minY + inset, maxY - inset), maxZ);
                        break;
                    case WEST:
                        visible = eye.x < minX;
                        point = new Vec3(minX, clamp(eye.y, minY + inset, maxY - inset), clamp(eye.z, minZ + inset, maxZ - inset));
                        break;
                    case EAST:
                        visible = eye.x > maxX;
                        point = new Vec3(maxX, clamp(eye.y, minY + inset, maxY - inset), clamp(eye.z, minZ + inset, maxZ - inset));
                        break;
                    default:
                        visible = false;
                        point = null;
                }

                if (visible) {
                    double dist = eye.distanceToSqr(point);
                    if (!(dist > reachSq) && dist < bestDist) {
                        bestDist = dist;
                        best = new BlockHitResult(point, side, pos, true);
                    }
                }
            }

            return best;
        } else {
            return null;
        }
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    public static BlockHitResult getSupportHit(BlockPos pos, double reach) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            Vec3 eye = MeteorClient.mc.player.getEyePosition();
            BlockHitResult best = null;
            double bestDist = Double.MAX_VALUE;

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                BlockState state = MeteorClient.mc.level.getBlockState(neighbor);
                if (!state.canBeReplaced() && !state.getCollisionShape(MeteorClient.mc.level, neighbor).isEmpty()) {
                    Direction side = dir.getOpposite();
                    Vec3 normal = Vec3.atLowerCornerOf(side.getUnitVec3i());
                    Vec3 hitVec = Vec3.atCenterOf(neighbor).add(normal.scale(0.5));
                    if (!(eye.subtract(hitVec).dot(normal) <= 0.0)) {
                        double dist = eye.distanceToSqr(hitVec);
                        if (!(dist > reach * reach) && dist < bestDist) {
                            bestDist = dist;
                            best = new BlockHitResult(hitVec, side, neighbor, false);
                        }
                    }
                }
            }

            return best;
        } else {
            return null;
        }
    }

    public static boolean canPlace(BlockPos pos, boolean strictDirection) {
        if (!MeteorClient.mc.level.getBlockState(pos).canBeReplaced()) {
            return false;
        }

        if (!MeteorClient.mc.level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), pos, CollisionContext.empty())) {
            return false;
        }

        AABB checkBox = AABB.unitCubeFromLowerCorner(Vec3.atCenterOf(pos));

        for (Entity entity : MeteorClient.mc.level.getEntities(null, checkBox)) {
            if (!entity.isSpectator() && entity.isAlive()) {
                return false;
            }
        }

        return !strictDirection || getPlaceSide(pos) != null;
    }

    public static Direction getPlaceSide(BlockPos pos) {
        if (!MeteorClient.mc.level.getBlockState(pos.below()).canBeReplaced()) {
            return Direction.DOWN;
        }

        for (Direction side : Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(side);
            if (!MeteorClient.mc.level.getBlockState(neighbor).canBeReplaced()) {
                return side;
            }
        }

        return !MeteorClient.mc.level.getBlockState(pos.above()).canBeReplaced() ? Direction.UP : null;
    }

    public static boolean isPhasing() {
        if (MeteorClient.mc.player == null) {
            return false;
        }

        AABB bb = MeteorClient.mc.player.getBoundingBox();
        int minX = Mth.floor(bb.minX);
        int maxX = Mth.floor(bb.maxX) + 1;
        int minY = Mth.floor(bb.minY);
        int maxY = Mth.floor(bb.maxY) + 1;
        int minZ = Mth.floor(bb.minZ);
        int maxZ = Mth.floor(bb.maxZ) + 1;

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!MeteorClient.mc.level.getBlockState(pos).getCollisionShape(MeteorClient.mc.level, pos).isEmpty()) {
                        AABB blockBox = new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0);
                        if (bb.intersects(blockBox)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public static int getEnderPearlSlot() {
        if (MeteorClient.mc.player == null) {
            return -1;
        }

        for (int i = 0; i < 45; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.ENDER_PEARL) {
                return i;
            }
        }

        return -1;
    }

    public static void clickSlot(int slot, ClickType actionType) {
        if (MeteorClient.mc.gameMode != null && MeteorClient.mc.player != null) {
            MeteorClient.mc.gameMode.handleInventoryMouseClick(0, slot, 0, actionType, MeteorClient.mc.player);
        }
    }
}
