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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public class DimensionCoords extends HudElement {
    public static final HudElementInfo<DimensionCoords> INFO = new HudElementInfo<>(
        Bep.HUD_GROUP, "DimensionCoords", "Displays coordinates for both overworld and nether dimensions.", DimensionCoords::new
    );
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> showTitle = this.sgGeneral
        .add(new Builder().name("show-title").description("Display the HUD title.").defaultValue(false).build());
    private final Setting<Boolean> showCurrentDim = this.sgGeneral
        .add(new Builder().name("show-current-dimension").description("Show current dimension name.").defaultValue(false).build());
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
    private final Setting<SettingColor> overworldColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("overworld-color")
                .description("Color for overworld coordinates.")
                .defaultValue(new SettingColor(0, 255, 0, 255))
                .build()
        );
    private final Setting<SettingColor> netherColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("nether-color")
                .description("Color for nether coordinates.")
                .defaultValue(new SettingColor(255, 0, 0, 255))
                .build()
        );
    private final Setting<SettingColor> endColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("end-color")
                .description("Color for end coordinates.")
                .defaultValue(new SettingColor(255, 0, 255, 255))
                .build()
        );
    private final Setting<Boolean> showLabels = this.sgGeneral
        .add(new Builder().name("show-labels").description("Show dimension labels (e.g. 'Overworld:', 'Nether:').").defaultValue(true).build());
    private final Setting<Boolean> removeCommas = this.sgGeneral
        .add(new Builder().name("remove-commas").description("Remove commas from coordinates.").defaultValue(false).build());
    private final Setting<Boolean> horizontalLayout = this.sgGeneral
        .add(new Builder().name("horizontal-layout").description("Display coordinates horizontally next to each other.").defaultValue(false).build());

    public DimensionCoords() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.level != null && MeteorClient.mc.player != null) {
            BlockPos playerPos = MeteorClient.mc.player.blockPosition();
            Identifier dimensionId = MeteorClient.mc.level.dimension().identifier();
            double curX = this.x;
            double curY = this.y;
            double maxWidth = 0.0;
            double height = 0.0;
            double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
            double spacing = 2.0;
            if (this.showTitle.get()) {
                String title = "Dimension Coords";
                double titleWidth = renderer.textWidth(title, this.textShadow.get(), this.textScale.get());
                renderer.text(title, curX, curY, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
                curY += textHeight + spacing;
                height += textHeight + spacing;
                maxWidth = Math.max(maxWidth, titleWidth);
            }

            if (this.showCurrentDim.get()) {
                String dimName = this.getDimensionName(dimensionId);
                String dimText = "Current: " + dimName;
                double dimWidth = renderer.textWidth(dimText, this.textShadow.get(), this.textScale.get());
                renderer.text(dimText, curX, curY, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
                curY += textHeight + spacing;
                height += textHeight + spacing;
                maxWidth = Math.max(maxWidth, dimWidth);
            }

            String coordFormat = this.removeCommas.get() ? "%d %d %d" : "%d, %d, %d";
            if (this.isOverworld(dimensionId)) {
                String overworldLabel = this.showLabels.get() ? "Overworld: " : "";
                String netherLabel = this.showLabels.get() ? "Nether: " : "";
                String overworldText = overworldLabel
                    + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
                String netherText = netherLabel
                    + String.format(
                        coordFormat, Math.floorDiv(playerPos.getX(), 8), playerPos.getY(), Math.floorDiv(playerPos.getZ(), 8)
                    );
                if (this.horizontalLayout.get()) {
                    double overworldWidth = renderer.textWidth(overworldText, this.textShadow.get(), this.textScale.get());
                    renderer.text(overworldText, curX, curY, this.overworldColor.get(), this.textShadow.get(), this.textScale.get());
                    curX += overworldWidth + spacing * 3.0;
                    renderer.text(netherText, curX, curY, this.netherColor.get(), this.textShadow.get(), this.textScale.get());
                    double netherWidth = renderer.textWidth(netherText, this.textShadow.get(), this.textScale.get());
                    maxWidth = Math.max(maxWidth, overworldWidth + netherWidth + spacing * 3.0);
                    height += textHeight + spacing;
                } else {
                    double overworldWidth = renderer.textWidth(overworldText, this.textShadow.get(), this.textScale.get());
                    double netherWidth = renderer.textWidth(netherText, this.textShadow.get(), this.textScale.get());
                    renderer.text(overworldText, curX, curY, this.overworldColor.get(), this.textShadow.get(), this.textScale.get());
                    curY += textHeight + spacing;
                    height += textHeight + spacing;
                    maxWidth = Math.max(maxWidth, overworldWidth);
                    renderer.text(netherText, curX, curY, this.netherColor.get(), this.textShadow.get(), this.textScale.get());
                    curY += textHeight + spacing;
                    height += textHeight + spacing;
                    maxWidth = Math.max(maxWidth, netherWidth);
                }
            } else if (this.isNether(dimensionId)) {
                String netherLabel = this.showLabels.get() ? "Nether: " : "";
                String overworldLabel = this.showLabels.get() ? "Overworld: " : "";
                String netherText = netherLabel + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
                String overworldText = overworldLabel
                    + String.format(coordFormat, playerPos.getX() * 8, playerPos.getY(), playerPos.getZ() * 8);
                if (this.horizontalLayout.get()) {
                    double netherWidth = renderer.textWidth(netherText, this.textShadow.get(), this.textScale.get());
                    renderer.text(netherText, curX, curY, this.netherColor.get(), this.textShadow.get(), this.textScale.get());
                    curX += netherWidth + spacing * 3.0;
                    renderer.text(overworldText, curX, curY, this.overworldColor.get(), this.textShadow.get(), this.textScale.get());
                    double overworldWidth = renderer.textWidth(overworldText, this.textShadow.get(), this.textScale.get());
                    maxWidth = Math.max(maxWidth, netherWidth + overworldWidth + spacing * 3.0);
                    height += textHeight + spacing;
                } else {
                    double netherWidth = renderer.textWidth(netherText, this.textShadow.get(), this.textScale.get());
                    double overworldWidth = renderer.textWidth(overworldText, this.textShadow.get(), this.textScale.get());
                    renderer.text(netherText, curX, curY, this.netherColor.get(), this.textShadow.get(), this.textScale.get());
                    curY += textHeight + spacing;
                    height += textHeight + spacing;
                    maxWidth = Math.max(maxWidth, netherWidth);
                    renderer.text(overworldText, curX, curY, this.overworldColor.get(), this.textShadow.get(), this.textScale.get());
                    curY += textHeight + spacing;
                    height += textHeight + spacing;
                    maxWidth = Math.max(maxWidth, overworldWidth);
                }
            } else if (this.isEnd(dimensionId)) {
                String endLabel = this.showLabels.get() ? "The End: " : "";
                String endText = endLabel + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
                double endWidth = renderer.textWidth(endText, this.textShadow.get(), this.textScale.get());
                renderer.text(endText, curX, curY, this.endColor.get(), this.textShadow.get(), this.textScale.get());
                curY += textHeight + spacing;
                height += textHeight + spacing;
                maxWidth = Math.max(maxWidth, endWidth);
            } else {
                String customLabel = this.showLabels.get() ? this.getDimensionName(dimensionId) + ": " : "";
                String customText = customLabel + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
                double customWidth = renderer.textWidth(customText, this.textShadow.get(), this.textScale.get());
                renderer.text(customText, curX, curY, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
                curY += textHeight + spacing;
                height += textHeight + spacing;
                maxWidth = Math.max(maxWidth, customWidth);
            }

            this.setSize(maxWidth, height > 0.0 ? height - spacing : 0.0);
        } else {
            if (this.isInEditor()) {
                this.renderEditorPreview(renderer);
            } else {
                this.setSize(0.0, 0.0);
            }
        }
    }

    private void renderEditorPreview(HudRenderer renderer) {
        double curX = this.x;
        double curY = this.y;
        double maxWidth = 0.0;
        double height = 0.0;
        double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
        double spacing = 2.0;
        if (this.showTitle.get()) {
            String title = "Dimension Coords";
            renderer.text(title, curX, curY, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, renderer.textWidth(title, this.textShadow.get(), this.textScale.get()));
        }

        if (this.showCurrentDim.get()) {
            String dimText = "Current: Overworld";
            renderer.text(dimText, curX, curY, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, renderer.textWidth(dimText, this.textShadow.get(), this.textScale.get()));
        }

        String coordFormat = this.removeCommas.get() ? "%d %d %d" : "%d, %d, %d";
        String overworldText = (this.showLabels.get() ? "Overworld: " : "") + String.format(coordFormat, 1024, 72, -2048);
        String netherText = (this.showLabels.get() ? "Nether: " : "") + String.format(coordFormat, 128, 72, -256);
        double overworldWidth = renderer.textWidth(overworldText, this.textShadow.get(), this.textScale.get());
        double netherWidth = renderer.textWidth(netherText, this.textShadow.get(), this.textScale.get());
        if (this.horizontalLayout.get()) {
            renderer.text(overworldText, curX, curY, this.overworldColor.get(), this.textShadow.get(), this.textScale.get());
            renderer.text(netherText, curX + overworldWidth + spacing * 3.0, curY, this.netherColor.get(), this.textShadow.get(), this.textScale.get());
            maxWidth = Math.max(maxWidth, overworldWidth + netherWidth + spacing * 3.0);
            height += textHeight + spacing;
        } else {
            renderer.text(overworldText, curX, curY, this.overworldColor.get(), this.textShadow.get(), this.textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, overworldWidth);
            renderer.text(netherText, curX, curY, this.netherColor.get(), this.textShadow.get(), this.textScale.get());
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, netherWidth);
        }

        this.setSize(maxWidth, height - spacing);
    }

    private boolean isOverworld(Identifier dimensionId) {
        return dimensionId.equals(Identifier.parse("minecraft:overworld"));
    }

    private boolean isNether(Identifier dimensionId) {
        return dimensionId.equals(Identifier.parse("minecraft:the_nether"));
    }

    private boolean isEnd(Identifier dimensionId) {
        return dimensionId.equals(Identifier.parse("minecraft:the_end"));
    }

    private String getDimensionName(Identifier dimensionId) {
        if (this.isOverworld(dimensionId)) {
            return "Overworld";
        } else if (this.isNether(dimensionId)) {
            return "Nether";
        } else {
            return this.isEnd(dimensionId) ? "The End" : dimensionId.getPath();
        }
    }
}
