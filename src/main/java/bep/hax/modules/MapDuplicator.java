package bep.hax.modules;

import bep.hax.Bep;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MapDuplicator extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgLimits = this.settings.getDefaultGroup();
    private final Setting<Boolean> showStatus = this.sgGeneral
        .add(new Builder().name("show-status").description("Show status messages in chat.").defaultValue(true).build());
    private final Setting<Boolean> silentCrafting = this.sgGeneral
        .add(
            new Builder()
                .name("silent-crafting")
                .description("Allow crafting without opening inventory (may not work on all servers).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> craftingLoops = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("crafting-loops")
                .description("Number of times to duplicate each map stack.")
                .defaultValue(1)
                .min(1)
                .max(64)
                .sliderMin(1)
                .sliderMax(64)
                .build()
        );
    private final Setting<Integer> clickDelay = this.sgLimits
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("click-delay")
                .description("Delay between clicks in ticks.")
                .defaultValue(2)
                .min(1)
                .max(10)
                .build()
        );
    private int tickCounter = 0;
    private boolean isCrafting = false;
    private int mapsToDuplicate = 0;
    private int emptyMapsAvailable = 0;
    private List<MapDuplicator.CraftingTask> craftingQueue = new ArrayList<>();
    private int currentTaskIndex = 0;
    private int craftingStep = 0;

    public MapDuplicator() {
        super(Bep.CATEGORY, "Map Copier", "Automatically duplicates all maps using inventory crafting.");
    }

    @Override
    public void onActivate() {
        this.tickCounter = 0;
        this.isCrafting = false;
        this.currentTaskIndex = 0;
        this.craftingStep = 0;
        this.craftingQueue.clear();
        if (!this.silentCrafting.get()) {
            this.mc.execute(() -> {
                if (this.mc.player != null) {
                    this.mc.setScreen(new InventoryScreen(this.mc.player));
                }
            });
        }
    }

    @Override
    public void onDeactivate() {
        this.isCrafting = false;
        this.craftingQueue.clear();
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.player != null && this.mc.level != null) {
            this.tickCounter++;
            if (!this.isCrafting) {
                if (!this.silentCrafting.get()
                    && !(this.mc.player.containerMenu instanceof InventoryMenu)
                    && !(this.mc.player.containerMenu instanceof CraftingMenu)) {
                    return;
                }

                this.analyzeInventory();
                int totalEmptyMapsNeeded = this.mapsToDuplicate * this.craftingLoops.get();
                if (this.mapsToDuplicate == 0 || this.emptyMapsAvailable < totalEmptyMapsNeeded) {
                    if (this.showStatus.get()) {
                        if (this.mapsToDuplicate == 0) {
                            this.info("No maps to duplicate found.");
                        } else {
                            this.error(
                                "Insufficient empty maps! Need "
                                    + totalEmptyMapsNeeded
                                    + " ("
                                    + this.mapsToDuplicate
                                    + " stacks × "
                                    + this.craftingLoops.get()
                                    + ") but only have "
                                    + this.emptyMapsAvailable
                                    + "."
                            );
                        }
                    }

                    this.toggle();
                    return;
                }

                this.startCrafting();
            }

            if (this.isCrafting && this.tickCounter >= this.clickDelay.get()) {
                this.tickCounter = 0;
                this.processCrafting();
            }
        }
    }

    private void analyzeInventory() {
        this.mapsToDuplicate = 0;
        this.emptyMapsAvailable = 0;
        this.craftingQueue.clear();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.FILLED_MAP && stack.getCount() > 0) {
                this.mapsToDuplicate++;

                for (int loop = 0; loop < this.craftingLoops.get(); loop++) {
                    this.craftingQueue.add(new MapDuplicator.CraftingTask(SlotUtils.indexToId(i)));
                }
            }
        }

        for (int i = 9; i < this.mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.FILLED_MAP && stack.getCount() > 0) {
                this.mapsToDuplicate++;

                for (int loop = 0; loop < this.craftingLoops.get(); loop++) {
                    this.craftingQueue.add(new MapDuplicator.CraftingTask(SlotUtils.indexToId(i)));
                }
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.MAP) {
                this.emptyMapsAvailable = this.emptyMapsAvailable + stack.getCount();
            }
        }

        for (int i = 9; i < this.mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.MAP) {
                this.emptyMapsAvailable = this.emptyMapsAvailable + stack.getCount();
            }
        }

        if (this.mc.player.containerMenu instanceof InventoryMenu || this.mc.player.containerMenu instanceof CraftingMenu) {
            AbstractContainerMenu handler = this.mc.player.containerMenu;
            if (handler instanceof InventoryMenu) {
                for (int i = 1; i <= 4; i++) {
                    try {
                        if (i < handler.slots.size()) {
                            ItemStack stack = handler.getSlot(i).getItem();
                            if (stack.getItem() == Items.MAP) {
                                this.emptyMapsAvailable = this.emptyMapsAvailable + stack.getCount();
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            } else if (handler instanceof CraftingMenu) {
                for (int i = 1; i <= 9; i++) {
                    try {
                        if (i < handler.slots.size()) {
                            ItemStack stack = handler.getSlot(i).getItem();
                            if (stack.getItem() == Items.MAP) {
                                this.emptyMapsAvailable = this.emptyMapsAvailable + stack.getCount();
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }

        int totalEmptyMapsNeeded = this.mapsToDuplicate * this.craftingLoops.get();
        if (this.emptyMapsAvailable >= totalEmptyMapsNeeded) {
            if (this.showStatus.get()) {
                this.info(
                    "Found "
                        + this.mapsToDuplicate
                        + " filled map stacks and "
                        + this.emptyMapsAvailable
                        + " empty maps. Will duplicate "
                        + this.mapsToDuplicate
                        + " stacks "
                        + this.craftingLoops.get()
                        + " times each."
                );
            }
        }
    }

    private void startCrafting() {
        if (this.craftingQueue.isEmpty()) {
            if (this.showStatus.get()) {
                this.error("Cannot start crafting: no maps to duplicate.");
            }
        } else {
            int totalEmptyMapsNeeded = this.mapsToDuplicate * this.craftingLoops.get();
            if (this.emptyMapsAvailable < totalEmptyMapsNeeded) {
                if (this.showStatus.get()) {
                    this.error(
                        "Cannot start crafting: insufficient empty maps. Need " + totalEmptyMapsNeeded + " but only have " + this.emptyMapsAvailable + "."
                    );
                }
            } else {
                this.currentTaskIndex = 0;
                this.craftingStep = 0;
                this.isCrafting = true;
                if (this.showStatus.get()) {
                    this.info(
                        "Starting map duplication process for "
                            + this.craftingQueue.size()
                            + " crafting tasks ("
                            + this.mapsToDuplicate
                            + " stacks × "
                            + this.craftingLoops.get()
                            + " loops)..."
                    );
                }
            }
        }
    }

    private void processCrafting() {
        if (!this.isCrafting || this.currentTaskIndex >= this.craftingQueue.size()) {
            this.finishCrafting();
        } else if (!this.silentCrafting.get()
            && !(this.mc.player.containerMenu instanceof InventoryMenu)
            && !(this.mc.player.containerMenu instanceof CraftingMenu)) {
            if (this.showStatus.get()) {
                this.error("Please open your inventory or a crafting table to continue duplication.");
            }

            this.finishCrafting();
        } else {
            this.performCraftingStep();
        }
    }

    private void performCraftingStep() {
        if (this.currentTaskIndex >= this.craftingQueue.size()) {
            this.finishCrafting();
        } else {
            MapDuplicator.CraftingTask currentTask = this.craftingQueue.get(this.currentTaskIndex);
            switch (this.craftingStep) {
                case 0:
                    if (this.isValidSlot(currentTask.mapSlot)) {
                        InvUtils.click().slotId(currentTask.mapSlot);
                        if (this.isValidSlot(1)) {
                            InvUtils.click().slotId(1);
                        }

                        this.craftingStep = 1;
                    } else {
                        this.info("Invalid source slot: " + currentTask.mapSlot);
                        this.finishCrafting();
                    }
                    break;
                case 1:
                    int emptyMapSlot = this.findNextEmptyMap();
                    if (emptyMapSlot != -1) {
                        if (this.isValidSlot(emptyMapSlot)) {
                            InvUtils.click().slotId(emptyMapSlot);
                            if (this.isValidSlot(2)) {
                                InvUtils.click().slotId(2);
                            }

                            this.craftingStep = 2;
                        } else {
                            this.info("Invalid empty map slot: " + emptyMapSlot);
                            this.finishCrafting();
                        }
                    } else {
                        this.info("No empty maps available");
                        this.finishCrafting();
                    }
                    break;
                case 2:
                    if (this.isValidSlot(0)) {
                        InvUtils.click().slotId(0);
                        this.craftingStep = 3;
                    } else {
                        this.info("Invalid output slot");
                        this.finishCrafting();
                    }
                    break;
                case 3:
                    if (this.isValidSlot(currentTask.mapSlot)) {
                        InvUtils.click().slotId(currentTask.mapSlot);
                        this.craftingStep = 4;
                    } else {
                        this.info("Invalid target slot: " + currentTask.mapSlot);
                        this.finishCrafting();
                    }
                    break;
                case 4:
                    if (this.isValidSlot(1) && this.isValidSlot(currentTask.mapSlot)) {
                        InvUtils.shiftClick().slotId(1);
                    }

                    this.craftingStep = 5;
                    break;
                case 5:
                    MapDuplicator.CraftingTask nextTask = this.currentTaskIndex + 1 < this.craftingQueue.size()
                        ? this.craftingQueue.get(this.currentTaskIndex + 1)
                        : null;
                    if ((nextTask == null || nextTask.mapSlot != currentTask.mapSlot) && this.showStatus.get()) {
                        this.info("Map stack completed all " + this.craftingLoops.get() + " loop(s)!");
                    }

                    this.currentTaskIndex++;
                    this.craftingStep = 0;
            }
        }
    }

    private boolean isValidSlot(int slotId) {
        try {
            if (this.mc.player != null && this.mc.player.containerMenu != null) {
                AbstractContainerMenu handler = this.mc.player.containerMenu;
                return slotId >= 0 && slotId < handler.slots.size() ? handler.getSlot(slotId) != null : false;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void clearCraftingGrid() {
        try {
            AbstractContainerMenu handler = this.mc.player.containerMenu;
            if (handler instanceof InventoryMenu || handler instanceof CraftingMenu) {
                int startSlot = handler instanceof InventoryMenu ? 1 : 1;
                int endSlot = handler instanceof InventoryMenu ? 4 : 9;

                for (int slot = startSlot; slot <= endSlot; slot++) {
                    if (this.isValidSlot(slot) && handler.getSlot(slot).hasItem()) {
                        InvUtils.click().slotId(slot);
                        int emptySlot = this.findEmptyInventorySlot();
                        if (emptySlot >= 0) {
                            int emptySlotId = SlotUtils.indexToId(emptySlot);
                            if (this.isValidSlot(emptySlotId)) {
                                InvUtils.click().slotId(emptySlotId);
                            }
                        }
                    }
                }

                if (this.showStatus.get()) {
                    this.info("Crafting grid cleared - all items moved to inventory");
                }
            }
        } catch (Exception e) {
            if (this.showStatus.get()) {
                this.error("Error clearing crafting grid: " + e.getMessage());
            }
        }
    }

    private int findEmptyInventorySlot() {
        for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private int findNextEmptyMap() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.MAP) {
                return SlotUtils.indexToId(i);
            }
        }

        for (int i = 9; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.MAP) {
                return SlotUtils.indexToId(i);
            }
        }

        AbstractContainerMenu handler = this.mc.player.containerMenu;
        if (handler instanceof InventoryMenu) {
            for (int i = 1; i <= 4; i++) {
                ItemStack stack = handler.getSlot(i).getItem();
                if (stack.getItem() == Items.MAP) {
                    return i;
                }
            }
        } else if (handler instanceof CraftingMenu) {
            for (int i = 1; i <= 9; i++) {
                ItemStack stack = handler.getSlot(i).getItem();
                if (stack.getItem() == Items.MAP) {
                    return i;
                }
            }
        }

        return -1;
    }

    private void finishCrafting() {
        this.clearCraftingGrid();
        this.isCrafting = false;
        if (this.showStatus.get()) {
            this.info("Map duplication completed! Duplicated " + this.currentTaskIndex + " maps.");
        }

        this.toggle();
    }

    @Override
    public String getInfoString() {
        return this.mapsToDuplicate + "/" + this.emptyMapsAvailable;
    }

    private static class CraftingTask {
        public final int mapSlot;

        public CraftingTask(int mapSlot) {
            this.mapSlot = mapSlot;
        }
    }
}
