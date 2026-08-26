package bep.hax.mixin;

import bep.hax.util.BetterChatConfigHolder;
import bep.hax.util.ChatFontRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net/minecraft/client/gui/components/ChatComponent$DrawingBackgroundGraphicsAccess")
public class ChatHudHudMixin {
    @Shadow
    @Final
    private GuiGraphics graphics;

    @Inject(method = "handleMessage", at = @At("HEAD"), cancellable = true)
    private void bephax$customFontChat(int y, float opacity, FormattedCharSequence text, CallbackInfoReturnable<Boolean> cir) {
        if (ChatFontRenderer.tryCapture(this.graphics, y, opacity, text)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "fill", at = @At("HEAD"), cancellable = true, require = 0)
    private void bephax$noChatBackground(int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
        if ((color & 16777215) == 0 && BetterChatConfigHolder.noChatBackground()) {
            ci.cancel();
        }
    }
}
