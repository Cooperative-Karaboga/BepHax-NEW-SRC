package bep.hax.util;

import bep.hax.config.BepConfig;
import java.util.Random;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public class RotationUtils {
    private static RotationUtils INSTANCE;
    public static final double DEFAULT_TURN_SPEED = 45.0;
    public static final double DEFAULT_ALIGN_EPS = 2.0;
    public static final int DEFAULT_DECAY_TICKS = 4;
    public static final double DECAY_TURN_SPEED = 80.0;
    private float serverYaw = 0.0F;
    private float serverPitch = 0.0F;
    private Float finalYaw = null;
    private Float finalPitch = null;
    private float currentYaw = 0.0F;
    private float currentPitch = 0.0F;
    private double turnSpeed = 45.0;
    private boolean syncMovement = false;
    private static final float WIRE_NUDGE = 0.01F;
    private int wireFreshTicks = 0;
    private float wireNudge = 0.0F;
    private int keepTicks = 0;
    private boolean decaying = false;
    private int decayTicks = 0;
    private Integer offsetK = null;
    private float offsetPitch = 0.0F;
    private int offsetKeepTicks = 0;
    private boolean direct;
    private int cachedInputSteps = 0;
    private boolean inputStepsValid = false;
    private boolean initialized = false;
    private Object owner;
    private int ownerPriority;
    private final float quantum;
    private final Random random = new Random();
    private static final boolean[][] INPUT_DIRS = new boolean[][]{
        {true, false, false, false},
        {true, false, false, true},
        {false, false, false, true},
        {false, true, false, true},
        {false, true, false, false},
        {false, true, true, false},
        {false, false, true, false},
        {true, false, true, false}
    };

    private RotationUtils() {
        float sens = 0.35F + this.random.nextFloat() * 0.3F;
        float f = sens * 0.6F + 0.2F;
        this.quantum = f * f * f * 8.0F * 0.15F;
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static RotationUtils getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RotationUtils();
        }

        return INSTANCE;
    }

    @EventHandler(priority = -200)
    public void onPacketSend(Send event) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            if (event.packet instanceof ServerboundMovePlayerPacket packet && packet.hasRotation()) {
                this.serverYaw = packet.getYRot(this.serverYaw);
                this.serverPitch = packet.getXRot(this.serverPitch);
            }
        }
    }

    @EventHandler(priority = 200)
    public void onPacketReceive(Receive event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket packet) {
            PositionMoveRotation change = packet.change();
            Set<Relative> relatives = packet.relatives();
            this.serverYaw = relatives.contains(Relative.Y_ROT) ? this.serverYaw + change.yRot() : change.yRot();
            this.serverPitch = relatives.contains(Relative.X_ROT) ? this.serverPitch + change.xRot() : change.xRot();
            this.currentYaw = this.serverYaw;
            this.currentPitch = this.serverPitch;
            this.clearAll();
        }
    }

    @EventHandler(priority = 200)
    public void onTickPre(Pre event) {
        this.inputStepsValid = false;
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            if (this.wireFreshTicks > 0) {
                this.wireFreshTicks--;
                this.wireNudge = this.wireNudge > 0.0F ? -0.01F : 0.01F;
            } else {
                this.wireNudge = 0.0F;
            }

            if (!this.initialized) {
                this.serverYaw = MeteorClient.mc.player.getYRot();
                this.serverPitch = MeteorClient.mc.player.getXRot();
                this.currentYaw = this.serverYaw;
                this.currentPitch = this.serverPitch;
                this.initialized = true;
            }

            if (this.finalYaw != null || this.finalPitch != null) {
                if (this.syncMovement
                    || !MeteorClient.mc.player.isFallFlying() && !MeteorClient.mc.player.isSwimming() && !MeteorClient.mc.player.isPassenger()) {
                    if (this.decaying) {
                        this.finalYaw = MeteorClient.mc.player.getYRot();
                        this.finalPitch = MeteorClient.mc.player.getXRot();
                    }

                    boolean ease = !this.direct || this.decaying;
                    double maxStep = this.decaying ? Math.min(this.turnSpeed, 80.0) : this.turnSpeed;
                    double frac = 0.55 + 0.3 * this.random.nextDouble();
                    double yawRemaining = Mth.wrapDegrees(this.finalYaw - this.currentYaw);
                    double yawStep = Mth.clamp(ease ? yawRemaining * frac : yawRemaining, -maxStep, maxStep);
                    int yawDots = (int)Math.round(yawStep / this.quantum);
                    if (Math.abs(yawDots) >= 2) {
                        yawDots += this.random.nextInt(3) - 1;
                    }

                    this.currentYaw = this.currentYaw + yawDots * this.quantum;
                    double pitchRemaining = this.finalPitch - this.currentPitch;
                    double pitchStep = Mth.clamp(ease ? pitchRemaining * frac : pitchRemaining, -maxStep, maxStep);
                    int pitchDots = (int)Math.round(pitchStep / this.quantum);
                    if (Math.abs(pitchDots) >= 2) {
                        pitchDots += this.random.nextInt(3) - 1;
                    }

                    this.currentPitch = Mth.clamp(this.currentPitch + pitchDots * this.quantum, -90.0F, 90.0F);
                } else {
                    this.clearAll();
                }
            }
        }
    }

    @EventHandler(priority = -200)
    public void onTickPost(Post event) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            if (this.offsetK != null && this.offsetKeepTicks-- <= 0) {
                this.offsetK = null;
            }

            if (this.finalYaw == null && this.finalPitch == null) {
                if (this.offsetK == null) {
                    this.owner = null;
                    this.ownerPriority = 0;
                }
            } else if (this.decaying) {
                if (this.decayTicks-- <= 0 || this.alignedNow(2.0)) {
                    this.clearAll();
                }
            } else if (this.alignedNow(2.0)) {
                if (this.keepTicks > 0) {
                    this.keepTicks--;
                } else {
                    this.decaying = true;
                    this.syncMovement = false;
                    this.decayTicks = 4;
                    this.owner = null;
                    this.ownerPriority = 0;
                }
            }
        }
    }

    private boolean claim(Object owner, int priority) {
        if (this.owner != null && this.owner != owner && !this.decaying && priority <= this.ownerPriority) {
            return false;
        }

        this.owner = owner;
        this.ownerPriority = owner == null ? 0 : priority;
        this.inputStepsValid = false;
        this.direct = false;
        return true;
    }

    public boolean isOwner(Object owner) {
        return this.owner == owner;
    }

    public void release(Object owner) {
        if (this.owner == owner) {
            this.owner = null;
            this.ownerPriority = 0;
        }
    }

    public boolean setRotationSilent(float yaw, float pitch) {
        return this.setRotationSmooth(yaw, pitch, BepConfig.getRotationTurnSpeed(), false);
    }

    public boolean setRotationSilent(float yaw, float pitch, double turnSpeed) {
        return this.setRotationSmooth(yaw, pitch, turnSpeed, false);
    }

    public boolean setRotationSilent(Object owner, int priority, float yaw, float pitch) {
        return this.setRotationSmooth(owner, priority, yaw, pitch, BepConfig.getRotationTurnSpeed(), false);
    }

    public boolean setRotationSilent(Object owner, int priority, float yaw, float pitch, double turnSpeed) {
        return this.setRotationSmooth(owner, priority, yaw, pitch, turnSpeed, false);
    }

    public boolean setRotationSilentDirect(Object owner, int priority, float yaw, float pitch, double turnSpeed) {
        if (!this.setRotationSmooth(owner, priority, yaw, pitch, turnSpeed, false)) {
            return false;
        }

        this.direct = true;
        return true;
    }

    public boolean setRotationFull(float yaw, float pitch) {
        return this.setRotationSmooth(yaw, pitch, BepConfig.getRotationTurnSpeed(), true);
    }

    public boolean setRotationFull(float yaw, float pitch, double turnSpeed) {
        return this.setRotationSmooth(yaw, pitch, turnSpeed, true);
    }

    public boolean setRotationFull(Object owner, int priority, float yaw, float pitch, double turnSpeed) {
        return this.setRotationSmooth(owner, priority, yaw, pitch, turnSpeed, true);
    }

    public boolean setRotationSilentInstant(Object owner, int priority, float yaw, float pitch) {
        if (!this.claim(owner, priority)) {
            return false;
        }

        this.finalYaw = yaw;
        this.finalPitch = Mth.clamp(pitch, -90.0F, 90.0F);
        this.currentYaw = yaw;
        this.currentPitch = this.finalPitch;
        this.turnSpeed = 45.0;
        this.syncMovement = false;
        this.keepTicks = 2;
        this.decaying = false;
        this.offsetK = null;
        this.offsetKeepTicks = 0;
        return true;
    }

    public boolean setRotationFullInstant(float yaw, float pitch) {
        if (!this.claim(null, 0)) {
            return false;
        }

        this.finalYaw = yaw;
        this.finalPitch = pitch;
        this.currentYaw = yaw;
        this.currentPitch = Mth.clamp(pitch, -90.0F, 90.0F);
        this.turnSpeed = 45.0;
        this.syncMovement = true;
        this.keepTicks = 2;
        this.decaying = false;
        this.offsetK = null;
        this.offsetKeepTicks = 0;
        return true;
    }

    public boolean setRotationSmooth(float yaw, float pitch, double turnSpeed, boolean sync) {
        return this.setRotationSmooth(null, 0, yaw, pitch, turnSpeed, sync);
    }

    public boolean setRotationSmooth(Object owner, int priority, float yaw, float pitch, double turnSpeed, boolean sync) {
        if (!this.claim(owner, priority)) {
            return false;
        }

        if (this.finalYaw == null && this.finalPitch == null) {
            this.currentYaw = this.serverYaw;
            this.currentPitch = this.serverPitch;
        }

        this.finalYaw = yaw;
        this.finalPitch = pitch;
        this.turnSpeed = turnSpeed;
        this.syncMovement = sync;
        this.keepTicks = 2;
        this.decaying = false;
        this.offsetK = null;
        this.offsetKeepTicks = 0;
        return true;
    }

    public boolean setRotationOffset(int k45, float pitch) {
        return this.setRotationOffset(null, 0, k45, pitch);
    }

    public boolean setRotationOffset(Object owner, int priority, int k45, float pitch) {
        if (!this.claim(owner, priority)) {
            return false;
        }

        this.offsetK = k45;
        this.offsetPitch = Mth.clamp(pitch, -90.0F, 90.0F);
        this.offsetKeepTicks = 2;
        this.finalYaw = null;
        this.finalPitch = null;
        this.syncMovement = false;
        this.keepTicks = 0;
        this.decaying = false;
        this.decayTicks = 0;
        return true;
    }

    public boolean isOffsetRotation() {
        return this.offsetK != null;
    }

    public void setRotationSilentSync() {
        if (MeteorClient.mc.player != null) {
            this.setRotationSilent(MeteorClient.mc.player.getYRot(), MeteorClient.mc.player.getXRot());
        }
    }

    public void clearRotations() {
        this.clearRotations(null);
    }

    public void clearRotations(Object owner) {
        if (this.owner == null || this.owner == owner) {
            this.clearAll();
        }
    }

    private void clearAll() {
        this.finalYaw = null;
        this.finalPitch = null;
        this.syncMovement = false;
        this.keepTicks = 0;
        this.decaying = false;
        this.decayTicks = 0;
        this.offsetK = null;
        this.offsetKeepTicks = 0;
        this.owner = null;
        this.ownerPriority = 0;
        this.inputStepsValid = false;
        this.direct = false;
    }

    public boolean isRotating() {
        return this.finalYaw != null || this.finalPitch != null || this.offsetK != null;
    }

    public float getRotationYaw() {
        if (this.offsetK != null && MeteorClient.mc.player != null) {
            return MeteorClient.mc.player.getYRot() + this.offsetK.intValue() * 45.0F;
        } else {
            return this.finalYaw == null && this.finalPitch == null
                ? (MeteorClient.mc.player != null ? MeteorClient.mc.player.getYRot() : 0.0F)
                : this.currentYaw;
        }
    }

    public float getRotationPitch() {
        if (this.offsetK != null) {
            return this.offsetPitch;
        } else {
            return this.finalYaw == null && this.finalPitch == null
                ? (MeteorClient.mc.player != null ? MeteorClient.mc.player.getXRot() : 0.0F)
                : this.currentPitch;
        }
    }

    public float getSentYaw() {
        return this.getRotationYaw() + this.wireNudge;
    }

    public float getSentPitch() {
        return this.getRotationPitch();
    }

    public void keepWireFresh() {
        this.wireFreshTicks = 2;
    }

    public boolean isWireFresh() {
        return this.wireNudge != 0.0F;
    }

    public boolean isAligned(double eps) {
        return this.owner != null ? false : this.alignedNow(eps);
    }

    public boolean isAligned() {
        return this.isAligned(2.0);
    }

    public boolean isAlignedFor(Object owner, double eps) {
        return this.owner != owner ? false : this.alignedNow(eps);
    }

    public boolean isAlignedFor(Object owner) {
        return this.isAlignedFor(owner, 2.0);
    }

    private boolean alignedNow(double eps) {
        if (this.offsetK != null) {
            return true;
        }

        if (!this.isRotating()) {
            return true;
        }

        double dy = this.finalYaw == null ? 0.0 : Math.abs(Mth.wrapDegrees(this.finalYaw - this.currentYaw));
        double dp = this.finalPitch == null ? 0.0 : Math.abs(this.finalPitch - this.currentPitch);
        return dy <= eps && dp <= eps;
    }

    public Float getMovementPitch() {
        return this.syncMovement && this.isRotating() ? this.currentPitch : null;
    }

    public Float getMovementYaw() {
        return this.syncMovement && this.isRotating() ? this.currentYaw : null;
    }

    public int getInputSteps() {
        if (!this.inputStepsValid) {
            this.cachedInputSteps = this.computeInputSteps();
            this.inputStepsValid = true;
        }

        return this.cachedInputSteps;
    }

    private int computeInputSteps() {
        if (MeteorClient.mc.player == null) {
            return 0;
        }

        if (this.offsetK != null) {
            return -this.offsetK;
        }

        if (this.finalYaw == null && this.finalPitch == null) {
            return 0;
        }

        if (this.syncMovement) {
            return 0;
        }

        float delta = Mth.wrapDegrees(MeteorClient.mc.player.getYRot() - this.currentYaw);
        boolean scaled = MeteorClient.mc.player.isUsingItem() || MeteorClient.mc.player.isCrouching() || MeteorClient.mc.player.isVisuallyCrawling();
        return scaled ? 2 * Math.round(delta / 90.0F) : Math.round(delta / 45.0F);
    }

    public Float getMoveYaw() {
        if (MeteorClient.mc.player == null || !this.isRotating()) {
            return null;
        } else if (this.offsetK != null) {
            return null;
        } else {
            return this.syncMovement ? this.currentYaw : this.currentYaw + this.getInputSteps() * 45.0F;
        }
    }

    public Input rotateDeclaredInput(Input in) {
        int k = this.getInputSteps();
        if (k == 0) {
            return in;
        }

        int s = (in.left() ? 1 : 0) - (in.right() ? 1 : 0);
        int f = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
        if (s == 0 && f == 0) {
            return in;
        }

        int idx = 0;

        for (int i = 0; i < 8; i++) {
            int ds = (INPUT_DIRS[i][2] ? 1 : 0) - (INPUT_DIRS[i][3] ? 1 : 0);
            int df = (INPUT_DIRS[i][0] ? 1 : 0) - (INPUT_DIRS[i][1] ? 1 : 0);
            if (ds == s && df == f) {
                idx = i;
                break;
            }
        }

        boolean[] d = INPUT_DIRS[Math.floorMod(idx + k, 8)];
        return new Input(d[0], d[1], d[2], d[3], in.jump(), in.shift(), in.sprint());
    }

    public float getMoveSpeedScale() {
        if (MeteorClient.mc.player == null || this.offsetK != null || this.syncMovement) {
            return 1.0F;
        }

        if (this.finalYaw == null && this.finalPitch == null) {
            return 1.0F;
        }

        int k = this.getInputSteps();
        if ((k & 1) == 0) {
            return 1.0F;
        }

        Input in = MeteorClient.mc.player.input.keyPresses;
        int s = (in.left() ? 1 : 0) - (in.right() ? 1 : 0);
        int f = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
        if (s == 0 && f == 0) {
            return 1.0F;
        }

        boolean realDiagonal = s != 0 && f != 0;
        return realDiagonal ? 0.98F : 1.0204082F;
    }

    public boolean isSprintBlocked() {
        if (MeteorClient.mc.player == null || this.offsetK != null || this.syncMovement) {
            return false;
        } else if (this.finalYaw == null && this.finalPitch == null) {
            return false;
        } else {
            return this.getInputSteps() == 0 ? false : !this.rotateDeclaredInput(MeteorClient.mc.player.input.keyPresses).forward();
        }
    }

    public float getServerYaw() {
        return this.serverYaw;
    }

    public float getServerPitch() {
        return this.serverPitch;
    }

    public float getWrappedYaw() {
        return Mth.wrapDegrees(this.serverYaw);
    }

    public static float[] getRotationsTo(Vec3 src, Vec3 dest) {
        double diffX = dest.x - src.x;
        double diffY = dest.y - src.y;
        double diffZ = dest.z - src.z;
        double yaw = Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0;
        double pitch = -Math.toDegrees(Math.atan2(diffY, Math.hypot(diffX, diffZ)));
        return new float[]{Mth.wrapDegrees((float)yaw), Mth.wrapDegrees((float)pitch)};
    }

    public static Vec3 getRotationVector(float pitch, float yaw) {
        float pitchRad = pitch * (float) (Math.PI / 180.0);
        float yawRad = -yaw * (float) (Math.PI / 180.0);
        float cosPitch = Mth.cos(pitchRad);
        float sinPitch = Mth.sin(pitchRad);
        float cosYaw = Mth.cos(yawRad);
        float sinYaw = Mth.sin(yawRad);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    public enum RotationMode {
        SILENT,
        SYNC,
        LOCK;
    }
}
