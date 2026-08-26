package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.accessor.InputAccessor;
import bep.hax.managers.SwapManager;
import bep.hax.util.RotationUtils;
import bep.hax.util.printer.AirPlaceExecutor;
import bep.hax.util.printer.PlacementSolver;
import bep.hax.util.printer.PrinterRegion;
import bep.hax.util.printer.SchematicAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class Printer extends Module {
    private static final boolean LITEMATICA = FabricLoader.getInstance().isModLoaded("litematica");
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgTiming = this.settings.createGroup("Timing");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Printer.Mode> mode = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("mode"))
                        .description("Auto: real face when trivial, else air-place. AirPlace: always air-place. Legit: only against real faces."))
                    .defaultValue(Printer.Mode.Auto))
                .build()
        );
    private final Setting<Printer.OnMove> onMove = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("on-move"))
                        .description(
                            "Free: keep printing while you move - camera and movement stay fully yours; the server-side look is snapped to a 45-degree offset of your real yaw, which keeps your movement exact in Grim's simulation. Pause: stop placing while you move. Sync: keep placing but steer your movement by the spoofed aim."
                        ))
                    .defaultValue(Printer.OnMove.Free))
                .build()
        );
    private final Setting<Double> range = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("range")
                .description("Max placement distance from your eyes. Keep at or below 4.5 to satisfy Grim's reach check.")
                .defaultValue(4.5)
                .min(1.0)
                .max(6.0)
                .sliderMax(6.0)
                .build()
        );
    private final Setting<Boolean> onlySelected = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("only-selected-placement")
                .description("Build only the selected Litematica placement. Off: build every enabled placement in the schematic world.")
                .defaultValue(true)
                .build()
        );
    private final Setting<AirPlaceExecutor.Method> airPlaceMethod = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("air-place-method"))
                        .description(
                            "Default: the ordinary vanilla interact, which 2b2t accepts. Grim: wrap it in the off-hand swap so the anticheat cannot see which item placed the block - only needed on servers that cancel air places."
                        ))
                    .defaultValue(AirPlaceExecutor.Method.Default))
                .build()
        );
    private final Setting<Boolean> pauseOnUse = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("pause-on-use")
                .description("Pause printing while you are using an item (eating, drawing a bow, etc.).")
                .defaultValue(true)
                .build()
        );
    private final Setting<List<Block>> ignoredBlocks = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                .name("ignored-blocks")
                .description(
                    "Schematic blocks the printer never places. Defaults to creative-only and survival-unobtainable blocks (end portal frames, spawners, infested blocks, ...)."
                )
                .defaultValue(
                    Blocks.END_PORTAL_FRAME,
                    Blocks.END_PORTAL,
                    Blocks.NETHER_PORTAL,
                    Blocks.END_GATEWAY,
                    Blocks.BEDROCK,
                    Blocks.BARRIER,
                    Blocks.LIGHT,
                    Blocks.COMMAND_BLOCK,
                    Blocks.CHAIN_COMMAND_BLOCK,
                    Blocks.REPEATING_COMMAND_BLOCK,
                    Blocks.STRUCTURE_BLOCK,
                    Blocks.STRUCTURE_VOID,
                    Blocks.JIGSAW,
                    Blocks.TEST_BLOCK,
                    Blocks.TEST_INSTANCE_BLOCK,
                    Blocks.SPAWNER,
                    Blocks.TRIAL_SPAWNER,
                    Blocks.VAULT,
                    Blocks.REINFORCED_DEEPSLATE,
                    Blocks.BUDDING_AMETHYST,
                    Blocks.SUSPICIOUS_SAND,
                    Blocks.SUSPICIOUS_GRAVEL,
                    Blocks.INFESTED_STONE,
                    Blocks.INFESTED_COBBLESTONE,
                    Blocks.INFESTED_STONE_BRICKS,
                    Blocks.INFESTED_MOSSY_STONE_BRICKS,
                    Blocks.INFESTED_CRACKED_STONE_BRICKS,
                    Blocks.INFESTED_CHISELED_STONE_BRICKS,
                    Blocks.INFESTED_DEEPSLATE,
                    Blocks.PETRIFIED_OAK_SLAB,
                    Blocks.FARMLAND,
                    Blocks.DIRT_PATH,
                    Blocks.FROSTED_ICE,
                    Blocks.CHORUS_PLANT
                )
                .build()
        );
    private final Setting<Boolean> debug = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug")
                .description(
                    "Log placement decisions to chat: chosen targets, every abandon with its reason, and why the printer is idle when it stops placing."
                )
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> placeDelay = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("place-delay")
                .description("Extra ticks to idle between placements. 0 places every tick, which is what a held right-click does.")
                .defaultValue(0)
                .min(0)
                .max(20)
                .build()
        );
    private final Setting<Integer> rotationHold = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("rotation-hold")
                .description(
                    "Ticks to keep aiming at a block after placing it, so Grim's post-flying line-of-sight check passes. Raise this if blocks fail or place in the wrong spot when going fast."
                )
                .defaultValue(2)
                .min(0)
                .max(10)
                .build()
        );
    private final Setting<Integer> replaceCooldown = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("replace-cooldown")
                .description(
                    "Ticks to wait before a placed position is re-checked. Raise this on high-ping servers (2b2t) so blocks are not placed twice before the server confirms them."
                )
                .defaultValue(20)
                .min(1)
                .max(100)
                .build()
        );
    private final Setting<Double> turnSpeed = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("turn-speed")
                .description("Degrees per tick the silent aim may move. Lower looks more human but places slower.")
                .defaultValue(180.0)
                .min(20.0)
                .max(360.0)
                .sliderMax(360.0)
                .build()
        );
    private final Setting<Boolean> snapRotation = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("snap-rotation")
                .description(
                    "Turn the whole way to a block in one tick (still capped by turn-speed) instead of easing in over three to five. The eased curve only exists to avoid Grim's DuplicateRotPlace, which is experimental and not enforced on 2b2t - this is most of the cost of every block that needs an aim."
                )
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> alignTolerance = this.sgTiming
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("align-tolerance")
                .description("How closely the aim must match the target before placing (degrees).")
                .defaultValue(3.0)
                .min(0.1)
                .max(15.0)
                .sliderMax(15.0)
                .build()
        );
    private final Setting<Boolean> render = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render")
                .description("Render blocks queued for placement.")
                .defaultValue(true)
                .build()
        );
    private final Setting<ShapeMode> shapeMode = this.sgRender
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("shape-mode")).description("How the shapes are rendered.")).defaultValue(ShapeMode.Both))
                    .visible(this.render::get))
                .build()
        );
    private final Setting<SettingColor> sideColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("side-color")
                .description("Side color of queued blocks.")
                .defaultValue(new SettingColor(45, 225, 150, 40))
                .visible(this.render::get)
                .build()
        );
    private final Setting<SettingColor> lineColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Line color of queued blocks.")
                .defaultValue(new SettingColor(45, 225, 150, 255))
                .visible(this.render::get)
                .build()
        );
    private final Setting<SettingColor> targetColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("target-color")
                .description("Color of the block currently being placed.")
                .defaultValue(new SettingColor(255, 200, 0, 255))
                .visible(this.render::get)
                .build()
        );
    private static final int ALIGN_TIMEOUT = 40;
    private static final IntegerProperty[] COUNT_PROPERTIES = new IntegerProperty[]{
        BlockStateProperties.LAYERS, BlockStateProperties.CANDLES, BlockStateProperties.PICKLES, BlockStateProperties.EGGS, BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.SEGMENT_AMOUNT
    };
    private Printer.Phase phase = Printer.Phase.SCAN;
    private BlockPos target;
    private BlockState targetState;
    private PlacementSolver.Solution solution;
    private int timer;
    private int alignTicks;
    private long tick;
    private BlockPos lastAbandoned;
    private int repeatAbandons;
    private long lastMissingWarnTick = -1000L;
    private String lastDebugMsg;
    private long lastDebugTick;
    private long lastIdleLogTick;
    private static final int WATCHDOG_TICKS = 200;
    private long lastProgressTick;
    private boolean sawWork;
    private int consecutiveResets;
    private final Map<BlockPos, Long> cooldown = new HashMap<>();
    private final List<BlockPos> preview = new ArrayList<>();
    private List<PrinterRegion> regions;
    private static final int T_COOLED = 0;
    private static final int T_OBSTRUCTED = 1;
    private static final int T_GONE = 2;

    public Printer() {
        super(Bep.CATEGORY, "printer", "Automatically builds the selected Litematica schematic. Grim-safe placement with correct block orientation.");
    }

    @Override
    public void onActivate() {
        if (!LITEMATICA) {
            this.error("Litematica is not installed - the Printer needs Litematica + malilib.");
            this.toggle();
        } else {
            this.reset();
        }
    }

    @Override
    public void onDeactivate() {
        this.reset();
        RotationUtils.getInstance().clearRotations(this);
    }

    private void reset() {
        this.phase = Printer.Phase.SCAN;
        this.target = null;
        this.targetState = null;
        this.solution = null;
        this.timer = 0;
        this.alignTicks = 0;
        this.cooldown.clear();
        this.preview.clear();
        this.lastAbandoned = null;
        this.repeatAbandons = 0;
        this.regions = null;
        this.lastDebugMsg = null;
        this.lastProgressTick = this.tick;
        this.sawWork = false;
        this.consecutiveResets = 0;
    }

    private void debug(String msg) {
        if (this.debug.get()) {
            if (!msg.equals(this.lastDebugMsg) || this.tick - this.lastDebugTick >= 20L) {
                this.lastDebugMsg = msg;
                this.lastDebugTick = this.tick;
                this.info("(highlight)[debug](default) " + msg);
            }
        }
    }

    @EventHandler
    private void onTickPre(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (!LITEMATICA) {
                this.toggle();
            } else {
                this.tick++;
                this.cooldown.values().removeIf(exp -> exp <= this.tick);
                if (!SchematicAccess.hasActivePlacement()) {
                    this.preview.clear();
                    this.regions = null;
                    this.abandonTarget("no active placement");
                    this.debug("idle: no schematic placement selected/enabled in Litematica");
                } else {
                    this.regions = this.onlySelected.get() ? SchematicAccess.getSelectedRegions() : null;
                    if ((this.phase == Printer.Phase.ALIGN || this.phase == Printer.Phase.HOLD) && (this.solution == null || this.target == null)) {
                        this.phase = Printer.Phase.SCAN;
                    }

                    if (this.sawWork && this.tick - this.lastProgressTick >= 200L) {
                        this.abandonTarget("watchdog reset");
                        this.cooldown.clear();
                        RotationUtils.getInstance().clearRotations(this);
                        SwapManager.getInstance().releaseNow(this);
                        this.phase = Printer.Phase.SCAN;
                        this.lastAbandoned = null;
                        this.repeatAbandons = 0;
                        this.lastProgressTick = this.tick;
                        this.sawWork = false;
                        String msg = "no placement for 10s despite pending work - printer state reset";
                        if (this.consecutiveResets++ == 0) {
                            this.info(msg);
                        } else {
                            this.debug(msg);
                        }
                    }

                    RotationUtils rotation = RotationUtils.getInstance();
                    if (this.solution == null && rotation.isOwner(this) && rotation.isOffsetRotation()) {
                        rotation.clearRotations(this);
                    }

                    switch (this.phase) {
                        case SCAN:
                            if (!this.holdFire()) {
                                this.acquireTarget();
                            } else {
                                this.debug("paused: " + this.holdFireReason());
                            }
                            break;
                        case ALIGN:
                            if (this.holdFire()) {
                                this.abandonTarget(this.holdFireReason());
                                return;
                            }

                            if (++this.alignTicks > 40) {
                                this.abandonTarget(20, "align timeout (aim or slot held by another module?)");
                                return;
                            }

                            if (!this.inBuildSet(this.target)) {
                                this.abandonTarget("left the build set");
                                return;
                            }

                            if (!this.solution.anyRotation() && this.rotationUnsafe()) {
                                this.abandonTarget("rotation unsafe (gliding/swimming/riding/sprint-jump)");
                                return;
                            }

                            if (this.onMove.get() == Printer.OnMove.Free
                                && !this.solution.anyRotation()
                                && this.solution.latticeK() == null
                                && this.hasMovementInput()) {
                                this.abandonTarget("movement started, re-solving on lattice");
                                return;
                            }

                            ItemStack required = SchematicAccess.getRequiredItem(this.targetState, this.target);
                            if (required == null || required.isEmpty() || !InvUtils.findInHotbar(required.getItem()).found()) {
                                this.warnMissing(required);
                                this.abandonTarget(20, "material no longer in hotbar");
                                return;
                            }

                            if (!this.selectItem(required)) {
                                this.debug("align: swap hold refused - retrying");
                                return;
                            }

                            PlacementSolver.Solution s = PlacementSolver.revalidate(
                                this.solution, this.target, this.targetState, required, this.range.get(), this.mc.player.isSprinting()
                            );
                            if (s == null) {
                                this.abandonTarget(10, "revalidate failed (drifted out of reach/occluded)");
                                return;
                            }

                            this.solution = s;
                            this.assertAim();
                            break;
                        case HOLD:
                            if (!this.solution.anyRotation() && this.solution.latticeK() == null) {
                                float[] rot = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), this.solution.hit().getLocation());
                                this.solution = new PlacementSolver.Solution(
                                    this.solution.hit(), this.solution.hand(), rot[0], rot[1], this.solution.predicted(), null, false
                                );
                            }

                            this.assertAim();
                            break;
                        case DELAY:
                            if (--this.timer <= 0) {
                                this.phase = Printer.Phase.SCAN;
                                if (!this.holdFire()) {
                                    this.acquireTarget();
                                } else {
                                    this.debug("paused: " + this.holdFireReason());
                                }
                            }
                    }
                }
            }
        }
    }

    private boolean holdFire() {
        return this.pauseOnUse.get() && this.mc.player.isUsingItem() ? true : this.onMove.get() == Printer.OnMove.Pause && this.hasMovementInput();
    }

    private String holdFireReason() {
        return this.pauseOnUse.get() && this.mc.player.isUsingItem() ? "using an item (pause-on-use)" : "moving (Pause mode)";
    }

    private boolean rotationUnsafe() {
        if (this.mc.player.isFallFlying() || this.mc.player.isSwimming() || this.mc.player.isAutoSpinAttack() || this.mc.player.isPassenger()) {
            return true;
        } else {
            return this.mc.player.isSprinting() && this.mc.player.onGround() && this.mc.options.keyJump.isDown()
                ? true
                : this.onMove.get() == Printer.OnMove.Pause && this.hasMovementInput();
        }
    }

    private boolean hasMovementInput() {
        if (!this.mc.options.keyUp.isDown()
            && !this.mc.options.keyDown.isDown()
            && !this.mc.options.keyLeft.isDown()
            && !this.mc.options.keyRight.isDown()) {
            return !(this.mc.player.input instanceof InputAccessor in)
                ? false
                : Math.abs(in.getMovementForward()) > 1.0E-4F || Math.abs(in.getMovementSideways()) > 1.0E-4F;
        } else {
            return true;
        }
    }

    private void assertAim() {
        if (!this.solution.anyRotation()) {
            RotationUtils rot = RotationUtils.getInstance();
            boolean ok;
            if (this.solution.latticeK() != null) {
                ok = rot.setRotationOffset(this, 35, this.solution.latticeK(), this.solution.pitch());
            } else {
                boolean sync = this.onMove.get() == Printer.OnMove.Sync || this.phase == Printer.Phase.HOLD && this.hasMovementInput();
                if (sync) {
                    ok = rot.setRotationFull(this, 35, this.solution.yaw(), this.solution.pitch(), this.turnSpeed.get());
                } else if (this.snapRotation.get()) {
                    ok = rot.setRotationSilentDirect(this, 35, this.solution.yaw(), this.solution.pitch(), this.turnSpeed.get());
                } else {
                    ok = rot.setRotationSilent(this, 35, this.solution.yaw(), this.solution.pitch(), this.turnSpeed.get());
                }
            }

            if (!ok) {
                this.debug("aim claim refused (rotation owned by a higher-priority module)");
            }
        }
    }

    private void abandonTarget(String reason) {
        this.abandonTarget(0, reason);
    }

    private void abandonTarget(int cooldownTicks, String reason) {
        if (this.target != null) {
            int cd = cooldownTicks;
            if (this.target.equals(this.lastAbandoned)) {
                if (++this.repeatAbandons >= 3) {
                    cd = Math.max(cd, 20);
                }
            } else {
                this.lastAbandoned = this.target.immutable();
                this.repeatAbandons = 1;
            }

            if (cd > 0) {
                this.cooldown.put(this.target.immutable(), this.tick + cd);
            }

            this.debug("abandon " + this.target.toShortString() + ": " + reason + (cd > 0 ? " (retry in " + cd + "t)" : ""));
        }

        if (this.solution != null) {
            RotationUtils.getInstance().clearRotations(this);
        }

        this.solution = null;
        this.target = null;
        this.targetState = null;
        this.alignTicks = 0;
        if (this.phase == Printer.Phase.ALIGN || this.phase == Printer.Phase.HOLD) {
            this.phase = Printer.Phase.SCAN;
        }
    }

    @EventHandler
    private void onTickPost(Post event) {
        if (this.mc.player != null && this.mc.level != null && this.solution != null && this.target != null) {
            if (this.phase == Printer.Phase.ALIGN) {
                if (this.holdFire()) {
                    this.abandonTarget(this.holdFireReason());
                    return;
                }

                if (!this.inBuildSet(this.target)) {
                    this.abandonTarget("left the build set");
                    return;
                }

                if (!this.solution.anyRotation() && this.rotationUnsafe()) {
                    this.abandonTarget("rotation unsafe (gliding/swimming/riding/sprint-jump)");
                    return;
                }

                if (this.solution.anyRotation()) {
                    AirPlaceExecutor.airPlace(this.solution.hit(), this.solution.predicted(), this.airPlaceMethod.get());
                } else {
                    RotationUtils rot = RotationUtils.getInstance();
                    if (!rot.isRotating() || !rot.isAlignedFor(this, this.alignTolerance.get())) {
                        this.debug("waiting for aim (rotating=" + rot.isRotating() + ", owned=" + rot.isOwner(this) + ")");
                        return;
                    }

                    if (this.solution.latticeK() != null && this.mc.player.isSprinting() && Math.abs(this.solution.latticeK()) > 1) {
                        this.abandonTarget("sprint started outside lattice window");
                        return;
                    }

                    ItemStack required = SchematicAccess.getRequiredItem(this.targetState, this.target);
                    if (required == null || required.isEmpty()) {
                        this.abandonTarget(20, "no required item resolved");
                        return;
                    }

                    if (!PlacementSolver.confirmSent(this.solution, this.target, this.targetState, required)) {
                        this.debug("sent-rotation simulation mismatch - deferring click");
                        return;
                    }

                    if (this.solution.latticeK() != null) {
                        AirPlaceExecutor.airPlace(this.solution.hit(), this.solution.predicted(), this.airPlaceMethod.get());
                    } else {
                        AirPlaceExecutor.place(this.solution.hit(), this.target, this.solution.hand(), this.solution.predicted(), this.airPlaceMethod.get());
                    }
                }

                int hold = !this.solution.anyRotation() && this.solution.latticeK() == null ? this.rotationHold.get() : 0;
                this.debug("placed " + this.target.toShortString() + (hold > 0 ? " (holding aim " + hold + "t)" : ""));
                if (hold > 0) {
                    this.preview.remove(this.target);
                    this.phase = Printer.Phase.HOLD;
                    this.timer = hold;
                    return;
                }

                this.completePlacement();
            } else if (this.phase == Printer.Phase.HOLD) {
                if (this.timer-- > 0) {
                    return;
                }

                this.completePlacement();
            }
        }
    }

    private void completePlacement() {
        this.lastAbandoned = null;
        this.repeatAbandons = 0;
        this.lastProgressTick = this.tick;
        this.consecutiveResets = 0;
        this.cooldown.put(this.target.immutable(), this.tick + this.replaceCooldown.get().intValue());
        this.preview.remove(this.target);
        this.finishTarget();
    }

    private void acquireTarget() {
        boolean allowFace = this.mode.get() != Printer.Mode.AirPlace;
        boolean allowAir = this.mode.get() != Printer.Mode.Legit;
        int[] tally = new int[3];
        int unsolvable = 0;
        int noMaterial = 0;
        List<BlockPos> candidates = this.scan();
        this.sawWork = !candidates.isEmpty();

        for (BlockPos c : candidates) {
            BlockState ts = this.candidateState(c, tally);
            if (ts != null) {
                ItemStack required = SchematicAccess.getRequiredItem(ts, c);
                if (!this.hasMaterial(c, required)) {
                    noMaterial++;
                } else {
                    PlacementSolver.Solution s = allowAir ? PlacementSolver.solveAnyRotation(c, ts, required, this.range.get()) : null;
                    if (s == null && !this.rotationUnsafe()) {
                        boolean lattice = this.onMove.get() == Printer.OnMove.Free && this.hasMovementInput();
                        s = lattice
                            ? PlacementSolver.solveMoving(c, ts, required, this.range.get(), allowFace, allowAir, this.mc.player.isSprinting())
                            : PlacementSolver.solve(c, ts, required, this.range.get(), allowFace, allowAir);
                    }

                    if (s != null) {
                        if (this.selectItem(required)) {
                            this.target = c;
                            this.targetState = ts;
                            this.solution = s;
                            this.phase = Printer.Phase.ALIGN;
                            this.alignTicks = 0;
                            this.assertAim();
                            this.debug("target " + c.toShortString() + " -> " + describeSolution(s));
                            return;
                        }

                        this.debug("swap hold refused (conflict tick or higher-priority session) - retrying next tick");
                        break;
                    }

                    this.cooldown.put(c, this.tick + 20L);
                    unsolvable++;
                }
            }
        }

        this.target = null;
        if (this.tick - this.lastIdleLogTick >= 20L) {
            this.lastIdleLogTick = this.tick;
            if (candidates.isEmpty()) {
                this.debug(
                    this.regions != null && this.regions.isEmpty()
                        ? "idle: selected placement has no enabled sub-regions"
                        : "idle: no mismatched blocks in range (finished, out of reach, or hidden by render layers)"
                );
            } else {
                this.debug(
                    "idle: "
                        + candidates.size()
                        + " candidates ("
                        + tally[0]
                        + " cooling down, "
                        + tally[1]
                        + " blocked by an entity, "
                        + tally[2]
                        + " gone/occupied, "
                        + unsolvable
                        + " unsolvable from here, "
                        + noMaterial
                        + " missing material)"
                );
            }
        }
    }

    private static String describeSolution(PlacementSolver.Solution s) {
        if (s.anyRotation()) {
            return "any-rotation";
        } else {
            return s.latticeK() != null ? "lattice k=" + s.latticeK() : String.format("aim %.1f/%.1f", s.yaw(), s.pitch());
        }
    }

    private BlockState candidateState(BlockPos c, int[] tally) {
        if (this.cooldown.containsKey(c)) {
            tally[0]++;
            return null;
        }

        BlockState ts = SchematicAccess.getTargetState(c);
        if (ts != null && !ts.isAir()) {
            if (!this.mc.level.isUnobstructed(ts, c, CollisionContext.empty())) {
                this.cooldown.put(c, this.tick + 10L);
                tally[1]++;
                return null;
            } else {
                BlockState ws = this.mc.level.getBlockState(c);
                if (!ws.canBeReplaced() && !isMergeCandidate(ws, ts)) {
                    this.cooldown.put(c, this.tick + 10L);
                    tally[2]++;
                    return null;
                } else if (PlacementSolver.statesMatch(ts, ws)) {
                    this.cooldown.put(c, this.tick + 10L);
                    tally[2]++;
                    return null;
                } else {
                    return ts;
                }
            }
        } else {
            this.cooldown.put(c, this.tick + 10L);
            tally[2]++;
            return null;
        }
    }

    private boolean hasMaterial(BlockPos c, ItemStack required) {
        if (required != null && !required.isEmpty() && InvUtils.findInHotbar(required.getItem()).found()) {
            return true;
        }

        this.cooldown.put(c, this.tick + 20L);
        this.warnMissing(required);
        return false;
    }

    private void finishTarget() {
        this.phase = this.placeDelay.get() > 0 ? Printer.Phase.DELAY : Printer.Phase.SCAN;
        this.timer = this.placeDelay.get();
        this.alignTicks = 0;
        this.solution = null;
        this.target = null;
        this.targetState = null;
    }

    private boolean selectItem(ItemStack required) {
        if (required != null && !required.isEmpty()) {
            FindItemResult res = InvUtils.findInHotbar(required.getItem());
            return !res.found() ? false : SwapManager.getInstance().hold(this, res.slot(), 35, 4);
        } else {
            return false;
        }
    }

    private void warnMissing(ItemStack required) {
        if (this.tick - this.lastMissingWarnTick >= 100L) {
            if (required == null || required.isEmpty() || !InvUtils.findInHotbar(required.getItem()).found()) {
                this.info("Missing material in hotbar: " + (required != null && !required.isEmpty() ? required.getHoverName().getString() : "unknown item"));
                this.lastMissingWarnTick = this.tick;
            }
        }
    }

    private List<BlockPos> scan() {
        this.preview.clear();
        List<BlockPos> out = new ArrayList<>();
        Vec3 eye = this.mc.player.getEyePosition();
        BlockPos origin = BlockPos.containing(eye);
        int r = (int)Math.ceil(this.range.get()) + 1;
        double reachSq = this.range.get() * this.range.get();
        MutableBlockPos pos = new MutableBlockPos();
        Set<Block> ignored = new HashSet<>(this.ignoredBlocks.get());

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (this.inBuildSet(pos)) {
                        BlockState ts = SchematicAccess.getTargetState(pos);
                        if (ts != null && !ts.isAir() && !ignored.contains(ts.getBlock())) {
                            BlockState ws = this.mc.level.getBlockState(pos);
                            if ((ws.canBeReplaced() || isMergeCandidate(ws, ts))
                                && !PlacementSolver.statesMatch(ts, ws)
                                && !(eye.distanceToSqr(Vec3.atCenterOf(pos)) > reachSq)) {
                                out.add(pos.immutable());
                            }
                        }
                    }
                }
            }
        }

        out.sort(Comparator.comparingDouble(p -> eye.distanceToSqr(Vec3.atCenterOf(p))));
        if (this.render.get()) {
            this.preview.addAll(out);
        }

        return out;
    }

    private boolean inBuildSet(BlockPos pos) {
        if (!SchematicAccess.isWithinRenderLayers(pos)) {
            return false;
        }

        if (this.regions == null) {
            return true;
        }

        for (PrinterRegion r : this.regions) {
            if (r.contains(pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isMergeCandidate(BlockState ws, BlockState ts) {
        if (ws.getBlock() != ts.getBlock()) {
            return false;
        }

        if (!ws.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            for (IntegerProperty p : COUNT_PROPERTIES) {
                if (ws.hasProperty(p)) {
                    return ts.getValue(p) > ws.getValue(p);
                }
            }

            if (PlacementSolver.faceDefinedByPlacement(ts)) {
                boolean missing = false;

                for (Direction d : Direction.values()) {
                    BooleanProperty p = MultifaceBlock.getFaceProperty(d);
                    if (p != null && ws.hasProperty(p) && ts.hasProperty(p)) {
                        boolean have = ws.getValue(p);
                        boolean want = ts.getValue(p);
                        if (have && !want) {
                            return false;
                        }

                        if (!have && want) {
                            missing = true;
                        }
                    }
                }

                return missing;
            } else {
                return false;
            }
        } else {
            return ts.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE && ws.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.render.get()) {
            for (BlockPos p : this.preview) {
                event.renderer.box(p, this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
            }

            if (this.target != null) {
                event.renderer.box(this.target, this.targetColor.get(), this.targetColor.get(), this.shapeMode.get(), 0);
            }
        }
    }

    public enum Mode {
        Auto,
        AirPlace,
        Legit;
    }

    public enum OnMove {
        Free,
        Pause,
        Sync;
    }

    private enum Phase {
        SCAN,
        ALIGN,
        HOLD,
        DELAY;
    }
}
