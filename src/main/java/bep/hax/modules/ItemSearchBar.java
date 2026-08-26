package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.modules.chesttracker.ChestTrackerModule;
import bep.hax.modules.chesttracker.TrackedContainer;
import java.util.List;
import java.util.WeakHashMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ItemSearchBar extends Module {
    private final WeakHashMap<ItemStack, Boolean> highlightCache = new WeakHashMap<>();
    private final WeakHashMap<ItemStack, Boolean> frameMatchCache = new WeakHashMap<>();
    private String cachedQuery = "";
    private String[] cachedSplitQueries = null;
    private final SettingGroup sgGeneral = this.settings.createGroup("General");
    private final SettingGroup sgGUI = this.settings.createGroup("GUI Settings");
    private final SettingGroup sgItemFrames = this.settings.createGroup("Item Frame ESP");
    public final Setting<String> searchQuery = this.sgGeneral
        .add(
            new Builder()
                .name("search-query")
                .description("Search query to match item names. Use commas to separate multiple search terms.")
                .defaultValue("")
                .build()
        );
    private final Setting<Boolean> caseSensitive = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("case-sensitive")
                .description("Whether the search should be case sensitive.")
                .defaultValue(false)
                .onChanged(v -> this.invalidateCache())
                .build()
        );
    private final Setting<Boolean> splitQueries = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("split-queries")
                .description("Split search queries by commas. Disable to treat commas literally.")
                .defaultValue(true)
                .onChanged(v -> this.invalidateCache())
                .build()
        );
    private final Setting<Boolean> searchItemName = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("search-item-name")
                .description("Search in item display names.")
                .defaultValue(true)
                .onChanged(v -> this.invalidateCache())
                .build()
        );
    private final Setting<Boolean> searchItemType = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("search-item-type")
                .description("Search in item type names.")
                .defaultValue(true)
                .onChanged(v -> this.invalidateCache())
                .build()
        );
    private final Setting<Boolean> searchLore = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("search-lore")
                .description("Search in item lore/tooltip text.")
                .defaultValue(true)
                .onChanged(v -> this.invalidateCache())
                .build()
        );
    public final Setting<SettingColor> highlightColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("highlight-color")
                .description("Color to highlight matching items.")
                .defaultValue(new SettingColor(134, 219, 255, 203))
                .build()
        );
    private final Setting<Boolean> ownInventory = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("inventory-highlight")
                .description("Highlight items in player inventory.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showSearchField = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-search-field")
                .description("Show search input field on top of container windows.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> chestTrackerIntegration = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("chest-tracker-integration")
                .description("Automatically search in ChestTracker when searching items.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Keybind> clickToSearchKey = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("click-to-search-key")
                .description("Key/button to click an item to search for it.")
                .defaultValue(Keybind.fromButton(2))
                .visible(() -> this.chestTrackerIntegration.get())
                .build()
        );
    private final Setting<Integer> fieldWidth = this.sgGUI
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("field-width")
                .description("Width of the search field.")
                .defaultValue(87)
                .min(80)
                .max(300)
                .sliderMin(80)
                .sliderMax(300)
                .build()
        );
    private final Setting<Integer> fieldHeight = this.sgGUI
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("field-height")
                .description("Height of the search field.")
                .defaultValue(12)
                .min(8)
                .max(20)
                .sliderMin(8)
                .sliderMax(20)
                .build()
        );
    private final Setting<Integer> offsetX = this.sgGUI
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("offset-x")
                .description("Horizontal offset from container edge.")
                .defaultValue(85)
                .min(-100)
                .max(100)
                .sliderMin(-100)
                .sliderMax(100)
                .build()
        );
    private final Setting<Integer> offsetY = this.sgGUI
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("offset-y")
                .description("Vertical offset from container top (negative = above container).")
                .defaultValue(-18)
                .min(-50)
                .max(50)
                .sliderMin(-50)
                .sliderMax(50)
                .build()
        );
    private final Setting<Boolean> highlightItemFrames = this.sgItemFrames
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("highlight-item-frames")
                .description("Highlight item frames containing matching items in the world.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> frameRenderDistance = this.sgItemFrames
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("render-distance")
                .description("Maximum distance to highlight item frames (blocks).")
                .defaultValue(256)
                .min(16)
                .sliderRange(16, 512)
                .visible(this.highlightItemFrames::get)
                .build()
        );
    private final Setting<SettingColor> frameFillColor = this.sgItemFrames
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("fill-color")
                .description("Fill color for item frame highlight.")
                .defaultValue(new SettingColor(0, 38, 255, 50))
                .visible(this.highlightItemFrames::get)
                .build()
        );
    private final Setting<SettingColor> frameOutlineColor = this.sgItemFrames
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("outline-color")
                .description("Outline color for item frame highlight.")
                .defaultValue(new SettingColor(58, 172, 255, 255))
                .visible(this.highlightItemFrames::get)
                .build()
        );
    private final Setting<Boolean> frameRenderFill = this.sgItemFrames
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render-fill")
                .description("Render fill of item frame highlight.")
                .defaultValue(true)
                .visible(this.highlightItemFrames::get)
                .build()
        );
    private final Setting<Boolean> frameRenderOutline = this.sgItemFrames
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render-outline")
                .description("Render outline of item frame highlight.")
                .defaultValue(true)
                .visible(this.highlightItemFrames::get)
                .build()
        );
    private final Setting<Boolean> frameRenderTracer = this.sgItemFrames
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("tracers")
                .description("Draw tracers to matching item frames.")
                .defaultValue(false)
                .visible(this.highlightItemFrames::get)
                .build()
        );
    private final Setting<SettingColor> frameTracerColor = this.sgItemFrames
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("tracer-color")
                .description("Color of tracers to item frames.")
                .defaultValue(new SettingColor(255, 255, 0, 125))
                .visible(() -> this.highlightItemFrames.get() && this.frameRenderTracer.get())
                .build()
        );
    private ItemStack lastHoveredItem = null;
    private boolean middleMousePressed = false;
    private String currentSearchQuery = "";

    public ItemSearchBar() {
        super(Bep.CATEGORY, "ItemSearchBar", "Search and highlight items in inventory and containers.");
    }

    @Override
    public void onActivate() {
        if (!this.searchQuery.get().isEmpty() && this.chestTrackerIntegration.get()) {
            this.updateChestTrackerSearch(this.searchQuery.get());
        }
    }

    @Override
    public void onDeactivate() {
        if (this.chestTrackerIntegration.get()) {
            ChestTrackerModule chestTracker = Modules.get().get(ChestTrackerModule.class);
            if (chestTracker != null && chestTracker.isActive()) {
                chestTracker.searchItem(null);
            }
        }

        this.lastHoveredItem = null;
        this.middleMousePressed = false;
    }

    @EventHandler
    private void onTick(Post event) {
        if (!this.searchQuery.get().equals(this.currentSearchQuery)) {
            this.updateSearchQuery(this.searchQuery.get());
        }

        if (this.chestTrackerIntegration.get() && this.clickToSearchKey.get().isSet()) {
            if (this.mc.screen != null && this.mc.screen instanceof AbstractContainerScreen<?> screen) {
                boolean var11 = this.clickToSearchKey.get().isPressed();
                ItemStack hoveredStack = null;
                if (screen.getMenu() != null) {
                    double mouseX = this.mc.mouseHandler.xpos() * this.mc.getWindow().getGuiScaledWidth() / this.mc.getWindow().getScreenWidth();
                    double mouseY = this.mc.mouseHandler.ypos() * this.mc.getWindow().getGuiScaledHeight() / this.mc.getWindow().getScreenHeight();

                    for (Slot slot : screen.getMenu().slots) {
                        if (this.isPointInSlot(screen, slot, mouseX, mouseY) && slot.hasItem()) {
                            hoveredStack = slot.getItem();
                            break;
                        }
                    }
                }

                if (hoveredStack != null) {
                    if (var11 && !this.middleMousePressed) {
                        this.middleMousePressed = true;
                        this.lastHoveredItem = hoveredStack;
                    } else if (!var11 && this.middleMousePressed && this.lastHoveredItem != null) {
                        this.middleMousePressed = false;
                        String itemName = this.lastHoveredItem.getHoverName().getString();
                        this.updateSearchQuery(itemName);
                        this.info("Searching for: " + itemName);
                        this.lastHoveredItem = null;
                    }
                }

                if (!var11) {
                    this.middleMousePressed = false;
                }
            } else {
                this.lastHoveredItem = null;
            }
        }
    }

    private boolean isPointInSlot(AbstractContainerScreen<?> screen, Slot slot, double pointX, double pointY) {
        int x = (screen.width - 176) / 2;
        int y = (screen.height - 166) / 2;
        if (!(screen instanceof ContainerScreen) && screen instanceof InventoryScreen) {
            x = (screen.width - 176) / 2;
            y = (screen.height - 166) / 2;
        }

        int slotX = x + slot.x;
        int slotY = y + slot.y;
        return pointX >= slotX && pointX < slotX + 16 && pointY >= slotY && pointY < slotY + 16;
    }

    private boolean shouldIgnoreCurrentScreenHandler(LocalPlayer player) {
        if (this.mc.screen == null) {
            return true;
        }

        if (player.containerMenu == null) {
            return true;
        }

        AbstractContainerMenu handler = player.containerMenu;
        return handler instanceof InventoryMenu
            ? !this.ownInventory.get()
            : !(handler instanceof AbstractFurnaceMenu)
                && !(handler instanceof ChestMenu)
                && !(handler instanceof DispenserMenu)
                && !(handler instanceof ShulkerBoxMenu)
                && !(handler instanceof HopperMenu)
                && !(handler instanceof HorseInventoryMenu);
    }

    private boolean matchesSearchQuery(String text, String query) {
        return this.caseSensitive.get() ? text.contains(query) : containsIgnoreCase(text, query);
    }

    private static boolean containsIgnoreCase(String text, String query) {
        int last = text.length() - query.length();

        for (int i = 0; i <= last; i++) {
            if (text.regionMatches(true, i, query, 0, query.length())) {
                return true;
            }
        }

        return false;
    }

    public void updateSearchQuery(String query) {
        this.currentSearchQuery = query;
        if (!this.searchQuery.get().equals(query)) {
            this.searchQuery.set(query);
        }

        this.invalidateCache();
        if (this.chestTrackerIntegration.get()) {
            this.updateChestTrackerSearch(query);
        }
    }

    private void updateChestTrackerSearch(String query) {
        ChestTrackerModule chestTracker = Modules.get().get(ChestTrackerModule.class);
        if (chestTracker != null && chestTracker.isActive()) {
            if (query != null && !query.trim().isEmpty()) {
                Item searchItem = null;
                String searchQuery = query.trim().toLowerCase();
                if (this.splitQueries.get() && searchQuery.contains(",")) {
                    String[] queries = searchQuery.split(",");
                    if (queries.length > 0) {
                        searchQuery = queries[0].trim();
                    }
                }

                for (Item item : BuiltInRegistries.ITEM) {
                    String itemName = item.getDefaultInstance().getHoverName().getString().toLowerCase();
                    if (itemName.equals(searchQuery)) {
                        searchItem = item;
                        break;
                    }
                }

                if (searchItem == null) {
                    for (Item item : BuiltInRegistries.ITEM) {
                        String itemName = item.getDefaultInstance().getHoverName().getString().toLowerCase();
                        String translationKey = item.getDescriptionId().toLowerCase();
                        String simplifiedKey = translationKey.replace("item.minecraft.", "").replace("block.minecraft.", "").replace("_", " ");
                        if (itemName.contains(searchQuery) || simplifiedKey.contains(searchQuery) || translationKey.contains(searchQuery)) {
                            searchItem = item;
                            break;
                        }
                    }
                }

                chestTracker.searchItem(searchItem);
                if (searchItem != null) {
                    String itemDisplayName = searchItem.getDefaultInstance().getHoverName().getString();
                    this.info("ChestTracker: Searching for §e" + itemDisplayName);
                    List<TrackedContainer> results = chestTracker.getSharedData().searchItem(searchItem);
                    if (!results.isEmpty()) {
                        Item finalSearchItem = searchItem;
                        int totalCount = results.stream().mapToInt(c -> c.getItemCount(BuiltInRegistries.ITEM.getKey(finalSearchItem).toString())).sum();
                        this.info("Found §a" + totalCount + "§r items in §e" + results.size() + "§r containers");
                    }
                } else {
                    this.info("ChestTracker: No item found matching \"" + query + "\"");
                }
            } else {
                chestTracker.searchItem(null);
                this.info("ChestTracker search cleared");
            }
        }
    }

    public boolean shouldShowSearchField() {
        return this.showSearchField.get();
    }

    public int getFieldWidth() {
        return this.fieldWidth.get();
    }

    public int getFieldHeight() {
        return this.fieldHeight.get();
    }

    public int getOffsetX() {
        return this.offsetX.get();
    }

    public int getOffsetY() {
        return this.offsetY.get();
    }

    public boolean shouldHighlightSlot(ItemStack stack) {
        if (this.mc.player == null) {
            return false;
        }

        if (!stack.isEmpty() && !this.shouldIgnoreCurrentScreenHandler(this.mc.player)) {
            String query = !this.currentSearchQuery.isEmpty() ? this.currentSearchQuery.trim() : this.searchQuery.get().trim();
            if (query.isEmpty()) {
                return false;
            }

            this.syncQuery(query);
            Boolean cached = this.highlightCache.get(stack);
            if (cached != null) {
                return cached;
            }

            boolean result = this.computeHighlight(stack, query);
            this.highlightCache.put(stack, result);
            return result;
        } else {
            return false;
        }
    }

    private void syncQuery(String query) {
        if (!query.equals(this.cachedQuery)) {
            this.cachedQuery = query;
            if (this.splitQueries.get() && query.contains(",")) {
                this.cachedSplitQueries = query.split(",");

                for (int i = 0; i < this.cachedSplitQueries.length; i++) {
                    this.cachedSplitQueries[i] = this.cachedSplitQueries[i].trim();
                }
            } else {
                this.cachedSplitQueries = null;
            }

            this.highlightCache.clear();
            this.frameMatchCache.clear();
        }
    }

    private void invalidateCache() {
        this.highlightCache.clear();
        this.frameMatchCache.clear();
        this.cachedQuery = "";
        this.cachedSplitQueries = null;
    }

    private boolean computeHighlight(ItemStack stack, String query) {
        if (Utils.hasItems(stack)) {
            ItemStack[] stacks = new ItemStack[27];
            Utils.getItemsInContainerItem(stack, stacks);

            for (ItemStack s : stacks) {
                if (s != null && !s.isEmpty() && this.matchesItemDirect(s, query)) {
                    return true;
                }
            }
        }

        return this.matchesItemDirect(stack, query);
    }

    private boolean matchesItemDirect(ItemStack stack, String query) {
        if (this.cachedSplitQueries != null) {
            for (String q : this.cachedSplitQueries) {
                if (!q.isEmpty() && this.matchesItem(stack, q)) {
                    return true;
                }
            }

            return false;
        } else {
            return this.matchesItem(stack, query);
        }
    }

    private boolean matchesItem(ItemStack stack, String query) {
        if (this.searchItemName.get()) {
            String displayName = stack.getHoverName().getString();
            if (this.matchesSearchQuery(displayName, query)) {
                return true;
            }
        }

        if (this.searchItemType.get()) {
            String typeName = stack.getItem().getDefaultInstance().getHoverName().getString();
            if (this.matchesSearchQuery(typeName, query)) {
                return true;
            }
        }

        if (this.searchLore.get()) {
            String tooltip = stack.getComponents().toString();
            if (this.matchesSearchQuery(tooltip, query)) {
                return true;
            }
        }

        return false;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (this.mc.level != null && this.mc.player != null) {
            if (this.highlightItemFrames.get()) {
                String query = !this.currentSearchQuery.isEmpty() ? this.currentSearchQuery.trim() : this.searchQuery.get().trim();
                if (!query.isEmpty()) {
                    ShapeMode shapeMode = this.getShapeMode(this.frameRenderFill.get(), this.frameRenderOutline.get());
                    boolean renderTracer = this.frameRenderTracer.get();
                    if (shapeMode != null || renderTracer) {
                        this.syncQuery(query);
                        Color fillColor = new Color(this.frameFillColor.get());
                        Color outlineColor = new Color(this.frameOutlineColor.get());
                        double range = this.frameRenderDistance.get().intValue();
                        double rangeSq = range * range;

                        for (Entity entity : this.mc.level.entitiesForRendering()) {
                            if (entity instanceof ItemFrame frame && !(this.mc.player.distanceToSqr(frame) > rangeSq)) {
                                ItemStack heldStack = frame.getItem();
                                if (!heldStack.isEmpty() && this.matchesItemForFrame(heldStack, query)) {
                                    AABB box = frame.getBoundingBox();
                                    if (shapeMode != null) {
                                        event.renderer.box(box, fillColor, outlineColor, shapeMode, 0);
                                    }

                                    if (renderTracer) {
                                        Vec3 center = box.getCenter();
                                        event.renderer
                                            .line(
                                                RenderUtils.center.x,
                                                RenderUtils.center.y,
                                                RenderUtils.center.z,
                                                center.x,
                                                center.y,
                                                center.z,
                                                this.frameTracerColor.get()
                                            );
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private ShapeMode getShapeMode(boolean renderFill, boolean renderOutline) {
        return bep.hax.util.RenderUtils.shapeMode(renderFill, renderOutline);
    }

    private boolean matchesItemForFrame(ItemStack stack, String query) {
        Boolean cached = this.frameMatchCache.get(stack);
        if (cached != null) {
            return cached;
        }

        boolean result = this.matchesItemDirect(stack, query);
        this.frameMatchCache.put(stack, result);
        return result;
    }
}
