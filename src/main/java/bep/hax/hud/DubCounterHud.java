package bep.hax.hud;

import bep.hax.Bep;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;

public class DubCounterHud extends HudElement {
    public static final HudElementInfo<DubCounterHud> INFO = new HudElementInfo<>(
        Bep.HUD_GROUP, "DubCounter", "Displays count of all containers in render distance for 2b2t looting.", DubCounterHud::new
    );
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgDisplay = this.settings.createGroup("Display");
    private final SettingGroup sgContainers = this.settings.createGroup("Containers");
    private final Setting<String> titleText = this.sgGeneral
        .add(new Builder().name("title-text").description("Custom title text for the HUD.").defaultValue("Dub Counter").build());
    private final Setting<Boolean> showTitle = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-title")
                .description("Display the HUD title.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showChestBreakdown = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-chest-breakdown")
                .description("Show single and double chest counts (S:X D:X).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> showShulkerBreakdown = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-shulker-breakdown")
                .description("Display shulker boxes broken down by color.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> sortShulkersByCount = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("sort-shulkers-by-count")
                .description("Sort shulker colors by count (highest first).")
                .defaultValue(true)
                .visible(this.showShulkerBreakdown::get)
                .build()
        );
    private final Setting<Boolean> showTotalValue = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-total-value")
                .description("Show estimated total storage slots.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> compactMode = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("compact-mode")
                .description("Show counts in a more compact format.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> showZeroCounts = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-zero-counts")
                .description("Always show toggled containers even when count is 0.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> countChests = this.sgContainers
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("chests")
                .description("Count regular and trapped chests.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> countBarrels = this.sgContainers
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("barrels").description("Count barrels.").defaultValue(true).build());
    private final Setting<Boolean> countShulkers = this.sgContainers
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("shulker-boxes")
                .description("Count shulker boxes.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> countEnderChests = this.sgContainers
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("ender-chests")
                .description("Count ender chests.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> countHoppers = this.sgContainers
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("hoppers")
                .description("Count hoppers (valuable for farms).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> countDroppers = this.sgContainers
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("droppers").description("Count droppers.").defaultValue(false).build());
    private final Setting<Boolean> countDispensers = this.sgContainers
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("dispensers").description("Count dispensers.").defaultValue(false).build());
    private final Setting<Boolean> countFurnaces = this.sgContainers
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("furnaces")
                .description("Count furnaces (includes blast furnaces and smokers).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> countBrewingStands = this.sgContainers
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("brewing-stands")
                .description("Count brewing stands.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> countLecterns = this.sgContainers
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("lecterns")
                .description("Count lecterns (book holders).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> countCrafters = this.sgContainers
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("crafters")
                .description("Count crafters (auto-crafting blocks).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> countDecoratedPots = this.sgContainers
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("decorated-pots")
                .description("Count decorated pots.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Double> textScale = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("text-scale")
                .description("Scale of the text.")
                .defaultValue(1.0)
                .min(0.5)
                .max(3.0)
                .sliderRange(0.5, 3.0)
                .build()
        );
    private final Setting<SettingColor> titleColor = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("title-color")
                .description("Color of the title text.")
                .defaultValue(new SettingColor(255, 255, 255, 255))
                .build()
        );
    private final Setting<SettingColor> textColor = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("text-color")
                .description("Color of the count text.")
                .defaultValue(new SettingColor(200, 200, 200, 255))
                .build()
        );
    private final Setting<SettingColor> shulkerTextColor = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("shulker-text-color")
                .description("Color of the shulker count text.")
                .defaultValue(new SettingColor(255, 200, 100, 255))
                .build()
        );
    private final Setting<SettingColor> valueTextColor = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("value-text-color")
                .description("Color of the value text.")
                .defaultValue(new SettingColor(100, 255, 100, 255))
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
    private final Setting<Boolean> rainbowTitle = this.sgDisplay
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("rainbow-title")
                .description("Rainbow colored title.")
                .defaultValue(false)
                .build()
        );
    private static final long SCAN_INTERVAL_MS = 500L;
    private DubCounterHud.ContainerCounts cachedCounts = new DubCounterHud.ContainerCounts();
    private long lastScanMs = 0L;
    private static final Comparator<Entry<String, Integer>> SHULKER_ORDER = (a, b) -> {
        int comp = b.getValue().compareTo(a.getValue());
        return comp != 0 ? comp : a.getKey().compareTo(b.getKey());
    };
    private static final Map<String, SettingColor> SHULKER_COLORS = Map.ofEntries(
        Map.entry("White", new SettingColor(255, 255, 255, 255)),
        Map.entry("Orange", new SettingColor(255, 165, 0, 255)),
        Map.entry("Magenta", new SettingColor(255, 0, 255, 255)),
        Map.entry("Light Blue", new SettingColor(173, 216, 230, 255)),
        Map.entry("Yellow", new SettingColor(255, 255, 0, 255)),
        Map.entry("Lime", new SettingColor(50, 205, 50, 255)),
        Map.entry("Pink", new SettingColor(255, 192, 203, 255)),
        Map.entry("Gray", new SettingColor(128, 128, 128, 255)),
        Map.entry("Light Gray", new SettingColor(211, 211, 211, 255)),
        Map.entry("Cyan", new SettingColor(0, 255, 255, 255)),
        Map.entry("Purple", new SettingColor(128, 0, 128, 255)),
        Map.entry("Blue", new SettingColor(0, 0, 255, 255)),
        Map.entry("Brown", new SettingColor(139, 69, 19, 255)),
        Map.entry("Green", new SettingColor(0, 128, 0, 255)),
        Map.entry("Red", new SettingColor(255, 0, 0, 255)),
        Map.entry("Black", new SettingColor(50, 50, 50, 255)),
        Map.entry("Undyed", new SettingColor(150, 100, 75, 255))
    );
    private final SettingColor rainbowColor = new SettingColor();
    private final List<Entry<String, Integer>> sortedShulkers = new ArrayList<>();

    public DubCounterHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.level != null && MeteorClient.mc.player != null) {
            long now = System.currentTimeMillis();
            if (now - this.lastScanMs >= 500L) {
                this.cachedCounts = this.countContainers();
                this.lastScanMs = now;
            }

            this.renderCounts(renderer, this.cachedCounts);
        } else {
            if (this.isInEditor()) {
                this.renderPlaceholder(renderer);
            } else {
                this.setSize(0.0, 0.0);
            }
        }
    }

    private void renderPlaceholder(HudRenderer renderer) {
        double y = this.y;
        double maxWidth = 0.0;
        double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
        if (this.showTitle.get()) {
            renderer.text(this.titleText.get(), this.x, y, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(this.titleText.get(), this.textShadow.get(), this.textScale.get()));
            y += textHeight + 2.0;
        }

        renderer.text("Dubs: 12.5", this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
        maxWidth = Math.max(maxWidth, renderer.textWidth("Dubs: 12.5", this.textShadow.get(), this.textScale.get()));
        y += textHeight;
        renderer.text("Barrels: 24", this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
        maxWidth = Math.max(maxWidth, renderer.textWidth("Barrels: 24", this.textShadow.get(), this.textScale.get()));
        y += textHeight;
        renderer.text("Shulkers: 37", this.x, y, this.shulkerTextColor.get(), this.textShadow.get(), this.textScale.get());
        maxWidth = Math.max(maxWidth, renderer.textWidth("Shulkers: 37", this.textShadow.get(), this.textScale.get()));
        y += textHeight;
        if (this.showTotalValue.get()) {
            renderer.text("Storage: ~2145 slots", this.x, y, this.valueTextColor.get(), this.textShadow.get(), this.textScale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth("Storage: ~2145 slots", this.textShadow.get(), this.textScale.get()));
            y += textHeight;
        }

        this.setSize(maxWidth, y - this.y);
    }

    private void renderCounts(HudRenderer renderer, DubCounterHud.ContainerCounts counts) {
        double y = this.y;
        double maxWidth = 0.0;
        double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
        if (this.showTitle.get()) {
            SettingColor color = this.rainbowTitle.get() ? this.getRainbowColor() : this.titleColor.get();
            renderer.text(this.titleText.get(), this.x, y, color, this.textShadow.get(), this.textScale.get());
            y += textHeight + 2.0;
            maxWidth = Math.max(maxWidth, renderer.textWidth(this.titleText.get(), this.textShadow.get(), this.textScale.get()));
        }

        if (this.countChests.get() && (counts.totalDubs > 0.0 || this.showZeroCounts.get())) {
            String dubText;
            if (this.compactMode.get()) {
                dubText = String.format("D: %.1f", counts.totalDubs);
            } else if (this.showChestBreakdown.get()) {
                dubText = String.format("Dubs: %.1f (S:%d D:%d)", counts.totalDubs, counts.singleChests, counts.doubleChests);
            } else {
                dubText = String.format("Dubs: %.1f", counts.totalDubs);
            }

            renderer.text(dubText, this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(dubText, this.textShadow.get(), this.textScale.get()));
        }

        if (this.countBarrels.get() && (counts.barrelCount > 0 || this.showZeroCounts.get())) {
            String barrelText = this.compactMode.get() ? String.format("B: %d", counts.barrelCount) : String.format("Barrels: %d", counts.barrelCount);
            renderer.text(barrelText, this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(barrelText, this.textShadow.get(), this.textScale.get()));
        }

        if (this.countShulkers.get() && (counts.totalShulkers > 0 || this.showZeroCounts.get())) {
            String shulkerTitle = this.compactMode.get() ? String.format("S: %d", counts.totalShulkers) : String.format("Shulkers: %d", counts.totalShulkers);
            renderer.text(shulkerTitle, this.x, y, this.shulkerTextColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(shulkerTitle, this.textShadow.get(), this.textScale.get()));
            if (this.showShulkerBreakdown.get()) {
                double shulkerTextHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get() * 0.9);
                this.sortedShulkers.clear();
                this.sortedShulkers.addAll(counts.shulkersByColor.entrySet());
                if (this.sortShulkersByCount.get()) {
                    this.sortedShulkers.sort(SHULKER_ORDER);
                }

                for (Entry<String, Integer> entry : this.sortedShulkers) {
                    if (entry.getValue() > 0 || this.showZeroCounts.get()) {
                        String colorText = this.compactMode.get()
                            ? String.format("  %s: %d", this.getShortColorName(entry.getKey()), entry.getValue())
                            : String.format("  %s: %d", entry.getKey(), entry.getValue());
                        SettingColor color = this.getShulkerColor(entry.getKey());
                        renderer.text(colorText, this.x, y, color, this.textShadow.get(), this.textScale.get() * 0.9);
                        y += shulkerTextHeight;
                        maxWidth = Math.max(maxWidth, renderer.textWidth(colorText, this.textShadow.get(), this.textScale.get() * 0.9));
                    }
                }
            }
        }

        if (this.countEnderChests.get() && (counts.enderChestCount > 0 || this.showZeroCounts.get())) {
            String enderText = this.compactMode.get()
                ? String.format("E: %d", counts.enderChestCount)
                : String.format("Ender Chests: %d", counts.enderChestCount);
            renderer.text(enderText, this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(enderText, this.textShadow.get(), this.textScale.get()));
        }

        if (this.countHoppers.get() && (counts.hopperCount > 0 || this.showZeroCounts.get())) {
            String hopperText = this.compactMode.get() ? String.format("H: %d", counts.hopperCount) : String.format("Hoppers: %d", counts.hopperCount);
            renderer.text(hopperText, this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(hopperText, this.textShadow.get(), this.textScale.get()));
        }

        if (this.countDroppers.get() && (counts.dropperCount > 0 || this.showZeroCounts.get())) {
            String dropperText = this.compactMode.get() ? String.format("Dr: %d", counts.dropperCount) : String.format("Droppers: %d", counts.dropperCount);
            renderer.text(dropperText, this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(dropperText, this.textShadow.get(), this.textScale.get()));
        }

        if (this.countDispensers.get() && (counts.dispenserCount > 0 || this.showZeroCounts.get())) {
            String dispenserText = this.compactMode.get()
                ? String.format("Di: %d", counts.dispenserCount)
                : String.format("Dispensers: %d", counts.dispenserCount);
            renderer.text(dispenserText, this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(dispenserText, this.textShadow.get(), this.textScale.get()));
        }

        if (this.countFurnaces.get() && (counts.furnaceCount > 0 || this.showZeroCounts.get())) {
            String furnaceText = this.compactMode.get() ? String.format("F: %d", counts.furnaceCount) : String.format("Furnaces: %d", counts.furnaceCount);
            renderer.text(furnaceText, this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(furnaceText, this.textShadow.get(), this.textScale.get()));
        }

        if (this.countBrewingStands.get() && (counts.brewingStandCount > 0 || this.showZeroCounts.get())) {
            String brewText = this.compactMode.get()
                ? String.format("BS: %d", counts.brewingStandCount)
                : String.format("Brewing Stands: %d", counts.brewingStandCount);
            renderer.text(brewText, this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(brewText, this.textShadow.get(), this.textScale.get()));
        }

        if (this.countLecterns.get() && (counts.lecternCount > 0 || this.showZeroCounts.get())) {
            String lecternText = this.compactMode.get() ? String.format("L: %d", counts.lecternCount) : String.format("Lecterns: %d", counts.lecternCount);
            renderer.text(lecternText, this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(lecternText, this.textShadow.get(), this.textScale.get()));
        }

        if (this.countCrafters.get() && (counts.crafterCount > 0 || this.showZeroCounts.get())) {
            String crafterText = this.compactMode.get() ? String.format("Cr: %d", counts.crafterCount) : String.format("Crafters: %d", counts.crafterCount);
            renderer.text(crafterText, this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(crafterText, this.textShadow.get(), this.textScale.get()));
        }

        if (this.countDecoratedPots.get() && (counts.decoratedPotCount > 0 || this.showZeroCounts.get())) {
            String potText = this.compactMode.get()
                ? String.format("DP: %d", counts.decoratedPotCount)
                : String.format("Decorated Pots: %d", counts.decoratedPotCount);
            renderer.text(potText, this.x, y, this.textColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(potText, this.textShadow.get(), this.textScale.get()));
        }

        if (this.showTotalValue.get() && counts.getTotalSlots() > 0) {
            y += 2.0;
            String valueText = this.compactMode.get()
                ? String.format("Slots: %d", counts.getTotalSlots())
                : String.format("Storage: ~%d slots", counts.getTotalSlots());
            renderer.text(valueText, this.x, y, this.valueTextColor.get(), this.textShadow.get(), this.textScale.get());
            y += textHeight;
            maxWidth = Math.max(maxWidth, renderer.textWidth(valueText, this.textShadow.get(), this.textScale.get()));
        }

        this.setSize(maxWidth, y - this.y);
    }

    private SettingColor getRainbowColor() {
        double time = System.currentTimeMillis() / 1000.0;
        int r = (int)((Math.sin(time) + 1.0) * 127.5);
        int g = (int)((Math.sin(time + 2.094) + 1.0) * 127.5);
        int b = (int)((Math.sin(time + 4.189) + 1.0) * 127.5);
        this.rainbowColor.set(r, g, b, 255);
        return this.rainbowColor;
    }

    private SettingColor getShulkerColor(String colorName) {
        SettingColor color = SHULKER_COLORS.get(colorName);
        return color != null ? color : this.textColor.get();
    }

    private String getShortColorName(String fullName) {
        return switch (fullName) {
            case "White" -> "W";
            case "Orange" -> "O";
            case "Magenta" -> "M";
            case "Light Blue" -> "LB";
            case "Yellow" -> "Y";
            case "Lime" -> "Li";
            case "Pink" -> "Pi";
            case "Gray" -> "Gr";
            case "Light Gray" -> "LG";
            case "Cyan" -> "C";
            case "Purple" -> "Pu";
            case "Blue" -> "B";
            case "Brown" -> "Br";
            case "Green" -> "Gn";
            case "Red" -> "R";
            case "Black" -> "Bl";
            case "Undyed" -> "U";
            default -> fullName.substring(0, Math.min(3, fullName.length()));
        };
    }

    private DubCounterHud.ContainerCounts countContainers() {
        DubCounterHud.ContainerCounts counts = new DubCounterHud.ContainerCounts();
        if (MeteorClient.mc.level != null && MeteorClient.mc.player != null) {
            int renderDistance = MeteorClient.mc.options.renderDistance().get();
            int playerChunkX = MeteorClient.mc.player.chunkPosition().x;
            int playerChunkZ = MeteorClient.mc.player.chunkPosition().z;

            for (int cx = playerChunkX - renderDistance; cx <= playerChunkX + renderDistance; cx++) {
                for (int cz = playerChunkZ - renderDistance; cz <= playerChunkZ + renderDistance; cz++) {
                    LevelChunk chunk = MeteorClient.mc.level.getChunk(cx, cz);
                    if (chunk != null) {
                        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                            BlockEntity blockEntity = chunk.getBlockEntity(pos);
                            if (blockEntity != null) {
                                if (this.countChests.get() && (blockEntity instanceof ChestBlockEntity || blockEntity instanceof TrappedChestBlockEntity)) {
                                    BlockState blockState = blockEntity.getBlockState();
                                    if (blockState.getBlock() instanceof ChestBlock && blockState.hasProperty(ChestBlock.TYPE)) {
                                        ChestType type = blockState.getValue(ChestBlock.TYPE);
                                        if (type == ChestType.SINGLE) {
                                            counts.singleChests++;
                                        } else if (type == ChestType.LEFT) {
                                            counts.doubleChests++;
                                        }
                                    }
                                }

                                if (this.countBarrels.get() && blockEntity instanceof BarrelBlockEntity) {
                                    counts.barrelCount++;
                                }

                                if (this.countShulkers.get() && blockEntity instanceof ShulkerBoxBlockEntity shulker) {
                                    counts.totalShulkers++;
                                    DyeColor color = shulker.getColor();
                                    String colorName = color != null ? this.getColorName(color) : "Undyed";
                                    counts.shulkersByColor.merge(colorName, 1, Integer::sum);
                                }

                                if (this.countEnderChests.get() && blockEntity instanceof EnderChestBlockEntity) {
                                    counts.enderChestCount++;
                                }

                                if (this.countHoppers.get() && blockEntity instanceof HopperBlockEntity) {
                                    counts.hopperCount++;
                                }

                                if (this.countDroppers.get() && blockEntity instanceof DropperBlockEntity) {
                                    counts.dropperCount++;
                                }

                                if (this.countDispensers.get() && blockEntity instanceof DispenserBlockEntity && !(blockEntity instanceof DropperBlockEntity)) {
                                    counts.dispenserCount++;
                                }

                                if (this.countFurnaces.get()
                                    && (blockEntity instanceof FurnaceBlockEntity || blockEntity instanceof BlastFurnaceBlockEntity || blockEntity instanceof SmokerBlockEntity)) {
                                    counts.furnaceCount++;
                                }

                                if (this.countBrewingStands.get() && blockEntity instanceof BrewingStandBlockEntity) {
                                    counts.brewingStandCount++;
                                }

                                if (this.countLecterns.get() && blockEntity instanceof LecternBlockEntity) {
                                    counts.lecternCount++;
                                }

                                if (this.countCrafters.get()) {
                                    String className = blockEntity.getClass().getSimpleName();
                                    if (className.equals("CrafterBlockEntity")) {
                                        counts.crafterCount++;
                                    }
                                }

                                if (this.countDecoratedPots.get() && blockEntity instanceof DecoratedPotBlockEntity) {
                                    counts.decoratedPotCount++;
                                }
                            }
                        }
                    }
                }
            }

            counts.totalDubs = counts.doubleChests + counts.singleChests * 0.5;
            return counts;
        } else {
            return counts;
        }
    }

    private String getColorName(DyeColor color) {
        return switch (color) {
            case WHITE -> "White";
            case ORANGE -> "Orange";
            case MAGENTA -> "Magenta";
            case LIGHT_BLUE -> "Light Blue";
            case YELLOW -> "Yellow";
            case LIME -> "Lime";
            case PINK -> "Pink";
            case GRAY -> "Gray";
            case LIGHT_GRAY -> "Light Gray";
            case CYAN -> "Cyan";
            case PURPLE -> "Purple";
            case BLUE -> "Blue";
            case BROWN -> "Brown";
            case GREEN -> "Green";
            case RED -> "Red";
            case BLACK -> "Black";
        };
    }

    private static class ContainerCounts {
        int singleChests = 0;
        int doubleChests = 0;
        double totalDubs = 0.0;
        int barrelCount = 0;
        int hopperCount = 0;
        int dropperCount = 0;
        int dispenserCount = 0;
        int enderChestCount = 0;
        int furnaceCount = 0;
        int brewingStandCount = 0;
        int lecternCount = 0;
        int crafterCount = 0;
        int decoratedPotCount = 0;
        int totalShulkers = 0;
        Map<String, Integer> shulkersByColor = new HashMap<>();

        int getTotalSlots() {
            int slots = 0;
            slots += this.singleChests * 27;
            slots += this.doubleChests * 54;
            slots += this.barrelCount * 27;
            slots += this.hopperCount * 5;
            slots += this.dropperCount * 9;
            slots += this.dispenserCount * 9;
            slots += this.totalShulkers * 27;
            slots += this.enderChestCount * 27;
            slots += this.furnaceCount * 3;
            slots += this.brewingStandCount * 5;
            slots += this.lecternCount * 1;
            slots += this.crafterCount * 9;
            return slots + this.decoratedPotCount * 1;
        }
    }
}
