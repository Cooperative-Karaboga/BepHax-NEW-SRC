package bep.hax.modules.arealoader.modes;

import bep.hax.modules.ElytraRecast;
import bep.hax.modules.arealoader.AreaLoader;
import bep.hax.modules.arealoader.AreaLoaderMode;
import bep.hax.modules.arealoader.AreaLoaderModes;
import bep.hax.util.Utils;
import java.io.File;
import java.io.FileReader;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.core.BlockPos;

public class ZigZag extends AreaLoaderMode {
    private ZigZag.PathingDataZigZag pd;
    private boolean goingToStart = true;
    private long startTime;
    private BlockPos nextWaypointTarget = null;
    private boolean recovering = false;
    private BlockPos recoveryTarget = null;
    private BlockPos lastKnownGoodPosition = null;
    private int offTrackCounter = 0;
    private static final int OFF_TRACK_THRESHOLD = 10;
    private static final int MAX_OFF_TRACK_DISTANCE = 200;
    private ElytraRecast elytraRecast = null;
    private long recoveryCompletedTime = 0L;
    private static final long RECOVERY_COOLDOWN_MS = 5000L;

    public ZigZag() {
        super(AreaLoaderModes.ZigZag);
    }

    @Override
    public void onActivate() {
        this.startTime = System.nanoTime();
        this.nextWaypointTarget = null;
        this.recovering = false;
        this.recoveryTarget = null;
        this.lastKnownGoodPosition = null;
        this.offTrackCounter = 0;
        this.elytraRecast = Modules.get().get(ElytraRecast.class);
        this.recoveryCompletedTime = 0L;
        File file = this.getJsonFile(this.saveFileName());
        if (file == null) {
            this.debugInfo("Error: Cannot create save file path. Check save-name setting.");
            float playerYaw = this.mc.player.getYRot();
            float mainYaw = this.normalizeToCardinal(playerYaw);
            float sideYaw = this.normalizeYaw(mainYaw + 90.0F);
            this.pd = new ZigZag.PathingDataZigZag(
                this.mc.player.blockPosition(), this.mc.player.blockPosition(), mainYaw, mainYaw, sideYaw, true, true, 0
            );
            this.goingToStart = false;
        } else if (!file.exists()) {
            float playerYaw = this.mc.player.getYRot();
            float mainYaw = this.normalizeToCardinal(playerYaw);
            float sideYaw = this.normalizeYaw(mainYaw + 90.0F);
            this.pd = new ZigZag.PathingDataZigZag(
                this.mc.player.blockPosition(), this.mc.player.blockPosition(), mainYaw, mainYaw, sideYaw, true, true, 0
            );
            this.goingToStart = false;
            ChatUtils.info(
                "ZigZag started from origin: " + this.pd.initialPos.toShortString() + ". Main direction: %.0f, Side direction: %.0f, Leg length: %d",
                mainYaw,
                sideYaw,
                this.searchArea.zigzagLegLength.get()
            );
        } else {
            try {
                this.debugInfo("Loading save file: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
                FileReader reader = new FileReader(file);
                this.pd = GSON.fromJson(reader, ZigZag.PathingDataZigZag.class);
                reader.close();
                this.goingToStart = true;
                ChatUtils.info("Loaded saved ZigZag successfully. Current: " + this.pd.currPos.toShortString() + ", Legs completed: " + this.pd.legsCompleted);
            } catch (Exception e) {
                ChatUtils.info("Failed to load saved ZigZag path. Starting fresh.");
                e.printStackTrace();
                float playerYaw = this.mc.player.getYRot();
                float mainYaw = this.normalizeToCardinal(playerYaw);
                float sideYaw = this.normalizeYaw(mainYaw + 90.0F);
                this.pd = new ZigZag.PathingDataZigZag(
                    this.mc.player.blockPosition(), this.mc.player.blockPosition(), mainYaw, mainYaw, sideYaw, true, true, 0
                );
                this.goingToStart = false;
            }
        }

        this.initializeFlightModes();
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        super.saveToJson(this.goingToStart, this.pd);
    }

    @Override
    public void resetState() {
        this.pd = null;
        this.goingToStart = true;
        this.nextWaypointTarget = null;
        this.recovering = false;
        this.recoveryTarget = null;
        this.lastKnownGoodPosition = null;
        this.offTrackCounter = 0;
        this.debugInfo("ZigZag state reset. Next activation will start fresh.");
    }

    @Override
    public void onTick() {
        super.onTick();
        if (this.mc.player != null && this.mc.level != null) {
            if (System.nanoTime() - this.startTime > 6.0E11) {
                this.startTime = System.nanoTime();
                super.saveToJson(this.goingToStart, this.pd);
            }

            if (System.nanoTime() < this.paused) {
                Utils.setPressed(this.mc.options.keyUp, false);
            } else {
                if (this.isInNether) {
                    this.onTickNether();
                } else {
                    this.onTickOverworld();
                }
            }
        }
    }

    private void onTickOverworld() {
        if (this.goingToStart) {
            this.updateGoalWaypoint(this.pd.currPos);
            if (Math.sqrt(
                    this.mc
                        .player
                        .blockPosition()
                        .distToLowCornerSqr(this.pd.currPos.getX(), this.mc.player.getY(), this.pd.currPos.getZ())
                )
                < 5.0) {
                this.goingToStart = false;
                this.mc.player.setDeltaMovement(0.0, 0.0, 0.0);
            } else {
                this.mc.player.setYRot((float)Rotations.getYaw(this.pd.currPos.getCenter()));
                Utils.setPressed(this.mc.options.keyUp, true);
            }
        } else {
            Utils.setPressed(this.mc.options.keyUp, true);
            this.mc.player.setYRot(this.pd.yawDirection);
            int legLength = this.searchArea.zigzagLegLength.get();
            int rowGap = this.searchArea.zigzagRowGap.get();
            if (this.pd.onMainLeg) {
                double distanceTraveled = this.getDistanceTraveled(this.pd.legStartPos, this.mc.player.blockPosition());
                if (distanceTraveled >= legLength) {
                    this.pd.yawDirection = this.pd.sideYaw;
                    this.pd.onMainLeg = false;
                    this.pd.legStartPos = this.mc.player.blockPosition();
                    this.mc.player.setDeltaMovement(0.0, 0.0, 0.0);
                    this.pd.legsCompleted++;
                }
            } else {
                double distanceTraveled = this.getDistanceTraveled(this.pd.legStartPos, this.mc.player.blockPosition());
                if (distanceTraveled >= rowGap) {
                    this.pd.goingForward = !this.pd.goingForward;
                    this.pd.yawDirection = this.pd.goingForward ? this.pd.mainYaw : this.normalizeYaw(this.pd.mainYaw + 180.0F);
                    this.pd.onMainLeg = true;
                    this.pd.legStartPos = this.mc.player.blockPosition();
                    this.mc.player.setDeltaMovement(0.0, 0.0, 0.0);
                }
            }
        }
    }

    private void onTickNether() {
        if (this.searchArea.netherPathMode.get() != AreaLoader.NetherPathMode.BARITONE_ELYTRA) {
            this.mc.player.setYRot(this.pd.yawDirection);
        } else {
            int legLength = this.searchArea.zigzagLegLength.get();
            int rowGap = this.searchArea.zigzagRowGap.get();
            int reachDist = this.searchArea.netherWaypointReachDistance.get();
            if (this.recovering && this.recoveryTarget != null) {
                double distToRecovery = Math.sqrt(
                    this.mc
                        .player
                        .blockPosition()
                        .distToLowCornerSqr(this.recoveryTarget.getX(), this.mc.player.getY(), this.recoveryTarget.getZ())
                );
                boolean elytraRecovering = this.elytraRecast != null && this.elytraRecast.isActive() && this.elytraRecast.isRecovering();
                if (elytraRecovering) {
                    this.debugInfo("ZigZag: Waiting for ElytraRecast recovery to complete...");
                } else {
                    if (distToRecovery < reachDist) {
                        this.debugInfo(
                            "ZigZag: Recovery complete. Resuming from X=%d Z=%d", this.recoveryTarget.getX(), this.recoveryTarget.getZ()
                        );
                        this.recovering = false;
                        this.recoveryTarget = null;
                        this.offTrackCounter = 0;
                        this.recoveryCompletedTime = System.currentTimeMillis();
                        this.lastKnownGoodPosition = this.mc.player.blockPosition();
                        this.nextWaypointTarget = null;
                    } else if (this.needsNewGoal()) {
                        this.setBaritoneGoal(this.recoveryTarget);
                    }
                }
            } else if (this.goingToStart) {
                double distToSaved = Math.sqrt(
                    this.mc
                        .player
                        .blockPosition()
                        .distToLowCornerSqr(this.pd.currPos.getX(), this.mc.player.getY(), this.pd.currPos.getZ())
                );
                if (distToSaved < reachDist) {
                    this.goingToStart = false;
                    this.lastKnownGoodPosition = this.pd.currPos;
                    this.nextWaypointTarget = null;
                    this.debugInfo(
                        "ZigZag: Reached saved position. Resuming at onMainLeg=%b, goingForward=%b, legsCompleted=%d",
                        this.pd.onMainLeg,
                        this.pd.goingForward,
                        this.pd.legsCompleted
                    );
                } else if (this.needsNewGoal()) {
                    this.setBaritoneGoal(this.pd.currPos);
                }
            } else if (this.nextWaypointTarget == null) {
                this.calculateNextWaypoint(legLength, rowGap);
                if (this.nextWaypointTarget != null) {
                    this.debugInfo(
                        "ZigZag: New waypoint at X=%d Z=%d (onMainLeg=%b, yaw=%.1f)",
                        this.nextWaypointTarget.getX(),
                        this.nextWaypointTarget.getZ(),
                        this.pd.onMainLeg,
                        this.pd.yawDirection
                    );
                    this.setBaritoneGoal(this.nextWaypointTarget);
                }
            } else {
                boolean elytraRecovering = this.elytraRecast != null && this.elytraRecast.isActive() && this.elytraRecast.isRecovering();
                if (!elytraRecovering) {
                    long timeSinceRecovery = System.currentTimeMillis() - this.recoveryCompletedTime;
                    if (this.recoveryCompletedTime > 0L && timeSinceRecovery < 5000L) {
                        if (this.needsNewGoal()) {
                            this.setBaritoneGoal(this.nextWaypointTarget);
                        }
                    } else {
                        double offTrackDist = this.getZigZagOffTrackDistance();
                        if (offTrackDist > 200.0) {
                            this.offTrackCounter++;
                            if (this.offTrackCounter >= 10) {
                                this.debugInfo("ZigZag: Off-track by %.0f blocks. Saving state and initiating recovery...", offTrackDist);
                                BlockPos currentPos = this.mc.player.blockPosition();
                                double distToLastGood = this.lastKnownGoodPosition != null
                                    ? Math.sqrt(currentPos.distSqr(this.lastKnownGoodPosition))
                                    : Double.MAX_VALUE;
                                if (this.lastKnownGoodPosition != null && distToLastGood > reachDist * 2) {
                                    this.pd.currPos = this.lastKnownGoodPosition;
                                    super.saveToJsonExact(this.pd);
                                    this.recoveryTarget = this.lastKnownGoodPosition;
                                    ChatUtils.info(
                                        "ZigZag: Saved recovery point at X=%d Z=%d. Navigating back.",
                                        this.lastKnownGoodPosition.getX(),
                                        this.lastKnownGoodPosition.getZ()
                                    );
                                } else {
                                    this.recoveryTarget = this.pd.currPos;
                                    super.saveToJsonExact(this.pd);
                                    ChatUtils.info(
                                        "ZigZag: Recovering to last saved position X=%d Z=%d", this.pd.currPos.getX(), this.pd.currPos.getZ()
                                    );
                                }

                                this.recovering = true;
                                this.nextWaypointTarget = null;
                                this.offTrackCounter = 0;
                                this.setBaritoneGoal(this.recoveryTarget);
                                return;
                            }
                        } else {
                            this.offTrackCounter = 0;
                            this.lastKnownGoodPosition = this.mc.player.blockPosition();
                        }

                        double distToWaypoint = Math.sqrt(
                            this.mc
                                .player
                                .blockPosition()
                                .distToLowCornerSqr(this.nextWaypointTarget.getX(), this.mc.player.getY(), this.nextWaypointTarget.getZ())
                        );
                        if (distToWaypoint < reachDist) {
                            this.debugInfo(
                                "ZigZag: Reached waypoint at X=%d Z=%d", this.nextWaypointTarget.getX(), this.nextWaypointTarget.getZ()
                            );
                            if (this.pd.onMainLeg) {
                                this.pd.yawDirection = this.pd.sideYaw;
                                this.pd.onMainLeg = false;
                                this.pd.legStartPos = this.nextWaypointTarget;
                                this.pd.legsCompleted++;
                                this.debugInfo("ZigZag: Finished main leg #%d, turning to side yaw=%.1f", this.pd.legsCompleted, this.pd.sideYaw);
                            } else {
                                this.pd.goingForward = !this.pd.goingForward;
                                this.pd.yawDirection = this.pd.goingForward ? this.pd.mainYaw : this.normalizeYaw(this.pd.mainYaw + 180.0F);
                                this.pd.onMainLeg = true;
                                this.pd.legStartPos = this.nextWaypointTarget;
                                this.debugInfo("ZigZag: Finished side leg, turning to yaw=%.1f (goingForward=%b)", this.pd.yawDirection, this.pd.goingForward);
                            }

                            this.pd.currPos = this.nextWaypointTarget;
                            this.lastKnownGoodPosition = this.nextWaypointTarget;
                            super.saveToJsonExact(this.pd);
                            this.nextWaypointTarget = null;
                            this.calculateNextWaypoint(legLength, rowGap);
                            if (this.nextWaypointTarget != null) {
                                this.debugInfo(
                                    "ZigZag: Next waypoint at X=%d Z=%d", this.nextWaypointTarget.getX(), this.nextWaypointTarget.getZ()
                                );
                                this.setBaritoneGoal(this.nextWaypointTarget);
                            }
                        } else if (this.needsNewGoal()) {
                            this.debugInfo("ZigZag: Baritone needs new goal, resetting");
                            this.setBaritoneGoal(this.nextWaypointTarget);
                        }
                    }
                }
            }
        }
    }

    private void calculateNextWaypoint(int legLength, int rowGap) {
        int distance = this.pd.onMainLeg ? legLength : rowGap;
        BlockPos startRef = this.pd.legStartPos != null ? this.pd.legStartPos : this.mc.player.blockPosition();
        int targetX = startRef.getX();
        int targetZ = startRef.getZ();
        float yaw = this.normalizeYaw(this.pd.yawDirection);
        if (yaw >= 315.0F || yaw < 45.0F) {
            targetZ += distance;
        } else if (yaw >= 45.0F && yaw < 135.0F) {
            targetX -= distance;
        } else if (yaw >= 135.0F && yaw < 225.0F) {
            targetZ -= distance;
        } else {
            targetX += distance;
        }

        this.nextWaypointTarget = new BlockPos(targetX, this.mc.player.getBlockY(), targetZ);
        this.updateGoalWaypoint(this.nextWaypointTarget);
    }

    private double getDistanceTraveled(BlockPos start, BlockPos current) {
        double dx = current.getX() - start.getX();
        double dz = current.getZ() - start.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private float normalizeToCardinal(float yaw) {
        yaw = this.normalizeYaw(yaw);
        if (yaw >= 315.0F || yaw < 45.0F) {
            return 0.0F;
        } else if (yaw >= 45.0F && yaw < 135.0F) {
            return 90.0F;
        } else {
            return yaw >= 135.0F && yaw < 225.0F ? 180.0F : 270.0F;
        }
    }

    private float normalizeYaw(float yaw) {
        yaw %= 360.0F;
        if (yaw < 0.0F) {
            yaw += 360.0F;
        }

        return yaw;
    }

    private double getZigZagOffTrackDistance() {
        if (this.pd != null && this.pd.legStartPos != null && this.nextWaypointTarget != null) {
            BlockPos currentPos = this.mc.player.blockPosition();
            float yaw = this.normalizeYaw(this.pd.yawDirection);
            boolean movingNS = yaw >= 315.0F || yaw < 45.0F || yaw >= 135.0F && yaw < 225.0F;
            return movingNS
                ? Math.abs(currentPos.getX() - this.pd.legStartPos.getX())
                : Math.abs(currentPos.getZ() - this.pd.legStartPos.getZ());
        } else {
            return 0.0;
        }
    }

    public static class PathingDataZigZag extends AreaLoaderMode.PathingData {
        public float mainYaw;
        public float sideYaw;
        public boolean goingForward;
        public boolean onMainLeg;
        public int legsCompleted;
        public BlockPos legStartPos;

        public PathingDataZigZag(
            BlockPos initialPos,
            BlockPos currPos,
            float yawDirection,
            float mainYaw,
            float sideYaw,
            boolean goingForward,
            boolean onMainLeg,
            int legsCompleted
        ) {
            this.initialPos = initialPos;
            this.currPos = currPos;
            this.yawDirection = yawDirection;
            this.mainYaw = mainYaw;
            this.sideYaw = sideYaw;
            this.goingForward = goingForward;
            this.onMainLeg = onMainLeg;
            this.legsCompleted = legsCompleted;
            this.legStartPos = currPos;
        }
    }
}
