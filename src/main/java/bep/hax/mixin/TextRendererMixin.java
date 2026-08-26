package bep.hax.mixin;

import bep.hax.emoji.ColoredGlyphAccess;
import bep.hax.emoji.EmojiData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public class TextRendererMixin {
    @Inject(method = "getGlyph", at = @At("RETURN"))
    private void bephax$tagEmojiGlyph(int codePoint, Style style, CallbackInfoReturnable<BakedGlyph> cir) {
        if (cir.getReturnValue() instanceof ColoredGlyphAccess glyph) {
            glyph.bephax$setEmoji(EmojiData.enabled() && EmojiData.isEmoji(codePoint));
        }
    }
}
