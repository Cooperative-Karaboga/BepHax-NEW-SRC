package bep.hax.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.ICustomGoalProcess;
import net.minecraft.core.BlockPos;

public final class HighwayBaritoneDriver {
    private static final int REISSUE_TICKS = 4;
    private static final double RECOVER_DIST = 1.25;
    private static boolean engaged;
    private static Goal lastGoal;
    private static int cooldown;
    private static Boolean lastAdvancing;
    private static Boolean savedAllowBreak;
    private static Boolean savedAllowPlace;
    private static Boolean savedRenderBeacon;

    private HighwayBaritoneDriver() {
    }

    public static void drive(int dx, int dz, boolean advancing, int headX, int headY, int headZ, double px, double pz) {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone != null) {
            applySettings();
            engaged = true;
            Goal goal = advancing ? new GoalXZ(headX + dx, headZ + dz) : new GoalGetToBlock(new BlockPos(headX, headY, headZ));
            ICustomGoalProcess process = baritone.getCustomGoalProcess();
            boolean active = process.isActive();
            boolean targetChanged = lastGoal == null || !goal.equals(lastGoal);
            boolean modeFlip = lastAdvancing == null || lastAdvancing != advancing;
            if (cooldown > 0) {
                cooldown--;
            }

            boolean shouldPath;
            if (advancing) {
                shouldPath = targetChanged || !active;
            } else {
                double dist = Math.hypot(px - (headX + 0.5), pz - (headZ + 0.5));
                shouldPath = targetChanged || dist > 1.25 && !active;
            }

            if (shouldPath && (modeFlip || !active || cooldown == 0)) {
                process.setGoalAndPath(goal);
                lastGoal = goal;
                cooldown = 4;
            }

            lastAdvancing = advancing;
        }
    }

    public static void pause() {
        if (engaged) {
            engaged = false;
            lastGoal = null;
            cooldown = 0;
            lastAdvancing = null;
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
                baritone.getPathingBehavior().cancelEverything();
            }
        }
    }

    public static void disengage() {
        pause();
        restoreSettings();
    }

    private static void applySettings() {
        Settings settings = BaritoneAPI.getSettings();
        if (savedAllowBreak == null) {
            savedAllowBreak = settings.allowBreak.value;
            settings.allowBreak.value = false;
        }

        if (savedAllowPlace == null) {
            savedAllowPlace = settings.allowPlace.value;
            settings.allowPlace.value = false;
        }

        if (savedRenderBeacon == null) {
            savedRenderBeacon = settings.renderGoalXZBeacon.value;
            settings.renderGoalXZBeacon.value = false;
        }
    }

    private static void restoreSettings() {
        Settings settings = BaritoneAPI.getSettings();
        if (savedAllowBreak != null) {
            settings.allowBreak.value = savedAllowBreak;
            savedAllowBreak = null;
        }

        if (savedAllowPlace != null) {
            settings.allowPlace.value = savedAllowPlace;
            savedAllowPlace = null;
        }

        if (savedRenderBeacon != null) {
            settings.renderGoalXZBeacon.value = savedRenderBeacon;
            savedRenderBeacon = null;
        }
    }
}
