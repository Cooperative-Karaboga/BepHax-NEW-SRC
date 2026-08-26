package bep.hax.config;

import bep.hax.util.EnemyColorManager;
import bep.hax.util.Utils;
import java.util.List;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class BepConfig {
    public static Setting<BepConfig.TurnSpeedMode> rotationTurnSpeedMode = ((Builder)new Builder().defaultValue(BepConfig.TurnSpeedMode.Normal)).build();
    public static Setting<Double> rotationTurnSpeedCustom = new meteordevelopment.meteorclient.settings.DoubleSetting.Builder().defaultValue(45.0).build();
    public static Setting<Boolean> illegalDisconnectButtonSetting = new meteordevelopment.meteorclient.settings.BoolSetting.Builder().build();
    public static Setting<Boolean> disableMeteorClientTelemetry = new meteordevelopment.meteorclient.settings.BoolSetting.Builder().build();
    public static Setting<Boolean> disableMeteorCapes = new meteordevelopment.meteorclient.settings.BoolSetting.Builder().build();
    public static Setting<Boolean> disableBepHaxCapes = new meteordevelopment.meteorclient.settings.BoolSetting.Builder().build();
    public static Setting<Boolean> streamerMode = new meteordevelopment.meteorclient.settings.BoolSetting.Builder().build();
    public static Setting<Boolean> ignoreOverlayMessages = new meteordevelopment.meteorclient.settings.BoolSetting.Builder().build();
    public static Setting<List<String>> overlayMessageFilter = new meteordevelopment.meteorclient.settings.StringListSetting.Builder().build();
    public static Setting<Utils.IllegalDisconnectMethod> illegalDisconnectMethodSetting = ((Builder)new Builder()
            .defaultValue(Utils.IllegalDisconnectMethod.Chat))
        .build();
    public static Setting<SettingColor> enemyColorSetting = new meteordevelopment.meteorclient.settings.ColorSetting.Builder().build();

    public static double getRotationTurnSpeed() {
        if (rotationTurnSpeedMode == null) {
            return 45.0;
        }

        BepConfig.TurnSpeedMode mode = rotationTurnSpeedMode.get();
        return mode == BepConfig.TurnSpeedMode.Custom ? rotationTurnSpeedCustom.get() : mode.speed;
    }

    public static void initialize() {
        SettingGroup sgBepHax = Config.get().settings.createGroup("BepHax");
        illegalDisconnectButtonSetting = sgBepHax.add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("illegal-disconnect-button")
                .description("Adds a button to the main menu that forces the server to kick you when pressed.")
                .defaultValue(false)
                .build()
        );
        illegalDisconnectMethodSetting = sgBepHax.add(
            ((Builder)((Builder)((Builder)new Builder().name("illegal-disconnect-method")).description("The method to use to cause the server to kick you."))
                    .defaultValue(Utils.IllegalDisconnectMethod.Chat))
                .build()
        );
        disableMeteorClientTelemetry = sgBepHax.add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("disable-meteor-telemetry")
                .description("Disables sending periodic telemetry pings to meteorclient.com for their online player count api.")
                .defaultValue(true)
                .build()
        );
        disableMeteorCapes = sgBepHax.add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("disable-meteor-capes")
                .description("Disables fetching and rendering Meteor Client custom capes.")
                .defaultValue(false)
                .build()
        );
        disableBepHaxCapes = sgBepHax.add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("disable-bephax-capes")
                .description("Disables rendering BepHax custom capes.")
                .defaultValue(false)
                .build()
        );
        streamerMode = sgBepHax.add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("streamer-mode")
                .description(
                    "Hides coordinates shown in BepHax module menus (StashFinder, Pearl Loader, Stash Mover). Module functionality still uses the real coordinates."
                )
                .defaultValue(false)
                .build()
        );
        enemyColorSetting = sgBepHax.add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("enemy-color")
                .description("Color for enemy players in nametags and tablist.")
                .defaultValue(new SettingColor(255, 0, 0, 255))
                .build()
        );
        EnemyColorManager.setEnemyColorSetting(enemyColorSetting);
        rotationTurnSpeedMode = sgBepHax.add(
            ((Builder)((Builder)((Builder)new Builder().name("rotation-turn-speed"))
                        .description("How fast smooth rotations turn each tick (degrees). Lower = more human-like, higher = snappier."))
                    .defaultValue(BepConfig.TurnSpeedMode.Normal))
                .build()
        );
        rotationTurnSpeedCustom = sgBepHax.add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("rotation-turn-speed-custom")
                .description("Custom per-tick turn cap in degrees (used when turn speed is set to Custom).")
                .defaultValue(45.0)
                .min(1.0)
                .max(180.0)
                .sliderRange(5.0, 180.0)
                .visible(() -> rotationTurnSpeedMode.get() == BepConfig.TurnSpeedMode.Custom)
                .build()
        );
        ignoreOverlayMessages = sgBepHax.add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("ignore-overlay-messages")
                .description("Overlay messages will be ignored if they match any of the provided filters.")
                .defaultValue(false)
                .build()
        );
        overlayMessageFilter = sgBepHax.add(
            new meteordevelopment.meteorclient.settings.StringListSetting.Builder()
                .name("overlay-message-filter")
                .description("Overlay messages will be ignored if they match any of the provided filters.")
                .defaultValue(List.of("2b2t.org"))
                .visible(ignoreOverlayMessages::get)
                .build()
        );
    }

    public enum TurnSpeedMode {
        Slow(25.0),
        Normal(45.0),
        Fast(90.0),
        Instant(180.0),
        Custom(45.0);

        public final double speed;

        TurnSpeedMode(double speed) {
            this.speed = speed;
        }
    }
}
