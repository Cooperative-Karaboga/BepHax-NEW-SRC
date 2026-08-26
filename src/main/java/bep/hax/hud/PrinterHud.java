package bep.hax.hud;

import bep.hax.Bep;
import bep.hax.util.printer.PrinterMaterial;
import bep.hax.util.printer.SchematicAccess;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PrinterHud extends HudElement {
    private static final boolean LITEMATICA = FabricLoader.getInstance().isModLoaded("litematica");
    public static final HudElementInfo<PrinterHud> INFO = new HudElementInfo<>(
        Bep.HUD_GROUP, "printer-materials", "Lists the materials the Litematica schematic still needs.", PrinterHud::new
    );
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> showTitle = this.sgGeneral
        .add(new Builder().name("show-title").description("Show the header line.").defaultValue(true).build());
    private final Setting<Double> itemScale = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("item-scale")
                .description("Scale of the item icons.")
                .defaultValue(1.0)
                .min(0.1)
                .max(3.0)
                .sliderRange(0.1, 3.0)
                .build()
        );
    private final Setting<Double> textScale = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("text-scale")
                .description("Scale of the text.")
                .defaultValue(1.0)
                .min(0.1)
                .max(3.0)
                .sliderRange(0.1, 3.0)
                .build()
        );
    private final Setting<Integer> maxRows = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-rows")
                .description("Maximum materials to list.")
                .defaultValue(16)
                .min(1)
                .max(64)
                .sliderRange(1, 64)
                .build()
        );
    private final Setting<SettingColor> textColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("text-color")
                .description("Count text color.")
                .defaultValue(new SettingColor(255, 255, 255, 255))
                .build()
        );
    private final Setting<SettingColor> titleColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("title-color")
                .description("Header color.")
                .defaultValue(new SettingColor(45, 225, 150, 255))
                .build()
        );
    private final Setting<Boolean> textShadow = this.sgGeneral
        .add(new Builder().name("text-shadow").description("Render text shadow.").defaultValue(true).build());

    public PrinterHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double x = this.x;
        double y = this.y;
        double width = 0.0;
        double height = 0.0;
        double itemSize = 16.0 * this.itemScale.get();
        double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
        double spacing = 2.0;
        if (this.isInEditor()) {
            String header = "Printer - 3 materials";
            renderer.text(header, x, y, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
            double iy = y + textHeight + spacing;
            ItemStack demo = new ItemStack(Items.OBSIDIAN);
            float s = this.itemScale.get().floatValue();
            renderer.post(() -> renderer.item(demo, (int)x, (int)iy, s, true));
            renderer.text(
                "x 128", x + itemSize + spacing, iy + (itemSize / 2.0 - textHeight / 2.0), this.textColor.get(), this.textShadow.get(), this.textScale.get()
            );
            this.setSize(
                Math.max(
                    renderer.textWidth(header, this.textShadow.get(), this.textScale.get()),
                    itemSize + spacing + renderer.textWidth("x 128", this.textShadow.get(), this.textScale.get())
                ),
                textHeight + spacing + itemSize
            );
        } else if (LITEMATICA && MeteorClient.mc.player != null) {
            List<PrinterMaterial> mats = SchematicAccess.getMissingMaterials();
            if (this.showTitle.get()) {
                String title = mats.isEmpty() ? "Printer - complete" : "Printer - " + mats.size() + " materials";
                renderer.text(title, x, y, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
                y += textHeight + spacing;
                height += textHeight + spacing;
                width = Math.max(width, renderer.textWidth(title, this.textShadow.get(), this.textScale.get()));
            }

            int rows = 0;

            for (PrinterMaterial m : mats) {
                if (rows >= this.maxRows.get()) {
                    break;
                }

                rows++;
                ItemStack stack = m.stack();
                double iy = y;
                float s = this.itemScale.get().floatValue();
                renderer.post(() -> renderer.item(stack, (int)x, (int)iy, s, true));
                String txt = "x " + m.missing();
                renderer.text(
                    txt, x + itemSize + spacing, y + (itemSize / 2.0 - textHeight / 2.0), this.textColor.get(), this.textShadow.get(), this.textScale.get()
                );
                width = Math.max(width, itemSize + spacing + renderer.textWidth(txt, this.textShadow.get(), this.textScale.get()));
                y += itemSize + spacing;
                height += itemSize + spacing;
            }

            this.setSize(width, height);
        } else {
            this.setSize(0.0, 0.0);
        }
    }
}
