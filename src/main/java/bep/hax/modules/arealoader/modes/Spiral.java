package bep.hax.modules.arealoader.modes;

import bep.hax.modules.arealoader.AreaLoader;
import bep.hax.modules.arealoader.AreaLoaderMode;
import bep.hax.modules.arealoader.AreaLoaderModes;
import bep.hax.util.Utils;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class Spiral extends AreaLoaderMode {
    private Spiral.PathingDataSpiral pd;
    private boolean goingToStart = true;
    private long startTime;
    private BlockPos nextCornerTarget = null;
    private boolean recovering = false;
    private BlockPos recoveryTarget = null;
    private BlockPos lastKnownGoodPosition = null;
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

    public Spiral() {
        super(AreaLoaderModes.Spiral);
    }

    @Override
    public void onActivate() {
        this.startTime = System.nanoTime();
        this.goingToStart = true;
        this.nextCornerTarget = null;
        this.recovering = false;
        this.recoveryTarget = null;
        this.lastTickPos = null;
        this.lastTickDimension = null;
        this.teleportPaused = false;
        this.teleportStableTicks = 0;
        this.teleportJumpDistance = 0.0;
        this.lastKnownGoodPosition = null;
        this.resetDriftDetectors();
        File file = this.getJsonFile(this.saveFileName());
        if (file == null) {
            this.debugInfo("Error: Cannot create save file path. Check save-name setting.");
            this.pd = new Spiral.PathingDataSpiral(this.mc.player.blockPosition(), this.mc.player.blockPosition(), -90.0F, true, 0, 0);
            this.goingToStart = false;
        } else if (!file.exists()) {
            this.pd = new Spiral.PathingDataSpiral(this.mc.player.blockPosition(), this.mc.player.blockPosition(), -90.0F, true, 0, 0);
            this.goingToStart = false;
            this.debugInfo("Spiral started from origin: " + this.pd.spiralOrigin.toShortString() + " (no save file found at: " + file.getAbsolutePath() + ")");
        } else {
            try {
                this.debugInfo("Loading save file: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
                FileReader reader = new FileReader(file);
                this.pd = GSON.fromJson(reader, Spiral.PathingDataSpiral.class);
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
                } else if (this.pd.spiralOrigin != null && this.pd.spiralWidth == 0 && this.pd.spiralHeight == 0) {
                    BlockPos origin = this.pd.spiralOrigin;
                    BlockPos curr = this.pd.currPos;
                    double distFromOrigin = Math.sqrt(curr.distSqr(origin));
                    if (distFromOrigin > 1000.0) {
                        corrupted = true;
                        corruptionReason = String.format(
                            "spiralWidth/Height are 0 but currPos is %.0f blocks from origin - save may have been reset", distFromOrigin
                        );
                    }
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

                this.debugInfo(
                    "Loaded saved path successfully. Origin: " + this.pd.spiralOrigin.toShortString() + ", Current: " + this.pd.currPos.toShortString()
                );
                this.debugInfo(
                    "Spiral state: width=%d, height=%d, yaw=%.1f, mainPath=%b",
                    this.pd.spiralWidth,
                    this.pd.spiralHeight,
                    this.pd.yawDirection,
                    this.pd.mainPath
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
        this.nextCornerTarget = null;
        this.recovering = false;
        this.recoveryTarget = null;
        this.lastTickPos = null;
        this.lastTickDimension = null;
        this.teleportPaused = false;
        this.teleportStableTicks = 0;
        this.teleportJumpDistance = 0.0;
        this.lastKnownGoodPosition = null;
        this.resetDriftDetectors();
        this.debugInfo("Spiral state reset. Next activation will start fresh.");
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
                    ChatUtils.info("Spiral PAUSED due to teleportation. State saved at last safe position.");
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
                            this.nextCornerTarget = null;
                            this.resetDriftDetectors();
                            ChatUtils.info(
                                "Position stable again. Auto-resuming spiral: navigating back to saved position X=%d Z=%d.",
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
                    this.nextCornerTarget = null;
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
                    this.debugInfo("Recovery complete. Resuming spiral at X=%d Z=%d", this.recoveryTarget.getX(), this.recoveryTarget.getZ());
                    this.recovering = false;
                    this.recoveryTarget = null;
                    this.nextCornerTarget = null;
                    this.mc.player.setDeltaMovement(0.0, 0.0, 0.0);
                    this.resetDriftDetectors();
                } else {
                    this.updateGoalWaypoint(this.recoveryTarget);
                    this.mc.player.setYRot((float)Rotations.getYaw(this.recoveryTarget.getCenter()));
                    Utils.setPressed(this.mc.options.keyUp, true);
                }
            } else {
                int blockGap = 16 * this.searchArea.rowGap.get();
                if (this.nextCornerTarget == null) {
                    this.nextCornerTarget = this.calculateExactCornerTarget(blockGap);
                }

                this.updateGoalWaypoint(this.nextCornerTarget);
                BlockPos expectedPos = this.calculateExpectedPosition(blockGap);
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
                    if (this.hasReachedCorner(this.nextCornerTarget)) {
                        BlockPos snappedCorner = this.nextCornerTarget;
                        this.pd.yawDirection = this.normalizeYaw(this.pd.yawDirection + 90.0F);
                        this.pd.initialPos = new BlockPos(snappedCorner.getX(), this.pd.initialPos.getY(), snappedCorner.getZ());
                        if (this.pd.mainPath) {
                            this.pd.spiralWidth += blockGap;
                            this.pd.mainPath = false;
                        } else {
                            this.pd.spiralHeight += blockGap;
                            this.pd.mainPath = true;
                        }

                        this.mc.player.setDeltaMovement(0.0, 0.0, 0.0);
                        this.mc.player.setYRot(this.pd.yawDirection);
                        this.resetDriftDetectors();
                        this.supposedYaw = this.pd.yawDirection;
                        this.pd.currPos = snappedCorner;
                        this.nextCornerTarget = null;
                        super.saveToJsonExact(this.pd);
                        this.debugInfo(
                            "Spiral: Turned corner at X=%d Z=%d (yaw=%.1f, mainPath=%b, width=%d, height=%d)",
                            snappedCorner.getX(),
                            snappedCorner.getZ(),
                            this.pd.yawDirection,
                            this.pd.mainPath,
                            this.pd.spiralWidth,
                            this.pd.spiralHeight
                        );
                    } else {
                        Utils.setPressed(this.mc.options.keyUp, true);
                        float yawToTarget = (float)Rotations.getYaw(this.nextCornerTarget.getCenter());
                        this.mc.player.setYRot(yawToTarget);
                        this.supposedYaw = this.normalizeYaw(yawToTarget);
                        this.checkTurnDrift(offTrackDistance, expectedPos);
                    }
                }
            }
        }
    }

    private BlockPos calculateExactCornerTarget(int blockGap) {
        float normalizedYaw = this.normalizeYaw(this.pd.yawDirection);
        int targetX;
        int targetZ;
        if (this.pd.mainPath) {
            targetZ = this.pd.initialPos.getZ();
            if (normalizedYaw >= 225.0F && normalizedYaw < 315.0F) {
                targetX = this.pd.initialPos.getX() + blockGap + this.pd.spiralWidth;
            } else {
                targetX = this.pd.initialPos.getX() - (blockGap + this.pd.spiralWidth);
            }
        } else {
            targetX = this.pd.initialPos.getX();
            if (!(normalizedYaw >= 315.0F) && !(normalizedYaw < 45.0F)) {
                targetZ = this.pd.initialPos.getZ() - (blockGap + this.pd.spiralHeight);
            } else {
                targetZ = this.pd.initialPos.getZ() + blockGap + this.pd.spiralHeight;
            }
        }

        return new BlockPos(targetX, this.mc.player.getBlockY(), targetZ);
    }

    private boolean hasReachedCorner(BlockPos corner) {
        double distToCorner = Math.sqrt(
            this.mc.player.blockPosition().distToLowCornerSqr(corner.getX(), this.mc.player.getY(), corner.getZ())
        );
        return distToCorner < this.searchArea.spiralCornerReachDistance.get().intValue() ? true : this.hasPassedCorner(corner);
    }

    private boolean hasPassedCorner(BlockPos corner) {
        if (this.pd.mainPath) {
            boolean goingPositive = corner.getX() >= this.pd.initialPos.getX();
            return goingPositive ? this.mc.player.getX() >= corner.getX() : this.mc.player.getX() <= corner.getX();
        } else {
            boolean goingPositive = corner.getZ() >= this.pd.initialPos.getZ();
            return goingPositive ? this.mc.player.getZ() >= corner.getZ() : this.mc.player.getZ() <= corner.getZ();
        }
    }

    private BlockPos calculateExpectedPosition(int blockGap) {
        float normalizedYaw = this.normalizeYaw(this.pd.yawDirection);
        int playerX = this.mc.player.getBlockX();
        int playerZ = this.mc.player.getBlockZ();
        if (this.pd.mainPath) {
            int expectedZ = this.pd.initialPos.getZ();
            int targetX;
            if (normalizedYaw >= 225.0F && normalizedYaw < 315.0F) {
                targetX = this.pd.initialPos.getX() + blockGap + this.pd.spiralWidth;
            } else {
                targetX = this.pd.initialPos.getX() - (blockGap + this.pd.spiralWidth);
            }

            int expectedX = this.clamp(playerX, Math.min(this.pd.initialPos.getX(), targetX), Math.max(this.pd.initialPos.getX(), targetX));
            return new BlockPos(expectedX, this.mc.player.getBlockY(), expectedZ);
        } else {
            int expectedX = this.pd.initialPos.getX();
            int targetZ;
            if (!(normalizedYaw >= 315.0F) && !(normalizedYaw < 45.0F)) {
                targetZ = this.pd.initialPos.getZ() - (blockGap + this.pd.spiralHeight);
            } else {
                targetZ = this.pd.initialPos.getZ() + blockGap + this.pd.spiralHeight;
            }

            int expectedZ = this.clamp(playerZ, Math.min(this.pd.initialPos.getZ(), targetZ), Math.max(this.pd.initialPos.getZ(), targetZ));
            return new BlockPos(expectedX, this.mc.player.getBlockY(), expectedZ);
        }
    }

    private double getOffTrackDistance(BlockPos expectedPos) {
        return this.pd.mainPath
            ? Math.abs(this.mc.player.getZ() - expectedPos.getZ())
            : Math.abs(this.mc.player.getX() - expectedPos.getX());
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
        ChatUtils.info("Saved recovery point at X=%d Z=%d. Navigating back to resume spiral.", target.getX(), target.getZ());
        this.recovering = true;
        this.recoveryTarget = target;
        this.nextCornerTarget = null;
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
                "Overdrift detected: off-track %.0f blocks and growing (supposed yaw %.0f). Recovering to the missed corner.",
                offTrackDistance,
                this.supposedYaw
            );
            this.startRecovery(expectedPos);
        } else if (this.headingDriftTicks >= 60) {
            this.debugInfo("Heading drift detected: motion is >%.0f° off the supposed yaw %.0f. Recovering to the spiral path.", 45.0F, this.supposedYaw);
            this.startRecovery(expectedPos);
        }
    }

    private float yawDistance(float a, float b) {
        float d = Math.abs(this.normalizeYaw(a) - this.normalizeYaw(b)) % 360.0F;
        return d > 180.0F ? 360.0F - d : d;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double distanceXZ(int x1, int z1, int x2, int z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void onTickNether() {
        if (this.isInNether) {
            if (this.searchArea.netherPathMode.get() == AreaLoader.NetherPathMode.BARITONE_ELYTRA) {
                int blockGap = 16 * this.searchArea.rowGap.get();
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
                        this.nextCornerTarget = null;
                        this.debugInfo(
                            "Reached saved position. Resuming spiral at yaw=%.1f, mainPath=%b, width=%d, height=%d",
                            this.pd.yawDirection,
                            this.pd.mainPath,
                            this.pd.spiralWidth,
                            this.pd.spiralHeight
                        );
                    } else if (this.needsNewGoal()) {
                        this.setBaritoneGoal(this.pd.currPos);
                    }
                } else if (this.nextCornerTarget == null) {
                    this.calculateNextCorner(blockGap);
                    if (this.nextCornerTarget != null) {
                        this.setBaritoneGoal(this.nextCornerTarget);
                    }
                } else {
                    double distToCorner = Math.sqrt(
                        this.mc
                            .player
                            .blockPosition()
                            .distToLowCornerSqr(this.nextCornerTarget.getX(), this.mc.player.getY(), this.nextCornerTarget.getZ())
                    );
                    if (distToCorner < reachDist || this.hasPassedCorner(this.nextCornerTarget)) {
                        BlockPos snappedCorner = this.nextCornerTarget;
                        this.debugInfo(
                            "Spiral: Reached corner at X=%d Z=%d, yaw was %.1f",
                            snappedCorner.getX(),
                            snappedCorner.getZ(),
                            this.pd.yawDirection
                        );
                        this.pd.yawDirection = this.normalizeYaw(this.pd.yawDirection + 90.0F);
                        this.pd.initialPos = new BlockPos(snappedCorner.getX(), this.pd.initialPos.getY(), snappedCorner.getZ());
                        if (this.pd.mainPath) {
                            this.pd.spiralWidth += blockGap;
                            this.pd.mainPath = false;
                        } else {
                            this.pd.spiralHeight += blockGap;
                            this.pd.mainPath = true;
                        }

                        this.pd.currPos = snappedCorner;
                        this.lastKnownGoodPosition = snappedCorner;
                        this.nextCornerTarget = null;
                        super.saveToJsonExact(this.pd);
                        this.debugInfo(
                            "Spiral: Turned to yaw=%.1f, mainPath=%b, width=%d, height=%d (saved)",
                            this.pd.yawDirection,
                            this.pd.mainPath,
                            this.pd.spiralWidth,
                            this.pd.spiralHeight
                        );
                    } else if (this.needsNewGoal()) {
                        this.debugInfo("Spiral: Baritone needs new goal, resetting to corner target");
                        this.setBaritoneGoal(this.nextCornerTarget);
                    }
                }
            }
        }
    }

    private float normalizeYaw(float yaw) {
        yaw %= 360.0F;
        if (yaw < 0.0F) {
            yaw += 360.0F;
        }

        return yaw;
    }

    private void calculateNextCorner(int blockGap) {
        float normalizedYaw = this.normalizeYaw(this.pd.yawDirection);
        int targetX;
        int targetZ;
        if (this.pd.mainPath) {
            int xDirection;
            if (normalizedYaw >= 225.0F && normalizedYaw < 315.0F) {
                xDirection = 1;
            } else if (normalizedYaw >= 45.0F && normalizedYaw < 135.0F) {
                xDirection = -1;
            } else {
                xDirection = 1;
            }

            targetX = this.pd.initialPos.getX() + xDirection * (blockGap + this.pd.spiralWidth);
            targetZ = this.pd.initialPos.getZ();
        } else {
            int zDirection;
            if (normalizedYaw >= 315.0F || normalizedYaw < 45.0F) {
                zDirection = 1;
            } else if (normalizedYaw >= 135.0F && normalizedYaw < 225.0F) {
                zDirection = -1;
            } else {
                zDirection = 1;
            }

            targetX = this.pd.initialPos.getX();
            targetZ = this.pd.initialPos.getZ() + zDirection * (blockGap + this.pd.spiralHeight);
        }

        this.nextCornerTarget = new BlockPos(targetX, this.mc.player.getBlockY(), targetZ);
        this.updateGoalWaypoint(this.nextCornerTarget);
        this.debugInfo("Spiral: Next corner target at X=%d, Z=%d (yaw=%.1f, mainPath=%b)", targetX, targetZ, normalizedYaw, this.pd.mainPath);
    }

    private List<Spiral.SpiralLegInfo> generateSpiralLegs(int originX, int originZ, int blockGap, int maxLegs) {
        List<Spiral.SpiralLegInfo> legs = new ArrayList<>();
        int x = originX;
        int z = originZ;
        float yaw = 270.0F;
        boolean mainPath = true;
        int spiralWidth = 0;
        int spiralHeight = 0;

        for (int legNum = 0; legNum < maxLegs; legNum++) {
            int startX = x;
            int startZ = z;
            float normYaw = this.normalizeYaw(yaw);
            int endX;
            int endZ;
            if (mainPath) {
                endZ = z;
                if (normYaw >= 225.0F && normYaw < 315.0F) {
                    endX = x + blockGap + spiralWidth;
                } else {
                    endX = x - (blockGap + spiralWidth);
                }
            } else {
                endX = x;
                if (!(normYaw >= 315.0F) && !(normYaw < 45.0F)) {
                    endZ = z - (blockGap + spiralHeight);
                } else {
                    endZ = z + blockGap + spiralHeight;
                }
            }

            legs.add(new Spiral.SpiralLegInfo(startX, startZ, endX, endZ, spiralWidth, spiralHeight, yaw, mainPath, legNum));
            x = endX;
            z = endZ;
            yaw = this.normalizeYaw(yaw + 90.0F);
            if (mainPath) {
                spiralWidth += blockGap;
                mainPath = false;
            } else {
                spiralHeight += blockGap;
                mainPath = true;
            }
        }

        return legs;
    }

    private Spiral.SpiralLegInfo findClosestLeg(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        int dxFromOrigin = Math.abs(targetX - originX);
        int dzFromOrigin = Math.abs(targetZ - originZ);
        int maxCoordDist = Math.max(dxFromOrigin, dzFromOrigin);
        int estimatedLegs = (maxCoordDist / blockGap + 1) * 4 + 500;
        estimatedLegs = Math.max(500, Math.min(estimatedLegs, 500000));
        this.debugInfo("Search params: origin=(%d,%d) target=(%d,%d) gap=%d", originX, originZ, targetX, targetZ, blockGap);
        this.debugInfo("Generating %d spiral legs (max coord dist = %d)", estimatedLegs, maxCoordDist);
        List<Spiral.SpiralLegInfo> legs = this.generateSpiralLegs(originX, originZ, blockGap, estimatedLegs);
        this.debugInfo("First 4 legs of generated spiral:");

        for (int i = 0; i < Math.min(4, legs.size()); i++) {
            Spiral.SpiralLegInfo l = legs.get(i);
            this.debugInfo(
                "  Leg %d: (%d,%d)->(%d,%d) yaw=%.0f mp=%b w=%d h=%d", i, l.startX, l.startZ, l.endX, l.endZ, l.yaw, l.mainPath, l.spiralWidth, l.spiralHeight
            );
        }

        List<Spiral.SpiralLegInfo> nearbyLegs = new ArrayList<>();

        for (Spiral.SpiralLegInfo leg : legs) {
            if (leg.distanceToLeg(targetX, targetZ) < blockGap * 3) {
                nearbyLegs.add(leg);
            }
        }

        if (!nearbyLegs.isEmpty()) {
            this.debugInfo("Legs near target position (%d found):", nearbyLegs.size());

            for (int i = 0; i < Math.min(5, nearbyLegs.size()); i++) {
                Spiral.SpiralLegInfo l = nearbyLegs.get(i);
                this.debugInfo(
                    "  Leg %d: (%d,%d)->(%d,%d) dist=%.0f yaw=%.0f mp=%b",
                    l.legNumber,
                    l.startX,
                    l.startZ,
                    l.endX,
                    l.endZ,
                    l.distanceToLeg(targetX, targetZ),
                    l.yaw,
                    l.mainPath
                );
            }
        }

        double tolerance = blockGap * 1.5;
        List<Spiral.SpiralLegInfo> candidates = new ArrayList<>();

        for (Spiral.SpiralLegInfo leg : legs) {
            double dist = leg.distanceToLeg(targetX, targetZ);
            if (dist <= tolerance) {
                candidates.add(leg);
            }
        }

        this.debugInfo("Found %d candidate legs within tolerance %.0f blocks", candidates.size(), tolerance);
        if (candidates.isEmpty()) {
            Spiral.SpiralLegInfo closest = null;
            double closestDist = Double.MAX_VALUE;

            for (Spiral.SpiralLegInfo leg : legs) {
                double dist = leg.distanceToLeg(targetX, targetZ);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = leg;
                }
            }

            this.debugInfo("No candidates within tolerance, using closest leg #%d at distance %.0f", closest != null ? closest.legNumber : -1, closestDist);
            return closest;
        } else {
            List<Spiral.SpiralLegInfo> notPastEnd = new ArrayList<>();

            for (Spiral.SpiralLegInfo leg : candidates) {
                int[] proj = leg.projectOntoLeg(targetX, targetZ);
                double legLength = distanceXZ(leg.startX, leg.startZ, leg.endX, leg.endZ);
                double projDist = distanceXZ(leg.startX, leg.startZ, proj[0], proj[1]);
                if (projDist < legLength * 0.95) {
                    notPastEnd.add(leg);
                }
            }

            List<Spiral.SpiralLegInfo> pool = notPastEnd.isEmpty() ? candidates : notPastEnd;
            Spiral.SpiralLegInfo best = null;

            for (Spiral.SpiralLegInfo leg : pool) {
                if (best == null || leg.legNumber > best.legNumber) {
                    best = leg;
                }
            }

            if (best != null) {
                double distToBest = best.distanceToLeg(targetX, targetZ);
                double closestPossible = Double.MAX_VALUE;

                for (Spiral.SpiralLegInfo leg : legs) {
                    double dist = leg.distanceToLeg(targetX, targetZ);
                    if (dist < closestPossible) {
                        closestPossible = dist;
                    }
                }

                if (distToBest > closestPossible * 2.0 + 50.0) {
                    for (Spiral.SpiralLegInfo leg : legs) {
                        if (leg.distanceToLeg(targetX, targetZ) <= closestPossible + 10.0) {
                            this.debugInfo(
                                "Selected Leg #%d (closest): (%d,%d)->(%d,%d) dist=%.0f",
                                leg.legNumber,
                                leg.startX,
                                leg.startZ,
                                leg.endX,
                                leg.endZ,
                                leg.distanceToLeg(targetX, targetZ)
                            );
                            return leg;
                        }
                    }
                }
            }

            if (best != null) {
                this.debugInfo(
                    "Selected Leg #%d (best candidate): (%d,%d)->(%d,%d) dist=%.0f",
                    best.legNumber,
                    best.startX,
                    best.startZ,
                    best.endX,
                    best.endZ,
                    best.distanceToLeg(targetX, targetZ)
                );
            }

            return best;
        }
    }

    public boolean validateManualCoordinates(int originX, int originZ, int targetX, int targetZ, int blockGap, boolean apply) {
        this.debugInfo("Finding spiral leg for position (%d, %d) with origin (%d, %d), gap=%d blocks", targetX, targetZ, originX, originZ, blockGap);
        Spiral.SpiralLegInfo closestLeg = this.findClosestLeg(originX, originZ, targetX, targetZ, blockGap);
        if (closestLeg == null) {
            ChatUtils.error("Could not calculate spiral path. Check your settings.");
            return false;
        }

        double distanceToLeg = closestLeg.distanceToLeg(targetX, targetZ);
        int[] projected = closestLeg.projectOntoLeg(targetX, targetZ);
        int expectedEndX;
        int expectedEndZ;
        if (closestLeg.mainPath) {
            expectedEndZ = closestLeg.startZ;
            float normYaw = this.normalizeYaw(closestLeg.yaw);
            if (normYaw >= 225.0F && normYaw < 315.0F) {
                expectedEndX = closestLeg.startX + blockGap + closestLeg.spiralWidth;
            } else {
                expectedEndX = closestLeg.startX - (blockGap + closestLeg.spiralWidth);
            }
        } else {
            expectedEndX = closestLeg.startX;
            float normYaw = this.normalizeYaw(closestLeg.yaw);
            if (!(normYaw >= 315.0F) && !(normYaw < 45.0F)) {
                expectedEndZ = closestLeg.startZ - (blockGap + closestLeg.spiralHeight);
            } else {
                expectedEndZ = closestLeg.startZ + blockGap + closestLeg.spiralHeight;
            }
        }

        this.debugInfo("Found Leg #%d:", closestLeg.legNumber);
        this.debugInfo("  Start: (%d, %d)", closestLeg.startX, closestLeg.startZ);
        this.debugInfo("  End:   (%d, %d)", closestLeg.endX, closestLeg.endZ);
        this.debugInfo("  Next corner will be at: (%d, %d)", expectedEndX, expectedEndZ);
        this.debugInfo("  Direction: yaw=%.0f (%s), mainPath=%b", closestLeg.yaw, this.getDirectionName(closestLeg.yaw), closestLeg.mainPath);
        this.debugInfo("  Spiral state: width=%d, height=%d", closestLeg.spiralWidth, closestLeg.spiralHeight);
        this.debugInfo("  Distance from path: %.0f blocks", distanceToLeg);
        if (distanceToLeg > blockGap / 2) {
            this.debugInfo("  Projected position onto leg: (%d, %d)", projected[0], projected[1]);
        }

        if (apply) {
            if (distanceToLeg > blockGap * 2) {
                ChatUtils.error("Position is too far from spiral path (%.0f blocks). Max tolerance: %d blocks.", distanceToLeg, blockGap * 2);
                ChatUtils.error("Make sure you entered the correct origin coordinates where the spiral started.");
                return false;
            }

            int resumeX = distanceToLeg < 50.0 ? targetX : projected[0];
            int resumeZ = distanceToLeg < 50.0 ? targetZ : projected[1];
            Spiral.PathingDataSpiral newPd = new Spiral.PathingDataSpiral(
                new BlockPos(closestLeg.startX, 64, closestLeg.startZ),
                new BlockPos(resumeX, 64, resumeZ),
                closestLeg.yaw,
                closestLeg.mainPath,
                closestLeg.spiralWidth,
                closestLeg.spiralHeight
            );
            newPd.spiralOrigin = new BlockPos(originX, 64, originZ);
            File file = this.getJsonFile(this.saveFileName());
            if (file == null) {
                ChatUtils.error("Failed to get save file path.");
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
                this.debugInfo("SAVED spiral recovery state:");
                this.debugInfo("  Origin: (%d, %d)", originX, originZ);
                this.debugInfo("  Resume from: (%d, %d)", resumeX, resumeZ);
                this.debugInfo("  Initial pos (leg start): (%d, %d)", closestLeg.startX, closestLeg.startZ);
                this.debugInfo("  Next target: (%d, %d) going %s", expectedEndX, expectedEndZ, this.getDirectionName(closestLeg.yaw));
                this.debugInfo("Enable the module to resume the spiral.");
                return true;
            } catch (Exception e) {
                ChatUtils.error("Failed to save: " + e.getMessage());
                return false;
            }
        } else {
            return distanceToLeg < blockGap / 2;
        }
    }

    private String getDirectionName(float yaw) {
        float norm = this.normalizeYaw(yaw);
        if (norm >= 315.0F || norm < 45.0F) {
            return "South (+Z)";
        } else if (norm >= 45.0F && norm < 135.0F) {
            return "West (-X)";
        } else {
            return norm >= 135.0F && norm < 225.0F ? "North (-Z)" : "East (+X)";
        }
    }

    public int[] snapToNearestCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        this.debugInfo("Snapping position (%d, %d) to spiral path...", targetX, targetZ);
        Spiral.SpiralLegInfo closestLeg = this.findClosestLeg(originX, originZ, targetX, targetZ, blockGap);
        if (closestLeg == null) {
            ChatUtils.error("Could not calculate spiral path.");
            return null;
        } else {
            int[] projected = closestLeg.projectOntoLeg(targetX, targetZ);
            double distance = closestLeg.distanceToLeg(targetX, targetZ);
            this.debugInfo("Snapped to Leg #%d at position (%d, %d)", closestLeg.legNumber, projected[0], projected[1]);
            this.debugInfo("  Was %.0f blocks off the spiral path", distance);
            this.debugInfo(
                "  This leg goes from (%d, %d) to (%d, %d) heading %s",
                closestLeg.startX,
                closestLeg.startZ,
                closestLeg.endX,
                closestLeg.endZ,
                this.getDirectionName(closestLeg.yaw)
            );
            return projected;
        }
    }

    public int[] snapToNextCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        this.debugInfo("=== SNAP TO NEXT CORNER ===");
        this.debugInfo("Input: origin=(%d, %d), target=(%d, %d), blockGap=%d", originX, originZ, targetX, targetZ, blockGap);
        Spiral.SpiralLegInfo closestLeg = this.findClosestLeg(originX, originZ, targetX, targetZ, blockGap);
        if (closestLeg == null) {
            ChatUtils.error("Could not calculate spiral path.");
            return null;
        } else {
            int nextCornerX = closestLeg.endX;
            int nextCornerZ = closestLeg.endZ;
            this.debugInfo("Found Leg #%d: (%d, %d) -> (%d, %d)", closestLeg.legNumber, closestLeg.startX, closestLeg.startZ, closestLeg.endX, closestLeg.endZ);
            this.debugInfo("Next corner: (%d, %d) going %s", nextCornerX, nextCornerZ, this.getDirectionName(closestLeg.yaw));
            return new int[]{nextCornerX, nextCornerZ};
        }
    }

    public boolean applyFromNextCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        this.debugInfo("=== APPLY FROM NEXT CORNER ===");
        this.debugInfo("Input: origin=(%d, %d), target=(%d, %d), blockGap=%d", originX, originZ, targetX, targetZ, blockGap);
        Spiral.SpiralLegInfo closestLeg = this.findClosestLeg(originX, originZ, targetX, targetZ, blockGap);
        if (closestLeg == null) {
            ChatUtils.error("Could not find your position on the spiral.");
            return false;
        }

        int cornerX = closestLeg.endX;
        int cornerZ = closestLeg.endZ;
        float newYaw = this.normalizeYaw(closestLeg.yaw + 90.0F);
        boolean newMainPath;
        int newWidth;
        int newHeight;
        if (closestLeg.mainPath) {
            newWidth = closestLeg.spiralWidth + blockGap;
            newHeight = closestLeg.spiralHeight;
            newMainPath = false;
        } else {
            newWidth = closestLeg.spiralWidth;
            newHeight = closestLeg.spiralHeight + blockGap;
            newMainPath = true;
        }

        float normNewYaw = this.normalizeYaw(newYaw);
        int nextTargetX;
        int nextTargetZ;
        if (newMainPath) {
            nextTargetZ = cornerZ;
            if (normNewYaw >= 225.0F && normNewYaw < 315.0F) {
                nextTargetX = cornerX + blockGap + newWidth;
            } else {
                nextTargetX = cornerX - (blockGap + newWidth);
            }
        } else {
            nextTargetX = cornerX;
            if (!(normNewYaw >= 315.0F) && !(normNewYaw < 45.0F)) {
                nextTargetZ = cornerZ - (blockGap + newHeight);
            } else {
                nextTargetZ = cornerZ + blockGap + newHeight;
            }
        }

        this.debugInfo("Current position near leg #%d", closestLeg.legNumber);
        this.debugInfo("Will snap to corner: (%d, %d)", cornerX, cornerZ);
        this.debugInfo("After turn, next target: (%d, %d) going %s", nextTargetX, nextTargetZ, this.getDirectionName(newYaw));
        this.debugInfo("New state: yaw=%.0f, mainPath=%b, width=%d, height=%d", newYaw, newMainPath, newWidth, newHeight);
        Spiral.PathingDataSpiral newPd = new Spiral.PathingDataSpiral(
            new BlockPos(cornerX, 64, cornerZ), new BlockPos(cornerX, 64, cornerZ), newYaw, newMainPath, newWidth, newHeight
        );
        newPd.spiralOrigin = new BlockPos(originX, 64, originZ);
        this.debugInfo("Getting save file for '%s'", this.toString());
        File file = this.getJsonFile(this.saveFileName());
        if (file == null) {
            ChatUtils.error("Failed to get save file path. Check that you're in a world.");
            return false;
        }

        this.debugInfo("Will save to: %s", file.getAbsolutePath());

        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            Writer writer = new FileWriter(file);
            GSON.toJson(newPd, writer);
            writer.flush();
            writer.close();
            this.debugInfo("=== SAVED ===");
            this.debugInfo("Go to (%d, %d) and enable the module.", cornerX, cornerZ);
            this.debugInfo("It will head toward (%d, %d)", nextTargetX, nextTargetZ);
            return true;
        } catch (Exception e) {
            ChatUtils.error("Failed to save: " + e.getMessage());
            return false;
        }
    }

    public static class PathingDataSpiral extends AreaLoaderMode.PathingData {
        public BlockPos spiralOrigin;
        public int spiralWidth = 0;
        public int spiralHeight = 0;

        public PathingDataSpiral(BlockPos initialPos, BlockPos currPos, float yawDirection, boolean mainPath, int spiralWidth, int spiralHeight) {
            this.spiralOrigin = initialPos;
            this.initialPos = initialPos;
            this.currPos = currPos;
            this.yawDirection = yawDirection;
            this.mainPath = mainPath;
            this.spiralWidth = spiralWidth;
            this.spiralHeight = spiralHeight;
        }
    }

    public static class SpiralLegInfo {
        public int startX;
        public int startZ;
        public int endX;
        public int endZ;
        public int spiralWidth;
        public int spiralHeight;
        public float yaw;
        public boolean mainPath;
        public int legNumber;

        SpiralLegInfo(int sx, int sz, int ex, int ez, int w, int h, float y, boolean mp, int num) {
            this.startX = sx;
            this.startZ = sz;
            this.endX = ex;
            this.endZ = ez;
            this.spiralWidth = w;
            this.spiralHeight = h;
            this.yaw = y;
            this.mainPath = mp;
            this.legNumber = num;
        }

        double distanceToLeg(int px, int pz) {
            double dx = this.endX - this.startX;
            double dz = this.endZ - this.startZ;
            double legLengthSq = dx * dx + dz * dz;
            if (legLengthSq == 0.0) {
                return Math.sqrt((px - this.startX) * (px - this.startX) + (pz - this.startZ) * (pz - this.startZ));
            }

            double t = Math.max(0.0, Math.min(1.0, ((px - this.startX) * dx + (pz - this.startZ) * dz) / legLengthSq));
            double projX = this.startX + t * dx;
            double projZ = this.startZ + t * dz;
            return Math.sqrt((px - projX) * (px - projX) + (pz - projZ) * (pz - projZ));
        }

        int[] projectOntoLeg(int px, int pz) {
            double dx = this.endX - this.startX;
            double dz = this.endZ - this.startZ;
            double legLengthSq = dx * dx + dz * dz;
            if (legLengthSq == 0.0) {
                return new int[]{this.startX, this.startZ};
            }

            double t = ((px - this.startX) * dx + (pz - this.startZ) * dz) / legLengthSq;
            t = Math.max(0.0, Math.min(1.0, t));
            int projX = (int)Math.round(this.startX + t * dx);
            int projZ = (int)Math.round(this.startZ + t * dz);
            return new int[]{projX, projZ};
        }
    }
}
