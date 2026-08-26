package bep.hax.mixin.meteor;

import bep.hax.accessor.InputAccessor;
import bep.hax.mixin.accessor.AccessorClientWorld;
import bep.hax.mixin.accessor.BundleS2CPacketAccessor;
import bep.hax.mixin.accessor.EntityVelocityUpdateS2CPacketAccessor;
import bep.hax.mixin.accessor.ExplosionS2CPacketAccessor;
import bep.hax.mixin.accessor.FireworkRocketEntityAccessor;
import bep.hax.mixin.accessor.LocalPlayerAccessor;
import bep.hax.util.InventoryManager;
import bep.hax.util.PositionUtil;
import bep.hax.util.PushFluidsEvent;
import bep.hax.util.PushOutOfBlocksEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.mixininterface.IExplosionS2CPacket;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.movement.Velocity;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Velocity.class, remap = false)
public abstract class VelocityMixin extends Module {
    @Shadow
    @Final
    public Setting<Boolean> knockback;
    @Shadow
    @Final
    public Setting<Double> knockbackHorizontal;
    @Shadow
    @Final
    public Setting<Double> knockbackVertical;
    @Shadow
    @Final
    public Setting<Boolean> explosions;
    @Shadow
    @Final
    public Setting<Double> explosionsHorizontal;
    @Shadow
    @Final
    public Setting<Double> explosionsVertical;
    @Unique
    private SettingGroup bephax$sgAdvancedModes;
    @Unique
    private Setting<InventoryManager.VelocityMode> bephax$mode;
    @Unique
    private Setting<Boolean> bephax$conceal;
    @Unique
    private Setting<Boolean> bephax$wallsGroundOnly;
    @Unique
    private Setting<Boolean> bephax$wallsTrapped;
    @Unique
    private Setting<Boolean> bephax$pushBlocks;
    @Unique
    private Setting<Boolean> bephax$pushLiquids;
    @Unique
    private Setting<Boolean> bephax$pushFishhook;
    @Unique
    private Setting<Integer> bephax$skipFreezeTicks;
    @Unique
    private Setting<Boolean> bephax$skipGroundOnly;
    @Unique
    private Setting<Boolean> bephax$skipAbortOnInput;
    @Unique
    private Setting<Boolean> bephax$skipReminderSafety;
    @Unique
    private Setting<Boolean> bephax$skipLegacyProtocol;
    @Unique
    private Setting<Boolean> bephax$skipDebug;
    @Unique
    private InventoryManager bephax$inventoryManager;
    @Unique
    private volatile boolean bephax$cancelVelocity = false;
    @Unique
    private volatile boolean bephax$concealVelocity = false;
    @Unique
    private volatile int bephax$freezeTicksLeft = 0;
    @Unique
    private volatile boolean bephax$freezeAnchored = false;
    @Unique
    private volatile boolean bephax$skipExempt = false;
    @Unique
    private double bephax$freezeX;
    @Unique
    private double bephax$freezeY;
    @Unique
    private double bephax$freezeZ;
    @Unique
    private static final Random RANDOM = new Random();

    public VelocityMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.bephax$inventoryManager = InventoryManager.getInstance();
        this.bephax$sgAdvancedModes = this.settings.createGroup("Advanced Modes");
        this.bephax$mode = this.bephax$sgAdvancedModes
            .add(
                ((Builder)((Builder)((Builder)new Builder().name("mode"))
                            .description("Velocity mode (NORMAL = Meteor default, WALLS = only when phased, GRIM/GRIM_V3 = 2b2t bypass)"))
                        .defaultValue(InventoryManager.VelocityMode.GRIM_V3))
                    .build()
            );
        this.bephax$conceal = this.bephax$sgAdvancedModes
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("conceal")
                    .description("Fixes velocity on servers with excessive setbacks")
                    .defaultValue(true)
                    .build()
            );
        this.bephax$wallsGroundOnly = this.bephax$sgAdvancedModes
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("ground-only")
                    .description("Only applies velocity in walls while on ground")
                    .defaultValue(false)
                    .visible(() -> this.bephax$mode.get() == InventoryManager.VelocityMode.WALLS)
                    .build()
            );
        this.bephax$wallsTrapped = this.bephax$sgAdvancedModes
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("walls-trapped")
                    .description("Applies velocity while player head is trapped in blocks")
                    .defaultValue(false)
                    .visible(() -> this.bephax$mode.get() == InventoryManager.VelocityMode.WALLS)
                    .build()
            );
        this.bephax$pushBlocks = this.bephax$sgAdvancedModes
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("nopush-blocks")
                    .description("Prevents being pushed out of blocks (WARNING: Can make you stuck inside blocks)")
                    .defaultValue(true)
                    .build()
            );
        this.bephax$pushLiquids = this.bephax$sgAdvancedModes
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("nopush-liquids")
                    .description("Prevents being pushed by flowing liquids")
                    .defaultValue(true)
                    .build()
            );
        this.bephax$pushFishhook = this.bephax$sgAdvancedModes
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("nopush-fishhook")
                    .description("Prevents being pulled by fishing rod hooks")
                    .defaultValue(true)
                    .build()
            );
        this.bephax$skipFreezeTicks = this.bephax$sgAdvancedModes
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("skip-freeze-ticks")
                    .description("How many ticks to hold still after eating a velocity in GRIM_SKIP mode")
                    .defaultValue(2)
                    .min(1)
                    .max(10)
                    .sliderRange(1, 6)
                    .visible(() -> this.bephax$mode.get() == InventoryManager.VelocityMode.GRIM_SKIP)
                    .build()
            );
        this.bephax$skipGroundOnly = this.bephax$sgAdvancedModes
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("skip-ground-only")
                    .description("Only eat velocity while on ground. In air gravity forces a position packet, which kills the skipped-tick window and flags.")
                    .defaultValue(true)
                    .visible(() -> this.bephax$mode.get() == InventoryManager.VelocityMode.GRIM_SKIP)
                    .build()
            );
        this.bephax$skipAbortOnInput = this.bephax$sgAdvancedModes
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("skip-abort-on-input")
                    .description("Take the velocity normally if you are moving. Moving sends a position packet, so the bypass cannot work.")
                    .defaultValue(true)
                    .visible(() -> this.bephax$mode.get() == InventoryManager.VelocityMode.GRIM_SKIP)
                    .build()
            );
        this.bephax$skipReminderSafety = this.bephax$sgAdvancedModes
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("skip-reminder-safety")
                    .description("Refuse the bypass when vanilla's every-20-tick forced position packet would land inside the freeze window")
                    .defaultValue(true)
                    .visible(() -> this.bephax$mode.get() == InventoryManager.VelocityMode.GRIM_SKIP)
                    .build()
            );
        this.bephax$skipLegacyProtocol = this.bephax$sgAdvancedModes
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("skip-legacy-protocol")
                    .description(
                        "Enable only while spoofing a pre-1.18.2 protocol (ViaVersion). Grim then allows a 0.03 window instead of 0.0002, so velocity can be eaten without a climbable or firework."
                    )
                    .defaultValue(false)
                    .visible(() -> this.bephax$mode.get() == InventoryManager.VelocityMode.GRIM_SKIP)
                    .build()
            );
        this.bephax$skipDebug = this.bephax$sgAdvancedModes
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("skip-debug")
                    .description("Log why each velocity was eaten or taken")
                    .defaultValue(false)
                    .visible(() -> this.bephax$mode.get() == InventoryManager.VelocityMode.GRIM_SKIP)
                    .build()
            );
    }

    @Override
    public void onActivate() {
        this.bephax$resetState();
    }

    @Override
    public void onDeactivate() {
        if (this.bephax$cancelVelocity && this.bephax$mode.get() == InventoryManager.VelocityMode.GRIM) {
            this.bephax$sendGrimBypass();
        }

        this.bephax$resetState();
    }

    @Unique
    private void bephax$resetState() {
        this.bephax$cancelVelocity = false;
        this.bephax$concealVelocity = false;
        this.bephax$freezeTicksLeft = 0;
        this.bephax$freezeAnchored = false;
        this.bephax$skipExempt = false;
    }

    @Inject(method = "onPacketReceive", at = @At("HEAD"), cancellable = true)
    private void cancelMeteorHandler(Receive event, CallbackInfo ci) {
        if (this.bephax$mode.get() != InventoryManager.VelocityMode.NORMAL) {
            ci.cancel();
        }
    }

    @Inject(method = "getHorizontal", at = @At("HEAD"), cancellable = true)
    private void bephax$getHorizontal(Setting<Double> setting, CallbackInfoReturnable<Double> cir) {
        if (setting == this.explosionsHorizontal && this.bephax$ownsExplosionScaling()) {
            cir.setReturnValue(1.0);
        }
    }

    @Inject(method = "getVertical", at = @At("HEAD"), cancellable = true)
    private void bephax$getVertical(Setting<Double> setting, CallbackInfoReturnable<Double> cir) {
        if (setting == this.explosionsVertical && this.bephax$ownsExplosionScaling()) {
            cir.setReturnValue(1.0);
        }
    }

    @Unique
    private boolean bephax$ownsExplosionScaling() {
        return this.bephax$mode != null && this.bephax$mode.get() != InventoryManager.VelocityMode.NORMAL;
    }

    @Unique
    @EventHandler(priority = 100)
    private void onReceivePacket(Receive event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (event.packet instanceof ClientboundPlayerPositionPacket) {
                this.bephax$onTeleport();
            } else if (event.packet instanceof ClientboundSetEntityMotionPacket packet && this.knockback.get()) {
                EntityVelocityUpdateS2CPacketAccessor accessor = (EntityVelocityUpdateS2CPacketAccessor)packet;
                if (accessor.getEntityId() != this.mc.player.getId()) {
                    return;
                }

                Vec3 velocity = accessor.getVelocity();
                Vec3 resolved = this.bephax$resolveKnockback(velocity);
                if (resolved == null) {
                    event.cancel();
                } else {
                    this.bephax$takeKnockback(packet, velocity, resolved);
                }
            } else if (event.packet instanceof ClientboundExplodePacket packet && this.explosions.get()) {
                Vec3 knockback = packet.playerKnockback().orElse(Vec3.ZERO);
                Vec3 resolved = this.bephax$resolveExplosion(knockback);
                if (resolved == null) {
                    event.cancel();
                    this.bephax$playExplosionSound(packet);
                } else {
                    this.bephax$takeExplosion(packet, knockback, resolved);
                }
            } else if (event.packet instanceof BundlePacket bundlePacket) {
                this.bephax$handleBundlePacket(bundlePacket);
            } else if (event.packet instanceof ClientboundEntityEventPacket packet
                && packet.getEventId() == 31
                && this.bephax$pushFishhook.get()
                && packet.getEntity(this.mc.level) instanceof FishingHook hook
                && hook.getHookedIn() == this.mc.player) {
                event.cancel();
            }
        }
    }

    @Unique
    private void bephax$handleBundlePacket(BundlePacket bundlePacket) {
        List<Packet<?>> allowedBundle = new ArrayList<>();

        for (Object subPacketObj : bundlePacket.subPackets()) {
            if (subPacketObj instanceof Packet<?> subPacket) {
                if (subPacket instanceof ClientboundPlayerPositionPacket) {
                    this.bephax$onTeleport();
                } else if (subPacket instanceof ClientboundSetEntityMotionPacket packet && this.knockback.get()) {
                    EntityVelocityUpdateS2CPacketAccessor accessor = (EntityVelocityUpdateS2CPacketAccessor)packet;
                    if (accessor.getEntityId() == this.mc.player.getId()) {
                        Vec3 velocity = accessor.getVelocity();
                        Vec3 resolved = this.bephax$resolveKnockback(velocity);
                        if (resolved == null) {
                            continue;
                        }

                        this.bephax$takeKnockback(packet, velocity, resolved);
                    }
                } else if (subPacket instanceof ClientboundExplodePacket packet && this.explosions.get()) {
                    Vec3 knockback = packet.playerKnockback().orElse(Vec3.ZERO);
                    Vec3 resolved = this.bephax$resolveExplosion(knockback);
                    if (resolved == null) {
                        this.bephax$playExplosionSound(packet);
                        continue;
                    }

                    this.bephax$takeExplosion(packet, knockback, resolved);
                }

                allowedBundle.add(subPacket);
            }
        }

        ((BundleS2CPacketAccessor)bundlePacket).setPackets(allowedBundle);
    }

    @Unique
    private Vec3 bephax$resolveKnockback(Vec3 velocity) {
        if (this.bephax$concealVelocity && velocity.x == 0.0 && velocity.y == 0.0 && velocity.z == 0.0) {
            this.bephax$concealVelocity = false;
            return velocity;
        }

        return switch ((InventoryManager.VelocityMode)this.bephax$mode.get()) {
            case NORMAL -> velocity;
            case WALLS -> this.bephax$wallsApplies() ? this.bephax$scale(velocity, this.knockbackHorizontal.get(), this.knockbackVertical.get()) : velocity;
            case GRIM -> {
                if (!this.bephax$inventoryManager.hasPassed(100L)) {
                    yield velocity;
                } else {
                    this.bephax$cancelVelocity = true;
                    yield null;
                }
            }
            case GRIM_V3 -> this.bephax$isPhased() ? null : velocity;
            case GRIM_SKIP -> {
                if (!this.bephax$canEatVelocity()) {
                    yield velocity;
                } else {
                    this.bephax$armFreeze();
                    yield null;
                }
            }
        };
    }

    @Unique
    private Vec3 bephax$resolveExplosion(Vec3 knockback) {
        return switch ((InventoryManager.VelocityMode)this.bephax$mode.get()) {
            case NORMAL -> knockback;
            case WALLS -> this.bephax$isPhased() ? this.bephax$scale(knockback, this.explosionsHorizontal.get(), this.explosionsVertical.get()) : knockback;
            case GRIM -> {
                if (!this.bephax$inventoryManager.hasPassed(100L)) {
                    yield knockback;
                } else {
                    this.bephax$cancelVelocity = true;
                    yield null;
                }
            }
            case GRIM_V3 -> this.bephax$isPhased() ? null : knockback;
            case GRIM_SKIP -> {
                if (!this.bephax$canEatVelocity()) {
                    yield knockback;
                } else {
                    this.bephax$armFreeze();
                    yield null;
                }
            }
        };
    }

    @Unique
    private Vec3 bephax$scale(Vec3 velocity, double horizontal, double vertical) {
        if (horizontal == 0.0 && vertical == 0.0) {
            return null;
        } else {
            return horizontal == 1.0 && vertical == 1.0
                ? velocity
                : new Vec3(velocity.x * horizontal, velocity.y * vertical, velocity.z * horizontal);
        }
    }

    @Unique
    private void bephax$takeKnockback(ClientboundSetEntityMotionPacket packet, Vec3 original, Vec3 resolved) {
        if (resolved != original) {
            ((meteordevelopment.meteorclient.mixin.EntityVelocityUpdateS2CPacketAccessor)packet).meteor$setVelocity(resolved);
        }

        this.bephax$releaseFreeze("newer knockback taken");
    }

    @Unique
    private void bephax$takeExplosion(ClientboundExplodePacket packet, Vec3 original, Vec3 resolved) {
        if (resolved != original) {
            IExplosionS2CPacket explosionPacket = (IExplosionS2CPacket)(Object)packet;
            explosionPacket.meteor$setVelocityX((float)resolved.x);
            explosionPacket.meteor$setVelocityY((float)resolved.y);
            explosionPacket.meteor$setVelocityZ((float)resolved.z);
        }

        this.bephax$releaseFreeze("explosion taken");
    }

    @Unique
    private void bephax$onTeleport() {
        if (this.bephax$conceal.get()) {
            this.bephax$concealVelocity = true;
        }

        this.bephax$releaseFreeze("teleport");
    }

    @Unique
    private void bephax$playExplosionSound(ClientboundExplodePacket packet) {
        Vec3 center = ((ExplosionS2CPacketAccessor)(Object)packet).getCenter();
        this.mc
            .executeIfPossible(
                () -> ((AccessorClientWorld)this.mc.level)
                    .hookPlaySound(
                        center.x,
                        center.y,
                        center.z,
                        SoundEvents.GENERIC_EXPLODE.value(),
                        SoundSource.BLOCKS,
                        4.0F,
                        (1.0F + (RANDOM.nextFloat() - RANDOM.nextFloat()) * 0.2F) * 0.7F,
                        false,
                        RANDOM.nextLong()
                    )
            );
    }

    @Unique
    private boolean bephax$wallsApplies() {
        return this.bephax$isPhased() || this.bephax$wallsTrapped.get() && this.bephax$isWallsTrapped()
            ? !this.bephax$wallsGroundOnly.get() || this.mc.player.onGround()
            : false;
    }

    @Unique
    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            this.bephax$concealVelocity = false;
            this.bephax$skipExempt = this.bephax$mode.get() == InventoryManager.VelocityMode.GRIM_SKIP && this.bephax$computeSkipExemption();
            this.bephax$tickFreeze();
            if (this.bephax$cancelVelocity && this.bephax$mode.get() == InventoryManager.VelocityMode.GRIM) {
                this.bephax$sendGrimBypass();
                this.bephax$cancelVelocity = false;
            }
        } else {
            this.bephax$freezeTicksLeft = 0;
            this.bephax$freezeAnchored = false;
            this.bephax$skipExempt = false;
        }
    }

    @Unique
    @EventHandler
    private void onPushOutOfBlocks(PushOutOfBlocksEvent event) {
        if (this.bephax$pushBlocks.get()) {
            event.cancel();
        }
    }

    @Unique
    @EventHandler
    private void onPushFluids(PushFluidsEvent event) {
        if (this.bephax$pushLiquids.get()) {
            event.cancel();
        }
    }

    @Unique
    private void bephax$sendGrimBypass() {
        if (this.mc.player != null && this.mc.getConnection() != null) {
            this.mc
                .getConnection()
                .send(
                    new ServerboundPlayerActionPacket(
                        Action.STOP_DESTROY_BLOCK,
                        this.mc.player.isVisuallyCrawling() ? this.mc.player.blockPosition() : this.mc.player.blockPosition().above(),
                        Direction.DOWN
                    )
                );
        }
    }

    @Unique
    private boolean bephax$canEatVelocity() {
        if (this.mc.player == null || this.mc.level == null) {
            return false;
        } else if (this.mc.player.isPassenger()) {
            return this.bephax$refuse("in vehicle");
        } else if (this.bephax$skipGroundOnly.get() && !this.mc.player.onGround()) {
            return this.bephax$refuse("airborne");
        } else if (this.bephax$skipAbortOnInput.get() && this.bephax$hasMovementInput()) {
            return this.bephax$refuse("moving");
        } else if (!this.bephax$skipExempt) {
            return this.bephax$refuse("no climbable/firework exemption");
        } else {
            LocalPlayerAccessor player = (LocalPlayerAccessor)this.mc.player;
            double dx = this.mc.player.getX() - player.getXLast();
            double dy = this.mc.player.getY() - player.getYLast();
            double dz = this.mc.player.getZ() - player.getZLast();
            if (dx * dx + dy * dy + dz * dz > 4.0E-8) {
                return this.bephax$refuse("drifted past 2.0E-4");
            } else {
                return this.bephax$skipReminderSafety.get() && player.getPositionReminder() + this.bephax$skipFreezeTicks.get() >= 20
                    ? this.bephax$refuse("forced position packet due in " + (20 - player.getPositionReminder()) + "t")
                    : true;
            }
        }
    }

    @Unique
    private boolean bephax$computeSkipExemption() {
        if (this.bephax$skipLegacyProtocol.get()) {
            return true;
        } else {
            return this.bephax$hasAttachedFirework() ? true : this.bephax$isNearClimbable();
        }
    }

    @Unique
    private boolean bephax$isNearClimbable() {
        return PositionUtil.getAllInBox(this.mc.player.getBoundingBox().move(0.0, -2.0E-4, 0.0))
            .stream()
            .anyMatch(
                blockPos -> {
                    BlockState state = this.mc.level.getBlockState(blockPos);
                    return state.is(BlockTags.CLIMBABLE)
                        || state.is(Blocks.POWDER_SNOW)
                            && this.mc.player.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS);
                }
            );
    }

    @Unique
    private boolean bephax$hasAttachedFirework() {
        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof FireworkRocketEntity firework
                && firework.isAlive()
                && ((FireworkRocketEntityAccessor)firework).getAttachedToEntity() == this.mc.player) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private boolean bephax$hasMovementInput() {
        if (this.mc.player != null && this.mc.player.input != null) {
            Input keys = this.mc.player.input.keyPresses;
            if (keys == null || !keys.forward() && !keys.backward() && !keys.left() && !keys.right() && !keys.jump()) {
                Vec2 move = this.mc.player.input.getMoveVector();
                return move != null && (Math.abs(move.x) > 1.0E-5F || Math.abs(move.y) > 1.0E-5F);
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    @Unique
    private boolean bephax$refuse(String reason) {
        this.bephax$skipLog("Took velocity: " + reason);
        return false;
    }

    @Unique
    private void bephax$skipLog(String message) {
        if (this.bephax$skipDebug.get()) {
            this.mc.executeIfPossible(() -> this.info(message));
        }
    }

    @Unique
    private void bephax$armFreeze() {
        this.bephax$freezeAnchored = false;
        this.bephax$freezeTicksLeft = this.bephax$skipFreezeTicks.get();
        this.bephax$skipLog("Ate velocity, holding " + this.bephax$freezeTicksLeft + " ticks");
    }

    @Unique
    private void bephax$releaseFreeze(String reason) {
        if (this.bephax$freezeTicksLeft > 0) {
            this.bephax$freezeTicksLeft = 0;
            this.bephax$freezeAnchored = false;
            this.bephax$skipLog("Freeze released (" + reason + ")");
        }
    }

    @Unique
    private void bephax$tickFreeze() {
        int ticksLeft = this.bephax$freezeTicksLeft;
        if (ticksLeft <= 0) {
            this.bephax$freezeAnchored = false;
        } else if (this.bephax$skipAbortOnInput.get() && this.bephax$hasMovementInput()) {
            this.bephax$releaseFreeze("input");
        } else {
            LocalPlayerAccessor player = (LocalPlayerAccessor)this.mc.player;
            if (!this.bephax$freezeAnchored) {
                this.bephax$freezeX = player.getXLast();
                this.bephax$freezeY = player.getYLast();
                this.bephax$freezeZ = player.getZLast();
                this.bephax$freezeAnchored = true;
            }

            this.bephax$freezeTicksLeft = ticksLeft - 1;
            this.mc.player.setDeltaMovement(Vec3.ZERO);
            this.mc.player.setPos(this.bephax$freezeX, this.bephax$freezeY, this.bephax$freezeZ);
            if (this.mc.player.input != null) {
                ((InputAccessor)this.mc.player.input).setMovementForward(0.0F);
                ((InputAccessor)this.mc.player.input).setMovementSideways(0.0F);
            }
        }
    }

    @Unique
    private boolean bephax$isWallsTrapped() {
        if (this.mc.player != null && this.mc.level != null) {
            BlockPos headPos = this.mc.player.blockPosition().above(this.mc.player.isVisuallyCrawling() ? 1 : 2);
            if (this.mc.level.getBlockState(headPos).canBeReplaced()) {
                return false;
            }

            List<BlockPos> surroundPos = this.bephax$getSurroundNoDown(this.mc.player.blockPosition());
            return surroundPos.stream()
                .noneMatch(blockPos -> this.mc.level.getBlockState(this.mc.player.isVisuallyCrawling() ? blockPos : blockPos.above()).canBeReplaced());
        } else {
            return false;
        }
    }

    @Unique
    private List<BlockPos> bephax$getSurroundNoDown(BlockPos center) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(center.north());
        positions.add(center.south());
        positions.add(center.east());
        positions.add(center.west());
        return positions;
    }

    @Unique
    private boolean bephax$isPhased() {
        return this.mc.player != null && this.mc.level != null
            ? PositionUtil.getAllInBox(this.mc.player.getBoundingBox())
                .stream()
                .anyMatch(blockPos -> !this.mc.level.getBlockState(blockPos).canBeReplaced())
            : false;
    }
}
