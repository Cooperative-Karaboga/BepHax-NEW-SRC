package bep.hax.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalNear;
import bep.hax.Bep;
import bep.hax.accessor.InputAccessor;
import bep.hax.config.BepConfig;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.RotationUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StashMover extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgInput = this.settings.createGroup("Input");
    private final SettingGroup sgForward = this.settings.createGroup("Forward (In→Out)");
    private final SettingGroup sgPearl = this.settings.createGroup("Pearl Loading");
    private final SettingGroup sgGoBack = this.settings.createGroup("Go Back");
    private final SettingGroup sgResetPearl = this.settings.createGroup("Reset Pearl");
    private final SettingGroup sgDelays = this.settings.createGroup("Delays");
    private final Setting<Double> containerReach = this.sgGeneral
        .add(
            new Builder()
                .name("container-reach")
                .description("Maximum reach distance for opening containers")
                .defaultValue(4.0)
                .min(2.5)
                .max(5.0)
                .sliderRange(2.5, 5.0)
                .build()
        );
    private final Setting<Integer> areaRange = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("area-range")
                .description("Horizontal distance (blocks) from an input/output area that still counts as being 'at' it")
                .defaultValue(20)
                .min(0)
                .max(1000)
                .sliderRange(0, 64)
                .build()
        );
    private final Setting<Integer> areaRangeVertical = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("area-range-vertical")
                .description("Vertical distance (blocks) from an input/output area that still counts as being 'at' it")
                .defaultValue(20)
                .min(0)
                .max(1000)
                .sliderRange(0, 64)
                .build()
        );
    private final SettingGroup sgRendering = this.settings.createGroup("Rendering");
    private final Setting<Boolean> pauseOnLag = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("pause-on-lag")
                .description("Pause when server is lagging")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> maxRetries = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-retries")
                .description("Maximum retries for failed actions")
                .defaultValue(5)
                .min(1)
                .max(10)
                .build()
        );
    private final Setting<Boolean> debugMode = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug-mode")
                .description("Show debug messages and state transitions")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> onlyShulkers = this.sgInput
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("only-shulkers")
                .description("Only take shulker boxes from input chests")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> breakEmptyContainers = this.sgInput
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("break-empty")
                .description("Break empty containers after emptying them")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> fillEnderChest = this.sgInput
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("fill-enderchest")
                .description(
                    "Also stash items in the ender chest (carried alongside the inventory load). Pearl-forward only — when the forward method kills you, the ender chest is used automatically as the only safe carrier."
                )
                .defaultValue(true)
                .visible(() -> !this.killBasedForward())
                .build()
        );
    private final Setting<StashMover.TransportMethod> forwardMethod = this.sgForward
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("forward-method"))
                        .description("How to travel from the input area to the output area"))
                    .defaultValue(StashMover.TransportMethod.PEARL))
                .build()
        );
    private final Setting<String> forwardKillCommand = this.sgForward
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("forward-kill-command")
                .description("Command used to die when travelling Input→Output (Kill Command method)")
                .defaultValue("/kill")
                .visible(() -> this.forwardMethod.get() == StashMover.TransportMethod.KILL)
                .build()
        );
    private final Setting<Boolean> forwardKillRandom = this.sgForward
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("forward-kill-random-suffix")
                .description("Append random text after the kill command to bypass anti-spam (Input→Output)")
                .defaultValue(true)
                .visible(() -> this.forwardMethod.get() == StashMover.TransportMethod.KILL)
                .build()
        );
    private final Setting<BlockPos> forwardDeathPos = this.sgForward
        .add(
            new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                .name("forward-death-pos")
                .description("Lava/drop block to walk into to die when travelling Input→Output (Walk to Death method)")
                .defaultValue(new BlockPos(0, 64, 0))
                .visible(() -> this.forwardMethod.get() == StashMover.TransportMethod.KILL_POSITION && !BepConfig.streamerMode.get())
                .build()
        );
    private final Setting<String> pearlPlayerName = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("pearl-player")
                .description("Player name to message for pearl loading (Input→Output)")
                .defaultValue("PlayerName")
                .visible(() -> this.forwardMethod.get() == StashMover.TransportMethod.PEARL)
                .build()
        );
    private final Setting<String> pearlCommand = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("pearl-command")
                .description("Command to send for pearl loading (Input→Output)")
                .defaultValue("pearl")
                .visible(() -> this.forwardMethod.get() == StashMover.TransportMethod.PEARL)
                .build()
        );
    private final Setting<Integer> pearlTimeout = this.sgPearl
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("pearl-timeout")
                .description("Timeout for pearl loading in seconds")
                .defaultValue(10)
                .min(5)
                .max(30)
                .build()
        );
    private final Setting<StashMover.TransportMethod> goBackMethod = this.sgGoBack
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("go-back-method"))
                        .description("Method to go back to input area"))
                    .defaultValue(StashMover.TransportMethod.PEARL))
                .build()
        );
    private final Setting<String> goBackPlayerName = this.sgGoBack
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("go-back-player")
                .description("Player name for go back pearl loading (Output→Input)")
                .defaultValue("PlayerName")
                .visible(() -> this.goBackMethod.get() == StashMover.TransportMethod.PEARL)
                .build()
        );
    private final Setting<String> goBackCommand = this.sgGoBack
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("go-back-command")
                .description("Command for go back pearl loading (Output→Input)")
                .defaultValue("back")
                .visible(() -> this.goBackMethod.get() == StashMover.TransportMethod.PEARL)
                .build()
        );
    private final Setting<String> goBackKillCommand = this.sgGoBack
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("go-back-kill-command")
                .description("Command used to die when going back Output→Input (Kill Command method)")
                .defaultValue("/kill")
                .visible(() -> this.goBackMethod.get() == StashMover.TransportMethod.KILL)
                .build()
        );
    private final Setting<Boolean> goBackKillRandom = this.sgGoBack
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("go-back-kill-random-suffix")
                .description("Append random text after the kill command to bypass anti-spam (Output→Input)")
                .defaultValue(true)
                .visible(() -> this.goBackMethod.get() == StashMover.TransportMethod.KILL)
                .build()
        );
    private final Setting<BlockPos> goBackDeathPos = this.sgGoBack
        .add(
            new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                .name("go-back-death-pos")
                .description("Lava/drop block to walk into to die when going back Output→Input (Walk to Death method)")
                .defaultValue(new BlockPos(0, 64, 0))
                .visible(() -> this.goBackMethod.get() == StashMover.TransportMethod.KILL_POSITION && !BepConfig.streamerMode.get())
                .build()
        );
    private final Setting<BlockPos> outputPearlPickupPos = this.sgResetPearl
        .add(
            new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                .name("output-pickup-pos")
                .description("Position for pearl pickup at output")
                .defaultValue(new BlockPos(0, 64, 0))
                .visible(() -> this.forwardMethod.get() == StashMover.TransportMethod.PEARL && !BepConfig.streamerMode.get())
                .build()
        );
    private final Setting<BlockPos> outputPearlThrowPos = this.sgResetPearl
        .add(
            new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                .name("output-throw-pos")
                .description("Position for pearl throw at output")
                .defaultValue(new BlockPos(0, 64, 0))
                .visible(() -> this.forwardMethod.get() == StashMover.TransportMethod.PEARL && !BepConfig.streamerMode.get())
                .build()
        );
    private final Setting<Double> outputPearlThrowPitch = this.sgResetPearl
        .add(
            new Builder()
                .name("output-throw-pitch")
                .description("Pitch for throwing pearl at output (90 = straight down)")
                .defaultValue(90.0)
                .sliderRange(-90.0, 90.0)
                .visible(() -> this.forwardMethod.get() == StashMover.TransportMethod.PEARL)
                .build()
        );
    private final Setting<Double> outputPearlThrowYaw = this.sgResetPearl
        .add(
            new Builder()
                .name("output-throw-yaw")
                .description("Yaw for throwing pearl at output")
                .defaultValue(90.0)
                .sliderRange(-180.0, 180.0)
                .visible(() -> this.forwardMethod.get() == StashMover.TransportMethod.PEARL)
                .build()
        );
    private final Setting<BlockPos> inputPearlPickupPos = this.sgResetPearl
        .add(
            new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                .name("input-pickup-pos")
                .description("Position for pearl pickup at input")
                .defaultValue(new BlockPos(0, 64, 0))
                .visible(() -> this.goBackMethod.get() == StashMover.TransportMethod.PEARL && !BepConfig.streamerMode.get())
                .build()
        );
    private final Setting<BlockPos> inputPearlThrowPos = this.sgResetPearl
        .add(
            new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                .name("input-throw-pos")
                .description("Position for pearl throw at input")
                .defaultValue(new BlockPos(0, 64, 0))
                .visible(() -> this.goBackMethod.get() == StashMover.TransportMethod.PEARL && !BepConfig.streamerMode.get())
                .build()
        );
    private final Setting<Double> inputPearlThrowPitch = this.sgResetPearl
        .add(
            new Builder()
                .name("input-throw-pitch")
                .description("Pitch for throwing pearl at input (90 = straight down)")
                .defaultValue(90.0)
                .sliderRange(-90.0, 90.0)
                .visible(() -> this.goBackMethod.get() == StashMover.TransportMethod.PEARL)
                .build()
        );
    private final Setting<Double> inputPearlThrowYaw = this.sgResetPearl
        .add(
            new Builder()
                .name("input-throw-yaw")
                .description("Yaw for throwing pearl at input")
                .defaultValue(0.0)
                .sliderRange(-180.0, 180.0)
                .visible(() -> this.goBackMethod.get() == StashMover.TransportMethod.PEARL)
                .build()
        );
    private final Setting<Integer> pearlWaitTime = this.sgResetPearl
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("pearl-wait-time")
                .description("Time to wait after throwing pearl (seconds)")
                .defaultValue(5)
                .min(1)
                .max(10)
                .build()
        );
    private final Setting<Double> positionTolerance = this.sgResetPearl
        .add(
            new Builder()
                .name("position-tolerance")
                .description("How close to target position before throwing pearl (blocks)")
                .defaultValue(0.3)
                .min(0.1)
                .max(2.0)
                .sliderRange(0.1, 2.0)
                .build()
        );
    private final Setting<Integer> openDelay = this.sgDelays
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("open-delay")
                .description("Delay after opening container in ticks")
                .defaultValue(30)
                .min(5)
                .max(100)
                .build()
        );
    private final Setting<Integer> transferDelay = this.sgDelays
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("transfer-delay")
                .description("Settle delay (ticks) after a container opens before moving items")
                .defaultValue(10)
                .min(0)
                .max(100)
                .build()
        );
    private final Setting<Integer> transferSpeed = this.sgDelays
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("transfer-speed")
                .description(
                    "Items shift-clicked per tick while emptying/filling containers (like Meteor's Steal/Dump speed). Higher = faster, no per-item wait."
                )
                .defaultValue(25)
                .min(1)
                .max(64)
                .sliderRange(1, 64)
                .build()
        );
    private final Setting<Integer> closeDelay = this.sgDelays
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("close-delay")
                .description("Delay after closing container in ticks")
                .defaultValue(20)
                .min(0)
                .max(100)
                .build()
        );
    private final Setting<Integer> moveDelay = this.sgDelays
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("move-delay")
                .description("Delay between movements in ticks")
                .defaultValue(5)
                .min(5)
                .max(100)
                .build()
        );
    private final Setting<Boolean> renderSelection = this.sgRendering
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render-selection")
                .description("Render selection areas")
                .defaultValue(true)
                .build()
        );
    private final Setting<SettingColor> inputAreaColor = this.sgRendering
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("input-area-outline")
                .description("Outline color for input area")
                .defaultValue(new SettingColor(0, 255, 0, 255))
                .build()
        );
    private final Setting<SettingColor> outputAreaColor = this.sgRendering
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("output-area-outline")
                .description("Outline color for output area")
                .defaultValue(new SettingColor(0, 100, 255, 255))
                .build()
        );
    private final Setting<SettingColor> inputContainerColor = this.sgRendering
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("input-container-color")
                .description("Color for input containers (not empty)")
                .defaultValue(new SettingColor(0, 255, 0, 100))
                .build()
        );
    private final Setting<SettingColor> outputContainerColor = this.sgRendering
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("output-container-color")
                .description("Color for output containers (not full)")
                .defaultValue(new SettingColor(0, 100, 255, 100))
                .build()
        );
    private final Setting<SettingColor> activeContainerColor = this.sgRendering
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("active-container-color")
                .description("Color for currently active container")
                .defaultValue(new SettingColor(255, 255, 0, 150))
                .build()
        );
    private StashMover.ProcessState currentState = StashMover.ProcessState.IDLE;
    private StashMover.ProcessState lastDebugState = StashMover.ProcessState.IDLE;
    private int stateTimer = 0;
    private static BlockPos inputAreaPos1 = null;
    private static BlockPos inputAreaPos2 = null;
    private static BlockPos outputAreaPos1 = null;
    private static BlockPos outputAreaPos2 = null;
    private static BlockPos selectionPos1 = null;
    private static StashMover.SelectionMode selectionMode = StashMover.SelectionMode.NONE;
    private static final Set<StashMover.ContainerInfo> inputContainers = ConcurrentHashMap.newKeySet();
    private static final Set<StashMover.ContainerInfo> outputContainers = ConcurrentHashMap.newKeySet();
    private StashMover.ContainerInfo currentContainer = null;
    private BlockPos enderChestPos = null;
    private BlockPos lastBaritoneGoal = null;
    private int containerOpenFailures = 0;
    private int outputSkipResets = 0;
    private long lastPearlMessageTime = 0L;
    private boolean waitingForPearl = false;
    private int pearlRetryCount = 0;
    private Vec3 initialPlayerPos = null;
    private boolean hasThrownPearl = false;
    private long pearlThrowTime = 0L;
    private boolean hasPlacedShulker = false;
    private boolean isGoingToInput = false;
    private ItemStack offhandBackup = ItemStack.EMPTY;
    private int pearlFailRetries = 0;
    private int previousSlot = -1;
    private int rotationStabilizationTimer = 0;
    private boolean rotationSet = false;
    private BlockPos safeRetreatPos = null;
    private boolean waitingForRespawn = false;
    private long lastKillTime = 0L;
    private int killRetryCount = 0;
    private static final int WORLD_LOAD_SETTLE_TICKS = 20;
    private int arrivalSettleTicks = 0;
    private int itemsTransferred = 0;
    private int containersProcessed = 0;
    private boolean enderChestFull = false;
    private boolean enderChestHasItems = false;
    private boolean enderChestEmptied = false;
    private int ecOpenFailures = 0;
    private int ecRepathAttempts = 0;
    private int ecApproachTicks = 0;
    private int ecFreeSlots = -1;
    private int killPositionRetries = 0;
    private int prepareApproachTicks = 0;
    private int repathAttempts = 0;
    private int alignTicks = 0;
    private boolean breakingStarted = false;
    private StashMover.ContainerInfo movingToContainer = null;
    private int moveTimeoutTicks = 0;
    private int pathGraceTicks = 0;
    private int openWaitTicks = 0;
    private int retreatTicks = 0;
    private int pearlPickupWaitRuns = 0;
    private final List<BlockPos> standTargets = new ArrayList<>();
    private final List<BlockPos> ecStandTargets = new ArrayList<>();
    private BlockPos pathStartPos = null;
    private int goalTier = 0;
    private static final boolean BARITONE_PRESENT = checkBaritonePresent();
    private static final int ENDERCHEST_SLOTS = 27;

    private static boolean checkBaritonePresent() {
        try {
            Class.forName("baritone.api.BaritoneAPI");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public StashMover() {
        super(Bep.CATEGORY, "stash-mover", "Automatically moves items between stash areas using pearl loading");
    }

    @Override
    public void onActivate() {
        if (!BARITONE_PRESENT) {
            this.error("Baritone is required for StashMover. Install Baritone and try again.");
            this.toggle();
        } else {
            this.stateTimer = 0;
            this.itemsTransferred = 0;
            this.containersProcessed = 0;
            this.enderChestFull = false;
            this.enderChestEmptied = false;
            this.currentContainer = null;
            this.waitingForPearl = false;
            this.pearlRetryCount = 0;
            this.killPositionRetries = 0;
            this.containerOpenFailures = 0;
            this.outputSkipResets = 0;
            this.ecOpenFailures = 0;
            this.ecRepathAttempts = 0;
            this.ecApproachTicks = 0;
            this.ecFreeSlots = -1;
            this.breakingStarted = false;
            this.movingToContainer = null;
            this.pearlFailRetries = 0;
            this.waitingForRespawn = false;
            this.arrivalSettleTicks = 0;
            this.moveTimeoutTicks = 0;
            this.pathGraceTicks = 0;
            this.openWaitTicks = 0;
            this.retreatTicks = 0;
            this.pearlPickupWaitRuns = 0;
            this.standTargets.clear();
            this.ecStandTargets.clear();
            this.pathStartPos = null;
            this.goalTier = 0;
            String prefix = Config.get().prefix.get();
            this.info("StashMover activated");
            if (inputAreaPos1 != null && inputAreaPos2 != null) {
                this.info("§aInput area set with §f" + inputContainers.size() + "§a containers");
            } else {
                this.info("§7Use §f" + prefix + "setinput §7to select input area");
            }

            if (outputAreaPos1 != null && outputAreaPos2 != null) {
                this.info("§bOutput area set with §f" + outputContainers.size() + "§b containers");
            } else {
                this.info("§7Use §f" + prefix + "setoutput §7to select output area");
            }

            if (this.hasValidAreas()) {
                this.info("§eStarting automated transfer process...");
                this.currentState = StashMover.ProcessState.CHECKING_LOCATION;
                this.stateTimer = 0;
            } else {
                this.info("§cConfigure both input and output areas to start");
                this.currentState = StashMover.ProcessState.IDLE;
                this.stateTimer = 20;
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (this.mc.screen instanceof ContainerScreen) {
            this.mc.player.closeContainer();
        }

        if (BARITONE_PRESENT && BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        }

        if (this.breakingStarted) {
            this.mc.gameMode.stopDestroyBlock();
            this.breakingStarted = false;
        }

        this.mc.options.keyShift.setDown(false);
        this.mc.options.keyUp.setDown(false);
        this.mc.options.keyDown.setDown(false);
        this.mc.options.keyLeft.setDown(false);
        this.mc.options.keyRight.setDown(false);
        this.mc.options.keySprint.setDown(false);
        if (this.mc.player != null && this.mc.player.input != null) {
            ((InputAccessor)this.mc.player.input).setMovementForward(0.0F);
            ((InputAccessor)this.mc.player.input).setMovementSideways(0.0F);
        }

        this.currentState = StashMover.ProcessState.IDLE;
        this.info("StashMover deactivated");
    }

    public void handleBlockSelectionPublic(BlockPos pos) {
        this.handleBlockSelection(pos);
    }

    private void handleBlockSelection(BlockPos pos) {
        switch (selectionMode) {
            case INPUT_FIRST:
                selectionPos1 = pos;
                selectionMode = StashMover.SelectionMode.INPUT_SECOND;
                this.info("§aInput area first corner set");
                this.info("§eLeft-click another block to set the second corner");
                break;
            case INPUT_SECOND:
                if (pos.equals(selectionPos1)) {
                    this.warning("Second corner must be different from the first!");
                    return;
                }

                this.setInputArea(selectionPos1, pos);
                selectionMode = StashMover.SelectionMode.NONE;
                selectionPos1 = null;
                this.info("§aInput area selection complete!");
                break;
            case OUTPUT_FIRST:
                selectionPos1 = pos;
                selectionMode = StashMover.SelectionMode.OUTPUT_SECOND;
                this.info("§bOutput area first corner set");
                this.info("§eLeft-click another block to set the second corner");
                break;
            case OUTPUT_SECOND:
                if (pos.equals(selectionPos1)) {
                    this.warning("Second corner must be different from the first!");
                    return;
                }

                this.setOutputArea(selectionPos1, pos);
                selectionMode = StashMover.SelectionMode.NONE;
                selectionPos1 = null;
                this.info("§bOutput area selection complete!");
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.isActive()) {
                if (BARITONE_PRESENT) {
                    if (this.stateTimer > 0) {
                        this.stateTimer--;
                        if (this.currentState != StashMover.ProcessState.IDLE) {
                            return;
                        }
                    }

                    if (!this.pauseOnLag.get() || !this.isServerLagging()) {
                        this.handleCurrentState();
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (this.isActive() || selectionMode != StashMover.SelectionMode.NONE) {
            if (this.renderSelection.get() || selectionMode != StashMover.SelectionMode.NONE) {
                if (inputAreaPos1 != null && inputAreaPos2 != null) {
                    AABB inputBox = new AABB(
                        inputAreaPos1.getX(),
                        inputAreaPos1.getY(),
                        inputAreaPos1.getZ(),
                        inputAreaPos2.getX() + 1,
                        inputAreaPos2.getY() + 1,
                        inputAreaPos2.getZ() + 1
                    );
                    event.renderer.box(inputBox, this.inputAreaColor.get(), this.inputAreaColor.get(), ShapeMode.Lines, 0);
                }

                if (outputAreaPos1 != null && outputAreaPos2 != null) {
                    AABB outputBox = new AABB(
                        outputAreaPos1.getX(),
                        outputAreaPos1.getY(),
                        outputAreaPos1.getZ(),
                        outputAreaPos2.getX() + 1,
                        outputAreaPos2.getY() + 1,
                        outputAreaPos2.getZ() + 1
                    );
                    event.renderer.box(outputBox, this.outputAreaColor.get(), this.outputAreaColor.get(), ShapeMode.Lines, 0);
                }

                if (this.isActive()) {
                    for (StashMover.ContainerInfo container : inputContainers) {
                        if (!container.isEmpty) {
                            SettingColor color = container == this.currentContainer ? this.activeContainerColor.get() : this.inputContainerColor.get();
                            this.renderContainer(event, container, color);
                        }
                    }

                    for (StashMover.ContainerInfo container : outputContainers) {
                        if (!container.isFull) {
                            SettingColor color = container == this.currentContainer ? this.activeContainerColor.get() : this.outputContainerColor.get();
                            this.renderContainer(event, container, color);
                        }
                    }
                }

                if (selectionMode != StashMover.SelectionMode.NONE && selectionPos1 != null) {
                    BlockPos currentPos = this.mc.hitResult != null && this.mc.hitResult.getType() == Type.BLOCK
                        ? ((BlockHitResult)this.mc.hitResult).getBlockPos()
                        : this.mc.player.blockPosition();
                    AABB selectionBox = new AABB(
                        Math.min(selectionPos1.getX(), currentPos.getX()),
                        Math.min(selectionPos1.getY(), currentPos.getY()),
                        Math.min(selectionPos1.getZ(), currentPos.getZ()),
                        Math.max(selectionPos1.getX(), currentPos.getX()) + 1,
                        Math.max(selectionPos1.getY(), currentPos.getY()) + 1,
                        Math.max(selectionPos1.getZ(), currentPos.getZ()) + 1
                    );
                    SettingColor color = selectionMode != StashMover.SelectionMode.INPUT_FIRST && selectionMode != StashMover.SelectionMode.INPUT_SECOND
                        ? new SettingColor(0, 100, 255, 100)
                        : new SettingColor(0, 255, 0, 100);
                    event.renderer.box(selectionBox, color, color, ShapeMode.Both, 0);
                    AABB corner1 = new AABB(
                        selectionPos1.getX(),
                        selectionPos1.getY(),
                        selectionPos1.getZ(),
                        selectionPos1.getX() + 1,
                        selectionPos1.getY() + 1,
                        selectionPos1.getZ() + 1
                    );
                    event.renderer.box(corner1, new SettingColor(255, 255, 0, 200), new SettingColor(255, 255, 0, 100), ShapeMode.Both, 0);
                }
            }
        }
    }

    private void renderContainer(Render3DEvent event, StashMover.ContainerInfo container, SettingColor color) {
        AABB box = new AABB(
            container.pos.getX(),
            container.pos.getY(),
            container.pos.getZ(),
            container.pos.getX() + 1,
            container.pos.getY() + 1,
            container.pos.getZ() + 1
        );
        if (container.type == StashMover.ContainerType.DOUBLE_CHEST || container.type == StashMover.ContainerType.DOUBLE_TRAPPED_CHEST) {
            BlockState state = this.mc.level.getBlockState(container.pos);
            if (state.hasProperty(BlockStateProperties.CHEST_TYPE)) {
                ChestType chestType = state.getValue(BlockStateProperties.CHEST_TYPE);
                Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                if (chestType == ChestType.LEFT) {
                    BlockPos otherPos = container.pos.relative(facing.getClockWise());
                    box = box.minmax(
                        new AABB(
                            otherPos.getX(),
                            otherPos.getY(),
                            otherPos.getZ(),
                            otherPos.getX() + 1,
                            otherPos.getY() + 1,
                            otherPos.getZ() + 1
                        )
                    );
                } else if (chestType == ChestType.RIGHT) {
                    BlockPos otherPos = container.pos.relative(facing.getCounterClockWise());
                    box = box.minmax(
                        new AABB(
                            otherPos.getX(),
                            otherPos.getY(),
                            otherPos.getZ(),
                            otherPos.getX() + 1,
                            otherPos.getY() + 1,
                            otherPos.getZ() + 1
                        )
                    );
                }
            }
        }

        event.renderer.box(box, color, color, ShapeMode.Both, 0);
    }

    private void handleCurrentState() {
        if (this.debugMode.get() && this.currentState != this.lastDebugState) {
            this.info("State: " + this.currentState + " (timer: " + this.stateTimer + ")");
            this.lastDebugState = this.currentState;
        }

        switch (this.currentState) {
            case IDLE:
                this.handleIdleState();
                break;
            case CHECKING_LOCATION:
                this.checkLocation();
                break;
            case INPUT_PROCESS:
                this.handleInputProcess();
                break;
            case LOADING_PEARL:
                this.handlePearlLoading();
                break;
            case RESET_PEARL_PICKUP:
                this.handleResetPearlPickup();
                break;
            case RESET_PEARL_PLACE_SHULKER:
                this.handleResetPearlPlaceShulker();
                break;
            case RESET_PEARL_APPROACH:
                this.handleResetPearlApproach();
                break;
            case RESET_PEARL_PREPARE:
                this.handleResetPearlPrepare();
                break;
            case RESET_PEARL_THROW:
                this.handleResetPearlThrow();
                break;
            case RESET_PEARL_WAIT:
                this.handleResetPearlWait();
                break;
            case OUTPUT_PROCESS:
                this.handleOutputProcess();
                break;
            case GOING_BACK:
                this.handleGoingBack();
                break;
            case OPENING_CONTAINER:
                this.handleOpeningContainer();
                break;
            case TRANSFERRING_ITEMS:
                this.handleTransferringItems();
                break;
            case CLOSING_CONTAINER:
                this.handleClosingContainer();
                break;
            case BREAKING_CONTAINER:
                this.handleBreakingContainer();
                break;
            case MOVING_TO_CONTAINER:
                this.handleMovingToContainer();
                break;
            case OPENING_ENDERCHEST:
                this.handleOpeningEnderChest();
                break;
            case FILLING_ENDERCHEST:
                this.handleFillingEnderChest();
                break;
            case EMPTYING_ENDERCHEST:
                this.handleEmptyingEnderChest();
                break;
            case WAITING:
                this.handleWaiting();
                break;
            case KILL_COMMAND:
                this.handleKillCommand();
                break;
            case KILL_POSITION_APPROACH:
                this.handleKillPositionApproach();
                break;
            case KILL_POSITION_WALK:
                this.handleKillPositionWalk();
                break;
            case KILL_POSITION_WAIT:
                this.handleKillPositionWait();
        }
    }

    public void startInputSelection() {
        selectionMode = StashMover.SelectionMode.INPUT_FIRST;
        selectionPos1 = null;
        this.info("§aInput area selection started - §fLeft-click §afirst corner block");
    }

    public void startOutputSelection() {
        selectionMode = StashMover.SelectionMode.OUTPUT_FIRST;
        selectionPos1 = null;
        this.info("§bOutput area selection started - §fLeft-click §bfirst corner block");
    }

    public void cancelSelection() {
        selectionMode = StashMover.SelectionMode.NONE;
        selectionPos1 = null;
        this.info("§cSelection cancelled");
    }

    public void setInputArea(BlockPos pos1, BlockPos pos2) {
        inputAreaPos1 = new BlockPos(
            Math.min(pos1.getX(), pos2.getX()),
            Math.min(pos1.getY(), pos2.getY()),
            Math.min(pos1.getZ(), pos2.getZ())
        );
        inputAreaPos2 = new BlockPos(
            Math.max(pos1.getX(), pos2.getX()),
            Math.max(pos1.getY(), pos2.getY()),
            Math.max(pos1.getZ(), pos2.getZ())
        );
        this.detectContainersInArea(inputAreaPos1, inputAreaPos2, true);
        this.info("§aInput area set with §f" + inputContainers.size() + " §acontainers");
    }

    public void setOutputArea(BlockPos pos1, BlockPos pos2) {
        outputAreaPos1 = new BlockPos(
            Math.min(pos1.getX(), pos2.getX()),
            Math.min(pos1.getY(), pos2.getY()),
            Math.min(pos1.getZ(), pos2.getZ())
        );
        outputAreaPos2 = new BlockPos(
            Math.max(pos1.getX(), pos2.getX()),
            Math.max(pos1.getY(), pos2.getY()),
            Math.max(pos1.getZ(), pos2.getZ())
        );
        this.detectContainersInArea(outputAreaPos1, outputAreaPos2, false);
        this.info("§bOutput area set with §f" + outputContainers.size() + " §bcontainers");
    }

    private void detectContainersInArea(BlockPos pos1, BlockPos pos2, boolean isInput) {
        this.detectContainersInArea(pos1, pos2, isInput, false);
    }

    private void detectContainersInArea(BlockPos pos1, BlockPos pos2, boolean isInput, boolean preserveKnown) {
        Set<StashMover.ContainerInfo> containers = isInput ? inputContainers : outputContainers;
        Map<BlockPos, StashMover.ContainerInfo> known = new HashMap<>();
        if (preserveKnown) {
            for (StashMover.ContainerInfo c : containers) {
                known.put(c.pos, c);
            }
        }

        containers.clear();
        Set<BlockPos> processedPositions = new HashSet<>();

        for (int x = pos1.getX(); x <= pos2.getX(); x++) {
            for (int y = pos1.getY(); y <= pos2.getY(); y++) {
                for (int z = pos1.getZ(); z <= pos2.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!processedPositions.contains(pos)) {
                        BlockState state = this.mc.level.getBlockState(pos);
                        net.minecraft.world.level.block.Block block = state.getBlock();
                        StashMover.ContainerInfo container = null;
                        if (block instanceof ChestBlock && !(block instanceof TrappedChestBlock)) {
                            if (state.hasProperty(BlockStateProperties.CHEST_TYPE)) {
                                ChestType chestType = state.getValue(BlockStateProperties.CHEST_TYPE);
                                if (chestType != ChestType.SINGLE) {
                                    Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                                    BlockPos otherPos = null;
                                    if (chestType == ChestType.LEFT) {
                                        otherPos = pos.relative(facing.getClockWise());
                                    } else {
                                        otherPos = pos.relative(facing.getCounterClockWise());
                                    }

                                    processedPositions.add(otherPos);
                                    container = new StashMover.ContainerInfo(pos, StashMover.ContainerType.DOUBLE_CHEST);
                                } else {
                                    container = new StashMover.ContainerInfo(pos, StashMover.ContainerType.CHEST);
                                }
                            } else {
                                container = new StashMover.ContainerInfo(pos, StashMover.ContainerType.CHEST);
                            }
                        } else if (block instanceof TrappedChestBlock) {
                            if (state.hasProperty(BlockStateProperties.CHEST_TYPE)) {
                                ChestType chestType = state.getValue(BlockStateProperties.CHEST_TYPE);
                                if (chestType != ChestType.SINGLE) {
                                    Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                                    BlockPos otherPos = null;
                                    if (chestType == ChestType.LEFT) {
                                        otherPos = pos.relative(facing.getClockWise());
                                    } else {
                                        otherPos = pos.relative(facing.getCounterClockWise());
                                    }

                                    processedPositions.add(otherPos);
                                    container = new StashMover.ContainerInfo(pos, StashMover.ContainerType.DOUBLE_TRAPPED_CHEST);
                                } else {
                                    container = new StashMover.ContainerInfo(pos, StashMover.ContainerType.TRAPPED_CHEST);
                                }
                            } else {
                                container = new StashMover.ContainerInfo(pos, StashMover.ContainerType.TRAPPED_CHEST);
                            }
                        } else if (block instanceof BarrelBlock) {
                            container = new StashMover.ContainerInfo(pos, StashMover.ContainerType.BARREL);
                        }

                        if (container != null) {
                            StashMover.ContainerInfo prev = known.get(container.pos);
                            if (prev != null && prev.type == container.type) {
                                container.isEmpty = prev.isEmpty;
                                container.isFull = prev.isFull;
                            }

                            containers.add(container);
                            processedPositions.add(pos);
                        }
                    }
                }
            }
        }

        for (StashMover.ContainerInfo prev : known.values()) {
            if (!this.mc.level.getChunkSource().hasChunk(prev.pos.getX() >> 4, prev.pos.getZ() >> 4)) {
                prev.skipped = false;
                containers.add(prev);
            }
        }
    }

    private void checkLocation() {
        if (this.isNearInputArea()) {
            this.enderChestEmptied = false;
            this.ecFreeSlots = -1;
            this.detectContainersInArea(inputAreaPos1, inputAreaPos2, true, true);
            this.info("Found " + inputContainers.size() + " input containers");
            if (this.goBackMethod.get() == StashMover.TransportMethod.PEARL) {
                this.info("Near input area, ensuring the input stasis is armed first");
                this.currentState = StashMover.ProcessState.RESET_PEARL_PICKUP;
                this.hasThrownPearl = false;
                this.hasPlacedShulker = false;
                this.isGoingToInput = true;
            } else {
                this.info("Near input area, starting input process");
                this.currentState = StashMover.ProcessState.INPUT_PROCESS;
                this.stateTimer = 0;
            }
        } else if (this.isNearOutputArea()) {
            this.detectContainersInArea(outputAreaPos1, outputAreaPos2, false, true);
            this.info("Found " + outputContainers.size() + " output containers");
            if (this.forwardMethod.get() == StashMover.TransportMethod.PEARL) {
                this.info("Near output area, resetting pearl first");
                this.currentState = StashMover.ProcessState.RESET_PEARL_PICKUP;
                this.hasThrownPearl = false;
                this.hasPlacedShulker = false;
                this.isGoingToInput = false;
            } else {
                this.info("Near output area, resuming deposit");
                this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
                this.stateTimer = 10;
            }
        } else {
            this.warning("Not near any configured area! Will retry in 5 seconds...");
            if (this.debugMode.get()) {
                this.warning("Input area not set");
                this.warning("Output area not set");
            }

            this.currentState = StashMover.ProcessState.IDLE;
            this.stateTimer = 100;
        }
    }

    private void handleInputProcess() {
        if (this.killBasedForward()) {
            this.killFerryDecideInput();
        } else if (this.isInventoryFull()) {
            if (this.fillEnderChest.get() && !this.isEnderChestFull()) {
                this.info("Inventory full, checking enderchest...");
                this.findOrPlaceEnderChest();
            } else {
                this.info("Inventory full and enderchest not available/full, travelling to output");
                this.startForwardTransport();
            }
        } else {
            this.findNextInputContainer();
        }
    }

    private void findNextInputContainer() {
        this.currentContainer = inputContainers.stream()
            .filter(c -> !c.isEmpty && !c.skipped)
            .min(Comparator.comparingDouble(c -> this.mc.player.position().distanceTo(Vec3.atCenterOf(c.pos))))
            .orElse(null);
        if (this.currentContainer == null) {
            if (!this.isInventoryFull() && (!this.useEnderChest() || !this.hasItemsInEnderChest())) {
                this.info("No containers with items found, rescanning...");
                this.detectContainersInArea(inputAreaPos1, inputAreaPos2, true);
                this.currentContainer = inputContainers.stream()
                    .filter(c -> !c.isEmpty && !c.skipped)
                    .min(Comparator.comparingDouble(c -> this.mc.player.position().distanceTo(Vec3.atCenterOf(c.pos))))
                    .orElse(null);
                if (this.currentContainer != null) {
                    this.info("Found container after rescan");
                    this.moveToContainer(this.currentContainer);
                } else {
                    this.info("No containers found, waiting 5 seconds...");
                    this.currentState = StashMover.ProcessState.IDLE;
                    this.stateTimer = 100;
                }
            } else {
                this.info("All input containers processed, travelling to output!");
                this.startForwardTransport();
            }
        } else {
            this.info("Moving to container");
            this.moveToContainer(this.currentContainer);
        }
    }

    private void moveToContainer(StashMover.ContainerInfo container) {
        if (container != null) {
            this.currentContainer = container;
            if (this.movingToContainer != container) {
                this.movingToContainer = container;
                this.resetContainerCounters();
            }

            Vec3 eyePos = this.mc.player.getEyePosition();
            BlockHitResult hit = this.findVisibleHit(container, eyePos);
            this.standTargets.clear();
            this.standTargets.addAll(this.findValidStandingPositionsNear(container.pos));
            if (this.closeEnoughToOpen(hit, this.standTargets, this.containerOpenFailures)) {
                this.cancelBaritone();
                this.alignTicks = 0;
                this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
                this.stateTimer = 0;
            } else {
                this.cancelBaritone();
                this.setBaritoneGoal(this.containerGoal(container));
                this.pathStartPos = this.mc.player.blockPosition();
                this.currentState = StashMover.ProcessState.MOVING_TO_CONTAINER;
                double distance = eyePos.distanceTo(Vec3.atCenterOf(container.pos));
                this.moveTimeoutTicks = distance > 20.0 ? 200 : (distance > 10.0 ? 120 : 80);
                this.pathGraceTicks = 10;
                this.stateTimer = 0;
                this.info("Moving to container (distance: " + String.format("%.1f", distance) + "m)");
            }
        }
    }

    private Goal containerGoal(StashMover.ContainerInfo container) {
        if (this.goalTier == 0 && !this.standTargets.isEmpty()) {
            return this.standingSpotsGoal(this.standTargets);
        } else {
            return this.goalTier <= 1 ? new GoalNear(this.getInteractBlock(container), 2) : new GoalGetToBlock(this.getInteractBlock(container));
        }
    }

    private Goal standingSpotsGoal(List<BlockPos> spots) {
        return new GoalComposite(spots.stream().limit(16L).map(GoalBlock::new).toArray(Goal[]::new));
    }

    private List<BlockPos> findValidStandingPositionsNear(BlockPos containerPos) {
        BlockPos otherHalf = this.getOtherHalf(containerPos);
        Vec3 playerPos = this.mc.player.position();
        int r = Mth.ceil(this.containerReach.get());

        record Scored(BlockPos pos, double score) {
        }

        List<Scored> scored = new ArrayList<>();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos checkPos = containerPos.offset(dx, dy, dz);
                    if (this.isValidStandingSpot(checkPos)) {
                        double reach = this.openReachFrom(checkPos, containerPos, otherHalf);
                        if (!(reach < 0.0)) {
                            scored.add(new Scored(checkPos, reach + Vec3.atCenterOf(checkPos).distanceTo(playerPos) * 0.05));
                        }
                    }
                }
            }
        }

        scored.sort(Comparator.comparingDouble(Scored::score));
        return scored.stream().map(Scored::pos).toList();
    }

    private double openReachFrom(BlockPos standBlock, BlockPos target, BlockPos other) {
        Vec3 eye = Vec3.atLowerCornerOf(standBlock).add(0.5, 1.62, 0.5);
        double best = -1.0;
        BlockPos[] halves = other == null ? new BlockPos[]{target} : new BlockPos[]{target, other};

        for (BlockPos half : halves) {
            Vec3 aim = this.containerAimPoint(half, eye);
            if (aim != null) {
                double d = eye.distanceTo(aim);
                if (!(d > this.containerReach.get())) {
                    BlockHitResult clip = this.mc
                        .level
                        .clip(new ClipContext(eye, aim, Block.OUTLINE, Fluid.NONE, this.mc.player));
                    boolean clear;
                    if (clip.getType() != Type.BLOCK) {
                        clear = true;
                    } else {
                        BlockPos hp = clip.getBlockPos();
                        clear = hp.equals(half) || hp.equals(target) || other != null && hp.equals(other);
                    }

                    if (clear && (best < 0.0 || d < best)) {
                        best = d;
                    }
                }
            }
        }

        return best;
    }

    private boolean isValidStandingSpot(BlockPos pos) {
        BlockState at = this.mc.level.getBlockState(pos);
        BlockState above = this.mc.level.getBlockState(pos.above());
        BlockState below = this.mc.level.getBlockState(pos.below());
        return at.getCollisionShape(this.mc.level, pos).isEmpty()
            && at.getFluidState().isEmpty()
            && above.getCollisionShape(this.mc.level, pos.above()).isEmpty()
            && net.minecraft.world.level.block.Block.isFaceFull(below.getCollisionShape(this.mc.level, pos.below()), Direction.UP);
    }

    private void handleMovingToContainer() {
        if (this.currentContainer == null) {
            this.currentState = this.isNearOutputArea() ? StashMover.ProcessState.OUTPUT_PROCESS : StashMover.ProcessState.INPUT_PROCESS;
        } else {
            Vec3 eyePos = this.mc.player.getEyePosition();
            BlockHitResult hit = this.findVisibleHit(this.currentContainer, eyePos);
            if (this.closeEnoughToOpen(hit, this.standTargets, this.containerOpenFailures)) {
                this.cancelBaritone();
                this.alignTicks = 0;
                this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
                this.stateTimer = 0;
                this.info("Reached container, opening...");
            } else if (this.pathGraceTicks > 0) {
                this.pathGraceTicks--;
            } else if (!this.isCalculatingPath()) {
                if (this.isPathing()) {
                    if (--this.moveTimeoutTicks <= 0) {
                        this.cancelBaritone();
                        if (++this.repathAttempts > this.maxRetries.get()) {
                            this.skipCurrentContainer("path timeout");
                        } else {
                            this.warning("Movement timeout, re-pathing (" + this.repathAttempts + "/" + this.maxRetries.get() + ")");
                            this.moveToContainer(this.currentContainer);
                        }
                    }
                } else {
                    boolean stalled = this.pathStartPos != null && this.mc.player.blockPosition().equals(this.pathStartPos);
                    if (stalled) {
                        if (this.goalTier < 2) {
                            this.goalTier++;
                            this.warning("Goal unreachable from here, loosening goal (tier " + this.goalTier + ")");
                            this.moveToContainer(this.currentContainer);
                        } else if (!this.tryLastResortOpen(hit, eyePos)) {
                            this.skipCurrentContainer("unreachable by Baritone");
                        }
                    } else if (++this.repathAttempts > this.maxRetries.get()) {
                        if (!this.tryLastResortOpen(hit, eyePos)) {
                            this.skipCurrentContainer("unreachable by Baritone");
                        }
                    } else {
                        this.warning("Path stopped short, re-pathing (" + this.repathAttempts + "/" + this.maxRetries.get() + ")");
                        this.cancelBaritone();
                        this.goalTier = 0;
                        this.moveToContainer(this.currentContainer);
                    }
                }
            }
        }
    }

    private void handleOpeningContainer() {
        if (this.mc.screen instanceof ContainerScreen) {
            this.onContainerOpened();
        } else if (this.currentContainer == null) {
            this.currentState = this.isNearOutputArea() ? StashMover.ProcessState.OUTPUT_PROCESS : StashMover.ProcessState.INPUT_PROCESS;
        } else {
            this.cancelBaritone();
            this.mc.options.keyShift.setDown(false);
            Vec3 eyePos = this.mc.player.getEyePosition();
            BlockHitResult hit = this.findVisibleHit(this.currentContainer, eyePos);
            if (hit != null && !(eyePos.distanceTo(hit.getLocation()) > this.containerReach.get() + 0.5)) {
                if (!this.lookAtReal(hit.getLocation())) {
                    this.alignTicks = 0;
                } else if (this.alignTicks++ >= 1) {
                    this.info("Opening container (attempt " + (this.containerOpenFailures + 1) + ")");
                    this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hit);
                    this.mc.player.swing(InteractionHand.MAIN_HAND);
                    this.currentState = StashMover.ProcessState.WAITING;
                    this.openWaitTicks = this.openDelay.get();
                }
            } else if (++this.repathAttempts > this.maxRetries.get()) {
                this.skipCurrentContainer("no clean angle to open");
            } else {
                this.moveToContainer(this.currentContainer);
            }
        }
    }

    private void cancelBaritone() {
        if (BARITONE_PRESENT && BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        }
    }

    private boolean isPathing() {
        return BARITONE_PRESENT && BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
    }

    private boolean isCalculatingPath() {
        return BARITONE_PRESENT && BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getInProgress().isPresent();
    }

    private boolean closeEnoughToOpen(BlockHitResult hit, List<BlockPos> stands, int openFailures) {
        if (hit == null) {
            return false;
        } else {
            double d = this.mc.player.getEyePosition().distanceTo(hit.getLocation());
            if (d > this.containerReach.get()) {
                return false;
            } else if (d <= 2.5 || stands.contains(this.mc.player.blockPosition())) {
                return true;
            } else {
                return openFailures > 0 ? false : !this.isPathing() && !this.isCalculatingPath() || d <= this.containerReach.get() - 0.5;
            }
        }
    }

    private boolean tryLastResortOpen(BlockHitResult hit, Vec3 eyePos) {
        if (hit != null && !(eyePos.distanceTo(hit.getLocation()) > this.containerReach.get())) {
            this.warning("Could not path closer, opening from current position");
            this.cancelBaritone();
            this.alignTicks = 0;
            this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
            this.stateTimer = 0;
            return true;
        } else {
            return false;
        }
    }

    private void setBaritoneGoal(Goal goal) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
    }

    private void resetContainerCounters() {
        this.containerOpenFailures = 0;
        this.repathAttempts = 0;
        this.alignTicks = 0;
        this.breakingStarted = false;
        this.moveTimeoutTicks = 0;
        this.pathGraceTicks = 0;
        this.openWaitTicks = 0;
        this.goalTier = 0;
        this.pathStartPos = null;
    }

    private void onContainerOpened() {
        this.stopAllMovement();
        this.mc.options.keyShift.setDown(false);
        this.resetContainerCounters();
        this.outputSkipResets = 0;
        this.movingToContainer = null;
        this.currentState = StashMover.ProcessState.TRANSFERRING_ITEMS;
        this.stateTimer = this.transferDelay.get();
        this.info("Container opened successfully!");
    }

    private void skipCurrentContainer(String reason) {
        this.cancelBaritone();
        this.stopAllMovement();
        if (this.currentContainer != null) {
            this.warning("Skipping container at " + this.currentContainer.pos.toShortString() + " (" + reason + ")");
            this.currentContainer.skipped = true;
        }

        this.currentContainer = null;
        this.movingToContainer = null;
        this.resetContainerCounters();
        this.currentState = this.isNearOutputArea() ? StashMover.ProcessState.OUTPUT_PROCESS : StashMover.ProcessState.INPUT_PROCESS;
    }

    private BlockPos getInteractBlock(StashMover.ContainerInfo container) {
        BlockPos pos = container.pos;
        if (container.type == StashMover.ContainerType.DOUBLE_CHEST || container.type == StashMover.ContainerType.DOUBLE_TRAPPED_CHEST) {
            BlockPos other = this.getOtherHalf(pos);
            if (other != null) {
                Vec3 eye = this.mc.player.getEyePosition();
                if (eye.distanceToSqr(Vec3.atCenterOf(other)) < eye.distanceToSqr(Vec3.atCenterOf(pos))) {
                    return other;
                }
            }
        }

        return pos;
    }

    private BlockPos getOtherHalf(BlockPos pos) {
        BlockState state = this.mc.level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(BlockStateProperties.CHEST_TYPE) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            ChestType type = state.getValue(BlockStateProperties.CHEST_TYPE);
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (type == ChestType.LEFT) {
                return pos.relative(facing.getClockWise());
            }

            if (type == ChestType.RIGHT) {
                return pos.relative(facing.getCounterClockWise());
            }
        }

        return null;
    }

    private BlockHitResult findVisibleHit(StashMover.ContainerInfo container, Vec3 eyePos) {
        BlockPos target = this.getInteractBlock(container);
        Vec3 aim = this.containerAimPoint(target, eyePos);
        if (aim == null) {
            return null;
        } else if (eyePos.distanceTo(aim) > this.containerReach.get() + 0.5) {
            return null;
        } else {
            BlockHitResult clip = this.mc.level.clip(new ClipContext(eyePos, aim, Block.OUTLINE, Fluid.NONE, this.mc.player));
            if (clip.getType() == Type.BLOCK) {
                BlockPos hp = clip.getBlockPos();
                return !hp.equals(target) && !hp.equals(this.getOtherHalf(target)) ? null : clip;
            } else {
                return new BlockHitResult(aim, this.nearestFace(target, aim), target, false);
            }
        }
    }

    private Vec3 containerAimPoint(BlockPos target, Vec3 eye) {
        BlockState state = this.mc.level.getBlockState(target);
        if (state.isAir()) {
            return null;
        }

        VoxelShape shape = state.getShape(this.mc.level, target);
        AABB box;
        if (shape.isEmpty()) {
            box = new AABB(target);
        } else {
            box = shape.bounds().move(target.getX(), target.getY(), target.getZ());
        }

        double x = clamp(eye.x, box.minX, box.maxX);
        double y = clamp(eye.y, box.minY, box.maxY);
        double z = clamp(eye.z, box.minZ, box.maxZ);
        return new Vec3(x, y, z);
    }

    private Direction nearestFace(BlockPos pos, Vec3 point) {
        double dx = point.x - (pos.getX() + 0.5);
        double dy = point.y - (pos.getY() + 0.5);
        double dz = point.z - (pos.getZ() + 0.5);
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ax >= ay && ax >= az) {
            return dx > 0.0 ? Direction.EAST : Direction.WEST;
        } else if (az >= ay) {
            return dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            return dy > 0.0 ? Direction.UP : Direction.DOWN;
        }
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    private boolean lookAtReal(Vec3 point) {
        float[] rot = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), point);
        return this.turnTowardsReal(rot[0], rot[1]);
    }

    private boolean turnTowardsReal(float targetYaw, float targetPitch) {
        float maxStep = (float)BepConfig.getRotationTurnSpeed();
        if (maxStep <= 0.0F) {
            maxStep = 45.0F;
        }

        float curYaw = this.mc.player.getYRot();
        float curPitch = this.mc.player.getXRot();
        float dYaw = Mth.wrapDegrees(targetYaw - curYaw);
        float dPitch = targetPitch - curPitch;
        float newYaw = curYaw + Mth.clamp(dYaw, -maxStep, maxStep);
        float newPitch = Mth.clamp(curPitch + Mth.clamp(dPitch, -maxStep, maxStep), -90.0F, 90.0F);
        this.mc.player.setYRot(newYaw);
        this.mc.player.setXRot(newPitch);
        this.mc.player.setYHeadRot(newYaw);
        return Math.abs(dYaw) <= 3.0F && Math.abs(dPitch) <= 3.0F;
    }

    private void lookAtBlockReal(BlockPos pos) {
        if (pos != null) {
            Vec3 eye = this.mc.player.getEyePosition();
            Vec3 aim = this.containerAimPoint(pos, eye);
            this.lookAtReal(aim != null ? aim : Vec3.atCenterOf(pos));
        }
    }

    private BlockHitResult blockHit(BlockPos pos, Vec3 eye) {
        Vec3 aim = this.containerAimPoint(pos, eye);
        if (aim == null) {
            return null;
        } else {
            BlockHitResult clip = this.mc.level.clip(new ClipContext(eye, aim, Block.OUTLINE, Fluid.NONE, this.mc.player));
            if (clip.getType() == Type.BLOCK) {
                BlockPos hp = clip.getBlockPos();
                return hp.equals(pos) ? clip : null;
            } else {
                return new BlockHitResult(aim, this.nearestFace(pos, aim), pos, false);
            }
        }
    }

    private void handleWaiting() {
        if (this.mc.screen instanceof ContainerScreen) {
            this.onContainerOpened();
        } else if (this.currentContainer == null) {
            this.currentState = this.isNearOutputArea() ? StashMover.ProcessState.OUTPUT_PROCESS : StashMover.ProcessState.INPUT_PROCESS;
        } else {
            this.lookAtBlockReal(this.getInteractBlock(this.currentContainer));
            if (this.openWaitTicks > 0) {
                this.openWaitTicks--;
            } else {
                this.containerOpenFailures++;
                if (this.containerOpenFailures >= this.maxRetries.get()) {
                    this.skipCurrentContainer("did not open after " + this.containerOpenFailures + " attempts");
                } else {
                    this.info("Container didn't open, retrying (" + this.containerOpenFailures + "/" + this.maxRetries.get() + ")");
                    this.alignTicks = 0;
                    this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
                    this.stateTimer = 0;
                }
            }
        }
    }

    private void stopAllMovement() {
        this.mc.options.keyUp.setDown(false);
        this.mc.options.keyDown.setDown(false);
        this.mc.options.keyLeft.setDown(false);
        this.mc.options.keyRight.setDown(false);
        this.mc.options.keyJump.setDown(false);
        this.mc.options.keyShift.setDown(false);
        this.mc.options.keySprint.setDown(false);
    }

    private void handleTransferringItems() {
        if (this.isNearInputArea()) {
            this.handleInputTransferringItems();
        } else if (this.isNearOutputArea()) {
            this.handleOutputTransferringItems();
        } else {
            this.currentState = StashMover.ProcessState.CHECKING_LOCATION;
        }
    }

    private void handleInputTransferringItems() {
        if (!(this.mc.screen instanceof ContainerScreen)) {
            if (this.currentContainer != null && !this.currentContainer.isEmpty && !this.currentContainer.isFull) {
                boolean inventoryHasSpace = false;

                for (int j = 0; j < 36; j++) {
                    if (this.mc.player.getInventory().getItem(j).isEmpty()) {
                        inventoryHasSpace = true;
                        break;
                    }
                }

                if (inventoryHasSpace) {
                    this.warning("Container window closed unexpectedly! Reopening...");
                    this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
                    this.stateTimer = 5;
                    this.containerOpenFailures++;
                    if (this.containerOpenFailures > 3) {
                        this.warning("Failed to reopen container multiple times, skipping");
                        this.currentContainer = null;
                        this.containerOpenFailures = 0;
                        this.currentState = StashMover.ProcessState.INPUT_PROCESS;
                    }

                    return;
                }
            }

            this.currentState = StashMover.ProcessState.INPUT_PROCESS;
        } else {
            if (this.currentContainer != null) {
                this.lookAtBlockReal(this.getInteractBlock(this.currentContainer));
            }

            if (!(this.mc.player.containerMenu instanceof ChestMenu handler)) {
                this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
            } else {
                if (this.onlyShulkers.get()) {
                    for (int j = 0; j < 36; j++) {
                        ItemStack invStack = this.mc.player.getInventory().getItem(j);
                        if (!invStack.isEmpty() && !this.isShulkerBox(invStack.getItem())) {
                            this.mc.player.closeContainer();
                            InvUtils.drop().slot(j);
                            this.info("Dropping non-shulker: " + invStack.getItem().getName().getString());
                            this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
                            this.stateTimer = 5;
                            return;
                        }
                    }
                }

                if (this.isInventoryFull()) {
                    this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                } else if (this.killBasedForward() && this.inventoryCargoCount() >= this.ecBufferTarget()) {
                    this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                } else {
                    int moved = 0;
                    boolean reachedCap = false;

                    for (int i = 0; i < this.currentContainer.totalSlots && moved < this.transferSpeed.get(); i++) {
                        Slot slot = handler.getSlot(i);
                        if (this.isCargo(slot.getItem())) {
                            this.mc.gameMode.handleInventoryMouseClick(handler.containerId, slot.index, 0, ClickType.QUICK_MOVE, this.mc.player);
                            this.itemsTransferred++;
                            moved++;
                            if (this.isInventoryFull() || this.killBasedForward() && this.inventoryCargoCount() >= this.ecBufferTarget()) {
                                reachedCap = true;
                                break;
                            }
                        }
                    }

                    if (moved > 0) {
                        if (reachedCap) {
                            this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                        }
                    } else {
                        boolean containerActuallyEmpty = true;

                        for (int i = 0; i < this.currentContainer.totalSlots; i++) {
                            Slot slot = handler.getSlot(i);
                            ItemStack stack = slot.getItem();
                            if (!stack.isEmpty() && (!this.onlyShulkers.get() || this.isShulkerBox(stack.getItem()))) {
                                containerActuallyEmpty = false;
                                break;
                            }
                        }

                        if (containerActuallyEmpty) {
                            this.currentContainer.isEmpty = true;
                            this.info("Container is now empty");
                        }

                        this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                    }
                }
            }
        }
    }

    private void handleClosingContainer() {
        if (this.mc.screen instanceof ContainerScreen) {
            this.mc.player.closeContainer();
        }

        this.stateTimer = this.closeDelay.get();
        if (this.isNearOutputArea()) {
            if (this.onlyShulkers.get()) {
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = this.mc.player.getInventory().getItem(i);
                    if (!stack.isEmpty() && !this.isShulkerBox(stack.getItem())) {
                        InvUtils.drop().slot(i);
                        this.info("Dropped non-shulker at output: " + stack.getItem().getName().getString());
                        this.stateTimer = 5;
                        return;
                    }
                }
            }

            this.currentContainer = null;
            this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
        } else if (this.isNearInputArea()) {
            if (this.onlyShulkers.get()) {
                boolean foundNonShulker = false;

                for (int i = 0; i < 36; i++) {
                    ItemStack stack = this.mc.player.getInventory().getItem(i);
                    if (!stack.isEmpty() && !this.isShulkerBox(stack.getItem())) {
                        InvUtils.drop().slot(i);
                        this.info("Dropped non-shulker: " + stack.getItem().getName().getString());
                        this.stateTimer = 5;
                        foundNonShulker = true;
                        if (this.currentContainer != null && !this.currentContainer.isEmpty) {
                            this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
                        } else {
                            this.currentState = StashMover.ProcessState.INPUT_PROCESS;
                        }

                        return;
                    }
                }

                if (!foundNonShulker) {
                    this.info("No non-shulker items to drop");
                }
            }

            if (this.currentContainer != null && this.currentContainer.isEmpty && this.breakEmptyContainers.get()) {
                this.breakingStarted = false;
                this.currentState = StashMover.ProcessState.BREAKING_CONTAINER;
                return;
            }

            if (this.killBasedForward()) {
                this.currentContainer = null;
                this.killFerryDecideInput();
                return;
            }

            if (this.isInventoryFull()) {
                this.info("Inventory full");
                if (this.fillEnderChest.get() && !this.isEnderChestFull()) {
                    this.info("Checking enderchest...");
                    this.findOrPlaceEnderChest();
                } else {
                    this.info("Inventory and enderchest full, travelling to output");
                    this.currentContainer = null;
                    this.startForwardTransport();
                }
            } else {
                this.currentContainer = null;
                this.currentState = StashMover.ProcessState.INPUT_PROCESS;
            }
        } else {
            this.currentState = StashMover.ProcessState.CHECKING_LOCATION;
        }
    }

    private void handleBreakingContainer() {
        if (this.currentContainer == null) {
            this.currentState = StashMover.ProcessState.INPUT_PROCESS;
        } else {
            BlockPos pos = this.currentContainer.pos;
            if (this.mc.level.getBlockState(pos).isAir()) {
                this.mc.gameMode.stopDestroyBlock();
                inputContainers.remove(this.currentContainer);
                this.containersProcessed++;
                this.currentContainer = null;
                this.breakingStarted = false;
                this.currentState = StashMover.ProcessState.INPUT_PROCESS;
                this.stateTimer = this.moveDelay.get();
            } else {
                this.cancelBaritone();
                this.mc.options.keyShift.setDown(false);
                Vec3 eyePos = this.mc.player.getEyePosition();
                Vec3 aim = this.containerAimPoint(pos, eyePos);
                if (aim != null && !(eyePos.distanceTo(aim) > this.containerReach.get() + 0.5)) {
                    boolean aligned = this.lookAtReal(aim);
                    if (this.breakingStarted || aligned) {
                        Direction face = this.nearestFace(pos, eyePos);
                        if (!this.breakingStarted) {
                            this.mc.gameMode.startDestroyBlock(pos, face);
                            this.breakingStarted = true;
                        } else {
                            this.mc.gameMode.continueDestroyBlock(pos, face);
                        }

                        this.mc.player.swing(InteractionHand.MAIN_HAND);
                    }
                } else {
                    this.warning("No clean angle to break container, skipping");
                    this.mc.gameMode.stopDestroyBlock();
                    this.breakingStarted = false;
                    this.currentContainer = null;
                    this.currentState = StashMover.ProcessState.INPUT_PROCESS;
                    this.stateTimer = this.moveDelay.get();
                }
            }
        }
    }

    private void findOrPlaceEnderChest() {
        this.enderChestPos = this.findNearbyEnderChest();
        if (this.enderChestPos == null) {
            FindItemResult enderChest = InvUtils.findInHotbar(Items.ENDER_CHEST);
            if (enderChest.found()) {
                BlockPos placePos = this.findSuitablePlacePos();
                if (placePos != null) {
                    this.placeEnderChest(placePos, enderChest.slot());
                    return;
                }
            }

            this.failEnderChest("no ender chest nearby or placeable");
        } else {
            this.ecOpenFailures = 0;
            this.ecRepathAttempts = 0;
            this.ecApproachTicks = 0;
            this.alignTicks = 0;
            this.pathGraceTicks = 0;
            this.openWaitTicks = 0;
            this.ecStandTargets.clear();
            this.pathStartPos = null;
            this.currentState = StashMover.ProcessState.OPENING_ENDERCHEST;
            this.stateTimer = 0;
        }
    }

    private void handleOpeningEnderChest() {
        if (this.mc.screen instanceof ContainerScreen) {
            this.ecOpenFailures = 0;
            this.ecRepathAttempts = 0;
            this.ecApproachTicks = 0;
            this.alignTicks = 0;
            this.pathGraceTicks = 0;
            this.openWaitTicks = 0;
            if (this.isNearOutputArea()) {
                this.currentState = StashMover.ProcessState.EMPTYING_ENDERCHEST;
            } else {
                this.currentState = StashMover.ProcessState.FILLING_ENDERCHEST;
            }

            this.stateTimer = this.transferDelay.get();
        } else if (this.openWaitTicks > 0) {
            this.openWaitTicks--;
            this.lookAtBlockReal(this.enderChestPos);
        } else if (this.ecOpenFailures >= this.maxRetries.get()) {
            this.failEnderChest("did not open after " + this.ecOpenFailures + " attempts");
        } else {
            if (this.enderChestPos == null || !(this.mc.level.getBlockState(this.enderChestPos).getBlock() instanceof EnderChestBlock)) {
                this.enderChestPos = this.findNearbyEnderChest();
                if (this.enderChestPos == null) {
                    FindItemResult enderChest = InvUtils.findInHotbar(Items.ENDER_CHEST);
                    if (enderChest.found()) {
                        BlockPos placePos = this.findSuitablePlacePos();
                        if (placePos != null) {
                            this.placeEnderChest(placePos, enderChest.slot());
                            return;
                        }
                    }

                    this.failEnderChest("no ender chest found or available to place");
                    return;
                }
            }

            Vec3 eyePos = this.mc.player.getEyePosition();
            BlockHitResult hit = this.blockHit(this.enderChestPos, eyePos);
            if (this.closeEnoughToOpen(hit, this.ecStandTargets, this.ecOpenFailures)) {
                this.cancelBaritone();
                this.mc.options.keyShift.setDown(false);
                if (!this.lookAtReal(hit.getLocation())) {
                    this.alignTicks = 0;
                    if (++this.ecApproachTicks > 600) {
                        this.failEnderChest("could not aim at the ender chest");
                    }
                } else if (this.alignTicks++ >= 1) {
                    this.info("Opening enderchest (attempt " + (this.ecOpenFailures + 1) + ")");
                    this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hit);
                    this.mc.player.swing(InteractionHand.MAIN_HAND);
                    this.alignTicks = 0;
                    this.ecApproachTicks = 0;
                    this.ecOpenFailures++;
                    this.openWaitTicks = this.openDelay.get();
                }
            } else if (++this.ecApproachTicks > 600) {
                this.failEnderChest("could not get in reach of the ender chest");
            } else if (this.pathGraceTicks > 0) {
                this.pathGraceTicks--;
            } else if (!this.isCalculatingPath()) {
                if (!this.isPathing()) {
                    if (++this.ecRepathAttempts > this.maxRetries.get()) {
                        this.failEnderChest("unreachable by Baritone");
                        return;
                    }

                    this.ecStandTargets.clear();
                    this.ecStandTargets.addAll(this.findValidStandingPositionsNear(this.enderChestPos));
                    boolean stalled = this.pathStartPos != null && this.mc.player.blockPosition().equals(this.pathStartPos);
                    if (!stalled && !this.ecStandTargets.isEmpty()) {
                        this.setBaritoneGoal(this.standingSpotsGoal(this.ecStandTargets));
                    } else {
                        this.setBaritoneGoal(new GoalGetToBlock(this.enderChestPos));
                    }

                    this.pathStartPos = this.mc.player.blockPosition();
                    this.info("Moving to enderchest (" + this.ecRepathAttempts + "/" + this.maxRetries.get() + ")");
                    this.pathGraceTicks = 10;
                }
            }
        }
    }

    private void failEnderChest(String reason) {
        this.cancelBaritone();
        this.stopAllMovement();
        this.mc.options.keyShift.setDown(false);
        this.ecOpenFailures = 0;
        this.ecRepathAttempts = 0;
        this.ecApproachTicks = 0;
        this.alignTicks = 0;
        this.pathGraceTicks = 0;
        this.openWaitTicks = 0;
        if (!this.isNearOutputArea()) {
            if (this.killBasedForward()) {
                this.error(
                    "Could not use the input ender chest ("
                        + reason
                        + "). Stopping to avoid losing items on the kill — place a reachable ender chest at the input area."
                );
                this.toggle();
            } else {
                this.warning("Skipping ender chest (" + reason + "), continuing without it");
                this.enderChestFull = true;
                this.currentContainer = null;
                if (this.isInventoryFull()) {
                    this.startForwardTransport();
                } else {
                    this.currentState = StashMover.ProcessState.INPUT_PROCESS;
                }
            }
        } else {
            if (!this.enderChestHasItems && !this.killBasedForward()) {
                this.warning("Skipping output ender chest (" + reason + ")");
                this.enderChestEmptied = true;
                this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
            } else {
                this.error(
                    "Could not open the output ender chest ("
                        + reason
                        + "). Stopping so ender-chest cargo isn't abandoned — place a reachable ender chest at the output area."
                );
                this.toggle();
            }
        }
    }

    private void handleFillingEnderChest() {
        if (!(this.mc.screen instanceof ContainerScreen)) {
            this.checkNextStepAfterEnderChest();
        } else {
            this.lookAtBlockReal(this.enderChestPos);
            if (!(this.mc.player.containerMenu instanceof ChestMenu handler)) {
                this.mc.player.closeContainer();
                this.checkNextStepAfterEnderChest();
            } else {
                boolean var7 = false;

                for (int moved = 0; moved < 27; moved++) {
                    if (handler.getSlot(moved).getItem().isEmpty()) {
                        var7 = true;
                        break;
                    }
                }

                if (!var7) {
                    this.enderChestFull = true;
                    this.ecFreeSlots = 0;
                    this.info("Enderchest is full");
                    this.mc.player.closeContainer();
                    this.stateTimer = this.closeDelay.get();
                    this.checkNextStepAfterEnderChest();
                } else {
                    int moved = 0;

                    for (int i = 27; i < 63 && moved < this.transferSpeed.get(); i++) {
                        Slot slot = handler.getSlot(i);
                        if (this.isCargo(slot.getItem())) {
                            if (this.countEnderChestFree(handler) == 0) {
                                this.enderChestFull = true;
                                this.ecFreeSlots = 0;
                                break;
                            }

                            this.mc.gameMode.handleInventoryMouseClick(handler.containerId, slot.index, 0, ClickType.QUICK_MOVE, this.mc.player);
                            this.itemsTransferred++;
                            this.enderChestHasItems = true;
                            moved++;
                        }
                    }

                    if (moved <= 0) {
                        boolean inventoryHasItems = false;

                        for (int i = 0; i < 36; i++) {
                            ItemStack invStack = this.mc.player.getInventory().getItem(i);
                            if (!invStack.isEmpty() && (!this.onlyShulkers.get() || this.isShulkerBox(invStack.getItem()))) {
                                inventoryHasItems = true;
                                break;
                            }
                        }

                        if (inventoryHasItems) {
                            this.stateTimer = this.transferDelay.get();
                        } else {
                            this.ecFreeSlots = this.countEnderChestFree(handler);
                            this.mc.player.closeContainer();
                            this.stateTimer = this.closeDelay.get();
                            this.checkNextStepAfterEnderChest();
                        }
                    }
                }
            }
        }
    }

    private int countEnderChestFree(ChestMenu handler) {
        int free = 0;

        for (int j = 0; j < 27; j++) {
            if (handler.getSlot(j).getItem().isEmpty()) {
                free++;
            }
        }

        return free;
    }

    private boolean containerHasFreeSlot(ChestMenu handler, int totalSlots) {
        for (int i = 0; i < totalSlots; i++) {
            if (handler.getSlot(i).getItem().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private boolean hasFreeInventorySlot() {
        for (int i = 0; i < 36; i++) {
            if (this.mc.player.getInventory().getItem(i).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private void checkNextStepAfterEnderChest() {
        this.currentContainer = null;
        if (this.killBasedForward()) {
            this.killFerryDecideInput();
        } else {
            if (this.isInventoryFull() && this.enderChestFull) {
                this.info("Both inventory and enderchest are full, travelling to output");
                this.startForwardTransport();
            } else {
                this.currentState = StashMover.ProcessState.INPUT_PROCESS;
            }
        }
    }

    private void handleEmptyingEnderChest() {
        if (!(this.mc.screen instanceof ContainerScreen)) {
            if (this.hasItemsToTransfer()) {
                this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
            } else {
                this.enderChestEmptied = true;
                this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
            }
        } else {
            this.lookAtBlockReal(this.enderChestPos);
            if (!(this.mc.player.containerMenu instanceof ChestMenu handler)) {
                this.mc.player.closeContainer();
                this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
            } else {
                boolean var7 = false;

                for (int enderChestIsEmpty = 0; enderChestIsEmpty < 36; enderChestIsEmpty++) {
                    if (this.mc.player.getInventory().getItem(enderChestIsEmpty).isEmpty()) {
                        var7 = true;
                        break;
                    }
                }

                if (!var7) {
                    this.info("Inventory full, closing enderchest to deposit items");
                    this.mc.player.closeContainer();
                    this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
                } else {
                    boolean enderChestIsEmpty = true;

                    for (int i = 0; i < 27; i++) {
                        if (!handler.getSlot(i).getItem().isEmpty()) {
                            enderChestIsEmpty = false;
                            break;
                        }
                    }

                    if (enderChestIsEmpty) {
                        this.enderChestHasItems = false;
                        this.enderChestEmptied = true;
                        this.enderChestFull = false;
                        this.ecFreeSlots = 27;
                        this.info("Enderchest is empty, all items transferred");
                        this.mc.player.closeContainer();
                        this.stateTimer = this.closeDelay.get();
                        this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
                    } else {
                        int moved = 0;

                        for (int i = 0; i < 27 && moved < this.transferSpeed.get(); i++) {
                            Slot slot = handler.getSlot(i);
                            if (this.isCargo(slot.getItem())) {
                                if (!this.hasFreeInventorySlot()) {
                                    break;
                                }

                                this.mc.gameMode.handleInventoryMouseClick(handler.containerId, slot.index, 0, ClickType.QUICK_MOVE, this.mc.player);
                                this.itemsTransferred++;
                                moved++;
                            }
                        }

                        if (moved <= 0) {
                            if (var7 && this.onlyShulkers.get()) {
                                this.info("Only non-shulkers left in enderchest");
                                this.enderChestHasItems = false;
                                this.enderChestEmptied = true;
                                this.mc.player.closeContainer();
                                this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
                            } else {
                                this.info("Inventory full, depositing items first");
                                this.mc.player.closeContainer();
                                this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
                            }
                        }
                    }
                }
            }
        }
    }

    private void handlePearlLoading() {
        if (!this.waitingForPearl) {
            this.sendPearlCommand();
            this.waitingForPearl = true;
            this.lastPearlMessageTime = System.currentTimeMillis();
            this.pearlRetryCount = 0;
            this.initialPlayerPos = this.mc.player.position();
        }

        Vec3 currentPos = this.mc.player.position();
        double distance = currentPos.distanceTo(this.initialPlayerPos);
        if (distance > 100.0) {
            if (this.isNearOutputArea()) {
                if (++this.arrivalSettleTicks < 20) {
                    return;
                }

                this.arrivalSettleTicks = 0;
                this.info("Successfully pearl loaded to output area!");
                this.waitingForPearl = false;
                this.ensureOffhandHasItem();
                this.currentState = StashMover.ProcessState.RESET_PEARL_PICKUP;
                this.hasThrownPearl = false;
                this.hasPlacedShulker = false;
                this.isGoingToInput = false;
                return;
            }

            if (!this.isNearInputArea()) {
                this.warning("Teleported but not to output area, retrying...");
                this.waitingForPearl = false;
                this.currentState = StashMover.ProcessState.LOADING_PEARL;
                return;
            }
        }

        if (System.currentTimeMillis() - this.lastPearlMessageTime > this.pearlTimeout.get() * 1000) {
            if (this.pearlRetryCount < this.maxRetries.get()) {
                this.pearlRetryCount++;
                this.info("Pearl loading timeout, retrying (attempt " + this.pearlRetryCount + "/" + this.maxRetries.get() + ")");
                this.sendPearlCommand();
                this.lastPearlMessageTime = System.currentTimeMillis();
            } else {
                this.error("Pearl loading failed after " + this.maxRetries.get() + " retries! Stopping.");
                this.waitingForPearl = false;
                this.toggle();
            }
        }
    }

    private void sendPearlCommand() {
        String randomSuffix = this.generateRandomString(8);
        String command = String.format("/msg %s %s %s", this.pearlPlayerName.get(), this.pearlCommand.get(), randomSuffix);
        ChatUtils.sendPlayerMsg(command);
        this.info("Sent pearl command: " + command);
    }

    private void handleResetPearlPickup() {
        BlockPos pickupPos;
        BlockPos throwPos;
        if (this.isGoingToInput) {
            pickupPos = this.inputPearlPickupPos.get();
            throwPos = this.inputPearlThrowPos.get();
        } else {
            pickupPos = this.outputPearlPickupPos.get();
            throwPos = this.outputPearlThrowPos.get();
        }

        if (this.isUnsetPos(pickupPos) || this.isUnsetPos(throwPos)) {
            this.error("Pearl reset positions for the " + (this.isGoingToInput ? "input" : "output") + " side are not configured! Stopping.");
            this.toggle();
        } else if (!this.hasPlacedShulker && !this.hasThrownPearl && this.isStasisArmed(throwPos)) {
            this.info("Stasis already armed (pearl at throw position), skipping reset");
            this.resumeAfterPearlReset();
        } else {
            this.ensureOffhandHasItem();
            double distance = this.mc.player.position().distanceTo(Vec3.atCenterOf(pickupPos));
            if (distance > 3.0) {
                GoalBlock goal = new GoalBlock(pickupPos);
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                this.stateTimer = this.moveDelay.get();
            } else {
                this.pearlPickupWaitRuns = 0;
                this.currentState = StashMover.ProcessState.RESET_PEARL_PLACE_SHULKER;
                this.stateTimer = 10;
            }
        }
    }

    private boolean isUnsetPos(BlockPos p) {
        return p.getX() == 0 && p.getY() == 64 && p.getZ() == 0;
    }

    private boolean isStasisArmed(BlockPos throwPos) {
        AABB box = new AABB(
            throwPos.getX() - 2,
            throwPos.getY() - 2,
            throwPos.getZ() - 2,
            throwPos.getX() + 3,
            throwPos.getY() + 3,
            throwPos.getZ() + 3
        );
        return !this.mc.level.getEntitiesOfClass(ThrownEnderpearl.class, box, e -> true).isEmpty();
    }

    private void resumeAfterPearlReset() {
        if (this.isNearInputArea()) {
            this.detectContainersInArea(inputAreaPos1, inputAreaPos2, true, true);
            this.currentState = StashMover.ProcessState.INPUT_PROCESS;
            this.resumeInputPickup();
        } else if (this.isNearOutputArea()) {
            this.detectContainersInArea(outputAreaPos1, outputAreaPos2, false, true);
            this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
            this.stateTimer = 5;
        } else {
            this.currentState = StashMover.ProcessState.CHECKING_LOCATION;
        }
    }

    private void handleResetPearlPlaceShulker() {
        if (!this.hasPlacedShulker) {
            ItemStack slot0 = this.mc.player.getInventory().getItem(0);
            if (slot0.getItem() == Items.ENDER_PEARL) {
                for (int i = 1; i < 36; i++) {
                    if (this.mc.player.getInventory().getItem(i).isEmpty()) {
                        InvUtils.move().from(0).to(i);
                        this.info("Moved pearl from slot 0 temporarily");
                        break;
                    }
                }
            }

            this.ensureOffhandHasItem();
            slot0 = this.mc.player.getInventory().getItem(0);
            if (this.isShulkerBox(slot0.getItem())) {
                this.offhandBackup = this.mc.player.getOffhandItem().copy();
                if (!this.mc.player.getOffhandItem().isEmpty()) {
                    for (int i = 1; i < 36; i++) {
                        if (this.mc.player.getInventory().getItem(i).isEmpty()) {
                            InvUtils.move().fromOffhand().to(i);
                            break;
                        }
                    }
                }

                InvUtils.move().from(0).toOffhand();
                this.hasPlacedShulker = true;
                this.info("Placed shulker in offhand, now walking to pressure plate");
                BlockPos pickupPos = this.isGoingToInput ? this.inputPearlPickupPos.get() : this.outputPearlPickupPos.get();
                GoalBlock goal = new GoalBlock(pickupPos);
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                this.stateTimer = 30;
            } else {
                this.info("No shulker in slot 0, continuing without offhand shulker");
                this.hasPlacedShulker = true;
                this.stateTimer = 5;
            }
        } else {
            FindItemResult pearl = InvUtils.find(Items.ENDER_PEARL);
            if (pearl.found()) {
                this.info("Pearl picked up, moving to throw location");
                this.currentState = StashMover.ProcessState.RESET_PEARL_APPROACH;
                this.stateTimer = 5;
            } else {
                if (++this.pearlPickupWaitRuns > 100) {
                    this.error(
                        "No pearl appeared at the "
                            + (this.isGoingToInput ? "input" : "output")
                            + " pickup position — check the pearl supply/dispenser. Stopping."
                    );
                    this.restoreOffhandItem();
                    this.toggle();
                    return;
                }

                BlockPos pickupPos = this.isGoingToInput ? this.inputPearlPickupPos.get() : this.outputPearlPickupPos.get();
                double distance = this.mc.player.position().distanceTo(Vec3.atCenterOf(pickupPos));
                if (distance > 1.0) {
                    GoalBlock goal = new GoalBlock(pickupPos);
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                }

                this.stateTimer = 5;
            }
        }
    }

    private void handleResetPearlApproach() {
        BlockPos throwPos;
        if (this.isGoingToInput) {
            throwPos = this.inputPearlThrowPos.get();
        } else {
            throwPos = this.outputPearlThrowPos.get();
        }

        double distance = this.mc.player.position().distanceTo(Vec3.atCenterOf(throwPos));
        if (distance <= 1.5) {
            if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            }

            this.lastBaritoneGoal = null;
            this.safeRetreatPos = this.mc.player.blockPosition();
            this.info("Starting precise positioning from adjacent block - stored safe retreat position");
            this.prepareApproachTicks = 0;
            this.currentState = StashMover.ProcessState.RESET_PEARL_PREPARE;
            this.stateTimer = 5;
        } else if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            this.stateTimer = 5;
        } else {
            BlockPos goalPos = null;
            this.safeRetreatPos = null;
            Direction[] preferredDirections = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

            for (Direction dir : preferredDirections) {
                BlockPos adjacent = throwPos.relative(dir);
                BlockState adjacentState = this.mc.level.getBlockState(adjacent);
                BlockState belowState = this.mc.level.getBlockState(adjacent.below());
                if (adjacentState.isAir() && belowState.isRedstoneConductor(this.mc.level, adjacent.below())) {
                    goalPos = adjacent;
                    this.safeRetreatPos = adjacent;
                    this.info("Found safe approach position from " + dir + " side");
                    break;
                }
            }

            if (goalPos == null) {
                for (Direction dir : preferredDirections) {
                    BlockPos candidate = throwPos.relative(dir, 2);
                    BlockState state = this.mc.level.getBlockState(candidate);
                    BlockState belowState = this.mc.level.getBlockState(candidate.below());
                    if (state.isAir() && belowState.isRedstoneConductor(this.mc.level, candidate.below())) {
                        goalPos = candidate;
                        this.safeRetreatPos = throwPos.relative(dir);
                        this.info("Using fallback approach position from " + dir + " side");
                        break;
                    }
                }
            }

            if (goalPos == null) {
                goalPos = throwPos.relative(Direction.NORTH, 2);
                this.safeRetreatPos = throwPos.relative(Direction.NORTH);
                this.warning("Using fallback approach position");
            }

            if (this.lastBaritoneGoal == null || !this.lastBaritoneGoal.equals(goalPos)) {
                this.lastBaritoneGoal = goalPos;
                GoalBlock goal = new GoalBlock(goalPos);
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                this.info("Pathing to approach position");
            }

            this.stateTimer = this.moveDelay.get();
        }
    }

    private void handleResetPearlPrepare() {
        BlockPos throwPos;
        double throwYaw;
        double throwPitch;
        if (this.isGoingToInput) {
            throwPos = this.inputPearlThrowPos.get();
            throwYaw = this.inputPearlThrowYaw.get();
            throwPitch = this.inputPearlThrowPitch.get();
        } else {
            throwPos = this.outputPearlThrowPos.get();
            throwYaw = this.outputPearlThrowYaw.get();
            throwPitch = this.outputPearlThrowPitch.get();
        }

        this.mc.options.keyShift.setDown(true);
        double targetX = throwPos.getX() + 0.5;
        double targetZ = throwPos.getZ() + 0.5;
        double dx = targetX - this.mc.player.getX();
        double dz = targetZ - this.mc.player.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double requiredYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double requiredPitch = 15.0;
        this.mc.player.setYRot((float)requiredYaw);
        this.mc.player.setXRot((float)requiredPitch);
        boolean inPosition = horizontalDistance < this.positionTolerance.get();
        if (!inPosition) {
            this.prepareApproachTicks++;
            this.mc.options.keyDown.setDown(false);
            this.mc.options.keyLeft.setDown(false);
            this.mc.options.keyRight.setDown(false);
            this.mc.options.keyShift.setDown(true);
            this.mc.options.keyUp.setDown(true);
            if (this.prepareApproachTicks % 20 == 0) {
                this.info(String.format("Approaching throw spot (%.2f blocks away) Yaw: %.1f", horizontalDistance, requiredYaw));
            }

            if (this.prepareApproachTicks > 60 && horizontalDistance > 2.0) {
                if (this.prepareApproachTicks % 40 < 5) {
                    this.mc.options.keyUp.setDown(false);
                    this.mc.options.keyDown.setDown(true);
                    this.info("Backing up briefly to unstick");
                } else {
                    this.mc.options.keyDown.setDown(false);
                    this.mc.options.keyUp.setDown(true);
                }
            }

            if (this.prepareApproachTicks > 120 && horizontalDistance < this.positionTolerance.get() * 1.5) {
                this.info("Close enough after timeout");
                inPosition = true;
            } else if (this.prepareApproachTicks > 200) {
                this.warning("Could not reach throw spot, re-pathing");
                this.stopAllMovement();
                this.lastBaritoneGoal = null;
                this.prepareApproachTicks = 0;
                this.currentState = StashMover.ProcessState.RESET_PEARL_APPROACH;
                this.stateTimer = 5;
                return;
            }

            if (!inPosition) {
                return;
            }
        }

        this.mc.options.keyUp.setDown(false);
        this.mc.options.keyDown.setDown(false);
        this.mc.options.keyLeft.setDown(false);
        this.mc.options.keyRight.setDown(false);
        this.mc.options.keyShift.setDown(true);
        Rotations.rotate(throwYaw, throwPitch);
        this.mc.player.setYRot((float)throwYaw);
        this.mc.player.setXRot((float)throwPitch);
        this.info("Switching to throw angle - Yaw: " + String.format("%.3f", throwYaw) + " Pitch: " + String.format("%.3f", throwPitch));
        this.info("In position, ready to throw");
        this.prepareApproachTicks = 0;
        this.currentState = StashMover.ProcessState.RESET_PEARL_THROW;
        this.stateTimer = 5;
    }

    private void handleResetPearlThrow() {
        double throwYaw;
        double throwPitch;
        if (this.isGoingToInput) {
            throwYaw = this.inputPearlThrowYaw.get();
            throwPitch = this.inputPearlThrowPitch.get();
        } else {
            throwYaw = this.outputPearlThrowYaw.get();
            throwPitch = this.outputPearlThrowPitch.get();
        }

        if (!this.hasThrownPearl) {
            this.mc.options.keyShift.setDown(true);
            if (!this.rotationSet) {
                Rotations.rotate(throwYaw, throwPitch);
                this.mc.player.setYRot((float)throwYaw);
                this.mc.player.setXRot((float)throwPitch);
                this.info("Set exact throw angle: Yaw=" + String.format("%.3f", throwYaw) + " Pitch=" + String.format("%.3f", throwPitch));
                this.rotationSet = true;
                this.rotationStabilizationTimer = 10;
                return;
            }

            if (this.rotationStabilizationTimer > 0) {
                Rotations.rotate(throwYaw, throwPitch);
                this.mc.player.setYRot((float)throwYaw);
                this.mc.player.setXRot((float)throwPitch);
                this.rotationStabilizationTimer--;
                if (this.rotationStabilizationTimer == 0) {
                    this.info("Rotation stabilized, ready to throw");
                }

                return;
            }

            FindItemResult pearl = InvUtils.find(Items.ENDER_PEARL);
            if (pearl.found()) {
                if (this.mc.player.getMainHandItem().getItem() != Items.ENDER_PEARL) {
                    this.previousSlot = ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot();
                    InvUtils.swap(pearl.slot(), false);
                    this.stateTimer = 3;
                    return;
                }

                this.initialPlayerPos = this.mc.player.position();
                this.info("Throwing pearl");
                this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
                this.hasThrownPearl = true;
                this.pearlThrowTime = System.currentTimeMillis();
                this.info("Pearl thrown! Walking back immediately!");
                this.mc.options.keyShift.setDown(true);
                this.mc.options.keyUp.setDown(false);
                this.mc.options.keyLeft.setDown(false);
                this.mc.options.keyRight.setDown(false);
                this.mc.options.keySprint.setDown(false);
                this.mc.options.keyDown.setDown(true);
                ((InputAccessor)this.mc.player.input).setMovementForward(-1.0F);
                ((InputAccessor)this.mc.player.input).setMovementSideways(0.0F);
                this.rotationSet = false;
                this.retreatTicks = 20;
                this.currentState = StashMover.ProcessState.RESET_PEARL_WAIT;
            } else {
                this.error("No ender pearl to arm the " + (this.isGoingToInput ? "input" : "output") + " stasis! Stopping.");
                this.mc.options.keyShift.setDown(false);
                this.restoreOffhandItem();
                this.toggle();
            }
        }
    }

    private void handleResetPearlWait() {
        if (this.retreatTicks > 0) {
            this.mc.options.keyShift.setDown(true);
            double throwYaw;
            double throwPitch;
            if (this.isGoingToInput) {
                throwYaw = this.inputPearlThrowYaw.get();
                throwPitch = this.inputPearlThrowPitch.get();
            } else {
                throwYaw = this.outputPearlThrowYaw.get();
                throwPitch = this.outputPearlThrowPitch.get();
            }

            Rotations.rotate(throwYaw, throwPitch);
            this.mc.player.setYRot((float)throwYaw);
            this.mc.player.setXRot((float)throwPitch);
            this.mc.options.keyUp.setDown(false);
            this.mc.options.keyLeft.setDown(false);
            this.mc.options.keyRight.setDown(false);
            this.mc.options.keySprint.setDown(false);
            this.mc.options.keyDown.setDown(true);
            ((InputAccessor)this.mc.player.input).setMovementForward(-1.0F);
            ((InputAccessor)this.mc.player.input).setMovementSideways(0.0F);
            if (this.safeRetreatPos != null && this.mc.player.position().distanceTo(Vec3.atCenterOf(this.safeRetreatPos)) < 0.5) {
                this.info("Reached safe position!");
                this.retreatTicks = 1;
            }

            if (--this.retreatTicks == 0) {
                this.info("Safe distance reached");
                this.mc.options.keyUp.setDown(false);
                this.mc.options.keyDown.setDown(false);
                ((InputAccessor)this.mc.player.input).setMovementForward(0.0F);
                this.mc.options.keyShift.setDown(false);
                Rotations.rotate(this.mc.player.getYRot(), this.mc.player.getXRot());
                if (this.initialPlayerPos == null) {
                    this.initialPlayerPos = this.mc.player.position();
                }
            }
        } else {
            this.mc.options.keyShift.setDown(false);
            this.mc.options.keyUp.setDown(false);
            this.mc.options.keyDown.setDown(false);
            this.mc.options.keyLeft.setDown(false);
            this.mc.options.keyRight.setDown(false);
            this.mc.options.keySprint.setDown(false);
            Rotations.rotate(this.mc.player.getYRot(), this.mc.player.getXRot());
            if (System.currentTimeMillis() - this.pearlThrowTime > this.pearlWaitTime.get() * 1000) {
                double distance = this.mc.player.position().distanceTo(this.initialPlayerPos);
                FindItemResult pearlCheck = InvUtils.find(Items.ENDER_PEARL);
                boolean stillHasPearl = pearlCheck.found() && pearlCheck.count() > 0;
                if (distance < 5.0 && !stillHasPearl) {
                    this.info("Pearl successfully placed in stasis (no pearl in inventory)");
                    this.restoreOffhandItem();
                    if (this.previousSlot >= 0 && this.previousSlot < 9) {
                        ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(this.previousSlot);
                        this.previousSlot = -1;
                    }

                    this.hasThrownPearl = false;
                    this.hasPlacedShulker = false;
                    this.pearlFailRetries = 0;
                    this.lastBaritoneGoal = null;
                    this.rotationSet = false;
                    this.rotationStabilizationTimer = 0;
                    this.safeRetreatPos = null;
                    Rotations.rotate(this.mc.player.getYRot(), this.mc.player.getXRot());
                    this.info("Continuing to " + (this.isNearInputArea() ? "input" : "output") + " process");
                    this.resumeAfterPearlReset();
                } else {
                    this.warning("Pearl was loaded! Teleportation detected");
                    this.pearlFailRetries++;
                    if (this.pearlFailRetries < this.maxRetries.get()) {
                        this.warning("Pearl throw failed, retrying (attempt " + this.pearlFailRetries + "/" + this.maxRetries.get() + ")");
                        this.restoreOffhandItem();
                        this.hasThrownPearl = false;
                        this.hasPlacedShulker = false;
                        this.rotationSet = false;
                        this.rotationStabilizationTimer = 0;
                        this.currentState = StashMover.ProcessState.RESET_PEARL_PICKUP;
                    } else {
                        this.error("Pearl throw failed after " + this.maxRetries.get() + " attempts! Stopping.");
                        this.restoreOffhandItem();
                        this.toggle();
                    }
                }
            }
        }
    }

    private void restoreOffhandItem() {
        ItemStack offhandItem = this.mc.player.getOffhandItem();
        if (!offhandItem.isEmpty() && this.isShulkerBox(offhandItem.getItem())) {
            this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, 45, 0, ClickType.PICKUP, this.mc.player);
            this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, 36, 0, ClickType.PICKUP, this.mc.player);
            this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, 45, 0, ClickType.PICKUP, this.mc.player);
            this.info("Moved shulker back to hotbar slot 0");
        }

        if (!this.offhandBackup.isEmpty()) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = this.mc.player.getInventory().getItem(i);
                if (ItemStack.matches(stack, this.offhandBackup)) {
                    this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, 45, 0, ClickType.PICKUP, this.mc.player);
                    this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, i < 9 ? i + 36 : i, 0, ClickType.PICKUP, this.mc.player);
                    this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, 45, 0, ClickType.PICKUP, this.mc.player);
                    this.info("Restored original offhand item");
                    break;
                }
            }
        }

        this.offhandBackup = ItemStack.EMPTY;
    }

    private void handleOutputProcess() {
        this.enderChestFull = false;
        if (this.hasItemsToTransfer()) {
            if (this.currentContainer == null) {
                this.findNextOutputContainer();
            } else {
                this.moveToContainer(this.currentContainer);
            }
        } else {
            if (this.useEnderChest()) {
                if (!this.enderChestEmptied) {
                    this.info("Inventory empty, checking enderchest for items...");
                    this.enderChestPos = this.findNearbyEnderChest();
                    if (this.enderChestPos != null) {
                        this.ecOpenFailures = 0;
                        this.ecRepathAttempts = 0;
                        this.ecApproachTicks = 0;
                        this.alignTicks = 0;
                        this.pathGraceTicks = 0;
                        this.openWaitTicks = 0;
                        this.currentState = StashMover.ProcessState.OPENING_ENDERCHEST;
                        this.stateTimer = 5;
                        return;
                    }

                    FindItemResult enderChest = InvUtils.findInHotbar(Items.ENDER_CHEST);
                    if (enderChest.found()) {
                        BlockPos placePos = this.findSuitablePlacePos();
                        if (placePos != null) {
                            this.info("Placing enderchest to check for items");
                            this.placeEnderChest(placePos, enderChest.slot());
                            return;
                        }
                    }

                    if (this.enderChestHasItems || this.killBasedForward()) {
                        this.error(
                            "Output area has no reachable ender chest to dump into. Stopping so the ender-chest cargo isn't carried back — place an ender chest at the output area."
                        );
                        this.toggle();
                        return;
                    }

                    this.warning("No enderchest available, skipping enderchest check");
                    this.enderChestEmptied = true;
                } else {
                    this.info("All items deposited and enderchest verified empty, going back to input");
                    this.enderChestEmptied = false;
                    this.startReverseTransport();
                }
            } else {
                this.info("All items deposited, going back to input");
                this.startReverseTransport();
            }
        }
    }

    private void findNextOutputContainer() {
        this.currentContainer = this.nearestUsableOutput();
        if (this.currentContainer == null) {
            if (outputContainers.stream().anyMatch(cx -> !cx.isFull)) {
                if (this.outputSkipResets >= 3) {
                    this.error("Could not open the remaining output containers! Stopping so the cargo isn't lost.");
                    this.toggle();
                    return;
                }

                this.outputSkipResets++;
                this.warning("Ran out of usable output containers, re-scanning and retrying skipped ones (attempt " + this.outputSkipResets + "/3)");
                this.detectContainersInArea(outputAreaPos1, outputAreaPos2, false, true);

                for (StashMover.ContainerInfo c : outputContainers) {
                    c.skipped = false;
                }

                this.currentContainer = this.nearestUsableOutput();
                if (this.currentContainer != null) {
                    this.moveToContainer(this.currentContainer);
                    return;
                }
            }

            this.info("All output containers verified full, re-scanning to confirm...");
            this.detectContainersInArea(outputAreaPos1, outputAreaPos2, false);
            this.currentContainer = this.nearestUsableOutput();
            if (this.currentContainer == null) {
                this.error("All output containers are full! Stopping — add storage at the output area.");
                this.toggle();
            } else {
                this.info("Found available container after rescan");
                this.moveToContainer(this.currentContainer);
            }
        } else {
            this.info("Moving to output container");
            this.moveToContainer(this.currentContainer);
        }
    }

    private StashMover.ContainerInfo nearestUsableOutput() {
        return outputContainers.stream()
            .filter(c -> !c.isFull && !c.skipped)
            .min(Comparator.comparingDouble(c -> this.mc.player.position().distanceTo(Vec3.atCenterOf(c.pos))))
            .orElse(null);
    }

    private void handleOutputTransferringItems() {
        if (!(this.mc.screen instanceof ContainerScreen)) {
            if (this.currentContainer != null && !this.currentContainer.isFull) {
                boolean hasItems = false;

                for (int i = 0; i < 36; i++) {
                    if (!this.mc.player.getInventory().getItem(i).isEmpty()) {
                        hasItems = true;
                        break;
                    }
                }

                if (hasItems) {
                    this.warning("Container window closed unexpectedly! Reopening...");
                    this.currentState = StashMover.ProcessState.OPENING_CONTAINER;
                    this.stateTimer = 5;
                    this.containerOpenFailures++;
                    if (this.containerOpenFailures > 3) {
                        this.warning("Failed to reopen container multiple times, skipping it this visit");
                        this.currentContainer.skipped = true;
                        this.currentContainer = null;
                        this.containerOpenFailures = 0;
                        this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
                    }

                    return;
                }
            }

            this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
        } else {
            if (this.currentContainer != null) {
                this.lookAtBlockReal(this.getInteractBlock(this.currentContainer));
            }

            if (!(this.mc.player.containerMenu instanceof ChestMenu handler)) {
                this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
            } else {
                boolean var10 = false;

                for (int hasItems = 0; hasItems < this.currentContainer.totalSlots; hasItems++) {
                    if (handler.getSlot(hasItems).getItem().isEmpty()) {
                        var10 = true;
                        break;
                    }
                }

                if (!var10) {
                    this.currentContainer.isFull = true;
                    this.info("Container is now full");
                    this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                } else {
                    boolean hasItems = false;

                    for (int i = 0; i < 36; i++) {
                        if (!this.mc.player.getInventory().getItem(i).isEmpty()) {
                            hasItems = true;
                            break;
                        }
                    }

                    if (!hasItems) {
                        this.info("No items left to transfer");
                        this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                    } else {
                        int playerInventoryStart = this.currentContainer.totalSlots;
                        int moved = 0;
                        boolean containerFilled = false;

                        for (int i = playerInventoryStart; i < playerInventoryStart + 36 && moved < this.transferSpeed.get(); i++) {
                            Slot slot = handler.getSlot(i);
                            if (this.isCargo(slot.getItem())) {
                                if (!this.containerHasFreeSlot(handler, this.currentContainer.totalSlots)) {
                                    containerFilled = true;
                                    break;
                                }

                                this.mc.gameMode.handleInventoryMouseClick(handler.containerId, slot.index, 0, ClickType.QUICK_MOVE, this.mc.player);
                                this.itemsTransferred++;
                                moved++;
                            }
                        }

                        if (containerFilled) {
                            this.currentContainer.isFull = true;
                            this.info("Container is now full");
                            this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                        } else if (moved <= 0) {
                            boolean inventoryEmpty = true;

                            for (int i = 0; i < 36; i++) {
                                if (!this.mc.player.getInventory().getItem(i).isEmpty()) {
                                    inventoryEmpty = false;
                                    break;
                                }
                            }

                            if (inventoryEmpty) {
                                if (this.enderChestHasItems && this.useEnderChest()) {
                                    this.info("Inventory empty but enderchest has items, retrieving from enderchest");
                                    this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                                } else {
                                    this.info("Inventory and enderchest empty");
                                    this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                                }
                            } else {
                                this.currentContainer.isFull = true;
                                this.info("Container is now full");
                                this.currentState = StashMover.ProcessState.CLOSING_CONTAINER;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean hasItemsInEnderChest() {
        return this.enderChestHasItems && !this.enderChestEmptied;
    }

    private boolean killBasedForward() {
        StashMover.TransportMethod m = this.forwardMethod.get();
        return m == StashMover.TransportMethod.KILL || m == StashMover.TransportMethod.KILL_POSITION;
    }

    private boolean useEnderChest() {
        return this.fillEnderChest.get() || this.killBasedForward();
    }

    private int ecBufferTarget() {
        return this.ecFreeSlots >= 0 ? this.ecFreeSlots : 27;
    }

    private boolean isCargo(ItemStack s) {
        return !s.isEmpty() && (!this.onlyShulkers.get() || this.isShulkerBox(s.getItem()));
    }

    private int inventoryCargoCount() {
        int n = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack s = this.mc.player.getInventory().getItem(i);
            if (!s.isEmpty() && (!this.onlyShulkers.get() || this.isShulkerBox(s.getItem()))) {
                n++;
            }
        }

        return n;
    }

    private StashMover.ContainerInfo nearestInputWithItems() {
        return inputContainers.stream()
            .filter(c -> !c.isEmpty && !c.skipped)
            .min(Comparator.comparingDouble(c -> this.mc.player.position().distanceTo(Vec3.atCenterOf(c.pos))))
            .orElse(null);
    }

    private boolean enderChestAvailable() {
        return this.findNearbyEnderChest() != null || InvUtils.findInHotbar(Items.ENDER_CHEST).found();
    }

    private void resumeInputPickup() {
        if (this.killBasedForward()) {
            this.ecFreeSlots = -1;
            this.killFerryDecideInput();
        } else {
            this.findNextInputContainer();
        }
    }

    private void killFerryDecideInput() {
        this.currentContainer = null;
        int cargo = this.inventoryCargoCount();
        if (this.enderChestFull) {
            if (cargo == 0) {
                this.startForwardTransport();
            } else {
                this.error(
                    "Ender-chest ferry: the ender chest is full but "
                        + cargo
                        + " stack(s) remain in the inventory. Stopping to avoid losing them on death — drain the ender chest or add capacity."
                );
                this.toggle();
            }
        } else if (this.ecFreeSlots < 0) {
            this.fillEnderChestOrFail();
        } else if (cargo >= this.ecBufferTarget()) {
            this.fillEnderChestOrFail();
        } else {
            StashMover.ContainerInfo next = this.nearestInputWithItems();
            if (next != null) {
                this.moveToContainer(next);
            } else if (cargo > 0) {
                this.fillEnderChestOrFail();
            } else if (this.enderChestHasItems) {
                this.startForwardTransport();
            } else {
                this.info("Ender-chest ferry: no items at input, rescanning...");
                this.detectContainersInArea(inputAreaPos1, inputAreaPos2, true);
                next = this.nearestInputWithItems();
                if (next != null) {
                    this.moveToContainer(next);
                } else {
                    this.currentState = StashMover.ProcessState.IDLE;
                    this.stateTimer = 100;
                }
            }
        }
    }

    private void fillEnderChestOrFail() {
        if (!this.enderChestAvailable()) {
            this.error("Ender-chest ferry: no ender chest found at the input area. Place one (or keep one in your hotbar) and retry.");
            this.currentState = StashMover.ProcessState.IDLE;
            this.stateTimer = 100;
        } else {
            this.findOrPlaceEnderChest();
        }
    }

    private void startForwardTransport() {
        this.currentContainer = null;
        this.isGoingToInput = false;
        StashMover.TransportMethod method = this.forwardMethod.get();
        switch (method) {
            case PEARL:
                this.waitingForPearl = false;
                this.currentState = StashMover.ProcessState.LOADING_PEARL;
                break;
            case KILL:
                this.waitingForRespawn = false;
                this.killRetryCount = 0;
                this.currentState = StashMover.ProcessState.KILL_COMMAND;
                break;
            case KILL_POSITION:
                this.killPositionRetries = 0;
                this.lastBaritoneGoal = null;
                this.currentState = StashMover.ProcessState.KILL_POSITION_APPROACH;
        }
    }

    private void startReverseTransport() {
        this.currentContainer = null;
        this.isGoingToInput = true;
        StashMover.TransportMethod method = this.goBackMethod.get();
        switch (method) {
            case PEARL:
                this.waitingForPearl = false;
                this.currentState = StashMover.ProcessState.GOING_BACK;
                break;
            case KILL:
                this.waitingForRespawn = false;
                this.killRetryCount = 0;
                this.currentState = StashMover.ProcessState.KILL_COMMAND;
                break;
            case KILL_POSITION:
                this.killPositionRetries = 0;
                this.lastBaritoneGoal = null;
                this.currentState = StashMover.ProcessState.KILL_POSITION_APPROACH;
        }
    }

    private void onTransportArrived() {
        this.stopAllMovement();
        this.currentContainer = null;
        if (this.isGoingToInput) {
            this.info("Arrived at input area, resuming pickup");
            this.enderChestEmptied = false;
            this.detectContainersInArea(inputAreaPos1, inputAreaPos2, true, true);
            this.currentState = StashMover.ProcessState.INPUT_PROCESS;
            this.resumeInputPickup();
        } else {
            this.info("Arrived at output area, resuming deposit");
            this.detectContainersInArea(outputAreaPos1, outputAreaPos2, false, true);
            this.currentState = StashMover.ProcessState.OUTPUT_PROCESS;
            this.stateTimer = 10;
        }
    }

    private void handleGoingBack() {
        if (!this.waitingForPearl) {
            this.sendGoBackPearlCommand();
            this.waitingForPearl = true;
            this.lastPearlMessageTime = System.currentTimeMillis();
            this.pearlRetryCount = 0;
            this.initialPlayerPos = this.mc.player.position();
        }

        Vec3 currentPos = this.mc.player.position();
        double distance = currentPos.distanceTo(this.initialPlayerPos);
        if (distance > 100.0) {
            if (this.isNearInputArea()) {
                if (++this.arrivalSettleTicks < 20) {
                    return;
                }

                this.arrivalSettleTicks = 0;
                this.info("Successfully returned to input area via pearl!");
                this.waitingForPearl = false;
                this.ensureOffhandHasItem();
                this.currentState = StashMover.ProcessState.RESET_PEARL_PICKUP;
                this.hasThrownPearl = false;
                this.hasPlacedShulker = false;
                this.isGoingToInput = true;
            } else if (!this.isNearOutputArea()) {
                this.warning("Teleported but not to input area, retrying...");
                this.waitingForPearl = false;
            }
        } else if (System.currentTimeMillis() - this.lastPearlMessageTime > this.pearlTimeout.get() * 1000) {
            if (this.pearlRetryCount < this.maxRetries.get()) {
                this.pearlRetryCount++;
                this.info("Go back pearl timeout, retrying (attempt " + this.pearlRetryCount + "/" + this.maxRetries.get() + ")");
                this.sendGoBackPearlCommand();
                this.lastPearlMessageTime = System.currentTimeMillis();
            } else {
                this.error("Go back pearl loading failed after " + this.maxRetries.get() + " retries! Stopping.");
                this.waitingForPearl = false;
                this.toggle();
            }
        }
    }

    private void sendKillCommand() {
        boolean random = this.isGoingToInput ? this.goBackKillRandom.get() : this.forwardKillRandom.get();
        String base = (this.isGoingToInput ? this.goBackKillCommand.get() : this.forwardKillCommand.get()).trim();
        if (base.isEmpty()) {
            base = "/kill";
        }

        String command = random ? base + " " + this.generateRandomString(6) : base;
        ChatUtils.sendPlayerMsg(command);
        this.info("Sent kill command: " + command);
    }

    private void handleKillCommand() {
        boolean toInput = this.isGoingToInput;
        if (!this.waitingForRespawn) {
            this.sendKillCommand();
            this.waitingForRespawn = true;
            this.lastKillTime = System.currentTimeMillis();
            this.initialPlayerPos = this.mc.player.position();
        }

        if (this.mc.player.isDeadOrDying() || this.mc.player.getHealth() <= 0.0F) {
            this.mc.getConnection().send(new ServerboundClientCommandPacket(Action.PERFORM_RESPAWN));
        }

        boolean arrived = toInput ? this.isNearInputArea() : this.isNearOutputArea();
        double distance = this.mc.player.position().distanceTo(this.initialPlayerPos);
        if ((distance > 100.0 || this.mc.player.getHealth() > 0.0F) && System.currentTimeMillis() - this.lastKillTime > 1000L) {
            if (arrived) {
                if (++this.arrivalSettleTicks < 20) {
                    return;
                }

                this.arrivalSettleTicks = 0;
                this.waitingForRespawn = false;
                this.killRetryCount = 0;
                this.onTransportArrived();
            } else if (System.currentTimeMillis() - this.lastKillTime > 5000L) {
                if (this.killRetryCount < this.maxRetries.get()) {
                    this.killRetryCount++;
                    this.waitingForRespawn = false;
                    this.warning("Kill respawn not at target, retrying (" + this.killRetryCount + "/" + this.maxRetries.get() + ")");
                } else {
                    this.error("Kill transport failed after " + this.maxRetries.get() + " retries! Stopping.");
                    this.waitingForRespawn = false;
                    this.toggle();
                }
            }
        }
    }

    private BlockPos currentDeathPos() {
        return this.isGoingToInput ? this.goBackDeathPos.get() : this.forwardDeathPos.get();
    }

    private void handleKillPositionApproach() {
        BlockPos deathPos = this.currentDeathPos();
        if (deathPos.getX() == 0 && deathPos.getY() == 64 && deathPos.getZ() == 0) {
            this.error("Walk-to-death position is not configured for this direction!");
            this.currentState = StashMover.ProcessState.IDLE;
            this.stateTimer = 60;
        } else {
            Vec3 playerPos = this.mc.player.position();
            double dx = deathPos.getX() + 0.5 - playerPos.x;
            double dz = deathPos.getZ() + 0.5 - playerPos.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal <= 1.8) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                this.initialPlayerPos = playerPos;
                this.lastKillTime = System.currentTimeMillis();
                this.currentState = StashMover.ProcessState.KILL_POSITION_WALK;
                this.info("At death block, walking in to die");
            } else {
                if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()
                    && (this.lastBaritoneGoal == null || !this.lastBaritoneGoal.equals(deathPos))) {
                    this.lastBaritoneGoal = deathPos;
                    GoalNear goal = new GoalNear(deathPos, 1);
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                    this.info("Pathing to death block");
                }

                this.stateTimer = this.moveDelay.get();
            }
        }
    }

    private void handleKillPositionWalk() {
        if (!this.mc.player.isDeadOrDying() && !(this.mc.player.getHealth() <= 0.0F)) {
            BlockPos deathPos = this.currentDeathPos();
            Vec3 center = Vec3.atCenterOf(deathPos);
            double yaw = Rotations.getYaw(center);
            this.mc.player.setYRot((float)yaw);
            this.mc.player.setXRot(0.0F);
            this.mc.options.keyShift.setDown(false);
            this.mc.options.keySprint.setDown(false);
            this.mc.options.keyUp.setDown(true);
            if (System.currentTimeMillis() - this.lastKillTime > 5000L) {
                this.stopAllMovement();
                this.lastBaritoneGoal = null;
                if (this.killPositionRetries < this.maxRetries.get()) {
                    this.killPositionRetries++;
                    this.warning("Did not die after 5s, re-approaching death block (" + this.killPositionRetries + "/" + this.maxRetries.get() + ")");
                    this.currentState = StashMover.ProcessState.KILL_POSITION_APPROACH;
                } else {
                    this.error("Walk-to-death never killed after " + this.maxRetries.get() + " attempts! Check the death position. Stopping.");
                    this.toggle();
                }
            }
        } else {
            this.stopAllMovement();
            this.mc.getConnection().send(new ServerboundClientCommandPacket(Action.PERFORM_RESPAWN));
            this.waitingForRespawn = true;
            this.lastKillTime = System.currentTimeMillis();
            this.currentState = StashMover.ProcessState.KILL_POSITION_WAIT;
            this.info("Died, waiting for respawn");
        }
    }

    private void handleKillPositionWait() {
        if (!this.mc.player.isDeadOrDying() && !(this.mc.player.getHealth() <= 0.0F)) {
            boolean arrived = this.isGoingToInput ? this.isNearInputArea() : this.isNearOutputArea();
            if (System.currentTimeMillis() - this.lastKillTime > 1000L) {
                if (arrived) {
                    if (++this.arrivalSettleTicks < 20) {
                        return;
                    }

                    this.arrivalSettleTicks = 0;
                    this.waitingForRespawn = false;
                    this.killPositionRetries = 0;
                    this.onTransportArrived();
                } else if (System.currentTimeMillis() - this.lastKillTime > 8000L) {
                    if (this.killPositionRetries < this.maxRetries.get()) {
                        this.killPositionRetries++;
                        this.waitingForRespawn = false;
                        this.lastBaritoneGoal = null;
                        this.warning("Respawn not at target area, retrying walk-to-death (" + this.killPositionRetries + "/" + this.maxRetries.get() + ")");
                        this.currentState = StashMover.ProcessState.KILL_POSITION_APPROACH;
                    } else {
                        this.error("Walk-to-death transport failed after " + this.maxRetries.get() + " retries! Stopping.");
                        this.waitingForRespawn = false;
                        this.toggle();
                    }
                }
            }
        } else {
            this.mc.getConnection().send(new ServerboundClientCommandPacket(Action.PERFORM_RESPAWN));
        }
    }

    private void sendGoBackPearlCommand() {
        String randomSuffix = this.generateRandomString(8);
        String command = String.format("/msg %s %s %s", this.goBackPlayerName.get(), this.goBackCommand.get(), randomSuffix);
        ChatUtils.sendPlayerMsg(command);
        this.info("Sent go back command: " + command);
    }

    private boolean hasValidAreas() {
        return inputAreaPos1 != null && inputAreaPos2 != null && outputAreaPos1 != null && outputAreaPos2 != null;
    }

    private boolean isNearInputArea() {
        return this.isNearArea(inputAreaPos1, inputAreaPos2);
    }

    private boolean isNearOutputArea() {
        return this.isNearArea(outputAreaPos1, outputAreaPos2);
    }

    private boolean isNearArea(BlockPos a, BlockPos b) {
        if (a != null && b != null) {
            int h = this.areaRange.get();
            int v = this.areaRangeVertical.get();
            BlockPos playerPos = this.mc.player.blockPosition();
            return playerPos.getX() >= a.getX() - h
                && playerPos.getX() <= b.getX() + h
                && playerPos.getY() >= a.getY() - v
                && playerPos.getY() <= b.getY() + v
                && playerPos.getZ() >= a.getZ() - h
                && playerPos.getZ() <= b.getZ() + h;
        } else {
            return false;
        }
    }

    private boolean isServerLagging() {
        return TickRate.INSTANCE.getTimeSinceLastTick() > 2.0F;
    }

    private boolean isInventoryFull() {
        if (this.onlyShulkers.get()) {
            int shulkerCount = 0;

            for (int i = 0; i < 36; i++) {
                ItemStack stack = this.mc.player.getInventory().getItem(i);
                if (!stack.isEmpty() && this.isShulkerBox(stack.getItem())) {
                    shulkerCount++;
                }
            }

            return shulkerCount >= 36;
        } else {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = this.mc.player.getInventory().getItem(i);
                if (stack.isEmpty()) {
                    return false;
                }
            }

            return true;
        }
    }

    private boolean isEnderChestFull() {
        return this.enderChestFull;
    }

    private boolean hasItemsToTransfer() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && (!this.onlyShulkers.get() || this.isShulkerBox(stack.getItem()))) {
                return true;
            }
        }

        return false;
    }

    private boolean isShulkerBox(Item item) {
        return item == Items.SHULKER_BOX
            || item == Items.WHITE_SHULKER_BOX
            || item == Items.ORANGE_SHULKER_BOX
            || item == Items.MAGENTA_SHULKER_BOX
            || item == Items.LIGHT_BLUE_SHULKER_BOX
            || item == Items.YELLOW_SHULKER_BOX
            || item == Items.LIME_SHULKER_BOX
            || item == Items.PINK_SHULKER_BOX
            || item == Items.GRAY_SHULKER_BOX
            || item == Items.LIGHT_GRAY_SHULKER_BOX
            || item == Items.CYAN_SHULKER_BOX
            || item == Items.PURPLE_SHULKER_BOX
            || item == Items.BLUE_SHULKER_BOX
            || item == Items.BROWN_SHULKER_BOX
            || item == Items.GREEN_SHULKER_BOX
            || item == Items.RED_SHULKER_BOX
            || item == Items.BLACK_SHULKER_BOX;
    }

    private BlockPos findNearbyEnderChest() {
        int searchRadius = 32;
        BlockPos playerPos = this.mc.player.blockPosition();
        BlockPos closestEnderChest = null;
        double closestDistance = Double.MAX_VALUE;

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    double dist = playerPos.distSqr(pos);
                    if (!(dist > searchRadius * searchRadius)
                        && this.mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                        net.minecraft.world.level.block.Block block = this.mc.level.getBlockState(pos).getBlock();
                        if (block instanceof EnderChestBlock && dist < closestDistance) {
                            closestDistance = dist;
                            closestEnderChest = pos;
                        }
                    }
                }
            }
        }

        if (closestEnderChest != null) {
            this.info("Found enderchest nearby");
        }

        return closestEnderChest;
    }

    private BlockPos findSuitablePlacePos() {
        BlockPos playerPos = this.mc.player.blockPosition();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos pos = playerPos.offset(x, 0, z);
                if (this.mc.level.getBlockState(pos).isAir()
                    && this.mc.level.getBlockState(pos.below()).isRedstoneConductor(this.mc.level, pos.below())) {
                    return pos;
                }
            }
        }

        return null;
    }

    private void placeEnderChest(BlockPos pos, int slot) {
        InvUtils.swap(slot, false);
        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos.below(), false);
        this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hitResult);
        this.enderChestPos = pos;
        this.ecOpenFailures = 0;
        this.ecRepathAttempts = 0;
        this.ecApproachTicks = 0;
        this.alignTicks = 0;
        this.pathGraceTicks = 0;
        this.openWaitTicks = 0;
        this.currentState = StashMover.ProcessState.OPENING_ENDERCHEST;
        this.stateTimer = this.openDelay.get();
    }

    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder result = new StringBuilder();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }

        return result.toString();
    }

    private void handleIdleState() {
        if (this.stateTimer <= 0) {
            if (this.hasValidAreas()) {
                this.info("Rechecking location...");
                this.currentState = StashMover.ProcessState.CHECKING_LOCATION;
            } else {
                this.stateTimer = 100;
            }
        }
    }

    public StashMover.SelectionMode getSelectionMode() {
        return selectionMode;
    }

    public BlockPos getSelectionPos1() {
        return selectionPos1;
    }

    public boolean isSelecting() {
        return selectionMode != StashMover.SelectionMode.NONE;
    }

    public StashMover.ProcessState getCurrentState() {
        return this.currentState;
    }

    public int getItemsTransferred() {
        return this.itemsTransferred;
    }

    public int getContainersProcessed() {
        return this.containersProcessed;
    }

    public boolean hasInputArea() {
        return inputAreaPos1 != null && inputAreaPos2 != null;
    }

    public boolean hasOutputArea() {
        return outputAreaPos1 != null && outputAreaPos2 != null;
    }

    public int getInputContainerCount() {
        return inputContainers.size();
    }

    public int getOutputContainerCount() {
        return outputContainers.size();
    }

    public void clearAreas() {
        inputAreaPos1 = null;
        inputAreaPos2 = null;
        outputAreaPos1 = null;
        outputAreaPos2 = null;
        inputContainers.clear();
        outputContainers.clear();
        selectionMode = StashMover.SelectionMode.NONE;
        this.info("All areas cleared");
    }

    public void renderAreas(Render3DEvent event) {
        if (inputAreaPos1 != null && inputAreaPos2 != null) {
            AABB inputBox = new AABB(
                inputAreaPos1.getX(),
                inputAreaPos1.getY(),
                inputAreaPos1.getZ(),
                inputAreaPos2.getX() + 1,
                inputAreaPos2.getY() + 1,
                inputAreaPos2.getZ() + 1
            );
            SettingColor inputColor = new SettingColor(0, 255, 0, 50);
            event.renderer.box(inputBox, inputColor, inputColor, ShapeMode.Both, 0);
        }

        if (outputAreaPos1 != null && outputAreaPos2 != null) {
            AABB outputBox = new AABB(
                outputAreaPos1.getX(),
                outputAreaPos1.getY(),
                outputAreaPos1.getZ(),
                outputAreaPos2.getX() + 1,
                outputAreaPos2.getY() + 1,
                outputAreaPos2.getZ() + 1
            );
            SettingColor outputColor = new SettingColor(0, 0, 255, 50);
            event.renderer.box(outputBox, outputColor, outputColor, ShapeMode.Both, 0);
        }
    }

    private void ensureOffhandHasItem() {
        ItemStack offhandStack = this.mc.player.getOffhandItem();
        ItemStack slot0 = this.mc.player.getInventory().getItem(0);
        if (!slot0.isEmpty() && slot0.getItem() != Items.ENDER_PEARL) {
            if (offhandStack.isEmpty()) {
                this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, 36, 0, ClickType.PICKUP, this.mc.player);
                this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, 45, 0, ClickType.PICKUP, this.mc.player);
                this.info("Moved " + slot0.getItem().getName().getString() + " from slot 0 to offhand");
            } else {
                for (int i = 9; i < 36; i++) {
                    if (this.mc.player.getInventory().getItem(i).isEmpty()) {
                        this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, 36, 0, ClickType.PICKUP, this.mc.player);
                        this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, i, 0, ClickType.PICKUP, this.mc.player);
                        this.info("Moved slot 0 to inventory to free space for pearl");
                        break;
                    }
                }
            }
        } else {
            if (offhandStack.isEmpty()) {
                for (int i = 1; i < 9; i++) {
                    ItemStack stack = this.mc.player.getInventory().getItem(i);
                    if (!stack.isEmpty() && stack.getItem() != Items.ENDER_PEARL) {
                        this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, 36 + i, 0, ClickType.PICKUP, this.mc.player);
                        this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, 45, 0, ClickType.PICKUP, this.mc.player);
                        this.info("Moved " + stack.getItem().getName().getString() + " to offhand");
                        return;
                    }
                }

                for (int i = 9; i < 36; i++) {
                    ItemStack stack = this.mc.player.getInventory().getItem(i);
                    if (!stack.isEmpty() && stack.getItem() != Items.ENDER_PEARL && !this.isShulkerBox(stack.getItem())) {
                        this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, i, 0, ClickType.PICKUP, this.mc.player);
                        this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, 45, 0, ClickType.PICKUP, this.mc.player);
                        this.info("Moved item from inventory to offhand");
                        return;
                    }
                }
            }
        }
    }

    private static class ContainerInfo {
        public final BlockPos pos;
        public final StashMover.ContainerType type;
        public boolean isEmpty = false;
        public boolean isFull = false;
        public boolean skipped = false;
        public int totalSlots = 27;

        public ContainerInfo(BlockPos pos, StashMover.ContainerType type) {
            this.pos = pos;
            this.type = type;
            if (type == StashMover.ContainerType.BARREL) {
                this.totalSlots = 27;
            } else if (type == StashMover.ContainerType.DOUBLE_CHEST || type == StashMover.ContainerType.DOUBLE_TRAPPED_CHEST) {
                this.totalSlots = 54;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o != null && this.getClass() == o.getClass()) {
                StashMover.ContainerInfo that = (StashMover.ContainerInfo)o;
                return this.pos.equals(that.pos);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return this.pos.hashCode();
        }
    }

    private enum ContainerType {
        CHEST,
        DOUBLE_CHEST,
        TRAPPED_CHEST,
        DOUBLE_TRAPPED_CHEST,
        BARREL;
    }

    public enum ProcessState {
        IDLE,
        CHECKING_LOCATION,
        INPUT_PROCESS,
        LOADING_PEARL,
        RESET_PEARL_PICKUP,
        RESET_PEARL_PLACE_SHULKER,
        RESET_PEARL_APPROACH,
        RESET_PEARL_PREPARE,
        RESET_PEARL_THROW,
        RESET_PEARL_WAIT,
        OUTPUT_PROCESS,
        GOING_BACK,
        OPENING_CONTAINER,
        TRANSFERRING_ITEMS,
        CLOSING_CONTAINER,
        BREAKING_CONTAINER,
        MOVING_TO_CONTAINER,
        OPENING_ENDERCHEST,
        FILLING_ENDERCHEST,
        EMPTYING_ENDERCHEST,
        WAITING,
        KILL_COMMAND,
        KILL_POSITION_APPROACH,
        KILL_POSITION_WALK,
        KILL_POSITION_WAIT;
    }

    public enum SelectionMode {
        NONE,
        INPUT_FIRST,
        INPUT_SECOND,
        OUTPUT_FIRST,
        OUTPUT_SECOND;
    }

    public enum TransportMethod {
        PEARL("Pearl Loading"),
        KILL("Kill Command"),
        KILL_POSITION("Kill (Walk to Death)");

        private final String name;

        TransportMethod(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
