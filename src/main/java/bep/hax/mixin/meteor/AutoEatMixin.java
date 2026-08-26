package bep.hax.mixin.meteor;

import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.InventoryManager;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutoEat.class, remap = false)
public abstract class AutoEatMixin {
    @Shadow
    public boolean eating;
    @Shadow
    private int slot;
    @Shadow
    private int prevSlot;
    @Shadow
    @Final
    private Setting<Boolean> pauseBaritone;
    @Unique
    private boolean bephax$wasBaritone = false;

    @Shadow
    protected abstract void stopEating();

    @Inject(method = "eat", at = @At("HEAD"), cancellable = true)
    private void onEat(CallbackInfo ci) {
        ci.cancel();
        if (bephax$isFacingContainer()) {
            if (this.eating) {
                this.stopEating();
            }
        } else if (!this.isSlotValid()) {
            this.stopEating();
        } else {
            if (!this.eating && this.pauseBaritone.get() && PathManagers.get().isPathing() && !this.bephax$wasBaritone) {
                this.bephax$wasBaritone = true;
                PathManagers.get().pause();
            }

            InventoryManager invManager = InventoryManager.getInstance();
            int serverSlot = invManager.getServerSlot();
            if (serverSlot != this.slot && this.slot != 40) {
                if (((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot() != this.slot) {
                    ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).setSelectedSlot(this.slot);
                }

                invManager.setSlotForced(this.slot);
            } else if (this.slot != 40) {
                this.bephax$changeSlot(this.slot);
            }

            invManager.setEating(true);
            boolean shouldPressKey = MeteorClient.mc.screen == null;
            if (shouldPressKey) {
                MeteorClient.mc.options.keyUse.setDown(true);
            }

            if (!MeteorClient.mc.player.isUsingItem()) {
                Utils.rightClick();
            }

            this.eating = true;
        }
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void onTickValidate(CallbackInfo ci) {
        if (bephax$isFacingContainer()) {
            if (this.eating) {
                this.stopEating();
            }

            ci.cancel();
        } else if (this.eating && !this.isSlotValid()) {
            this.stopEating();
        } else {
            if (this.eating) {
                boolean shouldPressKey = MeteorClient.mc.screen == null;
                if (MeteorClient.mc.options != null) {
                    if (shouldPressKey && !MeteorClient.mc.options.keyUse.isDown()) {
                        MeteorClient.mc.options.keyUse.setDown(true);
                    } else if (!shouldPressKey && MeteorClient.mc.options.keyUse.isDown()) {
                        MeteorClient.mc.options.keyUse.setDown(false);
                    }
                }
            }
        }
    }

    @Inject(method = "stopEating", at = @At("HEAD"))
    private void onStopEating(CallbackInfo ci) {
        InventoryManager.getInstance().setEating(false);
        this.bephax$changeSlot(this.prevSlot);
        if (MeteorClient.mc.options != null) {
            MeteorClient.mc.options.keyUse.setDown(false);
        }

        if (this.pauseBaritone.get() && this.bephax$wasBaritone) {
            this.bephax$wasBaritone = false;
            PathManagers.get().resume();
        }
    }

    @Inject(method = "onDeactivate", at = @At("HEAD"))
    private void onDeactivateCleanup(CallbackInfo ci) {
        InventoryManager.getInstance().setEating(false);
        if (MeteorClient.mc.options != null) {
            MeteorClient.mc.options.keyUse.setDown(false);
        }

        if (this.pauseBaritone.get() && this.bephax$wasBaritone) {
            this.bephax$wasBaritone = false;
            PathManagers.get().resume();
        }

        this.eating = false;
    }

    @Unique
    private void bephax$changeSlot(int slot) {
        InvUtils.swap(slot, false);
        this.slot = slot;
    }

    @Unique
    private static boolean bephax$isFacingContainer() {
        if (MeteorClient.mc.level != null && MeteorClient.mc.hitResult != null) {
            if (MeteorClient.mc.hitResult.getType() != Type.BLOCK) {
                return false;
            }

            BlockHitResult blockHit = (BlockHitResult)MeteorClient.mc.hitResult;
            BlockState state = MeteorClient.mc.level.getBlockState(blockHit.getBlockPos());
            return state.getMenuProvider(MeteorClient.mc.level, blockHit.getBlockPos()) != null;
        } else {
            return false;
        }
    }

    @Unique
    private boolean isSlotValid() {
        if (MeteorClient.mc.player == null) {
            return false;
        }

        ItemStack stack;
        if (this.slot == 40) {
            stack = MeteorClient.mc.player.getOffhandItem();
        } else {
            if (this.slot < 0 || this.slot >= 9) {
                return false;
            }

            stack = MeteorClient.mc.player.getInventory().getItem(this.slot);
        }

        return !stack.isEmpty() && stack.get(DataComponents.FOOD) != null;
    }
}
