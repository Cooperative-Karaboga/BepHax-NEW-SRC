package bep.hax.modules.arealoader;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IElytraProcess;
import bep.hax.modules.Pitch40;
import bep.hax.modules.RocketFly;
import bep.hax.util.BaritoneHelper;
import bep.hax.util.MapUtil;
import bep.hax.util.Utils;
import bep.hax.util.XaeroWaypointManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public abstract class AreaLoaderMode {
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    protected static final String WAYPOINT_CATEGORY = "AreaLoader";
    protected final AreaLoader searchArea;
    protected final Minecraft mc;
    private final AreaLoaderModes type;
    protected long paused = 0L;
    protected BlockPos currentGoalWaypoint = null;
    protected AreaLoaderMode.CurrentDimension currentDimension = AreaLoaderMode.CurrentDimension.UNKNOWN;
    private String lastKnownDimensionSuffix = null;
    protected boolean isInNether = false;
    protected boolean flightModesInitialized = false;
    private boolean oldAutoBoundAdjustValue = false;
    private AreaLoader.OverworldFlightMode enabledOverworldMode = null;
    private boolean weEnabledPitch40 = false;
    private boolean weEnabledRocketFly = false;
    private boolean pitch40SettingsModified = false;
    protected BlockPos currentBaritoneGoal = null;
    protected boolean baritoneElytraStarted = false;
    private long lastGoalSetTime = 0L;
    private static final long GOAL_SET_COOLDOWN_MS = 2000L;
    private BlockPos lastPlayerPos = null;
    private long lastMovementCheckTime = 0L;
    private int stuckCounter = 0;
    private static final int STUCK_THRESHOLD = 10;

    public AreaLoaderMode(AreaLoaderModes type) {
        this.searchArea = Modules.get().get(AreaLoader.class);
        this.mc = Minecraft.getInstance();
        this.type = type;
    }

    protected String saveFileName() {
        return this.type.fileName();
    }

    protected void debugInfo(String message) {
        if (this.searchArea.debugMode.get()) {
            ChatUtils.info(message);
        }
    }

    protected void debugInfo(String format, Object... args) {
        if (this.searchArea.debugMode.get()) {
            ChatUtils.info(format, args);
        }
    }

    protected void updateGoalWaypoint(BlockPos target) {
        if (!this.searchArea.showGoalWaypoint.get()) {
            this.clearGoalWaypoint();
        } else if (target == null) {
            this.clearGoalWaypoint();
        } else if (this.currentGoalWaypoint == null
            || this.currentGoalWaypoint.getX() != target.getX()
            || this.currentGoalWaypoint.getZ() != target.getZ()) {
            this.clearGoalWaypoint();
            this.currentGoalWaypoint = target;
            XaeroWaypointManager.addWaypoint("AreaLoader", target, "Goal", "→", MapUtil.WpColor.Aqua, true);
        }
    }

    protected void clearGoalWaypoint() {
        if (this.currentGoalWaypoint != null) {
            XaeroWaypointManager.removeWaypointXZ("AreaLoader", this.currentGoalWaypoint.getX(), this.currentGoalWaypoint.getZ());
            this.currentGoalWaypoint = null;
        }
    }

    protected boolean isNether() {
        return this.mc.level != null && this.mc.level.dimension().equals(Level.NETHER);
    }

    protected boolean isEnd() {
        return this.mc.level != null && this.mc.level.dimension().equals(Level.END);
    }

    protected boolean isSurvival() {
        return this.mc.player != null && this.mc.gameMode != null ? this.mc.gameMode.getPlayerMode().isSurvival() : false;
    }

    protected AreaLoaderMode.CurrentDimension getDimension() {
        if (this.mc.level == null) {
            return AreaLoaderMode.CurrentDimension.UNKNOWN;
        } else if (this.isNether()) {
            return AreaLoaderMode.CurrentDimension.NETHER;
        } else {
            return this.isEnd() ? AreaLoaderMode.CurrentDimension.END : AreaLoaderMode.CurrentDimension.OVERWORLD;
        }
    }

    protected void checkDimensionChange() {
        if (this.isWorldReady()) {
            AreaLoaderMode.CurrentDimension newDimension = this.getDimension();
            if (this.flightModesInitialized && newDimension != this.currentDimension) {
                this.debugInfo("AreaLoader: Dimension change detected (was %s, now %s). Switching flight modes...", this.currentDimension, newDimension);
                this.cleanupFlightModes();
                this.flightModesInitialized = false;
                this.currentDimension = newDimension;
                this.isInNether = newDimension == AreaLoaderMode.CurrentDimension.NETHER;
                this.initializeFlightModes();
            }

            if (!this.flightModesInitialized) {
                this.initializeFlightModes();
            }
        }
    }

    private boolean isWorldReady() {
        if (this.mc.level != null && this.mc.player != null) {
            try {
                ResourceKey<Level> key = this.mc.level.dimension();
                return key != null && key.identifier() != null;
            } catch (Exception e) {
                return false;
            }
        } else {
            return false;
        }
    }

    protected void initializeFlightModes() {
        if (!this.flightModesInitialized) {
            if (!this.isWorldReady()) {
                this.debugInfo("AreaLoader: World not ready, deferring flight mode initialization.");
            } else {
                this.currentDimension = this.getDimension();
                this.isInNether = this.currentDimension == AreaLoaderMode.CurrentDimension.NETHER;
                if (!this.isSurvival()) {
                    this.debugInfo("AreaLoader: Not in survival mode, deferring flight mode initialization.");
                } else {
                    this.flightModesInitialized = true;
                    this.enabledOverworldMode = null;
                    this.debugInfo(
                        "AreaLoader: Initializing flight modes. currentDimension=%s, dimension=%s",
                        this.currentDimension,
                        this.mc.level.dimension().identifier().toString()
                    );
                    if (this.currentDimension == AreaLoaderMode.CurrentDimension.NETHER) {
                        this.debugInfo("AreaLoader: In Nether, skipping overworld flight modules.");
                        if (this.searchArea.netherPathMode.get() == AreaLoader.NetherPathMode.BARITONE_ELYTRA) {
                            try {
                                Class.forName("baritone.api.BaritoneAPI");
                                this.debugInfo("AreaLoader: Nether detected, using Baritone elytra mode.");
                            } catch (ClassNotFoundException e) {
                                this.debugInfo("AreaLoader: Baritone is required for Nether pathing. Disabling.");
                                this.disable();
                                return;
                            }
                        }
                    } else if (this.isNether()) {
                        this.debugInfo("AreaLoader: Safety check caught nether, not enabling overworld modules.");
                        this.currentDimension = AreaLoaderMode.CurrentDimension.NETHER;
                        this.isInNether = true;
                    } else {
                        if (this.currentDimension == AreaLoaderMode.CurrentDimension.END) {
                            this.debugInfo("AreaLoader: In End dimension, using overworld flight mode.");
                        }

                        this.enabledOverworldMode = this.searchArea.overworldFlightMode.get();
                        if (this.enabledOverworldMode == AreaLoader.OverworldFlightMode.PITCH40) {
                            Module pitch40Module = Modules.get().get(Pitch40.class);
                            if (pitch40Module != null) {
                                if (!pitch40Module.isActive()) {
                                    pitch40Module.toggle();
                                    this.weEnabledPitch40 = true;
                                    this.debugInfo("AreaLoader: Pitch40 enabled.");
                                }

                                if (this.searchArea.pitch40AutoFirework.get()) {
                                    Setting<Boolean> autoFireworkSetting = (Setting<Boolean>)pitch40Module.settings.get("auto-firework");
                                    if (autoFireworkSetting != null) {
                                        autoFireworkSetting.set(true);
                                    }
                                }

                                Setting<Boolean> autoBoundAdjustSetting = (Setting<Boolean>)pitch40Module.settings.get("auto-bound-adjust");
                                if (autoBoundAdjustSetting != null) {
                                    this.oldAutoBoundAdjustValue = autoBoundAdjustSetting.get();
                                    autoBoundAdjustSetting.set(false);
                                    this.pitch40SettingsModified = true;
                                }
                            }
                        } else if (this.enabledOverworldMode == AreaLoader.OverworldFlightMode.ROCKETS) {
                            RocketFly rocketFly = Modules.get().get(RocketFly.class);
                            if (rocketFly != null && !rocketFly.isActive()) {
                                rocketFly.toggle();
                                this.weEnabledRocketFly = true;
                                this.debugInfo("AreaLoader: RocketFly enabled.");
                            }
                        }
                    }
                }
            }
        }
    }

    protected void cleanupFlightModes() {
        if (this.baritoneElytraStarted) {
            try {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("cancel");
                this.debugInfo("AreaLoader: Baritone cancelled.");
            } catch (Exception var2) {
            }
        }

        this.cleanupOverworldModules();
        this.flightModesInitialized = false;
        this.baritoneElytraStarted = false;
        this.currentBaritoneGoal = null;
        this.lastGoalSetTime = 0L;
        this.lastPlayerPos = null;
        this.lastMovementCheckTime = 0L;
        this.stuckCounter = 0;
        this.enabledOverworldMode = null;
    }

    private void cleanupOverworldModules() {
        Module pitch40Module = Modules.get().get(Pitch40.class);
        if (pitch40Module != null) {
            if (this.pitch40SettingsModified) {
                Setting<Boolean> autoBoundAdjustSetting = (Setting<Boolean>)pitch40Module.settings.get("auto-bound-adjust");
                if (autoBoundAdjustSetting != null) {
                    autoBoundAdjustSetting.set(this.oldAutoBoundAdjustValue);
                }

                this.pitch40SettingsModified = false;
            }

            if (this.weEnabledPitch40 && pitch40Module.isActive()) {
                pitch40Module.toggle();
                this.debugInfo("AreaLoader: Pitch40 disabled.");
            }

            this.weEnabledPitch40 = false;
        }

        RocketFly rocketFly = Modules.get().get(RocketFly.class);
        if (rocketFly != null) {
            if (this.weEnabledRocketFly && rocketFly.isActive()) {
                rocketFly.resetYLock();
                rocketFly.toggle();
                this.debugInfo("AreaLoader: RocketFly disabled.");
            }

            this.weEnabledRocketFly = false;
        }
    }

    protected void setBaritoneGoal(BlockPos goal) {
        if (this.searchArea.netherPathMode.get() == AreaLoader.NetherPathMode.BARITONE_ELYTRA) {
            if (goal != null) {
                if (!BaritoneHelper.hasElytraProcess()) {
                    this.debugInfo("Baritone with elytra support required for elytra pathfinding");
                } else {
                    long now = System.currentTimeMillis();
                    if (!goal.equals(this.currentBaritoneGoal) || now - this.lastGoalSetTime >= 2000L) {
                        this.currentBaritoneGoal = goal;
                        this.lastGoalSetTime = now;
                        this.stuckCounter = 0;

                        try {
                            BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
                            BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(new GoalXZ(goal.getX(), goal.getZ()));
                            this.baritoneElytraStarted = true;
                            this.debugInfo("Baritone goal set to X=%d Z=%d", goal.getX(), goal.getZ());
                        } catch (Exception e) {
                            this.debugInfo("Failed to set Baritone goal: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    protected boolean hasReachedBaritoneGoal() {
        if (this.currentBaritoneGoal != null && this.mc.player != null) {
            double distance = Math.sqrt(
                this.mc
                    .player
                    .blockPosition()
                    .distToLowCornerSqr(this.currentBaritoneGoal.getX(), this.mc.player.getY(), this.currentBaritoneGoal.getZ())
            );
            return distance < this.searchArea.netherWaypointReachDistance.get().intValue();
        } else {
            return false;
        }
    }

    protected boolean isBaritoneIdle() {
        if (!BaritoneHelper.hasElytraProcess()) {
            return true;
        }

        try {
            IElytraProcess elytraProcess = BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess();
            return elytraProcess.currentDestination() == null;
        } catch (Exception e) {
            return true;
        }
    }

    protected boolean isStuck() {
        if (this.mc.player != null && this.currentBaritoneGoal != null) {
            long now = System.currentTimeMillis();
            BlockPos currentPos = this.mc.player.blockPosition();
            if (now - this.lastMovementCheckTime < 1000L) {
                return false;
            }

            this.lastMovementCheckTime = now;
            if (this.lastPlayerPos != null) {
                double movedDistance = Math.sqrt(currentPos.distSqr(this.lastPlayerPos));
                if (movedDistance < 5.0) {
                    this.stuckCounter++;
                    if (this.stuckCounter >= 10) {
                        this.debugInfo("Baritone appears stuck (no progress for %d checks)", this.stuckCounter);
                        return true;
                    }
                } else {
                    this.stuckCounter = 0;
                }
            }

            this.lastPlayerPos = currentPos;
            return false;
        } else {
            return false;
        }
    }

    protected boolean needsNewGoal() {
        if (this.currentBaritoneGoal == null) {
            return true;
        } else if (this.hasReachedBaritoneGoal()) {
            return true;
        } else {
            long now = System.currentTimeMillis();
            if (now - this.lastGoalSetTime < 2000L) {
                return false;
            } else if (this.isBaritoneIdle()) {
                this.debugInfo("Baritone idle, needs new goal");
                return true;
            } else if (this.isStuck()) {
                this.stuckCounter = 0;
                return true;
            } else {
                return false;
            }
        }
    }

    public void onTick() {
        this.checkDimensionChange();
    }

    public void onActivate() {
        if (this.mc.level != null) {
            this.getDimensionSuffix();
        }
    }

    public void onDeactivate() {
        Utils.setPressed(this.mc.options.keyUp, false);
        this.cleanupFlightModes();
        this.clearGoalWaypoint();
    }

    public abstract void resetState();

    public void disable() {
        this.mc.execute(() -> {
            if (this.searchArea.isActive()) {
                this.searchArea.toggle();
            }
        });
    }

    protected String getDimensionSuffix() {
        if (this.mc.level == null) {
            return this.lastKnownDimensionSuffix != null ? this.lastKnownDimensionSuffix : null;
        }

        try {
            String suffix;
            if (this.mc.level.dimension().equals(Level.NETHER)) {
                suffix = "_nether";
            } else if (this.mc.level.dimension().equals(Level.END)) {
                suffix = "_end";
            } else {
                suffix = "_overworld";
            }

            this.lastKnownDimensionSuffix = suffix;
            return suffix;
        } catch (Exception e) {
            return this.lastKnownDimensionSuffix != null ? this.lastKnownDimensionSuffix : null;
        }
    }

    protected File getJsonFile(String fileName) {
        try {
            String saveName = this.searchArea.saveLocation.get();
            if (saveName == null || saveName.trim().isEmpty()) {
                saveName = "default";
            }

            File baseDir = new File(MeteorClient.FOLDER, "arealoader");
            File saveDir = new File(baseDir, saveName);
            String dimensionSuffix = this.getDimensionSuffix();
            if (dimensionSuffix == null) {
                this.debugInfo("Cannot determine dimension for save file - skipping save to prevent corruption");
                return null;
            } else {
                return new File(saveDir, fileName + dimensionSuffix + ".json");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    protected File getJsonFileForDimension(String fileName, String dimensionSuffix) {
        try {
            String saveName = this.searchArea.saveLocation.get();
            if (saveName == null || saveName.trim().isEmpty()) {
                saveName = "default";
            }

            File baseDir = new File(MeteorClient.FOLDER, "arealoader");
            File saveDir = new File(baseDir, saveName);
            return new File(saveDir, fileName + dimensionSuffix + ".json");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    protected void saveToJson(boolean goingToStart, AreaLoaderMode.PathingData pd) {
        if (pd != null) {
            if (!goingToStart && this.mc.player != null && this.mc.level != null) {
                pd.currPos = this.mc.player.blockPosition();
            }

            this.saveToJsonExact(pd);
        }
    }

    protected void saveToJsonExact(AreaLoaderMode.PathingData pd) {
        if (pd != null) {
            File file = this.getJsonFile(this.type.fileName());
            if (file == null) {
                this.debugInfo("Skipping save: cannot determine correct dimension file (likely in queue/disconnected)");
            } else {
                try {
                    if (!file.getParentFile().exists()) {
                        file.getParentFile().mkdirs();
                    }

                    Writer writer = new FileWriter(file);
                    GSON.toJson(pd, writer);
                    writer.flush();
                    writer.close();
                    if (!file.exists() || file.length() <= 0L) {
                        ChatUtils.info("Warning: Save file was not created or is empty: " + file.getAbsolutePath());
                    } else if (System.currentTimeMillis() % 30000L < 1000L) {
                        ChatUtils.info("Progress saved to: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
                    }
                } catch (IOException e) {
                    this.debugInfo("Failed to save progress to file: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public void clear() {
        File file = this.getJsonFile(this.type.fileName());
        if (file != null && file.exists()) {
            if (file.delete()) {
                this.debugInfo("✓ Cleared saved " + this.type + " progress from: " + file.getAbsolutePath());
                this.resetState();
            } else {
                this.debugInfo("✗ Failed to delete " + this.type + " save file: " + file.getAbsolutePath());
            }
        } else if (file != null) {
            this.debugInfo("No saved " + this.type + " progress to clear (file doesn't exist: " + file.getAbsolutePath() + ")");
            this.resetState();
        } else {
            this.debugInfo("No saved " + this.type + " progress to clear (invalid save path)");
        }
    }

    public void clearAll() {
        this.debugInfo("Clearing all AreaLoader saved progress (all dimensions)...");
        int deletedCount = 0;
        String[] dimensionSuffixes = new String[]{"_overworld", "_nether", "_end", ""};

        for (AreaLoaderModes mode : AreaLoaderModes.values()) {
            for (String suffix : dimensionSuffixes) {
                File file = this.getJsonFileForDimension(mode.fileName(), suffix);
                if (file != null && file.exists()) {
                    if (file.delete()) {
                        deletedCount++;
                        this.debugInfo("✓ Deleted " + mode.fileName() + suffix + ".json");
                    } else {
                        this.debugInfo("✗ Failed to delete " + mode.fileName() + suffix + ".json");
                    }
                }
            }
        }

        this.lastKnownDimensionSuffix = null;
        this.resetState();
        if (deletedCount == 0) {
            this.debugInfo("No save files found to clear, but in-memory state has been reset.");
        } else {
            this.debugInfo("Successfully deleted " + deletedCount + " save file(s) and reset state. Ready to start fresh!");
        }
    }

    @Override
    public String toString() {
        return this.type.toString();
    }

    protected enum CurrentDimension {
        OVERWORLD,
        NETHER,
        END,
        UNKNOWN;
    }

    protected static class PathingData {
        public BlockPos initialPos;
        public BlockPos currPos;
        public float yawDirection;
        public boolean mainPath;
    }
}
