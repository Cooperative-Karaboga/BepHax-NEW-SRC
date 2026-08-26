package bep.hax.mixin;

import bep.hax.util.BetterTabConfigHolder;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Gui.class, priority = 1100)
public class InGameHudTabFadeMixin {
    @Inject(method = "renderTabList", at = @At("HEAD"))
    private void bephax$advanceTabFade(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        boolean targetVisible = Minecraft.getInstance().options.keyPlayerList.isDown();
        BetterTabConfigHolder.updateFade(targetVisible);
    }

    @ModifyExpressionValue(method = "renderTabList", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean bephax$keepRenderingWhileFading(boolean pressed) {
        return pressed || BetterTabConfigHolder.shouldRenderFade();
    }
}
