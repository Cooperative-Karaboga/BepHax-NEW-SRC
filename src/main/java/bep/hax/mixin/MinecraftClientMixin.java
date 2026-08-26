package bep.hax.mixin;

import bep.hax.managers.SwapManager;
import bep.hax.util.InventoryManager;
import bep.hax.util.PeekNavigationConfig;
import meteordevelopment.meteorclient.gui.screens.ContainerInventoryScreen;
import meteordevelopment.meteorclient.utils.render.PeekScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Shadow
    public LocalPlayer player;
    @Shadow
    @Nullable
    public Screen screen;
    @Shadow
    @Nullable
    public HitResult hitResult;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(@Nullable Screen screen, CallbackInfo ci) {
        if (!PeekNavigationConfig.isNavigatingBack()
            && (screen instanceof PeekScreen || screen instanceof ContainerInventoryScreen)
            && this.screen != null
            && !(this.screen instanceof PeekScreen)
            && !(this.screen instanceof ContainerInventoryScreen)) {
            PeekNavigationConfig.pushScreen(this.screen);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void onDoItemUse(CallbackInfo ci) {
        if (this.player != null) {
            SwapManager.getInstance().onUserAction();
            ItemStack mainHand = this.player.getMainHandItem();
            ItemStack offHand = this.player.getOffhandItem();
            boolean mainHandIsFood = !mainHand.isEmpty() && mainHand.get(DataComponents.FOOD) != null;
            boolean offHandIsFood = !offHand.isEmpty() && offHand.get(DataComponents.FOOD) != null;
            if (mainHandIsFood || offHandIsFood) {
                InventoryManager.getInstance().setEating(true);
            }
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void bephax$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (this.player != null) {
            if (this.hitResult != null && this.hitResult.getType() == Type.ENTITY) {
                SwapManager.getInstance().onUserAction();
            }
        }
    }
}
