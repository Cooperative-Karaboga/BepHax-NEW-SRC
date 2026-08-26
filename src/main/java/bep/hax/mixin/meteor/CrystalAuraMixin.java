package bep.hax.mixin.meteor;

import bep.hax.managers.SwapManager;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.GrimUtils;
import bep.hax.util.RotationUtils;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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

@Mixin(value = CrystalAura.class, remap = false)
public abstract class CrystalAuraMixin extends Module {
    @Shadow
    @Final
    private Setting<Boolean> rotate;
    @Unique
    private static final int BEPHAX$RELEASE_TICKS = 4;
    @Unique
    private static final int BEPHAX$AIM_HOLD_TICKS = 6;
    @Unique
    private Setting<Boolean> bephax$grimRotations;
    @Unique
    private Setting<Double> bephax$turnSpeed;
    @Unique
    private Setting<Boolean> bephax$silentSwap;
    @Unique
    private float bephax$holdYaw;
    @Unique
    private float bephax$holdPitch;
    @Unique
    private int bephax$holdTicks;

    public CrystalAuraMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bephax$init(CallbackInfo ci) {
        SettingGroup sg = this.settings.createGroup("Grim Rotations");
        this.bephax$grimRotations = sg.add(
            new Builder()
                .name("grim-rotations")
                .description(
                    "Rotations through the BepHax rotation system: place/break only fire once the server-side look ray hits the target, and both ranges are capped to what the server will actually accept (3 blocks to a crystal, 4.5 to a block) - past that the attack is cancelled and the crystal never pops. Forces Meteor's rotate off."
                )
                .defaultValue(true)
                .build()
        );
        this.bephax$turnSpeed = sg.add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("turn-speed")
                .description(
                    "Degrees rotated per tick. Turns land in one tick when the target is inside this cap, stepping on a mouse-sensitivity grid; lower is stealthier but slows the crystal cycle."
                )
                .defaultValue(90.0)
                .min(10.0)
                .max(180.0)
                .sliderRange(10.0, 180.0)
                .visible(this.bephax$grimRotations::get)
                .build()
        );
        SettingGroup sgSwap = this.settings.createGroup("Silent Swap");
        this.bephax$silentSwap = sgSwap.add(
            new Builder()
                .name("silent-swap")
                .description(
                    "Crystals (and anti-weakness tools) are held server-side only through the BepHax swap system - no visible hotbar flicker, and the swap-back waits for a Grim-legal tick. Set Meteor's auto-switch to Normal or Silent to use it."
                )
                .defaultValue(true)
                .build()
        );
    }

    @Unique
    @EventHandler(priority = 200)
    private void bephax$onTick(Pre event) {
        if (this.isActive() && this.bephax$grimRotations.get()) {
            if (this.rotate.get()) {
                this.rotate.set(false);
            }
        }
    }

    @Unique
    @EventHandler(priority = -200)
    private void bephax$onHoldTick(Pre event) {
        if (this.bephax$holdTicks > 0) {
            this.bephax$holdTicks--;
            if (!this.isActive() || !this.bephax$grimRotations.get() || this.mc.player == null || this.mc.level == null) {
                this.bephax$holdTicks = 0;
            } else if (!this.bephax$noSpoofContext()) {
                if (!SwapManager.getInstance().isForeignSession(this, 30)) {
                    RotationUtils.getInstance().setRotationSilentDirect(this, 30, this.bephax$holdYaw, this.bephax$holdPitch, this.bephax$turnSpeed.get());
                }
            }
        }
    }

    @Inject(method = "isOutOfRange", at = @At("HEAD"), cancellable = true, remap = true)
    private void bephax$grimRange(Vec3 pos, BlockPos blockPos, boolean place, CallbackInfoReturnable<Boolean> cir) {
        if (this.bephax$enabled()) {
            if (!place) {
                if (!this.bephax$withinAttackReach(this.bephax$crystalBox(pos))) {
                    cir.setReturnValue(true);
                }
            } else {
                double blockReach = this.mc.player.blockInteractionRange();
                if (GrimUtils.closestEyeDistanceSqTo(this.mc.player, new AABB(blockPos.below())) > blockReach * blockReach) {
                    cir.setReturnValue(true);
                } else {
                    if (!this.bephax$withinAttackReach(this.bephax$crystalBox(pos))) {
                        cir.setReturnValue(true);
                    }
                }
            }
        }
    }

    @Redirect(
        method = "placeCrystal",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/player/InvUtils;swap(IZ)Z", ordinal = 0),
        remap = true
    )
    private boolean bephax$placeSwap(int slot, boolean swapBack) {
        if (this.bephax$silentSwap.get() && slot >= 0 && slot <= 8) {
            return !this.mc.player.getInventory().getItem(slot).is(Items.END_CRYSTAL)
                ? InvUtils.swap(slot, swapBack)
                : SwapManager.getInstance().hold(this, slot, 30, 4);
        } else {
            return InvUtils.swap(slot, swapBack);
        }
    }

    @Redirect(
        method = "placeCrystal",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/player/InvUtils;swap(IZ)Z", ordinal = 1),
        remap = true
    )
    private boolean bephax$placeSwapBack(int slot, boolean swapBack) {
        if (!this.bephax$silentSwap.get()) {
            return InvUtils.swap(slot, swapBack);
        } else {
            return slot >= 0 && slot <= 8 && ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != slot
                ? InvUtils.swap(slot, swapBack)
                : true;
        }
    }

    @Redirect(
        method = "placeCrystal",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/player/FindItemResult;getHand()Lnet/minecraft/world/InteractionHand;"),
        remap = true
    )
    private InteractionHand bephax$placeHand(FindItemResult item) {
        return this.bephax$silentSwap.get() && !item.isOffhand() && SwapManager.getInstance().getServerSlot() == item.slot()
            ? InteractionHand.MAIN_HAND
            : item.getHand();
    }

    @Redirect(
        method = "doBreak(Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"),
        remap = true
    )
    private ItemStack bephax$breakHandItem(LocalPlayer player) {
        if (!this.bephax$silentSwap.get()) {
            return player.getMainHandItem();
        }

        int slot = SwapManager.getInstance().getServerSlot();
        return slot >= 0 && slot <= 8 ? player.getInventory().getItem(slot) : player.getMainHandItem();
    }

    @Redirect(
        method = "doBreak(Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/player/InvUtils;swap(IZ)Z"),
        remap = true
    )
    private boolean bephax$breakSwap(int slot, boolean swapBack) {
        if (!this.bephax$silentSwap.get()) {
            return InvUtils.swap(slot, swapBack);
        } else {
            return slot >= 0 && slot <= 8 ? SwapManager.getInstance().hold(this, slot, 30, 4) : false;
        }
    }

    @Inject(method = "doBreak(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, remap = true)
    private void bephax$gateBreak(Entity crystal, CallbackInfo ci) {
        if (this.bephax$enabled()) {
            AABB box = crystal.getBoundingBox();
            double reach = this.mc.player.entityInteractionRange();
            if (this.bephax$serverRayHits(box, reach)) {
                this.bephax$refreshHold();
            } else if (this.bephax$noSpoofContext()) {
                ci.cancel();
            } else {
                this.bephax$rotateTo(this.bephax$breakAim(crystal, box, reach));
                ci.cancel();
            }
        }
    }

    @Inject(method = "placeCrystal", at = @At("HEAD"), cancellable = true, remap = true)
    private void bephax$gatePlace(BlockHitResult result, double damage, BlockPos supportBlock, CallbackInfo ci) {
        if (this.bephax$enabled()) {
            AABB box = new AABB(result.getBlockPos());
            if (this.bephax$serverRayHits(box, this.mc.player.blockInteractionRange())) {
                this.bephax$refreshHold();
            } else if (this.bephax$noSpoofContext()) {
                ci.cancel();
            } else {
                Vec3 aim = this.bephax$supportTopAim(result.getBlockPos());
                if (aim == null) {
                    aim = result.getLocation();
                    if (aim.distanceToSqr(this.mc.player.getEyePosition()) < 0.01) {
                        aim = this.bephax$closestPoint(box);
                    }
                }

                this.bephax$rotateTo(aim);
                ci.cancel();
            }
        }
    }

    @Unique
    private boolean bephax$enabled() {
        return this.bephax$grimRotations.get() && this.mc.player != null && this.mc.level != null;
    }

    @Unique
    private AABB bephax$crystalBox(Vec3 crystalPos) {
        return new AABB(
            crystalPos.x - 1.0,
            crystalPos.y,
            crystalPos.z - 1.0,
            crystalPos.x + 1.0,
            crystalPos.y + 2.0,
            crystalPos.z + 1.0
        );
    }

    @Unique
    private boolean bephax$withinAttackReach(AABB box) {
        double reach = this.mc.player.entityInteractionRange();
        return GrimUtils.closestEyeDistanceSqTo(this.mc.player, box) <= reach * reach;
    }

    @Unique
    private Vec3 bephax$supportTopAim(BlockPos support) {
        Vec3 eye = this.mc.player.getEyePosition();
        double topY = support.getY() + 1.0;
        return eye.y <= topY + 0.15
            ? null
            : new Vec3(
                Mth.clamp(eye.x, support.getX() + 0.15, support.getX() + 0.85),
                topY,
                Mth.clamp(eye.z, support.getZ() + 0.15, support.getZ() + 0.85)
            );
    }

    @Unique
    private Vec3 bephax$breakAim(Entity crystal, AABB box, double reach) {
        Vec3 aim = this.bephax$supportTopAim(crystal.blockPosition().below());
        if (aim != null) {
            float[] rot = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), aim);
            if (this.bephax$rayHits(box, reach, rot[0], rot[1])) {
                return aim;
            }
        }

        return this.bephax$closestPoint(box);
    }

    @Unique
    private boolean bephax$serverRayHits(AABB box, double reach) {
        RotationUtils rm = RotationUtils.getInstance();
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

    @Unique
    private boolean bephax$noSpoofContext() {
        return this.mc.player.isFallFlying() || this.mc.player.isSwimming() || this.mc.player.isPassenger();
    }

    @Unique
    private Vec3 bephax$closestPoint(AABB box) {
        Vec3 eye = this.mc.player.getEyePosition();
        Vec3 closest = new Vec3(
            Mth.clamp(eye.x, box.minX, box.maxX),
            Mth.clamp(eye.y, box.minY, box.maxY),
            Mth.clamp(eye.z, box.minZ, box.maxZ)
        );
        return closest.lerp(box.getCenter(), 0.1 + 0.15 * ThreadLocalRandom.current().nextDouble());
    }

    @Unique
    private void bephax$rotateTo(Vec3 aim) {
        float[] rot = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), aim);
        this.bephax$holdYaw = rot[0];
        this.bephax$holdPitch = rot[1];
        this.bephax$holdTicks = 6;
        RotationUtils.getInstance().setRotationSilentDirect(this, 30, rot[0], rot[1], this.bephax$turnSpeed.get());
    }

    @Unique
    private void bephax$refreshHold() {
        if (RotationUtils.getInstance().isOwner(this)) {
            this.bephax$holdTicks = 6;
        }
    }
}
