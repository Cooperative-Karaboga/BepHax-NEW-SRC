package bep.hax.hud;

import bep.hax.Bep;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.entity.player.Player;

public class SpeedKMH extends HudElement {
    public static final HudElementInfo<SpeedKMH> INFO = new HudElementInfo<>(Bep.HUD_GROUP, "SpeedKMH", "Displays movement speed in KM/H.", SpeedKMH::new);
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> showTitle = this.sgGeneral
        .add(new Builder().name("show-title").description("Display the HUD title.").defaultValue(true).build());
    private final Setting<Boolean> showHorizontalOnly = this.sgGeneral
        .add(new Builder().name("horizontal-only").description("Only calculate horizontal speed (ignore Y movement).").defaultValue(true).build());
    private final Setting<Integer> decimalPlaces = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("decimal-places")
                .description("Number of decimal places to show.")
                .defaultValue(1)
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
                .description("Color for the speed text.")
                .defaultValue(new SettingColor(0, 255, 255, 255))
                .build()
        );
    private double currentSpeed = 0.0;

    public SpeedKMH() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.level != null && MeteorClient.mc.player != null) {
            this.updateSpeed();
            double curX = this.x;
            double curY = this.y;
            double maxWidth = 0.0;
            double height = 0.0;
            double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
            double spacing = 2.0;
            if (this.showTitle.get()) {
                String title = "Speed";
                double titleWidth = renderer.textWidth(title, this.textShadow.get(), this.textScale.get());
                renderer.text(title, curX, curY, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
                curY += textHeight + spacing;
                height += textHeight + spacing;
                maxWidth = Math.max(maxWidth, titleWidth);
            }

            String speedText = String.format("%." + this.decimalPlaces.get() + "f KM/H", this.currentSpeed);
            double speedWidth = renderer.textWidth(speedText, this.textShadow.get(), this.textScale.get());
            renderer.text(speedText, curX, curY, this.speedColor.get(), this.textShadow.get(), this.textScale.get());
            height += textHeight;
            maxWidth = Math.max(maxWidth, speedWidth);
            this.setSize(maxWidth, height);
        } else {
            if (this.isInEditor()) {
                double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
                double spacing = 2.0;
                double width = renderer.textWidth("25.3 KM/H", this.textShadow.get(), this.textScale.get());
                double height = textHeight;
                double speedY = this.y;
                if (this.showTitle.get()) {
                    renderer.text("Speed", this.x, this.y, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
                    width = Math.max(width, renderer.textWidth("Speed", this.textShadow.get(), this.textScale.get()));
                    height += textHeight + spacing;
                    speedY += textHeight + spacing;
                }

                renderer.text("25.3 KM/H", this.x, speedY, this.speedColor.get(), this.textShadow.get(), this.textScale.get());
                this.setSize(width, height);
            } else {
                this.setSize(0.0, 0.0);
            }
        }
    }

    private void updateSpeed() {
        Player player = MeteorClient.mc.player;
        if (player != null) {
            double velX = player.getDeltaMovement().x;
            double velZ = player.getDeltaMovement().z;
            double velY = player.getDeltaMovement().y;
            double speed;
            if (this.showHorizontalOnly.get()) {
                speed = Math.sqrt(velX * velX + velZ * velZ);
            } else {
                speed = Math.sqrt(velX * velX + velZ * velZ + velY * velY);
            }

            this.currentSpeed = speed * 20.0 * 3.6;
        }
    }
}
