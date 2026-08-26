package bep.hax.modules.arealoader.modes;

import bep.hax.modules.arealoader.AreaLoader;
import bep.hax.modules.arealoader.AreaLoaderMode;
import bep.hax.modules.arealoader.AreaLoaderModes;
import bep.hax.util.Utils;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class SpiralCircle extends AreaLoaderMode {
    private static final double TWO_PI = Math.PI * 2;
    private static final double QUARTER_TURN = Math.PI / 2;
    private static final int MAX_WAYPOINT_SKIP = 16;
    private SpiralCircle.PathingDataSpiralCircle pd;
    private boolean goingToStart = true;
    private long startTime;
    private BlockPos legStartPoint = null;
    private BlockPos nextWaypoint = null;
    private boolean recovering = false;
    private BlockPos recoveryTarget = null;
    private BlockPos lastTickPos = null;
    private String lastTickDimension = null;
    private static final int TELEPORT_THRESHOLD = 100;
    private static final int TELEPORT_STABLE_TICKS = 100;
    private static final double TELEPORT_AUTO_RESUME_MAX = 5000.0;
    private boolean teleportPaused = false;
    private int teleportStableTicks = 0;
    private double teleportJumpDistance = 0.0;
    private float supposedYaw = Float.NaN;
    private double lastOffTrackDistance = 0.0;
    private int overshootGrowTicks = 0;
    private int headingDriftTicks = 0;
    private static final int OVERSHOOT_GROW_TRIP_TICKS = 20;
    private static final int HEADING_DRIFT_TRIP_TICKS = 60;
    private static final float HEADING_DRIFT_DEGREES = 45.0F;
    private long lastWaypointSaveTime = 0L;
    private static final long WAYPOINT_SAVE_INTERVAL_MS = 3000L;

    public SpiralCircle() {
        super(AreaLoaderModes.SpiralCircle);
    }

    @Override
    public void onActivate() {
        this.startTime = System.nanoTime();
        this.goingToStart = true;
        this.legStartPoint = null;
        this.nextWaypoint = null;
        this.recovering = false;
        this.recoveryTarget = null;
        this.lastTickPos = null;
        this.lastTickDimension = null;
        this.teleportPaused = false;
        this.teleportStableTicks = 0;
        this.teleportJumpDistance = 0.0;
        this.lastWaypointSaveTime = 0L;
        this.resetDriftDetectors();
        File file = this.getJsonFile(this.saveFileName());
        if (file == null) {
            this.debugInfo("Error: Cannot create save file path. Check save-name setting.");
            this.pd = new SpiralCircle.PathingDataSpiralCircle(this.mc.player.blockPosition(), this.mc.player.blockPosition(), 0.0, 0.0);
            this.goingToStart = false;
        } else if (!file.exists()) {
            this.pd = new SpiralCircle.PathingDataSpiralCircle(this.mc.player.blockPosition(), this.mc.player.blockPosition(), 0.0, 0.0);
            this.goingToStart = false;
            this.debugInfo(
                "Circle spiral started from origin: " + this.pd.spiralOrigin.toShortString() + " (no save file found at: " + file.getAbsolutePath() + ")"
            );
        } else {
            try {
                this.debugInfo("Loading save file: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
                FileReader reader = new FileReader(file);
                this.pd = GSON.fromJson(reader, SpiralCircle.PathingDataSpiralCircle.class);
                reader.close();
                boolean corrupted = false;
                String corruptionReason = "";
                if (this.pd == null) {
                    corrupted = true;
                    corruptionReason = "pd is null";
                } else if (this.pd.spiralOrigin == null && this.pd.initialPos == null) {
                    corrupted = true;
                    corruptionReason = "both spiralOrigin and initialPos are null";
                } else if (this.pd.currPos == null) {
                    corrupted = true;
                    corruptionReason = "currPos is null";
                } else if (!Double.isNaN(this.pd.theta) && !Double.isNaN(this.pd.legStartTheta) && !(this.pd.theta < 0.0) && !(this.pd.legStartTheta < 0.0)) {
                    BlockPos origin = this.pd.spiralOrigin != null ? this.pd.spiralOrigin : this.pd.initialPos;
                    if (this.pd.legStartTheta == 0.0 && this.pd.theta == 0.0) {
                        double distFromOrigin = Math.sqrt(this.pd.currPos.distSqr(origin));
                        if (distFromOrigin > 1000.0) {
                            corrupted = true;
                            corruptionReason = String.format("theta is 0 but currPos is %.0f blocks from origin - save may have been reset", distFromOrigin);
                        }
                    }
                } else {
                    corrupted = true;
                    corruptionReason = "theta values are invalid (theta=" + this.pd.theta + ", legStartTheta=" + this.pd.legStartTheta + ")";
                }

                if (corrupted) {
                    ChatUtils.error("SAVE FILE APPEARS CORRUPTED: " + corruptionReason);
                    ChatUtils.error("NOT starting fresh to protect your progress. Please fix the save file manually.");
                    ChatUtils.error("Save file location: " + file.getAbsolutePath());
                    ChatUtils.error("Disabling module to prevent data loss.");
                    this.pd = null;
                    this.disable();
                    return;
                }

                if (this.pd.spiralOrigin == null) {
                    this.pd.spiralOrigin = this.pd.initialPos;
                    this.debugInfo("Loaded legacy save - set origin to: " + this.pd.spiralOrigin.toShortString());
                }

                if (this.pd.legStartTheta > this.pd.theta) {
                    this.pd.legStartTheta = this.pd.theta;
                }

                this.debugInfo(
                    "Loaded saved path successfully. Origin: " + this.pd.spiralOrigin.toShortString() + ", Current: " + this.pd.currPos.toShortString()
                );
                this.debugInfo(
                    "Circle spiral state: theta=%.3f rad (%.1f loops), radius=%.0f",
                    this.pd.theta,
                    this.pd.theta / (Math.PI * 2),
                    radiusPerRadian(this.blockGap()) * this.pd.theta
                );
            } catch (Exception e) {
                ChatUtils.error("Failed to load saved path: " + e.getMessage());
                ChatUtils.error("NOT starting fresh to protect your progress. Please check the save file.");
                ChatUtils.error("Save file location: " + file.getAbsolutePath());
                e.printStackTrace();
                this.pd = null;
                this.disable();
                return;
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
        this.legStartPoint = null;
        this.nextWaypoint = null;
        this.recovering = false;
        this.recoveryTarget = null;
        this.lastTickPos = null;
        this.lastTickDimension = null;
        this.teleportPaused = false;
        this.teleportStableTicks = 0;
        this.teleportJumpDistance = 0.0;
        this.lastWaypointSaveTime = 0L;
        this.resetDriftDetectors();
        this.debugInfo("Circle spiral state reset. Next activation will start fresh.");
    }

    @Override
    public void onTick() {
        super.onTick();
        if (this.mc.player != null && this.mc.level != null) {
            if (this.pd != null) {
                BlockPos currentPos = this.mc.player.blockPosition();
                String currentTickDim = this.mc.level.dimension().identifier().toString();
                if (!currentTickDim.equals(this.lastTickDimension)) {
                    this.lastTickDimension = currentTickDim;
                    this.lastTickPos = null;
                }

                double tickDistance = this.lastTickPos != null ? Math.sqrt(this.lastTickPos.distSqr(currentPos)) : 0.0;
                if (this.lastTickPos != null && !this.goingToStart && !this.recovering && !this.teleportPaused && tickDistance > 100.0) {
                    this.debugInfo("TELEPORT DETECTED! Moved %.0f blocks in one tick. Pausing spiral to protect state.", tickDistance);
                    this.debugInfo(
                        "Last safe position: X=%d Z=%d. Current: X=%d Z=%d",
                        this.lastTickPos.getX(),
                        this.lastTickPos.getZ(),
                        currentPos.getX(),
                        currentPos.getZ()
                    );
                    this.pd.currPos = this.lastTickPos;
                    super.saveToJsonExact(this.pd);
                    this.teleportPaused = true;
                    this.teleportJumpDistance = tickDistance;
                    this.teleportStableTicks = 0;
                    Utils.setPressed(this.mc.options.keyUp, false);
                    this.mc.player.setDeltaMovement(0.0, 0.0, 0.0);
                    ChatUtils.info("Circle spiral PAUSED due to teleportation. State saved at last safe position.");
                    if (tickDistance < 5000.0) {
                        ChatUtils.info("Waiting for the position to stabilize, then auto-resuming from the saved position.");
                    } else {
                        ChatUtils.info("Jump too large to auto-resume. Disable and re-enable the module to resume from saved position.");
                    }
                } else {
                    this.lastTickPos = currentPos;
                    if (this.teleportPaused) {
                        Utils.setPressed(this.mc.options.keyUp, false);
                        if (tickDistance < 2.0) {
                            this.teleportStableTicks++;
                        } else {
                            this.teleportStableTicks = 0;
                        }

                        if (this.teleportJumpDistance < 5000.0 && this.teleportStableTicks >= 100) {
                            this.teleportPaused = false;
                            this.goingToStart = true;
                            this.legStartPoint = null;
                            this.nextWaypoint = null;
                            this.resetDriftDetectors();
                            ChatUtils.info(
                                "Position stable again. Auto-resuming circle spiral: navigating back to saved position X=%d Z=%d.",
                                this.pd.currPos.getX(),
                                this.pd.currPos.getZ()
                            );
                        }
                    } else {
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
            }
        }
    }

    private void onTickOverworld() {
        if (!this.isInNether) {
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
                    this.legStartPoint = null;
                    this.nextWaypoint = null;
                    this.mc.player.setDeltaMovement(0.0, 0.0, 0.0);
                    this.resetDriftDetectors();
                } else {
                    this.mc.player.setYRot((float)Rotations.getYaw(this.pd.currPos.getCenter()));
                    Utils.setPressed(this.mc.options.keyUp, true);
                }
            } else if (this.recovering) {
                if (this.recoveryTarget == null) {
                    this.recovering = false;
                } else if (Math.sqrt(
                        this.mc
                            .player
                            .blockPosition()
                            .distToLowCornerSqr(this.recoveryTarget.getX(), this.mc.player.getY(), this.recoveryTarget.getZ())
                    )
                    < 20.0) {
                    this.debugInfo(
                        "Recovery complete. Resuming circle spiral at X=%d Z=%d", this.recoveryTarget.getX(), this.recoveryTarget.getZ()
                    );
                    this.recovering = false;
                    this.recoveryTarget = null;
                    this.legStartPoint = null;
                    this.nextWaypoint = null;
                    this.mc.player.setDeltaMovement(0.0, 0.0, 0.0);
                    this.resetDriftDetectors();
                } else {
                    this.updateGoalWaypoint(this.recoveryTarget);
                    this.mc.player.setYRot((float)Rotations.getYaw(this.recoveryTarget.getCenter()));
                    Utils.setPressed(this.mc.options.keyUp, true);
                }
            } else {
                int blockGap = this.blockGap();
                double arcStep = this.searchArea.circleSegmentLength.get().intValue();
                int reachSetting = this.searchArea.spiralCornerReachDistance.get();
                this.ensureWaypoint(blockGap, arcStep);
                boolean advanced = false;

                for (int i = 0; i < 16 && this.hasReachedWaypoint(reachSetting); i++) {
                    this.advanceWaypoint(blockGap, arcStep);
                    advanced = true;
                }

                if (advanced) {
                    this.pd.currPos = this.legStartPoint;
                    this.debugInfo(
                        "Circle spiral: waypoint reached, now heading to X=%d Z=%d (theta=%.3f, %.2f loops, radius=%.0f)",
                        this.nextWaypoint.getX(),
                        this.nextWaypoint.getZ(),
                        this.pd.theta,
                        this.pd.theta / (Math.PI * 2),
                        radiusPerRadian(blockGap) * this.pd.theta
                    );
                    long now = System.currentTimeMillis();
                    if (now - this.lastWaypointSaveTime >= 3000L) {
                        this.lastWaypointSaveTime = now;
                        super.saveToJsonExact(this.pd);
                    }
                }

                this.updateGoalWaypoint(this.nextWaypoint);
                BlockPos expectedPos = this.calculateExpectedPosition();
                double offTrackDistance = this.getOffTrackDistance(expectedPos);
                if (offTrackDistance > this.searchArea.spiralMaxOffTrackDistance.get().intValue()) {
                    this.debugInfo(
                        "Off-track by %.0f blocks! Expected near X=%d Z=%d. Saving state and initiating recovery...",
                        offTrackDistance,
                        expectedPos.getX(),
                        expectedPos.getZ()
                    );
                    this.startRecovery(expectedPos);
                } else {
                    Utils.setPressed(this.mc.options.keyUp, true);
                    float yawToTarget = (float)Rotations.getYaw(this.nextWaypoint.getCenter());
                    this.mc.player.setYRot(yawToTarget);
                    this.pd.yawDirection = this.normalizeYaw(yawToTarget);
                    this.supposedYaw = this.pd.yawDirection;
                    if (radiusPerRadian(blockGap) * this.pd.legStartTheta >= 100.0) {
                        this.checkTurnDrift(offTrackDistance, expectedPos);
                    } else {
                        this.resetDriftDetectors();
                    }
                }
            }
        }
    }

    private void onTickNether() {
        if (this.isInNether) {
            if (this.searchArea.netherPathMode.get() == AreaLoader.NetherPathMode.BARITONE_ELYTRA) {
                int blockGap = this.blockGap();
                double arcStep = Math.max(this.searchArea.circleSegmentLength.get(), this.searchArea.netherWaypointDistance.get());
                int reachDist = this.searchArea.netherWaypointReachDistance.get();
                if (this.goingToStart) {
                    double distToSaved = Math.sqrt(
                        this.mc
                            .player
                            .blockPosition()
                            .distToLowCornerSqr(this.pd.currPos.getX(), this.mc.player.getY(), this.pd.currPos.getZ())
                    );
                    if (distToSaved < reachDist) {
                        this.goingToStart = false;
                        this.legStartPoint = null;
                        this.nextWaypoint = null;
                        this.debugInfo(
                            "Reached saved position. Resuming circle spiral at theta=%.3f (%.2f loops, radius=%.0f)",
                            this.pd.theta,
                            this.pd.theta / (Math.PI * 2),
                            radiusPerRadian(blockGap) * this.pd.theta
                        );
                    } else if (this.needsNewGoal()) {
                        this.setBaritoneGoal(this.pd.currPos);
                    }
                } else if (this.nextWaypoint == null) {
                    this.ensureWaypoint(blockGap, arcStep);
                    this.updateGoalWaypoint(this.nextWaypoint);
                    this.setBaritoneGoal(this.nextWaypoint);
                } else {
                    boolean advanced = false;

                    for (int i = 0; i < 16 && this.hasReachedWaypoint(reachDist); i++) {
                        this.advanceWaypoint(blockGap, arcStep);
                        advanced = true;
                    }

                    if (advanced) {
                        this.pd.currPos = this.legStartPoint;
                        super.saveToJsonExact(this.pd);
                        this.updateGoalWaypoint(this.nextWaypoint);
                        this.setBaritoneGoal(this.nextWaypoint);
                        this.debugInfo(
                            "Circle spiral: reached waypoint, next target X=%d Z=%d (theta=%.3f, radius=%.0f) (saved)",
                            this.nextWaypoint.getX(),
                            this.nextWaypoint.getZ(),
                            this.pd.theta,
                            radiusPerRadian(blockGap) * this.pd.theta
                        );
                    } else if (this.needsNewGoal()) {
                        this.debugInfo("Circle spiral: Baritone needs new goal, resetting to waypoint target");
                        this.setBaritoneGoal(this.nextWaypoint);
                    }
                }
            }
        }
    }

    private void ensureWaypoint(int blockGap, double arcStep) {
        if (this.pd.theta <= this.pd.legStartTheta + 1.0E-9) {
            this.pd.theta = this.nextTheta(this.pd.legStartTheta, blockGap, arcStep);
            this.legStartPoint = null;
            this.nextWaypoint = null;
        }

        if (this.legStartPoint == null) {
            this.legStartPoint = this.pointAt(this.pd.legStartTheta, blockGap);
        }

        if (this.nextWaypoint == null) {
            this.nextWaypoint = this.pointAt(this.pd.theta, blockGap);
        }
    }

    private void advanceWaypoint(int blockGap, double arcStep) {
        this.pd.legStartTheta = this.pd.theta;
        this.pd.theta = this.nextTheta(this.pd.theta, blockGap, arcStep);
        this.legStartPoint = this.pointAt(this.pd.legStartTheta, blockGap);
        this.nextWaypoint = this.pointAt(this.pd.theta, blockGap);
    }

    private boolean hasReachedWaypoint(int reachSetting) {
        double chord = distanceXZ(
            this.legStartPoint.getX(), this.legStartPoint.getZ(), this.nextWaypoint.getX(), this.nextWaypoint.getZ()
        );
        double distToWaypoint = Math.sqrt(
            this.mc
                .player
                .blockPosition()
                .distToLowCornerSqr(this.nextWaypoint.getX(), this.mc.player.getY(), this.nextWaypoint.getZ())
        );
        if (distToWaypoint > Math.max(this.searchArea.spiralMaxOffTrackDistance.get().intValue(), chord * 1.5)) {
            return false;
        }

        double reach = Math.min(reachSetting, Math.max(2.0, chord * 0.5));
        return distToWaypoint < reach ? true : this.projectionAlongLeg() >= 1.0;
    }

    private double projectionAlongLeg() {
        double dx = this.nextWaypoint.getX() - this.legStartPoint.getX();
        double dz = this.nextWaypoint.getZ() - this.legStartPoint.getZ();
        double lenSq = dx * dx + dz * dz;
        return lenSq < 1.0E-6
            ? 1.0
            : (
                    (this.mc.player.getX() - this.legStartPoint.getX()) * dx
                        + (this.mc.player.getZ() - this.legStartPoint.getZ()) * dz
                )
                / lenSq;
    }

    private BlockPos calculateExpectedPosition() {
        double t = Math.max(0.0, Math.min(1.0, this.projectionAlongLeg()));
        double dx = this.nextWaypoint.getX() - this.legStartPoint.getX();
        double dz = this.nextWaypoint.getZ() - this.legStartPoint.getZ();
        return new BlockPos(
            (int)Math.round(this.legStartPoint.getX() + t * dx),
            this.mc.player.getBlockY(),
            (int)Math.round(this.legStartPoint.getZ() + t * dz)
        );
    }

    private double getOffTrackDistance(BlockPos expectedPos) {
        return distanceXZ(expectedPos.getX(), expectedPos.getZ(), this.mc.player.getX(), this.mc.player.getZ());
    }

    private void resetDriftDetectors() {
        this.supposedYaw = Float.NaN;
        this.lastOffTrackDistance = 0.0;
        this.overshootGrowTicks = 0;
        this.headingDriftTicks = 0;
    }

    private void startRecovery(BlockPos target) {
        this.pd.currPos = target;
        super.saveToJsonExact(this.pd);
        ChatUtils.info("Saved recovery point at X=%d Z=%d. Navigating back to resume circle spiral.", target.getX(), target.getZ());
        this.recovering = true;
        this.recoveryTarget = target;
        this.legStartPoint = null;
        this.nextWaypoint = null;
        this.mc.player.setDeltaMovement(0.0, 0.0, 0.0);
        this.resetDriftDetectors();
    }

    private void checkTurnDrift(double offTrackDistance, BlockPos expectedPos) {
        double overshootTrip = Math.max(60, this.searchArea.spiralCornerReachDistance.get() * 3);
        if (offTrackDistance > overshootTrip && offTrackDistance > this.lastOffTrackDistance + 0.25) {
            this.overshootGrowTicks++;
        } else {
            this.overshootGrowTicks = 0;
        }

        this.lastOffTrackDistance = offTrackDistance;
        Vec3 vel = this.mc.player.getDeltaMovement();
        double horizontalSpeedSq = vel.x * vel.x + vel.z * vel.z;
        if (!Float.isNaN(this.supposedYaw) && horizontalSpeedSq > 0.04) {
            float motionYaw = this.normalizeYaw((float)Math.toDegrees(Math.atan2(-vel.x, vel.z)));
            if (this.yawDistance(motionYaw, this.supposedYaw) > 45.0F) {
                this.headingDriftTicks++;
            } else {
                this.headingDriftTicks = 0;
            }
        } else {
            this.headingDriftTicks = 0;
        }

        if (this.overshootGrowTicks >= 20) {
            this.debugInfo(
                "Overdrift detected: off-track %.0f blocks and growing (supposed yaw %.0f). Recovering to the arc.", offTrackDistance, this.supposedYaw
            );
            this.startRecovery(expectedPos);
        } else if (this.headingDriftTicks >= 60) {
            this.debugInfo("Heading drift detected: motion is >%.0f° off the supposed yaw %.0f. Recovering to the circle spiral path.", 45.0F, this.supposedYaw);
            this.startRecovery(expectedPos);
        }
    }

    private float yawDistance(float a, float b) {
        float d = Math.abs(this.normalizeYaw(a) - this.normalizeYaw(b)) % 360.0F;
        return d > 180.0F ? 360.0F - d : d;
    }

    private float normalizeYaw(float yaw) {
        yaw %= 360.0F;
        if (yaw < 0.0F) {
            yaw += 360.0F;
        }

        return yaw;
    }

    private int blockGap() {
        return 16 * this.searchArea.rowGap.get();
    }

    private static double distanceXZ(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double radiusPerRadian(int blockGap) {
        return blockGap / (Math.PI * 2);
    }

    private static double arcLengthAt(double theta, double b) {
        double s = Math.sqrt(1.0 + theta * theta);
        return 0.5 * b * (theta * s + Math.log(theta + s));
    }

    private static double thetaAfterArc(double theta, double arc, double b) {
        if (!(arc <= 0.0) && !(b <= 0.0)) {
            double target = arcLengthAt(theta, b) + arc;
            double t = theta + arc / (b * Math.sqrt(1.0 + theta * theta));

            for (int i = 0; i < 40; i++) {
                double slope = b * Math.sqrt(1.0 + t * t);
                if (slope < 1.0E-9) {
                    break;
                }

                double step = (arcLengthAt(t, b) - target) / slope;
                t -= step;
                if (Math.abs(step) < 1.0E-9) {
                    break;
                }
            }

            return Math.max(theta, t);
        } else {
            return theta;
        }
    }

    private double nextTheta(double theta, int blockGap, double arcStep) {
        double b = radiusPerRadian(blockGap);
        double byArc = thetaAfterArc(theta, arcStep, b) - theta;
        double byTurn = Math.toRadians(this.searchArea.circleTurnStep.get().intValue()) / (1.0 + 1.0 / (1.0 + theta * theta));
        return theta + Math.max(1.0E-4, Math.min(byArc, byTurn));
    }

    private BlockPos pointAt(double theta, int blockGap) {
        return spiralPoint(
            this.pd.spiralOrigin.getX(),
            this.pd.spiralOrigin.getZ(),
            theta,
            blockGap,
            this.mc.player != null ? this.mc.player.getBlockY() : 64
        );
    }

    private static BlockPos spiralPoint(int originX, int originZ, double theta, int blockGap, int y) {
        double r = radiusPerRadian(blockGap) * theta;
        return new BlockPos((int)Math.round(originX + r * Math.cos(theta)), y, (int)Math.round(originZ + r * Math.sin(theta)));
    }

    private static double thetaForPosition(int originX, int originZ, int x, int z, int blockGap) {
        double b = radiusPerRadian(blockGap);
        double dx = x - originX;
        double dz = z - originZ;
        double r = Math.sqrt(dx * dx + dz * dz);
        if (!(r < 1.0E-6) && !(b <= 0.0)) {
            double raw = Math.atan2(dz, dx);
            double theta = raw + Math.round((r / b - raw) / (Math.PI * 2)) * (Math.PI * 2);

            while (theta < 0.0) {
                theta += Math.PI * 2;
            }

            return theta;
        } else {
            return 0.0;
        }
    }

    private static double distanceFromPath(int originX, int originZ, int x, int z, int blockGap, double theta) {
        double dx = x - originX;
        double dz = z - originZ;
        double r = Math.sqrt(dx * dx + dz * dz);
        return Math.abs(r - radiusPerRadian(blockGap) * theta);
    }

    private String getDirectionName(double theta) {
        double tangent = theta + Math.atan(theta);
        float yaw = this.normalizeYaw((float)Math.toDegrees(Math.atan2(-Math.cos(tangent), Math.sin(tangent))));
        if (yaw >= 315.0F || yaw < 45.0F) {
            return "South (+Z)";
        } else if (yaw < 135.0F) {
            return "West (-X)";
        } else {
            return yaw < 225.0F ? "North (-Z)" : "East (+X)";
        }
    }

    public boolean validateManualCoordinates(int originX, int originZ, int targetX, int targetZ, int blockGap, boolean apply) {
        if (blockGap <= 0) {
            ChatUtils.error("Path gap must be greater than zero.");
            return false;
        }

        double theta = thetaForPosition(originX, originZ, targetX, targetZ, blockGap);
        double offPath = distanceFromPath(originX, originZ, targetX, targetZ, blockGap, theta);
        BlockPos projected = spiralPoint(originX, originZ, theta, blockGap, 64);
        this.debugInfo("Circle spiral position:");
        this.debugInfo("  Origin: (%d, %d)", originX, originZ);
        this.debugInfo("  Loop %.2f (theta=%.3f rad), expected radius %.0f blocks", theta / (Math.PI * 2), theta, radiusPerRadian(blockGap) * theta);
        this.debugInfo("  Heading there: %s", this.getDirectionName(theta));
        this.debugInfo("  Distance from path: %.0f blocks", offPath);
        if (offPath > blockGap / 2.0) {
            this.debugInfo("  Projected position onto path: (%d, %d)", projected.getX(), projected.getZ());
        }

        if (apply) {
            if (offPath > blockGap * 2.0) {
                ChatUtils.error("Position is too far from the circle spiral path (%.0f blocks). Max tolerance: %d blocks.", offPath, blockGap * 2);
                ChatUtils.error("Make sure you entered the correct origin coordinates where the spiral started.");
                return false;
            }

            int resumeX = offPath < 50.0 ? targetX : projected.getX();
            int resumeZ = offPath < 50.0 ? targetZ : projected.getZ();
            SpiralCircle.PathingDataSpiralCircle newPd = new SpiralCircle.PathingDataSpiralCircle(
                new BlockPos(originX, 64, originZ), new BlockPos(resumeX, 64, resumeZ), theta, theta
            );
            if (!this.writeState(newPd)) {
                return false;
            }

            this.debugInfo("SAVED circle spiral recovery state:");
            this.debugInfo("  Origin: (%d, %d)", originX, originZ);
            this.debugInfo("  Resume from: (%d, %d)", resumeX, resumeZ);
            this.debugInfo("  Resuming at loop %.2f heading %s", theta / (Math.PI * 2), this.getDirectionName(theta));
            this.debugInfo("Enable the module to resume the circle spiral.");
            return true;
        } else {
            return offPath < blockGap / 2.0;
        }
    }

    public int[] snapToNearestCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        if (blockGap <= 0) {
            ChatUtils.error("Path gap must be greater than zero.");
            return null;
        } else {
            double theta = thetaForPosition(originX, originZ, targetX, targetZ, blockGap);
            BlockPos projected = spiralPoint(originX, originZ, theta, blockGap, 64);
            double offPath = distanceFromPath(originX, originZ, targetX, targetZ, blockGap, theta);
            this.debugInfo("Snapped to loop %.2f at position (%d, %d)", theta / (Math.PI * 2), projected.getX(), projected.getZ());
            this.debugInfo("  Was %.0f blocks off the circle spiral path", offPath);
            this.debugInfo("  Heading there: %s", this.getDirectionName(theta));
            return new int[]{projected.getX(), projected.getZ()};
        }
    }

    public int[] snapToNextCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        if (blockGap <= 0) {
            ChatUtils.error("Path gap must be greater than zero.");
            return null;
        } else {
            double theta = nextQuarterTurn(thetaForPosition(originX, originZ, targetX, targetZ, blockGap));
            BlockPos corner = spiralPoint(originX, originZ, theta, blockGap, 64);
            this.debugInfo("=== SNAP TO NEXT QUARTER TURN ===");
            this.debugInfo(
                "Next quarter turn at loop %.2f: (%d, %d), heading %s",
                theta / (Math.PI * 2),
                corner.getX(),
                corner.getZ(),
                this.getDirectionName(theta)
            );
            return new int[]{corner.getX(), corner.getZ()};
        }
    }

    public boolean applyFromNextCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        if (blockGap <= 0) {
            ChatUtils.error("Path gap must be greater than zero.");
            return false;
        }

        double theta = nextQuarterTurn(thetaForPosition(originX, originZ, targetX, targetZ, blockGap));
        BlockPos corner = spiralPoint(originX, originZ, theta, blockGap, 64);
        SpiralCircle.PathingDataSpiralCircle newPd = new SpiralCircle.PathingDataSpiralCircle(new BlockPos(originX, 64, originZ), corner, theta, theta);
        if (!this.writeState(newPd)) {
            return false;
        }

        this.debugInfo("=== SAVED ===");
        this.debugInfo("Go to (%d, %d) and enable the module.", corner.getX(), corner.getZ());
        this.debugInfo("It will resume at loop %.2f heading %s", theta / (Math.PI * 2), this.getDirectionName(theta));
        return true;
    }

    private static double nextQuarterTurn(double theta) {
        return Math.floor(theta / (Math.PI / 2) + 1.0E-9) * (Math.PI / 2) + (Math.PI / 2);
    }

    private boolean writeState(SpiralCircle.PathingDataSpiralCircle newPd) {
        File file = this.getJsonFile(this.saveFileName());
        if (file == null) {
            ChatUtils.error("Failed to get save file path. Check that you're in a world.");
            return false;
        }

        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            Writer writer = new FileWriter(file);
            GSON.toJson(newPd, writer);
            writer.flush();
            writer.close();
            return true;
        } catch (Exception e) {
            ChatUtils.error("Failed to save: " + e.getMessage());
            return false;
        }
    }

    public static class PathingDataSpiralCircle extends AreaLoaderMode.PathingData {
        public BlockPos spiralOrigin;
        public double theta = 0.0;
        public double legStartTheta = 0.0;

        public PathingDataSpiralCircle(BlockPos spiralOrigin, BlockPos currPos, double legStartTheta, double theta) {
            this.spiralOrigin = spiralOrigin;
            this.initialPos = spiralOrigin;
            this.currPos = currPos;
            this.yawDirection = 0.0F;
            this.mainPath = true;
            this.legStartTheta = legStartTheta;
            this.theta = theta;
        }
    }
}
