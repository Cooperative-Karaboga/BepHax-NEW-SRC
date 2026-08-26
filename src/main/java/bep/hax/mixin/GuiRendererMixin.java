package bep.hax.mixin;

import bep.hax.util.ChatFontRenderer;
import bep.hax.util.MeteorFontReplay;
import bep.hax.util.TabFontRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiRenderer.class, priority = 1100)
public class GuiRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void bephax$flushDeferredFontReplay(CallbackInfo ci) {
        if (MeteorFontReplay.replayAfterGui()) {
            ChatFontRenderer chat = ChatFontRenderer.get();
            TabFontRenderer tab = TabFontRenderer.get();
            if (chat.hasQueued() || tab.hasQueued()) {
                Utils.unscaledProjection();

                try {
                    chat.flush();
                    tab.flush();
                } finally {
                    Utils.scaledProjection();
                }
            }
        }
    }
}
