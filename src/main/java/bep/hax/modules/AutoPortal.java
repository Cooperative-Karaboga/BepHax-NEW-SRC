package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.PlacementUtils;
import bep.hax.util.RotationUtils;
import bep.hax.util.printer.AirPlaceExecutor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class AutoPortal extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final List<BlockPos> waitingForBreak = new ArrayList<>();
    private final Setting<Integer> placeDelay = this.sgGeneral
        .add(
            new Builder()
                .name("place-delay")
                .description("Extra ticks to idle between placements. 0 places every tick.")
                .defaultValue(0)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Boolean> rotate = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("rotate")
                .description(
                    "Aim at each block before placing it. Obsidian is a full cube, so the look direction cannot change what gets placed - this only satisfies anticheats that demand line of sight, and it costs several ticks per block while the aim turns instead of one. Off is what makes the portal fast."
                )
                .defaultValue(false)
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
    private final Setting<Boolean> render = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render")
                .description("Renders the portal frame as it's being placed.")
                .defaultValue(true)
                .build()
        );
    private final Setting<ShapeMode> shapeMode = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("shape-mode"))
                        .description("How the box is rendered."))
                    .defaultValue(ShapeMode.Both))
                .build()
        );
    private final Setting<SettingColor> sideColor = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(100, 100, 255, 10)).build());
    private final Setting<SettingColor> lineColor = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(100, 100, 255, 255)).build());
    private static final double REACH = 4.5;
    private static final int PLACE_CONFIRM_TICKS = 5;
    private static final int MAX_LIGHT_ATTEMPTS = 10;
    private final List<BlockPos> portalBlocks = new ArrayList<>();
    private final List<BlockPos> interiorBlocks = new ArrayList<>();
    private final Map<BlockPos, Integer> placeCooldown = new HashMap<>();
    private int delay = 0;
    private int lightCooldown = 0;
    private int lightAttempts = 0;

    public AutoPortal() {
        super(Bep.HUNT_CATEGORY, "auto-portal", "For the Base Hunter who has places to be. By Stash Hunt Addon (Jeff)");
    }

    @Override
    public void onActivate() {
        int obsidianCount = 0;

        for (int i = 0; i < 36; i++) {
            if (this.mc.player.getInventory().getItem(i).getItem() == Items.OBSIDIAN) {
                obsidianCount += this.mc.player.getInventory().getItem(i).getCount();
            }
        }

        if (obsidianCount < 10) {
            this.error("Not enough obsidian to build the portal (need at least 10)!");
            this.toggle();
        } else {
            this.portalBlocks.clear();
            this.interiorBlocks.clear();
            this.placeCooldown.clear();
            this.waitingForBreak.clear();
            this.delay = 0;
            this.lightCooldown = 0;
            this.lightAttempts = 0;
            Direction forward = this.mc.player.getDirection();
            Direction right = forward.getClockWise();
            BlockPos standingPos = this.mc.player.blockPosition();
            BlockPos blockBelow = standingPos.below();
            double blockHeight = this.mc.level.getBlockState(blockBelow).getCollisionShape(this.mc.level, blockBelow).max(Axis.Y);
            if (blockHeight < 1.0) {
                standingPos = standingPos.above();
            }

            BlockPos base = standingPos.relative(forward, 2).relative(right, -1);
            int obsidianCheck = 0;
            List<BlockPos> checkPositions = List.of(
                base.relative(right, 1),
                base.relative(right, 2),
                base.relative(right, 0).above(1),
                base.relative(right, 0).above(2),
                base.relative(right, 0).above(3),
                base.relative(right, 3).above(1),
                base.relative(right, 3).above(2),
                base.relative(right, 3).above(3),
                base.relative(right, 1).above(4),
                base.relative(right, 2).above(4)
            );

            for (BlockPos checkPos : checkPositions) {
                if (this.mc.level.getBlockState(checkPos).getBlock().asItem() == Items.OBSIDIAN) {
                    obsidianCheck++;
                }
            }

            if (obsidianCheck >= checkPositions.size()) {
                this.error("A portal already exists here!");
                this.toggle();
            } else {
                this.portalBlocks.add(base.relative(right, 1));
                this.portalBlocks.add(base.relative(right, 2));

                for (int i = 1; i <= 3; i++) {
                    this.portalBlocks.add(base.relative(right, 0).above(i));
                }

                for (int i = 1; i <= 3; i++) {
                    this.portalBlocks.add(base.relative(right, 3).above(i));
                }

                this.portalBlocks.add(base.relative(right, 1).above(4));
                this.portalBlocks.add(base.relative(right, 2).above(4));

                for (int i = 1; i <= 3; i++) {
                    this.interiorBlocks.add(base.relative(right, 1).above(i));
                    this.interiorBlocks.add(base.relative(right, 2).above(i));
                }

                for (int i = 0; i < 9; i++) {
                    if (this.mc.player.getInventory().getItem(i).getItem() == Items.OBSIDIAN) {
                        this.mc.player.getInventory().setSelectedSlot(i);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        this.portalBlocks.clear();
        this.interiorBlocks.clear();
        this.placeCooldown.clear();
        this.waitingForBreak.clear();
        this.delay = 0;
        this.lightCooldown = 0;
        this.lightAttempts = 0;
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.player != null && this.mc.level != null && !this.portalBlocks.isEmpty()) {
            this.tickCooldowns();
            List<BlockPos> missing = this.getMissingBlocks();
            if (missing.isEmpty()) {
                this.tryFinishPortal();
            } else if (!this.hasObsidianSelected() && !this.selectObsidian()) {
                this.error("Ran out of obsidian!");
                this.toggle();
            } else if (this.hasObsidianSelected()) {
                this.delay++;
                if (this.delay >= this.placeDelay.get()) {
                    for (BlockPos pos : missing) {
                        if (!this.placeCooldown.containsKey(pos)) {
                            if (!this.mc.level.getBlockState(pos).canBeReplaced()) {
                                if (!this.waitingForBreak.contains(pos) && this.mc.gameMode != null) {
                                    this.mc.gameMode.startDestroyBlock(pos, Direction.UP);
                                    this.mc.player.swing(InteractionHand.MAIN_HAND);
                                    this.waitingForBreak.add(pos);
                                }
                            } else {
                                this.waitingForBreak.remove(pos);
                                BlockHitResult hit = PlacementUtils.getAirPlaceHit(pos, 4.5);
                                if (hit != null) {
                                    if (this.rotate.get()) {
                                        float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), hit.getLocation());
                                        RotationUtils.getInstance().setRotationSilent(rotations[0], rotations[1]);
                                        if (!RotationUtils.getInstance().isAligned()) {
                                            return;
                                        }
                                    }

                                    this.placeObsidian(hit);
                                    this.placeCooldown.put(pos, 5);
                                    this.delay = 0;
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void tickCooldowns() {
        Iterator<Entry<BlockPos, Integer>> it = this.placeCooldown.entrySet().iterator();

        while (it.hasNext()) {
            Entry<BlockPos, Integer> e = it.next();
            int v = e.getValue() - 1;
            if (v <= 0) {
                it.remove();
            } else {
                e.setValue(v);
            }
        }
    }

    private List<BlockPos> getMissingBlocks() {
        List<BlockPos> missing = new ArrayList<>();

        for (BlockPos pos : this.portalBlocks) {
            if (this.mc.level.getBlockState(pos).getBlock() != Blocks.OBSIDIAN) {
                missing.add(pos);
            }
        }

        return missing;
    }

    private boolean hasObsidianSelected() {
        return this.mc.player.getMainHandItem().getItem() == Items.OBSIDIAN;
    }

    private boolean selectObsidian() {
        for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getItem(i).getItem() == Items.OBSIDIAN) {
                this.mc.player.getInventory().setSelectedSlot(i);
                this.mc.gameMode.ensureHasSentCarriedItem();
                return true;
            }
        }

        return false;
    }

    private void tryFinishPortal() {
        if (this.isPortalLit()) {
            this.info("Portal complete. AutoPortal disabled.");
            this.toggle();
        } else if (this.lightCooldown > 0) {
            this.lightCooldown--;
        } else if (this.lightAttempts >= 10) {
            this.error("Failed to light the portal (interior may be obstructed).");
            this.toggle();
        } else {
            int slot = this.findFlintAndSteel();
            if (slot == -1) {
                this.error("No flint and steel to light the portal!");
                this.toggle();
            } else {
                this.mc.player.getInventory().setSelectedSlot(slot);
                this.mc.gameMode.ensureHasSentCarriedItem();
                if (this.mc.player.getMainHandItem().getItem() == Items.FLINT_AND_STEEL) {
                    BlockPos fireBlock = this.portalBlocks.get(0);
                    Vec3 hitVec = Vec3.atCenterOf(fireBlock).add(0.0, 0.5, 0.0);
                    float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), hitVec);
                    RotationUtils.getInstance().setRotationSilent(rotations[0], rotations[1]);
                    if (RotationUtils.getInstance().isAligned()) {
                        this.lightPortal(fireBlock, hitVec);
                        this.lightAttempts++;
                        this.lightCooldown = 5;
                    }
                }
            }
        }
    }

    private boolean isPortalLit() {
        for (BlockPos pos : this.interiorBlocks) {
            if (this.mc.level.getBlockState(pos).getBlock() == Blocks.NETHER_PORTAL) {
                return true;
            }
        }

        return false;
    }

    private int findFlintAndSteel() {
        for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getItem(i).getItem() == Items.FLINT_AND_STEEL) {
                return i;
            }
        }

        return -1;
    }

    private void placeObsidian(BlockHitResult hit) {
        AirPlaceExecutor.airPlace(hit, Blocks.OBSIDIAN.defaultBlockState(), this.airPlaceMethod.get());
    }

    private void lightPortal(BlockPos fireBlock, Vec3 hitVec) {
        BlockHitResult fireHit = new BlockHitResult(hitVec, Direction.UP, fireBlock, false);
        this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, fireHit);
        this.mc.player.swing(InteractionHand.MAIN_HAND);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.render.get()) {
            for (BlockPos pos : this.portalBlocks) {
                if (this.mc.level == null || this.mc.level.getBlockState(pos).getBlock() != Blocks.OBSIDIAN) {
                    event.renderer.box(pos, this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
                }
            }
        }
    }
}
