package bep.hax.hud;

import bep.hax.Bep;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.ItemListSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ItemCounterHud extends HudElement {
    public static final HudElementInfo<ItemCounterHud> INFO = new HudElementInfo<>(
        Bep.HUD_GROUP, "item-counter", "Displays selected items and their inventory counts.", ItemCounterHud::new
    );
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgDisplay = this.settings.createGroup("Display");
    private final Setting<List<Item>> items = this.sgGeneral
        .add(new Builder().name("items").description("Items to track and display in the HUD.").build());
    private final Setting<Boolean> showTitle = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-title")
                .description("Display the HUD title.")
                .defaultValue(false)
                .build()
        );
    private final Setting<String> titleText = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("title-text")
                .description("Custom title text.")
                .defaultValue("Item Counter")
                .visible(this.showTitle::get)
                .build()
        );
    private final Setting<Boolean> showZero = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-zero")
                .description("Show items with zero count.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> showTotal = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-total")
                .description("Show total count of all tracked items.")
                .defaultValue(false)
                .build()
        );
    private final Setting<ItemCounterHud.Layout> layout = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("layout"))
                        .description("Layout of the displayed items."))
                    .defaultValue(ItemCounterHud.Layout.Vertical))
                .build()
        );
    private final Setting<Double> itemScale = this.sgDisplay
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
    private final Setting<Double> textScale = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("text-scale")
                .description("Scale of the count text.")
                .defaultValue(1.0)
                .min(0.1)
                .max(3.0)
                .sliderRange(0.1, 3.0)
                .build()
        );
    private final Setting<SettingColor> textColor = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("text-color")
                .description("Color of the count text.")
                .defaultValue(new SettingColor(255, 255, 255, 255))
                .build()
        );
    private final Setting<SettingColor> zeroColor = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("zero-color")
                .description("Color for items with zero count.")
                .defaultValue(new SettingColor(128, 128, 128, 255))
                .visible(this.showZero::get)
                .build()
        );
    private final Setting<SettingColor> lowCountColor = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("low-count-color")
                .description("Color for items with low count.")
                .defaultValue(new SettingColor(255, 100, 100, 255))
                .build()
        );
    private final Setting<Integer> lowCountThreshold = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("low-count-threshold")
                .description("Threshold for low count warning.")
                .defaultValue(10)
                .min(1)
                .max(100)
                .sliderRange(1, 100)
                .build()
        );
    private final Setting<Boolean> textShadow = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("text-shadow")
                .description("Render shadow behind the text.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showStackCount = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-stack-count")
                .description("Show count in stacks (e.g., 2.5 stacks).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> showItemName = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-item-name")
                .description("Show item name next to count.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> maxItemsPerRow = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-items-per-row")
                .description("Maximum items per row in grid layout.")
                .defaultValue(8)
                .min(1)
                .max(20)
                .sliderRange(1, 20)
                .visible(() -> this.layout.get() == ItemCounterHud.Layout.Grid)
                .build()
        );
    private final Reference2IntOpenHashMap<Item> counts = new Reference2IntOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Item, ItemCounterHud.ItemDisplay> displays = new Reference2ObjectOpenHashMap<>();

    public ItemCounterHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double startX = this.x;
        double itemSize = 16.0 * this.itemScale.get();
        double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
        double rowHeight = Math.max(itemSize, textHeight);
        double spacing = 2.0;
        double width = 0.0;
        double bottom = this.y;
        if (this.isInEditor()) {
            String preview = this.titleText.get();
            renderer.text(preview, this.x, this.y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
            double rowY = this.y + textHeight + spacing;
            ItemStack diamond = new ItemStack(Items.DIAMOND);
            double editorItemX = this.x;
            double editorItemY = rowY + (rowHeight - itemSize) / 2.0;
            float editorItemScale = this.itemScale.get().floatValue();
            renderer.post(() -> renderer.item(diamond, (int)editorItemX, (int)editorItemY, editorItemScale, true));
            renderer.text(
                "64", this.x + itemSize + spacing, rowY + (rowHeight - textHeight) / 2.0, this.textColor.get(), this.textShadow.get(), this.textScale.get()
            );
            this.setSize(
                Math.max(
                    renderer.textWidth(preview, this.textShadow.get(), this.textScale.get()),
                    itemSize + spacing + renderer.textWidth("64", this.textShadow.get(), this.textScale.get())
                ),
                textHeight + spacing + rowHeight
            );
        } else if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            if (this.showTitle.get()) {
                String title = this.titleText.get();
                renderer.text(title, startX, this.y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
                width = Math.max(width, renderer.textWidth(title, this.textShadow.get(), this.textScale.get()));
                bottom = this.y + textHeight;
            }

            double contentY = bottom + (this.showTitle.get() ? spacing : 0.0);
            double contentWidth = 0.0;
            double contentHeight = 0.0;
            double gridRowWidth = 0.0;
            double gridRowY = 0.0;
            int totalCount = 0;
            int itemsInRow = 0;
            int renderedItems = 0;
            List<Item> tracked = this.items.get();
            if (!tracked.isEmpty()) {
                this.updateCounts();
            }

            for (Item item : tracked) {
                int count = this.counts.getInt(item);
                totalCount += count;
                if (count != 0 || this.showZero.get()) {
                    ItemCounterHud.ItemDisplay display = this.displayOf(item);
                    ItemStack stack = display.stack;
                    SettingColor countColor = this.textColor.get();
                    if (count == 0) {
                        countColor = this.zeroColor.get();
                    } else if (count < this.lowCountThreshold.get()) {
                        countColor = this.lowCountColor.get();
                    }

                    String countText;
                    if (this.showStackCount.get() && display.maxStackSize > 1) {
                        double stacks = (double)count / display.maxStackSize;
                        countText = String.format("%.1f", stacks);
                    } else {
                        countText = String.valueOf(count);
                    }

                    if (this.showItemName.get()) {
                        countText = countText + " " + display.name;
                    }

                    double itemWidth = itemSize + spacing + renderer.textWidth(countText, this.textShadow.get(), this.textScale.get());
                    double itemX;
                    double rowY;
                    if (this.layout.get() == ItemCounterHud.Layout.Horizontal) {
                        if (renderedItems > 0) {
                            contentWidth += spacing;
                        }

                        itemX = startX + contentWidth;
                        rowY = contentY;
                        contentWidth += itemWidth;
                        contentHeight = rowHeight;
                    } else if (this.layout.get() == ItemCounterHud.Layout.Grid) {
                        if (itemsInRow >= this.maxItemsPerRow.get()) {
                            gridRowWidth = 0.0;
                            gridRowY += rowHeight + spacing;
                            itemsInRow = 0;
                        }

                        if (itemsInRow > 0) {
                            gridRowWidth += spacing;
                        }

                        itemX = startX + gridRowWidth;
                        rowY = contentY + gridRowY;
                        gridRowWidth += itemWidth;
                        contentWidth = Math.max(contentWidth, gridRowWidth);
                        contentHeight = gridRowY + rowHeight;
                        itemsInRow++;
                    } else {
                        if (renderedItems > 0) {
                            contentHeight += spacing;
                        }

                        itemX = startX;
                        rowY = contentY + contentHeight;
                        contentWidth = Math.max(contentWidth, itemWidth);
                        contentHeight += rowHeight;
                    }

                    double itemY = rowY + (rowHeight - itemSize) / 2.0;
                    float iScale = this.itemScale.get().floatValue();
                    double fItemX = itemX;
                    double fItemY = itemY;
                    renderer.post(() -> renderer.item(stack, (int)fItemX, (int)fItemY, iScale, true));
                    double textX = itemX + itemSize + spacing;
                    double textY = rowY + (rowHeight - textHeight) / 2.0;
                    renderer.text(countText, textX, textY, countColor, this.textShadow.get(), this.textScale.get());
                    renderedItems++;
                }
            }

            if (renderedItems > 0) {
                width = Math.max(width, contentWidth);
                bottom = contentY + contentHeight;
            }

            if (this.showTotal.get() && totalCount > 0) {
                String totalText = "Total: " + totalCount;
                double totalY = bottom + (!this.showTitle.get() && renderedItems <= 0 ? 0.0 : spacing);
                renderer.text(totalText, startX, totalY, this.textColor.get(), this.textShadow.get(), this.textScale.get());
                width = Math.max(width, renderer.textWidth(totalText, this.textShadow.get(), this.textScale.get()));
                bottom = totalY + textHeight;
            }

            this.setSize(width, bottom - this.y);
        } else {
            this.setSize(0.0, 0.0);
        }
    }

    private void updateCounts() {
        this.counts.clear();
        Inventory inventory = MeteorClient.mc.player.getInventory();
        int size = inventory.getContainerSize();

        for (int i = 0; i <= size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                this.counts.addTo(stack.getItem(), stack.getCount());
            }
        }
    }

    private ItemCounterHud.ItemDisplay displayOf(Item item) {
        ItemCounterHud.ItemDisplay display = this.displays.get(item);
        if (display == null || display.components != item.components() || display.language != Language.getInstance()) {
            display = new ItemCounterHud.ItemDisplay(item);
            this.displays.put(item, display);
        }

        return display;
    }

    private static class ItemDisplay {
        final DataComponentMap components;
        final Language language;
        final ItemStack stack;
        final int maxStackSize;
        final String name;

        ItemDisplay(Item item) {
            this.components = item.components();
            this.language = Language.getInstance();
            this.stack = new ItemStack(item);
            this.maxStackSize = this.stack.getMaxStackSize();
            String itemName = item.getName().getString();
            this.name = itemName.length() > 10 ? itemName.substring(0, 8) + ".." : itemName;
        }
    }

    public enum Layout {
        Vertical,
        Horizontal,
        Grid;
    }
}
