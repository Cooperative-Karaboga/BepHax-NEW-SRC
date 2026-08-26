package bep.hax.modules;

import baritone.api.BaritoneAPI;
import bep.hax.Bep;
import bep.hax.mixin.accessor.FireworkRocketEntityAccessor;
import bep.hax.util.RotationUtils;
import bep.hax.util.ViaProtocolUtil;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class RocketBoost extends Module {
    private static final boolean BARITONE = baritonePresent();
    private static final double GRIM_FIREWORK_LIMIT = 1.7;
    private static final double ANTI_TICK_SKIPPING = 0.05;
    private static final double MAX_PLAUSIBLE_MOVEMENT = 40.0;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgSafety = this.settings.createGroup("Safety");
    private final Setting<Double> speed = this.sgGeneral
        .add(
            new Builder()
                .name("speed")
                .description(
                    "Ceiling on the resulting velocity, in blocks per tick - 1 b/t is 72 km/h. A limiter, not a target: the boost already takes everything the server will accept and nothing here makes it ask for more, and the ride is clamped back inside the window after this cap is applied, so no value here can make the movement illegal - only slower. It binds far sooner than it looks. A porpoise leg peaks at 4.75 b/t on the default 24-block swing, 6.0 at 60 and 7.2 at 120, so the old 4.0 was costing 244 km/h against the 280 the window was already granting, and it dragged the solved leg from 25 degrees down to 19 on top of that. 8 clears every swing height. Below about 2.4 it starts costing speed even on a level diagonal, and below 1.7 the module stands aside because vanilla's own firework beats it."
                )
                .defaultValue(9.5)
                .min(0.1)
                .max(20.0)
                .sliderRange(1.7, 10.0)
                .build()
        );
    private final Setting<Double> amount = this.sgGeneral
        .add(
            new Builder()
                .name("amount")
                .description(
                    "Size of the accepted box to ride, in the server's own units. 1.7 is the server's own constant (UncertaintyHandler.tickFireworksBox), so this matches its window exactly and is the fastest setting that never flags - 171 km/h on a level diagonal, 280 on a solved porpoise swing. There is no headroom above it: 1.71 is a cliff, not a slope, because the box is then wider than the server's and every tick banks offset until it sets you back. Drop to 1.68 if you ever see one."
                )
                .defaultValue(1.7)
                .min(0.1)
                .max(2.0)
                .sliderRange(1.0, 1.7)
                .build()
        );
    private final Setting<Double> alignment = this.sgGeneral
        .add(
            new Builder()
                .name("alignment")
                .description(
                    "How far off your aim the boost may fly, in degrees, to reach a faster point of the accepted box. The box is axis aligned so its fastest point is a corner, and a corner is not a direction you can also be looking down - in a climb or dive it sits 18-37 degrees off the aim, because it buys its speed by diving steeper than the camera. This bounds that error. The payout is almost all in the first few degrees: ground speed on a 24-block leg is 155 km/h at 0, flying exactly where you look, 216 at 5, 268 at 10 and 280 at 15 - against 280 at the unbounded corner, so it is saturated by 15 and there is no reason to go past it. Speed only, never legality: every value stays inside the same window."
                )
                .defaultValue(15.0)
                .min(0.0)
                .max(90.0)
                .sliderRange(0.0, 45.0)
                .build()
        );
    private final Setting<Boolean> requireInput = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("require-input")
                .description(
                    "Only boost while you are holding a movement key. Off by default because plain elytra gliding needs no key held - turning this on would stop the boost entirely for a normal glider. ControlFly's stationary hover is already handled separately."
                )
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> debug = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug")
                .description("Report once a second what the boost is doing, and why it is idle when it is.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> wallCheck = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("wall-check")
                .description("Throttle the declared speed when a block is coming up in the look direction.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> lookahead = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("lookahead")
                .description("Ticks of clear space to keep ahead. The speed is capped so obstacles stay at least this far away.")
                .defaultValue(10)
                .range(2, 40)
                .sliderRange(2, 40)
                .build()
        );
    private final Setting<Boolean> chunkCheck = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("chunk-check")
                .description("Throttle the speed when flying towards chunks that have not loaded yet.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> pauseInFluid = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("pause-in-fluid")
                .description("Fall back to vanilla gliding in water or lava, where the server predicts a different movement.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> baritoneSync = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("baritone-sync")
                .description(
                    "Fly exactly where Baritone's elytra is aiming while it is flying you, instead of swinging off it towards the window corner. Baritone picks its pitch by simulating that trajectory forward and raytracing it against terrain, so the path it cleared is the one down its own aim - alignment steers off that path into rock it never checked, which is what turns you into walls mid-route. This pins alignment to 0 for as long as its elytra process is active and hands it straight back afterwards. The boost is not disabled, only straightened: the window still pays, it just pays along Baritone's line. It also counts Baritone's flight as travelling, so require-input cannot switch the boost off on a route where you are holding no keys."
                )
                .defaultValue(true)
                .build()
        );
    private Vec3 lastMovement = Vec3.ZERO;
    private Vec3 prevPos = null;
    private Vec3 lastGlidePos = null;
    private volatile boolean repositioned = false;
    private boolean windowOpen = false;
    private int latchedRocketId = -1;
    private int latchGraceTicks = 0;
    private int suppressTicks = 0;
    private int travellingTicks = 0;
    private String state = "not gliding";
    private String limiter = "speed";
    private int appliedInWindow = 0;
    private int debugTicks = 0;
    private double appliedSpeed = 0.0;
    private double appliedOffAim = 0.0;
    private boolean syncedToBaritone = false;

    private static boolean baritonePresent() {
        try {
            Class.forName("baritone.api.BaritoneAPI");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public RocketBoost() {
        super(
            Bep.HUNT_CATEGORY,
            "RocketBoost",
            "Declares the velocity the server will still accept while a firework is lit, instead of the one vanilla would build up to. It never throws fireworks itself - light them yourself or let a flight module do it."
        );
    }

    @Override
    public void onActivate() {
        this.lastMovement = this.mc.player == null ? Vec3.ZERO : this.mc.player.getDeltaMovement();
        this.prevPos = this.mc.player == null ? null : this.mc.player.position();
        this.lastGlidePos = null;
        this.repositioned = false;
        this.windowOpen = false;
        this.latchedRocketId = -1;
        this.latchGraceTicks = 0;
        this.suppressTicks = 0;
        this.travellingTicks = 0;
        this.state = "not gliding";
        this.appliedInWindow = 0;
        this.debugTicks = 0;
    }

    @Override
    public void onDeactivate() {
        this.windowOpen = false;
        this.latchedRocketId = -1;
        this.latchGraceTicks = 0;
        this.prevPos = null;
        this.lastGlidePos = null;
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            this.repositioned = true;
        }
    }

    @EventHandler
    private void onTickPre(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (!this.mc.player.isFallFlying()) {
                this.lastGlidePos = null;
            }

            this.trackWindow();
            if (this.suppressTicks > 0) {
                this.suppressTicks--;
            }

            if (this.travellingTicks > 0) {
                this.travellingTicks--;
            }

            this.keepAimFresh();
        }
    }

    private void keepAimFresh() {
        if (this.mc.player.isFallFlying() && this.windowOpen && this.allowed()) {
            RotationUtils.getInstance().keepWireFresh();
        }
    }

    private void trackWindow() {
        if (!this.mc.player.isFallFlying()) {
            this.windowOpen = false;
            this.latchedRocketId = -1;
            this.latchGraceTicks = 0;
        } else {
            FireworkRocketEntity attached = this.findAttachedRocket();
            if (attached != null) {
                this.latchedRocketId = attached.getId();
                this.latchGraceTicks = 1;
                this.windowOpen = true;
            } else {
                if ((this.latchedRocketId == -1 ? null : this.mc.level.getEntity(this.latchedRocketId)) instanceof FireworkRocketEntity firework
                    && firework.isAlive()) {
                    this.windowOpen = true;
                } else if (this.latchGraceTicks > 0) {
                    this.latchGraceTicks--;
                    this.windowOpen = true;
                } else {
                    this.windowOpen = false;
                    this.latchedRocketId = -1;
                }
            }
        }
    }

    @EventHandler
    private void onTickPost(Post event) {
        if (this.mc.player == null) {
            this.prevPos = null;
        } else {
            Vec3 pos = this.mc.player.position();
            Vec3 travelled = this.prevPos == null ? Vec3.ZERO : pos.subtract(this.prevPos);
            if (travelled.length() > 40.0) {
                travelled = Vec3.ZERO;
            }

            this.prevPos = pos;
            if (this.debug.get() && ++this.debugTicks >= 20) {
                double bpt = travelled.length();
                if (this.appliedInWindow > 0) {
                    String past = this.amount.get() > 1.7 ? " - OUTSIDE the window, expect setbacks" : "";
                    this.info(
                        "(highlight)%.0f(default) km/h / %.0f b/s (%.2f b/t) - boosting %d/%d ticks at %.2f, %.1f deg off aim, limited by %s%s%s",
                        bpt * 72.0,
                        bpt * 20.0,
                        bpt,
                        this.appliedInWindow,
                        this.debugTicks,
                        this.appliedSpeed,
                        this.appliedOffAim,
                        this.limiter,
                        past,
                        this.syncedToBaritone ? " - straightened onto Baritone's line" : ""
                    );
                } else {
                    this.info("(highlight)%.0f(default) km/h / %.0f b/s (%.2f b/t) - idle: %s", bpt * 72.0, bpt * 20.0, bpt, this.state);
                }

                this.debugTicks = 0;
                this.appliedInWindow = 0;
            }
        }
    }

    public Vec3 glideVelocity(Vec3 oldVelocity, Vec3 vanilla) {
        if (this.mc.player != null && this.mc.level != null) {
            Vec3 pos = this.mc.player.position();
            Vec3 start = this.lastGlidePos == null ? oldVelocity : pos.subtract(this.lastGlidePos);
            boolean desynced = this.lastGlidePos != null && (this.repositioned || start.length() > 40.0);
            this.repositioned = false;
            this.lastGlidePos = pos;
            this.lastMovement = start;
            if (desynced) {
                return this.note("resyncing after a teleport");
            }

            if (!this.mc.player.isFallFlying()) {
                return this.note("not gliding");
            }

            if (!this.allowed()) {
                return this.note("holding still - press a movement key");
            }

            if (!this.windowOpen) {
                return this.note("no live rocket");
            }

            if (!this.pauseInFluid.get() || !this.mc.player.isInWater() && !this.mc.player.isInLava()) {
                RotationUtils rotations = RotationUtils.getInstance();
                float pitch = rotations.getSentPitch();
                Vec3 look = Vec3.directionFromRotation(pitch, rotations.getSentYaw());
                if (look.lengthSqr() < 1.0E-9) {
                    return this.note("no look direction");
                }

                Vec3 velocity = this.rideBox(start, look, pitch, this.throttledSpeed(look));
                if (velocity == null) {
                    return this.note("no room in the window");
                }

                if (velocity.dot(look) <= vanilla.dot(look)) {
                    return this.note("vanilla is faster on this heading");
                }

                this.state = "boosting";
                this.syncedToBaritone = this.baritoneSync.get() && this.baritoneFlying();
                this.appliedSpeed = velocity.length();
                this.appliedOffAim = Math.toDegrees(
                    Math.acos(Mth.clamp(velocity.dot(look) / Math.max(this.appliedSpeed, 1.0E-9), -1.0, 1.0))
                );
                this.appliedInWindow++;
                return velocity;
            } else {
                return this.note("in fluid");
            }
        } else {
            return null;
        }
    }

    private Vec3 note(String why) {
        this.state = why;
        return null;
    }

    private Vec3 rideBox(Vec3 start, Vec3 look, float pitch, double cap) {
        RotationUtils rotations = RotationUtils.getInstance();
        Vec3 lastLook = Vec3.directionFromRotation(rotations.getServerPitch(), rotations.getServerYaw());
        return rideWindow(
            start, look, lastLook, pitch, this.effectiveGravity(start.y), this.amount.get(), antiTickSkipping(), this.effectiveAlignment(), cap
        );
    }

    public static Vec3 rideWindow(
        Vec3 start, Vec3 look, Vec3 lastLook, float pitch, double gravity, double threshold, double anti, double alignmentDeg, double cap
    ) {
        double[] box = axisBox(look, lastLook, threshold, anti);
        Vec3 predicted = predictGliding(start, look, pitch, gravity);
        double[] w = new double[]{
            predicted.x + Math.min(0.0, box[0] - start.x),
            predicted.y + Math.min(0.0, box[1] - start.y),
            predicted.z + Math.min(0.0, box[2] - start.z),
            predicted.x + Math.max(0.0, box[3] - start.x),
            predicted.y + Math.max(0.0, box[4] - start.y),
            predicted.z + Math.max(0.0, box[5] - start.z)
        };
        Vec3 heading = heading(look, w, alignmentDeg, anti);
        double reach = reach(heading, w, anti);
        if (reach <= 1.0E-9) {
            return null;
        }

        Vec3 ride = heading.scale(Math.min(reach, cap));
        return new Vec3(
            Mth.clamp(ride.x, w[0], w[3]),
            Mth.clamp(ride.y, w[1], w[4]),
            Mth.clamp(ride.z, w[2], w[5])
        );
    }

    private static Vec3 heading(Vec3 look, double[] w, double alignmentDeg, double anti) {
        double slack = Math.toRadians(alignmentDeg);
        if (slack <= 1.0E-6) {
            return look;
        }

        Vec3 corner = new Vec3(
            axisEdge(look.x, w[0], w[3], anti), axisEdge(look.y, w[1], w[4], anti), axisEdge(look.z, w[2], w[5], anti)
        );
        double len = corner.length();
        if (len <= 1.0E-9) {
            return look;
        }

        Vec3 target = corner.scale(1.0 / len);
        double angle = Math.acos(Mth.clamp(target.dot(look), -1.0, 1.0));
        if (angle <= slack) {
            return target;
        }

        if (angle >= 3.141591653589793) {
            return look;
        }

        double t = slack / angle;
        double sin = Math.sin(angle);
        return look.scale(Math.sin((1.0 - t) * angle) / sin).add(target.scale(Math.sin(t * angle) / sin));
    }

    private static double reach(Vec3 dir, double[] w, double anti) {
        double reach = Double.MAX_VALUE;
        reach = Math.min(reach, axisReach(dir.x, w[0], w[3], anti));
        reach = Math.min(reach, axisReach(dir.y, w[1], w[4], anti));
        reach = Math.min(reach, axisReach(dir.z, w[2], w[5], anti));
        return reach == Double.MAX_VALUE ? 0.0 : Math.max(0.0, reach);
    }

    private static double axisReach(double dir, double lo, double hi, double anti) {
        if (dir > anti) {
            return hi <= 0.0 ? 0.0 : hi / dir;
        } else if (dir < -anti) {
            return lo >= 0.0 ? 0.0 : lo / dir;
        } else {
            return Double.MAX_VALUE;
        }
    }

    private static double axisEdge(double dir, double lo, double hi, double anti) {
        if (dir > anti) {
            return hi;
        } else {
            return dir < -anti ? lo : Mth.clamp(0.0, lo, hi);
        }
    }

    public static double antiTickSkipping() {
        return ViaProtocolUtil.isLegacyBand(ViaProtocolUtil.targetProtocol()) ? 0.0 : 0.05;
    }

    private static double[] axisBox(Vec3 look, Vec3 lastLook, double threshold, double a) {
        double minX = Math.min(-a, look.x) + Math.min(-a, lastLook.x);
        double minY = Math.min(-a, look.y) + Math.min(-a, lastLook.y);
        double minZ = Math.min(-a, look.z) + Math.min(-a, lastLook.z);
        double maxX = Math.max(a, look.x) + Math.max(a, lastLook.x);
        double maxY = Math.max(a, look.y) + Math.max(a, lastLook.y);
        double maxZ = Math.max(a, look.z) + Math.max(a, lastLook.z);
        return new double[]{
            Math.max(-threshold, minX * threshold),
            Math.max(-threshold, minY * threshold),
            Math.max(-threshold, minZ * threshold),
            Math.min(threshold, maxX * threshold),
            Math.min(threshold, maxY * threshold),
            Math.min(threshold, maxZ * threshold)
        };
    }

    public static Vec3 predictGliding(Vec3 old, Vec3 look, float pitchDeg, double gravity) {
        float pitchRad = pitchDeg * (float) (Math.PI / 180.0);
        double horizSqrt = Math.sqrt(look.x * look.x + look.z * look.z);
        double horizLen = old.horizontalDistance();
        double vertCos = Math.cos(pitchRad);
        vertCos = vertCos * vertCos * Math.min(1.0, look.length() / 0.4);
        Vec3 v = old.add(0.0, gravity * (-1.0 + vertCos * 0.75), 0.0);
        if (v.y < 0.0 && horizSqrt > 0.0) {
            double d = v.y * -0.1 * vertCos;
            v = v.add(look.x * d / horizSqrt, d, look.z * d / horizSqrt);
        }

        if (pitchRad < 0.0F && horizSqrt > 0.0) {
            double d = horizLen * -Mth.sin(pitchRad) * 0.04;
            v = v.add(-look.x * d / horizSqrt, d * 3.2, -look.z * d / horizSqrt);
        }

        if (horizSqrt > 0.0) {
            v = v.add((look.x / horizSqrt * horizLen - v.x) * 0.1, 0.0, (look.z / horizSqrt * horizLen - v.z) * 0.1);
        }

        return v.multiply(0.99F, 0.98F, 0.99F);
    }

    private double effectiveGravity(double velocityY) {
        double gravity = this.mc.player.getAttributeValue(Attributes.GRAVITY);
        return velocityY <= 0.0 && this.mc.player.hasEffect(MobEffects.SLOW_FALLING) ? Math.min(gravity, 0.01) : gravity;
    }

    private double throttledSpeed(Vec3 look) {
        double capped = this.speed.get();
        int ticks = this.lookahead.get();
        double reach = Math.max(4.0, capped * ticks);
        Vec3 eye = this.mc.player.getEyePosition();
        this.limiter = "speed";
        if (this.wallCheck.get()) {
            Vec3 end = eye.add(look.scale(reach));
            HitResult hit = this.mc.level.clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, this.mc.player));
            if (hit.getType() != Type.MISS) {
                double limit = hit.getLocation().distanceTo(eye) / ticks;
                if (limit < capped) {
                    capped = limit;
                    this.limiter = "wall ahead";
                }
            }
        }

        if (this.chunkCheck.get()) {
            for (double d = 16.0; d <= reach; d += 16.0) {
                Vec3 at = eye.add(look.scale(d));
                BlockPos atPos = BlockPos.containing(at);
                if (!this.mc.level.getChunkSource().hasChunk(atPos.getX() >> 4, atPos.getZ() >> 4)) {
                    if (d / ticks < capped) {
                        capped = d / ticks;
                        this.limiter = "unloaded chunks";
                    }
                    break;
                }
            }
        }

        return capped;
    }

    private boolean allowed() {
        if (this.suppressTicks > 0) {
            return false;
        } else if (!this.requireInput.get()) {
            return true;
        } else if (this.travellingTicks > 0) {
            return true;
        } else {
            return this.baritoneSync.get() && this.baritoneFlying()
                ? true
                : this.mc.options.keyUp.isDown()
                    || this.mc.options.keyDown.isDown()
                    || this.mc.options.keyLeft.isDown()
                    || this.mc.options.keyRight.isDown()
                    || this.mc.options.keyJump.isDown()
                    || this.mc.options.keyShift.isDown();
        }
    }

    public void suppress() {
        this.suppressTicks = 3;
    }

    public void clearSuppression() {
        this.suppressTicks = 0;
    }

    public void declareTravelling() {
        this.travellingTicks = 3;
    }

    private boolean baritoneFlying() {
        if (!BARITONE) {
            return false;
        }

        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private double effectiveAlignment() {
        return this.baritoneSync.get() && this.baritoneFlying() ? 0.0 : this.alignment.get();
    }

    public double alignmentDegrees() {
        return this.effectiveAlignment();
    }

    public double windowThreshold() {
        return this.amount.get();
    }

    public boolean hasWindow() {
        return this.isActive() && this.windowOpen;
    }

    private FireworkRocketEntity findAttachedRocket() {
        FireworkRocketEntity best = null;
        int bestRemaining = Integer.MIN_VALUE;

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof FireworkRocketEntity firework
                && firework.isAlive()
                && ((FireworkRocketEntityAccessor)firework).getAttachedToEntity() == this.mc.player) {
                int remaining = this.remainingLife(firework);
                if (remaining > bestRemaining) {
                    bestRemaining = remaining;
                    best = firework;
                }
            }
        }

        return best;
    }

    private int remainingLife(FireworkRocketEntity firework) {
        FireworkRocketEntityAccessor accessor = (FireworkRocketEntityAccessor)firework;
        return accessor.getLifetime() - accessor.getLife();
    }
}
