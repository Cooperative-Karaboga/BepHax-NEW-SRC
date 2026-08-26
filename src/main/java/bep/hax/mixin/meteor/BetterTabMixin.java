package bep.hax.mixin.meteor;

import bep.hax.util.BetterTabConfigHolder;
import bep.hax.util.DektoOwner;
import bep.hax.util.EnemyColorManager;
import bep.hax.util.EnemyManager;
import bep.hax.util.NametagEmoji;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BetterTab.class, remap = false)
public class BetterTabMixin {
    @Shadow
    private SettingGroup sgGeneral;
    @Shadow
    public Setting<Boolean> accurateLatency;
    @Unique
    private Setting<Boolean> bephax$hidePing;
    @Unique
    private Setting<Boolean> bephax$hideEnemies;
    @Unique
    private Setting<Boolean> bephax$showOnlyFriends;
    @Unique
    private Setting<Boolean> bephax$hideIcons;
    @Unique
    private Setting<Boolean> bephax$fade;
    @Unique
    private Setting<Double> bephax$fadeDuration;
    @Unique
    private Setting<Boolean> bephax$customFont;
    @Unique
    private Setting<Boolean> bephax$fixedSectionWidth;
    @Unique
    private Setting<Integer> bephax$sectionWidth;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void addHidePingSetting(CallbackInfo ci) {
        this.bephax$hidePing = this.sgGeneral
            .add(
                new Builder()
                    .name("hide-ping")
                    .description("Completely removes ping/latency from the player list. Overrides accurate-latency when enabled.")
                    .defaultValue(true)
                    .build()
            );
        BetterTabConfigHolder.setHidePingSetting(this.bephax$hidePing);
        this.bephax$hideEnemies = this.sgGeneral
            .add(new Builder().name("hide-enemies").description("Hides enemies from the tab list.").defaultValue(false).build());
        BetterTabConfigHolder.setHideEnemiesSetting(this.bephax$hideEnemies);
        this.bephax$showOnlyFriends = this.sgGeneral
            .add(new Builder().name("show-only-friends").description("Only shows friends in the tab list.").defaultValue(false).build());
        BetterTabConfigHolder.setShowOnlyFriendsSetting(this.bephax$showOnlyFriends);
        this.bephax$hideIcons = this.sgGeneral
            .add(new Builder().name("hide-icons").description("Hides player skin icons in the tab list.").defaultValue(false).build());
        BetterTabConfigHolder.setHideIconsSetting(this.bephax$hideIcons);
        this.bephax$fade = this.sgGeneral
            .add(
                new Builder()
                    .name("fade-animation")
                    .description("Smoothly fades the tab list in and out instead of snapping when you hold/release the player list key.")
                    .defaultValue(true)
                    .build()
            );
        BetterTabConfigHolder.setFadeSetting(this.bephax$fade);
        this.bephax$fadeDuration = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("fade-duration")
                    .description("How long the fade in/out takes, in seconds.")
                    .defaultValue(0.2)
                    .min(0.0)
                    .sliderRange(0.0, 1.0)
                    .visible(() -> this.bephax$fade.get())
                    .build()
            );
        BetterTabConfigHolder.setFadeDurationSetting(this.bephax$fadeDuration);
        this.bephax$customFont = this.sgGeneral
            .add(
                new Builder()
                    .name("custom-font")
                    .description("Renders the tab list with Meteor's custom font instead of the vanilla font.")
                    .defaultValue(false)
                    .build()
            );
        BetterTabConfigHolder.setCustomFontSetting(this.bephax$customFont);
        this.bephax$fixedSectionWidth = this.sgGeneral
            .add(
                new Builder()
                    .name("fixed-section-width")
                    .description("Forces every player-list column to a fixed width instead of sizing it to the longest name.")
                    .defaultValue(false)
                    .build()
            );
        BetterTabConfigHolder.setFixedSectionWidthSetting(this.bephax$fixedSectionWidth);
        this.bephax$sectionWidth = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("section-width")
                    .description("Width in pixels of each player-list column when fixed-section-width is enabled.")
                    .defaultValue(90)
                    .min(20)
                    .sliderRange(20, 240)
                    .visible(() -> this.bephax$fixedSectionWidth.get())
                    .build()
            );
        BetterTabConfigHolder.setSectionWidthSetting(this.bephax$sectionWidth);
    }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void injectEnemyColor(PlayerInfo playerListEntry, CallbackInfoReturnable<Component> cir) {
        if (playerListEntry != null && playerListEntry.getProfile() != null) {
            String suffix = NametagEmoji.suffixFor(playerListEntry.getProfile().id());
            if (DektoOwner.is(playerListEntry.getProfile().id())) {
                cir.setReturnValue(cir.getReturnValue().copy().append(suffix));
            } else {
                String playerName = playerListEntry.getProfile().name();
                Setting<SettingColor> enemyColor = EnemyColorManager.getEnemyColorSetting();
                if (EnemyManager.get().isEnemy(playerName) && enemyColor != null) {
                    int color = enemyColor.get().getPacked();
                    Component original = cir.getReturnValue();
                    String textContent = original.getString();
                    cir.setReturnValue(Component.literal(textContent + suffix).withStyle(style -> style.withColor(color)));
                } else if (!suffix.isEmpty()) {
                    cir.setReturnValue(cir.getReturnValue().copy().append(suffix));
                }
            }
        }
    }
}
