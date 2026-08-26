package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.managers.SwapManager;
import bep.hax.util.PlacementUtils;
import bep.hax.util.RotationUtils;
import bep.hax.util.printer.AirPlaceExecutor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class GrimScaffold extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgSpeed = this.settings.createGroup("Speed");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<List<Block>> blocks = this.sgGeneral.add(new Builder().name("blocks").description("Selected blocks.").build());
    private final Setting<GrimScaffold.ListMode> blocksFilter = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("blocks-filter"))
                        .description("How to use the block list setting"))
                    .defaultValue(GrimScaffold.ListMode.Blacklist))
                .build()
        );
    private final Setting<Boolean> tower = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("tower")
                .description("Towers up while holding jump: vanilla jumps with the support placed at the apex. No velocity modification.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> onlyOnClick = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("only-on-click")
                .description("Only places blocks when holding right click.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> placeDelay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("place-delay")
                .description("Delay in ticks between placements.")
                .defaultValue(0)
                .min(0)
                .max(10)
                .build()
        );
    private final Setting<Boolean> autoSwitch = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-switch")
                .description("Silently holds a block slot on the server while placing (the visible hand never changes).")
                .defaultValue(true)
                .build()
        );
    private final Setting<GrimScaffold.RotationMode> rotationMode = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("rotation-mode"))
                        .description(
                            "None sends no rotation (2b2t accepts unaimed places). Simple snaps the silent aim in one tick; Precise eases into it like a real mouse turn."
                        ))
                    .defaultValue(GrimScaffold.RotationMode.None))
                .build()
        );
    private final Setting<AirPlaceExecutor.Method> airPlaceMethod = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("air-place-method"))
                        .description(
                            "Default: the ordinary vanilla interact, which 2b2t accepts. Grim: wrap it in the off-hand swap so the anticheat cannot see which item placed the block - only needed on servers that cancel air places."
                        ))
                    .defaultValue(AirPlaceExecutor.Method.Default))
                .build()
        );
    private final Setting<Double> extendDistance = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("extend-distance")
                .description("How far ahead to place blocks when moving.")
                .defaultValue(0.1)
                .min(0.0)
                .max(2.0)
                .sliderMax(2.0)
                .build()
        );
    private final Setting<Boolean> velocityPredict = this.sgSpeed
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("velocity-predict")
                .description("Predicts position based on velocity when falling fast.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> velocityMultiplier = this.sgSpeed
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("velocity-multiplier")
                .description("Multiplier for velocity prediction.")
                .defaultValue(1.0)
                .min(0.5)
                .max(5.0)
                .sliderMax(5.0)
                .visible(this.velocityPredict::get)
                .build()
        );
    private final Setting<Boolean> adaptiveSpeed = this.sgSpeed
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("adaptive-speed")
                .description("Places more blocks per tick when falling fast.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> maxBlocksPerTick = this.sgSpeed
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-blocks-per-tick")
                .description("Maximum blocks to place per tick when falling fast.")
                .defaultValue(1)
                .min(1)
                .max(5)
                .visible(this.adaptiveSpeed::get)
                .build()
        );
    private final Setting<Boolean> safeWalk = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("safewalk")
                .description("Prevents you from walking off edges.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> render = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render")
                .description("Renders blocks being placed.")
                .defaultValue(true)
                .build()
        );
    private final Setting<ShapeMode> shapeMode = this.sgRender
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                                .name("shape-mode"))
                            .description("How the shapes are rendered."))
                        .defaultValue(ShapeMode.Both))
                    .visible(this.render::get))
                .build()
        );
    private final Setting<SettingColor> sideColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("side-color")
                .description("Side color of rendered blocks.")
                .defaultValue(new SettingColor(197, 137, 232, 10))
                .visible(this.render::get)
                .build()
        );
    private final Setting<SettingColor> lineColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Line color of rendered blocks.")
                .defaultValue(new SettingColor(0, 1, 255))
                .visible(this.render::get)
                .build()
        );
    private static final double SCAFFOLD_TURN_SPEED = 120.0;
    private static final double SCAFFOLD_ALIGN_EPS = 3.0;
    private static final double REACH = 4.5;
    private static final int ALIGN_TIMEOUT = 6;
    private static final int PLACE_COOLDOWN = 6;
    private static final int WINDOW_TICKS = 6;
    private static final int WINDOW_MAX = 9;
    private static final long RENDER_FADE_NANOS = 400000000L;
    private final MutableBlockPos targetPos = new MutableBlockPos();
    private final Map<BlockPos, Long> renderedBlocks = new HashMap<>();
    private final Map<BlockPos, Integer> cooldown = new HashMap<>();
    private final ArrayDeque<Integer> window = new ArrayDeque<>();
    private int tick = 0;
    private int tickDelay = 0;
    private BlockPos aimPos = null;
    private BlockHitResult aimHit = null;
    private int alignTicks = 0;
    private final Color fadeSide = new Color();
    private final Color fadeLine = new Color();

    public GrimScaffold() {
        super(Bep.CATEGORY, "grim-scaffold", "Places blocks under you with Grim-compatible packets and rotations.");
    }

    @Override
    public void onActivate() {
        this.tick = 0;
        this.tickDelay = 0;
        this.renderedBlocks.clear();
        this.cooldown.clear();
        this.window.clear();
        this.dropAim();
    }

    @Override
    public void onDeactivate() {
        this.renderedBlocks.clear();
        this.cooldown.clear();
        this.window.clear();
        this.dropAim();
        RotationUtils.getInstance().clearRotations(this);
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            this.tick++;
            this.cooldown.values().removeIf(exp -> exp <= this.tick);
            boolean towering = this.tower.get() && this.shouldTower();
            if (this.onlyOnClick.get() && !this.mc.options.keyUse.isDown() && !towering) {
                this.dropAim();
            } else {
                this.tickDelay++;
                if (this.tickDelay >= this.placeDelay.get()) {
                    List<BlockPos> candidates = this.getPlacementPositions();
                    if (candidates.isEmpty()) {
                        this.dropAim();
                    } else {
                        FindItemResult blockItem = this.findBlock(candidates.get(0));
                        if (blockItem.found() && (this.autoSwitch.get() || blockItem.getHand() != null)) {
                            if (this.rotationMode.get() != GrimScaffold.RotationMode.None) {
                                BlockPos pos = candidates.get(0);
                                if (!pos.equals(this.aimPos)) {
                                    this.alignTicks = 0;
                                }

                                BlockHitResult hit = this.resolveHit(pos);
                                if (hit == null) {
                                    this.cooldown.put(pos, this.tick + 6);
                                    this.dropAim();
                                } else if (this.windowBudget() > 0) {
                                    if (this.rotationUnsafe()) {
                                        this.placeAt(pos, hit, blockItem);
                                        this.dropAim();
                                    } else {
                                        float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), hit.getLocation());
                                        boolean claimed = this.rotationMode.get() == GrimScaffold.RotationMode.Simple
                                            ? RotationUtils.getInstance().setRotationSilentDirect(this, 35, rotations[0], rotations[1], 120.0)
                                            : RotationUtils.getInstance().setRotationSilent(this, 35, rotations[0], rotations[1], 120.0);
                                        if (!claimed) {
                                            this.placeAt(pos, hit, blockItem);
                                            this.dropAim();
                                        } else {
                                            this.aimPos = pos.immutable();
                                            this.aimHit = hit;
                                        }
                                    }
                                }
                            } else {
                                this.dropAim();
                                int budget = Math.min(this.getBlocksPerTick(), this.windowBudget());
                                int placed = 0;

                                for (BlockPos pos : candidates) {
                                    if (placed >= budget) {
                                        break;
                                    }

                                    BlockHitResult hit = this.resolveHit(pos);
                                    if (hit == null) {
                                        this.cooldown.put(pos, this.tick + 6);
                                    } else {
                                        if (!this.placeAt(pos, hit, blockItem)) {
                                            break;
                                        }

                                        placed++;
                                    }
                                }
                            }
                        } else {
                            this.dropAim();
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTickPost(Post event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.aimHit != null && this.aimPos != null) {
                if (!this.canPlace(this.aimPos)) {
                    this.dropAim();
                } else {
                    RotationUtils rot = RotationUtils.getInstance();
                    boolean aligned = rot.isRotating() && rot.isAlignedFor(this, 3.0);
                    if (aligned || ++this.alignTicks > 6) {
                        FindItemResult blockItem = this.findBlock(this.aimPos);
                        if (!blockItem.found()) {
                            this.dropAim();
                        } else {
                            if (this.placeAt(this.aimPos, this.aimHit, blockItem)) {
                                this.dropAim();
                            }
                        }
                    }
                }
            }
        }
    }

    private void dropAim() {
        this.aimPos = null;
        this.aimHit = null;
        this.alignTicks = 0;
    }

    private BlockHitResult resolveHit(BlockPos pos) {
        BlockHitResult hit = PlacementUtils.getSupportHit(pos, 4.5);
        return hit != null ? hit : PlacementUtils.getAirPlaceHit(pos, 4.5);
    }

    private boolean placeAt(BlockPos pos, BlockHitResult hit, FindItemResult blockItem) {
        InteractionHand hand;
        if (blockItem.isOffhand()) {
            hand = InteractionHand.OFF_HAND;
        } else {
            if (!SwapManager.getInstance().hold(this, blockItem.slot(), 35, 2)) {
                return false;
            }

            hand = InteractionHand.MAIN_HAND;
        }

        BlockState predicted = this.predictedState(blockItem);
        if (hit.isInside() && hand == InteractionHand.OFF_HAND) {
            BlockHitResult wireHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), hit.getBlockPos(), false);
            AirPlaceExecutor.silentPlace(wireHit, pos, predicted, hand, true);
        } else {
            AirPlaceExecutor.place(hit, pos, hand, predicted, this.airPlaceMethod.get());
        }

        this.cooldown.put(pos.immutable(), this.tick + 6);
        this.window.addLast(this.tick);
        if (this.render.get()) {
            this.renderedBlocks.put(pos.immutable(), System.nanoTime());
        }

        this.tickDelay = 0;
        return true;
    }

    private BlockState predictedState(FindItemResult blockItem) {
        ItemStack stack = blockItem.isOffhand() ? this.mc.player.getOffhandItem() : this.mc.player.getInventory().getItem(blockItem.slot());
        return stack.getItem() instanceof BlockItem item ? item.getBlock().defaultBlockState() : null;
    }

    private boolean rotationUnsafe() {
        return !this.mc.player.isFallFlying() && !this.mc.player.isSwimming() && !this.mc.player.isAutoSpinAttack() && !this.mc.player.isPassenger()
            ? this.mc.player.isSprinting() && this.mc.player.onGround() && this.mc.options.keyJump.isDown()
            : true;
    }

    private int windowBudget() {
        while (!this.window.isEmpty() && this.window.peekFirst() <= this.tick - 6) {
            this.window.pollFirst();
        }

        return 9 - this.window.size();
    }

    private int getBlocksPerTick() {
        if (!this.adaptiveSpeed.get()) {
            return 1;
        } else {
            double velocity = Math.abs(this.mc.player.getDeltaMovement().y);
            if (velocity > 0.5) {
                return this.maxBlocksPerTick.get();
            } else {
                return velocity > 0.3 ? Math.min(2, this.maxBlocksPerTick.get()) : 1;
            }
        }
    }

    private List<BlockPos> getPlacementPositions() {
        List<BlockPos> positions = new ArrayList<>();
        Vec3 playerPos = this.mc.player.position();
        Vec3 velocity = this.mc.player.getDeltaMovement();
        Vec3 predictedPos = playerPos;
        if (this.velocityPredict.get() && Math.abs(velocity.y) > 0.1) {
            double multiplier = this.velocityMultiplier.get();
            predictedPos = playerPos.add(velocity.x * multiplier, velocity.y * multiplier, velocity.z * multiplier);
        }

        if (this.isMovingHorizontally() && this.extendDistance.get() > 0.0) {
            Vec3 moveVec = this.getMovementVector();
            predictedPos = predictedPos.add(moveVec.scale(this.extendDistance.get()));
        }

        this.targetPos.set(predictedPos.x, playerPos.y - 1.0, predictedPos.z);
        if (this.canPlace(this.targetPos)) {
            positions.add(this.targetPos.immutable());
        }

        if (Math.abs(velocity.y) > 0.4) {
            BlockPos aboveTarget = this.targetPos.above().immutable();
            if (this.canPlace(aboveTarget)) {
                positions.add(aboveTarget);
            }
        }

        return positions;
    }

    private boolean canPlace(BlockPos pos) {
        if (this.cooldown.containsKey(pos)) {
            return false;
        } else if (this.mc.level.isOutsideBuildHeight(pos)) {
            return false;
        } else if (!this.mc.level.getBlockState(pos).canBeReplaced()) {
            return false;
        } else {
            return this.mc.player.getBoundingBox().intersects(new AABB(pos))
                ? false
                : this.mc
                    .level
                    .getEntitiesOfClass(
                        Entity.class,
                        new AABB(pos),
                        e -> !(e instanceof ItemEntity)
                            && !(e instanceof ExperienceOrb)
                            && !(e instanceof Projectile)
                            && !(e instanceof EndCrystal)
                            && e != this.mc.player
                    )
                    .isEmpty();
        }
    }

    private Vec3 getMovementVector() {
        Vec3 velocity = Vec3.ZERO;
        float yaw = this.mc.player.getYRot();
        if (this.mc.options.keyUp.isDown()) {
            velocity = velocity.add(Vec3.directionFromRotation(0.0F, yaw));
        }

        if (this.mc.options.keyDown.isDown()) {
            velocity = velocity.add(Vec3.directionFromRotation(0.0F, yaw + 180.0F));
        }

        if (this.mc.options.keyLeft.isDown()) {
            velocity = velocity.add(Vec3.directionFromRotation(0.0F, yaw - 90.0F));
        }

        if (this.mc.options.keyRight.isDown()) {
            velocity = velocity.add(Vec3.directionFromRotation(0.0F, yaw + 90.0F));
        }

        return velocity.lengthSqr() > 0.0 ? velocity.normalize() : velocity;
    }

    private boolean isMovingHorizontally() {
        return this.mc.options.keyUp.isDown()
            || this.mc.options.keyDown.isDown()
            || this.mc.options.keyLeft.isDown()
            || this.mc.options.keyRight.isDown();
    }

    private boolean shouldTower() {
        return this.mc.options.keyJump.isDown() && !this.mc.options.keyShift.isDown() && !this.isMovingHorizontally();
    }

    private FindItemResult findBlock(BlockPos target) {
        return InvUtils.findInHotbar(
            itemStack -> {
                if (itemStack.getItem() instanceof BlockItem blockItem) {
                    Block var5 = blockItem.getBlock();
                    if (this.blocksFilter.get() == GrimScaffold.ListMode.Blacklist && this.blocks.get().contains(var5)) {
                        return false;
                    } else if (this.blocksFilter.get() == GrimScaffold.ListMode.Whitelist && !this.blocks.get().contains(var5)) {
                        return false;
                    } else {
                        return !Block.isShapeFullBlock(var5.defaultBlockState().getCollisionShape(this.mc.level, target))
                            ? false
                            : !(var5 instanceof FallingBlock) || !FallingBlock.isFree(this.mc.level.getBlockState(target.below()));
                    }
                } else {
                    return false;
                }
            }
        );
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.render.get() && !this.renderedBlocks.isEmpty()) {
            long now = System.nanoTime();
            this.renderedBlocks.values().removeIf(placed -> now - placed >= 400000000L);
            SettingColor side = this.sideColor.get();
            SettingColor line = this.lineColor.get();
            ShapeMode mode = this.shapeMode.get();

            for (Entry<BlockPos, Long> entry : this.renderedBlocks.entrySet()) {
                float alpha = 1.0F - (float)(now - entry.getValue()) / 4.0E8F;
                this.fadeSide.set(side.r, side.g, side.b, Math.round(side.a * alpha));
                this.fadeLine.set(line.r, line.g, line.b, Math.round(line.a * alpha));
                event.renderer.box(entry.getKey(), this.fadeSide, this.fadeLine, mode, 0);
            }
        }
    }

    public boolean isSafeWalking() {
        return this.isActive() && this.safeWalk.get();
    }

    public enum ListMode {
        Whitelist,
        Blacklist;
    }

    public enum RotationMode {
        None,
        Simple,
        Precise;
    }
}
