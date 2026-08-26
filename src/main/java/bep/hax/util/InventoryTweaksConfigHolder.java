package bep.hax.util;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.InventoryTweaks;
import net.minecraft.util.Mth;

public class InventoryTweaksConfigHolder {
    private static Setting<Boolean> hoverEffect;
    private static Setting<Double> hoverEffectScale;
    private static Setting<Boolean> fade;
    private static Setting<Double> fadeDuration;
    private static float fadeAlpha;
    private static long lastNanos;

    public static void setHoverEffectSetting(Setting<Boolean> setting) {
        hoverEffect = setting;
    }

    public static void setHoverEffectScaleSetting(Setting<Double> setting) {
        hoverEffectScale = setting;
    }

    public static void setFadeSetting(Setting<Boolean> setting) {
        fade = setting;
    }

    public static void setFadeDurationSetting(Setting<Double> setting) {
        fadeDuration = setting;
    }

    public static boolean moduleActive() {
        InventoryTweaks module = Modules.get().get(InventoryTweaks.class);
        return module != null && module.isActive();
    }

    public static boolean hoverEffectEnabled() {
        return moduleActive() && hoverEffect != null && hoverEffect.get();
    }

    public static float hoverEffectScale() {
        return hoverEffectScale != null ? hoverEffectScale.get().floatValue() : 1.4F;
    }

    public static boolean isFadeActive() {
        return moduleActive() && fade != null && fade.get();
    }

    public static void resetFade() {
        fadeAlpha = 0.0F;
        lastNanos = 0L;
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
            double duration = fadeDuration != null ? fadeDuration.get() : 0.15;
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
}
