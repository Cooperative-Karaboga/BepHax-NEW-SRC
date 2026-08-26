package bep.hax.util;

import bep.hax.modules.RocketBoost;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class PorpoiseSolver {
    private static final double GRAVITY = 0.08;
    private static final int SIM_TICKS = 1200;
    private static final int SEARCH_TICKS = 600;
    private static final float MIN_PITCH = 2.0F;
    private static final float MAX_PITCH = 70.0F;
    private static final double NO_CAP = Double.MAX_VALUE;
    private static final int BUCKETS = 8;
    private static final float YAW_BUCKET = 11.25F;
    private static final int SLOTS = 16;
    private static final double[] keyHeight = new double[16];
    private static final float[] keyYaw = new float[16];
    private static final double[] keyAlignment = new double[16];
    private static final double[] keyThreshold = new double[16];
    private static final boolean[] keyAsymmetric = new boolean[16];
    private static final float[] valDive = new float[16];
    private static final float[] valClimb = new float[16];
    private static final double[] valSpeed = new double[16];
    private static int used = 0;
    private static int next = 0;
    private static double lastSpeed = 0.0;

    private PorpoiseSolver() {
    }

    public static float solvePitch(double height, float yaw, double alignmentDeg, double threshold) {
        return solveLegs(height, yaw, alignmentDeg, threshold, false)[0];
    }

    public static float[] solveLegs(double height, float yaw, double alignmentDeg, double threshold, boolean asymmetric) {
        float key = Mth.positiveModulo(yaw, 90.0F);

        for (int i = 0; i < used; i++) {
            if (Math.abs(height - keyHeight[i]) < 0.5
                && Math.abs(key - keyYaw[i]) < 5.625F
                && Math.abs(alignmentDeg - keyAlignment[i]) < 0.5
                && Math.abs(threshold - keyThreshold[i]) < 0.005
                && asymmetric == keyAsymmetric[i]) {
                lastSpeed = valSpeed[i];
                return new float[]{valDive[i], valClimb[i]};
            }
        }

        float centre = (Mth.floor(key / 11.25F) + 0.5F) * 11.25F;
        float best = 26.5F;
        double bestSpeed = -1.0;

        for (float p = 2.0F; p <= 70.0F; p += 4.0F) {
            double s = simulateSpeed(p, p, height, centre, alignmentDeg, threshold, 1200);
            if (s > bestSpeed) {
                bestSpeed = s;
                best = p;
            }
        }

        float lo = Math.max(2.0F, best - 4.0F);
        float hi = Math.min(70.0F, best + 4.0F);

        for (float p = lo; p <= hi; p += 0.5F) {
            double s = simulateSpeed(p, p, height, centre, alignmentDeg, threshold, 1200);
            if (s > bestSpeed) {
                bestSpeed = s;
                best = p;
            }
        }

        float dive = best;
        float climb = best;
        if (asymmetric) {
            float[] legs = descend(best, height, centre, alignmentDeg, threshold);
            double score = simulateSpeed(legs[0], legs[1], height, centre, alignmentDeg, threshold, 1200);
            if (score > bestSpeed) {
                dive = legs[0];
                climb = legs[1];
                bestSpeed = score;
            }
        }

        int slot = next;
        next = (next + 1) % 16;
        if (used < 16) {
            used++;
        }

        keyHeight[slot] = height;
        keyYaw[slot] = centre;
        keyAlignment[slot] = alignmentDeg;
        keyThreshold[slot] = threshold;
        keyAsymmetric[slot] = asymmetric;
        valDive[slot] = dive;
        valClimb[slot] = climb;
        valSpeed[slot] = bestSpeed;
        lastSpeed = bestSpeed;
        return new float[]{dive, climb};
    }

    private static float[] descend(float seed, double height, float yaw, double alignmentDeg, double threshold) {
        float dive = seed;
        float climb = seed;
        double best = simulateSpeed(dive, climb, height, yaw, alignmentDeg, threshold, 600);

        for (int round = 0; round < 2; round++) {
            for (float c = 2.0F; c <= 70.0F; c += 5.0F) {
                double s = simulateSpeed(dive, c, height, yaw, alignmentDeg, threshold, 600);
                if (s > best) {
                    best = s;
                    climb = c;
                }
            }

            for (float c = Math.max(2.0F, climb - 5.0F); c <= Math.min(70.0F, climb + 5.0F); c += 0.5F) {
                double s = simulateSpeed(dive, c, height, yaw, alignmentDeg, threshold, 600);
                if (s > best) {
                    best = s;
                    climb = c;
                }
            }

            for (float d = 2.0F; d <= 70.0F; d += 5.0F) {
                double s = simulateSpeed(d, climb, height, yaw, alignmentDeg, threshold, 600);
                if (s > best) {
                    best = s;
                    dive = d;
                }
            }

            for (float d = Math.max(2.0F, dive - 5.0F); d <= Math.min(70.0F, dive + 5.0F); d += 0.5F) {
                double s = simulateSpeed(d, climb, height, yaw, alignmentDeg, threshold, 600);
                if (s > best) {
                    best = s;
                    dive = d;
                }
            }
        }

        return new float[]{dive, climb};
    }

    public static double predictedSpeed() {
        return lastSpeed;
    }

    public static double simulateSpeed(float pitchMag, double height, float yaw, double alignmentDeg, double threshold) {
        return simulateSpeed(pitchMag, pitchMag, height, yaw, alignmentDeg, threshold, 1200);
    }

    public static double simulateSpeed(float diveMag, float climbMag, double height, float yaw, double alignmentDeg, double threshold, int ticks) {
        double anti = RocketBoost.antiTickSkipping();
        boolean diving = true;
        float pitch = diveMag;
        Vec3 look = Vec3.directionFromRotation(diveMag, yaw);
        Vec3 v = look.scale(0.5);
        double alt = 0.0;
        double travelledX = 0.0;
        double travelledZ = 0.0;

        for (int t = 0; t < ticks; t++) {
            if (alt <= -height / 2.0) {
                diving = false;
            } else if (alt >= height / 2.0) {
                diving = true;
            }

            float wanted = diving ? diveMag : -climbMag;
            Vec3 lastLook;
            if (wanted != pitch) {
                pitch = wanted;
                lastLook = look;
                look = Vec3.directionFromRotation(wanted, yaw);
            } else {
                lastLook = look;
            }

            Vec3 next = RocketBoost.rideWindow(v, look, lastLook, pitch, 0.08, threshold, anti, alignmentDeg, Double.MAX_VALUE);
            if (next == null) {
                next = vanillaGlide(v, look, pitch);
            }

            v = next;
            alt += v.y;
            travelledX += v.x;
            travelledZ += v.z;
        }

        return Math.sqrt(travelledX * travelledX + travelledZ * travelledZ) / ticks * 20.0;
    }

    private static Vec3 vanillaGlide(Vec3 v, Vec3 look, float pitch) {
        Vec3 pushed = v.add(
            look.x * 0.1 + (look.x * 1.5 - v.x) * 0.5,
            look.y * 0.1 + (look.y * 1.5 - v.y) * 0.5,
            look.z * 0.1 + (look.z * 1.5 - v.z) * 0.5
        );
        return RocketBoost.predictGliding(pushed, look, pitch, 0.08);
    }
}
