package bep.hax.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.IMineProcess;
import baritone.api.schematic.FillSchematic;
import baritone.api.schematic.ISchematic;
import bep.hax.Bep;
import bep.hax.accessor.InputAccessor;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.InventoryManager;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.world.Nuker;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class Miner extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRepair = this.settings.createGroup("Repair");
    private final SettingGroup sgSlots = this.settings.createGroup("Slots");
    private final SettingGroup sgThrowaway = this.settings.createGroup("Throwaway");
    private final SettingGroup sgToolSwap = this.settings.createGroup("Tool Swap");
    private final Setting<Miner.Mode> mode = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("tool-mode"))
                        .description("MENDING: repair the tool by mining XP ores. TOOL_SWAP: swap to fresh tools pulled from the inventory toolbox."))
                    .defaultValue(Miner.Mode.MENDING))
                .build()
        );
    private final Setting<Miner.MainTool> mainTool = this.sgToolSwap
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("main-tool"))
                            .description("Tool type to mine and refill with (e.g. SHOVEL to mine sand). SHEARS for leaves/wool, HOE for anything else."))
                        .defaultValue(Miner.MainTool.PICKAXE))
                    .visible(() -> this.mode.get() == Miner.Mode.TOOL_SWAP))
                .build()
        );
    private final Setting<Integer> toolsPerRefill = this.sgToolSwap
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("tools-per-refill")
                .description("How many tools to pull from the inventory toolbox into the hotbar each refill.")
                .defaultValue(9)
                .range(1, 9)
                .sliderRange(1, 9)
                .visible(() -> this.mode.get() == Miner.Mode.TOOL_SWAP)
                .build()
        );
    private final Setting<Double> replaceThreshold = this.sgToolSwap
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("replace-threshold")
                .description("Durability % at or below which a tool is considered spent and swapped out.")
                .defaultValue(5.0)
                .range(0.0, 99.0)
                .sliderRange(0.0, 99.0)
                .visible(() -> this.mode.get() == Miner.Mode.TOOL_SWAP)
                .build()
        );
    private final Setting<Integer> swapDelay = this.sgToolSwap
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("swap-delay")
                .description("Ticks between tool swap/transfer clicks. Higher is safer on anticheat (2b2t).")
                .defaultValue(3)
                .min(0)
                .sliderRange(0, 10)
                .visible(() -> this.mode.get() == Miner.Mode.TOOL_SWAP)
                .build()
        );
    private final Setting<Boolean> disconnectNoTools = this.sgToolSwap
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("disconnect-no-tools")
                .description("Disconnect (and disable auto-reconnect) when no usable tools remain. Otherwise just turn the module off.")
                .defaultValue(false)
                .visible(() -> this.mode.get() == Miner.Mode.TOOL_SWAP)
                .build()
        );
    private final Setting<List<Block>> targetBlocks = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                .name("target-blocks")
                .description("The blocks to mine.")
                .defaultValue(Blocks.ANCIENT_DEBRIS)
                .build()
        );
    private final Setting<List<Item>> targetItems = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("target-items")
                .description("The items to collect and fill inventory with.")
                .defaultValue(Items.ANCIENT_DEBRIS)
                .build()
        );
    private final Setting<Integer> actionDelay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("action-delay")
                .description("Delay in ticks between major actions like placing/opening/breaking.")
                .defaultValue(20)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> transferDelay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("transfer-delay")
                .description("Delay in ticks between inventory transfer clicks.")
                .defaultValue(1)
                .min(0)
                .sliderRange(0, 5)
                .build()
        );
    private final Setting<Integer> dispersionDelay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("dispersion-delay")
                .description("Delay in ticks between dispersion clicks in inventory.")
                .defaultValue(1)
                .min(0)
                .sliderRange(0, 5)
                .build()
        );
    private final Setting<Integer> minimumPlaceY = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("minimum-place-y")
                .description("Minimum Y level for placing ender chest and shulkers. Will move to Y+2 above this value.")
                .defaultValue(-64)
                .min(-64)
                .max(320)
                .sliderRange(-64, 128)
                .build()
        );
    private final Setting<Boolean> disconnectNoShulkers = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("disconnect-no-shulkers")
                .description("Disconnect and disable auto-reconnect if no empty shulkers remain in the ender chest.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> debug = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug")
                .description("Print status and warning messages to chat. Errors are always shown.")
                .defaultValue(false)
                .build()
        );
    private final Setting<List<Block>> repairBlocks = this.sgRepair
        .add(
            new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                .name("repair-blocks")
                .description("The blocks to mine for repair (ores that drop exp).")
                .defaultValue(Blocks.NETHER_QUARTZ_ORE)
                .visible(() -> this.mode.get() == Miner.Mode.MENDING)
                .build()
        );
    private final Setting<Double> repairThreshold = this.sgRepair
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("repair-threshold")
                .description("The durability percentage at which to start repairing.")
                .defaultValue(20.0)
                .range(1.0, 99.0)
                .sliderRange(1.0, 99.0)
                .visible(() -> this.mode.get() == Miner.Mode.MENDING)
                .build()
        );
    private final Setting<Double> mineThreshold = this.sgRepair
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("mine-threshold")
                .description("The durability percentage at which to resume mining.")
                .defaultValue(70.0)
                .range(1.0, 99.0)
                .sliderRange(1.0, 99.0)
                .visible(() -> this.mode.get() == Miner.Mode.MENDING)
                .build()
        );
    private final Setting<Integer> toolHotbarSlot = this.sgSlots
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("tool-hotbar-slot")
                .description("The hotbar slot for the mining tool (0-8).")
                .defaultValue(0)
                .range(0, 8)
                .sliderRange(0, 8)
                .build()
        );
    private final Setting<Integer> shulkerHotbarSlot = this.sgSlots
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("shulker-hotbar-slot")
                .description("The hotbar slot for the shulker box (0-8).")
                .defaultValue(1)
                .range(0, 8)
                .sliderRange(0, 8)
                .build()
        );
    private final Setting<Integer> enderChestHotbarSlot = this.sgSlots
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("enderchest-hotbar-slot")
                .description(
                    "The hotbar slot reserved for ender chests (0-8). Chests are kept here, out of the loot area, so a whole stack is never miscounted or dropped."
                )
                .defaultValue(2)
                .range(0, 8)
                .sliderRange(0, 8)
                .build()
        );
    private final Setting<Boolean> enableThrowaway = this.sgThrowaway
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("enable-throwaway")
                .description("Enable the throwaway item feature.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Block> throwawayBlock = this.sgThrowaway
        .add(
            new meteordevelopment.meteorclient.settings.BlockSetting.Builder()
                .name("throwaway-block")
                .description("The block to use as throwaway item (e.g., cobblestone).")
                .defaultValue(Blocks.COBBLESTONE)
                .build()
        );
    private final Setting<List<Block>> throwawayMineBlocks = this.sgThrowaway
        .add(
            new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                .name("throwaway-mine-blocks")
                .description(
                    "Blocks to mine when refilling throwaway items. Empty = mine the throwaway block itself. E.g. mine deepslate to collect cobbled deepslate."
                )
                .build()
        );
    private final Setting<Integer> throwawayHotbarSlot = this.sgThrowaway
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("throwaway-hotbar-slot")
                .description("The hotbar slot for the throwaway item (0-8).")
                .defaultValue(8)
                .range(0, 8)
                .sliderRange(0, 8)
                .build()
        );
    private final Setting<Integer> throwawayStackMinimum = this.sgThrowaway
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("throwaway-stack-minimum")
                .description("Minimum stack size that triggers refill for throwaway items.")
                .defaultValue(16)
                .range(1, 64)
                .sliderRange(1, 64)
                .build()
        );
    private final Setting<Integer> throwawayStackGoal = this.sgThrowaway
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("throwaway-stack-goal")
                .description("Target stack size to reach when refilling throwaway items.")
                .defaultValue(64)
                .range(1, 320)
                .sliderRange(1, 320)
                .build()
        );
    private Miner.ModuleState state = Miner.ModuleState.MINING;
    private int timer;
    private BlockPos ecPos;
    private BlockPos shulkerPos;
    private BlockPos originalPos;
    private Direction originalFacing;
    private BlockPos clearFrom;
    private BlockPos clearTo;
    private Miner.ModuleState nextStateAfterClear;
    private BlockPos foundationPos;
    private Miner.ModuleState stateAfterFoundation;
    private boolean foundationFlagsForced;
    private boolean foundationSavedPlace;
    private boolean foundationSavedBreak;
    private int shulkerEnderSlot = -1;
    private boolean foundEmptyShulker;
    private int transferStep = 0;
    private int currentTransferSlot = 27;
    private int backTransferStep = 0;
    private boolean fillingPartialShulker = false;
    private int partialFillAttempts = 0;
    private int dispersionTimer = 0;
    private int dispersionCurrentSlot = -1;
    private int dispersionStep = 0;
    private int dispersionDropsLeft = 0;
    private int dropTimer = 0;
    private int dropCurrentSlot = 9;
    private int pickupTimeout = 0;
    private int centeringTimeout = 0;
    private int placementAttempts = 0;
    private boolean savedAllowSprint;
    private boolean savedAllowBreak;
    private boolean savedAllowPlace;
    private double savedRandomLooking;
    private boolean miningThrowaway = false;
    private Miner.ModuleState stateBeforeThrowaway = Miner.ModuleState.MINING;
    private int throwawayCheckTimer = 0;
    private int startupDelay = 0;
    private boolean needsInit = true;
    private boolean wasNukerActive = false;
    private int currentToolSlot = 0;
    private boolean savedAutoTool;
    private int refillMoved = 0;
    private int refillTimer = 0;
    private boolean refillingTools = false;
    private int refillToolStep = 0;
    private int ecToolScanAttempts = 0;
    private int consecutiveToolFlows = 0;
    private int clearAreaTicks = 0;
    private int minYTicks = 0;
    private int foundationTicks = 0;
    private int pathWaitTicks = 0;
    private int repairSampleTimer = 0;
    private int repairLastDamage = -1;
    private int repairNoProgress = 0;
    private boolean savedMineScan;

    public Miner() {
        super(Bep.CATEGORY, "miner", "Uses Baritone to mine selected blocks and stores in shulkers via ender chest.");
    }

    private void dbg(String message, Object... args) {
        if (this.debug.get()) {
            this.info(message, args);
        }
    }

    private void dwarn(String message, Object... args) {
        if (this.debug.get()) {
            this.warning(message, args);
        }
    }

    @Override
    public void onActivate() {
        this.resetState();
        this.needsInit = true;
        this.startupDelay = 60;
        this.saveBaritoneSettings();
    }

    @Override
    public void onDeactivate() {
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            this.restoreBaritoneSettings();
        } catch (Exception var3) {
        }

        if (this.mc.player != null) {
            ((InputAccessor)this.mc.player.input).setMovementForward(0.0F);
            ((InputAccessor)this.mc.player.input).setMovementSideways(0.0F);
            this.mc.options.keyUp.setDown(false);
            this.mc.options.keyDown.setDown(false);
            this.mc.options.keyLeft.setDown(false);
            this.mc.options.keyRight.setDown(false);
            this.mc.options.keySprint.setDown(false);
            this.mc.options.keyShift.setDown(false);
            if (this.mc.screen instanceof AbstractContainerScreen) {
                try {
                    this.mc.player.closeContainer();
                } catch (Exception var2) {
                }
            }
        }

        this.resetState();
    }

    @EventHandler
    private void onTick(Post event) {
        if (Utils.canUpdate() && this.mc.level != null && this.mc.player != null) {
            if (this.startupDelay > 0) {
                this.startupDelay--;
                if (this.startupDelay == 0) {
                    this.dbg("Starting miner...");
                }
            } else {
                if (this.state != Miner.ModuleState.MINING
                    && this.state != Miner.ModuleState.DELAY_RESUME
                    && this.state != Miner.ModuleState.DISPERSION
                    && this.state != Miner.ModuleState.REPAIR_PICKAXE
                    && this.state != Miner.ModuleState.CHECK_THROWAWAY
                    && this.state != Miner.ModuleState.MINING_THROWAWAY
                    && this.state != Miner.ModuleState.REFILL_TOOLS) {
                    boolean needsReset = false;
                    if ((
                            this.state == Miner.ModuleState.OPEN_EC
                                || this.state == Miner.ModuleState.OPEN_EC_BACK
                                || this.state == Miner.ModuleState.PLACE_EC
                                || this.state == Miner.ModuleState.PREP_BREAK_EC
                        )
                        && this.ecPos == null) {
                        this.dwarn("EC position is null in state " + this.state + ", resetting");
                        needsReset = true;
                    }

                    if ((
                            this.state == Miner.ModuleState.OPEN_SHULKER
                                || this.state == Miner.ModuleState.PLACE_SHULKER
                                || this.state == Miner.ModuleState.PREP_BREAK_SHULKER
                        )
                        && this.shulkerPos == null) {
                        this.dwarn("Shulker position is null in state " + this.state + ", resetting");
                        needsReset = true;
                    }

                    if (needsReset) {
                        this.resetState();
                        return;
                    }
                }

                if (this.needsInit) {
                    this.initializeMining();
                    this.needsInit = false;
                } else {
                    if (this.enableThrowaway.get()
                        && !this.miningThrowaway
                        && this.mc.screen == null
                        && (this.state == Miner.ModuleState.MINING || this.state == Miner.ModuleState.REPAIR_PICKAXE)) {
                        this.throwawayCheckTimer++;
                        if (this.throwawayCheckTimer >= 40) {
                            this.throwawayCheckTimer = 0;
                            if (this.needsThrowawayRefill()) {
                                this.stateBeforeThrowaway = this.state;
                                this.state = Miner.ModuleState.CHECK_THROWAWAY;
                            }
                        }
                    }

                    int syncId = this.mc.player.containerMenu.containerId;
                    KillAura killAura = Modules.get().get(KillAura.class);
                    boolean combatActive = killAura != null && killAura.isActive() && (killAura.getTarget() != null || killAura.attacking || killAura.swapped);
                    if (this.mc.level.getGameTime() % 100L == 0L) {
                        this.dbg("Current state: " + this.state.name());
                    }

                    if ((this.state == Miner.ModuleState.MINING || this.state == Miner.ModuleState.REPAIR_PICKAXE) && this.mc.screen == null) {
                        this.consolidateEnderChests();
                        if (this.dropTimer > 0) {
                            this.dropTimer--;
                            return;
                        }

                        if (this.dropCurrentSlot >= 36) {
                            this.dropCurrentSlot = 9;
                        }

                        ItemStack stack = this.mc.player.getInventory().getItem(this.dropCurrentSlot);
                        boolean isThrowawayItem = this.enableThrowaway.get() && stack.getItem() == this.throwawayBlock.get().asItem();
                        if (isThrowawayItem && this.hotbarThrowawayCount() >= this.throwawayStackGoal.get()) {
                            isThrowawayItem = false;
                        }

                        if (!stack.isEmpty()
                            && !this.isTargetItem(stack)
                            && !isThrowawayItem
                            && (this.mode.get() != Miner.Mode.TOOL_SWAP || !this.isMainToolType(stack))) {
                            InvUtils.drop().slot(this.dropCurrentSlot);
                            this.dropTimer = this.transferDelay.get();
                        }

                        this.dropCurrentSlot++;
                    }

                    int reservedToolboxSlots = 0;
                    int targetFreeSpace = 0;
                    boolean anyEmptySlot = false;
                    boolean anyJunkSlot = false;
                    boolean toolSwapMode = this.mode.get() == Miner.Mode.TOOL_SWAP;
                    boolean keepThrowawayReserve = this.enableThrowaway.get() && this.hotbarThrowawayCount() < this.throwawayStackGoal.get();

                    for (int i = 9; i < 36; i++) {
                        ItemStack stack = this.mc.player.getInventory().getItem(i);
                        if (toolSwapMode && this.isMainToolType(stack)) {
                            reservedToolboxSlots++;
                        } else if (stack.isEmpty()) {
                            anyEmptySlot = true;
                        } else if (this.isTargetItem(stack)) {
                            targetFreeSpace += stack.getMaxStackSize() - stack.getCount();
                        } else if (keepThrowawayReserve && stack.getItem() == this.throwawayBlock.get().asItem()) {
                            reservedToolboxSlots++;
                        } else {
                            anyJunkSlot = true;
                        }
                    }

                    int managedSlots = 27 - reservedToolboxSlots;
                    boolean isInvFull = managedSlots > 0 && !anyEmptySlot && !anyJunkSlot && targetFreeSpace == 0;
                    if (this.state == Miner.ModuleState.MINING) {
                        if (!combatActive) {
                            if (!isInvFull
                                && (anyEmptySlot || targetFreeSpace > 0)
                                && !InventoryManager.getInstance().isEating()
                                && this.consolidateHotbarOverflow(syncId)) {
                                this.dropTimer = this.transferDelay.get();
                            } else {
                                if (this.mode.get() == Miner.Mode.MENDING) {
                                    ItemStack pick = this.mc.player.getMainHandItem();
                                    if (!pick.isEmpty() && pick.isDamageableItem()) {
                                        double toolPercentage = (double)(pick.getMaxDamage() - pick.getDamageValue()) / pick.getMaxDamage() * 100.0;
                                        if (toolPercentage < this.repairThreshold.get()) {
                                            this.dbg("Tool durability low: %.1f%%, starting repair", toolPercentage);
                                            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                                            BaritoneAPI.getSettings().mineScanDroppedItems.value = false;
                                            this.state = Miner.ModuleState.REPAIR_PICKAXE;
                                            this.repairSampleTimer = 100;
                                            this.repairLastDamage = -1;
                                            this.repairNoProgress = 0;
                                            this.mineRepairBlocks();
                                            return;
                                        }
                                    }
                                } else if (!InventoryManager.getInstance().isEating()) {
                                    int cfg = this.toolHotbarSlot.get();
                                    if (!this.isUsableTool(this.mc.player.getInventory().getItem(cfg))) {
                                        int src = this.findUsableHotbarToolSlot();
                                        if (src == -1) {
                                            this.dbg("Hotbar out of usable tools, refilling from toolbox");
                                            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                                            this.refillMoved = 0;
                                            this.refillTimer = 0;
                                            this.state = Miner.ModuleState.REFILL_TOOLS;
                                            return;
                                        }

                                        this.swapIntoConfigured(src);
                                        InvUtils.swap(cfg, false);
                                    }
                                }

                                if (isInvFull && !InventoryManager.getInstance().isEating()) {
                                    this.dbg("Inventory full of target items, starting shulker storage process");
                                    BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                                    Nuker nuker = Modules.get().get(Nuker.class);
                                    this.wasNukerActive = nuker != null && nuker.isActive();
                                    if (this.wasNukerActive) {
                                        nuker.toggle();
                                    }

                                    this.state = Miner.ModuleState.PREP_PLACE_EC;
                                    this.timer = 0;
                                } else {
                                    int countEmpty = 0;

                                    for (int i = 9; i < 36; i++) {
                                        ItemStack s = this.mc.player.getInventory().getItem(i);
                                        if (s.isEmpty()) {
                                            countEmpty++;
                                        }
                                    }

                                    if (countEmpty > 0 && !InventoryManager.getInstance().isEating()) {
                                        boolean hasCompleteStack = false;

                                        for (int j = 9; j < 36; j++) {
                                            ItemStack s = this.mc.player.getInventory().getItem(j);
                                            if (this.isTargetItem(s) && s.getCount() >= s.getMaxStackSize()) {
                                                hasCompleteStack = true;
                                                break;
                                            }
                                        }

                                        if (hasCompleteStack) {
                                            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                                            this.state = Miner.ModuleState.DISPERSION;
                                            this.timer = 0;
                                            this.dispersionStep = 0;
                                            this.dispersionCurrentSlot = -1;
                                            this.dispersionDropsLeft = 0;
                                            return;
                                        }
                                    }

                                    if (this.isBaritoneNotMining()) {
                                        this.mineTargetBlocks();
                                    }
                                }
                            }
                        }
                    } else if (this.state != Miner.ModuleState.REPAIR_PICKAXE) {
                        if (this.state == Miner.ModuleState.REFILL_TOOLS) {
                            this.handleRefillTools();
                        } else if (this.timer > 0) {
                            this.timer--;
                        } else {
                            if (!this.isWaitState(this.state)) {
                                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                            }

                            switch (this.state) {
                                case PREP_PLACE_EC:
                                    this.handlePrepPlaceEC();
                                    break;
                                case CENTER_FOR_EC:
                                    this.handleCenterForEC();
                                    break;
                                case PLACE_EC:
                                    this.handlePlaceEC();
                                    break;
                                case OPEN_EC:
                                    this.handleOpenEC();
                                    break;
                                case TRANSFER_EC:
                                    this.handleTransferEC();
                                    break;
                                case ROTATE_FOR_SHULKER:
                                    this.handleRotateForShulker();
                                    break;
                                case PREP_PLACE_SHULKER:
                                    this.handlePrepPlaceShulker();
                                    break;
                                case CENTER_FOR_SHULKER:
                                    this.handleCenterForShulker();
                                    break;
                                case PLACE_SHULKER:
                                    this.handlePlaceShulker();
                                    break;
                                case OPEN_SHULKER:
                                    this.handleOpenShulker();
                                    break;
                                case TRANSFER_SHULKER:
                                    this.handleTransferShulker();
                                    break;
                                case DELAY_AFTER_FILL:
                                    this.state = Miner.ModuleState.PREP_BREAK_SHULKER;
                                    break;
                                case PREP_BREAK_SHULKER:
                                    this.handlePrepBreakShulker();
                                    break;
                                case DELAY_AFTER_BREAK_SHULKER:
                                    this.state = Miner.ModuleState.PATH_TO_SHULKER_POSITION;
                                    break;
                                case PATH_TO_SHULKER_POSITION:
                                    this.handlePathToShulker();
                                    break;
                                case WAIT_PATH_TO_SHULKER:
                                    this.handleWaitPathToShulker();
                                    break;
                                case WAIT_AT_SHULKER_POSITION:
                                    this.handleWaitAtShulker();
                                    break;
                                case CHECK_SHULKER_PICKUP:
                                    this.handleCheckShulkerPickup();
                                    break;
                                case MOVE_SHULKER_TO_HOTBAR:
                                    this.handleMoveShulkerToHotbar(syncId);
                                    break;
                                case PATH_TO_EC_POSITION:
                                    this.handlePathToEC();
                                    break;
                                case WAIT_PATH_TO_EC:
                                    this.handleWaitPathToEC();
                                    break;
                                case ROTATE_BACK:
                                    this.handleRotateBack();
                                    break;
                                case OPEN_EC_BACK:
                                    this.handleOpenECBack();
                                    break;
                                case TRANSFER_EC_BACK:
                                    this.handleTransferECBack();
                                    break;
                                case FILL_PARTIAL_SHULKER:
                                    this.handleFillPartialShulker();
                                    break;
                                case PREP_BREAK_EC:
                                    this.handlePrepBreakEC();
                                    break;
                                case DELAY_RESUME:
                                    this.handleDelayResume();
                                case REPAIR_PICKAXE:
                                default:
                                    break;
                                case CLEAR_AREA:
                                    this.handleClearArea();
                                    break;
                                case WAIT_CLEAR_AREA:
                                    this.handleWaitClearArea();
                                    break;
                                case DISPERSION:
                                    this.handleDispersion(syncId);
                                    break;
                                case MINING_THROWAWAY:
                                    this.handleMiningThrowaway();
                                    break;
                                case CHECK_THROWAWAY:
                                    this.handleCheckThrowaway();
                                    break;
                                case THROW_SLOT_ITEM:
                                    this.handleThrowSlotItem();
                                    break;
                                case MOVE_TO_MIN_Y:
                                    this.handleMoveToMinY();
                                    break;
                                case WAIT_MIN_Y:
                                    this.handleWaitMinY();
                                    break;
                                case PLACE_FOUNDATION:
                                    this.handlePlaceFoundation();
                                    break;
                                case WAIT_FOUNDATION:
                                    this.handleWaitFoundation();
                            }
                        }
                    } else if (!combatActive) {
                        int cfg = this.toolHotbarSlot.get();
                        if (!InventoryManager.getInstance().isEating() && ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != cfg
                            )
                         {
                            InvUtils.swap(cfg, false);
                        }

                        ItemStack pick = this.mc.player.getInventory().getItem(cfg);
                        if (!pick.isEmpty() && pick.isDamageableItem()) {
                            double toolPercentage = (double)(pick.getMaxDamage() - pick.getDamageValue()) / pick.getMaxDamage() * 100.0;
                            if (toolPercentage > this.mineThreshold.get()) {
                                this.dbg("Tool repaired to %.1f%%, resuming mining", toolPercentage);
                                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                                BaritoneAPI.getSettings().mineScanDroppedItems.value = true;
                                this.repairNoProgress = 0;
                                this.state = Miner.ModuleState.MINING;
                                this.mineTargetBlocks();
                            } else {
                                if (this.repairSampleTimer > 0) {
                                    this.repairSampleTimer--;
                                } else {
                                    this.repairSampleTimer = 100;
                                    int dmg = pick.getDamageValue();
                                    if (this.repairLastDamage >= 0 && dmg >= this.repairLastDamage) {
                                        this.repairNoProgress++;
                                    } else {
                                        this.repairNoProgress = 0;
                                    }

                                    this.repairLastDamage = dmg;
                                    if (this.repairNoProgress >= 6) {
                                        this.error("Repair not progressing (no Mending or no XP ore reachable?), disabling.");
                                        this.toggle();
                                        return;
                                    }
                                }

                                if (this.isBaritoneNotMining()) {
                                    this.mineRepairBlocks();
                                }
                            }
                        } else {
                            this.error("Repair tool missing in slot " + cfg + ", disabling.");
                            this.toggle();
                        }
                    }
                }
            }
        }
    }

    private void initializeMining() {
        try {
            BaritoneAPI.getSettings().autoTool.value = false;
            if (this.mode.get() == Miner.Mode.TOOL_SWAP) {
                int cfg = this.toolHotbarSlot.get();
                if (!this.isUsableTool(this.mc.player.getInventory().getItem(cfg))) {
                    int src = this.findUsableHotbarToolSlot();
                    if (src != -1) {
                        this.swapIntoConfigured(src);
                    }
                }

                this.currentToolSlot = cfg;
                InvUtils.swap(cfg, false);
            } else {
                this.currentToolSlot = this.toolHotbarSlot.get();
                InvUtils.swap(this.toolHotbarSlot.get(), false);
            }

            BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(this.targetBlocks.get().toArray(new Block[0]));
            BaritoneAPI.getSettings().mineScanDroppedItems.value = true;
        } catch (Exception e) {
            this.error("Failed to initialize mining: " + e.getMessage());
        }
    }

    private TagKey<Item> toolTag() {
        return switch ((Miner.MainTool)this.mainTool.get()) {
            case SHOVEL -> ItemTags.SHOVELS;
            case AXE -> ItemTags.AXES;
            case HOE -> ItemTags.HOES;
            default -> ItemTags.PICKAXES;
        };
    }

    private boolean isMainToolType(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        } else {
            return this.mainTool.get() == Miner.MainTool.SHEARS ? s.getItem() == Items.SHEARS : s.is(this.toolTag());
        }
    }

    private int toolTier(ItemStack s) {
        String n = s.getItem().toString().toLowerCase();
        if (n.contains("netherite")) {
            return 5;
        } else if (n.contains("diamond")) {
            return 4;
        } else if (n.contains("iron")) {
            return 3;
        } else if (n.contains("gold")) {
            return 2;
        } else {
            return n.contains("stone") ? 1 : 0;
        }
    }

    private boolean isUsableTool(ItemStack s) {
        if (!s.isEmpty() && this.isMainToolType(s) && s.isDamageableItem()) {
            double pct = (double)(s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage() * 100.0;
            return pct > this.replaceThreshold.get();
        } else {
            return false;
        }
    }

    private boolean isTargetItem(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }

        Item item = s.getItem();
        if (this.targetItems.get().contains(item)) {
            return true;
        }

        for (Block b : this.targetBlocks.get()) {
            if (b.asItem() == item) {
                return true;
            }
        }

        return false;
    }

    private boolean isReservedSlot(int slot) {
        if (slot == this.shulkerHotbarSlot.get()) {
            return true;
        } else {
            return slot == this.enderChestHotbarSlot.get() ? true : this.enableThrowaway.get() && slot == this.throwawayHotbarSlot.get();
        }
    }

    private void consolidateEnderChests() {
        int ecSlot = this.enderChestHotbarSlot.get();
        ItemStack ecStack = this.mc.player.getInventory().getItem(ecSlot);
        if (ecStack.getItem() != Items.ENDER_CHEST || ecStack.getCount() < ecStack.getMaxStackSize()) {
            for (int i = 0; i < 36; i++) {
                if (i != ecSlot && this.mc.player.getInventory().getItem(i).getItem() == Items.ENDER_CHEST) {
                    InvUtils.move().from(i).to(ecSlot);
                    return;
                }
            }
        }
    }

    private boolean consolidateHotbarOverflow(int syncId) {
        int toolSlot = this.toolHotbarSlot.get();
        int hotbarThrowaway = this.enableThrowaway.get() ? this.hotbarThrowawayCount() : 0;

        for (int i = 0; i < 9; i++) {
            if (i != toolSlot && i != this.currentToolSlot && (!this.enableThrowaway.get() || i != this.throwawayHotbarSlot.get())) {
                ItemStack s = this.mc.player.getInventory().getItem(i);
                if (!s.isEmpty()
                    && this.isTargetItem(s)
                    && (
                        !this.enableThrowaway.get()
                            || s.getItem() != this.throwawayBlock.get().asItem()
                            || hotbarThrowaway - s.getCount() >= this.throwawayStackGoal.get()
                    )) {
                    this.mc.gameMode.handleInventoryMouseClick(syncId, this.getHandlerSlot(i), 0, ClickType.QUICK_MOVE, this.mc.player);
                    return true;
                }
            }
        }

        return false;
    }

    private int findBestUsableSlot(int from, int to, boolean skipReserved) {
        int best = -1;
        int bestTier = -1;
        int bestDura = -1;

        for (int i = from; i <= to; i++) {
            if (!skipReserved || !this.isReservedSlot(i)) {
                ItemStack s = this.mc.player.getInventory().getItem(i);
                if (this.isUsableTool(s)) {
                    int tier = this.toolTier(s);
                    int dura = s.getMaxDamage() - s.getDamageValue();
                    if (tier > bestTier || tier == bestTier && dura > bestDura) {
                        best = i;
                        bestTier = tier;
                        bestDura = dura;
                    }
                }
            }
        }

        return best;
    }

    private int findUsableHotbarToolSlot() {
        return this.findBestUsableSlot(0, 8, true);
    }

    private int findUsableToolboxSlot() {
        return this.findBestUsableSlot(9, 35, false);
    }

    private int findFreeToolHotbarSlot() {
        for (int i = 0; i <= 8; i++) {
            if (!this.isReservedSlot(i)) {
                ItemStack s = this.mc.player.getInventory().getItem(i);
                if (s.isEmpty()) {
                    return i;
                }

                if (this.isMainToolType(s) && !this.isUsableTool(s)) {
                    return i;
                }
            }
        }

        return -1;
    }

    private void swapIntoConfigured(int src) {
        int cfg = this.toolHotbarSlot.get();
        if (src != cfg) {
            this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, this.getHandlerSlot(src), cfg, ClickType.SWAP, this.mc.player);
        }

        this.currentToolSlot = cfg;
    }

    private int shulkerMenuSlot(int invSlot) {
        return invSlot < 9 ? 54 + invSlot : invSlot + 18;
    }

    private int findBrokenMainToolPlayerSlot() {
        for (int i = 0; i < 36; i++) {
            if (!this.isReservedSlot(i)) {
                ItemStack s = this.mc.player.getInventory().getItem(i);
                if (this.isMainToolType(s) && !this.isUsableTool(s)) {
                    return i;
                }
            }
        }

        return -1;
    }

    private int findFreeNonReservedPlayerSlot() {
        for (int i = 9; i < 36; i++) {
            if (this.mc.player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }

        for (int i = 0; i <= 8; i++) {
            if (!this.isReservedSlot(i) && this.mc.player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private boolean shulkerHasUsableTool(ItemStack shulkerStack) {
        ItemContainerContents container = shulkerStack.get(DataComponents.CONTAINER);
        return container == null ? false : container.nonEmptyStream().anyMatch(this::isUsableTool);
    }

    private void outOfTools() {
        if (this.disconnectNoTools.get()) {
            this.error("Miner: out of usable tools—disconnecting.");
            this.toggle();
            bep.hax.util.Utils.disableAutoReconnect();
            if (this.mc.getConnection() != null) {
                this.mc.getConnection().getConnection().disconnect(Component.literal("[BepHax Miner] Out of usable tools"));
            }
        } else {
            this.dwarn("Miner: out of usable tools—disabling.");
            this.toggle();
        }
    }

    private int selectToolForBreaking() {
        if (this.mode.get() != Miner.Mode.TOOL_SWAP) {
            return this.toolHotbarSlot.get();
        }

        int cfg = this.toolHotbarSlot.get();
        if (!this.isUsableTool(this.mc.player.getInventory().getItem(cfg))) {
            int src = this.findUsableHotbarToolSlot();
            if (src == -1) {
                src = this.findUsableToolboxSlot();
            }

            if (src != -1) {
                this.swapIntoConfigured(src);
            }
        }

        this.currentToolSlot = cfg;
        return cfg;
    }

    private void handleRefillTools() {
        if (this.refillTimer > 0) {
            this.refillTimer--;
        } else {
            if (this.refillMoved < this.toolsPerRefill.get()) {
                int invSlot = this.findUsableToolboxSlot();
                int hotbarSlot = this.findFreeToolHotbarSlot();
                if (invSlot != -1 && hotbarSlot != -1) {
                    this.mc
                        .gameMode
                        .handleInventoryMouseClick(
                            this.mc.player.containerMenu.containerId, this.getHandlerSlot(invSlot), hotbarSlot, ClickType.SWAP, this.mc.player
                        );
                    this.refillMoved++;
                    this.refillTimer = this.swapDelay.get();
                    return;
                }
            }

            int slot = this.findUsableHotbarToolSlot();
            if (slot == -1) {
                int invSlot = this.findUsableToolboxSlot();
                if (invSlot != -1) {
                    this.swapIntoConfigured(invSlot);
                    slot = this.findUsableHotbarToolSlot();
                }
            }

            if (slot != -1) {
                this.swapIntoConfigured(slot);
                InvUtils.swap(this.toolHotbarSlot.get(), false);
                BaritoneAPI.getSettings().mineScanDroppedItems.value = true;
                this.consecutiveToolFlows = 0;
                this.state = Miner.ModuleState.MINING;
                this.mineTargetBlocks();
            } else {
                this.consecutiveToolFlows++;
                if (this.consecutiveToolFlows > 3) {
                    this.consecutiveToolFlows = 0;
                    this.outOfTools();
                } else {
                    this.dbg("No usable tools in inventory, retrieving tool shulker from ender chest");
                    BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                    Nuker nuker = Modules.get().get(Nuker.class);
                    this.wasNukerActive = nuker != null && nuker.isActive();
                    if (this.wasNukerActive) {
                        nuker.toggle();
                    }

                    this.refillingTools = true;
                    this.refillToolStep = 0;
                    this.ecToolScanAttempts = 0;
                    this.currentTransferSlot = 0;
                    this.transferStep = 0;
                    this.state = Miner.ModuleState.PREP_PLACE_EC;
                    this.timer = 0;
                }
            }
        }
    }

    private void resetState() {
        try {
            this.restoreFoundationFlags();
        } catch (Exception var2) {
        }

        this.state = Miner.ModuleState.MINING;
        this.timer = 0;
        this.ecPos = null;
        this.shulkerPos = null;
        this.originalPos = null;
        this.originalFacing = null;
        this.clearFrom = null;
        this.clearTo = null;
        this.nextStateAfterClear = null;
        this.foundationPos = null;
        this.stateAfterFoundation = null;
        this.shulkerEnderSlot = -1;
        this.foundEmptyShulker = false;
        this.transferStep = 0;
        this.currentTransferSlot = 27;
        this.backTransferStep = 0;
        this.fillingPartialShulker = false;
        this.partialFillAttempts = 0;
        this.dispersionTimer = 0;
        this.dispersionCurrentSlot = -1;
        this.dispersionStep = 0;
        this.dispersionDropsLeft = 0;
        this.dropTimer = 0;
        this.dropCurrentSlot = 9;
        this.pickupTimeout = 0;
        this.centeringTimeout = 0;
        this.placementAttempts = 0;
        this.miningThrowaway = false;
        this.stateBeforeThrowaway = Miner.ModuleState.MINING;
        this.throwawayCheckTimer = 0;
        this.wasNukerActive = false;
        this.currentToolSlot = this.toolHotbarSlot.get();
        this.refillMoved = 0;
        this.refillTimer = 0;
        this.refillingTools = false;
        this.refillToolStep = 0;
        this.ecToolScanAttempts = 0;
        this.consecutiveToolFlows = 0;
        this.clearAreaTicks = 0;
        this.minYTicks = 0;
        this.foundationTicks = 0;
        this.pathWaitTicks = 0;
        this.repairSampleTimer = 0;
        this.repairLastDamage = -1;
        this.repairNoProgress = 0;
    }

    private void handleDispersion(int syncId) {
        if (this.mc.screen == null) {
            this.mc.setScreen(new InventoryScreen(this.mc.player));
            this.timer = this.actionDelay.get();
        } else if (this.mc.screen instanceof InventoryScreen) {
            if (this.dispersionTimer > 0) {
                this.dispersionTimer--;
            } else {
                if (this.dispersionStep == 0) {
                    int countEmpty = 0;

                    for (int i = 9; i < 36; i++) {
                        ItemStack s = this.mc.player.getInventory().getItem(i);
                        if (s.isEmpty()) {
                            countEmpty++;
                        }
                    }

                    int targetSlot = -1;
                    int maxCount = 0;

                    for (int j = 9; j < 36; j++) {
                        ItemStack s = this.mc.player.getInventory().getItem(j);
                        if (this.isTargetItem(s) && s.getCount() > maxCount) {
                            maxCount = s.getCount();
                            targetSlot = j;
                        }
                    }

                    int toDistribute = Math.min(countEmpty, maxCount - 1);
                    if (targetSlot != -1 && toDistribute > 0) {
                        this.mc.gameMode.handleInventoryMouseClick(syncId, targetSlot, 0, ClickType.PICKUP, this.mc.player);
                        this.dispersionCurrentSlot = targetSlot;
                        this.dispersionDropsLeft = toDistribute;
                        this.dispersionStep = 1;
                        this.dispersionTimer = this.dispersionDelay.get();
                    } else {
                        this.mc.player.closeContainer();
                        this.state = Miner.ModuleState.MINING;
                        this.mineTargetBlocks();
                    }
                } else if (this.dispersionStep == 1) {
                    if (this.dispersionDropsLeft <= 0) {
                        this.dispersionStep = 2;
                        this.dispersionTimer = this.dispersionDelay.get();
                        return;
                    }

                    for (int i = 9; i < 36; i++) {
                        if (i != this.dispersionCurrentSlot && this.mc.player.getInventory().getItem(i).isEmpty()) {
                            this.mc.gameMode.handleInventoryMouseClick(syncId, i, 1, ClickType.PICKUP, this.mc.player);
                            this.dispersionDropsLeft--;
                            this.dispersionTimer = this.dispersionDelay.get();
                            return;
                        }
                    }

                    this.dispersionStep = 2;
                    this.dispersionTimer = this.dispersionDelay.get();
                } else if (this.dispersionStep == 2) {
                    this.mc.gameMode.handleInventoryMouseClick(syncId, this.dispersionCurrentSlot, 0, ClickType.PICKUP, this.mc.player);
                    this.mc.player.closeContainer();
                    this.state = Miner.ModuleState.MINING;
                    this.mineTargetBlocks();
                    this.dispersionStep = 0;
                    this.dispersionCurrentSlot = -1;
                    this.dispersionDropsLeft = 0;
                }
            }
        }
    }

    private void handleDelayResume() {
        this.refillingTools = false;
        this.transferStep = 0;
        this.currentTransferSlot = 27;
        this.backTransferStep = 0;
        InvUtils.swap(this.selectToolForBreaking(), false);
        if (this.wasNukerActive) {
            Nuker nuker = Modules.get().get(Nuker.class);
            if (nuker != null && !nuker.isActive()) {
                nuker.toggle();
            }

            this.wasNukerActive = false;
        }

        this.state = Miner.ModuleState.MINING;
        this.mineTargetBlocks();
    }

    private void handlePathToShulker() {
        BlockPos targetPos = this.shulkerPos;
        ItemEntity targetEntity = null;
        AABB searchBox = new AABB(this.shulkerPos).inflate(16.0);

        for (ItemEntity itemEntity : this.mc.level.getEntitiesOfClass(ItemEntity.class, searchBox, Entity::isAlive)) {
            ItemStack stack = itemEntity.getItem();
            if (stack.getItemHolder().is(ItemTags.SHULKER_BOXES)) {
                ItemContainerContents container = stack.get(DataComponents.CONTAINER);
                if (container != null && !container.nonEmptyStream().findAny().isEmpty()) {
                    targetEntity = itemEntity;
                    break;
                }
            }
        }

        if (targetEntity != null) {
            int ex = (int)Math.floor(targetEntity.getX());
            int ey = (int)Math.floor(targetEntity.getY());
            int ez = (int)Math.floor(targetEntity.getZ());
            targetPos = new BlockPos(ex, ey, ez);
        }

        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(targetPos, 0));
        this.state = Miner.ModuleState.WAIT_PATH_TO_SHULKER;
        this.pathWaitTicks = 0;
    }

    private void handleWaitPathToShulker() {
        if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            this.state = Miner.ModuleState.WAIT_AT_SHULKER_POSITION;
            this.timer = 60;
        } else {
            if (++this.pathWaitTicks > 600) {
                this.dwarn("Path to shulker timed out, proceeding to pickup check");
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                this.state = Miner.ModuleState.WAIT_AT_SHULKER_POSITION;
                this.timer = 60;
            }
        }
    }

    private void handleWaitAtShulker() {
        this.state = Miner.ModuleState.CHECK_SHULKER_PICKUP;
        this.pickupTimeout = 0;
        this.timer = 0;
    }

    private void handleCheckShulkerPickup() {
        if (InvUtils.find(this::isFilledShulker).found()) {
            try {
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            } catch (Exception var3) {
            }

            this.state = Miner.ModuleState.MOVE_SHULKER_TO_HOTBAR;
            this.timer = 0;
        } else {
            ItemEntity item = this.findDroppedShulkerItem();
            if (item != null) {
                this.reserveShulkerLandingSlot();
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (baritone.getPathingBehavior().isPathing()) {
                    this.pickupTimeout = 0;
                } else {
                    baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(item.blockPosition(), 0));
                    this.pickupTimeout++;
                }
            } else {
                this.pickupTimeout++;
            }

            if (this.pickupTimeout > 60) {
                this.error("Timeout waiting for shulker pickup, disabling.");
                this.toggle();
            } else {
                this.timer = 2;
            }
        }
    }

    private boolean isFilledShulker(ItemStack stack) {
        if (!stack.getItemHolder().is(ItemTags.SHULKER_BOXES)) {
            return false;
        }

        ItemContainerContents c = stack.get(DataComponents.CONTAINER);
        return c != null && c.nonEmptyStream().findAny().isPresent();
    }

    private ItemEntity findDroppedShulkerItem() {
        AABB box = new AABB(this.mc.player.blockPosition()).inflate(16.0);
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (ItemEntity e : this.mc.level.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)) {
            if (this.isFilledShulker(e.getItem())) {
                double d = e.distanceToSqr(this.mc.player);
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                }
            }
        }

        return best;
    }

    private void reserveShulkerLandingSlot() {
        int reserve = this.shulkerHotbarSlot.get();
        ItemStack inSlot = this.mc.player.getInventory().getItem(reserve);
        if (!inSlot.isEmpty() && !inSlot.getItemHolder().is(ItemTags.SHULKER_BOXES)) {
            for (int i = 9; i < 36; i++) {
                if (this.mc.player.getInventory().getItem(i).isEmpty()) {
                    InvUtils.move().from(reserve).to(i);
                    return;
                }
            }
        }
    }

    private void handleMoveShulkerToHotbar(int syncId) {
        if (this.mc.screen == null) {
            this.mc.setScreen(new InventoryScreen(this.mc.player));
            this.timer = this.actionDelay.get();
        } else if (this.mc.screen instanceof InventoryScreen) {
            int fullShulSlot = InvUtils.find(
                    stack -> stack.getItemHolder().is(ItemTags.SHULKER_BOXES)
                        && stack.get(DataComponents.CONTAINER) != null
                        && !stack.get(DataComponents.CONTAINER).nonEmptyStream().findAny().isEmpty()
                )
                .slot();
            if (fullShulSlot == -1) {
                this.mc.player.closeContainer();
                this.state = Miner.ModuleState.PATH_TO_EC_POSITION;
                this.timer = this.actionDelay.get();
            } else {
                int targetInvSlot = this.shulkerHotbarSlot.get();
                if (fullShulSlot == targetInvSlot) {
                    this.mc.player.closeContainer();
                    this.state = Miner.ModuleState.PATH_TO_EC_POSITION;
                    this.timer = this.actionDelay.get();
                } else {
                    int handlerFull = this.getHandlerSlot(fullShulSlot);
                    int handlerTarget = this.getHandlerSlot(targetInvSlot);
                    this.mc.gameMode.handleInventoryMouseClick(syncId, handlerFull, 0, ClickType.PICKUP, this.mc.player);
                    this.mc.gameMode.handleInventoryMouseClick(syncId, handlerTarget, 0, ClickType.PICKUP, this.mc.player);
                    if (!this.mc.player.inventoryMenu.getCarried().isEmpty()) {
                        this.mc.gameMode.handleInventoryMouseClick(syncId, handlerFull, 0, ClickType.PICKUP, this.mc.player);
                    }

                    this.mc.player.closeContainer();
                    this.state = Miner.ModuleState.PATH_TO_EC_POSITION;
                    this.timer = this.actionDelay.get();
                }
            }
        }
    }

    private void handlePathToEC() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(this.originalPos, 1));
        this.state = Miner.ModuleState.WAIT_PATH_TO_EC;
        this.pathWaitTicks = 0;
    }

    private void handleWaitPathToEC() {
        if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            this.state = Miner.ModuleState.ROTATE_BACK;
            this.timer = 0;
        } else {
            if (++this.pathWaitTicks > 600) {
                this.dwarn("Path to ender chest timed out, proceeding");
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                this.state = Miner.ModuleState.ROTATE_BACK;
                this.timer = 0;
            }
        }
    }

    private void handleRotateBack() {
        this.mc.player.setYRot(this.mc.player.getYRot() - 90.0F);
        ((InputAccessor)this.mc.player.input).setMovementForward(0.0F);
        ((InputAccessor)this.mc.player.input).setMovementSideways(0.0F);
        this.state = Miner.ModuleState.OPEN_EC_BACK;
        this.timer = this.actionDelay.get();
    }

    private void handlePrepPlaceEC() {
        this.originalFacing = this.mc.player.getDirection();
        this.originalPos = this.mc.player.blockPosition();
        int safeY = this.minimumPlaceY.get() + 2;
        if (this.originalPos.getY() < safeY) {
            this.dbg("Below safe Y level (" + this.originalPos.getY() + "), moving up to Y=" + safeY);
            this.state = Miner.ModuleState.MOVE_TO_MIN_Y;
            this.timer = 0;
        } else {
            this.ecPos = this.originalPos.relative(this.originalFacing, 1);
            int eChestSlot = InvUtils.find(Items.ENDER_CHEST).slot();
            if (eChestSlot == -1) {
                this.error("No ender chest found in inventory, disabling.");
                this.toggle();
            } else if (this.mc.level.getBlockState(this.ecPos).getBlock() == Blocks.ENDER_CHEST) {
                this.dbg("Ender chest already placed, opening it");
                this.state = Miner.ModuleState.OPEN_EC;
                this.timer = this.actionDelay.get();
            } else {
                BlockPos abovePos = this.ecPos.above();
                boolean posReplaceable = this.mc.level.getBlockState(this.ecPos).isAir() || this.mc.level.getBlockState(this.ecPos).canBeReplaced();
                boolean aboveReplaceable = this.mc.level.getBlockState(abovePos).isAir() || this.mc.level.getBlockState(abovePos).canBeReplaced();
                if (posReplaceable && aboveReplaceable) {
                    this.state = Miner.ModuleState.CENTER_FOR_EC;
                    this.timer = 0;
                    this.centeringTimeout = 0;
                    this.placementAttempts = 0;
                } else {
                    this.dbg("Clearing area for ender chest placement");
                    this.nextStateAfterClear = Miner.ModuleState.CENTER_FOR_EC;
                    this.clearFrom = this.ecPos;
                    this.clearTo = abovePos;
                    this.state = Miner.ModuleState.CLEAR_AREA;
                    this.timer = 0;
                }
            }
        }
    }

    private void handlePlaceEC() {
        if (this.ecPos == null) {
            this.dwarn("Ender chest position is null, resetting state");
            this.resetState();
        } else {
            this.placementAttempts++;
            if (this.placementAttempts > 10) {
                this.error("Failed to place enderchest after 10 attempts, resuming mining");
                this.placementAttempts = 0;
                this.state = Miner.ModuleState.DELAY_RESUME;
                this.timer = this.actionDelay.get();
            } else {
                int eChestSlot = this.enderChestHotbarSlot.get();
                if (this.mc.player.getInventory().getItem(eChestSlot).getItem() != Items.ENDER_CHEST) {
                    this.consolidateEnderChests();
                }

                if (this.mc.player.getInventory().getItem(eChestSlot).getItem() != Items.ENDER_CHEST) {
                    this.error("No ender chest available in hotbar slot " + eChestSlot);
                    this.placementAttempts = 0;
                    this.state = Miner.ModuleState.DELAY_RESUME;
                    this.timer = this.actionDelay.get();
                } else {
                    BlockPos groundPos = this.ecPos.below();
                    if (!this.mc.level.getBlockState(groundPos).isRedstoneConductor(this.mc.level, groundPos)) {
                        if (this.enableThrowaway.get()) {
                            this.dbg("No solid ground for ender chest, placing foundation with Baritone");
                            this.foundationPos = groundPos;
                            this.stateAfterFoundation = Miner.ModuleState.PLACE_EC;
                            this.state = Miner.ModuleState.PLACE_FOUNDATION;
                            this.timer = 0;
                        } else {
                            this.error("No solid ground and throwaway blocks disabled, cannot place ender chest");
                            this.state = Miner.ModuleState.DELAY_RESUME;
                            this.timer = this.actionDelay.get();
                        }
                    } else {
                        InvUtils.swap(eChestSlot, false);
                        Vec3 groundCenter = Vec3.atLowerCornerOf(groundPos).add(0.5, 0.5, 0.5);
                        Vec3 eyePos = this.mc.player.getEyePosition();
                        Vec3 lookVec = groundCenter.subtract(eyePos);
                        double deltaX = lookVec.x;
                        double deltaY = lookVec.y;
                        double deltaZ = lookVec.z;
                        double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                        float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
                        float targetPitch = (float)Math.toDegrees(Math.atan2(-deltaY, horizontalDist));
                        Rotations.rotate(
                            targetYaw,
                            targetPitch,
                            50,
                            () -> {
                                BlockHitResult placeHit = new BlockHitResult(
                                    Vec3.atLowerCornerOf(groundPos).add(0.5, 1.0, 0.5), Direction.UP, groundPos, false
                                );
                                this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, placeHit);
                            }
                        );
                        this.state = Miner.ModuleState.OPEN_EC;
                        this.timer = this.actionDelay.get();
                        this.placementAttempts = 0;
                    }
                }
            }
        }
    }

    private void handleOpenEC() {
        if (this.ecPos == null) {
            this.dwarn("Ender chest position is null, resetting state");
            this.resetState();
        } else if (this.mc.level.getBlockState(this.ecPos).getBlock() != Blocks.ENDER_CHEST) {
            if (this.placementAttempts > 5) {
                this.error("Enderchest not found at expected position after placement");
                this.placementAttempts = 0;
                this.state = Miner.ModuleState.DELAY_RESUME;
                this.timer = this.actionDelay.get();
            } else {
                this.state = Miner.ModuleState.PREP_PLACE_EC;
                this.timer = 0;
            }
        } else {
            this.placementAttempts = 0;
            Vec3 ecCenter = Vec3.atCenterOf(this.ecPos);
            Vec3 eyePos = this.mc.player.getEyePosition();
            Vec3 rotationVec = ecCenter.subtract(eyePos);
            double deltaX = rotationVec.x;
            double deltaY = rotationVec.y;
            double deltaZ = rotationVec.z;
            double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
            float targetPitch = (float)Math.toDegrees(Math.atan2(-deltaY, horizontalDist));
            Rotations.rotate(targetYaw, targetPitch, 50, () -> {
                BlockHitResult openHit = new BlockHitResult(ecCenter, this.originalFacing.getOpposite(), this.ecPos, false);
                this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, openHit);
            });
            this.state = Miner.ModuleState.TRANSFER_EC;
            this.timer = this.actionDelay.get();
        }
    }

    private void handleTransferEC() {
        if (this.mc.screen instanceof AbstractContainerScreen<?> ecScreen) {
            if (!(this.mc.player.containerMenu instanceof ChestMenu handler) || handler.getRowCount() != 3) {
                this.mc.player.closeContainer();
                this.state = Miner.ModuleState.OPEN_EC;
                this.timer = 0;
                return;
            }

            int syncIdEc = this.mc.player.containerMenu.containerId;
            if (this.transferStep == 0) {
                this.foundEmptyShulker = false;

                for (int slot = 0; slot <= 26; slot++) {
                    ItemStack stack = this.mc.player.containerMenu.getSlot(slot).getItem();
                    if (stack.getItemHolder().is(ItemTags.SHULKER_BOXES)) {
                        if (this.refillingTools) {
                            if (this.shulkerHasUsableTool(stack)) {
                                this.shulkerEnderSlot = slot;
                                this.foundEmptyShulker = true;
                                this.dbg("Found tool shulker in enderchest slot " + slot);
                                break;
                            }
                        } else {
                            ItemContainerContents container = stack.get(DataComponents.CONTAINER);
                            if (container == null || !container.nonEmptyStream().findAny().isPresent()) {
                                this.shulkerEnderSlot = slot;
                                this.foundEmptyShulker = true;
                                this.dbg("Found empty shulker in enderchest slot " + slot);
                                break;
                            }
                        }
                    }
                }

                if (!this.foundEmptyShulker) {
                    if (this.refillingTools) {
                        this.ecToolScanAttempts++;
                        if (this.ecToolScanAttempts < 4) {
                            this.dbg("Tool shulker not detected yet (attempt " + this.ecToolScanAttempts + "), re-checking ender chest");
                            this.mc.player.closeContainer();
                            this.state = Miner.ModuleState.OPEN_EC;
                            this.transferStep = 0;
                            this.timer = this.actionDelay.get();
                            return;
                        }

                        this.dwarn("No tool shulker with usable tools in ender chest after " + this.ecToolScanAttempts + " checks.");
                        this.mc.player.closeContainer();
                        this.outOfTools();
                        return;
                    }

                    if (this.disconnectNoShulkers.get()) {
                        this.error("No empty shulker in ender chest—disconnecting.");
                        this.mc.player.closeContainer();
                        this.toggle();
                        bep.hax.util.Utils.disableAutoReconnect();
                        if (this.mc.getConnection() != null) {
                            this.mc.getConnection().getConnection().disconnect(Component.literal("[BepHax Miner] No empty shulkers left"));
                        }

                        return;
                    }

                    this.dwarn("No empty shulker in ender chest—resuming mining.");
                    this.mc.player.closeContainer();
                    this.state = Miner.ModuleState.PREP_BREAK_EC;
                    return;
                }

                this.mc.gameMode.handleInventoryMouseClick(syncIdEc, this.shulkerEnderSlot, this.shulkerHotbarSlot.get(), ClickType.SWAP, this.mc.player);
                this.timer = this.transferDelay.get() * 2;
                this.transferStep = 1;
            } else if (this.transferStep == 1) {
                ItemStack hotbarStack = this.mc.player.getInventory().getItem(this.shulkerHotbarSlot.get());
                if (!hotbarStack.getItemHolder().is(ItemTags.SHULKER_BOXES)) {
                    this.dwarn("Shulker swap failed, retrying...");
                    this.transferStep = 0;
                    this.timer = this.actionDelay.get();
                    return;
                }

                this.mc.player.closeContainer();
                this.state = Miner.ModuleState.ROTATE_FOR_SHULKER;
                this.transferStep = 0;
                this.partialFillAttempts = 0;
                this.timer = this.actionDelay.get();
            }
        } else {
            this.state = Miner.ModuleState.OPEN_EC;
            this.timer = 0;
        }
    }

    private void handleRotateForShulker() {
        this.mc.player.setYRot(this.mc.player.getYRot() + 90.0F);
        ((InputAccessor)this.mc.player.input).setMovementForward(0.0F);
        ((InputAccessor)this.mc.player.input).setMovementSideways(0.0F);
        this.state = Miner.ModuleState.PREP_PLACE_SHULKER;
        this.timer = this.actionDelay.get();
    }

    private void handlePrepPlaceShulker() {
        Direction shulkerDir = this.mc.player.getDirection();
        this.shulkerPos = this.originalPos.relative(shulkerDir, 1);
        Block blockAtPos = this.mc.level.getBlockState(this.shulkerPos).getBlock();
        if (blockAtPos instanceof ShulkerBoxBlock) {
            this.dbg("Shulker box already placed, opening it");
            this.state = Miner.ModuleState.OPEN_SHULKER;
            this.timer = this.actionDelay.get();
        } else {
            BlockPos abovePos = this.shulkerPos.above();
            boolean posReplaceable = this.mc.level.getBlockState(this.shulkerPos).isAir()
                || this.mc.level.getBlockState(this.shulkerPos).canBeReplaced();
            boolean aboveReplaceable = this.mc.level.getBlockState(abovePos).isAir() || this.mc.level.getBlockState(abovePos).canBeReplaced();
            if (posReplaceable && aboveReplaceable) {
                this.state = Miner.ModuleState.CENTER_FOR_SHULKER;
                this.timer = 0;
            } else {
                this.dbg("Clearing area for shulker placement");
                this.nextStateAfterClear = Miner.ModuleState.CENTER_FOR_SHULKER;
                this.clearFrom = this.shulkerPos;
                this.clearTo = abovePos;
                this.state = Miner.ModuleState.CLEAR_AREA;
                this.timer = 0;
            }
        }
    }

    private void handlePlaceShulker() {
        if (this.shulkerPos == null) {
            this.dwarn("Shulker position is null, resetting state");
            this.resetState();
        } else {
            Block blockAtPos = this.mc.level.getBlockState(this.shulkerPos).getBlock();
            if (blockAtPos instanceof ShulkerBoxBlock) {
                this.placementAttempts = 0;
                this.state = Miner.ModuleState.OPEN_SHULKER;
                this.timer = this.actionDelay.get();
            } else {
                this.placementAttempts++;
                if (this.placementAttempts > 10) {
                    this.error("Failed to place shulker after 10 attempts, going back to enderchest");
                    this.placementAttempts = 0;
                    this.state = Miner.ModuleState.ROTATE_BACK;
                    this.timer = this.actionDelay.get();
                } else {
                    ItemStack shulkerStack = this.mc.player.getInventory().getItem(this.shulkerHotbarSlot.get());
                    if (!shulkerStack.getItemHolder().is(ItemTags.SHULKER_BOXES)) {
                        this.error("No shulker box found in designated slot");
                        this.placementAttempts = 0;
                        this.state = Miner.ModuleState.ROTATE_BACK;
                        this.timer = this.actionDelay.get();
                    } else {
                        BlockPos groundPos = this.shulkerPos.below();
                        if (!this.mc.level.getBlockState(groundPos).isRedstoneConductor(this.mc.level, groundPos)) {
                            if (this.enableThrowaway.get()) {
                                this.dbg("No solid ground for shulker, placing foundation with Baritone");
                                this.foundationPos = groundPos;
                                this.stateAfterFoundation = Miner.ModuleState.PLACE_SHULKER;
                                this.state = Miner.ModuleState.PLACE_FOUNDATION;
                                this.timer = 0;
                            } else {
                                this.error("No solid ground and throwaway blocks disabled, cannot place shulker");
                                this.state = Miner.ModuleState.ROTATE_BACK;
                                this.timer = this.actionDelay.get();
                            }
                        } else {
                            InvUtils.swap(this.shulkerHotbarSlot.get(), false);
                            Vec3 groundCenter = Vec3.atLowerCornerOf(groundPos).add(0.5, 0.5, 0.5);
                            Vec3 eyePos = this.mc.player.getEyePosition();
                            Vec3 lookVec = groundCenter.subtract(eyePos);
                            double deltaX = lookVec.x;
                            double deltaY = lookVec.y;
                            double deltaZ = lookVec.z;
                            double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                            float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
                            float targetPitch = (float)Math.toDegrees(Math.atan2(-deltaY, horizontalDist));
                            Rotations.rotate(
                                targetYaw,
                                targetPitch,
                                50,
                                () -> {
                                    BlockHitResult placeHit = new BlockHitResult(
                                        Vec3.atLowerCornerOf(groundPos).add(0.5, 1.0, 0.5), Direction.UP, groundPos, false
                                    );
                                    this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, placeHit);
                                }
                            );
                            this.timer = this.actionDelay.get();
                        }
                    }
                }
            }
        }
    }

    private void handleOpenShulker() {
        if (this.shulkerPos == null) {
            this.dwarn("Shulker position is null, resetting state");
            this.resetState();
        } else {
            Block blockAtPos = this.mc.level.getBlockState(this.shulkerPos).getBlock();
            boolean isShulkerBox = blockAtPos instanceof ShulkerBoxBlock;
            if (!isShulkerBox) {
                this.error("Shulker box not found at expected position after placement");
                this.placementAttempts = 0;
                this.state = Miner.ModuleState.ROTATE_BACK;
                this.timer = this.actionDelay.get();
            } else {
                this.placementAttempts = 0;
                Vec3 shulkerCenter = Vec3.atCenterOf(this.shulkerPos);
                Vec3 eyePos = this.mc.player.getEyePosition();
                Vec3 rotationVec = shulkerCenter.subtract(eyePos);
                double deltaX = rotationVec.x;
                double deltaY = rotationVec.y;
                double deltaZ = rotationVec.z;
                double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
                float targetPitch = (float)Math.toDegrees(Math.atan2(-deltaY, horizontalDist));
                Direction shulkerDir = this.mc.player.getDirection();
                Rotations.rotate(targetYaw, targetPitch, 50, () -> {
                    BlockHitResult shulOpenHit = new BlockHitResult(shulkerCenter, shulkerDir.getOpposite(), this.shulkerPos, false);
                    this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, shulOpenHit);
                });
                this.state = Miner.ModuleState.TRANSFER_SHULKER;
                this.timer = this.actionDelay.get();
            }
        }
    }

    private void handleTransferShulker() {
        if (this.mc.screen instanceof AbstractContainerScreen<?> shulScreen) {
            if (!(this.mc.player.containerMenu instanceof ShulkerBoxMenu)) {
                this.mc.player.closeContainer();
                this.state = Miner.ModuleState.OPEN_SHULKER;
                this.timer = 0;
                return;
            }

            int shulSyncId = this.mc.player.containerMenu.containerId;
            if (this.refillingTools) {
                if (this.refillToolStep == 0) {
                    if (this.currentTransferSlot < 27) {
                        int shulkerSlot = this.currentTransferSlot;
                        ItemStack stack = this.mc.player.containerMenu.getSlot(shulkerSlot).getItem();
                        if (this.isUsableTool(stack)) {
                            int brokenInv = this.findBrokenMainToolPlayerSlot();
                            if (brokenInv != -1) {
                                int brokenMenuSlot = this.shulkerMenuSlot(brokenInv);
                                this.mc.gameMode.handleInventoryMouseClick(shulSyncId, shulkerSlot, 0, ClickType.PICKUP, this.mc.player);
                                this.mc.gameMode.handleInventoryMouseClick(shulSyncId, brokenMenuSlot, 0, ClickType.PICKUP, this.mc.player);
                                this.mc.gameMode.handleInventoryMouseClick(shulSyncId, shulkerSlot, 0, ClickType.PICKUP, this.mc.player);
                                this.timer = this.swapDelay.get();
                            } else {
                                int dest = this.findFreeNonReservedPlayerSlot();
                                if (dest != -1) {
                                    int destMenuSlot = this.shulkerMenuSlot(dest);
                                    this.mc.gameMode.handleInventoryMouseClick(shulSyncId, shulkerSlot, 0, ClickType.PICKUP, this.mc.player);
                                    this.mc.gameMode.handleInventoryMouseClick(shulSyncId, destMenuSlot, 0, ClickType.PICKUP, this.mc.player);
                                    this.timer = this.swapDelay.get();
                                } else {
                                    this.timer = 0;
                                }
                            }
                        } else {
                            this.timer = 0;
                        }

                        this.currentTransferSlot++;
                    } else {
                        this.refillToolStep = 1;
                        this.currentTransferSlot = 27;
                        this.timer = 0;
                    }
                } else if (this.currentTransferSlot < 63) {
                    int slot = this.currentTransferSlot;
                    ItemStack stack = this.mc.player.containerMenu.getSlot(slot).getItem();
                    boolean reserved = slot >= 54 && this.isReservedSlot(slot - 54);
                    if (!reserved && this.isMainToolType(stack) && !this.isUsableTool(stack)) {
                        this.mc.gameMode.handleInventoryMouseClick(shulSyncId, slot, 0, ClickType.QUICK_MOVE, this.mc.player);
                        this.timer = this.swapDelay.get();
                    } else {
                        this.timer = 0;
                    }

                    this.currentTransferSlot++;
                } else {
                    this.mc.player.closeContainer();
                    this.state = Miner.ModuleState.DELAY_AFTER_FILL;
                    this.currentTransferSlot = 27;
                    this.refillToolStep = 0;
                    this.timer = this.actionDelay.get();
                }

                return;
            }

            int endSlot = 54;
            if (this.fillingPartialShulker) {
                if (this.currentTransferSlot < endSlot) {
                    ItemStack stack = this.mc.player.containerMenu.getSlot(this.currentTransferSlot).getItem();
                    if (this.isTargetItem(stack)) {
                        this.mc.gameMode.handleInventoryMouseClick(shulSyncId, this.currentTransferSlot, 0, ClickType.QUICK_MOVE, this.mc.player);
                        this.timer = this.transferDelay.get();
                    } else {
                        this.timer = 0;
                    }

                    this.currentTransferSlot++;
                } else {
                    this.mc.player.closeContainer();
                    this.state = Miner.ModuleState.PREP_BREAK_SHULKER;
                    this.currentTransferSlot = 27;
                    this.fillingPartialShulker = false;
                    this.timer = this.actionDelay.get();
                }
            } else if (this.currentTransferSlot < endSlot) {
                ItemStack stack = this.mc.player.containerMenu.getSlot(this.currentTransferSlot).getItem();
                if (this.isTargetItem(stack)) {
                    this.mc.gameMode.handleInventoryMouseClick(shulSyncId, this.currentTransferSlot, 0, ClickType.QUICK_MOVE, this.mc.player);
                    this.timer = this.transferDelay.get();
                } else {
                    this.timer = 0;
                }

                this.currentTransferSlot++;
            } else {
                this.mc.player.closeContainer();
                this.state = Miner.ModuleState.DELAY_AFTER_FILL;
                this.currentTransferSlot = 27;
                this.timer = this.actionDelay.get();
            }
        } else {
            this.state = Miner.ModuleState.OPEN_SHULKER;
            this.timer = 0;
        }
    }

    private void handlePrepBreakShulker() {
        if (this.shulkerPos == null) {
            this.dwarn("Shulker position is null, resetting state");
            this.resetState();
        } else {
            Block blockAtPos = this.mc.level.getBlockState(this.shulkerPos).getBlock();
            if (this.mc.level.getBlockState(this.shulkerPos).isAir()) {
                if (this.fillingPartialShulker) {
                    this.state = Miner.ModuleState.CHECK_SHULKER_PICKUP;
                    this.fillingPartialShulker = false;
                } else {
                    this.state = Miner.ModuleState.DELAY_AFTER_BREAK_SHULKER;
                }

                this.timer = 5;
            } else if (blockAtPos instanceof ShulkerBoxBlock) {
                InvUtils.swap(this.selectToolForBreaking(), false);
                if (this.fillingPartialShulker) {
                    this.nextStateAfterClear = Miner.ModuleState.CHECK_SHULKER_PICKUP;
                    this.fillingPartialShulker = false;
                } else {
                    this.nextStateAfterClear = Miner.ModuleState.DELAY_AFTER_BREAK_SHULKER;
                }

                this.clearFrom = this.shulkerPos;
                this.clearTo = this.shulkerPos;
                this.state = Miner.ModuleState.CLEAR_AREA;
                this.timer = 0;
            } else {
                if (this.fillingPartialShulker) {
                    this.nextStateAfterClear = Miner.ModuleState.CHECK_SHULKER_PICKUP;
                    this.fillingPartialShulker = false;
                } else {
                    this.nextStateAfterClear = Miner.ModuleState.DELAY_AFTER_BREAK_SHULKER;
                }

                this.clearFrom = this.shulkerPos;
                this.clearTo = this.shulkerPos;
                this.state = Miner.ModuleState.CLEAR_AREA;
                this.timer = 0;
            }
        }
    }

    private void handleOpenECBack() {
        if (this.ecPos == null) {
            this.dwarn("Ender chest position is null, resetting state");
            this.resetState();
        } else if (this.mc.level.getBlockState(this.ecPos).getBlock() != Blocks.ENDER_CHEST) {
            this.state = Miner.ModuleState.PREP_PLACE_EC;
            this.timer = 0;
        } else {
            Vec3 ecCenter = Vec3.atCenterOf(this.ecPos);
            Vec3 eyePos = this.mc.player.getEyePosition();
            Vec3 rotationVec = ecCenter.subtract(eyePos);
            double deltaX = rotationVec.x;
            double deltaY = rotationVec.y;
            double deltaZ = rotationVec.z;
            double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
            float targetPitch = (float)Math.toDegrees(Math.atan2(-deltaY, horizontalDist));
            Direction facing = this.originalFacing != null ? this.originalFacing : Direction.NORTH;
            Rotations.rotate(targetYaw, targetPitch, 50, () -> {
                BlockHitResult backOpenHit = new BlockHitResult(ecCenter, facing.getOpposite(), this.ecPos, false);
                this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, backOpenHit);
            });
            this.state = Miner.ModuleState.TRANSFER_EC_BACK;
            this.timer = this.actionDelay.get();
        }
    }

    private void handleTransferECBack() {
        if (this.mc.screen instanceof AbstractContainerScreen<?> ecBackScreen) {
            if (!(this.mc.player.containerMenu instanceof ChestMenu handler) || handler.getRowCount() != 3) {
                this.mc.player.closeContainer();
                this.state = Miner.ModuleState.OPEN_EC_BACK;
                this.timer = 0;
                return;
            }

            int backSyncId = this.mc.player.containerMenu.containerId;
            if (this.backTransferStep == 0) {
                ItemStack hotbarShulker = this.mc.player.getInventory().getItem(this.shulkerHotbarSlot.get());
                if (hotbarShulker.getItemHolder().is(ItemTags.SHULKER_BOXES)) {
                    if (this.refillingTools) {
                        this.dbg("Putting tool shulker back in enderchest slot " + this.shulkerEnderSlot);
                        this.mc
                            .gameMode
                            .handleInventoryMouseClick(backSyncId, this.shulkerEnderSlot, this.shulkerHotbarSlot.get(), ClickType.SWAP, this.mc.player);
                        this.timer = this.transferDelay.get() * 2;
                        this.backTransferStep = 1;
                        return;
                    }

                    ItemContainerContents container = hotbarShulker.get(DataComponents.CONTAINER);
                    if (container != null && container.nonEmptyStream().findAny().isPresent()) {
                        long occupiedSlots = container.nonEmptyStream().count();
                        if (occupiedSlots >= 27L) {
                            this.dbg("Putting full shulker back in enderchest slot " + this.shulkerEnderSlot);
                            this.mc
                                .gameMode
                                .handleInventoryMouseClick(backSyncId, this.shulkerEnderSlot, this.shulkerHotbarSlot.get(), ClickType.SWAP, this.mc.player);
                            this.timer = this.transferDelay.get() * 2;
                            this.backTransferStep = 1;
                        } else if (this.countInventoryTargetItems() > 0 && this.partialFillAttempts < 3) {
                            this.partialFillAttempts++;
                            this.dbg("Shulker not full (" + occupiedSlots + "/27), filling with target items from inventory");
                            this.mc.player.closeContainer();
                            this.state = Miner.ModuleState.FILL_PARTIAL_SHULKER;
                            this.backTransferStep = 0;
                            this.fillingPartialShulker = true;
                            this.currentTransferSlot = 27;
                            this.timer = this.actionDelay.get();
                        } else {
                            this.dbg("Storing partial shulker (" + occupiedSlots + "/27) back in enderchest slot " + this.shulkerEnderSlot);
                            this.mc
                                .gameMode
                                .handleInventoryMouseClick(backSyncId, this.shulkerEnderSlot, this.shulkerHotbarSlot.get(), ClickType.SWAP, this.mc.player);
                            this.timer = this.transferDelay.get() * 2;
                            this.backTransferStep = 1;
                        }
                    } else {
                        this.dwarn("Shulker is empty, placing it back anyway");
                        this.mc
                            .gameMode
                            .handleInventoryMouseClick(backSyncId, this.shulkerEnderSlot, this.shulkerHotbarSlot.get(), ClickType.SWAP, this.mc.player);
                        this.timer = this.transferDelay.get() * 2;
                        this.backTransferStep = 1;
                    }
                } else {
                    this.dwarn("No shulker found in hotbar slot to put back");
                    this.mc.player.closeContainer();
                    this.state = Miner.ModuleState.PREP_BREAK_EC;
                    this.backTransferStep = 0;
                    this.timer = this.actionDelay.get();
                }
            } else if (this.backTransferStep == 1) {
                this.mc.player.closeContainer();
                this.state = Miner.ModuleState.PREP_BREAK_EC;
                this.backTransferStep = 0;
                this.timer = this.actionDelay.get();
            }
        } else {
            this.state = Miner.ModuleState.OPEN_EC_BACK;
            this.timer = 0;
        }
    }

    private int countInventoryTargetItems() {
        int count = 0;

        for (int i = 9; i < 36; i++) {
            ItemStack s = this.mc.player.getInventory().getItem(i);
            if (this.isTargetItem(s)) {
                count += s.getCount();
            }
        }

        return count;
    }

    private void handleFillPartialShulker() {
        if (this.shulkerPos != null) {
            BlockPos groundPos = this.shulkerPos.below();
            if (this.mc.level.getBlockState(groundPos).isRedstoneConductor(this.mc.level, groundPos)) {
                if (this.mc.level.getBlockState(this.shulkerPos).isAir()) {
                    this.placementAttempts = 0;
                    this.state = Miner.ModuleState.PLACE_SHULKER;
                    this.timer = 0;
                } else {
                    this.nextStateAfterClear = Miner.ModuleState.FILL_PARTIAL_SHULKER;
                    this.clearFrom = this.shulkerPos;
                    this.clearTo = this.shulkerPos;
                    this.state = Miner.ModuleState.CLEAR_AREA;
                    this.timer = 0;
                }

                return;
            }
        }

        BlockPos fillPos = null;

        for (Direction dir : Direction.values()) {
            if (dir != Direction.UP && dir != Direction.DOWN) {
                BlockPos testPos = this.mc.player.blockPosition().offset(dir.getUnitVec3i());
                if (this.mc.level.getBlockState(testPos).isAir()) {
                    BlockPos groundPos = testPos.below();
                    if (this.mc.level.getBlockState(groundPos).isRedstoneConductor(this.mc.level, groundPos)) {
                        fillPos = testPos;
                        break;
                    }

                    if (fillPos == null) {
                        fillPos = testPos;
                    }
                }
            }
        }

        if (fillPos == null) {
            fillPos = this.mc.player.blockPosition().relative(this.mc.player.getDirection());
        }

        ItemStack shulkerStack = this.mc.player.getInventory().getItem(this.shulkerHotbarSlot.get());
        if (!shulkerStack.getItemHolder().is(ItemTags.SHULKER_BOXES)) {
            this.dwarn("No shulker in hotbar slot to fill");
            this.state = Miner.ModuleState.OPEN_EC_BACK;
            this.timer = this.actionDelay.get();
        } else if (!this.mc.level.getBlockState(fillPos).isAir()) {
            this.shulkerPos = fillPos;
            this.nextStateAfterClear = Miner.ModuleState.FILL_PARTIAL_SHULKER;
            this.clearFrom = fillPos;
            this.clearTo = fillPos;
            this.state = Miner.ModuleState.CLEAR_AREA;
            this.timer = 0;
        } else {
            BlockPos groundPos = fillPos.below();
            if (!this.mc.level.getBlockState(groundPos).isRedstoneConductor(this.mc.level, groundPos)) {
                if (this.enableThrowaway.get()) {
                    this.shulkerPos = fillPos;
                    this.dbg("Using Baritone to place foundation for partial shulker");
                    BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().clearArea(groundPos, groundPos);
                    InvUtils.swap(this.throwawayHotbarSlot.get(), false);
                    BlockHitResult placeHit = new BlockHitResult(
                        Vec3.atCenterOf(groundPos.below()).add(0.0, 0.5, 0.0), Direction.UP, groundPos.below(), false
                    );
                    this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, placeHit);
                    this.timer = this.actionDelay.get() * 2;
                } else {
                    this.dwarn("No throwaway blocks enabled, cannot place shulker without foundation");
                    this.state = Miner.ModuleState.OPEN_EC_BACK;
                    this.timer = this.actionDelay.get();
                }
            } else {
                this.shulkerPos = fillPos;
                this.placementAttempts = 0;
                this.state = Miner.ModuleState.PLACE_SHULKER;
                this.timer = 0;
            }
        }
    }

    private void handlePrepBreakEC() {
        if (this.ecPos == null) {
            this.dwarn("Ender chest position is null, resetting state");
            this.resetState();
        } else {
            if (this.mc.level.getBlockState(this.ecPos).isAir()) {
                this.state = Miner.ModuleState.DELAY_RESUME;
                this.timer = this.actionDelay.get();
            } else {
                this.nextStateAfterClear = Miner.ModuleState.DELAY_RESUME;
                this.clearFrom = this.ecPos;
                this.clearTo = this.ecPos;
                this.state = Miner.ModuleState.CLEAR_AREA;
                this.timer = 0;
            }
        }
    }

    private void handleClearArea() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().clearArea(this.clearFrom, this.clearTo);
        this.state = Miner.ModuleState.WAIT_CLEAR_AREA;
        this.clearAreaTicks = 0;
        this.timer = 0;
    }

    private void handleWaitClearArea() {
        this.clearAreaTicks++;
        if (this.clearAreaTicks > 100) {
            this.dwarn("Clear area timeout, proceeding anyway");
            this.state = this.nextStateAfterClear;
            this.timer = this.actionDelay.get();
        } else {
            boolean isPathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
            boolean isBuilding = BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().isActive();
            if (!isPathing && !isBuilding) {
                boolean cleared = true;
                int minX = Math.min(this.clearFrom.getX(), this.clearTo.getX());
                int maxX = Math.max(this.clearFrom.getX(), this.clearTo.getX());
                int minY = Math.min(this.clearFrom.getY(), this.clearTo.getY());
                int maxY = Math.max(this.clearFrom.getY(), this.clearTo.getY());
                int minZ = Math.min(this.clearFrom.getZ(), this.clearTo.getZ());
                int maxZ = Math.max(this.clearFrom.getZ(), this.clearTo.getZ());

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (!this.mc.level.getBlockState(pos).isAir() && !this.mc.level.getBlockState(pos).canBeReplaced()) {
                                cleared = false;
                                break;
                            }
                        }

                        if (!cleared) {
                            break;
                        }
                    }

                    if (!cleared) {
                        break;
                    }
                }

                if (cleared) {
                    this.state = this.nextStateAfterClear;
                    this.timer = this.actionDelay.get();
                } else if (this.clearAreaTicks > 20) {
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                BlockPos pos = new BlockPos(x, y, z);
                                if (!this.mc.level.getBlockState(pos).isAir()) {
                                    this.mc.gameMode.continueDestroyBlock(pos, Direction.UP);
                                    return;
                                }
                            }
                        }
                    }

                    this.state = this.nextStateAfterClear;
                    this.timer = this.actionDelay.get();
                }
            }
        }
    }

    private boolean isWaitState(Miner.ModuleState s) {
        return s == Miner.ModuleState.WAIT_PATH_TO_SHULKER
            || s == Miner.ModuleState.WAIT_PATH_TO_EC
            || s == Miner.ModuleState.WAIT_CLEAR_AREA
            || s == Miner.ModuleState.WAIT_AT_SHULKER_POSITION
            || s == Miner.ModuleState.CHECK_SHULKER_PICKUP
            || s == Miner.ModuleState.MINING_THROWAWAY
            || s == Miner.ModuleState.CENTER_FOR_EC
            || s == Miner.ModuleState.CENTER_FOR_SHULKER
            || s == Miner.ModuleState.WAIT_MIN_Y
            || s == Miner.ModuleState.WAIT_FOUNDATION;
    }

    private int getHandlerSlot(int invSlot) {
        if (invSlot < 9) {
            return 36 + invSlot;
        } else {
            return invSlot < 36 ? invSlot : -1;
        }
    }

    private boolean isBaritoneNotMining() {
        return !(BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager().mostRecentInControl().orElse(null) instanceof IMineProcess);
    }

    private void mineRepairBlocks() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(this.repairBlocks.get().toArray(new Block[0]));
    }

    private void mineTargetBlocks() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(this.targetBlocks.get().toArray(new Block[0]));
    }

    private void saveBaritoneSettings() {
        Settings settings = BaritoneAPI.getSettings();
        this.savedAllowSprint = settings.allowSprint.value;
        this.savedAllowBreak = settings.allowBreak.value;
        this.savedAllowPlace = settings.allowPlace.value;
        this.savedRandomLooking = settings.randomLooking.value;
        this.savedAutoTool = settings.autoTool.value;
        this.savedMineScan = settings.mineScanDroppedItems.value;
    }

    private void restoreBaritoneSettings() {
        Settings settings = BaritoneAPI.getSettings();
        settings.allowSprint.value = this.savedAllowSprint;
        settings.allowBreak.value = this.savedAllowBreak;
        settings.allowPlace.value = this.savedAllowPlace;
        settings.randomLooking.value = this.savedRandomLooking;
        settings.autoTool.value = this.savedAutoTool;
        settings.mineScanDroppedItems.value = this.savedMineScan;
    }

    private void restoreFoundationFlags() {
        if (this.foundationFlagsForced) {
            this.foundationFlagsForced = false;
            Settings settings = BaritoneAPI.getSettings();
            settings.allowPlace.value = this.foundationSavedPlace;
            settings.allowBreak.value = this.foundationSavedBreak;
        }
    }

    private void handleCenterForEC() {
        this.centeringTimeout++;
        if (this.ecPos != null && this.centeringTimeout == 1) {
            BlockPos ecGroundPos = this.ecPos.below();
            if (!this.mc.level.getBlockState(ecGroundPos).isRedstoneConductor(this.mc.level, ecGroundPos) && this.enableThrowaway.get()) {
                this.dbg("No solid ground for EC, placing foundation");
                this.foundationPos = ecGroundPos;
                this.stateAfterFoundation = Miner.ModuleState.CENTER_FOR_EC;
                this.state = Miner.ModuleState.PLACE_FOUNDATION;
                this.centeringTimeout = 0;
                this.timer = 0;
                return;
            }
        }

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        BlockPos pPos = this.mc.player.blockPosition();
        boolean onStandBlock = pPos.getX() == this.originalPos.getX() && pPos.getZ() == this.originalPos.getZ();
        if (!onStandBlock && this.centeringTimeout <= 100) {
            if (!baritone.getPathingBehavior().isPathing()) {
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.originalPos));
            }
        } else {
            baritone.getPathingBehavior().cancelEverything();
            this.centeringTimeout = 0;
            this.state = Miner.ModuleState.PLACE_EC;
            this.timer = this.actionDelay.get();
        }
    }

    private void handleCenterForShulker() {
        this.centeringTimeout++;
        if (this.shulkerPos != null && this.centeringTimeout == 1) {
            BlockPos shulkerGroundPos = this.shulkerPos.below();
            if (!this.mc.level.getBlockState(shulkerGroundPos).isRedstoneConductor(this.mc.level, shulkerGroundPos) && this.enableThrowaway.get()) {
                this.dbg("No solid ground for shulker, placing foundation");
                this.foundationPos = shulkerGroundPos;
                this.stateAfterFoundation = Miner.ModuleState.CENTER_FOR_SHULKER;
                this.state = Miner.ModuleState.PLACE_FOUNDATION;
                this.centeringTimeout = 0;
                this.timer = 0;
                return;
            }
        }

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        BlockPos pPos = this.mc.player.blockPosition();
        boolean onStandBlock = pPos.getX() == this.originalPos.getX() && pPos.getZ() == this.originalPos.getZ();
        if (!onStandBlock && this.centeringTimeout <= 100) {
            if (!baritone.getPathingBehavior().isPathing()) {
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.originalPos));
            }
        } else {
            baritone.getPathingBehavior().cancelEverything();
            this.centeringTimeout = 0;
            this.state = Miner.ModuleState.PLACE_SHULKER;
            this.timer = this.actionDelay.get();
        }
    }

    private int hotbarThrowawayCount() {
        Item throwawayItem = this.throwawayBlock.get().asItem();
        int total = 0;

        for (int i = 0; i <= 8; i++) {
            ItemStack s = this.mc.player.getInventory().getItem(i);
            if (s.getItem() == throwawayItem) {
                total += s.getCount();
            }
        }

        return total;
    }

    private boolean hotbarThrowawayCanGrow() {
        Item throwawayItem = this.throwawayBlock.get().asItem();

        for (int i = 0; i <= 8; i++) {
            ItemStack s = this.mc.player.getInventory().getItem(i);
            if (s.getItem() == throwawayItem && s.getCount() < s.getMaxStackSize()) {
                return true;
            }

            if (s.isEmpty() && !this.isReservedSlot(i) && i != this.toolHotbarSlot.get() && i != this.currentToolSlot) {
                return true;
            }
        }

        return false;
    }

    private boolean needsThrowawayRefill() {
        if (!this.enableThrowaway.get()) {
            return false;
        }

        ItemStack stack = this.mc.player.getInventory().getItem(this.throwawayHotbarSlot.get());
        return !stack.isEmpty() && stack.getItem() != this.throwawayBlock.get().asItem()
            ? true
            : this.hotbarThrowawayCount() < this.throwawayStackMinimum.get();
    }

    private void handleCheckThrowaway() {
        if (this.stateBeforeThrowaway != Miner.ModuleState.CENTER_FOR_EC
            && this.stateBeforeThrowaway != Miner.ModuleState.CENTER_FOR_SHULKER
            && this.stateBeforeThrowaway != Miner.ModuleState.PLACE_EC
            && this.stateBeforeThrowaway != Miner.ModuleState.PLACE_SHULKER) {
            int slot = this.throwawayHotbarSlot.get();
            ItemStack stack = this.mc.player.getInventory().getItem(slot);
            Item throwawayItem = this.throwawayBlock.get().asItem();
            if (!stack.isEmpty() && stack.getItem() != throwawayItem) {
                this.state = Miner.ModuleState.THROW_SLOT_ITEM;
                this.timer = 0;
            } else {
                if (this.hotbarThrowawayCount() < this.throwawayStackGoal.get()) {
                    int inventoryThrowaway = 0;

                    for (int i = 9; i < 36; i++) {
                        ItemStack invStack = this.mc.player.getInventory().getItem(i);
                        if (invStack.getItem() == throwawayItem) {
                            inventoryThrowaway += invStack.getCount();
                        }
                    }

                    if (inventoryThrowaway > 0) {
                        this.moveThrowawayToSlot();
                    }

                    if (this.hotbarThrowawayCount() < this.throwawayStackGoal.get() && this.hotbarThrowawayCanGrow()) {
                        this.startMiningThrowaway();
                    } else {
                        this.state = this.stateBeforeThrowaway;
                        this.timer = this.actionDelay.get();
                    }
                } else {
                    this.state = this.stateBeforeThrowaway;
                    this.timer = this.actionDelay.get();
                }
            }
        } else {
            this.state = this.stateBeforeThrowaway;
            this.timer = 0;
        }
    }

    private void handleThrowSlotItem() {
        int slot = this.throwawayHotbarSlot.get();
        ItemStack stack = this.mc.player.getInventory().getItem(slot);
        if (!stack.isEmpty()) {
            InvUtils.drop().slot(slot);
            this.timer = this.actionDelay.get();
        }

        this.state = Miner.ModuleState.CHECK_THROWAWAY;
    }

    private void handleMiningThrowaway() {
        this.moveThrowawayToSlot();
        int totalHotbar = this.hotbarThrowawayCount();
        if (totalHotbar >= this.throwawayStackGoal.get() || !this.hotbarThrowawayCanGrow()) {
            this.dbg("Throwaway refill done: " + totalHotbar + " items in hotbar");
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            this.miningThrowaway = false;
            this.state = this.stateBeforeThrowaway;
            if (this.stateBeforeThrowaway == Miner.ModuleState.MINING) {
                this.mineTargetBlocks();
            } else if (this.stateBeforeThrowaway == Miner.ModuleState.REPAIR_PICKAXE) {
                this.mineRepairBlocks();
            }

            this.timer = this.actionDelay.get();
        } else if (this.isBaritoneNotMining()) {
            this.startMiningThrowaway();
        }
    }

    private void startMiningThrowaway() {
        this.miningThrowaway = true;
        this.state = Miner.ModuleState.MINING_THROWAWAY;
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        List<Block> mineBlocks = this.throwawayMineBlocks.get();
        if (mineBlocks.isEmpty()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(this.throwawayBlock.get());
            this.dbg("Mining " + this.throwawayBlock.get().getName().getString() + " for throwaway items");
        } else {
            BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(mineBlocks.toArray(new Block[0]));
            this.dbg("Mining " + mineBlocks.size() + " block type(s) for throwaway items");
        }
    }

    private void moveThrowawayToSlot() {
        int targetSlot = this.throwawayHotbarSlot.get();
        Item throwawayItem = this.throwawayBlock.get().asItem();
        ItemStack targetStack = this.mc.player.getInventory().getItem(targetSlot);
        int currentInSlot = 0;
        if (!targetStack.isEmpty() && targetStack.getItem() == throwawayItem) {
            currentInSlot = targetStack.getCount();
        }

        int targetAmount = Math.min(64, this.throwawayStackGoal.get());
        int needed = targetAmount - currentInSlot;
        if (needed > 0) {
            if (targetStack.isEmpty() || targetStack.getItem() != throwawayItem) {
                for (int i = 9; i < 36; i++) {
                    ItemStack stack = this.mc.player.getInventory().getItem(i);
                    if (stack.getItem() == throwawayItem) {
                        InvUtils.move().from(i).to(targetSlot);
                        targetStack = this.mc.player.getInventory().getItem(targetSlot);
                        currentInSlot = targetStack.getCount();
                        needed = targetAmount - currentInSlot;
                        break;
                    }
                }
            }

            if (needed > 0 && !targetStack.isEmpty() && targetStack.getItem() == throwawayItem) {
                for (int i = 9; i < 36 && needed > 0; i++) {
                    ItemStack stack = this.mc.player.getInventory().getItem(i);
                    if (stack.getItem() == throwawayItem && stack.getCount() > 0) {
                        this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, i, 0, ClickType.PICKUP, this.mc.player);
                        this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, targetSlot, 0, ClickType.PICKUP, this.mc.player);
                        if (!this.mc.player.containerMenu.getCarried().isEmpty()) {
                            this.mc.gameMode.handleInventoryMouseClick(this.mc.player.containerMenu.containerId, i, 0, ClickType.PICKUP, this.mc.player);
                        }

                        targetStack = this.mc.player.getInventory().getItem(targetSlot);
                        currentInSlot = targetStack.getCount();
                        needed = targetAmount - currentInSlot;
                    }
                }
            }

            int totalThrowaway = 0;

            for (int i = 0; i < 36; i++) {
                ItemStack invStack = this.mc.player.getInventory().getItem(i);
                if (invStack.getItem() == throwawayItem) {
                    totalThrowaway += invStack.getCount();
                }
            }

            this.dbg(
                "Throwaway items organized. Hotbar: " + this.mc.player.getInventory().getItem(targetSlot).getCount() + ", Total: " + totalThrowaway
            );
        }
    }

    private void handleMoveToMinY() {
        BlockPos currentPos = this.mc.player.blockPosition();
        int targetY = this.minimumPlaceY.get() + 2;
        BlockPos targetPos = new BlockPos(currentPos.getX(), targetY, currentPos.getZ());
        if (currentPos.getY() >= targetY) {
            this.dbg("Reached safe Y level at Y=" + currentPos.getY());
            this.originalPos = currentPos;
            this.state = Miner.ModuleState.PREP_PLACE_EC;
            this.timer = this.actionDelay.get();
        } else {
            this.dbg("Moving to Y=" + targetY + " (currently at Y=" + currentPos.getY() + ")");
            IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
            if (pathing.isPathing()) {
                pathing.cancelEverything();
            }

            GoalBlock goal = new GoalBlock(targetPos);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            this.state = Miner.ModuleState.WAIT_MIN_Y;
            this.minYTicks = 0;
            this.timer = 0;
        }
    }

    private void handleWaitMinY() {
        this.minYTicks++;
        if (this.minYTicks > 200) {
            this.dwarn("Timeout while moving to safe Y level, proceeding anyway");
            this.originalPos = this.mc.player.blockPosition();
            this.state = Miner.ModuleState.PREP_PLACE_EC;
            this.timer = this.actionDelay.get();
        } else {
            BlockPos currentPos = this.mc.player.blockPosition();
            int targetY = this.minimumPlaceY.get() + 2;
            if (currentPos.getY() >= targetY) {
                this.dbg("Reached safe Y level at Y=" + currentPos.getY());
                IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
                if (pathing.isPathing()) {
                    pathing.cancelEverything();
                }

                this.originalPos = currentPos;
                this.state = Miner.ModuleState.PREP_PLACE_EC;
                this.timer = this.actionDelay.get();
            } else {
                IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
                boolean isPathing = pathing.isPathing();
                if (!isPathing && this.minYTicks > 20 && this.minYTicks % 20 == 0) {
                    this.dbg("Retrying movement to Y=" + targetY);
                    BlockPos targetPos = new BlockPos(currentPos.getX(), targetY, currentPos.getZ());
                    GoalBlock goal = new GoalBlock(targetPos);
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                }
            }
        }
    }

    private void handlePlaceFoundation() {
        if (this.foundationPos == null) {
            this.dwarn("Foundation position is null, skipping");
            this.restoreFoundationFlags();
            this.state = this.stateAfterFoundation != null ? this.stateAfterFoundation : Miner.ModuleState.MINING;
            this.timer = this.actionDelay.get();
        } else {
            int slot = this.throwawayHotbarSlot.get();
            ItemStack stack = this.mc.player.getInventory().getItem(slot);
            Item throwawayItem = this.throwawayBlock.get().asItem();
            if (stack.isEmpty() || stack.getItem() != throwawayItem || stack.getCount() < 1) {
                this.moveThrowawayToSlot();
                stack = this.mc.player.getInventory().getItem(slot);
                if (stack.isEmpty() || stack.getItem() != throwawayItem) {
                    this.dwarn("No throwaway blocks available for foundation");
                    this.restoreFoundationFlags();
                    this.state = this.stateAfterFoundation != null ? this.stateAfterFoundation : Miner.ModuleState.MINING;
                    this.timer = this.actionDelay.get();
                    return;
                }
            }

            this.dbg("Using Baritone to place foundation block at " + this.foundationPos.toShortString());
            InvUtils.swap(this.throwawayHotbarSlot.get(), false);
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            Settings settings = BaritoneAPI.getSettings();
            if (!this.foundationFlagsForced) {
                this.foundationSavedPlace = settings.allowPlace.value;
                this.foundationSavedBreak = settings.allowBreak.value;
                this.foundationFlagsForced = true;
            }

            settings.allowPlace.value = true;
            settings.allowBreak.value = false;
            ISchematic schematic = new FillSchematic(1, 1, 1, this.throwawayBlock.get().defaultBlockState());
            baritone.getBuilderProcess().build("foundation", schematic, this.foundationPos);
            this.state = Miner.ModuleState.WAIT_FOUNDATION;
            this.foundationTicks = 0;
            this.timer = 0;
        }
    }

    private void handleWaitFoundation() {
        this.foundationTicks++;
        if (this.foundationTicks > 100) {
            this.dwarn("Foundation placement timeout");
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone.getBuilderProcess().isActive()) {
                baritone.getPathingBehavior().cancelEverything();
            }

            this.restoreFoundationFlags();
            this.state = this.stateAfterFoundation != null ? this.stateAfterFoundation : Miner.ModuleState.MINING;
            this.foundationPos = null;
            this.stateAfterFoundation = null;
            this.timer = this.actionDelay.get();
        } else if (this.foundationPos != null && this.mc.level.getBlockState(this.foundationPos).isRedstoneConductor(this.mc.level, this.foundationPos)) {
            this.dbg("Foundation placed successfully");
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone.getBuilderProcess().isActive()) {
                baritone.getPathingBehavior().cancelEverything();
            }

            this.restoreFoundationFlags();
            this.state = this.stateAfterFoundation != null ? this.stateAfterFoundation : Miner.ModuleState.MINING;
            this.foundationPos = null;
            this.stateAfterFoundation = null;
            this.timer = this.actionDelay.get();
        } else {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            boolean isBuilding = baritone.getBuilderProcess().isActive();
            boolean isPathing = baritone.getPathingBehavior().isPathing();
            if (!isBuilding && !isPathing) {
                if (this.foundationTicks > 20 && this.foundationTicks % 20 == 0) {
                    this.dbg("Retrying foundation placement");
                    this.state = Miner.ModuleState.PLACE_FOUNDATION;
                    this.timer = 0;
                }
            }
        }
    }

    private enum MainTool {
        PICKAXE,
        SHOVEL,
        AXE,
        HOE,
        SHEARS;
    }

    private enum Mode {
        MENDING,
        TOOL_SWAP;
    }

    private enum ModuleState {
        MINING,
        PREP_PLACE_EC,
        CENTER_FOR_EC,
        PLACE_EC,
        OPEN_EC,
        TRANSFER_EC,
        ROTATE_FOR_SHULKER,
        PREP_PLACE_SHULKER,
        CENTER_FOR_SHULKER,
        PLACE_SHULKER,
        OPEN_SHULKER,
        TRANSFER_SHULKER,
        DELAY_AFTER_FILL,
        PREP_BREAK_SHULKER,
        DELAY_AFTER_BREAK_SHULKER,
        PATH_TO_SHULKER_POSITION,
        WAIT_PATH_TO_SHULKER,
        WAIT_AT_SHULKER_POSITION,
        CHECK_SHULKER_PICKUP,
        MOVE_SHULKER_TO_HOTBAR,
        PATH_TO_EC_POSITION,
        WAIT_PATH_TO_EC,
        ROTATE_BACK,
        OPEN_EC_BACK,
        TRANSFER_EC_BACK,
        FILL_PARTIAL_SHULKER,
        PREP_BREAK_EC,
        DELAY_RESUME,
        REPAIR_PICKAXE,
        CLEAR_AREA,
        WAIT_CLEAR_AREA,
        DISPERSION,
        MINING_THROWAWAY,
        CHECK_THROWAWAY,
        THROW_SLOT_ITEM,
        MOVE_TO_MIN_Y,
        WAIT_MIN_Y,
        PLACE_FOUNDATION,
        WAIT_FOUNDATION,
        REFILL_TOOLS;
    }
}
