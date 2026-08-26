package bep.hax.modules;

import bep.hax.Bep;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ElytraSwap extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Integer> durabilityThreshold = this.sgGeneral
        .add(
            new Builder()
                .name("Durability Threshold")
                .description("Swap elytra when durability drops below this value.")
                .defaultValue(10)
                .min(1)
                .max(100)
                .sliderRange(1, 50)
                .build()
        );
    private final Setting<Boolean> onlyWhileFlying = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("Only While Flying")
                .description("Only swap elytras while actively flying.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> pauseInInventory = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("Pause In Inventory")
                .description("Don't swap while inventory is open to prevent desync.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> swapCooldown = this.sgGeneral
        .add(
            new Builder()
                .name("Swap Cooldown")
                .description("Ticks to wait after swapping before checking again.")
                .defaultValue(100)
                .min(20)
                .max(200)
                .sliderRange(20, 200)
                .build()
        );
    private final Setting<Integer> stageDelay = this.sgGeneral
        .add(
            new Builder()
                .name("Stage Delay")
                .description("Ticks to wait between swap stages. Higher values are safer for anticheat.")
                .defaultValue(8)
                .min(3)
                .max(20)
                .sliderRange(3, 20)
                .build()
        );
    private final Setting<Boolean> notifySwap = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("Notify Swap")
                .description("Send a chat message when swapping elytras.")
                .defaultValue(true)
                .build()
        );
    private final SettingGroup sgCombat = this.settings.createGroup("Combat Protection");
    private final Setting<Boolean> swapOnHit = this.sgCombat
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("Swap On Hit")
                .description("Automatically swap elytra to chestplate when hit by an entity.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> hitProtectionDuration = this.sgCombat
        .add(
            new Builder()
                .name("Protection Duration")
                .description("Ticks to keep chestplate equipped after being hit.")
                .defaultValue(60)
                .min(20)
                .max(200)
                .sliderRange(20, 200)
                .visible(this.swapOnHit::get)
                .build()
        );
    private final Setting<Boolean> autoSwapBack = this.sgCombat
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("Auto Swap Back")
                .description("Automatically swap back to elytra after protection duration.")
                .defaultValue(true)
                .visible(this.swapOnHit::get)
                .build()
        );
    private final Setting<Boolean> prioritizeNetherite = this.sgCombat
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("Prioritize Netherite")
                .description("Prioritize netherite chestplates over diamond.")
                .defaultValue(true)
                .visible(this.swapOnHit::get)
                .build()
        );
    private int cooldownTimer = 0;
    private boolean needsSwap = false;
    private int swapStage = 0;
    private int stageTimer = 0;
    private int targetSlot = -1;
    private int newElytraOriginalSlot = -1;
    private int hotbarSlotUsed = -1;
    private ItemStack hotbarOriginalItem = ItemStack.EMPTY;
    private boolean protectionActive = false;
    private int protectionTimer = 0;
    private int lastHurtTime = 0;
    private boolean needsChestplateSwap = false;
    private int chestplateSwapStage = 0;
    private int chestplateSlot = -1;
    private ItemStack storedElytra = ItemStack.EMPTY;

    public ElytraSwap() {
        super(Bep.HUNT_CATEGORY, "ElytraSwap", "Automatically swaps elytras when they reach low durability.");
    }

    @Override
    public void onActivate() {
        this.resetSwapState();
    }

    @Override
    public void onDeactivate() {
        this.resetSwapState();
    }

    private void resetSwapState() {
        this.cooldownTimer = 0;
        this.needsSwap = false;
        this.swapStage = 0;
        this.stageTimer = 0;
        this.targetSlot = -1;
        this.newElytraOriginalSlot = -1;
        this.hotbarSlotUsed = -1;
        this.hotbarOriginalItem = ItemStack.EMPTY;
        this.protectionActive = false;
        this.protectionTimer = 0;
        this.lastHurtTime = 0;
        this.needsChestplateSwap = false;
        this.chestplateSwapStage = 0;
        this.chestplateSlot = -1;
        this.storedElytra = ItemStack.EMPTY;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.swapOnHit.get()) {
                this.handleCombatProtection();
            }

            if (this.cooldownTimer > 0) {
                this.cooldownTimer--;
            } else if (this.pauseInInventory.get() && this.mc.player.containerMenu != this.mc.player.inventoryMenu) {
                this.resetSwapState();
            } else {
                ItemStack chestItem = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
                if (!this.protectionActive) {
                    if (chestItem.getItem().equals(Items.ELYTRA)) {
                        if (!this.onlyWhileFlying.get() || this.mc.player.isFallFlying()) {
                            if (this.needsSwap) {
                                this.processSwapStages();
                            } else {
                                int currentDurability = chestItem.getMaxDamage() - chestItem.getDamageValue();
                                if (currentDurability <= this.durabilityThreshold.get()) {
                                    this.initiateSwap();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void initiateSwap() {
        int bestSlot = -1;
        int bestDurability = this.durabilityThreshold.get();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem().equals(Items.ELYTRA)) {
                int durability = stack.getMaxDamage() - stack.getDamageValue();
                if (durability > bestDurability) {
                    bestDurability = durability;
                    bestSlot = i;
                }
            }
        }

        if (bestSlot != -1) {
            this.targetSlot = bestSlot;
            this.needsSwap = true;
            this.swapStage = 1;
            this.stageTimer = 0;
        }
    }

    private void processSwapStages() {
        this.stageTimer++;
        if (this.stageTimer >= this.stageDelay.get()) {
            switch (this.swapStage) {
                case 1:
                    this.newElytraOriginalSlot = this.targetSlot;
                    if (this.targetSlot >= 9) {
                        int hotbarSlot = -1;
                        if (this.hotbarSlotUsed != -1 && this.hotbarSlotUsed < 9) {
                            hotbarSlot = this.hotbarSlotUsed;
                        } else {
                            for (int i = 0; i < 9; i++) {
                                ItemStack stack = this.mc.player.getInventory().getItem(i);
                                if (stack.isEmpty() || !this.isEssentialItem(stack)) {
                                    hotbarSlot = i;
                                    break;
                                }
                            }

                            if (hotbarSlot == -1) {
                                hotbarSlot = 0;
                            }
                        }

                        this.hotbarOriginalItem = this.mc.player.getInventory().getItem(hotbarSlot).copy();
                        this.hotbarSlotUsed = hotbarSlot;
                        InvUtils.move().from(this.targetSlot).toHotbar(hotbarSlot);
                        this.targetSlot = hotbarSlot;
                        this.swapStage = 2;
                        this.stageTimer = 0;
                    } else {
                        this.hotbarSlotUsed = this.targetSlot;
                        this.hotbarOriginalItem = ItemStack.EMPTY;
                        this.swapStage = 2;
                        this.stageTimer = 0;
                    }
                    break;
                case 2:
                    ItemStack toEquip = this.mc.player.getInventory().getItem(this.targetSlot);
                    if (!toEquip.getItem().equals(Items.ELYTRA)) {
                        this.resetSwapState();
                        return;
                    }

                    if (toEquip.getItem().equals(Items.LEATHER_LEGGINGS)
                        || toEquip.getItem().equals(Items.CHAINMAIL_LEGGINGS)
                        || toEquip.getItem().equals(Items.IRON_LEGGINGS)
                        || toEquip.getItem().equals(Items.GOLDEN_LEGGINGS)
                        || toEquip.getItem().equals(Items.DIAMOND_LEGGINGS)
                        || toEquip.getItem().equals(Items.NETHERITE_LEGGINGS)) {
                        this.resetSwapState();
                        return;
                    }

                    InvUtils.swap(this.targetSlot, false);
                    this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
                    this.mc.player.swing(InteractionHand.MAIN_HAND);
                    InvUtils.swapBack();
                    this.swapStage = 3;
                    this.stageTimer = 0;
                    break;
                case 3:
                    if (this.newElytraOriginalSlot >= 9) {
                        InvUtils.move().fromHotbar(this.targetSlot).to(this.newElytraOriginalSlot);
                        if (!this.hotbarOriginalItem.isEmpty()) {
                            this.swapStage = 4;
                            this.stageTimer = 0;
                            return;
                        }
                    }

                    if (this.notifySwap.get()) {
                        ItemStack newChest = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
                        if (newChest.getItem().equals(Items.ELYTRA)) {
                            int newDurability = newChest.getMaxDamage() - newChest.getDamageValue();
                            this.info("Swapped to elytra with " + newDurability + " durability");
                        }
                    }

                    this.needsSwap = false;
                    this.swapStage = 0;
                    this.stageTimer = 0;
                    this.targetSlot = -1;
                    this.cooldownTimer = this.swapCooldown.get();
                    break;
                case 4:
                    if (this.stageTimer < 3) {
                        this.stageTimer++;
                        return;
                    }

                    for (int i = 9; i < 36; i++) {
                        ItemStack stack = this.mc.player.getInventory().getItem(i);
                        if (ItemStack.isSameItem(stack, this.hotbarOriginalItem)) {
                            InvUtils.move().from(i).toHotbar(this.hotbarSlotUsed);
                            break;
                        }
                    }

                    this.needsSwap = false;
                    this.swapStage = 0;
                    this.stageTimer = 0;
                    this.targetSlot = -1;
                    this.cooldownTimer = this.swapCooldown.get();
            }
        }
    }

    private boolean isEssentialItem(ItemStack stack) {
        return stack.getItem().equals(Items.TOTEM_OF_UNDYING)
            || stack.getItem().equals(Items.GOLDEN_APPLE)
            || stack.getItem().equals(Items.ENCHANTED_GOLDEN_APPLE)
            || stack.getItem().equals(Items.ENDER_PEARL)
            || stack.getItem().equals(Items.CHORUS_FRUIT);
    }

    private void handleCombatProtection() {
        if (this.mc.player != null) {
            if (this.mc.player.hurtTime > 0 && this.mc.player.hurtTime > this.lastHurtTime) {
                this.lastHurtTime = this.mc.player.hurtTime;
                ItemStack chestItem = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
                if (chestItem.getItem().equals(Items.ELYTRA) && !this.protectionActive) {
                    int bestChestplate = this.findBestChestplate();
                    if (bestChestplate != -1) {
                        this.storedElytra = chestItem.copy();
                        this.chestplateSlot = bestChestplate;
                        this.needsChestplateSwap = true;
                        this.chestplateSwapStage = 1;
                        this.stageTimer = 0;
                        this.protectionActive = true;
                        this.protectionTimer = this.hitProtectionDuration.get();
                        if (this.notifySwap.get()) {
                            this.info("Swapping to chestplate for protection!");
                        }
                    }
                } else if (this.protectionActive) {
                    this.protectionTimer = this.hitProtectionDuration.get();
                }
            }

            if (this.mc.player.hurtTime < this.lastHurtTime) {
                this.lastHurtTime = this.mc.player.hurtTime;
            }

            if (this.needsChestplateSwap) {
                this.processChestplateSwap();
            } else {
                if (this.protectionActive && !this.needsChestplateSwap) {
                    this.protectionTimer--;
                    if (this.protectionTimer <= 0 && this.autoSwapBack.get()) {
                        ItemStack chestItem = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
                        if (!chestItem.getItem().equals(Items.ELYTRA) && !this.storedElytra.isEmpty()) {
                            int elytraSlot = this.findStoredElytra();
                            if (elytraSlot != -1) {
                                this.chestplateSlot = elytraSlot;
                                this.needsChestplateSwap = true;
                                this.chestplateSwapStage = 1;
                                this.stageTimer = 0;
                                if (this.notifySwap.get()) {
                                    this.info("Protection period ended, swapping back to elytra.");
                                }
                            } else {
                                this.protectionActive = false;
                                this.storedElytra = ItemStack.EMPTY;
                            }
                        } else {
                            this.protectionActive = false;
                            this.storedElytra = ItemStack.EMPTY;
                        }
                    }
                }
            }
        }
    }

    private void processChestplateSwap() {
        this.stageTimer++;
        if (this.stageTimer >= this.stageDelay.get()) {
            switch (this.chestplateSwapStage) {
                case 1:
                    if (this.chestplateSlot >= 9) {
                        int hotbarSlot = 0;

                        for (int i = 0; i < 9; i++) {
                            ItemStack stack = this.mc.player.getInventory().getItem(i);
                            if (stack.isEmpty() || !this.isEssentialItem(stack)) {
                                hotbarSlot = i;
                                break;
                            }
                        }

                        InvUtils.move().from(this.chestplateSlot).toHotbar(hotbarSlot);
                        this.chestplateSlot = hotbarSlot;
                    }

                    this.chestplateSwapStage = 2;
                    this.stageTimer = 0;
                    break;
                case 2:
                    ItemStack toEquip = this.mc.player.getInventory().getItem(this.chestplateSlot);
                    if (!this.isChestplateItem(toEquip)) {
                        this.needsChestplateSwap = false;
                        this.chestplateSwapStage = 0;
                        return;
                    }

                    if (toEquip.getItem().equals(Items.LEATHER_LEGGINGS)
                        || toEquip.getItem().equals(Items.CHAINMAIL_LEGGINGS)
                        || toEquip.getItem().equals(Items.IRON_LEGGINGS)
                        || toEquip.getItem().equals(Items.GOLDEN_LEGGINGS)
                        || toEquip.getItem().equals(Items.DIAMOND_LEGGINGS)
                        || toEquip.getItem().equals(Items.NETHERITE_LEGGINGS)) {
                        this.needsChestplateSwap = false;
                        this.chestplateSwapStage = 0;
                        return;
                    }

                    InvUtils.swap(this.chestplateSlot, false);
                    this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
                    this.mc.player.swing(InteractionHand.MAIN_HAND);
                    InvUtils.swapBack();
                    this.chestplateSwapStage = 3;
                    this.stageTimer = 0;
                    break;
                case 3:
                    this.needsChestplateSwap = false;
                    this.chestplateSwapStage = 0;
                    this.stageTimer = 0;
                    this.chestplateSlot = -1;
                    ItemStack chestItem = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
                    if (chestItem.getItem().equals(Items.ELYTRA)) {
                        this.protectionActive = false;
                        this.storedElytra = ItemStack.EMPTY;
                    }
            }
        }
    }

    private int findBestChestplate() {
        int bestSlot = -1;
        int bestValue = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            int value = this.getChestplateValue(stack);
            if (value > bestValue) {
                bestValue = value;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private int getChestplateValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        } else if (stack.getItem().equals(Items.NETHERITE_CHESTPLATE)) {
            return this.prioritizeNetherite.get() ? 1000 + (stack.getMaxDamage() - stack.getDamageValue()) : 400 + (stack.getMaxDamage() - stack.getDamageValue());
        } else if (stack.getItem().equals(Items.DIAMOND_CHESTPLATE)) {
            return 300 + (stack.getMaxDamage() - stack.getDamageValue());
        } else if (stack.getItem().equals(Items.IRON_CHESTPLATE)) {
            return 200 + (stack.getMaxDamage() - stack.getDamageValue());
        } else if (stack.getItem().equals(Items.GOLDEN_CHESTPLATE)) {
            return 100 + (stack.getMaxDamage() - stack.getDamageValue());
        } else if (stack.getItem().equals(Items.CHAINMAIL_CHESTPLATE)) {
            return 150 + (stack.getMaxDamage() - stack.getDamageValue());
        } else {
            return stack.getItem().equals(Items.LEATHER_CHESTPLATE) ? 50 + (stack.getMaxDamage() - stack.getDamageValue()) : 0;
        }
    }

    private boolean isChestplateItem(ItemStack stack) {
        return stack.isEmpty()
            ? false
            : stack.getItem().equals(Items.ELYTRA)
                || stack.getItem().equals(Items.NETHERITE_CHESTPLATE)
                || stack.getItem().equals(Items.DIAMOND_CHESTPLATE)
                || stack.getItem().equals(Items.IRON_CHESTPLATE)
                || stack.getItem().equals(Items.GOLDEN_CHESTPLATE)
                || stack.getItem().equals(Items.CHAINMAIL_CHESTPLATE)
                || stack.getItem().equals(Items.LEATHER_CHESTPLATE);
    }

    private int findStoredElytra() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem().equals(Items.ELYTRA) && Math.abs(stack.getDamageValue() - this.storedElytra.getDamageValue()) <= 5) {
                return i;
            }
        }

        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem().equals(Items.ELYTRA)) {
                return i;
            }
        }

        return -1;
    }
}
