package bep.hax.mixin.meteor;

import bep.hax.managers.SwapManager;
import bep.hax.util.GrimUtils;
import bep.hax.util.RotationUtils;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura.AttackItems;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura.RotationMode;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura.ShieldMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KillAura.class, remap = false)
public abstract class KillAuraMixin extends Module {
    @Shadow
    @Final
    private Setting<RotationMode> rotation;
    @Shadow
    @Final
    private Setting<AttackItems> attackWhenHolding;
    @Shadow
    @Final
    private Setting<List<Item>> weapons;
    @Shadow
    @Final
    private Setting<ShieldMode> shieldMode;
    @Shadow
    @Final
    private Setting<Boolean> autoSwitch;
    @Shadow
    @Final
    private Setting<Set<EntityType<?>>> entities;
    @Shadow
    @Final
    private Setting<Double> range;
    @Shadow
    @Final
    private List<Entity> targets;
    @Shadow
    public boolean attacking;
    @Unique
    private static final int BEPHAX$RELEASE_TICKS = 4;
    @Unique
    private Setting<Boolean> bephax$grimRotations;
    @Unique
    private Setting<Double> bephax$turnSpeed;
    @Unique
    private Setting<Boolean> bephax$predictMovement;
    @Unique
    private Setting<Boolean> bephax$requireRaytrace;
    @Unique
    private Setting<Boolean> bephax$grimReach;
    @Unique
    private Setting<Boolean> bephax$silentSwap;
    @Unique
    private Setting<Boolean> bephax$preCharge;
    @Unique
    private Setting<Double> bephax$preChargeRange;
    @Unique
    private int bephax$chargeTicks;
    @Unique
    private ItemStack bephax$lastServerStack = ItemStack.EMPTY;

    public KillAuraMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bephax$init(CallbackInfo ci) {
        SettingGroup sg = this.settings.createGroup("Grim Rotations");
        this.bephax$grimRotations = sg.add(
            new Builder()
                .name("grim-rotations")
                .description("Smooth server-side rotations through the BepHax rotation system. Forces Meteor's rotate to None.")
                .defaultValue(true)
                .build()
        );
        this.bephax$turnSpeed = sg.add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("turn-speed")
                .description(
                    "Degrees rotated per tick towards the target. The full remaining angle is covered every tick up to this cap, so anything inside it lands in a single tick."
                )
                .defaultValue(180.0)
                .min(10.0)
                .max(180.0)
                .sliderRange(10.0, 180.0)
                .visible(this.bephax$grimRotations::get)
                .build()
        );
        this.bephax$predictMovement = sg.add(
            new Builder()
                .name("predict-movement")
                .description("Biases the aim towards where the target's hitbox is moving, without ever leaving the hitbox they are actually in.")
                .defaultValue(true)
                .visible(this.bephax$grimRotations::get)
                .build()
        );
        this.bephax$requireRaytrace = sg.add(
            new Builder()
                .name("require-raytrace")
                .description("Only attacks when the rotation the server last saw actually intersects the target's hitbox within legal reach.")
                .defaultValue(true)
                .visible(this.bephax$grimRotations::get)
                .build()
        );
        this.bephax$grimReach = sg.add(
            new Builder()
                .name("grim-reach")
                .description(
                    "Ignores targets past the reach the server will accept (3 blocks eye-to-hitbox). Meteor's 4.5 range and 3.5 walls-range are measured from your feet, so every hit in the gap is cancelled server-side and simply does nothing."
                )
                .defaultValue(true)
                .visible(this.bephax$grimRotations::get)
                .build()
        );
        SettingGroup sgSwap = this.settings.createGroup("Silent Swap");
        this.bephax$silentSwap = sgSwap.add(
            new Builder()
                .name("silent-swap")
                .description(
                    "Attacks with the best weapon held server-side only - you keep seeing (and holding) your current item. Forces Meteor's auto-switch off and waits for the weapon's full attack charge."
                )
                .defaultValue(true)
                .build()
        );
        this.bephax$preCharge = sgSwap.add(
            new Builder()
                .name("pre-charge")
                .description(
                    "Holds the weapon server-side while a target is still closing in, so the attack charge the swap resets has already recovered when they come into reach."
                )
                .defaultValue(true)
                .visible(this.bephax$silentSwap::get)
                .build()
        );
        this.bephax$preChargeRange = sgSwap.add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("pre-charge-range")
                .description("Extra blocks beyond attack range at which the weapon hold starts.")
                .defaultValue(10.0)
                .min(0.0)
                .max(32.0)
                .sliderRange(0.0, 16.0)
                .visible(() -> this.bephax$silentSwap.get() && this.bephax$preCharge.get())
                .build()
        );
    }

    @Unique
    @EventHandler(priority = 100)
    private void bephax$onSwapTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            this.bephax$noteServerItem(true);
            if (this.isActive() && this.bephax$silentSwap.get()) {
                if (this.autoSwitch.get()) {
                    this.autoSwitch.set(false);
                }

                if (this.attacking || this.bephax$preCharge.get() && this.bephax$preChargeTargetNearby()) {
                    int slot = this.bephax$weaponSlot();
                    int priority = this.attacking ? 20 : 3;
                    if (slot != -1 && !this.bephax$useBlocksSwap(slot)) {
                        SwapManager.getInstance().hold(this, slot, priority, 4);
                    }
                }
            }
        }
    }

    @Unique
    private boolean bephax$useBlocksSwap(int slot) {
        return this.mc.player.isUsingItem()
            && this.mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND
            && SwapManager.getInstance().getServerSlot() != slot;
    }

    @Inject(method = "entityCheck", at = @At("RETURN"), cancellable = true, remap = true)
    private void bephax$grimReachFilter(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && this.bephax$grimReach.get() && this.bephax$grimRotations.get()) {
            if (this.mc.player != null) {
                double reach = this.mc.player.entityInteractionRange();
                if (GrimUtils.closestEyeDistanceSqTo(this.mc.player, entity.getBoundingBox()) > reach * reach) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @Unique
    private boolean bephax$preChargeTargetNearby() {
        double r = this.range.get() + this.bephax$preChargeRange.get();
        double rSq = r * r;

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity != this.mc.player
                && entity.isAlive()
                && this.entities.get().contains(entity.getType())
                && !(entity instanceof Player player && (player.isCreative() || !Friends.get().shouldAttack(player)))
                && this.mc.player.distanceToSqr(entity) <= rSq) {
                return true;
            }
        }

        return false;
    }

    @Unique
    @EventHandler(priority = -200)
    private void bephax$onTick(Pre event) {
        if (this.isActive() && this.bephax$grimRotations.get()) {
            if (this.mc.player != null && this.mc.level != null) {
                if (this.rotation.get() != RotationMode.None) {
                    this.rotation.set(RotationMode.None);
                }

                if (this.attacking && !this.targets.isEmpty()) {
                    if (!this.mc.player.isFallFlying() && !this.mc.player.isSwimming() && !this.mc.player.isPassenger()) {
                        if (!this.bephax$silentSwap.get() || this.bephax$weaponSlot() == -1 || !SwapManager.getInstance().isForeignSession(this, 20)) {
                            Entity primary = this.targets.getFirst();
                            Vec3 aim = this.bephax$aimPoint(primary);
                            float[] rot = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), aim);
                            RotationUtils.getInstance().setRotationSilentDirect(this, 20, rot[0], rot[1], this.bephax$turnSpeed.get());
                        }
                    }
                }
            }
        }
    }

    @Redirect(method = "onTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"), remap = true)
    private ItemStack bephax$effectiveWeaponCheck(LocalPlayer player) {
        if (!this.bephax$silentSwap.get()) {
            return player.getMainHandItem();
        }

        int slot = this.bephax$weaponSlot();
        return slot == -1 ? player.getMainHandItem() : player.getInventory().getItem(slot);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true, remap = true)
    private void bephax$gateAttack(Entity target, CallbackInfo ci) {
        if (this.mc.player != null) {
            if (this.bephax$grimRotations.get() && this.bephax$requireRaytrace.get() && !this.bephax$serverAimIntersects(target)) {
                ci.cancel();
            } else if (this.bephax$silentSwap.get()) {
                int slot = this.bephax$weaponSlot();
                if (slot != -1 && this.bephax$useBlocksSwap(slot)) {
                    ci.cancel();
                } else if (slot != -1 && !SwapManager.getInstance().hold(this, slot, 20, 4)) {
                    ci.cancel();
                } else {
                    this.bephax$noteServerItem(false);
                    if (this.bephax$chargeTicks < this.bephax$chargeNeeded(this.bephax$serverStack())) {
                        ci.cancel();
                    }
                }
            }
        }
    }

    @Inject(method = "attack", at = @At("TAIL"), remap = true)
    private void bephax$afterAttack(Entity target, CallbackInfo ci) {
        this.bephax$chargeTicks = 0;
    }

    @Unique
    private ItemStack bephax$serverStack() {
        int slot = SwapManager.getInstance().getServerSlot();
        return slot >= 0 && slot <= 8 ? this.mc.player.getInventory().getItem(slot) : this.mc.player.getMainHandItem();
    }

    @Unique
    private void bephax$noteServerItem(boolean tickIncrement) {
        ItemStack stack = this.bephax$serverStack();
        if (!ItemStack.isSameItem(stack, this.bephax$lastServerStack)) {
            this.bephax$chargeTicks = 0;
            this.bephax$lastServerStack = stack.copy();
        } else if (tickIncrement && this.bephax$chargeTicks < 200) {
            this.bephax$chargeTicks++;
        }
    }

    @Unique
    private int bephax$chargeNeeded(ItemStack stack) {
        double[] add = new double[]{0.0};
        double[] mulBase = new double[]{0.0};
        double[] mulTotal = new double[]{1.0};
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        mods.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() == Attributes.ATTACK_SPEED.value()) {
                switch (modifier.operation()) {
                    case ADD_VALUE:
                        add[0] += modifier.amount();
                        break;
                    case ADD_MULTIPLIED_BASE:
                        mulBase[0] += modifier.amount();
                        break;
                    case ADD_MULTIPLIED_TOTAL:
                        mulTotal[0] *= 1.0 + modifier.amount();
                }
            }
        });
        double base = 4.0 + add[0];
        double speed = base * (1.0 + mulBase[0]) * mulTotal[0];
        if (speed <= 0.05) {
            return 100;
        }

        double delay = 1.0 / speed * 20.0;
        return Math.max(0, Mth.ceil(delay - 0.5));
    }

    @Unique
    private int bephax$weaponSlot() {
        if (this.attackWhenHolding.get() == AttackItems.All && !this.bephax$shouldShieldBreak()) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            if (this.bephax$acceptableWeapon(this.mc.player.getInventory().getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    @Unique
    private boolean bephax$shouldShieldBreak() {
        if (this.shieldMode.get() != ShieldMode.Break) {
            return false;
        }

        for (Entity target : this.targets) {
            if (target instanceof Player player && player.isBlocking()) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private boolean bephax$acceptableWeapon(ItemStack stack) {
        if (this.bephax$shouldShieldBreak()) {
            return stack.getItem() instanceof AxeItem;
        } else if (this.attackWhenHolding.get() == AttackItems.All) {
            return true;
        } else if (this.weapons.get().contains(Items.DIAMOND_SWORD) && stack.is(ItemTags.SWORDS)) {
            return true;
        } else if (this.weapons.get().contains(Items.DIAMOND_AXE) && stack.is(ItemTags.AXES)) {
            return true;
        } else if (this.weapons.get().contains(Items.DIAMOND_PICKAXE) && stack.is(ItemTags.PICKAXES)) {
            return true;
        } else if (this.weapons.get().contains(Items.DIAMOND_SHOVEL) && stack.is(ItemTags.SHOVELS)) {
            return true;
        } else if (this.weapons.get().contains(Items.DIAMOND_HOE) && stack.is(ItemTags.HOES)) {
            return true;
        } else if (this.weapons.get().contains(Items.MACE) && stack.getItem() instanceof MaceItem) {
            return true;
        } else {
            return this.weapons.get().contains(Items.DIAMOND_SPEAR) && stack.is(ItemTags.SPEARS)
                ? true
                : this.weapons.get().contains(Items.TRIDENT) && stack.getItem() instanceof TridentItem;
        }
    }

    @Unique
    private Vec3 bephax$aimPoint(Entity target) {
        AABB box = target.getBoundingBox();
        AABB aimBox = this.bephax$predictMovement.get() ? box.move(target.getDeltaMovement()) : box;
        Vec3 eye = this.mc.player.getEyePosition();
        Vec3 closest = new Vec3(
            Mth.clamp(eye.x, aimBox.minX, aimBox.maxX),
            Mth.clamp(eye.y, aimBox.minY, aimBox.maxY),
            Mth.clamp(eye.z, aimBox.minZ, aimBox.maxZ)
        );
        closest = closest.lerp(aimBox.getCenter(), 0.1 + 0.15 * ThreadLocalRandom.current().nextDouble());
        return new Vec3(
            this.bephax$clampInside(closest.x, box.minX, box.maxX),
            this.bephax$clampInside(closest.y, box.minY, box.maxY),
            this.bephax$clampInside(closest.z, box.minZ, box.maxZ)
        );
    }

    @Unique
    private double bephax$clampInside(double v, double lo, double hi) {
        double inset = Math.min(0.05, (hi - lo) * 0.25);
        return Mth.clamp(v, lo + inset, hi - inset);
    }

    @Unique
    private boolean bephax$serverAimIntersects(Entity target) {
        AABB box = target.getBoundingBox();
        RotationUtils rm = RotationUtils.getInstance();
        double reach = this.mc.player.entityInteractionRange();
        return this.bephax$rayHits(box, reach, rm.getServerYaw(), rm.getServerPitch())
            ? true
            : rm.isRotating() && this.bephax$rayHits(box, reach, rm.getSentYaw(), rm.getSentPitch());
    }

    @Unique
    private boolean bephax$rayHits(AABB box, double reach, float yaw, float pitch) {
        Vec3 dir = RotationUtils.getRotationVector(pitch, yaw);
        Vec3 base = this.mc.player.position();

        for (double h : GrimUtils.getPossibleEyeHeights(this.mc.player)) {
            Vec3 eye = base.add(0.0, h, 0.0);
            if (box.contains(eye) || box.clip(eye, eye.add(dir.scale(reach))).isPresent()) {
                return true;
            }
        }

        return false;
    }
}
