package bep.hax.modules.arealoader;

import bep.hax.Bep;
import bep.hax.modules.arealoader.modes.Rectangle;
import bep.hax.modules.arealoader.modes.Spiral;
import bep.hax.modules.arealoader.modes.SpiralCircle;
import bep.hax.modules.arealoader.modes.ZigZag;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;

public class AreaLoader extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgFlight = this.settings.createGroup("Flight");
    private final SettingGroup sgNether = this.settings.createGroup("Nether");
    private final SettingGroup sgDebug = this.settings.createGroup("Debug");
    public final Setting<AreaLoaderModes> chunkLoadMode = this.sgGeneral
        .add(
            new Builder<AreaLoaderModes>()
                .name("mode")
                .description("The mode chunks are loaded.")
                .defaultValue(AreaLoaderModes.Spiral)
                .onModuleActivated(chunkMode -> this.rebuildMode(chunkMode.get()))
                .onChanged(this::onModeChanged)
                .build()
        );
    public final Setting<BlockPos> startPos = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                .name("start-position")
                .description("The coordinates to start the rectangle at. Y Pos is ignored")
                .defaultValue(new BlockPos(0, 0, 0))
                .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.Rectangle)
                .build()
        );
    public final Setting<BlockPos> targetPos = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                .name("end-position")
                .description("The coordinates to end the rectangle at. Y Pos is ignored")
                .defaultValue(new BlockPos(0, 0, 0))
                .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.Rectangle)
                .build()
        );
    public final Setting<Integer> rowGap = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("path-gap")
                .description("The amount of chunks to space between each chunk path.")
                .defaultValue(25)
                .min(1)
                .sliderRange(0, 100)
                .build()
        );
    public final Setting<Integer> zigzagLegLength = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("zigzag-leg-length")
                .description("How far to travel in blocks before turning (one leg of the zigzag).")
                .defaultValue(5000)
                .min(100)
                .sliderRange(500, 20000)
                .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.ZigZag)
                .build()
        );
    public final Setting<Integer> zigzagRowGap = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("zigzag-row-gap")
                .description("How far to shift sideways in blocks between legs.")
                .defaultValue(192)
                .min(16)
                .sliderRange(16, 512)
                .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.ZigZag)
                .build()
        );
    public final Setting<String> saveLocation = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("save-name")
                .description("The name to use for the folder that saves data. Leave blank to use 'default'.")
                .defaultValue("world")
                .build()
        );
    public final Setting<Boolean> disconnectOnCompletion = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("disconnect-on-completion")
                .description("Whether to disconnect after the path is complete. This will turn autoreconnect off when disconnecting.")
                .defaultValue(false)
                .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.Rectangle)
                .build()
        );
    public final Setting<Double> boatFlySpeed = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("boat-fly-speed")
                .description("Speed in blocks per second for ETA calculation.")
                .defaultValue(30.0)
                .min(1.0)
                .sliderRange(1.0, 100.0)
                .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.Rectangle)
                .build()
        );
    public final Setting<AreaLoader.OverworldFlightMode> overworldFlightMode = this.sgFlight
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("overworld-flight-mode"))
                        .description(
                            "How AreaLoader flies in Overworld/End. PITCH40 uses Pitch40, ROCKETS uses RocketFly, OTHER does nothing (manual control)."
                        ))
                    .defaultValue(AreaLoader.OverworldFlightMode.PITCH40))
                .build()
        );
    public final Setting<Boolean> pitch40AutoFirework = this.sgFlight
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("pitch40-auto-firework")
                .description("Enable auto-firework in Pitch40 when using PITCH40 mode.")
                .defaultValue(false)
                .visible(() -> this.overworldFlightMode.get() == AreaLoader.OverworldFlightMode.PITCH40)
                .build()
        );
    public final Setting<Integer> spiralCornerReachDistance = this.sgFlight
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("corner-reach-distance")
                .description(
                    "How close to a spiral corner (Circle: waypoint) before turning. It also turns once you pass it along the leg, so this is mainly a fallback."
                )
                .defaultValue(20)
                .min(1)
                .sliderRange(1, 100)
                .visible(this::isSpiral)
                .build()
        );
    public final Setting<Integer> spiralMaxOffTrackDistance = this.sgFlight
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-off-track-distance")
                .description("How far off the spiral path before saving state and navigating back to recover.")
                .defaultValue(500)
                .min(20)
                .sliderRange(50, 2000)
                .visible(this::isSpiral)
                .build()
        );
    public final Setting<Integer> circleSegmentLength = this.sgFlight
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("circle-segment-length")
                .description("Blocks flown along the arc between each heading update. Smaller is a rounder circle at the cost of more frequent turns.")
                .defaultValue(100)
                .min(10)
                .sliderRange(10, 500)
                .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.SpiralCircle)
                .build()
        );
    public final Setting<Integer> circleTurnStep = this.sgFlight
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("circle-turn-step")
                .description("Maximum degrees the heading turns per segment. Caps how sharp the turns get on the tight inner loops.")
                .defaultValue(10)
                .min(1)
                .sliderRange(1, 45)
                .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.SpiralCircle)
                .build()
        );
    public final Setting<AreaLoader.NetherPathMode> netherPathMode = this.sgNether
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("nether-path-mode"))
                        .description("How AreaLoader navigates in Nether. BARITONE_ELYTRA uses Baritone elytra pathing, OTHER does nothing (manual control)."))
                    .defaultValue(AreaLoader.NetherPathMode.BARITONE_ELYTRA))
                .build()
        );
    public final Setting<Integer> netherWaypointDistance = this.sgNether
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("nether-waypoint-distance")
                .description("Distance in blocks between Baritone waypoints in the Nether. Smaller = more accurate but slower.")
                .defaultValue(200)
                .min(50)
                .sliderRange(50, 500)
                .visible(() -> this.netherPathMode.get() == AreaLoader.NetherPathMode.BARITONE_ELYTRA)
                .build()
        );
    public final Setting<Integer> netherWaypointReachDistance = this.sgNether
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("nether-reach-distance")
                .description("How close to a waypoint before considering it reached and moving to next.")
                .defaultValue(100)
                .min(10)
                .sliderRange(10, 100)
                .visible(() -> this.netherPathMode.get() == AreaLoader.NetherPathMode.BARITONE_ELYTRA)
                .build()
        );
    public final Setting<Boolean> debugMode = this.sgDebug
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug-messages")
                .description("Show debug messages in chat (file saves, dimension changes, corner reached, etc.)")
                .defaultValue(false)
                .build()
        );
    public final Setting<Boolean> showGoalWaypoint = this.sgDebug
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-goal-waypoint")
                .description("Create a temporary Xaero waypoint at the current goal position.")
                .defaultValue(false)
                .build()
        );
    private AreaLoaderMode currentMode = new Rectangle();

    public AreaLoader() {
        super(
            Bep.HUNT_CATEGORY,
            "arealoader",
            "Either loads chunks in a rectangle to a certain point from you, or spirals endlessly from you. Useful with Stash Finder or other map saving mods."
        );
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WButton clear = list.add(theme.button("Clear Currently Selected")).widget();
        clear.action = () -> {
            boolean wasActive = this.isActive();
            if (wasActive) {
                this.toggle();
            }

            this.currentMode.clear();
            if (wasActive) {
                this.info("Module was disabled. Activate it again to start fresh.");
            }
        };
        WButton clearAll = list.add(theme.button("Clear All")).widget();
        clearAll.action = () -> {
            boolean wasActive = this.isActive();
            if (wasActive) {
                this.toggle();
            }

            this.currentMode.clearAll();
            if (wasActive) {
                this.info("Module was disabled. Activate it again to start fresh with any mode.");
            }
        };
        list.add(theme.horizontalSeparator()).expandX();
        WButton recoveryBtn = list.add(theme.button("Recovery Configuration")).widget();
        recoveryBtn.action = () -> this.mc.setScreen(new AreaRecoveryScreen(theme, this));
        return list;
    }

    @Override
    public void onActivate() {
        this.currentMode.onActivate();
    }

    @Override
    public void onDeactivate() {
        this.currentMode.onDeactivate();
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.mc.gameMode != null) {
                GameType currentGameMode = this.mc.gameMode.getPlayerMode();
                if (currentGameMode == GameType.SURVIVAL) {
                    this.currentMode.onTick();
                }
            }
        }
    }

    private boolean isSpiral() {
        return this.chunkLoadMode.get() == AreaLoaderModes.Spiral || this.chunkLoadMode.get() == AreaLoaderModes.SpiralCircle;
    }

    private void rebuildMode(AreaLoaderModes mode) {
        switch (mode) {
            case Rectangle:
                this.currentMode = new Rectangle();
                break;
            case Spiral:
                this.currentMode = new Spiral();
                break;
            case SpiralCircle:
                this.currentMode = new SpiralCircle();
                break;
            case ZigZag:
                this.currentMode = new ZigZag();
        }
    }

    private void onModeChanged(AreaLoaderModes mode) {
        boolean wasActive = this.isActive() && this.mc.player != null && this.mc.level != null;
        if (wasActive) {
            this.currentMode.onDeactivate();
        }

        this.rebuildMode(mode);
        if (wasActive) {
            this.currentMode.onActivate();
        }
    }

    public enum NetherPathMode {
        BARITONE_ELYTRA,
        OTHER;
    }

    public enum OverworldFlightMode {
        ROCKETS,
        PITCH40,
        OTHER;
    }
}
