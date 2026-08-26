package bep.hax.mixin.meteor;

import bep.hax.Bep;
import bep.hax.util.NametagEmoji;
import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.render.Nametags;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Nametags.class, remap = false)
public abstract class NametagsMixin extends Module {
    @Shadow
    @Final
    private SettingGroup sgGeneral;
    @Unique
    @Nullable
    private Setting<Boolean> forceDefaultFont = null;
    @Unique
    private String bephax$emojiSuffix = "";
    @Unique
    private static final Color bephax$WHITE = new Color(255, 255, 255);
    @Unique
    private static boolean bephax$emojiErrorLogged;

    public NametagsMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Inject(method = "renderNametagPlayer", at = @At("HEAD"))
    private void bephax$captureEmoji(Render2DEvent event, Player player, boolean ignored, CallbackInfo ci) {
        this.bephax$emojiSuffix = player == null ? "" : NametagEmoji.suffixFor(player.getUUID());
    }

    @Inject(
        method = "<init>",
        at = @At(
            value = "FIELD",
            target = "Lmeteordevelopment/meteorclient/systems/modules/render/Nametags;scale:Lmeteordevelopment/meteorclient/settings/Setting;",
            shift = Shift.AFTER
        )
    )
    private void addDefaultFontSettings(CallbackInfo ci) {
        this.forceDefaultFont = this.sgGeneral
            .add(
                new Builder()
                    .name("force-default-font")
                    .description("Force nametags to render using the default font, even if a custom GUI font is selected in your Meteor config.")
                    .defaultValue(false)
                    .build()
            );
    }

    @Redirect(
        method = "renderNametagPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;get()Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;"
        )
    )
    private TextRenderer redirectTextRendererPlayer() {
        return this.forceDefaultFont != null && this.forceDefaultFont.get() ? VanillaTextRenderer.INSTANCE : TextRenderer.get();
    }

    @ModifyVariable(method = "renderNametagPlayer", at = @At("STORE"), index = 22)
    private double bephax$widenBackgroundForEmoji(double nameWidth) {
        return this.bephax$emojiSuffix.isEmpty() ? nameWidth : nameWidth + VanillaTextRenderer.INSTANCE.getWidth(this.bephax$emojiSuffix, false);
    }

    @Inject(
        method = "renderNametagPlayer",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;end()V", ordinal = 0)
    )
    private void bephax$renderEndEmoji(
        Render2DEvent event, Player player, boolean ignored, CallbackInfo ci, @Local(index = 33) double widthHalf, @Local(index = 39) double y
    ) {
        if (!this.bephax$emojiSuffix.isEmpty()) {
            try {
                VanillaTextRenderer renderer = VanillaTextRenderer.INSTANCE;
                renderer.setAlpha(1.0);
                double x = widthHalf - renderer.getWidth(this.bephax$emojiSuffix, false);
                renderer.render(this.bephax$emojiSuffix, x, y, bephax$WHITE, false);
            } catch (Throwable t) {
                if (!bephax$emojiErrorLogged) {
                    bephax$emojiErrorLogged = true;
                    Bep.LOG.error("[Nametags] Emoji suffix render failed", t);
                }
            }
        }
    }

    @Redirect(
        method = "renderNametagItem",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;get()Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;"
        )
    )
    private TextRenderer redirectTextRendererItem() {
        return this.forceDefaultFont != null && this.forceDefaultFont.get() ? VanillaTextRenderer.INSTANCE : TextRenderer.get();
    }

    @Redirect(
        method = "renderGenericNametag",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;get()Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;"
        )
    )
    private TextRenderer redirectTextRendererGeneric() {
        return this.forceDefaultFont != null && this.forceDefaultFont.get() ? VanillaTextRenderer.INSTANCE : TextRenderer.get();
    }

    @Redirect(
        method = "renderGenericLivingNametag",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;get()Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;"
        )
    )
    private TextRenderer redirectTextRendererLiving() {
        return this.forceDefaultFont != null && this.forceDefaultFont.get() ? VanillaTextRenderer.INSTANCE : TextRenderer.get();
    }

    @Redirect(
        method = "renderTntNametag",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;get()Lmeteordevelopment/meteorclient/renderer/text/TextRenderer;"
        )
    )
    private TextRenderer redirectTextRendererTnt() {
        return this.forceDefaultFont != null && this.forceDefaultFont.get() ? VanillaTextRenderer.INSTANCE : TextRenderer.get();
    }
}
