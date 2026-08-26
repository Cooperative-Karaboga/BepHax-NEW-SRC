package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.ViaProtocolUtil;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

public class GrimGlide extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgEnvelope = this.settings.createGroup("Envelope");
    private final SettingGroup sgReport = this.settings.createGroup("Report");
    private final SettingGroup sgTest = this.settings.createGroup("Test");
    private final Setting<GrimGlide.Mode> mode = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("mode"))
                        .description("Envelope flies the uncertainty Grim grants gliders. Report is the issue's original code, kept to compare against."))
                    .defaultValue(GrimGlide.Mode.Envelope))
                .build()
        );
    private final Setting<GrimGlide.Protocol> protocol = this.sgEnvelope
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("protocol"))
                            .description("Which client version Grim thinks you are - the whole budget comes off it. Auto reads the ViaFabricPlus target."))
                        .defaultValue(GrimGlide.Protocol.Auto))
                    .visible(() -> this.mode.get() == GrimGlide.Mode.Envelope))
                .build()
        );
    private final Setting<Double> safety = this.sgEnvelope
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("safety")
                .description("Fraction of the granted budget to actually use. Sitting exactly on the boundary flags on any rounding or lag spike.")
                .defaultValue(0.85)
                .range(0.1, 1.0)
                .sliderRange(0.5, 1.0)
                .visible(() -> this.mode.get() == GrimGlide.Mode.Envelope)
                .build()
        );
    private final Setting<Double> climb = this.sgEnvelope
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("climb")
                .description(
                    "Blocks per tick of vertical travel. 0 is a level hover. The ceiling is the vertical budget minus gravity, and the module clamps to it."
                )
                .defaultValue(0.0)
                .sliderRange(-0.1, 0.1)
                .visible(() -> this.mode.get() == GrimGlide.Mode.Envelope)
                .build()
        );
    private final Setting<Boolean> steer = this.sgEnvelope
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("steer")
                .description("Only push forward while a movement key is held. Off holds the yaw direction constantly.")
                .defaultValue(true)
                .visible(() -> this.mode.get() == GrimGlide.Mode.Envelope)
                .build()
        );
    private final Setting<Double> forward = this.sgReport
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("forward")
                .description("Horizontal velocity written every tick, along the yaw. 0.087 in the report.")
                .defaultValue(0.087)
                .min(0.0)
                .sliderRange(0.0, 1.0)
                .visible(() -> this.mode.get() == GrimGlide.Mode.Report)
                .build()
        );
    private final Setting<Boolean> randomize = this.sgReport
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("randomize")
                .description("Scales the written velocity by a random factor each write, as the report does.")
                .defaultValue(true)
                .visible(() -> this.mode.get() == GrimGlide.Mode.Report)
                .build()
        );
    private final Setting<Double> randomMin = this.sgReport
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("random-min")
                .description("Lower bound of the random factor.")
                .defaultValue(1.1)
                .min(0.0)
                .sliderRange(0.5, 2.0)
                .visible(() -> this.mode.get() == GrimGlide.Mode.Report && this.randomize.get())
                .build()
        );
    private final Setting<Double> randomMax = this.sgReport
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("random-max")
                .description("Upper bound of the random factor.")
                .defaultValue(1.21)
                .min(0.0)
                .sliderRange(0.5, 2.0)
                .visible(() -> this.mode.get() == GrimGlide.Mode.Report && this.randomize.get())
                .build()
        );
    private final Setting<Double> fall = this.sgReport
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("fall")
                .description("Subtracted from y on the first velocity write.")
                .defaultValue(0.02)
                .sliderRange(0.0, 0.1)
                .visible(() -> this.mode.get() == GrimGlide.Mode.Report)
                .build()
        );
    private final Setting<Double> lift = this.sgReport
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("lift")
                .description("Added back to y on the second velocity write. Net vertical drift is lift - fall, which loses to gravity.")
                .defaultValue(0.016)
                .sliderRange(0.0, 0.1)
                .visible(() -> this.mode.get() == GrimGlide.Mode.Report)
                .build()
        );
    private final Setting<Double> speedLimit = this.sgReport
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("speed-limit")
                .description("Blocks per second at which the forward push is dropped to zero. 48 (overworld) / 52 in the report.")
                .defaultValue(48.0)
                .min(0.0)
                .sliderRange(0.0, 120.0)
                .visible(() -> this.mode.get() == GrimGlide.Mode.Report)
                .build()
        );
    private final Setting<Boolean> teleport = this.sgReport
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("teleport")
                .description("Also shifts the client position forward, on top of the velocity write. The report's 50ms timer is one tick.")
                .defaultValue(true)
                .visible(() -> this.mode.get() == GrimGlide.Mode.Report)
                .build()
        );
    private final Setting<Boolean> reportSetbacks = this.sgTest
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("report-setbacks")
                .description("Chat-logs every server teleport while active - a setback is how a Grim flag shows up client-side.")
                .defaultValue(true)
                .build()
        );
    private static final double GRAVITY = 0.08;
    private int setbacks;
    private double bps;
    private Vec3 lastPos;

    public GrimGlide() {
        super(Bep.CATEGORY, "grim-glide", "Elytra hover riding Grim's own gliding uncertainty. Needs a pre-1.18.2 protocol to have any budget at all.");
    }

    @Override
    public void onActivate() {
        this.setbacks = 0;
        this.bps = 0.0;
        this.lastPos = this.mc.player == null ? null : this.mc.player.position();
        if (this.mode.get() == GrimGlide.Mode.Envelope) {
            this.reportEnvelope();
        }
    }

    private void reportEnvelope() {
        int detected = ViaProtocolUtil.targetProtocol();
        if (this.protocol.get() == GrimGlide.Protocol.Auto) {
            if (!ViaProtocolUtil.isPresent()) {
                this.warning("ViaFabricPlus not loaded - assuming native protocol, which leaves no usable budget.");
            } else if (detected == -1) {
                this.warning("Could not read the ViaFabricPlus target (auto-detect?) - assuming native. Set protocol by hand.");
            } else {
                this.info("Via target %s (protocol %d).", ViaProtocolUtil.targetName(), detected);
            }
        } else if (detected != -1 && this.isLegacy() != ViaProtocolUtil.isLegacyBand(detected)) {
            this.warning("Protocol is forced to %s but Via is on %s - the budget will be wrong.", this.protocol.get(), ViaProtocolUtil.targetName());
        }

        if (!this.isLegacy()) {
            this.warning(
                "Protocol reports 1.18.2+: Grim's threshold is 0.0002, so the budget is %.4f b/s and below its own 0.001 flag line. Drop to a 1.9-1.18.1 target.",
                this.horizontalBudget() * 20.0
            );
        } else {
            this.info("Envelope live: %.2f b/s horizontal, %.3f blocks/tick vertical.", this.horizontalBudget() * 20.0, this.verticalBudget());
        }
    }

    private boolean isLegacy() {
        if (this.protocol.get() == GrimGlide.Protocol.Legacy) {
            return true;
        } else {
            return this.protocol.get() == GrimGlide.Protocol.Modern ? false : ViaProtocolUtil.isLegacyBand(ViaProtocolUtil.targetProtocol());
        }
    }

    private double threshold() {
        return this.isLegacy() ? 0.03 : 2.0E-4;
    }

    private double horizontalBudget() {
        double t = this.threshold();
        return (0.99 * (t * 2.0) + t) * this.safety.get();
    }

    private double verticalBudget() {
        return this.threshold() * 2.0 * this.safety.get();
    }

    private double gravity() {
        double cos = Math.cos(Math.toRadians(this.mc.player.getXRot()));
        double vertCosRotation = cos * cos;
        return 0.08 * (-1.0 + vertCosRotation * 0.75);
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.lastPos != null) {
                Vec3 delta = this.mc.player.position().subtract(this.lastPos);
                this.bps = Math.sqrt(delta.x * delta.x + delta.z * delta.z) * 20.0;
            }

            this.lastPos = this.mc.player.position();
            if (this.mc.player.isFallFlying()) {
                if (this.mode.get() == GrimGlide.Mode.Report) {
                    this.tickReport();
                } else {
                    this.mc.player.setDeltaMovement(this.envelope());
                }
            }
        }
    }

    @EventHandler(priority = 100)
    private void onPlayerMove(PlayerMoveEvent event) {
        if (this.mode.get() == GrimGlide.Mode.Envelope && this.mc.player != null && this.mc.level != null) {
            if (event.type == MoverType.SELF && this.mc.player.isFallFlying()) {
                Vec3 target = this.envelope();
                ((IVec3d)event.movement).meteor$set(target.x, target.y, target.z);
            }
        }
    }

    private Vec3 envelope() {
        double h = this.horizontalBudget();
        double v = this.verticalBudget();
        double gravity = this.gravity();
        double y = Math.max(gravity - v, Math.min(gravity + v, this.climb.get()));
        if (this.steer.get() && !this.isMoveKeyDown()) {
            return new Vec3(0.0, y, 0.0);
        }

        double yawRad = Math.toRadians(this.mc.player.getYRot());
        return new Vec3(-Math.sin(yawRad) * h, y, Math.cos(yawRad) * h);
    }

    private boolean isMoveKeyDown() {
        return this.mc.options.keyUp.isDown()
            || this.mc.options.keyDown.isDown()
            || this.mc.options.keyLeft.isDown()
            || this.mc.options.keyRight.isDown();
    }

    private void tickReport() {
        double step = this.bps >= this.speedLimit.get() ? 0.0 : this.forward.get();
        double yawRad = Math.toRadians(this.mc.player.getYRot());
        double dx = -Math.sin(yawRad) * step;
        double dz = Math.cos(yawRad) * step;
        Vec3 velocity = this.mc.player.getDeltaMovement();
        this.mc.player.setDeltaMovement(dx * this.factor(), velocity.y - this.fall.get(), dz * this.factor());
        if (this.teleport.get()) {
            this.mc.player.setPos(this.mc.player.getX() + dx, this.mc.player.getY(), this.mc.player.getZ() + dz);
        }

        this.mc.player.setDeltaMovement(dx * this.factor(), this.mc.player.getDeltaMovement().y + this.lift.get(), dz * this.factor());
    }

    @EventHandler(priority = -200)
    private void onPacketReceive(Receive event) {
        if (this.reportSetbacks.get() && this.mc.player != null) {
            if (event.packet instanceof ClientboundPlayerPositionPacket packet) {
                Vec3 var7 = packet.change().position();
                Vec3 current = this.mc.player.position();
                Set relatives = packet.relatives();
                Vec3 target = new Vec3(
                    relatives.contains(Relative.X) ? current.x + var7.x : var7.x,
                    relatives.contains(Relative.Y) ? current.y + var7.y : var7.y,
                    relatives.contains(Relative.Z) ? current.z + var7.z : var7.z
                );
                this.warning("Setback #%d - pulled back %.2f blocks.", ++this.setbacks, target.distanceTo(current));
            }
        }
    }

    private double factor() {
        if (!this.randomize.get()) {
            return 1.0;
        }

        double min = Math.min(this.randomMin.get(), this.randomMax.get());
        double max = Math.max(this.randomMin.get(), this.randomMax.get());
        return min == max ? min : ThreadLocalRandom.current().nextDouble(min, max);
    }

    @Override
    public String getInfoString() {
        return this.mode.get() == GrimGlide.Mode.Report
            ? String.format("report | %.1f b/s | %d setbacks", this.bps, this.setbacks)
            : String.format("%.2f b/s cap | %d setbacks", this.horizontalBudget() * 20.0, this.setbacks);
    }

    public enum Mode {
        Envelope("Envelope"),
        Report("Report");

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
        }
    }

    public enum Protocol {
        Auto("Auto"),
        Legacy("Legacy"),
        Modern("Modern");

        private final String title;

        Protocol(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
        }
    }
}
