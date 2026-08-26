package bep.hax.modules;

import bep.hax.Bep;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection.CraftableStatus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

public class AutoCraft extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> antiDesync = this.sgGeneral
        .add(new Builder().name("anti-desync").description("Try to prevent inventory desync.").defaultValue(false).build());
    private final Setting<Integer> craftDelay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("craft-delay")
                .description("Base delay between crafts in ticks (20 ticks = 1 second).")
                .defaultValue(4)
                .min(1)
                .sliderMax(40)
                .build()
        );
    private final Setting<Boolean> randomDelay = this.sgGeneral
        .add(new Builder().name("random-delay").description("Add random variation to craft delay (more human-like).").defaultValue(false).build());
    private final Setting<Integer> randomDelayRange = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("random-delay-range")
                .description("Random ticks to add (0 to this value).")
                .defaultValue(2)
                .min(0)
                .sliderMax(10)
                .visible(this.randomDelay::get)
                .build()
        );
    private final Setting<Boolean> adaptiveDelay = this.sgGeneral
        .add(
            new Builder()
                .name("adaptive-delay")
                .description("Increase delay when server TPS is low (reduces kicks on laggy servers).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> buttonX = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("button-x")
                .description("X offset for buttons (negative = left of inventory).")
                .defaultValue(-1)
                .min(-200)
                .max(200)
                .sliderMin(-200)
                .sliderMax(200)
                .build()
        );
    private final Setting<Integer> buttonY = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("button-y")
                .description("Y offset for buttons.")
                .defaultValue(-84)
                .min(-100)
                .max(200)
                .sliderMin(-100)
                .sliderMax(200)
                .build()
        );
    private final Setting<Integer> buttonWidth = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("button-width")
                .description("Width of each button.")
                .defaultValue(60)
                .min(20)
                .max(100)
                .sliderMin(20)
                .sliderMax(100)
                .build()
        );
    private final Setting<Integer> buttonHeight = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("button-height")
                .description("Height of each button.")
                .defaultValue(20)
                .min(16)
                .max(40)
                .sliderMin(16)
                .sliderMax(40)
                .build()
        );
    private final Setting<Integer> buttonSpacing = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("button-spacing")
                .description("Vertical spacing between buttons.")
                .defaultValue(20)
                .min(20)
                .max(50)
                .sliderMin(20)
                .sliderMax(50)
                .build()
        );
    private final Setting<Boolean> debugMode = this.sgGeneral
        .add(new Builder().name("debug-mode").description("Show debug messages in chat.").defaultValue(false).build());
    private final List<AutoCraft.CraftButton> craftButtons = new ArrayList<>();
    private AutoCraft.CraftButton activeCraftButton = null;
    private int craftTickCounter = 0;
    private int currentDelayTarget = 0;

    public AutoCraft() {
        super(Bep.CATEGORY, "auto-craft", "Advanced crafting automation with configurable buttons. Loop mode crafts until materials run out.");
        this.craftButtons.add(new AutoCraft.CraftButton("minecraft:oak_planks", false, false, false));
        this.craftButtons.add(new AutoCraft.CraftButton("minecraft:stick", false, false, false));
        this.craftButtons.add(new AutoCraft.CraftButton("minecraft:torch", false, false, false));
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        WButton openConfig = table.add(theme.button("Configure Buttons")).expandX().widget();
        openConfig.action = () -> this.mc.setScreen(new AutoCraft.CraftButtonConfigScreen(theme, this));
        return table;
    }

    @EventHandler
    private void onTick(Post event) {
        if (Utils.canUpdate() && this.mc.gameMode != null) {
            if (!(this.mc.player.containerMenu instanceof CraftingMenu) && !(this.mc.player.containerMenu instanceof InventoryMenu)) {
                if (this.activeCraftButton != null) {
                    this.debug("Not in crafting screen - clearing active button");
                    this.activeCraftButton.currentCrafts = 0;
                    this.activeCraftButton = null;
                }
            } else {
                if (this.activeCraftButton != null && this.mc.player.tickCount % 20 == 0) {
                    this.debug(
                        "Tick handler active. Button: "
                            + this.activeCraftButton.getName()
                            + ", Loop: "
                            + this.activeCraftButton.loop
                            + ", Crafts: "
                            + this.activeCraftButton.currentCrafts
                    );
                }

                if (this.antiDesync.get()) {
                    this.mc.player.getInventory().tick();
                }

                if (this.activeCraftButton != null) {
                    this.craftTickCounter++;
                    if (this.currentDelayTarget == 0) {
                        this.currentDelayTarget = this.calculateSmartDelay();
                        this.debug("Next craft delay: " + this.currentDelayTarget + " ticks");
                    }

                    if (this.craftTickCounter < this.currentDelayTarget) {
                        return;
                    }

                    this.craftTickCounter = 0;
                    this.currentDelayTarget = 0;
                    this.debug("Attempting craft (delay passed)...");
                    boolean useCraftMax = this.activeCraftButton.shiftClick;
                    boolean crafted = this.craftItem(this.activeCraftButton.itemId, this.activeCraftButton.drop, useCraftMax);
                    if (crafted) {
                        this.activeCraftButton.currentCrafts++;
                        this.debug("Craft #" + this.activeCraftButton.currentCrafts + " successful! Continuing...");
                        if (!this.activeCraftButton.loop) {
                            this.debug("Not looping - stopping after one craft");
                            this.activeCraftButton.currentCrafts = 0;
                            this.activeCraftButton = null;
                        }
                    } else {
                        this.debug("No more materials to craft! Completed " + this.activeCraftButton.currentCrafts + " crafts. Stopping.");
                        this.activeCraftButton.currentCrafts = 0;
                        this.activeCraftButton = null;
                    }
                }
            }
        }
    }

    private boolean craftItem(String itemId, boolean drop, boolean craftMax) {
        try {
            this.debug("craftItem called: " + itemId + ", drop=" + drop + ", craftMax=" + craftMax);
            Identifier id = Identifier.parse(itemId);
            Item targetItem = BuiltInRegistries.ITEM.getValue(id);
            if (targetItem == null) {
                this.debugError("Target item is null!");
                return false;
            }

            this.debug("Target item: " + targetItem.getName().getString());
            int syncId;
            if (this.mc.player.containerMenu instanceof CraftingMenu) {
                syncId = ((CraftingMenu)this.mc.player.containerMenu).containerId;
                this.debug("Using CraftingScreenHandler, syncId=" + syncId);
            } else {
                if (!(this.mc.player.containerMenu instanceof InventoryMenu)) {
                    this.debugError("Not in crafting screen! Handler: " + this.mc.player.containerMenu.getClass().getName());
                    return false;
                }

                syncId = ((InventoryMenu)this.mc.player.containerMenu).containerId;
                this.debug("Using PlayerScreenHandler, syncId=" + syncId);
            }

            List<RecipeCollection> recipeCollections = this.mc.player.getRecipeBook().getCollections();
            this.debug("Searching " + recipeCollections.size() + " recipe collections...");
            int totalCraftable = 0;

            for (RecipeCollection collection : recipeCollections) {
                List<RecipeDisplayEntry> craftableRecipes = collection.getSelectedRecipes(CraftableStatus.CRAFTABLE);
                if (craftableRecipes.size() > 0) {
                    totalCraftable += craftableRecipes.size();
                }

                for (RecipeDisplayEntry recipe : craftableRecipes) {
                    RecipeDisplay display = recipe.display();

                    for (ItemStack resultStack : display.result().resolveForStacks(SlotDisplayContext.fromLevel(this.mc.level))) {
                        if (resultStack.getItem() == targetItem) {
                            this.debug("Found recipe! Clicking recipe with craftMax=" + craftMax);
                            this.mc.gameMode.handlePlaceRecipe(syncId, recipe.id(), craftMax);
                            if (drop) {
                                this.debug("Dropping item from output slot");
                                this.mc.gameMode.handleInventoryMouseClick(syncId, 0, 1, ClickType.THROW, this.mc.player);
                            } else {
                                this.debug("Shift-clicking item to inventory");
                                this.mc.gameMode.handleInventoryMouseClick(syncId, 0, 0, ClickType.QUICK_MOVE, this.mc.player);
                            }

                            this.debug("Craft successful!");
                            return true;
                        }
                    }
                }
            }

            this.debug("Found " + totalCraftable + " total craftable recipes, but none match " + targetItem.getName().getString());
            this.debugError("No craftable recipe found for " + targetItem.getName().getString());
        } catch (Exception e) {
            this.debugError("Craft failed: " + e.getMessage());
            if (this.debugMode.get()) {
                e.printStackTrace();
            }
        }

        return false;
    }

    public void activateButton(AutoCraft.CraftButton button) {
        if (button == null) {
            this.debugError("Button is null!");
        } else {
            this.debug("=== BUTTON CLICKED ===");
            this.debug("Item: " + button.getName());
            this.debug("ShiftClick: " + button.shiftClick);
            this.debug("Loop: " + button.loop);
            this.debug("Drop: " + button.drop);
            boolean sameButton = this.activeCraftButton != null && this.activeCraftButton.itemId.equals(button.itemId);
            if (sameButton) {
                this.debug("Toggling OFF active button");
                this.activeCraftButton.currentCrafts = 0;
                this.activeCraftButton = null;
            } else {
                if (this.activeCraftButton != null) {
                    this.activeCraftButton.currentCrafts = 0;
                }

                AutoCraft.CraftButton actualButton = null;

                for (AutoCraft.CraftButton btn : this.craftButtons) {
                    if (btn.itemId.equals(button.itemId)) {
                        actualButton = btn;
                        break;
                    }
                }

                if (actualButton == null) {
                    this.debugError("Could not find button in list!");
                } else {
                    this.activeCraftButton = actualButton;
                    this.activeCraftButton.currentCrafts = 0;
                    this.craftTickCounter = 0;
                    this.currentDelayTarget = 0;
                    if (actualButton.shiftClick && actualButton.loop) {
                        this.debug("SHIFT-CLICK + LOOP MODE: Will repeatedly craft MAX until no materials");
                    } else {
                        if (actualButton.shiftClick) {
                            this.debug("SHIFT-CLICK MODE: Crafting max amount ONCE");
                            boolean result = this.craftItem(actualButton.itemId, actualButton.drop, true);
                            this.activeCraftButton = null;
                            return;
                        }

                        if (!actualButton.loop) {
                            this.debug("SINGLE CRAFT MODE: Crafting once");
                            boolean result = this.craftItem(actualButton.itemId, actualButton.drop, false);
                            this.activeCraftButton = null;
                            return;
                        }

                        this.debug("LOOP MODE: Will craft continuously until no materials");
                    }
                }
            }
        }
    }

    public List<AutoCraft.CraftButton> getButtons() {
        return this.craftButtons;
    }

    public AutoCraft.CraftButton getActiveButton() {
        return this.activeCraftButton;
    }

    public int getButtonX() {
        return this.buttonX.get();
    }

    public int getButtonY() {
        return this.buttonY.get();
    }

    public int getButtonWidth() {
        return this.buttonWidth.get();
    }

    public int getButtonHeight() {
        return this.buttonHeight.get();
    }

    public int getButtonSpacing() {
        return this.buttonSpacing.get();
    }

    private void debug(String message) {
        if (this.debugMode.get()) {
            this.info(message);
        }
    }

    private void debugError(String message) {
        if (this.debugMode.get()) {
            this.error(message);
        }
    }

    private int calculateSmartDelay() {
        int baseDelay = this.craftDelay.get();
        if (this.randomDelay.get()) {
            int randomAdd = (int)(Math.random() * (this.randomDelayRange.get() + 1));
            baseDelay += randomAdd;
        }

        if (this.adaptiveDelay.get() && this.mc.level != null) {
            float tps = this.mc.level.tickRateManager().millisecondsPerTick();
            float expectedTps = 50.0F;
            if (tps > expectedTps) {
                float lagMultiplier = tps / expectedTps;
                baseDelay = (int)(baseDelay * lagMultiplier);
                this.debug("Server lagging (TPS: " + 1000.0F / tps + "), increased delay to " + baseDelay);
            }
        }

        return Math.max(1, baseDelay);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();
        ListTag buttonsList = new ListTag();

        for (AutoCraft.CraftButton button : this.craftButtons) {
            buttonsList.add(button.toTag());
        }

        tag.put("craftButtons", buttonsList);
        return tag;
    }

    @Override
    public Module fromTag(CompoundTag tag) {
        super.fromTag(tag);
        this.craftButtons.clear();
        if (tag.contains("craftButtons")) {
            Optional<ListTag> buttonsListOpt = tag.getList("craftButtons");
            if (!buttonsListOpt.isPresent()) {
                return this;
            }

            ListTag buttonsList = buttonsListOpt.get();
            this.debug("Loading " + buttonsList.size() + " buttons from config...");

            for (int i = 0; i < buttonsList.size(); i++) {
                AutoCraft.CraftButton button = new AutoCraft.CraftButton();
                Optional<CompoundTag> opt = buttonsList.getCompound(i);
                if (opt.isPresent()) {
                    button.fromTag(opt.get());
                    this.craftButtons.add(button);
                    this.debug(
                        "  Button "
                            + (i + 1)
                            + ": "
                            + button.getName()
                            + " (loop="
                            + button.loop
                            + ", drop="
                            + button.drop
                            + ", shiftClick="
                            + button.shiftClick
                            + ")"
                    );
                }
            }
        }

        if (this.craftButtons.isEmpty()) {
            this.debug("No buttons in config, adding defaults");
            this.craftButtons.add(new AutoCraft.CraftButton("minecraft:oak_planks", false, false, false));
            this.craftButtons.add(new AutoCraft.CraftButton("minecraft:stick", false, false, false));
            this.craftButtons.add(new AutoCraft.CraftButton("minecraft:torch", false, false, false));
        }

        return this;
    }

    public static class CraftButton implements ISerializable<AutoCraft.CraftButton> {
        public String itemId;
        public boolean loop;
        public boolean drop;
        public boolean shiftClick;
        public int fireworkDuration;
        public int currentCrafts;

        public CraftButton() {
            this.itemId = "minecraft:stick";
            this.loop = false;
            this.drop = false;
            this.shiftClick = false;
            this.fireworkDuration = 1;
            this.currentCrafts = 0;
        }

        public CraftButton(String itemId, boolean loop, boolean drop, boolean shiftClick) {
            this.itemId = itemId;
            this.loop = loop;
            this.drop = drop;
            this.shiftClick = shiftClick;
            this.fireworkDuration = 1;
            this.currentCrafts = 0;
        }

        public AutoCraft.CraftButton copy() {
            AutoCraft.CraftButton copy = new AutoCraft.CraftButton(this.itemId, this.loop, this.drop, this.shiftClick);
            copy.fireworkDuration = this.fireworkDuration;
            copy.currentCrafts = this.currentCrafts;
            return copy;
        }

        public String getName() {
            try {
                Identifier id = Identifier.parse(this.itemId);
                Item item = BuiltInRegistries.ITEM.getValue(id);
                return item.getName().getString();
            } catch (Exception e) {
                return this.itemId;
            }
        }

        @Override
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("itemId", this.itemId);
            tag.putBoolean("loop", this.loop);
            tag.putBoolean("drop", this.drop);
            tag.putBoolean("shiftClick", this.shiftClick);
            tag.putInt("fireworkDuration", this.fireworkDuration);
            return tag;
        }

        public AutoCraft.CraftButton fromTag(CompoundTag tag) {
            this.itemId = tag.getString("itemId").orElse("");
            this.loop = tag.getBoolean("loop").orElse(false);
            this.drop = tag.getBoolean("drop").orElse(false);
            this.shiftClick = tag.getBoolean("shiftClick").orElse(false);
            this.fireworkDuration = tag.getInt("fireworkDuration").orElse(1);
            return this;
        }
    }

    private static class CraftButtonConfigScreen extends WindowScreen {
        private final AutoCraft module;

        public CraftButtonConfigScreen(GuiTheme theme, AutoCraft module) {
            super(theme, "Craft Buttons");
            this.module = module;
        }

        @Override
        public void initWidgets() {
            WTable table = this.add(this.theme.table()).expandX().widget();
            table.add(this.theme.label("Item")).expandCellX();
            table.add(this.theme.label("Loop"));
            table.add(this.theme.label("Drop"));
            table.add(this.theme.label("Shift"));
            table.add(this.theme.label("Edit"));
            table.add(this.theme.label("Delete"));
            table.row();

            for (int i = 0; i < this.module.craftButtons.size(); i++) {
                int index = i;
                AutoCraft.CraftButton btn = this.module.craftButtons.get(i);

                try {
                    Identifier id = Identifier.parse(btn.itemId);
                    Item item = BuiltInRegistries.ITEM.getValue(id);
                    table.add(this.theme.label(item.getName().getString()));
                } catch (Exception e) {
                    table.add(this.theme.label(btn.itemId));
                }

                table.add(this.theme.label(btn.loop ? "Yes" : "No"));
                table.add(this.theme.label(btn.drop ? "Yes" : "No"));
                table.add(this.theme.label(btn.shiftClick ? "Yes" : "No"));
                WButton editBtn = table.add(this.theme.button("Edit")).widget();
                editBtn.action = () -> this.minecraft.setScreen(new AutoCraft.EditButtonScreen(this.theme, this.module, index));
                WButton deleteBtn = table.add(this.theme.button("Delete")).widget();
                deleteBtn.action = () -> {
                    this.module.craftButtons.remove(index);
                    this.reload();
                };
                table.row();
            }

            WButton addBtn = this.add(this.theme.button("Add New Button")).expandX().widget();
            addBtn.action = () -> this.minecraft.setScreen(new AutoCraft.EditButtonScreen(this.theme, this.module, -1));
        }
    }

    private static class EditButtonScreen extends WindowScreen {
        private final AutoCraft module;
        private final int buttonIndex;
        private final AutoCraft.CraftButton button;
        private boolean loopEnabled;
        private boolean dropEnabled;
        private boolean shiftClickEnabled;
        private Item selectedItem;
        private int fireworkDuration;

        public EditButtonScreen(GuiTheme theme, AutoCraft module, int buttonIndex) {
            super(theme, buttonIndex == -1 ? "Add Button" : "Edit Button");
            this.module = module;
            this.buttonIndex = buttonIndex;
            if (buttonIndex >= 0 && buttonIndex < module.craftButtons.size()) {
                this.button = module.craftButtons.get(buttonIndex).copy();
            } else {
                this.button = new AutoCraft.CraftButton("minecraft:stick", false, false, false);
            }

            this.loopEnabled = this.button.loop;
            this.dropEnabled = this.button.drop;
            this.shiftClickEnabled = this.button.shiftClick;
            this.fireworkDuration = this.button.fireworkDuration;

            try {
                Identifier id = Identifier.parse(this.button.itemId);
                this.selectedItem = BuiltInRegistries.ITEM.getValue(id);
            } catch (Exception e) {
                this.selectedItem = Items.STICK;
            }
        }

        @Override
        public void initWidgets() {
            WTable table = this.add(this.theme.table()).expandX().widget();
            table.add(this.theme.label("Craft Item:"));
            WButton itemSelectBtn = table.add(this.theme.button(this.selectedItem.getName().getString())).minWidth(200.0).expandX().widget();
            itemSelectBtn.action = () -> this.minecraft.setScreen(new AutoCraft.ItemSelectorScreen(this.theme, this));
            table.row();
            table.add(this.theme.label("Loop Crafting:"));
            WButton loopBtn = table.add(this.theme.button(this.loopEnabled ? "Enabled" : "Disabled")).widget();
            loopBtn.action = () -> {
                this.loopEnabled = !this.loopEnabled;
                loopBtn.set(this.loopEnabled ? "Enabled" : "Disabled");
            };
            table.row();
            table.add(this.theme.label("Drop Items:"));
            WButton dropBtn = table.add(this.theme.button(this.dropEnabled ? "Enabled" : "Disabled")).widget();
            dropBtn.action = () -> {
                this.dropEnabled = !this.dropEnabled;
                dropBtn.set(this.dropEnabled ? "Enabled" : "Disabled");
            };
            table.row();
            table.add(this.theme.label("Shift-Click Mode:"));
            WButton shiftBtn = table.add(this.theme.button(this.shiftClickEnabled ? "Enabled" : "Disabled")).widget();
            shiftBtn.action = () -> {
                this.shiftClickEnabled = !this.shiftClickEnabled;
                shiftBtn.set(this.shiftClickEnabled ? "Enabled" : "Disabled");
            };
            table.row();
            if (this.selectedItem == Items.FIREWORK_ROCKET) {
                table.add(this.theme.label("Firework Duration:"));
                WButton durationBtn = table.add(this.theme.button("Flight: " + this.fireworkDuration)).widget();
                durationBtn.action = () -> {
                    this.fireworkDuration = this.fireworkDuration % 3 + 1;
                    durationBtn.set("Flight: " + this.fireworkDuration);
                };
                table.row();
            }

            table.add(this.theme.horizontalSeparator()).expandX();
            table.add(this.theme.horizontalSeparator()).expandX();
            table.row();
            table.add(this.theme.label("Loop: Continuously craft until materials run out")).expandCellX();
            table.add(this.theme.label(""));
            table.row();
            table.add(this.theme.label("Shift-Click: Use recipe book shift-click (craft max)")).expandCellX();
            table.add(this.theme.label(""));
            table.row();
            table.add(this.theme.horizontalSeparator()).expandX();
            table.add(this.theme.horizontalSeparator()).expandX();
            table.row();
            WButton saveBtn = table.add(this.theme.button("Save")).expandX().widget();
            table.add(this.theme.label("")).expandX();
            saveBtn.action = () -> {
                String itemId = BuiltInRegistries.ITEM.getKey(this.selectedItem).toString();
                AutoCraft.CraftButton newButton = new AutoCraft.CraftButton(itemId, this.loopEnabled, this.dropEnabled, this.shiftClickEnabled);
                newButton.fireworkDuration = this.fireworkDuration;
                if (this.buttonIndex >= 0 && this.buttonIndex < this.module.craftButtons.size()) {
                    this.module.craftButtons.set(this.buttonIndex, newButton);
                } else {
                    this.module.craftButtons.add(newButton);
                }

                this.minecraft.setScreen(new AutoCraft.CraftButtonConfigScreen(this.theme, this.module));
            };
            table.row();
            WButton cancelBtn = table.add(this.theme.button("Cancel")).expandX().widget();
            cancelBtn.action = () -> this.minecraft.setScreen(new AutoCraft.CraftButtonConfigScreen(this.theme, this.module));
            table.add(this.theme.label("")).expandX();
        }

        public void setSelectedItem(Item item) {
            this.selectedItem = item;
        }
    }

    private static class ItemSelectorScreen extends WindowScreen {
        private final AutoCraft.EditButtonScreen parentScreen;
        private String searchQuery = "";
        private WTextBox searchBox;

        public ItemSelectorScreen(GuiTheme theme, AutoCraft.EditButtonScreen parentScreen) {
            super(theme, "Select Item");
            this.parentScreen = parentScreen;
        }

        @Override
        public void initWidgets() {
            WTable searchTable = this.add(this.theme.table()).expandX().widget();
            searchTable.add(this.theme.label("Search:"));
            this.searchBox = searchTable.add(this.theme.textBox(this.searchQuery)).minWidth(300.0).expandX().widget();
            this.searchBox.setFocused(true);
            WButton searchButton = searchTable.add(this.theme.button("Search")).widget();
            searchButton.action = () -> {
                this.searchQuery = this.searchBox.get().toLowerCase();
                this.reload();
            };
            this.add(this.theme.horizontalSeparator()).expandX();
            List<Item> itemsToShow = new ArrayList<>();
            List<Item> commonItems = new ArrayList<>();
            commonItems.add(Items.OAK_PLANKS);
            commonItems.add(Items.STICK);
            commonItems.add(Items.TORCH);
            commonItems.add(Items.CRAFTING_TABLE);
            commonItems.add(Items.CHEST);
            commonItems.add(Items.FURNACE);
            commonItems.add(Items.LADDER);
            commonItems.add(Items.FIREWORK_ROCKET);
            commonItems.add(Items.PAPER);
            commonItems.add(Items.BOOK);
            commonItems.add(Items.IRON_BLOCK);
            commonItems.add(Items.GOLD_BLOCK);
            commonItems.add(Items.DIAMOND_BLOCK);
            commonItems.add(Items.GOLD_INGOT);
            commonItems.add(Items.IRON_INGOT);
            commonItems.add(Items.SHULKER_BOX);
            if (this.searchQuery.isEmpty()) {
                itemsToShow.addAll(commonItems);
            } else {
                for (Item item : BuiltInRegistries.ITEM) {
                    if (item != Items.AIR) {
                        String itemName = item.getName().getString().toLowerCase();
                        String itemId = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase();
                        if (itemName.contains(this.searchQuery) || itemId.contains(this.searchQuery)) {
                            itemsToShow.add(item);
                            if (itemsToShow.size() >= 50) {
                                break;
                            }
                        }
                    }
                }
            }

            WTable itemTable = this.add(this.theme.table()).expandX().widget();
            int col = 0;

            for (Item item : itemsToShow) {
                String displayName = item.getName().getString();
                WButton itemBtn = itemTable.add(this.theme.button(displayName)).minWidth(180.0).widget();
                Item finalItem = item;
                itemBtn.action = () -> {
                    this.parentScreen.setSelectedItem(finalItem);
                    this.minecraft.setScreen(this.parentScreen);
                };
                if (++col >= 2) {
                    col = 0;
                    itemTable.row();
                }
            }

            if (itemsToShow.isEmpty()) {
                itemTable.add(this.theme.label("No items found"));
            }

            this.add(this.theme.horizontalSeparator()).expandX();
            WButton backBtn = this.add(this.theme.button("Back")).expandX().widget();
            backBtn.action = () -> this.minecraft.setScreen(this.parentScreen);
        }
    }
}
