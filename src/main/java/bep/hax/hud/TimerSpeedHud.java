package bep.hax.hud;

import bep.hax.Bep;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class TimerSpeedHud extends HudElement {
    public static final HudElementInfo<TimerSpeedHud> INFO = new HudElementInfo<>(
        Bep.HUD_GROUP, "TimerSpeed", "Displays the current Timer module multiplier.", TimerSpeedHud::new
    );
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> showTitle = this.sgGeneral
        .add(new Builder().name("show-title").description("Display the HUD title.").defaultValue(true).build());
    private final Setting<Integer> decimalPlaces = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("decimal-places")
                .description("Number of decimal places to show.")
                .defaultValue(2)
                .min(0)
                .max(3)
                .sliderRange(0, 3)
                .build()
        );
    private final Setting<Double> textScale = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("text-scale")
                .description("Scale of the text.")
                .defaultValue(1.0)
                .min(0.1)
                .sliderRange(0.1, 3.0)
                .build()
        );
    private final Setting<Boolean> textShadow = this.sgGeneral
        .add(new Builder().name("text-shadow").description("Render shadow behind the text.").defaultValue(true).build());
    private final Setting<SettingColor> titleColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("title-color")
                .description("Color for the title text.")
                .defaultValue(new SettingColor(255, 255, 255, 255))
                .build()
        );
    private final Setting<SettingColor> speedColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("speed-color")
                .description("Color for the timer speed text.")
                .defaultValue(new SettingColor(0, 255, 255, 255))
                .build()
        );

    public TimerSpeedHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double multiplier = this.getTimerMultiplier();
        if (MeteorClient.mc.level != null && MeteorClient.mc.player != null) {
            double curX = this.x;
            double curY = this.y;
            double maxWidth = 0.0;
            double height = 0.0;
            double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
            double spacing = 2.0;
            if (this.showTitle.get()) {
                String title = "Timer";
                double titleWidth = renderer.textWidth(title, this.textShadow.get(), this.textScale.get());
                renderer.text(title, curX, curY, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
                curY += textHeight + spacing;
                height += textHeight + spacing;
                maxWidth = Math.max(maxWidth, titleWidth);
            }

            String speedText = String.format("%." + this.decimalPlaces.get() + "fx", multiplier);
            double speedWidth = renderer.textWidth(speedText, this.textShadow.get(), this.textScale.get());
            renderer.text(speedText, curX, curY, this.speedColor.get(), this.textShadow.get(), this.textScale.get());
            height += textHeight;
            maxWidth = Math.max(maxWidth, speedWidth);
            this.setSize(maxWidth, height);
        } else {
            if (this.isInEditor()) {
                double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
                double spacing = 2.0;
                double width = renderer.textWidth("1.00x", this.textShadow.get(), this.textScale.get());
                double height = textHeight;
                double speedY = this.y;
                if (this.showTitle.get()) {
                    renderer.text("Timer", this.x, this.y, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
                    width = Math.max(width, renderer.textWidth("Timer", this.textShadow.get(), this.textScale.get()));
                    height += textHeight + spacing;
                    speedY += textHeight + spacing;
                }

                renderer.text("1.00x", this.x, speedY, this.speedColor.get(), this.textShadow.get(), this.textScale.get());
                this.setSize(width, height);
            } else {
                this.setSize(0.0, 0.0);
            }
        }
    }

    private double getTimerMultiplier() {
        Timer timer = Modules.get().get(Timer.class);
        return timer != null && timer.isActive() ? timer.getMultiplier() : 1.0;
    }
}
