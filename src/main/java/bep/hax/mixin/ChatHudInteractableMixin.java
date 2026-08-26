package bep.hax.mixin;

import bep.hax.util.BetterChatConfigHolder;
import bep.hax.util.ChatFontRenderer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.ActiveTextCollector.Parameters;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net/minecraft/client/gui/components/ChatComponent$DrawingFocusedGraphicsAccess")
public class ChatHudInteractableMixin {
    @Shadow
    @Final
    private GuiGraphics graphics;

    @ModifyExpressionValue(
        method = "handleMessage",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ActiveTextCollector$Parameters;withOpacity(F)Lnet/minecraft/client/gui/ActiveTextCollector$Parameters;")
    )
    private Parameters bephax$customFontChat(Parameters transformation, int y, float opacity, FormattedCharSequence text) {
        return !ChatFontRenderer.tryCapture(this.graphics, y, opacity, text) ? transformation : transformation.withOpacity(0.0F);
    }

    @Inject(method = "fill", at = @At("HEAD"), cancellable = true, require = 0)
    private void bephax$noChatBackground(int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
        if ((color & 16777215) == 0 && BetterChatConfigHolder.noChatBackground()) {
            ci.cancel();
        }
    }
}
