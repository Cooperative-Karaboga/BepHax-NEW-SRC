package bep.hax.mixin;

import bep.hax.emoji.ColoredGlyphAccess;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BakedSheetGlyph.class)
public class BakedGlyphImplMixin implements ColoredGlyphAccess {
    @Unique
    private boolean bephax$emoji;

    @Override
    public void bephax$setEmoji(boolean emoji) {
        this.bephax$emoji = emoji;
    }

    @Override
    public boolean bephax$isEmoji() {
        return this.bephax$emoji;
    }

    @ModifyVariable(method = "createGlyph", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int bephax$forceFullColor(int color) {
        return this.bephax$emoji ? color & 0xFF000000 | 16777215 : color;
    }
}
