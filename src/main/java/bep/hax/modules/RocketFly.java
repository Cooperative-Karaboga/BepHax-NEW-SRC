package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.PorpoiseSolver;
import bep.hax.util.RotationUtils;
import bep.hax.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class RocketFly extends Module {
    private long lastRocketUse = 0L;
    private boolean launched = false;
    private double yTarget = -1.0;
    private float targetPitch = 0.0F;
    private boolean boostToggledByUs = false;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Integer> fireworkDelay = this.sgGeneral
        .add(
            new Builder()
                .name("timed-delay")
                .description("The delay between firework usages in milliseconds.")
                .defaultValue(4000)
                .sliderRange(0, 10000)
                .build()
        );
    private final Setting<Boolean> useManualY = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("use-manual-y-level")
                .description("Use a manually set Y level instead of the Y level when activated.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> manualYLevel = this.sgGeneral
        .add(
            new Builder()
                .name("manual-y-level")
                .description("The Y level to maintain when using manual Y level.")
                .defaultValue(256)
                .sliderRange(-64, 320)
                .visible(this.useManualY::get)
                .onChanged(val -> this.yTarget = val.intValue())
                .build()
        );
    private final SettingGroup sgBoost = this.settings.createGroup("Firework Boost");
    private final Setting<Boolean> useBoost = this.sgBoost
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("firework-boost")
                .description(
                    "Ride the firework window while holding the Y level. The rockets stay RocketFly's own on timed-delay - the boost only rides whatever is lit. Level flight pays out on diagonal headings only: straight down an axis the window is worth no more than the firework itself and the boost stands aside, so leave boost-porpoise on."
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
    private final Setting<Boolean> boostPorpoise = this.sgBoost
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("boost-porpoise")
                .description(
                    "Alternate climb and dive legs instead of holding level flight. This is what makes the window pay on ANY heading. Level flight only settles each axis at the box edge (~1.68 b/t), so it needs a diagonal to split speed across two axes - but a dive makes the elytra's own fall-to-forward term grow the predicted movement the box is measured against, and that term depends on |vy| * cos^2(pitch), not on how the horizontal splits. Measured 92.15 b/s straight down an axis against 92.42 b/s on a diagonal at a 24-block leg: heading stops mattering. Net altitude stays inside porpoise-height; jump and sneak bias the legs up or down instead of steering pitch directly."
                )
                .defaultValue(true)
                .visible(this.useBoost::get)
                .build()
        );
    private final Setting<Integer> porpoiseHeight = this.sgBoost
        .add(
            new Builder()
                .name("porpoise-height")
                .description(
                    "Vertical blocks a climb or dive leg covers before flipping, centred on the Y lock. Longer legs lose fewer ticks to the turn-around but swing the altitude further around the locked height."
                )
                .defaultValue(24)
                .min(4)
                .max(128)
                .sliderRange(8, 64)
                .visible(() -> this.useBoost.get() && this.boostPorpoise.get())
                .build()
        );
    private final Setting<Boolean> diagonalSnap = this.sgBoost
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("diagonal-snap")
                .description(
                    "Veer travel onto the nearest 45-degree diagonal. Off by default: it is only a real win with boost-porpoise OFF, where level flight settles each axis at the box edge and a diagonal splits speed across two of them - 47.8 b/s against 33.8 straight down an axis. With the porpoise running the speed comes from the dive conversion instead, which does not care how the horizontal splits, so a diagonal is worth 0.3% (92.4 against 92.2) and costs you your heading. Leave it off unless you actually want to travel diagonally."
                )
                .defaultValue(false)
                .visible(() -> this.useBoost.get() && this.boostPorpoise.get())
                .build()
        );
    private static final double PORPOISE_CLEARANCE = 32.0;
    private boolean porpoising = false;
    private boolean porpoiseDescending = false;
    private boolean porpoiseRotating = false;
    private int porpoiseFlipTicks = 0;
    private int boostWindowGrace = 0;

    public RocketFly() {
        super(Bep.HUNT_CATEGORY, "RocketFly", "Maintains a level Y-flight with fireworks and smooth pitch control.");
    }

    @Override
    public void onActivate() {
        this.launched = false;
        this.yTarget = -1.0;
        this.porpoising = false;
        this.porpoiseRotating = false;
        this.porpoiseFlipTicks = 0;
        this.boostWindowGrace = 0;
        if (this.useBoost.get()) {
            this.enableBoost();
        }

        if (this.mc.player == null || !this.mc.player.isFallFlying()) {
            this.info("You must be flying before enabling RocketFly.");
        }
    }

    @Override
    public void onDeactivate() {
        this.endPorpoise();
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

    private RocketBoost activeBoost() {
        if (!this.useBoost.get()) {
            return null;
        }

        RocketBoost boost = Modules.get().get(RocketBoost.class);
        return boost != null && boost.isActive() ? boost : null;
    }

    private void driveBoost() {
        if (this.mc.player != null) {
            RocketBoost windowBoost = this.activeBoost();
            if (windowBoost != null && windowBoost.hasWindow()) {
                this.boostWindowGrace = 5;
            } else if (this.boostWindowGrace > 0) {
                this.boostWindowGrace--;
            }

            RocketBoost boost = Modules.get().get(RocketBoost.class);
            if (boost != null && boost.isActive() && this.mc.player.isFallFlying()) {
                boost.declareTravelling();
            }
        }
    }

    public void tickFlyLogic() {
        if (this.mc.player != null) {
            double currentY = this.mc.player.getY();
            if (this.mc.player.isFallFlying()) {
                if (this.yTarget == -1.0 || !this.launched) {
                    if (this.useManualY.get()) {
                        this.yTarget = this.manualYLevel.get().intValue();
                    } else {
                        this.yTarget = currentY;
                    }

                    this.launched = true;
                }

                if (!this.porpoiseTravel(currentY)) {
                    this.endPorpoise();
                    if (!this.useManualY.get()) {
                        double yDiffFromLock = currentY - this.yTarget;
                        if (Math.abs(yDiffFromLock) > 10.0) {
                            this.yTarget = currentY;
                            this.info("Y-lock reset due to altitude deviation.");
                        }
                    }

                    double yDiff = currentY - this.yTarget;
                    if (Math.abs(yDiff) > 10.0) {
                        this.targetPitch = (float)(Math.atan2(yDiff, 100.0) * (180.0 / Math.PI));
                    } else if (yDiff > 2.0) {
                        this.targetPitch = 10.0F;
                    } else if (yDiff < -2.0) {
                        this.targetPitch = -10.0F;
                    } else {
                        this.targetPitch = 0.0F;
                    }

                    float currentPitch = this.mc.player.getXRot();
                    float pitchDiff = this.targetPitch - currentPitch;
                    this.mc.player.setXRot(currentPitch + pitchDiff * 0.1F);
                }

                if (System.currentTimeMillis() - this.lastRocketUse > this.fireworkDelay.get().intValue()) {
                    this.tryUseFirework();
                }
            } else {
                this.endPorpoise();
                if (!this.launched) {
                    this.mc.player.jumpFromGround();
                    this.launched = true;
                } else if (System.currentTimeMillis() - this.lastRocketUse > 1000L) {
                    this.tryUseFirework();
                }

                this.yTarget = -1.0;
            }
        }
    }

    private boolean porpoiseTravel(double y) {
        if (this.useBoost.get() && this.boostPorpoise.get() && this.boostWindowGrace != 0) {
            double half = this.porpoiseHeight.get().intValue() / 2.0;
            if (!this.porpoising) {
                this.porpoising = true;
                this.porpoiseDescending = y > this.yTarget;
                this.porpoiseFlipTicks = 0;
            }

            if (this.porpoiseFlipTicks > 0) {
                this.porpoiseFlipTicks--;
            }

            boolean manualUp = this.mc.options.keyJump.isDown();
            boolean manualDown = this.mc.options.keyShift.isDown();
            if (manualUp || manualDown) {
                this.porpoiseDescending = manualDown;
                if (!this.useManualY.get()) {
                    this.yTarget = y;
                }
            } else if (this.porpoiseFlipTicks == 0) {
                if (this.porpoiseDescending && y <= this.yTarget - half) {
                    this.porpoiseDescending = false;
                } else if (!this.porpoiseDescending && y >= this.yTarget + half) {
                    this.porpoiseDescending = true;
                }
            }

            float yaw = this.mc.player.getYRot();
            if (this.diagonalSnap.get()) {
                yaw = snapToDiagonal(yaw);
            }

            float magnitude = this.porpoisePitch();
            float pitch = this.porpoiseDescending ? magnitude : -magnitude;
            if (!manualUp && !manualDown && this.porpoiseFlipTicks == 0 && this.legObstructed(yaw, pitch)) {
                this.porpoiseDescending = !this.porpoiseDescending;
                pitch = -pitch;
                this.porpoiseFlipTicks = 10;
            }

            if (RotationUtils.getInstance().setRotationFullInstant(yaw, pitch)) {
                this.porpoiseRotating = true;
            }

            return true;
        } else {
            return false;
        }
    }

    private float porpoisePitch() {
        RocketBoost boost = this.activeBoost();
        double alignment = boost == null ? 0.0 : boost.alignmentDegrees();
        double threshold = boost == null ? 1.7 : boost.windowThreshold();
        return PorpoiseSolver.solvePitch(this.porpoiseHeight.get().intValue(), this.mc.player.getYRot(), alignment, threshold);
    }

    private static float snapToDiagonal(float yaw) {
        return (float)(Math.floor(yaw / 90.0F) * 90.0 + 45.0);
    }

    private boolean legObstructed(float yaw, float pitch) {
        if (this.mc.level == null) {
            return false;
        }

        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 eye = this.mc.player.getEyePosition();
        HitResult hit = this.mc
            .level
            .clip(new ClipContext(eye, eye.add(look.scale(32.0)), Block.COLLIDER, Fluid.NONE, this.mc.player));
        return hit.getType() != Type.MISS;
    }

    private void endPorpoise() {
        this.porpoising = false;
        this.porpoiseFlipTicks = 0;
        if (this.porpoiseRotating) {
            this.porpoiseRotating = false;
            RotationUtils.getInstance().clearRotations();
        }
    }

    public void resetYLock() {
        this.yTarget = -1.0;
        this.launched = false;
    }

    @EventHandler
    private void onTick(Pre event) {
        this.driveBoost();
        this.tickFlyLogic();
    }

    private void tryUseFirework() {
        FindItemResult hotbar = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!hotbar.found()) {
            FindItemResult inv = InvUtils.find(Items.FIREWORK_ROCKET);
            if (!inv.found()) {
                this.info("No fireworks found in hotbar or inventory.");
                return;
            }

            int hotbarSlot = this.findEmptyHotbarSlot();
            if (hotbarSlot == -1) {
                this.info("No empty hotbar slot available to move fireworks.");
                return;
            }

            InvUtils.move().from(inv.slot()).to(hotbarSlot);
        }

        Utils.firework(this.mc, false);
        this.lastRocketUse = System.currentTimeMillis();
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }
}
