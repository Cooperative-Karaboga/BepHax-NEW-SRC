package bep.hax.mixin;

import bep.hax.util.BetterTabConfigHolder;
import bep.hax.util.EnemyManager;
import bep.hax.util.TabFontRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerTabOverlay.class, priority = 1100)
public class PlayerListHudMixin {
    @Unique
    private static boolean bephax$customFontActive() {
        return BetterTabConfigHolder.useCustomFont() && TabFontRenderer.get().isAvailable();
    }

    @Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
    private void onRenderLatencyIcon(GuiGraphics context, int width, int x, int y, PlayerInfo entry, CallbackInfo ci) {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        if (betterTab != null && betterTab.isActive()) {
            if (BetterTabConfigHolder.shouldHidePing()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "getPlayerInfos", at = @At("RETURN"), cancellable = true)
    private void filterPlayerEntries(CallbackInfoReturnable<List<PlayerInfo>> cir) {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        if (betterTab != null && betterTab.isActive()) {
            boolean hideEnemies = BetterTabConfigHolder.shouldHideEnemies();
            boolean showOnlyFriends = BetterTabConfigHolder.shouldShowOnlyFriends();
            if (hideEnemies || showOnlyFriends) {
                List<PlayerInfo> entries = cir.getReturnValue();
                List<PlayerInfo> filtered = entries.stream().filter(entry -> {
                    if (entry != null && entry.getProfile() != null) {
                        String name = entry.getProfile().name();
                        if (showOnlyFriends) {
                            return Friends.get().get(entry) != null;
                        } else {
                            return hideEnemies ? !EnemyManager.get().isEnemy(name) : true;
                        }
                    } else {
                        return true;
                    }
                }).toList();
                cir.setReturnValue(filtered);
            }
        }
    }

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerFaceRenderer;draw(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;IIIZZI)V")
    )
    private void wrapDrawSkin(
        GuiGraphics context, Identifier texture, int x, int y, int size, boolean hasHatLayer, boolean hasJacketLayer, int alpha, Operation<Void> original
    ) {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        if (betterTab == null || !betterTab.isActive() || !BetterTabConfigHolder.shouldHideIcons()) {
            if (BetterTabConfigHolder.isFadeActive()) {
                alpha = BetterTabConfigHolder.applyFadeAlpha(alpha);
            }

            original.call(context, texture, x, y, size, hasHatLayer, hasJacketLayer, alpha);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void wrapFill(GuiGraphics context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (BetterTabConfigHolder.isFadeActive()) {
            color = BetterTabConfigHolder.applyFadeAlpha(color);
        }

        original.call(context, x1, y1, x2, y2, color);
    }

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V")
    )
    private void wrapDrawOrderedText(GuiGraphics context, Font textRenderer, FormattedCharSequence text, int x, int y, int color, Operation<Void> original) {
        if (BetterTabConfigHolder.isFadeActive()) {
            color = BetterTabConfigHolder.applyFadeAlpha(color);
        }

        if (bephax$customFontActive()) {
            TabFontRenderer.get().submit(text, x, y, color);
            original.call(context, textRenderer, text, x, y, color & 16777215 | 16777216);
        } else {
            original.call(context, textRenderer, text, x, y, color);
        }
    }

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V")
    )
    private void wrapDrawText(GuiGraphics context, Font textRenderer, Component text, int x, int y, int color, Operation<Void> original) {
        if (BetterTabConfigHolder.isFadeActive()) {
            color = BetterTabConfigHolder.applyFadeAlpha(color);
        }

        if (bephax$customFontActive()) {
            TabFontRenderer.get().submit(text.getVisualOrderText(), x, y, color);
            original.call(context, textRenderer, text, x, y, color & 16777215 | 16777216);
        } else {
            original.call(context, textRenderer, text, x, y, color);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"))
    private int bephax$visitableWidthWithCustomFont(Font textRenderer, FormattedText text, Operation<Integer> original) {
        return bephax$customFontActive() ? TabFontRenderer.get().scaledWidth(text) : original.call(textRenderer, text);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/util/FormattedCharSequence;)I"))
    private int bephax$orderedWidthWithCustomFont(Font textRenderer, FormattedCharSequence text, Operation<Integer> original) {
        return bephax$customFontActive() ? TabFontRenderer.get().scaledWidth(text) : original.call(textRenderer, text);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
    private int bephax$stringWidthWithCustomFont(Font textRenderer, String text, Operation<Integer> original) {
        return bephax$customFontActive() ? TabFontRenderer.get().scaledWidth(text) : original.call(textRenderer, text);
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 13))
    private int bephax$columnPadding(int padding) {
        if (!bephax$customFontActive()) {
            return padding;
        } else {
            return BetterTabConfigHolder.shouldHidePing() ? 3 : 10;
        }
    }

    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 0), index = 15)
    private int adjustColumnWidth(int width) {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        return betterTab != null && betterTab.isActive() && BetterTabConfigHolder.shouldHidePing() && betterTab.accurateLatency.get() ? width - 30 : width;
    }

    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 2), index = 15)
    private int bephax$fixedColumnWidth(int columnWidth) {
        if (!BetterTabConfigHolder.useFixedSectionWidth()) {
            return columnWidth;
        }

        int max = MeteorClient.mc.getWindow().getGuiScaledWidth() - 50;
        return Math.min(BetterTabConfigHolder.getSectionWidth(), Math.max(20, max));
    }
}
