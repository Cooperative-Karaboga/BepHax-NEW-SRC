package bep.hax.modules;

import bep.hax.Bep;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShovelItem;

public class Replenish extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgItems = this.settings.createGroup("Items");
    private final SettingGroup sgAdvanced = this.settings.createGroup("Advanced");
    private final Setting<Integer> threshold = this.sgGeneral
        .add(
            new Builder()
                .name("threshold")
                .description("Refill when stack reaches this amount.")
                .defaultValue(8)
                .min(1)
                .max(63)
                .sliderMin(1)
                .sliderMax(63)
                .build()
        );
    private final Setting<Integer> tickDelay = this.sgGeneral
        .add(
            new Builder()
                .name("tick-delay")
                .description("Delay in ticks between refill operations.")
                .defaultValue(0)
                .min(0)
                .max(10)
                .sliderMin(0)
                .sliderMax(10)
                .build()
        );
    private final Setting<Boolean> pauseOnUse = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("pause-on-use")
                .description("Pause refilling while using items.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> smartRefill = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("smart-refill")
                .description("Only refill when you're about to run out completely.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Replenish.StackPreference> stackPreference = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("stack-preference"))
                        .description("Which stacks to prioritize when refilling."))
                    .defaultValue(Replenish.StackPreference.SmallStacks))
                .build()
        );
    private final Setting<Boolean> refillBlocks = this.sgItems
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("blocks").description("Refill building blocks.").defaultValue(true).build());
    private final Setting<Boolean> refillFood = this.sgItems
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("food").description("Refill food items.").defaultValue(true).build());
    private final Setting<Boolean> refillTools = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("tools")
                .description("Refill tools (pickaxe, axe, shovel, etc).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> refillWeapons = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("weapons")
                .description("Refill weapons (sword, bow, crossbow).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> refillProjectiles = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("projectiles")
                .description("Refill projectiles (arrows, fireworks).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> refillPearls = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("ender-pearls")
                .description("Refill ender pearls.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> refillPotions = this.sgItems
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("potions").description("Refill potions.").defaultValue(true).build());
    private final Setting<Boolean> refillTotems = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("totems")
                .description("Refill totems of undying.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> refillGaps = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("golden-apples")
                .description("Refill golden apples.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> refillFireworks = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("fireworks")
                .description("Refill firework rockets.")
                .defaultValue(true)
                .build()
        );
    private final Setting<List<Item>> blacklist = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("blacklist")
                .description("Items that will never be replenished, regardless of other settings.")
                .build()
        );
    private final Setting<Boolean> useShiftClick = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("use-shift-click")
                .description("Use shift-click packets for faster refilling.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> silentRefill = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("silent-refill")
                .description("Refill without opening inventory (packet-based).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> maxRefillsPerTick = this.sgAdvanced
        .add(
            new Builder()
                .name("max-refills-per-tick")
                .description("Maximum refill operations per tick.")
                .defaultValue(1)
                .min(1)
                .max(5)
                .sliderMin(1)
                .sliderMax(5)
                .build()
        );
    private final Setting<Boolean> maintainTool = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("maintain-tool-type")
                .description("Only replace tools with the same type and material.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> respectCustomNames = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("respect-custom-names")
                .description("Wait until custom-named items are completely empty before replacing with differently-named items.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> refillAllStackable = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("refill-all-stackable")
                .description("Refill ALL stackable items, not just specific categories.")
                .defaultValue(true)
                .build()
        );
    private int delayTicks = 0;
    private final Map<Integer, Integer> lastStackSizes = new HashMap<>();
    private final List<Replenish.RefillOperation> pendingRefills = new ArrayList<>();
    private final Map<Integer, String> hotbarItemNames = new HashMap<>();

    public Replenish() {
        super(Bep.CATEGORY, "replenish", "Advanced auto replenish using shift-click packets.");
    }

    @Override
    public void onActivate() {
        this.delayTicks = 0;
        this.lastStackSizes.clear();
        this.pendingRefills.clear();
        this.hotbarItemNames.clear();
    }

    @Override
    public void onDeactivate() {
        this.lastStackSizes.clear();
        this.pendingRefills.clear();
        this.hotbarItemNames.clear();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.delayTicks > 0) {
                this.delayTicks--;
            } else if (!this.pauseOnUse.get() || !this.mc.player.isUsingItem()) {
                if (!this.pendingRefills.isEmpty()) {
                    this.processPendingRefills();
                } else {
                    this.checkHotbar();
                }
            }
        }
    }

    private void checkHotbar() {
        int refillsThisTick = 0;

        for (int hotbarSlot = 0; hotbarSlot < 9 && refillsThisTick < this.maxRefillsPerTick.get(); hotbarSlot++) {
            ItemStack hotbarStack = this.mc.player.getInventory().getItem(hotbarSlot);
            if (hotbarStack.isEmpty()) {
                this.hotbarItemNames.remove(hotbarSlot);
            } else if (this.shouldRefillItem(hotbarStack)) {
                int currentSize = hotbarStack.getCount();
                int maxSize = hotbarStack.getMaxStackSize();
                if (this.smartRefill.get()) {
                    Integer lastSize = this.lastStackSizes.get(hotbarSlot);
                    if (lastSize != null && lastSize > currentSize && currentSize <= 1 && this.attemptRefill(hotbarSlot, hotbarStack)) {
                        refillsThisTick++;
                    }

                    this.lastStackSizes.put(hotbarSlot, currentSize);
                } else if (currentSize <= this.threshold.get() && currentSize < maxSize && this.attemptRefill(hotbarSlot, hotbarStack)) {
                    refillsThisTick++;
                }
            }
        }

        if (refillsThisTick > 0) {
            this.delayTicks = this.tickDelay.get();
        }
    }

    private boolean shouldRefillItem(ItemStack stack) {
        Item item = stack.getItem();
        if (this.blacklist.get().contains(item)) {
            return false;
        }

        if (this.refillAllStackable.get() && stack.getMaxStackSize() > 1) {
            return true;
        }

        if (this.refillTotems.get() && item == Items.TOTEM_OF_UNDYING) {
            return true;
        }

        if (this.refillPearls.get() && item == Items.ENDER_PEARL) {
            return true;
        }

        if (!this.refillGaps.get() || item != Items.GOLDEN_APPLE && item != Items.ENCHANTED_GOLDEN_APPLE) {
            if (this.refillFireworks.get() && item == Items.FIREWORK_ROCKET) {
                return true;
            }

            if (this.refillBlocks.get() && item instanceof BlockItem) {
                return true;
            }

            if (this.refillFood.get() && item.components().has(DataComponents.FOOD)) {
                return true;
            }

            if (!this.refillTools.get()
                || !(item instanceof ShovelItem)
                    && !(item instanceof AxeItem)
                    && !(item instanceof HoeItem)
                    && !item.toString().toLowerCase().contains("pickaxe")) {
                if (!this.refillWeapons.get()
                    || !item.toString().toLowerCase().contains("sword") && !(item instanceof BowItem) && !(item instanceof CrossbowItem)) {
                    return !this.refillProjectiles.get() || !(item instanceof ArrowItem) && item != Items.FIREWORK_ROCKET
                        ? this.refillPotions.get() && item instanceof PotionItem
                        : true;
                } else {
                    return true;
                }
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    private boolean attemptRefill(int hotbarSlot, ItemStack hotbarStack) {
        if (this.respectCustomNames.get() && hotbarStack.getMaxStackSize() > 1) {
            String currentName = this.getItemName(hotbarStack);
            String trackedName = this.hotbarItemNames.get(hotbarSlot);
            if (trackedName == null) {
                this.hotbarItemNames.put(hotbarSlot, currentName);
                trackedName = currentName;
            }

            int sourceSlot = this.findSourceSlot(hotbarStack);
            if (sourceSlot == -1) {
                this.hotbarItemNames.remove(hotbarSlot);
                return false;
            }

            ItemStack sourceStack = this.mc.player.getInventory().getItem(sourceSlot);
            String sourceName = this.getItemName(sourceStack);
            if (!trackedName.equals(sourceName)) {
                if (hotbarStack.getCount() > 1) {
                    return false;
                }

                this.hotbarItemNames.put(hotbarSlot, sourceName);
            }
        } else {
            int sourceSlot = this.findSourceSlot(hotbarStack);
            if (sourceSlot == -1) {
                return false;
            }
        }

        int sourceSlot = this.findSourceSlot(hotbarStack);
        if (sourceSlot == -1) {
            return false;
        }

        Replenish.RefillOperation operation = new Replenish.RefillOperation(sourceSlot, hotbarSlot + 36);
        if (this.useShiftClick.get()) {
            this.performShiftClickRefill(operation);
        } else {
            this.performNormalRefill(operation);
        }

        return true;
    }

    private String getItemName(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            Component customName = stack.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                return customName.getString();
            }
        }

        return stack.getItem().getName().getString();
    }

    private int findSourceSlot(ItemStack targetStack) {
        int bestSlot = -1;
        int bestCount = 0;
        boolean searchingForMax = this.stackPreference.get() == Replenish.StackPreference.FullStacks;
        boolean searchingForMin = this.stackPreference.get() == Replenish.StackPreference.SmallStacks;
        if (searchingForMin) {
            bestCount = Integer.MAX_VALUE;
        }

        for (int i = 9; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()
                && this.canStack(targetStack, stack)
                && (
                    !this.maintainTool.get()
                        || !(targetStack.getItem() instanceof ShovelItem)
                            && !(targetStack.getItem() instanceof AxeItem)
                            && !(targetStack.getItem() instanceof HoeItem)
                            && !targetStack.getItem().toString().toLowerCase().contains("pickaxe")
                        || stack.getItem().getClass() == targetStack.getItem().getClass()
                )) {
                if (this.stackPreference.get() == Replenish.StackPreference.FirstMatch) {
                    return i;
                }

                if (searchingForMax) {
                    if (stack.getCount() > bestCount) {
                        bestCount = stack.getCount();
                        bestSlot = i;
                    }
                } else if (searchingForMin && stack.getCount() < bestCount) {
                    bestCount = stack.getCount();
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    private boolean canStack(ItemStack stack1, ItemStack stack2) {
        if (stack1.getItem() != stack2.getItem()) {
            return false;
        } else if (stack1.getMaxStackSize() == 1) {
            return true;
        } else {
            return this.respectCustomNames.get() ? ItemStack.isSameItemSameComponents(stack1, stack2) : ItemStack.isSameItem(stack1, stack2);
        }
    }

    private void performShiftClickRefill(Replenish.RefillOperation operation) {
        if (this.silentRefill.get()) {
            int syncId = this.mc.player.containerMenu.containerId;
            this.mc.gameMode.handleInventoryMouseClick(syncId, operation.sourceSlot, 0, ClickType.QUICK_MOVE, this.mc.player);
        } else {
            this.pendingRefills.add(operation);
        }
    }

    private void performNormalRefill(Replenish.RefillOperation operation) {
        InvUtils.move().from(operation.sourceSlot).to(operation.targetSlot - 36);
    }

    private void processPendingRefills() {
        if (this.mc.player.containerMenu == this.mc.player.inventoryMenu) {
            int processed;
            for (processed = 0; !this.pendingRefills.isEmpty() && processed < this.maxRefillsPerTick.get(); processed++) {
                Replenish.RefillOperation operation = this.pendingRefills.remove(0);
                int syncId = this.mc.player.containerMenu.containerId;
                this.mc.gameMode.handleInventoryMouseClick(syncId, operation.sourceSlot, 0, ClickType.QUICK_MOVE, this.mc.player);
            }

            if (processed > 0) {
                this.delayTicks = this.tickDelay.get();
            }
        }
    }

    @Override
    public String getInfoString() {
        return !this.pendingRefills.isEmpty() ? "Refilling (" + this.pendingRefills.size() + ")" : null;
    }

    private static class RefillOperation {
        final int sourceSlot;
        final int targetSlot;

        RefillOperation(int sourceSlot, int targetSlot) {
            this.sourceSlot = sourceSlot;
            this.targetSlot = targetSlot;
        }
    }

    public enum StackPreference {
        FirstMatch("First Match"),
        FullStacks("Full Stacks"),
        SmallStacks("Small Stacks");

        private final String title;

        StackPreference(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
        }
    }
}
