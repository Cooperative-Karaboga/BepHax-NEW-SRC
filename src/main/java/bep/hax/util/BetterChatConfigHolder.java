package bep.hax.util;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.BetterChat;

public class BetterChatConfigHolder {
    private static Setting<Boolean> customFontSetting;
    private static Setting<BetterChatConfigHolder.ChatAnimation> animationSetting;
    private static Setting<Double> animationDurationSetting;
    private static Setting<Boolean> noChatBackgroundSetting;

    public static void setCustomFontSetting(Setting<Boolean> setting) {
        customFontSetting = setting;
    }

    public static void setNoChatBackgroundSetting(Setting<Boolean> setting) {
        noChatBackgroundSetting = setting;
    }

    public static boolean noChatBackground() {
        if (noChatBackgroundSetting != null && noChatBackgroundSetting.get()) {
            BetterChat betterChat = Modules.get().get(BetterChat.class);
            return betterChat != null && betterChat.isActive();
        } else {
            return false;
        }
    }

    public static void setAnimationSetting(Setting<BetterChatConfigHolder.ChatAnimation> setting) {
        animationSetting = setting;
    }

    public static void setAnimationDurationSetting(Setting<Double> setting) {
        animationDurationSetting = setting;
    }

    public static boolean useCustomFont() {
        if (customFontSetting != null && customFontSetting.get()) {
            BetterChat betterChat = Modules.get().get(BetterChat.class);
            return betterChat != null && betterChat.isActive();
        } else {
            return false;
        }
    }

    public static BetterChatConfigHolder.ChatAnimation getAnimation() {
        return animationSetting == null ? BetterChatConfigHolder.ChatAnimation.None : animationSetting.get();
    }

    public static double getAnimationDuration() {
        return animationDurationSetting == null ? 0.25 : animationDurationSetting.get();
    }

    public enum ChatAnimation {
        None,
        Fade,
        SlideUp,
        SlideLeft,
        Scale;
    }
}
