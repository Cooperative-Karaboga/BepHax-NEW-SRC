package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.Utils;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class ElytraRecast extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgWallSafety = this.settings.createGroup("Wall Safety");
    private final SettingGroup sgNether = this.settings.createGroup("Nether");
    private final SettingGroup sgOverworld = this.settings.createGroup("Overworld/End");
    private final Setting<Boolean> disableIfNoRockets = this.sgGeneral
        .add(
            new Builder()
                .name("disable-if-no-rockets")
                .description("Automatically disable ElytraRecast when no firework rockets are available.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> debugMessages = this.sgGeneral
        .add(new Builder().name("debug-messages").description("Show debug messages in chat for state changes and recovery events.").defaultValue(false).build());
    private final Setting<Integer> activationDelay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("activation-delay")
                .description("Ticks to wait between jump and elytra activation attempts.")
                .defaultValue(3)
                .range(1, 20)
                .sliderRange(1, 20)
                .build()
        );
    private final Setting<Integer> rocketDelay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("rocket-delay")
                .description("Minimum ticks between rocket usages during ascent.")
                .defaultValue(15)
                .range(5, 60)
                .sliderRange(5, 60)
                .build()
        );
    private final Setting<Boolean> smartRockets = this.sgGeneral
        .add(
            new Builder()
                .name("smart-rockets")
                .description("Only fire a rocket when the previous boost has run out and climb speed drops, instead of on a fixed timer.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> minClimbSpeed = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("min-climb-speed")
                .description("Fire the next rocket only when vertical speed drops below this during ascent.")
                .defaultValue(0.5)
                .range(0.0, 2.0)
                .sliderRange(0.0, 2.0)
                .visible(this.smartRockets::get)
                .build()
        );
    private final Setting<Integer> wallClearance = this.sgWallSafety
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("wall-clearance")
                .description("Blocks of clear space needed in the look direction before firing a rocket. Prevents boosting into walls.")
                .defaultValue(20)
                .range(5, 50)
                .sliderRange(5, 50)
                .build()
        );
    private final Setting<ElytraRecast.WallHitAction> wallHitAction = this.sgWallSafety
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("on-wall-hit"))
                        .description("What to do when you take fly-into-wall damage."))
                    .defaultValue(ElytraRecast.WallHitAction.Pause))
                .build()
        );
    private final Setting<Integer> pauseDuration = this.sgWallSafety
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("pause-duration")
                .description("Ticks to pause recovery after taking wall damage.")
                .defaultValue(20)
                .range(0, 200)
                .sliderRange(0, 200)
                .visible(() -> this.wallHitAction.get() == ElytraRecast.WallHitAction.Pause)
                .build()
        );
    private final Setting<Integer> maxWallHits = this.sgWallSafety
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-wall-hits")
                .description("Disable after this many wall impacts without 30 seconds of damage-free flight.")
                .defaultValue(3)
                .range(1, 10)
                .sliderRange(1, 10)
                .visible(() -> this.wallHitAction.get() == ElytraRecast.WallHitAction.Pause)
                .build()
        );
    private final Setting<Double> rotationSpeed = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("rotation-speed")
                .description("Degrees per tick to adjust pitch when ascending. Lower = smoother & more realistic.")
                .defaultValue(3.0)
                .range(0.5, 15.0)
                .sliderRange(0.5, 15.0)
                .build()
        );
    private final Setting<Double> targetPitch = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("target-pitch")
                .description("Target pitch angle when ascending. Negative = looking up.")
                .defaultValue(-70.0)
                .range(-89.0, 0.0)
                .sliderRange(-89.0, 0.0)
                .build()
        );
    private final Setting<Integer> netherFallDelay = this.sgNether
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("fall-delay")
                .description("Ticks to wait after stopping flight before triggering recovery in Nether.")
                .defaultValue(5)
                .range(1, 40)
                .sliderRange(1, 40)
                .build()
        );
    private final Setting<Boolean> usePitch40Bounds = this.sgOverworld
        .add(
            new Builder()
                .name("use-pitch40-bounds")
                .description(
                    "Derive altitudes from Pitch40's bounds: recover below its lower bounds, ascend back above its upper bounds. Falls back to the settings below when Pitch40 is inactive."
                )
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> overworldTargetAltitude = this.sgOverworld
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("target-altitude")
                .description("Target Y level to ascend to in Overworld/End. Ignored if use-pitch40-bounds is enabled.")
                .defaultValue(360)
                .sliderRange(50, 400)
                .visible(() -> !this.usePitch40Bounds.get())
                .build()
        );
    private final Setting<Integer> overworldMinAltitude = this.sgOverworld
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("min-altitude")
                .description("Trigger recovery if Y drops below this in Overworld/End. Ignored if use-pitch40-bounds is enabled.")
                .defaultValue(310)
                .sliderRange(10, 400)
                .visible(() -> !this.usePitch40Bounds.get())
                .build()
        );
    private static final int WALL_HIT_RESET_TICKS = 600;
    private ElytraRecast.State state;
    private int tickCounter;
    private int rocketTickCounter;
    private boolean usedActivationRocket;
    private int notGlidingTicks;
    private boolean netherRocketUsed;
    private Pitch40 pitch40Util;
    private int pauseTicks;
    private int wallHits;
    private int ticksSinceWallHit;
    private volatile boolean pendingWallHit;

    public ElytraRecast() {
        super(Bep.HUNT_CATEGORY, "ElytraRecast", "Flight recovery fallback. Monitors and recovers when you stop flying or drop too low.");
    }

    public boolean isRecovering() {
        return this.state == ElytraRecast.State.JUMPING || this.state == ElytraRecast.State.ACTIVATING || this.state == ElytraRecast.State.ASCENDING;
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null) {
            if (!this.hasElytraEquipped()) {
                this.error("No elytra equipped!");
                this.toggle();
            } else {
                this.tickCounter = 0;
                this.rocketTickCounter = 0;
                this.usedActivationRocket = false;
                this.notGlidingTicks = 0;
                this.netherRocketUsed = false;
                this.pitch40Util = Modules.get().get(Pitch40.class);
                this.pauseTicks = 0;
                this.wallHits = 0;
                this.ticksSinceWallHit = 0;
                this.pendingWallHit = false;
                this.state = ElytraRecast.State.MONITORING;
                if (this.debugMessages.get()) {
                    this.info("Monitoring flight...");
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        this.state = null;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.state != null) {
                if (this.hasElytraEquipped()) {
                    if (this.pendingWallHit) {
                        this.pendingWallHit = false;
                        this.handleWallHit();
                        if (!this.isActive()) {
                            return;
                        }
                    }

                    this.ticksSinceWallHit++;
                    if (this.wallHits > 0 && this.ticksSinceWallHit > 600) {
                        this.wallHits = 0;
                    }

                    this.tickCounter++;
                    this.rocketTickCounter++;
                    switch (this.state) {
                        case MONITORING:
                            this.handleMonitoring();
                            break;
                        case JUMPING:
                            this.handleJumping();
                            break;
                        case ACTIVATING:
                            this.handleActivating();
                            break;
                        case ASCENDING:
                            this.handleAscending();
                    }
                }
            }
        }
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (this.mc.player != null) {
            if (event.packet instanceof ClientboundDamageEventPacket packet) {
                if (packet.entityId() == this.mc.player.getId()) {
                    if (packet.sourceType().is(DamageTypes.FLY_INTO_WALL)) {
                        this.pendingWallHit = true;
                    }
                }
            }
        }
    }

    private void handleWallHit() {
        this.wallHits++;
        this.ticksSinceWallHit = 0;
        if (this.wallHitAction.get() == ElytraRecast.WallHitAction.Disable) {
            this.error("Flew into a wall! Disabling.");
            this.toggle();
        } else if (this.wallHits >= this.maxWallHits.get()) {
            this.error("Flew into a wall " + this.wallHits + " times! Disabling.");
            this.toggle();
        } else {
            this.warning(
                "Flew into a wall (" + this.wallHits + "/" + this.maxWallHits.get() + ")! Pausing recovery for " + this.pauseDuration.get() + " ticks."
            );
            this.state = ElytraRecast.State.MONITORING;
            this.pauseTicks = this.pauseDuration.get();
            this.tickCounter = 0;
            this.notGlidingTicks = 0;
            this.usedActivationRocket = false;
            this.netherRocketUsed = false;
        }
    }

    private void handleMonitoring() {
        if (this.pauseTicks > 0) {
            this.pauseTicks--;
            this.notGlidingTicks = 0;
        } else {
            boolean currentlyGliding = this.mc.player.isFallFlying();
            double currentY = this.mc.player.getY();
            if (this.isInNether()) {
                if (!currentlyGliding) {
                    this.notGlidingTicks++;
                    if (this.notGlidingTicks >= this.netherFallDelay.get()) {
                        if (this.debugMessages.get()) {
                            this.info("Nether: Flight stopped for " + this.notGlidingTicks + " ticks! Recovering...");
                        }

                        this.triggerRecovery();
                        this.notGlidingTicks = 0;
                        return;
                    }
                } else {
                    this.notGlidingTicks = 0;
                }
            } else {
                double minAlt = this.getEffectiveMinAltitude();
                if (currentY < minAlt) {
                    if (!currentlyGliding) {
                        if (this.debugMessages.get()) {
                            this.info("Below min altitude (Y=" + (int)currentY + " < " + (int)minAlt + ") and not flying! Recovering...");
                        }

                        this.triggerRecovery();
                        return;
                    }

                    if (this.debugMessages.get()) {
                        this.info("Altitude too low (Y=" + (int)currentY + " < " + (int)minAlt + ")! Ascending...");
                    }

                    this.triggerRecovery();
                    return;
                }
            }
        }
    }

    private double getEffectiveMinAltitude() {
        return this.usePitch40Bounds.get() && this.pitch40Util != null && this.pitch40Util.isActive()
            ? this.pitch40Util.pitch40LowerBounds.get() - 10.0
            : this.overworldMinAltitude.get().intValue();
    }

    private double getEffectiveTargetAltitude() {
        return this.usePitch40Bounds.get() && this.pitch40Util != null && this.pitch40Util.isActive()
            ? Math.max(this.pitch40Util.pitch40UpperBounds.get(), this.pitch40Util.pitch40LowerBounds.get() + 40.0) + 2.0
            : this.overworldTargetAltitude.get().intValue();
    }

    private void triggerRecovery() {
        if (!this.hasRockets()) {
            if (this.disableIfNoRockets.get()) {
                this.error("No firework rockets available! Disabling ElytraRecast.");
                this.toggle();
                return;
            }

            this.warning("No firework rockets available! Recovery may fail.");
        }

        this.tickCounter = 0;
        this.rocketTickCounter = this.rocketDelay.get();
        this.usedActivationRocket = false;
        this.netherRocketUsed = false;
        if (this.mc.player.isFallFlying()) {
            this.state = ElytraRecast.State.ASCENDING;
        } else if (this.mc.player.onGround()) {
            this.state = ElytraRecast.State.JUMPING;
        } else {
            this.state = ElytraRecast.State.ACTIVATING;
        }
    }

    private void handleJumping() {
        if (this.mc.player.onGround()) {
            this.mc.player.jumpFromGround();
            this.tickCounter = 0;
        } else if (this.tickCounter >= this.activationDelay.get()) {
            this.state = ElytraRecast.State.ACTIVATING;
            this.tickCounter = 0;
        }
    }

    private void handleActivating() {
        if (this.mc.player.isFallFlying()) {
            this.state = ElytraRecast.State.ASCENDING;
            this.rocketTickCounter = this.rocketDelay.get();
            this.tickCounter = 0;
        } else if (this.mc.player.onGround()) {
            this.state = ElytraRecast.State.JUMPING;
            this.tickCounter = 0;
            this.usedActivationRocket = false;
        } else {
            if (this.tickCounter % 2 == 0) {
                this.sendElytraPacket();
            }

            if (this.tickCounter > 15 && !this.usedActivationRocket) {
                if (!this.hasRocketClearance()) {
                    this.adjustPitchUp();
                } else if (this.useRocket()) {
                    this.usedActivationRocket = true;
                } else if (this.disableIfNoRockets.get()) {
                    this.error("No rockets for activation! Disabling.");
                    this.toggle();
                    return;
                }
            }

            if (this.tickCounter > 40) {
                if (!this.hasRockets() && this.disableIfNoRockets.get()) {
                    this.error("No rockets and activation failed! Disabling.");
                    this.toggle();
                    return;
                }

                this.state = ElytraRecast.State.JUMPING;
                this.tickCounter = 0;
                this.usedActivationRocket = false;
            }
        }
    }

    private void handleAscending() {
        if (!this.mc.player.isFallFlying()) {
            if (!this.hasRockets() && this.disableIfNoRockets.get()) {
                this.error("Lost flight and no rockets! Disabling.");
                this.toggle();
            } else {
                this.state = ElytraRecast.State.ACTIVATING;
                this.tickCounter = 0;
                this.usedActivationRocket = false;
            }
        } else {
            if (this.isInNether()) {
                this.adjustPitchUp();
                if (!this.netherRocketUsed && this.hasRocketClearance()) {
                    if (this.useRocket()) {
                        this.netherRocketUsed = true;
                    } else if (this.disableIfNoRockets.get()) {
                        this.error("No rockets for Nether recovery! Disabling.");
                        this.toggle();
                        return;
                    }
                }

                if (this.netherRocketUsed ? this.tickCounter > 10 : this.tickCounter > 40) {
                    if (this.debugMessages.get()) {
                        this.info(
                            this.netherRocketUsed
                                ? "Nether recovery complete - baritone takes over."
                                : "Nether recovery: no clear path for a rocket, handing back to monitoring."
                        );
                    }

                    this.state = ElytraRecast.State.MONITORING;
                    this.tickCounter = 0;
                    this.netherRocketUsed = false;
                    return;
                }
            } else {
                double currentY = this.mc.player.getY();
                double target = this.getEffectiveTargetAltitude();
                if (currentY >= target) {
                    if (this.debugMessages.get()) {
                        this.info("Target altitude Y=" + (int)target + " reached! Resuming monitoring.");
                    }

                    this.state = ElytraRecast.State.MONITORING;
                    this.tickCounter = 0;
                    return;
                }

                this.adjustPitchUp();
                if (this.rocketTickCounter >= this.rocketDelay.get() && this.shouldBoost()) {
                    if (this.useRocket()) {
                        this.rocketTickCounter = 0;
                    } else if (this.disableIfNoRockets.get()) {
                        this.error("Out of rockets during ascent! Disabling.");
                        this.toggle();
                        return;
                    }
                }
            }
        }
    }

    private boolean shouldBoost() {
        if (!this.hasRocketClearance()) {
            return false;
        } else {
            return !this.smartRockets.get() ? true : !this.isBoosted() && this.mc.player.getDeltaMovement().y < this.minClimbSpeed.get();
        }
    }

    private boolean hasRocketClearance() {
        Vec3 eye = this.mc.player.getEyePosition();
        Vec3 end = eye.add(this.mc.player.getLookAngle().scale(this.wallClearance.get().intValue()));
        return this.mc.level.clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, this.mc.player)).getType()
            == Type.MISS;
    }

    private boolean isBoosted() {
        return !this.mc.level.getEntitiesOfClass(FireworkRocketEntity.class, this.mc.player.getBoundingBox().inflate(2.0), rocket -> !rocket.isShotAtAngle()).isEmpty();
    }

    private void adjustPitchUp() {
        float currentPitch = this.mc.player.getXRot();
        float target = this.targetPitch.get().floatValue();
        float speed = this.rotationSpeed.get().floatValue();
        float diff = target - currentPitch;
        if (!(Math.abs(diff) < 0.5F)) {
            float adjustment;
            if (Math.abs(diff) <= speed) {
                adjustment = diff;
            } else {
                adjustment = diff > 0.0F ? speed : -speed;
            }

            float newPitch = currentPitch + adjustment;
            newPitch = Math.max(-89.0F, Math.min(89.0F, newPitch));
            this.mc.player.setXRot(newPitch);
        }
    }

    private void sendElytraPacket() {
        if (this.mc.player != null && this.mc.getConnection() != null) {
            this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.START_FALL_FLYING));
        }
    }

    private boolean hasRockets() {
        FindItemResult hotbar = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (hotbar.found()) {
            return true;
        }

        FindItemResult inv = InvUtils.find(Items.FIREWORK_ROCKET);
        return inv.found();
    }

    private boolean useRocket() {
        FindItemResult hotbar = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!hotbar.found()) {
            FindItemResult inv = InvUtils.find(Items.FIREWORK_ROCKET);
            if (!inv.found()) {
                return false;
            }

            int hotbarSlot = this.findEmptyHotbarSlot();
            if (hotbarSlot != -1) {
                InvUtils.move().from(inv.slot()).to(hotbarSlot);
            }
        }

        Utils.firework(this.mc, false);
        return true;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private boolean hasElytraEquipped() {
        return this.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
    }

    private boolean isInNether() {
        return this.mc.level == null ? false : this.mc.level.dimension() == Level.NETHER;
    }

    @Override
    public String getInfoString() {
        if (this.state == null) {
            return null;
        }

        if (this.mc.player == null) {
            return this.state.name();
        }

        double minAlt = this.isInNether() ? 0.0 : this.getEffectiveMinAltitude();

        return switch (this.state) {
            case MONITORING -> this.pauseTicks > 0
                ? String.format("PAUSED %d", this.pauseTicks)
                : (
                    this.isInNether()
                        ? String.format("Y=%.0f", this.mc.player.getY())
                        : String.format("Y=%.0f (min:%.0f)", this.mc.player.getY(), minAlt)
                );
            case JUMPING -> "JUMP";
            case ACTIVATING -> "ACTIVATE";
            case ASCENDING -> this.isInNether()
                ? String.format("↑%.0f", this.mc.player.getY())
                : String.format("↑%.0f/%.0f", this.mc.player.getY(), this.getEffectiveTargetAltitude());
        };
    }

    private enum State {
        MONITORING,
        JUMPING,
        ACTIVATING,
        ASCENDING;
    }

    public enum WallHitAction {
        Pause,
        Disable;
    }
}
