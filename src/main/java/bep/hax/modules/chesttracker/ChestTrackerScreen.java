package bep.hax.modules.chesttracker;

import bep.hax.modules.ItemSearchBar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ChestTrackerScreen extends Screen {
    private final ChestTrackerModule module;
    private final ChestTrackerDataV2 data;
    private EditBox searchField;
    private String searchQuery = "";
    private List<ChestTrackerScreen.ItemEntry> allItems = new ArrayList<>();
    private List<ChestTrackerScreen.ItemEntry> filteredItems = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private static final int ITEM_SIZE = 18;
    private static final int ITEMS_PER_ROW = 16;
    private static final int TOP_PADDING = 70;
    private static final int BOTTOM_PADDING = 35;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int MAX_PANEL_HEIGHT = 600;
    private static final int MIN_VISIBLE_ROWS = 5;
    private Button clearSearchButton;
    private Button sortButton;
    private ChestTrackerScreen.SortMode currentSortMode = ChestTrackerScreen.SortMode.COUNT_DESC;
    private boolean isDraggingScrollbar = false;
    private int scrollbarDragStartY = 0;
    private int scrollbarDragStartOffset = 0;
    private int cachedStartX;
    private int cachedStartY;
    private int cachedMaxY;
    private int cachedTotalRows;
    private int cachedVisibleHeight;

    public ChestTrackerScreen(ChestTrackerModule module) {
        super(Component.literal("Chest Tracker"));
        this.module = module;
        this.data = module.getSharedData();
    }

    @Override
    protected void init() {
        super.init();
        ItemSearchBar itemSearchBar = Modules.get().get(ItemSearchBar.class);
        String initialSearch = "";
        if (itemSearchBar != null && itemSearchBar.isActive()) {
            initialSearch = itemSearchBar.searchQuery.get();
        }

        this.searchField = new EditBox(this.font, this.width / 2 - 110, 20, 200, 20, Component.literal("Search items..."));
        this.searchField.setMaxLength(50);
        this.searchField.setHint(Component.literal("Search items..."));
        this.searchField.setResponder(this::onSearchChanged);
        if (!initialSearch.isEmpty()) {
            this.searchField.setValue(initialSearch);
            this.searchQuery = initialSearch;
        }

        this.addWidget(this.searchField);
        this.clearSearchButton = Button.builder(Component.literal("§cx"), button -> {
            this.searchField.setValue("");
            this.searchQuery = "";
            this.filterItems();
            if (itemSearchBar != null && itemSearchBar.isActive()) {
                itemSearchBar.updateSearchQuery("");
            }
        }).bounds(this.width / 2 + 95, 20, 20, 20).build();
        this.addRenderableWidget(this.clearSearchButton);
        this.sortButton = Button.builder(Component.literal("Sort: " + this.currentSortMode.getDisplayName()), button -> {
            this.currentSortMode = this.currentSortMode.next();
            button.setMessage(Component.literal("Sort: " + this.currentSortMode.getDisplayName()));
            this.sortItems();
            this.filterItems();
        }).bounds(this.width / 2 - 220, 20, 100, 20).build();
        this.addRenderableWidget(this.sortButton);
        this.loadItems();
        this.filterItems();
    }

    private void loadItems() {
        this.allItems = new ArrayList<>();
        Map<String, Integer> itemCounts = new HashMap<>();
        String currentDim = this.getCurrentDimension();

        for (TrackedContainer container : this.data.getAllContainers(currentDim)) {
            for (Entry<String, Integer> entry : container.getItems().entrySet()) {
                itemCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }

        for (Entry<String, Integer> entry : itemCounts.entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                Item item = BuiltInRegistries.ITEM.getValue(id);
                this.allItems.add(new ChestTrackerScreen.ItemEntry(item, entry.getValue()));
            }
        }

        this.sortItems();
    }

    private void sortItems() {
        switch (this.currentSortMode) {
            case COUNT_DESC:
                this.allItems.sort((a, b) -> Integer.compare(b.count, a.count));
                break;
            case COUNT_ASC:
                this.allItems.sort((a, b) -> Integer.compare(a.count, b.count));
                break;
            case NAME_ASC:
                this.allItems.sort((a, b) -> a.item.getName().getString().compareToIgnoreCase(b.item.getName().getString()));
                break;
            case NAME_DESC:
                this.allItems.sort((a, b) -> b.item.getName().getString().compareToIgnoreCase(a.item.getName().getString()));
        }
    }

    private void filterItems() {
        if (this.allItems == null) {
            this.allItems = new ArrayList<>();
        }

        if (this.searchQuery.isEmpty()) {
            this.filteredItems = new ArrayList<>(this.allItems);
        } else {
            String query = this.searchQuery.toLowerCase();
            this.filteredItems = this.allItems
                .stream()
                .filter(entry -> entry.item.getName().getString().toLowerCase().contains(query))
                .collect(Collectors.toList());
        }

        int rows = (int)Math.ceil(this.filteredItems.size() / 16.0);
        int contentHeight = Math.max(rows * 18, 90);
        int maxPanelHeight = Math.min(600, this.height - 70 - 35);
        int actualPanelHeight = Math.min(contentHeight, maxPanelHeight);
        int visibleRows = actualPanelHeight / 18;
        this.maxScroll = Math.max(0, rows - visibleRows);
        this.scrollOffset = Math.min(this.scrollOffset, this.maxScroll);
    }

    private void onSearchChanged(String query) {
        this.searchQuery = query;
        this.filterItems();
        ItemSearchBar itemSearchBar = Modules.get().get(ItemSearchBar.class);
        if (itemSearchBar != null && itemSearchBar.isActive()) {
            itemSearchBar.updateSearchQuery(query);
        }
    }

    private void updateCachedBounds() {
        this.cachedStartX = this.width / 2 - 144;
        this.cachedStartY = 70;
        this.cachedTotalRows = (int)Math.ceil(this.filteredItems.size() / 16.0);
        int contentHeight = Math.max(this.cachedTotalRows * 18, 90);
        int maxPanelHeight = Math.min(600, this.height - 70 - 35);
        this.cachedVisibleHeight = Math.min(contentHeight, maxPanelHeight);
        this.cachedMaxY = 70 + this.cachedVisibleHeight;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.updateCachedBounds();
        context.fill(0, 0, this.width, this.height, -268435456);
        int panelWidth = 308;
        int panelX = this.width / 2 - panelWidth / 2;
        int panelY = 65;
        int totalRows = (int)Math.ceil(this.filteredItems.size() / 16.0);
        int contentHeight = Math.max(totalRows * 18, 90);
        int maxPanelHeight = Math.min(600, this.height - 70 - 35);
        int panelContentHeight = Math.min(contentHeight, maxPanelHeight);
        int panelBottom = panelY + panelContentHeight + 15;
        context.fill(panelX, panelY, panelX + panelWidth, panelBottom, -15066598);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 2, -11184811);
        context.fill(panelX, panelY, panelX + 2, panelBottom, -11184811);
        context.fill(panelX + panelWidth - 2, panelY, panelX + panelWidth, panelBottom, -14013910);
        context.fill(panelX, panelBottom - 2, panelX + panelWidth, panelBottom, -14013910);
        String currentDim = this.getCurrentDimension();
        String dimName = currentDim.contains("overworld")
            ? "Overworld"
            : (currentDim.contains("nether") ? "Nether" : (currentDim.contains("end") ? "End" : currentDim));
        context.drawCenteredString(this.font, "§l§eChest Tracker §r§7- " + dimName, this.width / 2, 8, 16777215);
        this.searchField.render(context, mouseX, mouseY, delta);
        this.clearSearchButton.visible = !this.searchQuery.isEmpty();
        this.clearSearchButton.active = !this.searchQuery.isEmpty();
        this.renderItemGrid(context, mouseX, mouseY);
        this.renderScrollbar(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        this.renderTooltip(context, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
    }

    private void renderItemGrid(GuiGraphics context, int mouseX, int mouseY) {
        int index = this.scrollOffset * 16;
        int maxIndex = this.filteredItems.size();
        int panelWidth = 308;
        int panelX = this.width / 2 - panelWidth / 2;
        context.enableScissor(panelX + 10, 70, panelX + panelWidth - 10, this.cachedMaxY);
        int visibleRows = this.cachedVisibleHeight / 18 + 2;
        int maxRow = Math.min(visibleRows, this.cachedTotalRows - this.scrollOffset);

        for (int row = 0; row < maxRow; row++) {
            for (int col = 0; col < 16 && index < maxIndex; col++) {
                ChestTrackerScreen.ItemEntry entry = this.filteredItems.get(index);
                int x = this.cachedStartX + col * 18;
                int y = this.cachedStartY + row * 18;
                if (y + 18 > 70 && y < this.cachedMaxY) {
                    boolean hovered = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
                    context.fill(x, y, x + 18, y + 18, -12961222);
                    if (hovered) {
                        context.fill(x, y, x + 18, y + 1, -16711936);
                        context.fill(x, y, x + 1, y + 18, -16711936);
                        context.fill(x + 18 - 1, y, x + 18, y + 18, -16711936);
                        context.fill(x, y + 18 - 1, x + 18, y + 18, -16711936);
                    } else {
                        context.fill(x, y, x + 18, y + 1, -11184811);
                        context.fill(x, y, x + 1, y + 18, -11184811);
                        context.fill(x + 18 - 1, y, x + 18, y + 18, -14013910);
                        context.fill(x, y + 18 - 1, x + 18, y + 18, -14013910);
                    }

                    context.renderItem(new ItemStack(entry.item), x + 1, y + 1);
                    index++;
                } else {
                    index++;
                }
            }

            if (index >= maxIndex) {
                break;
            }
        }

        context.disableScissor();
        String itemCountText;
        if (this.searchQuery.isEmpty()) {
            itemCountText = String.format("§e%d §7unique items tracked", this.filteredItems.size());
        } else {
            itemCountText = String.format("§e%d §7items found (filtered from §e%d§7 total)", this.filteredItems.size(), this.allItems.size());
        }

        int countTextWidth = this.font.width(itemCountText);
        int countX = this.width / 2 - countTextWidth / 2;
        int countY = 52;
        context.fill(countX - 4, countY - 2, countX + countTextWidth + 4, countY + 10, -587202560);
        context.drawString(this.font, itemCountText, countX, countY, -22016, false);
    }

    private void renderScrollbar(GuiGraphics context, int mouseX, int mouseY) {
        if (this.maxScroll > 0) {
            int panelWidth = 308;
            int scrollbarX = this.width / 2 + panelWidth / 2 + 5;
            int scrollbarY = 70;
            int scrollbarHeight = this.cachedVisibleHeight;
            context.fill(scrollbarX, scrollbarY, scrollbarX + 8, scrollbarY + scrollbarHeight, -14013910);
            int visibleRows = scrollbarHeight / 18;
            int thumbHeight = Math.max(20, (int)((double)visibleRows / this.cachedTotalRows * scrollbarHeight));
            int scrollableHeight = scrollbarHeight - thumbHeight;
            int thumbY = scrollbarY + (this.maxScroll > 0 ? (int)((double)this.scrollOffset / this.maxScroll * scrollableHeight) : 0);
            boolean hovered = mouseX >= scrollbarX && mouseX <= scrollbarX + 8 && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
            int thumbColor = this.isDraggingScrollbar ? -16711936 : (hovered ? -16724992 : -16742400);
            context.fill(scrollbarX + 1, thumbY, scrollbarX + 8 - 1, thumbY + thumbHeight, thumbColor);
            context.fill(scrollbarX + 1, thumbY, scrollbarX + 8 - 1, thumbY + 1, -16711936);
            context.fill(scrollbarX + 1, thumbY + thumbHeight - 1, scrollbarX + 8 - 1, thumbY + thumbHeight, -16755456);
        }
    }

    private void renderTooltip(GuiGraphics context, int mouseX, int mouseY) {
        int index = this.scrollOffset * 16;
        int maxIndex = this.filteredItems.size();
        int visibleRows = this.cachedVisibleHeight / 18 + 2;
        int maxRow = Math.min(visibleRows, this.cachedTotalRows - this.scrollOffset);

        for (int row = 0; row < maxRow; row++) {
            for (int col = 0; col < 16; col++) {
                if (index >= maxIndex) {
                    return;
                }

                int x = this.cachedStartX + col * 18;
                int y = this.cachedStartY + row * 18;
                if (y + 18 > 70 && y < this.cachedMaxY) {
                    if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                        ChestTrackerScreen.ItemEntry entry = this.filteredItems.get(index);
                        List<TrackedContainer> containers = this.data.searchItem(entry.item);
                        int withinRange = 0;
                        double renderDist = this.module.getRenderDistance();
                        if (this.minecraft != null && this.minecraft.player != null) {
                            for (TrackedContainer container : containers) {
                                BlockPos pos = container.getPosition();
                                double distSq = this.minecraft
                                    .player
                                    .distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                                if (distSq <= renderDist * renderDist) {
                                    withinRange++;
                                }
                            }
                        }

                        List<Component> tooltip = new ArrayList<>();
                        tooltip.add(Component.literal("§f§l" + entry.item.getName().getString()));
                        tooltip.add(Component.literal(""));
                        tooltip.add(Component.literal("§7Total Amount: §a" + this.formatCountFull(entry.count)));
                        tooltip.add(Component.literal("§7Found in: §e" + containers.size() + " §7container(s)"));
                        if (withinRange > 0 && withinRange < containers.size()) {
                            tooltip.add(Component.literal("§7Will highlight: §e" + withinRange + " §7nearby"));
                            tooltip.add(Component.literal("§8(Increase render distance for more)"));
                        } else if (withinRange == 0) {
                            tooltip.add(Component.literal("§cAll containers are far away!"));
                            tooltip.add(Component.literal("§8(Increase render distance in settings)"));
                        }

                        tooltip.add(Component.literal(""));
                        tooltip.add(Component.literal("§e§l» Click to Highlight All Within Range «"));
                        ItemSearchBar itemSearchBar = Modules.get().get(ItemSearchBar.class);
                        if (itemSearchBar != null && itemSearchBar.isActive()) {
                            tooltip.add(Component.literal("§7(Also searches in ItemSearchBar)"));
                        }

                        context.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
                        return;
                    }

                    index++;
                } else {
                    index++;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (this.maxScroll > 0 && button == 0) {
            int panelWidth = 308;
            int scrollbarX = this.width / 2 + panelWidth / 2 + 5;
            int scrollbarY = 70;
            int totalRows = (int)Math.ceil(this.filteredItems.size() / 16.0);
            int contentHeight = Math.max(totalRows * 18, 90);
            int maxPanelHeight = Math.min(600, this.height - 70 - 35);
            int scrollbarHeight = Math.min(contentHeight, maxPanelHeight);
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + 8 && mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight) {
                int visibleRows = scrollbarHeight / 18;
                int thumbHeight = Math.max(20, (int)((double)visibleRows / totalRows * scrollbarHeight));
                int scrollableHeight = scrollbarHeight - thumbHeight;
                int thumbY = scrollbarY + (this.maxScroll > 0 ? (int)((double)this.scrollOffset / this.maxScroll * scrollableHeight) : 0);
                if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
                    this.isDraggingScrollbar = true;
                    this.scrollbarDragStartY = (int)mouseY;
                    this.scrollbarDragStartOffset = this.scrollOffset;
                    return true;
                }

                double clickRatio = (mouseY - scrollbarY) / scrollableHeight;
                this.scrollOffset = (int)(clickRatio * this.maxScroll);
                this.scrollOffset = Math.max(0, Math.min(this.maxScroll, this.scrollOffset));
                return true;
            }
        }

        int index = this.scrollOffset * 16;
        int maxIndex = this.filteredItems.size();
        int visibleRows = this.cachedVisibleHeight / 18 + 2;
        int maxRow = Math.min(visibleRows, this.cachedTotalRows - this.scrollOffset);

        for (int row = 0; row < maxRow; row++) {
            for (int col = 0; col < 16 && index < maxIndex; col++) {
                int x = this.cachedStartX + col * 18;
                int y = this.cachedStartY + row * 18;
                if (y + 18 > 70 && y < this.cachedMaxY) {
                    if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                        ChestTrackerScreen.ItemEntry entry = this.filteredItems.get(index);
                        this.onItemClicked(entry);
                        return true;
                    }

                    index++;
                } else {
                    index++;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private void onItemClicked(ChestTrackerScreen.ItemEntry entry) {
        List<TrackedContainer> results = this.data.searchItem(entry.item);
        this.module.searchItem(entry.item);
        ItemSearchBar itemSearchBar = Modules.get().get(ItemSearchBar.class);
        if (itemSearchBar != null && itemSearchBar.isActive()) {
            String itemName = entry.item.getName().getString();
            itemSearchBar.updateSearchQuery(itemName);
            this.searchField.setValue(itemName);
            this.searchQuery = itemName;
            this.filterItems();
        }

        int withinRange = 0;
        if (this.minecraft != null && this.minecraft.player != null) {
            double renderDist = this.module.getRenderDistance();

            for (TrackedContainer container : results) {
                BlockPos pos = container.getPosition();
                double distSq = this.minecraft.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (distSq <= renderDist * renderDist) {
                    withinRange++;
                }
            }
        }

        if (this.minecraft != null && this.minecraft.player != null) {
            String msg = withinRange < results.size()
                ? String.format("§aLit: §e%d§7/§f%d §7(%d far)", withinRange, results.size(), results.size() - withinRange)
                : String.format("§aLit: §e%d §7boxes", results.size());
            this.minecraft.player.displayClientMessage(Component.literal(msg), false);
        }

        this.onClose();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (this.isDraggingScrollbar && this.maxScroll > 0) {
            int totalRows = (int)Math.ceil(this.filteredItems.size() / 16.0);
            int contentHeight = Math.max(totalRows * 18, 90);
            int maxPanelHeight = Math.min(600, this.height - 70 - 35);
            int scrollbarHeight = Math.min(contentHeight, maxPanelHeight);
            int visibleRows = scrollbarHeight / 18;
            int thumbHeight = Math.max(20, (int)((double)visibleRows / totalRows * scrollbarHeight));
            int scrollableHeight = scrollbarHeight - thumbHeight;
            int dragDelta = (int)mouseY - this.scrollbarDragStartY;
            double scrollRatio = (double)dragDelta / scrollableHeight;
            int newOffset = this.scrollbarDragStartOffset + (int)(scrollRatio * this.maxScroll);
            this.scrollOffset = Math.max(0, Math.min(this.maxScroll, newOffset));
            return true;
        } else {
            return super.mouseDragged(click, deltaX, deltaY);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        int button = click.button();
        if (this.isDraggingScrollbar && button == 0) {
            this.isDraggingScrollbar = false;
            return true;
        } else {
            return super.mouseReleased(click);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount > 0.0) {
            this.scrollOffset = Math.max(0, this.scrollOffset - 1);
        } else if (verticalAmount < 0.0) {
            this.scrollOffset = Math.min(this.maxScroll, this.scrollOffset + 1);
        }

        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String formatCountFull(int count) {
        return String.format("%,d", count);
    }

    private String getCurrentDimension() {
        return this.minecraft != null && this.minecraft.level != null
            ? this.minecraft.level.dimension().identifier().toString()
            : "unknown";
    }

    private static class ItemEntry {
        final Item item;
        final int count;

        ItemEntry(Item item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    private enum SortMode {
        COUNT_DESC("Count ↓"),
        COUNT_ASC("Count ↑"),
        NAME_ASC("Name A-Z"),
        NAME_DESC("Name Z-A");

        private final String displayName;

        SortMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public ChestTrackerScreen.SortMode next() {
            ChestTrackerScreen.SortMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }
}
