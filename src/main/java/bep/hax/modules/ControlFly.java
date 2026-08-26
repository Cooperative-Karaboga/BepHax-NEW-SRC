package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.mixin.accessor.FireworkRocketEntityAccessor;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.PorpoiseSolver;
import bep.hax.util.RotationUtils;
import java.util.concurrent.atomic.AtomicInteger;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.ParticleEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class ControlFly extends Module {
    public static ControlFly INSTANCE;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgFirework = this.settings.createGroup("Firework");
    private final Setting<Boolean> autoFirework = this.sgGeneral
        .add(new Builder().name("auto-firework").description("Automatically use fireworks to maintain flight.").defaultValue(true).build());
    private final Setting<ControlFly.HoverMode> hoverMode = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("hover-mode"))
                        .description(
                            "Idle hover style. FREEZE brakes with vanilla physics, then parks you dead still - no drift, no wobble, camera free - by pinning the client and thinning movement packets to one every packet-gap ticks, and it spends no rockets doing it: whatever is already lit is ridden out, nothing new is thrown until you move again. Without a lit firework the server pushes back on the parked position; the park adopts each pushback as its new anchor, which is what keeps you on the spot. FLIP_FLOP is the old rocket-fed altitude hold with alternating yaw."
                        ))
                    .defaultValue(ControlFly.HoverMode.FREEZE))
                .build()
        );
    private final Setting<Integer> packetGap = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("packet-gap")
                .description(
                    "Ticks between movement packets let through while freeze-hovering. The sparse cadence reads as network lag; the client physics stay vanilla so the catch-up packet always simulates clean."
                )
                .defaultValue(20)
                .min(5)
                .max(60)
                .sliderRange(5, 40)
                .visible(() -> this.hoverMode.get() == ControlFly.HoverMode.FREEZE)
                .build()
        );
    private final Setting<Double> hoverPitch = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("hover-pitch")
                .description("Pitch angle when idle. Negative values angle upward to maintain altitude.")
                .defaultValue(-1.25)
                .min(-10.0)
                .max(10.0)
                .sliderRange(-10.0, 10.0)
                .visible(() -> this.hoverMode.get() == ControlFly.HoverMode.FLIP_FLOP)
                .build()
        );
    private final Setting<Double> upAngle = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("up-angle")
                .description(
                    "Climb angle in degrees while holding jump with a direction key, including over a porpoise swing. 30-60 keeps both axes of the firework window saturated while boosting; 45 is the robust middle. Jump alone still climbs straight up."
                )
                .defaultValue(45.0)
                .min(0.0)
                .max(90.0)
                .sliderRange(0.0, 90.0)
                .build()
        );
    private final Setting<Double> downAngle = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("down-angle")
                .description(
                    "Dive angle in degrees while holding sneak with a direction key, including over a porpoise swing. Shallower than the climb by default - a steep dive spends altitude faster than it buys distance. Sneak alone still drops straight down."
                )
                .defaultValue(20.0)
                .min(0.0)
                .max(90.0)
                .sliderRange(0.0, 90.0)
                .build()
        );
    private final Setting<Boolean> smoothCamera = this.sgGeneral
        .add(new Builder().name("smooth-camera").description("Smooth out camera jitter when hovering idle.").defaultValue(true).build());
    private final Setting<Boolean> hideRocketParticles = this.sgGeneral
        .add(
            new Builder()
                .name("hide-rocket-particles")
                .description("Hide firework rocket particles including launch and trail effects.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> fireworkDelay = this.sgFirework
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("firework-delay")
                .description("Base delay in seconds between firework uses.")
                .defaultValue(1.5)
                .min(0.5)
                .sliderRange(0.5, 5.0)
                .visible(this.autoFirework::get)
                .build()
        );
    private final Setting<Boolean> durationScaling = this.sgFirework
        .add(
            new Builder()
                .name("duration-scaling")
                .description("Scale firework delay based on firework flight duration.")
                .defaultValue(true)
                .visible(this.autoFirework::get)
                .build()
        );
    private final Setting<Double> safetyMargin = this.sgFirework
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("safety-margin")
                .description(
                    "Fires the next rocket this many seconds before the previous one runs out, so thrust never gaps to ping variation (Lambda's margin)."
                )
                .defaultValue(0.2)
                .min(0.0)
                .max(1.0)
                .sliderRange(0.0, 0.5)
                .visible(() -> this.autoFirework.get() && this.durationScaling.get())
                .build()
        );
    private final Setting<Boolean> useBoost = this.sgFirework
        .add(
            new Builder()
                .name("firework-boost")
                .description(
                    "Ride the firework window while flying. Level flight is heading-dependent - the window is per axis, so it pays when travel is split across two axes and is worth nothing straight down one, where the boost stands aside rather than cost you speed (171 km/h on a 45 degree diagonal against 120 without it). Climbing and diving is not heading-dependent and is worth far more: hold a dive and the elytra's fall-to-forward term grows the movement the server predicts, which is what lets an axis pass the window's own edge - about 78 b/s on any heading on the default 24-block swing, against 34 level, and 90 b/s on a 60-block one. The rockets stay ControlFly's own - the boost only rides whatever is lit."
                )
                .defaultValue(false)
                .onChanged(val -> {
                    if (this.isActive()) {
                        if (val) {
                            this.enableBoost();
                        } else {
                            this.disableBoost();
                        }
                    }
                })
                .build()
        );
    private final Setting<Boolean> porpoise = this.sgFirework
        .add(
            new Builder()
                .name("porpoise")
                .description(
                    "Steer the pitch for you while travelling, alternating climb and dive legs, instead of flying the flat heading your keys describe. This is where the window actually pays: level flight only settles each axis at the box edge (~1.68 b/t) and needs a diagonal to split speed across two of them, while a dive grows the movement the server itself predicts and pays on any heading. The leg angle is solved by flying the legs with the boost's own ride policy, so it tracks porpoise-height, your heading and the boost's alignment together - about 16 degrees when alignment is 0, 25 at 15. Jump and sneak bias the legs up or down; releasing them hands the swing back."
                )
                .defaultValue(true)
                .visible(this.useBoost::get)
                .build()
        );
    private final Setting<Integer> porpoiseHeight = this.sgFirework
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("porpoise-height")
                .description(
                    "Total height of the climb-and-dive swing, in blocks, centred on the altitude you were at when travel began. Longer legs are faster - fewer ticks are lost to turnarounds - and are bounded by your terrain and your nerve."
                )
                .defaultValue(24)
                .min(4)
                .max(120)
                .sliderRange(8, 60)
                .visible(() -> this.useBoost.get() && this.porpoise.get())
                .build()
        );
    private final Setting<Boolean> asymmetricLegs = this.sgFirework
        .add(
            new Builder()
                .name("asymmetric-legs")
                .description(
                    "Solve the climb and the dive as separate angles instead of one magnitude flown both ways. They are not the same manoeuvre - the elytra's fall-to-forward term only pays while you are descending - so the fastest swing rarely uses one angle for both. Worth about 1.3% at the default alignment and up to 12% at alignment 0, where the climb wants to be far steeper than the dive. It cannot cost speed: the one-angle answer is the starting point and only a leg pair that beats it on a full re-score is used. Costs a longer solve on each new heading bucket."
                )
                .defaultValue(true)
                .visible(() -> this.useBoost.get() && this.porpoise.get())
                .build()
        );
    private boolean flipFlop = false;
    private double nextFireworkDelay = 1.5;
    private long lastFireworkTime = 0L;
    private int previousSlot = -1;
    private int swapBackTicks = 0;
    private int freezeGapTicks = 0;
    private boolean frozen = false;
    private Vec3 freezeAnchor = null;
    private final AtomicInteger passMovePackets = new AtomicInteger();
    private volatile boolean reanchor = false;
    private int recentSetbacks = 0;
    private int ticksSinceSetback = 0;
    private int reparkCooldown = 0;
    private boolean rocketTickFree = false;
    private boolean boostToggledByUs = false;
    private static final double LEG_LOOKAHEAD = 40.0;
    private static final double FIREWORK_TERMINAL_SPEED = 1.7;
    private boolean porpoising = false;
    private boolean porpoiseDescending = false;
    private int porpoiseFlipTicks = 0;
    private double porpoiseTarget = 0.0;
    private boolean idleHovering = false;
    private double prevActualX;
    private double prevActualZ;
    private double smoothX;
    private double smoothZ;
    private double prevSmoothX;
    private double prevSmoothZ;

    public ControlFly() {
        super(Bep.HUNT_CATEGORY, "control-fly", "GrimAC-compatible elytra flight control using WASD keys.");
        INSTANCE = this;
    }

    public boolean shouldHideRocketParticles() {
        return this.isActive() && this.hideRocketParticles.get();
    }

    public boolean isIdleHovering() {
        return this.isActive() && this.idleHovering && this.smoothCamera.get() && this.hoverMode.get() == ControlFly.HoverMode.FLIP_FLOP;
    }

    public double getSmoothCamX(float tickDelta) {
        return Mth.lerp(tickDelta, this.prevSmoothX, this.smoothX);
    }

    public double getSmoothCamZ(float tickDelta) {
        return Mth.lerp(tickDelta, this.prevSmoothZ, this.smoothZ);
    }

    @EventHandler
    private void onParticle(ParticleEvent event) {
        if (this.shouldHideRocketParticles() && event.particle.getType() == ParticleTypes.FIREWORK) {
            event.cancel();
        }
    }

    @Override
    public void onActivate() {
        this.flipFlop = false;
        this.idleHovering = false;
        this.nextFireworkDelay = this.fireworkDelay.get();
        this.lastFireworkTime = System.currentTimeMillis();
        this.previousSlot = -1;
        this.swapBackTicks = 0;
        this.freezeGapTicks = 0;
        this.frozen = false;
        this.freezeAnchor = null;
        this.reanchor = false;
        this.passMovePackets.set(0);
        this.recentSetbacks = 0;
        this.reparkCooldown = 0;
        this.rocketTickFree = false;
        this.porpoising = false;
        this.porpoiseFlipTicks = 0;
        if (this.useBoost.get()) {
            this.enableBoost();
        }
    }

    @Override
    public void onDeactivate() {
        this.idleHovering = false;
        this.frozen = false;
        this.freezeAnchor = null;
        this.reanchor = false;
        this.passMovePackets.set(0);
        this.recentSetbacks = 0;
        RotationUtils.getInstance().clearRotations();
        this.disableBoost();
    }

    private void enableBoost() {
        RocketBoost boost = Modules.get().get(RocketBoost.class);
        if (boost != null && !boost.isActive()) {
            boost.toggle();
            this.boostToggledByUs = true;
        }
    }

    private void disableBoost() {
        if (this.boostToggledByUs) {
            this.boostToggledByUs = false;
            RocketBoost boost = Modules.get().get(RocketBoost.class);
            if (boost != null && boost.isActive()) {
                boost.toggle();
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.mc.player.isFallFlying()) {
                if (this.hasAnyFirework() || this.frozen && this.hoverMode.get() == ControlFly.HoverMode.FREEZE) {
                    if (this.swapBackTicks > 0) {
                        this.swapBackTicks--;
                        if (this.swapBackTicks == 0 && this.previousSlot != -1) {
                            InvUtils.swap(this.previousSlot, false);
                            this.previousSlot = -1;
                        }
                    }

                    this.handleMovement();
                    boolean freezeParking = this.idleHovering && this.hoverMode.get() == ControlFly.HoverMode.FREEZE;
                    if (!freezeParking) {
                        this.frozen = false;
                        this.freezeAnchor = null;
                        this.reanchor = false;
                        this.passMovePackets.set(0);
                        this.recentSetbacks = 0;
                    } else if (this.frozen) {
                        if (this.reanchor) {
                            this.reanchor = false;
                            this.freezeAnchor = this.mc.player.position();
                            this.ticksSinceSetback = 0;
                            if (this.fireworkAttached() && ++this.recentSetbacks >= 3) {
                                this.frozen = false;
                                this.freezeAnchor = null;
                                this.recentSetbacks = 0;
                                this.reparkCooldown = 40;
                            }
                        } else if (this.recentSetbacks > 0 && ++this.ticksSinceSetback > 100) {
                            this.recentSetbacks = 0;
                        }

                        if (this.frozen) {
                            this.mc.player.setDeltaMovement(Vec3.ZERO);
                        }
                    }

                    if (this.idleHovering) {
                        this.suppressBoost();
                    }

                    this.rocketTickFree = false;
                    if (!freezeParking && this.autoFirework.get()) {
                        this.handleFirework();
                    }
                } else {
                    this.info("Out of firework rockets, disabling.");
                    this.toggle();
                }
            }
        }
    }

    @EventHandler
    private void onPacketSend(Send event) {
        if (event.packet instanceof ServerboundMovePlayerPacket packet) {
            if (this.mc.player != null && this.mc.player.isFallFlying()) {
                if (this.frozen && this.hoverMode.get() == ControlFly.HoverMode.FREEZE && this.idleHovering) {
                    if (this.passMovePackets.get() > 0) {
                        this.passMovePackets.decrementAndGet();
                    } else if (packet.hasPosition()) {
                        if (!this.rocketTickFree) {
                            if (!RotationUtils.getInstance().isRotating()) {
                                BepMine bepMine = Modules.get().get(BepMine.class);
                                if (bepMine == null || !bepMine.needsMiningTickPacket()) {
                                    if (++this.freezeGapTicks >= this.packetGap.get()) {
                                        this.freezeGapTicks = 0;
                                    } else {
                                        event.cancel();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = 200)
    private void onPacketReceive(Receive event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            if (this.frozen) {
                this.passMovePackets.incrementAndGet();
                this.reanchor = true;
            }
        }
    }

    private RocketBoost activeBoost() {
        if (!this.useBoost.get()) {
            return null;
        }

        RocketBoost boost = Modules.get().get(RocketBoost.class);
        return boost != null && boost.isActive() ? boost : null;
    }

    private void suppressBoost() {
        RocketBoost boost = Modules.get().get(RocketBoost.class);
        if (boost != null && boost.isActive()) {
            boost.suppress();
        }
    }

    private boolean fireworkAttached() {
        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof FireworkRocketEntity firework
                && firework.isAlive()
                && ((FireworkRocketEntityAccessor)firework).getAttachedToEntity() == this.mc.player) {
                return true;
            }
        }

        return false;
    }

    @EventHandler
    private void onTickPost(Post event) {
        if (this.mc.player != null && this.mc.player.isFallFlying() && this.idleHovering) {
            if (this.frozen && this.freezeAnchor != null) {
                if (!this.reanchor) {
                    this.mc.player.setPos(this.freezeAnchor);
                }

                this.mc.player.setDeltaMovement(Vec3.ZERO);
            } else if (this.hoverMode.get() == ControlFly.HoverMode.FLIP_FLOP) {
                this.prevSmoothX = this.smoothX;
                this.prevSmoothZ = this.smoothZ;
                double currentX = this.mc.player.getX();
                double currentZ = this.mc.player.getZ();
                this.smoothX = (currentX + this.prevActualX) * 0.5;
                this.smoothZ = (currentZ + this.prevActualZ) * 0.5;
                this.prevActualX = currentX;
                this.prevActualZ = currentZ;
            }
        }
    }

    private void handleFirework() {
        double delayMs = this.nextFireworkDelay * 1000.0;
        if (!(System.currentTimeMillis() - this.lastFireworkTime < delayMs)) {
            if (!this.mc.options.keyUse.isDown()) {
                int fireworkSlot = this.findFireworkHotbar();
                if (fireworkSlot == -1) {
                    fireworkSlot = this.moveFireworkToHotbar();
                }

                if (fireworkSlot != -1) {
                    if (this.durationScaling.get()) {
                        ItemStack stack = this.mc.player.getInventory().getItem(fireworkSlot);
                        Fireworks component = stack.get(DataComponents.FIREWORKS);
                        int flightDuration = component != null ? component.flightDuration() : 1;
                        this.nextFireworkDelay = Math.max(0.3, flightDuration * 0.5 + 0.5 - this.safetyMargin.get());
                    } else {
                        this.nextFireworkDelay = this.fireworkDelay.get();
                    }

                    PlayerInventoryAccessor invAccessor = (PlayerInventoryAccessor)this.mc.player.getInventory();
                    int currentSlot = invAccessor.getSelectedSlot();
                    if (currentSlot != fireworkSlot) {
                        this.previousSlot = currentSlot;
                        InvUtils.swap(fireworkSlot, false);
                        this.swapBackTicks = 2;
                    }

                    this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
                    this.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                    this.rocketTickFree = true;
                    this.lastFireworkTime = System.currentTimeMillis();
                }
            }
        }
    }

    private int findFireworkHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.is(Items.FIREWORK_ROCKET)) {
                return i;
            }
        }

        return -1;
    }

    private int moveFireworkToHotbar() {
        int invSlot = -1;

        for (int i = 9; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.is(Items.FIREWORK_ROCKET)) {
                invSlot = i;
                break;
            }
        }

        if (invSlot == -1) {
            return -1;
        }

        int targetHotbar = this.findEmptyHotbarSlot();
        if (targetHotbar == -1) {
            targetHotbar = 8;
        }

        InvUtils.move().from(invSlot).toHotbar(targetHotbar);
        return targetHotbar;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private void handleMovement() {
        Vec3 vec = Vec3.ZERO;
        float yaw = this.mc.player.getYRot();
        boolean jumpHeld = false;
        boolean sneakHeld = false;
        if (!Modules.get().get(Freecam.class).isActive()) {
            if (this.mc.options.keyUp.isDown()) {
                vec = vec.add(Vec3.directionFromRotation(0.0F, yaw));
            }

            if (this.mc.options.keyDown.isDown()) {
                vec = vec.add(Vec3.directionFromRotation(0.0F, yaw + 180.0F));
            }

            if (this.mc.options.keyLeft.isDown()) {
                vec = vec.add(Vec3.directionFromRotation(0.0F, yaw - 90.0F));
            }

            if (this.mc.options.keyRight.isDown()) {
                vec = vec.add(Vec3.directionFromRotation(0.0F, yaw + 90.0F));
            }

            jumpHeld = this.mc.options.keyJump.isDown();
            sneakHeld = this.mc.options.keyShift.isDown();
        }

        boolean hasFirework = this.findFireworkHotbar() != -1 || this.hasFireworkInInventory();
        boolean horizontal = vec.lengthSqr() >= 1.0E-4;
        if (!horizontal && !jumpHeld && !sneakHeld) {
            this.porpoising = false;
            boolean freeze = this.hoverMode.get() == ControlFly.HoverMode.FREEZE;
            if (freeze || hasFirework) {
                if (!this.idleHovering) {
                    this.idleHovering = true;
                    this.freezeGapTicks = 0;
                    double x = this.mc.player.getX();
                    double z = this.mc.player.getZ();
                    this.prevActualX = x;
                    this.prevActualZ = z;
                    this.smoothX = x;
                    this.smoothZ = z;
                    this.prevSmoothX = x;
                    this.prevSmoothZ = z;
                }

                if (freeze) {
                    if (this.reparkCooldown > 0) {
                        this.reparkCooldown--;
                    }

                    if (!this.frozen && (this.reparkCooldown > 0 || this.mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.25)) {
                        if (this.flipFlop) {
                            this.flipFlop = false;
                            RotationUtils.getInstance().setRotationFullInstant(0.0F, 0.0F);
                        } else {
                            this.flipFlop = true;
                            RotationUtils.getInstance().setRotationFullInstant(180.0F, 0.0F);
                        }
                    } else if (!this.frozen) {
                        this.frozen = true;
                        this.freezeAnchor = this.mc.player.position();
                        RotationUtils.getInstance().clearRotations();
                    }

                    return;
                }

                if (this.mc.options.keyUse.isDown()) {
                    RotationUtils.getInstance().setRotationFullInstant(this.mc.player.getYRot(), this.mc.player.getXRot());
                } else {
                    float idlePitch = this.hoverPitch.get().floatValue();
                    if (this.flipFlop) {
                        this.flipFlop = false;
                        RotationUtils.getInstance().setRotationFullInstant(0.0F, idlePitch);
                    } else {
                        this.flipFlop = true;
                        RotationUtils.getInstance().setRotationFullInstant(180.0F, idlePitch);
                    }
                }
            }
        } else {
            boolean wasIdle = this.idleHovering;
            this.idleHovering = false;
            float[] rot;
            float pitch;
            if (!horizontal) {
                rot = new float[]{yaw, sneakHeld ? 90.0F : -90.0F};
                this.porpoising = false;
                pitch = rot[1];
            } else {
                rot = this.getYawPitch(vec);
                if (sneakHeld) {
                    rot[1] = this.downAngle.get().floatValue();
                } else if (jumpHeld) {
                    rot[1] = -this.upAngle.get().floatValue();
                }

                pitch = this.porpoisePitch(rot[0], rot[1]);
            }

            RotationUtils.getInstance().setRotationFullInstant(rot[0], pitch);
            if (wasIdle && this.fireworkAttached()) {
                this.mc.player.setDeltaMovement(Vec3.directionFromRotation(pitch, rot[0]).scale(1.7));
                RocketBoost boost = Modules.get().get(RocketBoost.class);
                if (boost != null && boost.isActive()) {
                    boost.clearSuppression();
                }
            }
        }
    }

    private float porpoisePitch(float yaw, float keyPitch) {
        RocketBoost boost = this.activeBoost();
        if (this.porpoise.get() && boost != null && boost.hasWindow()) {
            double y = this.mc.player.getY();
            double half = this.porpoiseHeight.get().intValue() / 2.0;
            if (!this.porpoising) {
                this.porpoising = true;
                this.porpoiseTarget = y;
                this.porpoiseDescending = true;
                this.porpoiseFlipTicks = 0;
            }

            if (this.porpoiseFlipTicks > 0) {
                this.porpoiseFlipTicks--;
            }

            boolean manualUp = this.mc.options.keyJump.isDown();
            boolean manualDown = this.mc.options.keyShift.isDown();
            if (!manualUp && !manualDown) {
                if (this.porpoiseFlipTicks == 0) {
                    if (this.porpoiseDescending && y <= this.porpoiseTarget - half) {
                        this.porpoiseDescending = false;
                    } else if (!this.porpoiseDescending && y >= this.porpoiseTarget + half) {
                        this.porpoiseDescending = true;
                    }
                }

                float[] legs = PorpoiseSolver.solveLegs(
                    this.porpoiseHeight.get().intValue(), yaw, boost.alignmentDegrees(), boost.windowThreshold(), this.asymmetricLegs.get()
                );
                float pitch = this.porpoiseDescending ? legs[0] : -legs[1];
                if (this.porpoiseFlipTicks == 0 && this.legObstructed(yaw, pitch)) {
                    this.porpoiseDescending = !this.porpoiseDescending;
                    pitch = this.porpoiseDescending ? legs[0] : -legs[1];
                    this.porpoiseFlipTicks = 10;
                }

                return pitch;
            } else {
                this.porpoiseDescending = manualDown;
                this.porpoiseTarget = y;
                return manualDown ? this.downAngle.get().floatValue() : -this.upAngle.get().floatValue();
            }
        } else {
            this.porpoising = false;
            return keyPitch;
        }
    }

    private boolean legObstructed(float yaw, float pitch) {
        Vec3 eye = this.mc.player.getEyePosition();
        Vec3 end = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(40.0));
        return this.mc.level.clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, this.mc.player)).getType()
            != Type.MISS;
    }

    private boolean hasFireworkInInventory() {
        for (int i = 9; i < 36; i++) {
            if (this.mc.player.getInventory().getItem(i).is(Items.FIREWORK_ROCKET)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasAnyFirework() {
        return this.findFireworkHotbar() != -1 || this.hasFireworkInInventory();
    }

    private float[] getYawPitch(Vec3 vec) {
        if (vec.lengthSqr() < 1.0E-8) {
            return new float[]{this.mc.player.getYRot(), this.mc.player.getXRot()};
        }

        vec = vec.normalize();
        float yaw = (float)Math.toDegrees(Math.atan2(-vec.x, vec.z));
        double horizontalLength = Math.sqrt(vec.x * vec.x + vec.z * vec.z);
        float pitch = (float)Math.toDegrees(-Math.atan2(vec.y, horizontalLength));
        pitch = Mth.clamp(pitch, -90.0F, 90.0F);
        return new float[]{yaw, pitch};
    }

    @Override
    public String getInfoString() {
        return this.mc.player != null && this.mc.player.isFallFlying() ? "Flying" : null;
    }

    public enum HoverMode {
        FREEZE,
        FLIP_FLOP;
    }
}
