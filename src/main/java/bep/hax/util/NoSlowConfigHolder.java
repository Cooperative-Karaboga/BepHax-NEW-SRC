package bep.hax.util;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;

public class NoSlowConfigHolder {
    private static Setting<NoSlowConfigHolder.Mode> modeSetting;
    private static Setting<Boolean> sprintWhileUsingSetting;

    public static void setModeSetting(Setting<NoSlowConfigHolder.Mode> setting) {
        modeSetting = setting;
    }

    public static void setSprintWhileUsingSetting(Setting<Boolean> setting) {
        sprintWhileUsingSetting = setting;
    }

    public static NoSlowConfigHolder.Mode mode() {
        return modeSetting != null && active() ? modeSetting.get() : NoSlowConfigHolder.Mode.None;
    }

    public static boolean sprintWhileUsing() {
        return sprintWhileUsingSetting != null && active() ? sprintWhileUsingSetting.get() : false;
    }

    private static boolean active() {
        NoSlow noSlow = Modules.get().get(NoSlow.class);
        return noSlow != null && noSlow.isActive();
    }

    public enum Mode {
        None,
        HandSwap,
        V3,
        DutyCycle;
    }
}
