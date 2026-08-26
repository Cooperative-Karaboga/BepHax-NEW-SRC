package bep.hax.mixin;

import bep.hax.emoji.EmojiData;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignEditScreen.class)
public class SignEditEmojiMixin {
    @Shadow
    @Final
    private String[] messages;

    @Inject(method = "removed", at = @At("HEAD"))
    private void bephax$expandEmoji(CallbackInfo ci) {
        if (EmojiData.enabled() && this.messages != null) {
            for (int i = 0; i < this.messages.length; i++) {
                if (this.messages[i] != null) {
                    this.messages[i] = EmojiData.expand(this.messages[i]);
                }
            }
        }
    }
}
