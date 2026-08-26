package bep.hax.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalXZ;
import bep.hax.Bep;
import bep.hax.config.BepConfig;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.modules.arealoader.AreaLoader;
import bep.hax.util.BaritoneHelper;
import bep.hax.util.Utils;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.path.XaeroPath;
import xaero.map.mods.SupportMods;

public class WaypointFollower extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgFlight = this.settings.createGroup("Flight");
    private final SettingGroup sgControl = this.settings.createGroup("Control");
    private final List<BlockPos> waypointsToFollow = new ArrayList<>();
    private boolean isInNether = false;
    private boolean isInEnd = false;
    private int currentWaypointIndex = 0;
    private BlockPos currentBaritoneTarget = null;
    private boolean isPaused = false;
    private int waypointsCompletedThisSession = 0;
    private double totalDistanceTraveled = 0.0;
    private Vec3 lastPlayerPos = null;
    private WaypointFollower.FlightMode activeFlightMode = WaypointFollower.FlightMode.None;
    private RocketFly rocketFly = null;
    private Pitch40 pitch40Util = null;
    private ElytraRecast elytraRecast = null;
    private int fireworkCooldown = 0;
    private int lastKnownWaypointCount = 0;
    private int reloadCheckTimer = 0;
    private int startupDelayTicks = 0;
    private boolean startupRecastTriggered = false;
    private int glidingTicksAfterStartup = 0;
    private boolean baritoneActivatedAfterStartup = false;
    private int waypointLoadRetries = 0;
    private boolean waypointsFullyLoaded = false;
    private String lastDimensionKey = "";
    private volatile String lastWaypointWorldKey = "";
    private Thread waypointLoadThread = null;
    private volatile boolean isDeactivating = false;
    private boolean wasInSurvival = false;
    private final Setting<String> waypointPrefix = this.sgGeneral
        .add(new Builder().name("waypoint-prefix").description("Prefix for waypoints that should be followed (e.g., 'Hunt_')").defaultValue("Hunt_").build());
    private final Setting<WaypointFollower.FollowMode> followMode = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("follow-mode"))
                        .description("How to follow waypoints: Closest goes to nearest waypoint, Numerical follows in order"))
                    .defaultValue(WaypointFollower.FollowMode.Numerical))
                .build()
        );
    private final Setting<Double> reachDistance = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("reach-distance")
                .description("Horizontal distance to consider a waypoint reached (ignores Y level)")
                .defaultValue(20.0)
                .range(1.0, 50.0)
                .sliderRange(1.0, 100.0)
                .build()
        );
    private final Setting<Boolean> showStatistics = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-statistics")
                .description("Show session statistics in info string")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> showChatMessages = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-chat-messages")
                .description("Show chat notifications (disable to hide messages containing coordinates)")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> completionSound = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("completion-sound")
                .description("Play a sound when all waypoints have been reached")
                .defaultValue(true)
                .build()
        );
    private final Setting<WaypointFollower.CompletionAction> completionAction = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("on-completion"))
                        .description("Action to run after disabling Waypoint Follower when it runs out of waypoints to follow."))
                    .defaultValue(WaypointFollower.CompletionAction.None))
                .build()
        );
    private final Setting<Keybind> addWaypointKey = this.sgControl
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("add-waypoint-key")
                .description("Key to add a hunt waypoint at mouse position on Xaero's map (works even when module is off)")
                .defaultValue(Keybind.fromKey(82))
                .build()
        );
    private final Setting<Keybind> clearWaypoints = this.sgControl
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("clear-waypoints")
                .description("Clears all follow waypoints (only works when Xaero's world map is open)")
                .defaultValue(Keybind.none())
                .action(() -> {
                    if (this.mc.screen != null && this.mc.screen.getClass().getName().equals("xaero.map.gui.GuiMap")) {
                        if (!this.isTextFieldFocusedInXaeroMap()) {
                            this.clearAllFollowWaypoints();
                            this.waypointsToFollow.clear();
                            this.currentWaypointIndex = 0;
                            if (this.showChatMessages.get()) {
                                this.info("Cleared all hunt waypoints");
                            }
                        }
                    }
                })
                .build()
        );
    private final Setting<Keybind> skipWaypointKey = this.sgControl
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("skip-waypoint")
                .description("Skip to next waypoint in the list")
                .defaultValue(Keybind.none())
                .action(() -> {
                    if (!this.isTypingInTextField()) {
                        if (this.isActive() && !this.waypointsToFollow.isEmpty()) {
                            BlockPos current = this.getNextWaypoint();
                            if (current != null) {
                                this.removeCurrentWaypoint(current);
                                if (this.showChatMessages.get()) {
                                    this.info("Skipped waypoint. " + this.waypointsToFollow.size() + " remaining.");
                                }
                            }
                        }
                    }
                })
                .build()
        );
    private final Setting<Keybind> pauseResumeKey = this.sgControl
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("pause-resume")
                .description("Pause or resume waypoint following")
                .defaultValue(Keybind.none())
                .action(() -> {
                    if (!this.isTypingInTextField()) {
                        if (this.isActive()) {
                            this.isPaused = !this.isPaused;
                            if (this.isPaused) {
                                this.stopFlightMode();
                                this.stopBaritone();
                                if (this.showChatMessages.get()) {
                                    this.info("Waypoint following paused");
                                }
                            } else if (this.showChatMessages.get()) {
                                this.info("Waypoint following resumed");
                            }
                        }
                    }
                })
                .build()
        );
    private final Setting<WaypointFollower.NetherFlightMode> netherFlightMode = this.sgFlight
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("nether-flight-mode"))
                        .description("Flight mode to use in the Nether (Baritone uses elytra pathfinding, None only rotates yaw)"))
                    .defaultValue(WaypointFollower.NetherFlightMode.Baritone))
                .build()
        );
    private final Setting<WaypointFollower.FlightMode> overworldFlightMode = this.sgFlight
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("overworld-flight-mode"))
                        .description("Flight mode to use in the Overworld"))
                    .defaultValue(WaypointFollower.FlightMode.Pitch40))
                .build()
        );
    private final Setting<WaypointFollower.FlightMode> endFlightMode = this.sgFlight
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("end-flight-mode"))
                        .description("Flight mode to use in the End"))
                    .defaultValue(WaypointFollower.FlightMode.Pitch40))
                .build()
        );
    private final Setting<Boolean> snapRotation = this.sgFlight
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("snap-rotation")
                .description(
                    "Instantly snap yaw to the exact heading toward the waypoint instead of easing in. Eliminates drift but is more detectable by anticheat."
                )
                .defaultValue(false)
                .build()
        );
    private final Setting<Double> rotationSpeed = this.sgFlight
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("rotation-speed")
                .description("Fraction of the yaw error corrected each tick (0.01 = slow/smooth, 0.2 = snappy). Higher reduces drift on far waypoints.")
                .defaultValue(0.1)
                .range(0.01, 1.0)
                .sliderRange(0.01, 0.5)
                .visible(() -> !this.snapRotation.get())
                .build()
        );
    private final Setting<Double> yawDeadzone = this.sgFlight
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("yaw-deadzone")
                .description(
                    "Yaw error (degrees) below which no correction is applied. Lower = tighter tracking and less drift on distant waypoints; 0 always corrects."
                )
                .defaultValue(0.5)
                .range(0.0, 10.0)
                .sliderRange(0.0, 5.0)
                .build()
        );
    private final Setting<Double> netherReachMultiplier = this.sgFlight
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("nether-reach-multiplier")
                .description("Multiplier for reach distance in the Nether (waypoints may be stored in overworld scale)")
                .defaultValue(5.0)
                .range(1.0, 10.0)
                .sliderRange(1.0, 10.0)
                .build()
        );
    private final Setting<Boolean> autoStartFlight = this.sgFlight
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-start-flight")
                .description("Automatically trigger ElytraRecast to start flying after relog/activation.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> startupDelay = this.sgFlight
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("startup-delay")
                .description("Ticks to wait after activation before starting flight.")
                .defaultValue(5)
                .range(0, 40)
                .sliderRange(0, 40)
                .visible(this.autoStartFlight::get)
                .build()
        );
    public static final String HUNT_SYMBOL = "H";
    public static final WaypointColor HUNT_COLOR = WaypointColor.AQUA;

    public WaypointFollower() {
        super(Bep.HUNT_CATEGORY, "waypoint-follower", "Advanced waypoint following system with multi-dimensional flight support");
    }

    @Override
    public void onActivate() {
        if (this.mc.level != null && this.mc.player != null) {
            this.rocketFly = Modules.get().get(RocketFly.class);
            this.pitch40Util = Modules.get().get(Pitch40.class);
            this.elytraRecast = Modules.get().get(ElytraRecast.class);
            this.isDeactivating = false;
            this.isInNether = this.isInNether(this.mc.level);
            this.isInEnd = this.isInEnd(this.mc.level);
            this.isPaused = false;
            this.waypointsCompletedThisSession = 0;
            this.totalDistanceTraveled = 0.0;
            this.lastPlayerPos = this.mc.player.position();
            this.waypointsToFollow.clear();
            this.currentWaypointIndex = 0;
            this.startupDelayTicks = 0;
            this.startupRecastTriggered = false;
            this.glidingTicksAfterStartup = 0;
            this.baritoneActivatedAfterStartup = false;
            this.waypointLoadRetries = 0;
            this.waypointsFullyLoaded = false;
            this.wasInSurvival = this.mc.gameMode == null || this.mc.gameMode.getPlayerMode() == GameType.SURVIVAL;
            this.lastDimensionKey = this.getCurrentDimensionKey();
            this.lastWaypointWorldKey = "";
            this.startAsyncWaypointLoad();
            this.activeFlightMode = WaypointFollower.FlightMode.None;
        }
    }

    @Override
    public void onDeactivate() {
        this.isDeactivating = true;
        this.stopFlightMode();
        this.stopBaritone();
        if (this.waypointLoadThread != null && this.waypointLoadThread.isAlive()) {
            this.waypointLoadThread.interrupt();
        }

        if (this.rocketFly != null && this.rocketFly.isActive()) {
            this.rocketFly.toggle();
        }

        if (this.pitch40Util != null && this.pitch40Util.isActive()) {
            this.pitch40Util.toggle();
        }

        this.currentBaritoneTarget = null;
        this.activeFlightMode = WaypointFollower.FlightMode.None;
        this.fireworkCooldown = 0;
        this.isPaused = false;
        if (this.showChatMessages.get() && this.waypointsCompletedThisSession > 0) {
            this.info(String.format("Session completed: %d waypoints, %.1f blocks traveled", this.waypointsCompletedThisSession, this.totalDistanceTraveled));
        }
    }

    private void stopBaritone() {
        try {
            IBaritone baritoneInstance = BaritoneAPI.getProvider().getPrimaryBaritone();
            baritoneInstance.getPathingBehavior().cancelEverything();
            baritoneInstance.getCustomGoalProcess().setGoal(null);
            baritoneInstance.getInputOverrideHandler().clearAllKeys();
            if (BaritoneHelper.hasElytraProcess()) {
                baritoneInstance.getCommandManager().execute("forcecancel");
                baritoneInstance.getPathingBehavior().forceCancel();
            }
        } catch (Exception var2) {
        }

        this.currentBaritoneTarget = null;
    }

    private void rearmStartupFlight() {
        this.startupRecastTriggered = false;
        this.baritoneActivatedAfterStartup = false;
        this.startupDelayTicks = 0;
        this.glidingTicksAfterStartup = 0;
    }

    public String getWaypointPrefix() {
        return this.waypointPrefix.get();
    }

    public void clearTrackedWaypoints() {
        this.waypointsToFollow.clear();
        this.currentWaypointIndex = 0;
    }

    public int skipCurrentWaypoint() {
        if (this.waypointsToFollow.isEmpty()) {
            return -1;
        } else {
            BlockPos current = this.getNextWaypoint();
            if (current != null) {
                this.removeCurrentWaypoint(current);
                return this.waypointsToFollow.size();
            } else {
                return -1;
            }
        }
    }

    public boolean isPaused() {
        return this.isPaused;
    }

    public void pause() {
        if (!this.isPaused) {
            this.isPaused = true;
            this.stopFlightMode();
            this.stopBaritone();
        }
    }

    public void resume() {
        this.isPaused = false;
    }

    public boolean shouldShowChatMessages() {
        return this.showChatMessages.get();
    }

    public int getWaypointCount() {
        return this.getNextWaypointNumber();
    }

    public int getAddWaypointKeyCode() {
        return this.addWaypointKey.get().getValue();
    }

    public int getNextWaypointNumber() {
        return this.getNextWaypointNumber(this.getWaypointWorld());
    }

    public int getNextWaypointNumber(MinimapWorld waypointWorld) {
        try {
            if (waypointWorld == null) {
                return 0;
            }

            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            if (currentSet == null) {
                return 0;
            }

            String prefix = this.waypointPrefix.get();
            int highestNumber = -1;

            for (Waypoint waypoint : currentSet.getWaypoints()) {
                if (waypoint.getName().startsWith(prefix)) {
                    try {
                        String suffix = waypoint.getName().substring(prefix.length());
                        int num = Integer.parseInt(suffix);
                        if (num > highestNumber) {
                            highestNumber = num;
                        }
                    } catch (NumberFormatException var9) {
                    }
                }
            }

            return highestNumber + 1;
        } catch (Exception e) {
            return this.waypointsToFollow.size();
        }
    }

    public int getTotalHuntWaypointCount() {
        try {
            MinimapWorld waypointWorld = this.getWaypointWorld();
            if (waypointWorld == null) {
                return 0;
            }

            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            if (currentSet == null) {
                return 0;
            }

            String prefix = this.waypointPrefix.get();
            int count = 0;

            for (Waypoint waypoint : currentSet.getWaypoints()) {
                if (waypoint.getName().startsWith(prefix)) {
                    count++;
                }
            }

            return count;
        } catch (Exception e) {
            return this.waypointsToFollow.size();
        }
    }

    public void addWaypointToTrack(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!this.waypointsToFollow.contains(pos)) {
            this.waypointsToFollow.add(pos);
            if (this.isActive() && this.showChatMessages.get()) {
                this.info("Added waypoint to tracking list");
            }
        }
    }

    private boolean isInNether(Level world) {
        try {
            return world.dimension() == Level.NETHER;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isInEnd(Level world) {
        try {
            return world.dimension() == Level.END;
        } catch (Throwable t) {
            return false;
        }
    }

    private String getCurrentDimensionKey() {
        if (this.mc.level == null) {
            return "unknown";
        }

        try {
            return this.mc.level.dimension().identifier().toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public MinimapWorld getWaypointWorld() {
        try {
            MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session != null) {
                MinimapWorld current = session.getWorldManager().getCurrentWorld();
                if (current != null) {
                    return current;
                }
            }
        } catch (Exception var3) {
        }

        return SupportMods.xaeroMinimap.getWaypointWorld();
    }

    private String getWaypointWorldKey(MinimapWorld world) {
        if (world == null) {
            return "";
        }

        try {
            XaeroPath path = world.getFullPath();
            return (path == null ? "?" : path.toString()) + "|" + world.getCurrentWaypointSetId();
        } catch (Exception e) {
            return "";
        }
    }

    private String getWaypointWorldKey() {
        return this.getWaypointWorldKey(this.getWaypointWorld());
    }

    public int toPlayerDimensionCoord(int waypointCoord) {
        return (int)Math.floor(waypointCoord * this.getWaypointToPlayerScale());
    }

    public int toWaypointWorldCoord(int playerCoord, MinimapWorld waypointWorld) {
        try {
            MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session != null && this.mc.level != null && waypointWorld != null) {
                double waypointScale = session.getDimensionHelper().getDimCoordinateScale(waypointWorld);
                double playerScale = this.mc.level.dimensionType().coordinateScale();
                return !(waypointScale <= 0.0) && !(playerScale <= 0.0) ? (int)Math.floor(playerCoord * (playerScale / waypointScale)) : playerCoord;
            } else {
                return playerCoord;
            }
        } catch (Exception e) {
            return playerCoord;
        }
    }

    public static int getWaypointY(MinimapWorld waypointWorld) {
        try {
            ResourceKey<Level> dim = waypointWorld == null ? null : waypointWorld.getDimId();
            if (dim == Level.NETHER) {
                return 120;
            }

            if (dim == Level.END) {
                return 70;
            }
        } catch (Exception var2) {
        }

        return 320;
    }

    private double getWaypointToPlayerScale() {
        try {
            MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session != null && this.mc.level != null) {
                MinimapWorld waypointWorld = this.getWaypointWorld();
                if (waypointWorld == null) {
                    return 1.0;
                }

                double storedScale = session.getDimensionHelper().getDimCoordinateScale(waypointWorld);
                double playerScale = this.mc.level.dimensionType().coordinateScale();
                return !(storedScale <= 0.0) && !(playerScale <= 0.0) ? storedScale / playerScale : 1.0;
            } else {
                return 1.0;
            }
        } catch (Exception e) {
            return 1.0;
        }
    }

    private void startAsyncWaypointLoad() {
        if (this.waypointLoadThread != null && this.waypointLoadThread.isAlive()) {
            this.waypointLoadThread.interrupt();
        }

        this.waypointLoadThread = new Thread(() -> {
            try {
                try {
                    MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
                    if (minimapSession != null) {
                        minimapSession.getWaypointSession();
                        minimapSession.getWorldManager();
                    }
                } catch (Exception var6) {
                }

                for (int i = 0; i < 5; i++) {
                    try {
                        SupportMods.xaeroMinimap.requestWaypointsRefresh();
                        Thread.sleep(250L);
                    } catch (InterruptedException e) {
                        return;
                    } catch (Exception var5) {
                    }
                }

                for (int attempt = 0; attempt < 10 && !this.waypointsFullyLoaded; attempt++) {
                    try {
                        Thread.sleep(300L);
                        this.attemptWaypointLoad();
                    } catch (InterruptedException e) {
                        return;
                    }
                }

                if (!this.waypointsFullyLoaded && this.showChatMessages.get()) {
                    this.mc.execute(() -> this.warning("Waypoints may not have loaded - try opening world map manually"));
                }
            } catch (Exception e) {
                if (this.showChatMessages.get()) {
                    this.mc.execute(() -> this.error("Waypoint loading error: " + e.getMessage()));
                }
            }
        }, "WaypointFollower-Loader");
        this.waypointLoadThread.setDaemon(true);
        this.waypointLoadThread.start();
    }

    private int extractOrderNumber(String waypointName, String prefix) {
        try {
            String suffix = waypointName.substring(prefix.length());
            return Integer.parseInt(suffix);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return Integer.MAX_VALUE;
        }
    }

    private void attemptWaypointLoad() {
        this.waypointLoadRetries++;

        try {
            SupportMods.xaeroMinimap.requestWaypointsRefresh();
            MinimapWorld waypointWorld = this.getWaypointWorld();
            boolean fromEnd = this.isInEnd;
            if (waypointWorld == null) {
                if (this.waypointLoadRetries >= 10) {
                    if (this.showChatMessages.get()) {
                        this.mc.execute(() -> this.error("Waypoint world is null after " + this.waypointLoadRetries + " retries"));
                    }

                    this.waypointsFullyLoaded = true;
                }

                return;
            }

            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            if (currentSet == null) {
                if (this.waypointLoadRetries >= 10) {
                    if (this.showChatMessages.get()) {
                        this.mc.execute(() -> this.error("Current waypoint set is null after " + this.waypointLoadRetries + " retries"));
                    }

                    this.waypointsFullyLoaded = true;
                }

                return;
            }

            String prefix = this.waypointPrefix.get();
            List<Waypoint> huntWaypoints = new ArrayList<>();

            for (Waypoint wp : currentSet.getWaypoints()) {
                if (wp.getName().startsWith(prefix) && !wp.isDisabled()) {
                    huntWaypoints.add(wp);
                }
            }

            huntWaypoints.sort((wp1, wp2) -> {
                int order1 = this.extractOrderNumber(wp1.getName(), prefix);
                int order2 = this.extractOrderNumber(wp2.getName(), prefix);
                return Integer.compare(order1, order2);
            });

            for (Waypoint wp : huntWaypoints) {
                this.addWaypointToFollow(new BlockPos(wp.getX(), wp.getY(), wp.getZ()));
            }

            this.waypointsFullyLoaded = true;
            this.lastWaypointWorldKey = this.getWaypointWorldKey(waypointWorld);
            this.lastKnownWaypointCount = this.getTotalHuntWaypointCount();
            if (this.showChatMessages.get()) {
                int size = this.waypointsToFollow.size();
                int retries = this.waypointLoadRetries;
                boolean isEnd = fromEnd;
                this.mc.execute(() -> {
                    if (size > 0) {
                        String source = isEnd ? " from end" : "";
                        this.info("Loaded " + size + " hunt waypoints" + source + " (attempt " + retries + ")");
                    } else {
                        this.info("No waypoints with prefix '" + prefix + "' found");
                    }
                });
            }
        } catch (Exception e) {
            if (this.showChatMessages.get() && this.waypointLoadRetries >= 10) {
                String msg = e.getMessage();
                this.mc.execute(() -> this.error("Failed to load waypoints from Xaero: " + msg));
            }
        }
    }

    public void clearAllFollowWaypoints() {
        try {
            MinimapWorld waypointWorld = this.getWaypointWorld();
            if (waypointWorld == null) {
                if (this.showChatMessages.get()) {
                    this.warning("Could not get waypoint world for clearing");
                }

                return;
            }

            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            if (currentSet == null) {
                if (this.showChatMessages.get()) {
                    this.warning("Could not get waypoint set for clearing");
                }

                return;
            }

            String prefix = this.waypointPrefix.get();
            List<Waypoint> toRemove = new ArrayList<>();

            for (Waypoint wp : currentSet.getWaypoints()) {
                if (wp.getName().startsWith(prefix)) {
                    toRemove.add(wp);
                }
            }

            for (Waypoint wp : toRemove) {
                currentSet.remove(wp);
            }

            try {
                MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
                if (minimapSession != null) {
                    minimapSession.getWorldManagerIO().saveWorld(waypointWorld);
                    if (this.showChatMessages.get()) {
                        this.info("Cleared " + toRemove.size() + " hunt waypoints (saved to disk)");
                    }
                }
            } catch (Exception saveEx) {
                if (this.showChatMessages.get()) {
                    this.error("Failed to save waypoints to disk: " + saveEx.getMessage());
                }
            }

            SupportMods.xaeroMinimap.requestWaypointsRefresh();
        } catch (Exception e) {
            if (this.showChatMessages.get()) {
                this.error("Failed to clear waypoints: " + e.getMessage());
            }
        }
    }

    private void removeFollowWaypoint(BlockPos pos) {
        try {
            MinimapWorld waypointWorld = this.getWaypointWorld();
            if (waypointWorld == null) {
                if (this.showChatMessages.get()) {
                    this.warning("Could not get waypoint world for removal");
                }

                return;
            }

            WaypointSet currentSet = waypointWorld.getCurrentWaypointSet();
            if (currentSet == null) {
                if (this.showChatMessages.get()) {
                    this.warning("Could not get waypoint set for removal");
                }

                return;
            }

            String prefix = this.waypointPrefix.get();
            Waypoint toRemove = null;

            for (Waypoint wp : currentSet.getWaypoints()) {
                if (wp.getName().startsWith(prefix)) {
                    BlockPos p = new BlockPos(wp.getX(), wp.getY(), wp.getZ());
                    if (p.equals(pos)) {
                        toRemove = wp;
                        break;
                    }
                }
            }

            if (toRemove != null) {
                currentSet.remove(toRemove);

                try {
                    MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
                    if (minimapSession != null) {
                        minimapSession.getWorldManagerIO().saveWorld(waypointWorld);
                    }
                } catch (Exception saveEx) {
                    if (this.showChatMessages.get()) {
                        this.error("Failed to save waypoints to disk: " + saveEx.getMessage());
                    }
                }

                SupportMods.xaeroMinimap.requestWaypointsRefresh();
            }
        } catch (Exception e) {
            if (this.showChatMessages.get()) {
                this.error("Failed to remove waypoint: " + e.getMessage());
            }
        }
    }

    private void addWaypointToFollow(BlockPos pos) {
        if (!this.waypointsToFollow.contains(pos)) {
            this.waypointsToFollow.add(pos);
        }
    }

    private BlockPos getNextWaypoint() {
        if (this.waypointsToFollow.isEmpty()) {
            return null;
        }

        if (this.followMode.get() == WaypointFollower.FollowMode.Closest) {
            return this.getClosestWaypoint();
        }

        if (this.currentWaypointIndex >= this.waypointsToFollow.size()) {
            this.currentWaypointIndex = 0;
        }

        return this.waypointsToFollow.get(this.currentWaypointIndex);
    }

    private BlockPos getClosestWaypoint() {
        if (!this.waypointsToFollow.isEmpty() && this.mc.player != null) {
            Vec3 playerPos = this.mc.player.position();
            BlockPos closest = null;
            double closestDistance = Double.MAX_VALUE;

            for (BlockPos waypoint : this.waypointsToFollow) {
                double distance = playerPos.distanceTo(waypoint.getCenter());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = waypoint;
                }
            }

            return closest;
        } else {
            return null;
        }
    }

    private void removeCurrentWaypoint(BlockPos waypoint) {
        this.waypointsToFollow.remove(waypoint);
        this.removeFollowWaypoint(waypoint);
        this.waypointsCompletedThisSession++;
        if (this.followMode.get() == WaypointFollower.FollowMode.Numerical && this.currentWaypointIndex >= this.waypointsToFollow.size()) {
            this.currentWaypointIndex = 0;
        }
    }

    private void checkAndReloadWaypoints() {
        if (this.waypointsFullyLoaded) {
            this.reloadCheckTimer++;
            if (this.reloadCheckTimer >= 40) {
                this.reloadCheckTimer = 0;

                try {
                    SupportMods.xaeroMinimap.requestWaypointsRefresh();
                } catch (Exception var4) {
                }

                int currentCount = this.getTotalHuntWaypointCount();
                if (currentCount != this.lastKnownWaypointCount) {
                    int before = this.waypointsToFollow.size();
                    this.waypointsToFollow.clear();
                    this.waypointLoadRetries = 0;
                    this.attemptWaypointLoad();
                    int diff = this.waypointsToFollow.size() - before;
                    if (diff != 0 && this.showChatMessages.get()) {
                        if (diff > 0) {
                            this.info("Auto-reloaded: " + diff + " new waypoints added");
                        } else {
                            this.info("Auto-reloaded: " + Math.abs(diff) + " waypoints removed");
                        }
                    }

                    this.lastKnownWaypointCount = currentCount;
                }
            }
        }
    }

    private void startFlightMode(WaypointFollower.FlightMode mode) {
        if (mode != this.activeFlightMode) {
            this.stopFlightMode();
            switch (mode) {
                case RocketFly:
                    if (this.rocketFly != null && !this.rocketFly.isActive()) {
                        this.rocketFly.toggle();
                        this.activeFlightMode = WaypointFollower.FlightMode.RocketFly;
                    }
                    break;
                case Pitch40:
                    if (this.pitch40Util != null && !this.pitch40Util.isActive()) {
                        this.pitch40Util.toggle();
                        this.activeFlightMode = WaypointFollower.FlightMode.Pitch40;
                    }
                    break;
                case None:
                    this.activeFlightMode = WaypointFollower.FlightMode.None;
            }
        }
    }

    private void stopFlightMode() {
        if (this.activeFlightMode == WaypointFollower.FlightMode.RocketFly && this.rocketFly != null && this.rocketFly.isActive()) {
            this.rocketFly.toggle();
        } else if (this.activeFlightMode == WaypointFollower.FlightMode.Pitch40 && this.pitch40Util != null && this.pitch40Util.isActive()) {
            this.pitch40Util.toggle();
        }

        this.activeFlightMode = WaypointFollower.FlightMode.None;
    }

    private void rotateYawTowards(Vec3 targetPos, float rotationSpeedMultiplier) {
        if (this.mc.player != null) {
            Vec3 playerPos = this.mc.player.position();
            double deltaX = targetPos.x - playerPos.x;
            double deltaZ = targetPos.z - playerPos.z;
            float targetYaw = (float)(Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F;
            float currentYaw = this.mc.player.getYRot();
            float yawDiff = targetYaw - currentYaw;

            while (yawDiff > 180.0F) {
                yawDiff -= 360.0F;
            }

            while (yawDiff < -180.0F) {
                yawDiff += 360.0F;
            }

            if (Math.abs(yawDiff) > (float)this.yawDeadzone.get().doubleValue()) {
                if (this.snapRotation.get()) {
                    this.mc.player.setYRot(targetYaw);
                } else {
                    this.mc.player.setYRot(currentYaw + yawDiff * rotationSpeedMultiplier);
                }
            }
        }
    }

    private double getHorizontalDistance(Vec3 from, Vec3 to) {
        double deltaX = to.x - from.x;
        double deltaZ = to.z - from.z;
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private Vec3 getAdjustedWaypointPos(BlockPos waypoint) {
        int adjustedX = this.toPlayerDimensionCoord(waypoint.getX());
        int adjustedZ = this.toPlayerDimensionCoord(waypoint.getZ());
        return new Vec3(adjustedX + 0.5, waypoint.getY() + 0.5, adjustedZ + 0.5);
    }

    private void useFireworkRocket() {
        if (this.mc.player != null && this.mc.gameMode != null) {
            if (this.fireworkCooldown <= 0) {
                boolean foundRocket = false;

                for (int n = 0; n < 9; n++) {
                    Item item = this.mc.player.getInventory().getItem(n).getItem();
                    if (item == Items.FIREWORK_ROCKET) {
                        InvUtils.swap(n, true);
                        foundRocket = true;
                        break;
                    }
                }

                if (foundRocket) {
                    this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
                    InvUtils.swapBack();
                    this.fireworkCooldown = 10;
                } else {
                    int movedSlot = -1;

                    for (int n = 9; n < ((PlayerInventoryAccessor)this.mc.player.getInventory()).getMain().size(); n++) {
                        Item item = this.mc.player.getInventory().getItem(n).getItem();
                        if (item == Items.FIREWORK_ROCKET) {
                            InvUtils.move().from(n).to(((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot());
                            movedSlot = n;
                            foundRocket = true;
                            break;
                        }
                    }

                    if (foundRocket) {
                        this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
                        if (movedSlot != -1) {
                            InvUtils.move().from(((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot()).to(movedSlot);
                        }

                        this.fireworkCooldown = 10;
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (!this.isDeactivating) {
            if (this.mc.player != null && this.mc.level != null) {
                String currentDim = this.getCurrentDimensionKey();
                if (!currentDim.equals(this.lastDimensionKey)) {
                    this.lastDimensionKey = currentDim;
                    if (this.showChatMessages.get()) {
                        this.info("Dimension changed, reloading waypoints...");
                    }

                    this.stopBaritone();
                    this.waypointsToFollow.clear();
                    this.currentWaypointIndex = 0;
                    this.waypointLoadRetries = 0;
                    this.waypointsFullyLoaded = false;
                    this.lastPlayerPos = this.mc.player.position();
                    this.startAsyncWaypointLoad();
                }

                boolean inSurvival = this.mc.gameMode == null || this.mc.gameMode.getPlayerMode() == GameType.SURVIVAL;
                if (!inSurvival) {
                    if (this.wasInSurvival) {
                        this.stopFlightMode();
                        this.stopBaritone();
                        this.rearmStartupFlight();
                        if (this.showChatMessages.get()) {
                            this.info("Left survival (queue/spectator) - paused until rejoin");
                        }
                    }

                    this.wasInSurvival = false;
                } else {
                    if (!this.wasInSurvival) {
                        this.rearmStartupFlight();
                        this.lastPlayerPos = this.mc.player.position();
                        if (this.showChatMessages.get()) {
                            this.info("Survival detected - resuming waypoint following");
                        }
                    }

                    this.wasInSurvival = true;
                    if (this.waypointsFullyLoaded) {
                        String currentWaypointWorldKey = this.getWaypointWorldKey();
                        if (!currentWaypointWorldKey.isEmpty() && !currentWaypointWorldKey.equals(this.lastWaypointWorldKey)) {
                            this.lastWaypointWorldKey = currentWaypointWorldKey;
                            if (this.showChatMessages.get()) {
                                this.info("Waypoint set changed, reloading waypoints...");
                            }

                            this.stopBaritone();
                            this.waypointsToFollow.clear();
                            this.currentWaypointIndex = 0;
                            this.waypointLoadRetries = 0;
                            this.waypointsFullyLoaded = false;
                            this.startAsyncWaypointLoad();
                        } else {
                            if (this.lastPlayerPos != null) {
                                double distance = this.mc.player.position().distanceTo(this.lastPlayerPos);
                                if (distance < 100.0) {
                                    this.totalDistanceTraveled += distance;
                                }
                            }

                            this.lastPlayerPos = this.mc.player.position();
                            this.checkAndReloadWaypoints();
                            if (!this.waypointsToFollow.isEmpty() && !this.isPaused) {
                                if (this.fireworkCooldown > 0) {
                                    this.fireworkCooldown--;
                                }

                                this.isInNether = this.isInNether(this.mc.level);
                                this.isInEnd = this.isInEnd(this.mc.level);
                                if (this.autoStartFlight.get() && !this.startupRecastTriggered) {
                                    if (!this.mc.player.isFallFlying()) {
                                        this.startupDelayTicks++;
                                        if (this.startupDelayTicks >= this.startupDelay.get()) {
                                            if (this.hasElytraEquipped()) {
                                                this.triggerStartupRecast();
                                            } else if (this.showChatMessages.get()) {
                                                this.error("No elytra equipped, cannot auto-start flight.");
                                            }

                                            this.startupRecastTriggered = true;
                                        }

                                        return;
                                    }

                                    this.startupRecastTriggered = true;
                                    this.baritoneActivatedAfterStartup = true;
                                    if (this.showChatMessages.get()) {
                                        this.info("Already flying, starting navigation immediately");
                                    }
                                }

                                if (this.startupRecastTriggered && !this.baritoneActivatedAfterStartup) {
                                    if (this.mc.player.isFallFlying()) {
                                        this.glidingTicksAfterStartup++;
                                        if (this.glidingTicksAfterStartup >= 2) {
                                            this.baritoneActivatedAfterStartup = true;
                                            if (this.showChatMessages.get()) {
                                                this.info("Flight stabilized, starting navigation");
                                            }
                                        }
                                    }

                                    if (!this.baritoneActivatedAfterStartup) {
                                        return;
                                    }
                                }

                                if (this.isInNether) {
                                    this.handleNetherPathfinding();
                                } else if (this.isInEnd) {
                                    this.handleEndPathfinding();
                                } else {
                                    this.handleOverworldPathfinding();
                                }

                                if (this.showStatistics.get() && !this.waypointsToFollow.isEmpty()) {
                                    StringBuilder stats = new StringBuilder();
                                    stats.append(this.waypointsToFollow.size()).append(" waypoints");
                                    if (this.waypointsCompletedThisSession > 0) {
                                        stats.append(String.format(" | %d done", this.waypointsCompletedThisSession));
                                    }

                                    stats.append(String.format(" | %.0f blocks traveled", this.totalDistanceTraveled));
                                    this.mc.player.displayClientMessage(Component.literal(stats.toString()), true);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean hasElytraEquipped() {
        return this.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
    }

    private boolean isTextFieldFocusedInXaeroMap() {
        if (this.mc.screen == null) {
            return false;
        }

        if (!this.mc.screen.getClass().getName().equals("xaero.map.gui.GuiMap")) {
            return false;
        }

        GuiEventListener focused = this.mc.screen.getFocused();
        return focused instanceof EditBox ? true : focused != null && focused.getClass().getName().contains("TextField");
    }

    private boolean isTypingInTextField() {
        if (this.mc.screen == null) {
            return false;
        }

        if (this.mc.screen instanceof ChatScreen) {
            return true;
        }

        GuiEventListener focused = this.mc.screen.getFocused();
        return focused instanceof EditBox ? true : focused != null && focused.getClass().getName().contains("TextField");
    }

    private void triggerStartupRecast() {
        if (this.elytraRecast == null) {
            if (this.showChatMessages.get()) {
                this.error("ElytraRecast module not found for startup.");
            }
        } else if (!this.elytraRecast.isActive()) {
            Setting<Boolean> waitForFall = (Setting<Boolean>)this.elytraRecast.settings.get("wait-for-fall");
            Setting<Boolean> ascendMode = (Setting<Boolean>)this.elytraRecast.settings.get("ascend-mode");
            Setting<Boolean> autoDisable = (Setting<Boolean>)this.elytraRecast.settings.get("auto-disable");
            if (waitForFall != null) {
                waitForFall.set(false);
            }

            if (autoDisable != null) {
                autoDisable.set(true);
            }

            if (ascendMode != null) {
                if (this.isInNether) {
                    ascendMode.set(false);
                } else {
                    WaypointFollower.FlightMode mode = this.isInEnd ? this.endFlightMode.get() : this.overworldFlightMode.get();
                    ascendMode.set(mode == WaypointFollower.FlightMode.Pitch40);
                }
            }

            if (this.showChatMessages.get()) {
                this.info("Starting flight via ElytraRecast...");
            }

            this.elytraRecast.toggle();
        }
    }

    private void handleNetherPathfinding() {
        WaypointFollower.NetherFlightMode mode = this.netherFlightMode.get();
        if (mode == WaypointFollower.NetherFlightMode.Baritone) {
            this.handleNetherBaritonePathfinding();
        } else {
            this.handleNetherManualPathfinding();
        }
    }

    private void handleNetherBaritonePathfinding() {
        if (!this.waypointsToFollow.isEmpty()) {
            if (!BaritoneHelper.hasElytraProcess()) {
                if (this.showChatMessages.get()) {
                    this.error("Baritone with elytra support required for nether elytra pathfinding");
                }
            } else {
                BlockPos nextWaypoint = this.getNextWaypoint();
                if (nextWaypoint != null) {
                    try {
                        IBaritone baritoneInstance = BaritoneAPI.getProvider().getPrimaryBaritone();
                        BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
                        if (!baritoneInstance.getElytraProcess().isLoaded()) {
                            if (this.showChatMessages.get()) {
                                this.error("Baritone elytra process not loaded - native library missing?");
                            }

                            return;
                        }

                        int goalX = this.toPlayerDimensionCoord(nextWaypoint.getX());
                        int goalZ = this.toPlayerDimensionCoord(nextWaypoint.getZ());
                        Object currentDest = baritoneInstance.getElytraProcess().currentDestination();
                        boolean needsNewGoal = this.currentBaritoneTarget == null || !this.currentBaritoneTarget.equals(nextWaypoint) || currentDest == null;
                        if (needsNewGoal) {
                            this.currentBaritoneTarget = nextWaypoint;
                            GoalXZ goal = new GoalXZ(goalX, goalZ);
                            baritoneInstance.getElytraProcess().pathTo(goal);
                            if (this.showChatMessages.get()) {
                                this.info("Set Baritone elytra goal to: " + goalX + ", " + goalZ);
                            }
                        }
                    } catch (Exception e) {
                        if (this.showChatMessages.get()) {
                            this.error("Baritone not available for nether pathfinding: " + e.getMessage());
                        }

                        return;
                    }

                    Vec3 playerPos = this.mc.player.position();
                    int checkX = this.toPlayerDimensionCoord(nextWaypoint.getX());
                    int checkZ = this.toPlayerDimensionCoord(nextWaypoint.getZ());
                    double deltaX = checkX - playerPos.x;
                    double deltaZ = checkZ - playerPos.z;
                    double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    double effectiveReachDistance = this.reachDistance.get() * this.netherReachMultiplier.get();
                    if (horizontalDistance <= effectiveReachDistance) {
                        this.removeCurrentWaypoint(nextWaypoint);
                        this.currentBaritoneTarget = null;
                        if (!this.waypointsToFollow.isEmpty()) {
                            BlockPos newTarget = this.getNextWaypoint();
                            if (newTarget != null) {
                                this.currentBaritoneTarget = newTarget;

                                try {
                                    int newGoalX = this.toPlayerDimensionCoord(newTarget.getX());
                                    int newGoalZ = this.toPlayerDimensionCoord(newTarget.getZ());
                                    IBaritone baritoneInstance = BaritoneAPI.getProvider().getPrimaryBaritone();
                                    GoalXZ goal = new GoalXZ(newGoalX, newGoalZ);
                                    baritoneInstance.getElytraProcess().pathTo(goal);
                                    if (this.showChatMessages.get()) {
                                        this.info("Set next Baritone elytra goal to: " + newGoalX + ", " + newGoalZ);
                                    }
                                } catch (Exception e) {
                                    if (this.showChatMessages.get()) {
                                        this.error("Failed to set next Baritone goal: " + e.getMessage());
                                    }

                                    this.currentBaritoneTarget = null;
                                }
                            }
                        } else {
                            try {
                                IBaritone baritoneInstance = BaritoneAPI.getProvider().getPrimaryBaritone();
                                boolean canceled = baritoneInstance.getPathingBehavior().cancelEverything();
                                if (!canceled && baritoneInstance.getElytraProcess().isActive()) {
                                    baritoneInstance.getPathingBehavior().forceCancel();
                                }

                                baritoneInstance.getInputOverrideHandler().clearAllKeys();
                                if (this.showChatMessages.get()) {
                                    this.info("All waypoints reached, stopping Baritone");
                                }

                                this.playCompletionSound();
                            } catch (Exception e) {
                                if (this.showChatMessages.get()) {
                                    this.error("Failed to stop Baritone: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleNetherManualPathfinding() {
        if (!this.waypointsToFollow.isEmpty()) {
            BlockPos nextWaypoint = this.getNextWaypoint();
            if (nextWaypoint != null) {
                Vec3 playerPos = this.mc.player.position();
                int targetX = this.toPlayerDimensionCoord(nextWaypoint.getX());
                int targetZ = this.toPlayerDimensionCoord(nextWaypoint.getZ());
                Vec3 scaledTarget = new Vec3(targetX, nextWaypoint.getY(), targetZ);
                this.rotateYawTowards(scaledTarget, this.rotationSpeed.get().floatValue());
                double deltaX = targetX - playerPos.x;
                double deltaZ = targetZ - playerPos.z;
                double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                double effectiveReachDistance = this.reachDistance.get() * this.netherReachMultiplier.get();
                if (horizontalDistance < effectiveReachDistance) {
                    this.removeCurrentWaypoint(nextWaypoint);
                    if (this.waypointsToFollow.isEmpty()) {
                        if (this.showChatMessages.get()) {
                            this.info("All waypoints reached");
                        }

                        this.playCompletionSound();
                    }
                }
            }
        }
    }

    private void handleOverworldPathfinding() {
        WaypointFollower.FlightMode mode = this.overworldFlightMode.get();
        this.handleDimensionPathfinding(mode);
    }

    private void handleEndPathfinding() {
        WaypointFollower.FlightMode mode = this.endFlightMode.get();
        this.handleDimensionPathfinding(mode);
    }

    private void handleDimensionPathfinding(WaypointFollower.FlightMode mode) {
        if (!this.waypointsToFollow.isEmpty()) {
            BlockPos nextWaypoint = this.getNextWaypoint();
            if (nextWaypoint != null) {
                switch (mode) {
                    case RocketFly:
                        this.handleRocketFlyPathfinding(nextWaypoint);
                        break;
                    case Pitch40:
                        this.handlePitch40Pathfinding(nextWaypoint);
                        break;
                    case None:
                        this.handleManualPathfinding(nextWaypoint);
                }
            }
        }
    }

    private void handleRocketFlyPathfinding(BlockPos nextWaypoint) {
        if (this.rocketFly == null) {
            if (this.showChatMessages.get()) {
                this.error("RocketFly module not found.");
            }
        } else {
            if (!this.rocketFly.isActive()) {
                if (!this.hasElytraEquipped()) {
                    return;
                }

                this.startFlightMode(WaypointFollower.FlightMode.RocketFly);
                if (this.showChatMessages.get()) {
                    this.info("Started RocketFly for pathfinding");
                }
            }

            Vec3 targetPos = this.getAdjustedWaypointPos(nextWaypoint);
            this.rotateYawTowards(targetPos, this.rotationSpeed.get().floatValue());
            double horizontalDistance = this.getHorizontalDistance(this.mc.player.position(), targetPos);
            if (horizontalDistance < this.reachDistance.get()) {
                this.removeCurrentWaypoint(nextWaypoint);
                if (this.waypointsToFollow.isEmpty()) {
                    this.stopFlightMode();
                    if (this.showChatMessages.get()) {
                        this.info("All waypoints reached");
                    }

                    this.playCompletionSound();
                }
            }
        }
    }

    private void handlePitch40Pathfinding(BlockPos nextWaypoint) {
        if (this.pitch40Util == null) {
            if (this.showChatMessages.get()) {
                this.error("Pitch40 module not found.");
            }
        } else {
            if (!this.pitch40Util.isActive()) {
                if (!this.hasElytraEquipped()) {
                    return;
                }

                this.startFlightMode(WaypointFollower.FlightMode.Pitch40);
                if (this.showChatMessages.get()) {
                    this.info("Started Pitch40 for pathfinding");
                }
            }

            if (this.pitch40Util.autoFirework != null && !this.pitch40Util.autoFirework.get()) {
                this.pitch40Util.autoFirework.set(true);
            }

            Vec3 targetPos = this.getAdjustedWaypointPos(nextWaypoint);
            this.rotateYawTowards(targetPos, this.rotationSpeed.get().floatValue());
            if (this.mc.player.getXRot() < -35.0F && this.mc.player.getDeltaMovement().y < -0.05) {
                this.useFireworkRocket();
            }

            double horizontalDistance = this.getHorizontalDistance(this.mc.player.position(), targetPos);
            if (horizontalDistance < this.reachDistance.get()) {
                this.removeCurrentWaypoint(nextWaypoint);
                if (this.waypointsToFollow.isEmpty()) {
                    this.stopFlightMode();
                    if (this.showChatMessages.get()) {
                        this.info("All waypoints reached");
                    }

                    this.playCompletionSound();
                }
            }
        }
    }

    private void handleManualPathfinding(BlockPos nextWaypoint) {
        Vec3 targetPos = this.getAdjustedWaypointPos(nextWaypoint);
        this.rotateYawTowards(targetPos, this.rotationSpeed.get().floatValue());
        double horizontalDistance = this.getHorizontalDistance(this.mc.player.position(), targetPos);
        if (horizontalDistance < this.reachDistance.get()) {
            this.removeCurrentWaypoint(nextWaypoint);
            if (this.waypointsToFollow.isEmpty()) {
                if (this.showChatMessages.get()) {
                    this.info("All waypoints reached");
                }

                this.playCompletionSound();
            }
        }
    }

    private void playCompletionSound() {
        if (this.completionSound.get() && this.mc.player != null) {
            this.mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
        }

        this.handleCompletionAction();
    }

    private void handleCompletionAction() {
        WaypointFollower.CompletionAction action = this.completionAction.get();
        if (action != WaypointFollower.CompletionAction.None) {
            if (this.isActive()) {
                this.toggle();
            }

            switch (action) {
                case AutoLog:
                    if (this.showChatMessages.get()) {
                        this.info("Out of waypoints, auto-logging.");
                    }

                    Utils.illegalDisconnect(true, BepConfig.illegalDisconnectMethodSetting.get());
                    break;
                case AreaLoader:
                    this.enableModule(Modules.get().get(AreaLoader.class), "AreaLoader");
                    break;
                case TrailFollower:
                    this.enableModule(Modules.get().get(TrailFollower.class), "TrailFollower");
            }
        }
    }

    private void enableModule(Module target, String label) {
        if (target == null) {
            if (this.showChatMessages.get()) {
                this.error("Out of waypoints, but " + label + " was not found.");
            }
        } else {
            if (!target.isActive()) {
                target.toggle();
            }

            if (this.showChatMessages.get()) {
                this.info("Out of waypoints, enabled " + label + ".");
            }
        }
    }

    @Override
    public String getInfoString() {
        if (this.waypointsToFollow.isEmpty()) {
            return "No waypoints";
        }

        StringBuilder info = new StringBuilder();
        if (this.isPaused) {
            info.append("[PAUSED] ");
        }

        info.append(this.waypointsToFollow.size()).append(" waypoints");
        String modeInfo = "";
        if (this.isInNether) {
            modeInfo = " [" + this.netherFlightMode.get() + "]";
        } else if (this.isInEnd) {
            modeInfo = " [" + this.endFlightMode.get() + "]";
        } else {
            modeInfo = " [" + this.overworldFlightMode.get() + "]";
        }

        info.append(modeInfo);
        BlockPos next = this.getNextWaypoint();
        if (next != null && this.mc.player != null) {
            double dist = this.getHorizontalDistance(this.mc.player.position(), this.getAdjustedWaypointPos(next));
            info.append(String.format(" - %.0fm", dist));
        }

        if (this.showStatistics.get() && this.waypointsCompletedThisSession > 0) {
            info.append(String.format(" | %d completed", this.waypointsCompletedThisSession));
        }

        return info.toString();
    }

    public enum CompletionAction {
        None("None"),
        AutoLog("Auto Log"),
        AreaLoader("AreaLoader"),
        TrailFollower("TrailFollower");

        private final String name;

        CompletionAction(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public enum FlightMode {
        RocketFly("RocketFly"),
        Pitch40("Pitch40"),
        None("None");

        private final String name;

        FlightMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public enum FollowMode {
        Closest("Closest"),
        Numerical("Numerical");

        private final String name;

        FollowMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public enum NetherFlightMode {
        Baritone("Baritone"),
        None("None");

        private final String name;

        NetherFlightMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
