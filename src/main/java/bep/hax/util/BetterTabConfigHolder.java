package bep.hax.util;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import net.minecraft.util.Mth;

public class BetterTabConfigHolder {
    private static Setting<Boolean> hidePingSetting;
    private static Setting<Boolean> hideEnemiesSetting;
    private static Setting<Boolean> showOnlyFriendsSetting;
    private static Setting<Boolean> hideIconsSetting;
    private static Setting<Boolean> fadeSetting;
    private static Setting<Double> fadeDurationSetting;
    private static Setting<Boolean> customFontSetting;
    private static Setting<Boolean> fixedSectionWidthSetting;
    private static Setting<Integer> sectionWidthSetting;
    private static float fadeAlpha;
    private static long lastNanos;

    public static void setHidePingSetting(Setting<Boolean> setting) {
        hidePingSetting = setting;
    }

    public static void setCustomFontSetting(Setting<Boolean> setting) {
        customFontSetting = setting;
    }

    public static boolean useCustomFont() {
        if (customFontSetting != null && customFontSetting.get()) {
            BetterTab betterTab = Modules.get().get(BetterTab.class);
            return betterTab != null && betterTab.isActive();
        } else {
            return false;
        }
    }

    public static void setFixedSectionWidthSetting(Setting<Boolean> setting) {
        fixedSectionWidthSetting = setting;
    }

    public static void setSectionWidthSetting(Setting<Integer> setting) {
        sectionWidthSetting = setting;
    }

    public static boolean useFixedSectionWidth() {
        if (fixedSectionWidthSetting != null && fixedSectionWidthSetting.get()) {
            BetterTab betterTab = Modules.get().get(BetterTab.class);
            return betterTab != null && betterTab.isActive();
        } else {
            return false;
        }
    }

    public static int getSectionWidth() {
        return sectionWidthSetting != null ? sectionWidthSetting.get() : 0;
    }

    public static boolean shouldHidePing() {
        return hidePingSetting != null && hidePingSetting.get();
    }

    public static void setHideEnemiesSetting(Setting<Boolean> setting) {
        hideEnemiesSetting = setting;
    }

    public static boolean shouldHideEnemies() {
        return hideEnemiesSetting != null && hideEnemiesSetting.get();
    }

    public static void setShowOnlyFriendsSetting(Setting<Boolean> setting) {
        showOnlyFriendsSetting = setting;
    }

    public static boolean shouldShowOnlyFriends() {
        return showOnlyFriendsSetting != null && showOnlyFriendsSetting.get();
    }

    public static void setHideIconsSetting(Setting<Boolean> setting) {
        hideIconsSetting = setting;
    }

    public static boolean shouldHideIcons() {
        return hideIconsSetting != null && hideIconsSetting.get();
    }

    public static void setFadeSetting(Setting<Boolean> setting) {
        fadeSetting = setting;
    }

    public static void setFadeDurationSetting(Setting<Double> setting) {
        fadeDurationSetting = setting;
    }

    public static boolean isFadeActive() {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        return betterTab != null && betterTab.isActive() && fadeSetting != null && fadeSetting.get();
    }

    public static void updateFade(boolean targetVisible) {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : (float)(now - lastNanos) / 1.0E9F;
        lastNanos = now;
        dt = Mth.clamp(dt, 0.0F, 0.1F);
        if (!isFadeActive()) {
            fadeAlpha = targetVisible ? 1.0F : 0.0F;
        } else {
            float target = targetVisible ? 1.0F : 0.0F;
            double duration = fadeDurationSetting != null ? fadeDurationSetting.get() : 0.2;
            float step = duration <= 0.0 ? 1.0F : (float)(dt / duration);
            if (fadeAlpha < target) {
                fadeAlpha = Math.min(target, fadeAlpha + step);
            } else if (fadeAlpha > target) {
                fadeAlpha = Math.max(target, fadeAlpha - step);
            }
        }
    }

    public static float getFadeAlpha() {
        return fadeAlpha;
    }

    public static boolean shouldRenderFade() {
        return isFadeActive() && fadeAlpha > 0.001F;
    }

    public static int applyFadeAlpha(int color) {
        int a = color >>> 24 & 0xFF;
        if (a == 0) {
            a = 255;
        }

        a = Mth.clamp(Math.round(a * fadeAlpha), 0, 255);
        return a << 24 | color & 16777215;
    }
}
