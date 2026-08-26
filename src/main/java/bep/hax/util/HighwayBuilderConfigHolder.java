package bep.hax.util;

import meteordevelopment.meteorclient.settings.Setting;

public class HighwayBuilderConfigHolder {
    private static Setting<Boolean> grimAirPlaceSetting;

    public static void setGrimAirPlaceSetting(Setting<Boolean> setting) {
        grimAirPlaceSetting = setting;
    }

    public static boolean grimAirPlaceEnabled() {
        return grimAirPlaceSetting != null && grimAirPlaceSetting.get();
    }
}
