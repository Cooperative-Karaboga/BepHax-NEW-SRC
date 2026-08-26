package bep.hax.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import bep.hax.Bep;
import bep.hax.config.BepConfig;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.modules.arealoader.AreaLoader;
import bep.hax.util.RotationUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AutoRegear extends Module {
    private final SettingGroup sgTriggers = this.settings.createGroup("Triggers");
    private final SettingGroup sgPlatform = this.settings.createGroup("Platform");
    private final SettingGroup sgHotbar = this.settings.createGroup("Hotbar Slots");
    private final SettingGroup sgRestock = this.settings.createGroup("Restock");
    private final SettingGroup sgItems = this.settings.createGroup("Items");
    private final SettingGroup sgTrash = this.settings.createGroup("Trash Dump");
    private final SettingGroup sgDelays = this.settings.createGroup("Delays");
    private final Setting<Integer> minRockets = this.sgTriggers
        .add(new Builder().name("min-rockets").description("Minimum rockets in inventory to trigger regear").defaultValue(64).min(0).sliderMax(256).build());
    private final Setting<Integer> minElytras = this.sgTriggers
        .add(new Builder().name("min-elytras").description("Minimum number of valid elytras to trigger regear").defaultValue(2).min(1).max(10).build());
    private final Setting<Integer> goalElytras = this.sgTriggers
        .add(new Builder().name("goal-elytras").description("Target number of valid elytras after regearing").defaultValue(6).min(2).max(20).build());
    private final Setting<Integer> elytraDurabilityThreshold = this.sgTriggers
        .add(new Builder().name("elytra-durability-%").description("Durability percentage threshold for valid elytra").defaultValue(30).min(1).max(99).build());
    private final Setting<Boolean> debugMessages = this.sgTriggers
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug-messages")
                .description("Show debug messages in chat")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> createWalls = this.sgPlatform
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("create-walls")
                .description("Create protective walls around platform")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> encapsule = this.sgPlatform
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("encapsule")
                .description("Fully enclose with 2-level walls and roof")
                .defaultValue(true)
                .visible(this.createWalls::get)
                .build()
        );
    private final Setting<Integer> targetYOffset = this.sgPlatform
        .add(
            new Builder()
                .name("target-y-offset")
                .description("In Overworld/End: blocks below build limit to start platform creation. Ignored in Nether.")
                .defaultValue(10)
                .min(5)
                .max(50)
                .sliderRange(5, 30)
                .build()
        );
    private final Setting<Integer> eChestHotbarSlot = this.sgHotbar
        .add(new Builder().name("ender-chest-slot").description("Hotbar slot for ender chest (0-8)").defaultValue(7).range(0, 8).sliderRange(0, 8).build());
    private final Setting<Integer> shulkerHotbarSlot = this.sgHotbar
        .add(new Builder().name("shulker-slot").description("Hotbar slot for shulker boxes (0-8)").defaultValue(6).range(0, 8).sliderRange(0, 8).build());
    private final Setting<Integer> obsidianHotbarSlot = this.sgHotbar
        .add(new Builder().name("obsidian-slot").description("Hotbar slot for obsidian/blocks (0-8)").defaultValue(5).range(0, 8).sliderRange(0, 8).build());
    private final Setting<Integer> rocketHotbarSlot = this.sgHotbar
        .add(new Builder().name("rocket-slot").description("Hotbar slot for firework rockets (0-8)").defaultValue(4).range(0, 8).sliderRange(0, 8).build());
    private final Setting<Boolean> autoReEnable = this.sgRestock
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-re-enable-flight")
                .description("Automatically re-enable flight modules after restocking")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> swapToChestplate = this.sgRestock
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("swap-to-chestplate")
                .description("Swap to chestplate before dropping down")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> restockTotems = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("restock-totems")
                .description("Restock totems of undying from shulkers during regear")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> goalTotems = this.sgItems
        .add(
            new Builder()
                .name("goal-totems")
                .description("Target number of totems after regearing")
                .defaultValue(4)
                .min(0)
                .sliderMax(36)
                .visible(this.restockTotems::get)
                .build()
        );
    private final Setting<Boolean> totemsTriggerRegear = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("totems-trigger-regear")
                .description("Low totems also trigger a regear")
                .defaultValue(false)
                .visible(this.restockTotems::get)
                .build()
        );
    private final Setting<Integer> minTotems = this.sgItems
        .add(
            new Builder()
                .name("min-totems")
                .description("Minimum totems in inventory to trigger regear")
                .defaultValue(1)
                .min(0)
                .sliderMax(36)
                .visible(() -> this.restockTotems.get() && this.totemsTriggerRegear.get())
                .build()
        );
    private final Setting<Boolean> restockFood = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("restock-food")
                .description("Restock food from shulkers during regear")
                .defaultValue(true)
                .build()
        );
    private final Setting<Item> foodItem = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.ItemSetting.Builder()
                .name("food-item")
                .description("The food item to restock")
                .defaultValue(Items.ENCHANTED_GOLDEN_APPLE)
                .filter(item -> item.components().has(DataComponents.FOOD))
                .visible(this.restockFood::get)
                .build()
        );
    private final Setting<Integer> goalFood = this.sgItems
        .add(
            new Builder()
                .name("goal-food")
                .description("Target number of food items after regearing")
                .defaultValue(32)
                .min(0)
                .sliderMax(128)
                .visible(this.restockFood::get)
                .build()
        );
    private final Setting<Boolean> foodTriggerRegear = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("food-trigger-regear")
                .description("Low food also triggers a regear")
                .defaultValue(false)
                .visible(this.restockFood::get)
                .build()
        );
    private final Setting<Integer> minFood = this.sgItems
        .add(
            new Builder()
                .name("min-food")
                .description("Minimum food items in inventory to trigger regear")
                .defaultValue(8)
                .min(0)
                .sliderMax(128)
                .visible(() -> this.restockFood.get() && this.foodTriggerRegear.get())
                .build()
        );
    private final Setting<Integer> placeDelay = this.sgDelays
        .add(
            new Builder()
                .name("place-delay")
                .description("Delay between GrimAirPlace block placements (ticks)")
                .defaultValue(5)
                .min(0)
                .max(100)
                .sliderMin(1)
                .sliderMax(20)
                .build()
        );
    private final Setting<Integer> clickDelay = this.sgDelays
        .add(new Builder().name("click-delay").description("Delay between inventory clicks (ticks)").defaultValue(3).min(0).max(10).build());
    private final Setting<Integer> containerOpenDelay = this.sgDelays
        .add(new Builder().name("container-open-delay").description("Delay after opening container (ticks)").defaultValue(10).min(5).max(30).build());
    private final Setting<Integer> breakDelay = this.sgDelays
        .add(new Builder().name("break-delay").description("Delay after breaking blocks (ticks)").defaultValue(8).min(0).max(20).build());
    private final Setting<Integer> startupDelay = this.sgDelays
        .add(
            new Builder()
                .name("startup-delay")
                .description("Ticks to wait in survival mode before checking inventory. Prevents false triggers from queue.")
                .defaultValue(100)
                .min(0)
                .max(600)
                .sliderMin(20)
                .sliderMax(200)
                .build()
        );
    private final Setting<Boolean> dumpTrash = this.sgTrash
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("dump-trash")
                .description(
                    "Throw non-essential inventory items off the platform before building walls. Never dumps shulkers, ender chests, gear (armor/tools/weapons/elytra), enchanted items, totems, rockets, food, obsidian or netherite loot."
                )
                .defaultValue(false)
                .build()
        );
    private final Setting<List<Item>> keepItems = this.sgTrash
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("keep-items")
                .description("Extra items that are never dumped.")
                .visible(this.dumpTrash::get)
                .build()
        );
    private AutoRegear.RegearState state = AutoRegear.RegearState.IDLE;
    private AutoRegear.RegearState stateBeforeEating = null;
    private int timer = 0;
    private BlockPos platformCenter = null;
    private final List<BlockPos> placedBlocks = new ArrayList<>();
    private final List<BlockPos> pendingBlocks = new ArrayList<>();
    private int currentBlockIndex = 0;
    private BlockPos shulkerPlacePos = null;
    private BlockPos echestPos = null;
    private final List<AutoRegear.RestockCategory> categoriesToProcess = new ArrayList<>();
    private int categoryIndex = 0;
    private int leftoverSweepAttempts = 0;
    private int sweepBaselineShulkers = Integer.MAX_VALUE;
    private int lastSweepShulkerCount = Integer.MAX_VALUE;
    private int transferSlotIndex = 0;
    private int transferStep = 0;
    private int pendingBrokenElytraSlot = -1;
    private ItemStack savedChestplate = ItemStack.EMPTY;
    private final Set<String> processedShulkers = new HashSet<>();
    private int placementAttempts = 0;
    private int shulkerEnderSlot = -1;
    private final int maxPlacementAttempts = 15;
    private int ecAlignTicks = 0;
    private int ecOpenFailures = 0;
    private int returnOpenAttempts = 0;
    private int stateTickCounter = 0;
    private AutoRegear.RegearState lastState = AutoRegear.RegearState.IDLE;
    private final int maxStateTimeout = 600;
    private final Map<String, Module> flightModules = new HashMap<>();
    private final Set<String> disabledModules = new HashSet<>();
    private GrimScaffold grimScaffold = null;
    private Module bepMine = null;
    private boolean bepMineWasActive = false;
    private int scaffoldWaitTicks = 0;
    private float savedYaw = 0.0F;
    private float savedPitch = 0.0F;
    private boolean hadBaritoneGoal = false;
    private String savedBaritoneCommand = null;
    private int startupDelayTicks = 0;
    private boolean startupComplete = false;
    private AutoEat autoEatModule = null;
    private int wallLayer = 0;
    private BlockPos currentClearingPos = null;
    private int clearingProgress = 0;
    private int cleanupBlockIndex = 0;
    private int cleanupBlockAttempts = 0;
    private List<BlockPos> cleanupBlocks = new ArrayList<>();
    private boolean cleanupInitialized = false;
    private int reEnableStage = 0;

    public AutoRegear() {
        super(Bep.HUNT_CATEGORY, "auto-regear", "Automatically creates a platform and restocks rockets/elytras from ender chest");
    }

    @Override
    public void onActivate() {
        this.state = AutoRegear.RegearState.IDLE;
        this.stateBeforeEating = null;
        this.timer = 0;
        this.platformCenter = null;
        this.placedBlocks.clear();
        this.pendingBlocks.clear();
        this.currentBlockIndex = 0;
        this.disabledModules.clear();
        this.shulkerPlacePos = null;
        this.echestPos = null;
        this.categoriesToProcess.clear();
        this.categoryIndex = 0;
        this.leftoverSweepAttempts = 0;
        this.sweepBaselineShulkers = Integer.MAX_VALUE;
        this.lastSweepShulkerCount = Integer.MAX_VALUE;
        this.transferSlotIndex = 0;
        this.transferStep = 0;
        this.pendingBrokenElytraSlot = -1;
        this.savedChestplate = ItemStack.EMPTY;
        this.placementAttempts = 0;
        this.shulkerEnderSlot = -1;
        this.ecAlignTicks = 0;
        this.ecOpenFailures = 0;
        this.returnOpenAttempts = 0;
        this.processedShulkers.clear();
        this.stateTickCounter = 0;
        this.lastState = AutoRegear.RegearState.IDLE;
        this.scaffoldWaitTicks = 0;
        this.savedYaw = 0.0F;
        this.currentClearingPos = null;
        this.clearingProgress = 0;
        this.savedPitch = 0.0F;
        this.hadBaritoneGoal = false;
        this.savedBaritoneCommand = null;
        this.reEnableStage = 0;
        this.cleanupBlockIndex = 0;
        this.cleanupBlockAttempts = 0;
        this.cleanupBlocks.clear();
        this.cleanupInitialized = false;
        this.bepMineWasActive = false;
        this.startupDelayTicks = 0;
        this.startupComplete = false;
        this.grimScaffold = Modules.get().get(GrimScaffold.class);
        this.bepMine = Modules.get().get(BepMine.class);
        this.autoEatModule = Modules.get().get(AutoEat.class);
        if (this.bepMine != null) {
            this.bepMineWasActive = this.bepMine.isActive();
        }

        this.flightModules.put("Replenish", Modules.get().get(Replenish.class));
        this.flightModules.put("AreaLoader", Modules.get().get(AreaLoader.class));
        this.flightModules.put("RocketFly", Modules.get().get(RocketFly.class));
        this.flightModules.put("WaypointFollower", Modules.get().get(WaypointFollower.class));
        this.flightModules.put("ElytraBounce", Modules.get().get(ElytraBounce.class));
        this.flightModules.put("Pitch40", Modules.get().get(Pitch40.class));
        this.flightModules.put("TrailFollower", Modules.get().get(TrailFollower.class));
        this.flightModules.put("ElytraRecast", Modules.get().get(ElytraRecast.class));
        if (this.debugMessages.get()) {
            this.info("AutoRegear activated - monitoring inventory");
        }
    }

    @Override
    public void onDeactivate() {
        this.reEnableFlightModules();
        if (!this.savedChestplate.isEmpty() && this.swapToChestplate.get()) {
            this.restoreChestplate();
        }

        if (this.grimScaffold != null && this.grimScaffold.isActive()) {
            this.grimScaffold.toggle();
        }

        this.mc.options.keyJump.setDown(false);
        this.mc.options.keyUse.setDown(false);
        this.mc.options.keyUp.setDown(false);
        this.mc.options.keyDown.setDown(false);
        this.mc.options.keyLeft.setDown(false);
        this.mc.options.keyRight.setDown(false);
        this.mc.options.keyShift.setDown(false);

        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null && baritone.getPathingBehavior().isPathing()) {
                baritone.getPathingBehavior().cancelEverything();
            }
        } catch (Exception var2) {
        }

        this.placedBlocks.clear();
        this.pendingBlocks.clear();
        this.wallLayer = 0;
        this.cleanupBlocks.clear();
        this.cleanupBlockIndex = 0;
        this.cleanupInitialized = false;
    }

    private boolean isAutoEating() {
        if (this.autoEatModule == null) {
            return false;
        } else {
            return !this.autoEatModule.isActive() ? false : this.autoEatModule.eating;
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.isAutoEating() && this.state != AutoRegear.RegearState.IDLE) {
                if (this.stateBeforeEating == null) {
                    this.stateBeforeEating = this.state;
                    if (this.debugMessages.get()) {
                        this.info("AutoEat is eating - pausing AutoRegear operations");
                    }
                }
            } else {
                if (this.stateBeforeEating != null) {
                    if (this.debugMessages.get()) {
                        this.info("AutoEat finished eating - resuming AutoRegear");
                    }

                    this.stateBeforeEating = null;
                }

                if (this.state != this.lastState) {
                    this.lastState = this.state;
                    this.stateTickCounter = 0;
                    this.ecAlignTicks = 0;
                } else {
                    this.stateTickCounter++;
                }

                if (this.stateTickCounter > 600) {
                    switch (this.state) {
                        case IDLE:
                        case OPENING_ECHEST:
                        case TAKING_SHULKER:
                        case OPENING_SHULKER:
                        case TRANSFERRING_ITEMS:
                        case OPENING_ECHEST_RETURN:
                        case RETURNING_SHULKER:
                        case COMPLETE:
                            break;
                        case SWAP_TO_CHESTPLATE:
                        case DISABLING_MODULES:
                        case CREATING_INITIAL_PLATFORM:
                        case DUMPING_TRASH:
                        case CREATING_WALLS:
                        case CLEARING_ECHEST_AREA:
                        case ROTATING_FOR_ECHEST:
                        case PLACING_ECHEST:
                        case WAIT_ECHEST_PLACE:
                        case WAIT_SHULKER_TAKEN:
                        case ROTATING_FOR_SHULKER:
                        case PLACING_SHULKER:
                        case WAIT_SHULKER_PLACE:
                        case BREAKING_SHULKER:
                        case WAIT_SHULKER_BREAK:
                        case WAIT_SHULKER_PICKUP:
                        case CHECK_NEXT_SHULKER:
                        case RESTORING_ELYTRA:
                        case CLEANUP:
                        case TAKING_OFF:
                        default:
                            if (this.debugMessages.get()) {
                                this.error("Unexpected state timeout in " + this.state + " after 30 seconds. Force completing...");
                            }

                            this.categoryIndex = this.categoriesToProcess.size();
                            this.state = AutoRegear.RegearState.RESTORING_ELYTRA;
                            this.stateTickCounter = 0;
                            this.timer = 0;
                            return;
                        case DROPPING:
                        case CENTERING_ON_PLATFORM:
                        case POSITIONING_FOR_SHULKER:
                            if (this.debugMessages.get()) {
                                this.warning("State timeout in " + this.state + " after 30 seconds. Attempting to continue...");
                            }

                            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                            if (baritone != null) {
                                baritone.getPathingBehavior().cancelEverything();
                            }

                            this.mc.options.keyUp.setDown(false);
                            this.mc.options.keyDown.setDown(false);
                            this.mc.options.keyLeft.setDown(false);
                            this.mc.options.keyRight.setDown(false);
                            this.mc.options.keyShift.setDown(false);
                            if (this.state == AutoRegear.RegearState.DROPPING) {
                                this.platformCenter = this.mc.player.blockPosition().below();
                                this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
                            } else if (this.state == AutoRegear.RegearState.CENTERING_ON_PLATFORM) {
                                this.state = AutoRegear.RegearState.CREATING_INITIAL_PLATFORM;
                            } else if (this.state == AutoRegear.RegearState.POSITIONING_FOR_SHULKER) {
                                this.state = AutoRegear.RegearState.ROTATING_FOR_SHULKER;
                            }

                            this.stateTickCounter = 0;
                            this.timer = 5;
                            return;
                    }
                }

                if (this.timer > 0) {
                    this.timer--;
                } else {
                    switch (this.state) {
                        case IDLE:
                            this.handleIdleState();
                            break;
                        case SWAP_TO_CHESTPLATE:
                            this.handleSwapToChestplate();
                            break;
                        case DISABLING_MODULES:
                            this.handleDisablingModules();
                            break;
                        case DROPPING:
                            this.handleDroppingState();
                            break;
                        case CENTERING_ON_PLATFORM:
                            this.handleCenteringOnPlatform();
                            break;
                        case CREATING_INITIAL_PLATFORM:
                            this.handleCreatingInitialPlatform();
                            break;
                        case DUMPING_TRASH:
                            this.handleDumpingTrash();
                            break;
                        case CREATING_WALLS:
                            this.handleCreatingWalls();
                            break;
                        case CLEARING_ECHEST_AREA:
                            this.handleClearingEchestArea();
                            break;
                        case ROTATING_FOR_ECHEST:
                            this.handleRotatingForEchest();
                            break;
                        case PLACING_ECHEST:
                            this.handlePlacingEchest();
                            break;
                        case WAIT_ECHEST_PLACE:
                            this.handleWaitEchestPlace();
                            break;
                        case OPENING_ECHEST:
                            this.handleOpeningEchest();
                            break;
                        case TAKING_SHULKER:
                            this.handleTakingShulker();
                            break;
                        case WAIT_SHULKER_TAKEN:
                            this.handleWaitShulkerTaken();
                            break;
                        case POSITIONING_FOR_SHULKER:
                            this.handlePositioningForShulker();
                            break;
                        case ROTATING_FOR_SHULKER:
                            this.handleRotatingForShulker();
                            break;
                        case PLACING_SHULKER:
                            this.handlePlacingShulker();
                            break;
                        case WAIT_SHULKER_PLACE:
                            this.handleWaitShulkerPlace();
                            break;
                        case OPENING_SHULKER:
                            this.handleOpeningShulker();
                            break;
                        case TRANSFERRING_ITEMS:
                            this.handleTransferringItems();
                            break;
                        case BREAKING_SHULKER:
                            this.handleBreakingShulker();
                            break;
                        case WAIT_SHULKER_BREAK:
                            this.handleWaitShulkerBreak();
                            break;
                        case WAIT_SHULKER_PICKUP:
                            this.handleWaitShulkerPickup();
                            break;
                        case OPENING_ECHEST_RETURN:
                            this.handleOpeningEchestReturn();
                            break;
                        case RETURNING_SHULKER:
                            this.handleReturningShulker();
                            break;
                        case CHECK_NEXT_SHULKER:
                            this.handleCheckNextShulker();
                            break;
                        case RESTORING_ELYTRA:
                            this.handleRestoringElytra();
                            break;
                        case CLEANUP:
                            this.handleCleanup();
                            break;
                        case TAKING_OFF:
                            this.handleTakingOff();
                            break;
                        case COMPLETE:
                            this.handleComplete();
                    }
                }
            }
        }
    }

    private void handleIdleState() {
        if (!this.startupComplete) {
            if (this.mc.gameMode != null && this.mc.gameMode.getPlayerMode() == GameType.SURVIVAL) {
                this.startupDelayTicks++;
                if (this.startupDelayTicks >= this.startupDelay.get()) {
                    this.startupComplete = true;
                    if (this.debugMessages.get()) {
                        this.info("Startup delay complete - now monitoring inventory");
                    }
                }
            } else {
                this.startupDelayTicks = 0;
            }
        } else {
            if (this.shouldTriggerRegear()) {
                this.leftoverSweepAttempts = 0;
                this.categoriesToProcess.clear();
                this.categoryIndex = 0;
                this.categoriesToProcess.add(AutoRegear.RestockCategory.ELYTRA);
                if (this.restockTotems.get() && this.countCategory(AutoRegear.RestockCategory.TOTEMS) < this.goalTotems.get()) {
                    this.categoriesToProcess.add(AutoRegear.RestockCategory.TOTEMS);
                }

                if (this.restockFood.get() && this.countCategory(AutoRegear.RestockCategory.FOOD) < this.goalFood.get()) {
                    this.categoriesToProcess.add(AutoRegear.RestockCategory.FOOD);
                }

                this.categoriesToProcess.add(AutoRegear.RestockCategory.ROCKETS);
                this.skipSatisfiedCategories();
                if (this.currentCategory() == null) {
                    if (this.debugMessages.get() && this.stateTickCounter % 200 == 0) {
                        this.warning("Supplies low but nothing can be restocked (inventory full?), waiting");
                    }

                    return;
                }

                if (this.debugMessages.get()) {
                    this.info("Low on supplies - initiating auto-regear sequence");
                }

                this.processedShulkers.clear();
                this.sweepBaselineShulkers = this.countShulkerBoxes();
                this.lastSweepShulkerCount = Integer.MAX_VALUE;
                this.savedYaw = this.mc.player.getYRot();
                this.savedPitch = this.mc.player.getXRot();
                if (this.debugMessages.get()) {
                    this.info("Saved rotation: yaw=" + this.savedYaw + ", pitch=" + this.savedPitch);
                }

                try {
                    IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                    if (baritone != null && baritone.getPathingBehavior().isPathing()) {
                        if (baritone.getPathingBehavior().getGoal() != null) {
                            String goalString = baritone.getPathingBehavior().getGoal().toString();
                            ItemStack chestItem = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
                            boolean wearingElytra = chestItem.getItem() == Items.ELYTRA;
                            if (goalString.contains("Elytra") || wearingElytra && !this.mc.player.onGround()) {
                                this.hadBaritoneGoal = true;
                                this.savedBaritoneCommand = "#elytra";
                                if (this.debugMessages.get()) {
                                    this.info("Saved baritone elytra state");
                                }
                            }
                        }

                        baritone.getPathingBehavior().cancelEverything();
                    }
                } catch (Exception e) {
                    if (this.debugMessages.get()) {
                        this.warning("Failed to save baritone state: " + e.getMessage());
                    }
                }

                BlockPos beneath = this.mc.player.blockPosition().below();
                BlockState blockState = this.mc.level.getBlockState(beneath);
                boolean isNether = this.mc.level.dimension() == Level.NETHER;
                int playerY = (int)Math.floor(this.mc.player.getY());
                if (!blockState.canBeReplaced() && blockState.isRedstoneConductor(this.mc.level, beneath)) {
                    if (this.debugMessages.get()) {
                        this.info("Already on solid ground, skipping drop phase");
                    }

                    this.platformCenter = beneath;
                    this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
                    this.disableAllFlightModules();
                    this.timer = 5;
                } else if (!isNether) {
                    int buildLimit = this.mc.level.getHeight() + this.mc.level.getMinY();
                    int minHeightForDrop = buildLimit - this.targetYOffset.get() - 50;
                    if (playerY < minHeightForDrop) {
                        if (this.debugMessages.get()) {
                            this.warning(
                                "Player at Y=" + playerY + " is too low for build limit scaffold (min: " + minHeightForDrop + "). Looking for ground..."
                            );
                        }

                        for (int y = playerY; y > playerY - 20 && y > this.mc.level.getMinY(); y--) {
                            BlockPos checkPos = new BlockPos(
                                this.mc.player.blockPosition().getX(), y, this.mc.player.blockPosition().getZ()
                            );
                            BlockState checkState = this.mc.level.getBlockState(checkPos);
                            if (!checkState.canBeReplaced() && checkState.isRedstoneConductor(this.mc.level, checkPos)) {
                                this.platformCenter = checkPos;
                                if (this.debugMessages.get()) {
                                    this.info("Found solid ground at Y=" + y + ", using as platform");
                                }

                                this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
                                this.disableAllFlightModules();
                                this.timer = 5;
                                return;
                            }
                        }

                        if (this.debugMessages.get()) {
                            this.info("No nearby ground found, will scaffold at current position");
                        }

                        this.disableAllFlightModules();
                        if (this.swapToChestplate.get()) {
                            this.state = AutoRegear.RegearState.SWAP_TO_CHESTPLATE;
                        } else {
                            this.state = AutoRegear.RegearState.DISABLING_MODULES;
                        }
                    } else if (this.swapToChestplate.get()) {
                        this.state = AutoRegear.RegearState.SWAP_TO_CHESTPLATE;
                    } else {
                        this.state = AutoRegear.RegearState.DISABLING_MODULES;
                    }
                } else if (this.swapToChestplate.get()) {
                    this.state = AutoRegear.RegearState.SWAP_TO_CHESTPLATE;
                } else {
                    this.state = AutoRegear.RegearState.DISABLING_MODULES;
                }
            }
        }
    }

    private void handleSwapToChestplate() {
        ItemStack chestStack = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (chestStack.getItem() == Items.ELYTRA) {
            this.savedChestplate = chestStack.copy();
            FindItemResult chestplate = InvUtils.find(
                itemStack -> {
                    Item item = itemStack.getItem();
                    return item == Items.NETHERITE_CHESTPLATE
                        || item == Items.DIAMOND_CHESTPLATE
                        || item == Items.IRON_CHESTPLATE
                        || item == Items.GOLDEN_CHESTPLATE
                        || item == Items.CHAINMAIL_CHESTPLATE
                        || item == Items.LEATHER_CHESTPLATE;
                }
            );
            if (chestplate.found()) {
                InvUtils.move().from(chestplate.slot()).toArmor(2);
                if (this.debugMessages.get()) {
                    this.info("Swapped to chestplate for safe landing");
                }

                this.timer = this.clickDelay.get();
            } else if (this.debugMessages.get()) {
                this.warning("No chestplate found, proceeding without swap");
            }
        }

        this.state = AutoRegear.RegearState.DISABLING_MODULES;
    }

    private void handleDisablingModules() {
        this.disableAllFlightModules();
        this.state = AutoRegear.RegearState.DROPPING;
        if (this.debugMessages.get()) {
            this.info("Flight modules disabled - dropping to platform level");
        }
    }

    private void handleDroppingState() {
        BlockPos beneath = this.mc.player.blockPosition().below();
        BlockState blockState = this.mc.level.getBlockState(beneath);
        boolean isNether = this.mc.level.dimension() == Level.NETHER;
        if (!blockState.canBeReplaced() && blockState.isRedstoneConductor(this.mc.level, beneath)) {
            this.platformCenter = beneath;
            if (this.debugMessages.get()) {
                this.info("Detected solid ground at " + this.platformCenter.toShortString() + " - centering");
            }

            this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
            this.timer = 5;
        } else if (this.stateTickCounter > 400) {
            if (this.debugMessages.get()) {
                this.warning("Falling timeout - forcing platform creation");
            }

            this.platformCenter = this.mc.player.blockPosition();
            this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
            this.timer = 5;
        } else {
            int playerY = (int)Math.floor(this.mc.player.getY());
            double yVelocity = this.mc.player.getDeltaMovement().y;
            int buildLimit = 0;
            int activationHeight;
            if (isNether) {
                activationHeight = Integer.MAX_VALUE;
            } else {
                buildLimit = this.mc.level.getHeight() + this.mc.level.getMinY();
                activationHeight = buildLimit - this.targetYOffset.get();
                if (playerY > activationHeight && this.stateTickCounter % 20 == 0 && this.debugMessages.get()) {
                    this.info("Falling to platform level... Y=" + playerY + " (target: " + activationHeight + ")");
                }
            }

            boolean canActivate;
            if (isNether) {
                canActivate = this.hasSpaceBelow() && yVelocity < -0.05;
            } else {
                int minHeightForScaffold = buildLimit - this.targetYOffset.get() - 50;
                boolean playerIsInValidRange = playerY >= minHeightForScaffold;
                boolean playerIsAtOrBelowActivation = playerY <= activationHeight;
                if (!playerIsInValidRange) {
                    if (this.stateTickCounter % 40 == 0 && this.debugMessages.get()) {
                        this.info("Player at Y=" + playerY + " is below valid scaffold range (min: " + minHeightForScaffold + "). Waiting for ground...");
                    }

                    canActivate = false;
                } else {
                    canActivate = playerIsAtOrBelowActivation && this.hasSpaceBelow() && yVelocity < -0.05;
                }
            }

            if (canActivate) {
                if (this.grimScaffold != null && !this.grimScaffold.isActive()) {
                    this.grimScaffold.toggle();
                    if (this.debugMessages.get()) {
                        this.info("GrimScaffold activated at Y=" + playerY + (isNether ? " (Nether)" : " (below build limit)"));
                    }

                    this.scaffoldWaitTicks = 0;
                    this.timer = 5;
                    return;
                }

                if (this.grimScaffold != null && this.grimScaffold.isActive()) {
                    this.scaffoldWaitTicks++;
                    BlockPos checkPos = this.mc.player.blockPosition().below();
                    if (!this.mc.level.getBlockState(checkPos).canBeReplaced()) {
                        this.platformCenter = checkPos;
                        this.grimScaffold.toggle();
                        if (this.debugMessages.get()) {
                            this.info("GrimScaffold placed initial block at " + this.platformCenter.toShortString());
                        }

                        this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
                        this.timer = 10;
                    } else if (this.scaffoldWaitTicks > 60) {
                        if (this.debugMessages.get()) {
                            this.warning("GrimScaffold timeout");
                        }

                        this.platformCenter = this.mc.player.blockPosition().below();
                        if (this.grimScaffold.isActive()) {
                            this.grimScaffold.toggle();
                        }

                        this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
                        this.timer = 10;
                    }
                }
            }
        }
    }

    private boolean hasSpaceBelow() {
        BlockPos playerPos = this.mc.player.blockPosition();
        return this.mc.level.getBlockState(playerPos.below()).canBeReplaced()
            && this.mc.level.getBlockState(playerPos.below(2)).canBeReplaced();
    }

    private void handleCenteringOnPlatform() {
        if (this.stateTickCounter == 1) {
            if (this.grimScaffold != null && this.grimScaffold.isActive()) {
                this.grimScaffold.toggle();
                if (this.debugMessages.get()) {
                    this.info("Disabled GrimScaffold for centering");
                }
            }

            if (this.debugMessages.get()) {
                this.info("Initiating centering on platform at " + this.platformCenter.toShortString());
            }
        }

        Vec3 centerTarget = Vec3.atCenterOf(this.platformCenter);
        Vec3 playerPos = this.mc.player.position();
        double deltaX = centerTarget.x - playerPos.x;
        double deltaZ = centerTarget.z - playerPos.z;
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (distance < 0.2) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
                baritone.getPathingBehavior().cancelEverything();
                baritone.getCommandManager().execute("cancel");
            }

            this.mc.options.keyUp.setDown(false);
            this.mc.options.keyDown.setDown(false);
            this.mc.options.keyLeft.setDown(false);
            this.mc.options.keyRight.setDown(false);
            this.mc.options.keyShift.setDown(false);
            if (this.debugMessages.get()) {
                this.info("Successfully centered (distance: " + String.format("%.3f", distance) + ")");
            }

            this.state = AutoRegear.RegearState.CREATING_INITIAL_PLATFORM;
            this.timer = 10;
        } else if (this.stateTickCounter <= 5) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
                baritone.getCommandManager().execute("cancel");
                baritone.getCommandManager()
                    .execute(
                        "goto "
                            + this.platformCenter.getX()
                            + " "
                            + (this.platformCenter.getY() + 1)
                            + " "
                            + this.platformCenter.getZ()
                    );
                if (this.debugMessages.get()) {
                    this.info("Baritone goto center - distance: " + String.format("%.2f", distance));
                }
            }
        } else {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            boolean baritoneActive = baritone != null && baritone.getPathingBehavior().isPathing();
            if (!baritoneActive && this.stateTickCounter > 20) {
                if (this.debugMessages.get()) {
                    this.info("Baritone not active, using manual movement");
                }

                float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
                float playerYaw = this.mc.player.getYRot();
                float yawDiff = Mth.wrapDegrees(targetYaw - playerYaw);
                if (Math.abs(yawDiff) > 5.0F) {
                    this.mc.player.setYRot(playerYaw + Math.signum(yawDiff) * 5.0F);
                }

                if (Math.abs(yawDiff) < 30.0F) {
                    if (distance > 0.5) {
                        this.mc.options.keyUp.setDown(true);
                    } else if (distance > 0.2) {
                        this.mc.options.keyUp.setDown(this.stateTickCounter % 3 == 0);
                    }
                }

                this.mc.options.keyShift.setDown(distance < 1.0);
            }

            if (this.stateTickCounter > 200) {
                if (this.debugMessages.get()) {
                    this.warning("Centering timeout, proceeding anyway");
                }

                if (baritone != null) {
                    baritone.getCommandManager().execute("cancel");
                }

                this.mc.options.keyUp.setDown(false);
                this.mc.options.keyDown.setDown(false);
                this.mc.options.keyLeft.setDown(false);
                this.mc.options.keyRight.setDown(false);
                this.mc.options.keyShift.setDown(false);
                this.state = AutoRegear.RegearState.CREATING_INITIAL_PLATFORM;
                this.timer = 10;
            }
        }
    }

    private void handleCreatingInitialPlatform() {
        if (this.stateTickCounter == 1) {
            if (this.debugMessages.get()) {
                this.info("Starting 2x2 platform construction at " + this.platformCenter.toShortString());
            }

            this.pendingBlocks.clear();
            this.placedBlocks.clear();
            this.currentBlockIndex = 0;
        }

        if (this.pendingBlocks.isEmpty() && this.timer == 0) {
            BlockPos[] platformBlocks = new BlockPos[]{
                this.platformCenter,
                this.platformCenter.offset(1, 0, 0),
                this.platformCenter.offset(0, 0, 1),
                this.platformCenter.offset(1, 0, 1)
            };

            for (BlockPos pos : platformBlocks) {
                BlockState state = this.mc.level.getBlockState(pos);
                if (!state.canBeReplaced() && !state.isAir()) {
                    this.placedBlocks.add(pos.immutable());
                    if (this.debugMessages.get()) {
                        this.info("Block already exists at " + pos.toShortString());
                    }
                } else {
                    this.pendingBlocks.add(pos.immutable());
                    if (this.debugMessages.get()) {
                        this.info("Need to place block at " + pos.toShortString());
                    }
                }
            }

            this.currentBlockIndex = 0;
            if (this.pendingBlocks.isEmpty()) {
                if (this.debugMessages.get()) {
                    this.info("2x2 platform already complete");
                }

                if (this.grimScaffold != null && this.grimScaffold.isActive()) {
                    this.grimScaffold.toggle();
                }

                this.state = this.nextStateAfterPlatform();
                this.timer = this.placeDelay.get();
            } else {
                if (this.debugMessages.get()) {
                    this.info("Need to place " + this.pendingBlocks.size() + " blocks for 2x2 platform");
                }

                this.timer = this.placeDelay.get();
            }
        } else if (this.timer <= 0) {
            if (this.currentBlockIndex < this.pendingBlocks.size()) {
                BlockPos pos = this.pendingBlocks.get(this.currentBlockIndex);
                BlockState currentState = this.mc.level.getBlockState(pos);
                if (!currentState.canBeReplaced() && !currentState.isAir()) {
                    if (this.debugMessages.get()) {
                        this.info("Block placed at " + pos.toShortString());
                    }

                    if (!this.placedBlocks.contains(pos.immutable())) {
                        this.placedBlocks.add(pos.immutable());
                    }

                    this.currentBlockIndex++;
                    this.placementAttempts = 0;
                    this.timer = Math.max(this.placeDelay.get() / 4, 2);
                    return;
                }

                if (this.placementAttempts >= 10) {
                    if (this.debugMessages.get()) {
                        this.warning("Failed to place block after 10 attempts at " + pos.toShortString() + ", skipping");
                    }

                    this.currentBlockIndex++;
                    this.placementAttempts = 0;
                    this.timer = this.placeDelay.get();
                    return;
                }

                if (this.placeBlockGrim(pos)) {
                    if (this.debugMessages.get()) {
                        this.info(
                            "Placing platform block "
                                + (this.currentBlockIndex + 1)
                                + "/"
                                + this.pendingBlocks.size()
                                + " at "
                                + pos.toShortString()
                                + " (attempt "
                                + (this.placementAttempts + 1)
                                + ")"
                        );
                    }

                    this.placementAttempts++;
                    this.timer = this.placeDelay.get();
                } else {
                    if (this.debugMessages.get()) {
                        this.warning("Failed to send placement packet, retrying...");
                    }

                    this.placementAttempts++;
                    this.timer = Math.max(this.placeDelay.get() / 2, 5);
                }
            } else {
                if (this.debugMessages.get()) {
                    this.info("Verifying 2x2 platform completion...");
                }

                BlockPos[] requiredBlocks = new BlockPos[]{
                    this.platformCenter,
                    this.platformCenter.offset(1, 0, 0),
                    this.platformCenter.offset(0, 0, 1),
                    this.platformCenter.offset(1, 0, 1)
                };
                boolean allPlaced = true;

                for (BlockPos pos : requiredBlocks) {
                    if (this.mc.level.getBlockState(pos).canBeReplaced() || this.mc.level.getBlockState(pos).isAir()) {
                        if (this.debugMessages.get()) {
                            this.warning("Missing block at " + pos.toShortString());
                        }

                        allPlaced = false;
                    }
                }

                if (allPlaced) {
                    if (this.debugMessages.get()) {
                        this.info("2x2 platform verified complete");
                    }

                    if (this.grimScaffold != null && this.grimScaffold.isActive()) {
                        this.grimScaffold.toggle();
                    }

                    this.pendingBlocks.clear();
                    this.currentBlockIndex = 0;
                    Vec3 centerTarget = Vec3.atCenterOf(this.platformCenter);
                    Vec3 playerPos = this.mc.player.position();
                    double distance = Math.sqrt(
                        Math.pow(centerTarget.x - playerPos.x, 2.0) + Math.pow(centerTarget.z - playerPos.z, 2.0)
                    );
                    if (distance > 0.5 && this.createWalls.get()) {
                        if (this.debugMessages.get()) {
                            this.info("Re-centering before walls (distance: " + String.format("%.2f", distance) + ")");
                        }

                        this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
                        this.timer = 0;
                    } else {
                        this.state = this.nextStateAfterPlatform();
                        this.timer = this.placeDelay.get();
                    }
                } else {
                    if (this.debugMessages.get()) {
                        this.warning("Platform incomplete, restarting...");
                    }

                    this.pendingBlocks.clear();
                    this.currentBlockIndex = 0;
                    this.timer = this.placeDelay.get();
                }
            }
        }
    }

    private void handleCreatingWalls() {
        if (this.stateTickCounter == 1) {
            Vec3 centerTarget = Vec3.atCenterOf(this.platformCenter);
            Vec3 playerPos = this.mc.player.position();
            double distance = Math.sqrt(
                Math.pow(centerTarget.x - playerPos.x, 2.0) + Math.pow(centerTarget.z - playerPos.z, 2.0)
            );
            if (distance > 0.5) {
                if (this.debugMessages.get()) {
                    this.warning("Not centered for wall construction, re-centering");
                }

                this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
                this.timer = 0;
                return;
            }

            if (this.debugMessages.get()) {
                this.info("Starting wall construction with GrimAirPlace" + (this.encapsule.get() ? " (Encapsule mode)" : ""));
            }

            this.pendingBlocks.clear();
            this.currentBlockIndex = 0;
            this.wallLayer = 0;
        }

        if (this.pendingBlocks.isEmpty() && this.timer == 0) {
            List<BlockPos> layerPositions = new ArrayList<>();
            BlockPos[] wallPositions = new BlockPos[]{
                this.platformCenter.offset(-1, 1, 0),
                this.platformCenter.offset(-1, 1, 1),
                this.platformCenter.offset(0, 1, -1),
                this.platformCenter.offset(1, 1, -1),
                this.platformCenter.offset(2, 1, 0),
                this.platformCenter.offset(2, 1, 1),
                this.platformCenter.offset(0, 1, 2),
                this.platformCenter.offset(1, 1, 2)
            };
            if (!this.encapsule.get()) {
                for (BlockPos wallPos : wallPositions) {
                    BlockState wallState = this.mc.level.getBlockState(wallPos);
                    if (wallState.canBeReplaced() || wallState.isAir()) {
                        this.pendingBlocks.add(wallPos.immutable());
                    }
                }

                if (this.pendingBlocks.isEmpty()) {
                    if (this.debugMessages.get()) {
                        this.info("Walls already complete");
                    }

                    this.state = AutoRegear.RegearState.CLEARING_ECHEST_AREA;
                    this.timer = this.placeDelay.get();
                    return;
                }
            } else {
                if (this.wallLayer == 0) {
                    for (BlockPos wallPos : wallPositions) {
                        BlockState wallState = this.mc.level.getBlockState(wallPos);
                        if (wallState.canBeReplaced() || wallState.isAir()) {
                            layerPositions.add(wallPos.immutable());
                        }
                    }
                } else if (this.wallLayer == 1) {
                    for (BlockPos baseWall : wallPositions) {
                        BlockPos upperWall = baseWall.above();
                        BlockState wallState = this.mc.level.getBlockState(upperWall);
                        if (wallState.canBeReplaced() || wallState.isAir()) {
                            layerPositions.add(upperWall.immutable());
                        }
                    }
                } else {
                    if (this.wallLayer != 2) {
                        if (this.debugMessages.get()) {
                            this.info("Encapsule construction complete");
                        }

                        this.state = AutoRegear.RegearState.CLEARING_ECHEST_AREA;
                        this.timer = this.placeDelay.get();
                        return;
                    }

                    BlockPos[] roofPositions = new BlockPos[]{
                        this.platformCenter.offset(0, 3, 0),
                        this.platformCenter.offset(1, 3, 0),
                        this.platformCenter.offset(0, 3, 1),
                        this.platformCenter.offset(1, 3, 1)
                    };

                    for (BlockPos roofPos : roofPositions) {
                        BlockState roofState = this.mc.level.getBlockState(roofPos);
                        if (roofState.canBeReplaced() || roofState.isAir()) {
                            layerPositions.add(roofPos.immutable());
                        }
                    }
                }

                if (layerPositions.isEmpty()) {
                    if (this.debugMessages.get()) {
                        this.info("Layer " + (this.wallLayer + 1) + " already complete");
                    }

                    this.wallLayer++;
                    this.timer = this.placeDelay.get();
                    return;
                }

                this.pendingBlocks.addAll(layerPositions);
                if (this.debugMessages.get()) {
                    this.info("Building layer " + (this.wallLayer + 1) + " with " + this.pendingBlocks.size() + " blocks");
                }
            }

            this.currentBlockIndex = 0;
            if (this.debugMessages.get()) {
                this.info("Placing " + this.pendingBlocks.size() + " blocks using GrimAirPlace");
            }

            this.timer = this.placeDelay.get();
        } else if (this.timer <= 0) {
            if (this.currentBlockIndex < this.pendingBlocks.size()) {
                BlockPos pos = this.pendingBlocks.get(this.currentBlockIndex);
                BlockState currentState = this.mc.level.getBlockState(pos);
                if (!currentState.canBeReplaced() && !currentState.isAir()) {
                    String layerInfo = this.encapsule.get() ? " (Layer " + (this.wallLayer + 1) + ")" : "";
                    if (this.debugMessages.get()) {
                        this.info("Block placed" + layerInfo + " at " + pos.toShortString());
                    }

                    if (!this.placedBlocks.contains(pos.immutable())) {
                        this.placedBlocks.add(pos.immutable());
                    }

                    this.currentBlockIndex++;
                    this.placementAttempts = 0;
                    this.timer = Math.max(this.placeDelay.get() / 3, 3);
                    return;
                }

                if (this.placementAttempts >= 10) {
                    if (this.debugMessages.get()) {
                        this.warning("Failed to place wall block after 10 attempts at " + pos.toShortString() + ", skipping");
                    }

                    this.currentBlockIndex++;
                    this.placementAttempts = 0;
                    this.timer = this.placeDelay.get();
                    return;
                }

                if (this.placeBlockGrim(pos)) {
                    String layerInfo = this.encapsule.get() ? " (Layer " + (this.wallLayer + 1) + ")" : "";
                    if (this.debugMessages.get()) {
                        this.info(
                            "Placing block "
                                + (this.currentBlockIndex + 1)
                                + "/"
                                + this.pendingBlocks.size()
                                + layerInfo
                                + " at "
                                + pos.toShortString()
                                + " (attempt "
                                + (this.placementAttempts + 1)
                                + ")"
                        );
                    }

                    this.placementAttempts++;
                    this.timer = this.placeDelay.get();
                } else {
                    if (this.debugMessages.get()) {
                        this.warning("Failed block placement packet, retrying...");
                    }

                    this.placementAttempts++;
                    this.timer = Math.max(this.placeDelay.get() / 2, 5);
                }
            } else {
                this.placementAttempts = 0;
                if (this.encapsule.get() && this.wallLayer < 2) {
                    if (this.debugMessages.get()) {
                        this.info("Layer " + (this.wallLayer + 1) + " complete, moving to next layer");
                    }

                    this.wallLayer++;
                    this.pendingBlocks.clear();
                    this.currentBlockIndex = 0;
                    this.timer = this.placeDelay.get();
                } else {
                    if (this.debugMessages.get()) {
                        this.info(this.encapsule.get() ? "Encapsule construction complete" : "Wall construction complete");
                    }

                    this.pendingBlocks.clear();
                    this.currentBlockIndex = 0;
                    this.state = AutoRegear.RegearState.CLEARING_ECHEST_AREA;
                    this.timer = this.placeDelay.get();
                }
            }
        }
    }

    private void handleClearingEchestArea() {
        BlockPos[] clearPositions = new BlockPos[]{
            this.platformCenter.offset(0, 1, 0),
            this.platformCenter.offset(1, 1, 0),
            this.platformCenter.offset(0, 1, 1),
            this.platformCenter.offset(1, 1, 1)
        };
        if (this.currentClearingPos != null) {
            BlockState blockState = this.mc.level.getBlockState(this.currentClearingPos);
            if (!blockState.isAir() && !blockState.canBeReplaced() && blockState.getBlock() != Blocks.ENDER_CHEST) {
                Vec3 targetVec = Vec3.atCenterOf(this.currentClearingPos);
                float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), targetVec);
                this.mc.player.setYRot(rotations[0]);
                this.mc.player.setXRot(rotations[1]);
                if (this.clearingProgress == 0) {
                    this.mc.gameMode.startDestroyBlock(this.currentClearingPos, Direction.UP);
                    this.mc.player.swing(InteractionHand.MAIN_HAND);
                    this.clearingProgress++;
                } else {
                    this.mc.gameMode.continueDestroyBlock(this.currentClearingPos, Direction.UP);
                    this.mc.player.swing(InteractionHand.MAIN_HAND);
                    this.clearingProgress++;
                }

                if (this.clearingProgress > 600) {
                    if (this.debugMessages.get()) {
                        this.warning("Block clearing timeout after 30 seconds at " + this.currentClearingPos.toShortString());
                    }

                    this.currentClearingPos = null;
                    this.clearingProgress = 0;
                    this.timer = 2;
                }

                this.timer = 0;
            } else {
                if (this.debugMessages.get()) {
                    this.info("Block cleared at " + this.currentClearingPos.toShortString());
                }

                this.currentClearingPos = null;
                this.clearingProgress = 0;
                this.timer = 2;
            }
        } else {
            for (BlockPos pos : clearPositions) {
                BlockState blockState = this.mc.level.getBlockState(pos);
                if (!blockState.isAir() && !blockState.canBeReplaced() && blockState.getBlock() != Blocks.ENDER_CHEST) {
                    this.currentClearingPos = pos;
                    this.clearingProgress = 0;
                    if (this.debugMessages.get()) {
                        this.info("Clearing " + blockState.getBlock().getName().getString() + " at " + pos.toShortString());
                    }

                    return;
                }
            }

            if (this.debugMessages.get()) {
                this.info("2x2 area cleared for ender chest placement");
            }

            this.currentClearingPos = null;
            this.clearingProgress = 0;
            this.state = AutoRegear.RegearState.ROTATING_FOR_ECHEST;
            this.timer = this.placeDelay.get();
        }
    }

    private void handleRotatingForEchest() {
        this.echestPos = this.platformCenter.offset(1, 1, 1);
        if (this.mc.level.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST) {
            if (this.debugMessages.get()) {
                this.info("Ender chest already present");
            }

            this.state = AutoRegear.RegearState.OPENING_ECHEST;
            this.timer = this.containerOpenDelay.get();
        } else {
            if (this.stateTickCounter == 1) {
                Vec3 targetCenter = Vec3.atCenterOf(this.platformCenter);
                Vec3 playerPos = this.mc.player.position();
                double distance = Math.sqrt(
                    Math.pow(targetCenter.x - playerPos.x, 2.0) + Math.pow(targetCenter.z - playerPos.z, 2.0)
                );
                if (distance > 0.5) {
                    if (this.debugMessages.get()) {
                        this.warning("Player drifted from center before placing enderchest, re-centering");
                    }

                    this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
                    this.timer = 0;
                    return;
                }
            }

            if (this.stateTickCounter > 40) {
                if (this.debugMessages.get()) {
                    this.info("Rotation timeout, placing anyway");
                }

                this.state = AutoRegear.RegearState.PLACING_ECHEST;
                this.timer = 2;
            } else {
                BlockPos groundPos = this.echestPos.below();
                Vec3 groundCenter = Vec3.atLowerCornerOf(groundPos).add(0.5, 0.5, 0.5);
                Vec3 eyePos = this.mc.player.getEyePosition();
                Vec3 lookVec = groundCenter.subtract(eyePos);
                double deltaX = lookVec.x;
                double deltaY = lookVec.y;
                double deltaZ = lookVec.z;
                double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
                float targetPitch = (float)Math.toDegrees(Math.atan2(-deltaY, horizontalDist));
                float yawDiff = Math.abs(Mth.wrapDegrees(this.mc.player.getYRot() - targetYaw));
                float pitchDiff = Math.abs(this.mc.player.getXRot() - targetPitch);
                if (yawDiff < 5.0F && pitchDiff < 5.0F) {
                    if (this.debugMessages.get()) {
                        this.info("Rotation complete for ender chest");
                    }

                    this.state = AutoRegear.RegearState.PLACING_ECHEST;
                    this.timer = 2;
                } else {
                    Rotations.rotate(targetYaw, targetPitch, 50, () -> {});
                }
            }
        }
    }

    private void handlePlacingEchest() {
        if (this.placementAttempts > 15) {
            if (this.debugMessages.get()) {
                this.error("Failed to place ender chest after 15 attempts");
            }

            this.state = AutoRegear.RegearState.IDLE;
            this.placementAttempts = 0;
        } else {
            this.mc.options.keyShift.setDown(false);
            int targetHotbarSlot = this.eChestHotbarSlot.get();
            ItemStack currentStack = this.mc.player.getInventory().getItem(targetHotbarSlot);
            if (currentStack.getItem() != Items.ENDER_CHEST) {
                int eChestSlot = InvUtils.find(Items.ENDER_CHEST).slot();
                if (eChestSlot == -1) {
                    if (this.debugMessages.get()) {
                        this.error("No ender chest found in inventory!");
                    }

                    this.state = AutoRegear.RegearState.IDLE;
                } else {
                    InvUtils.move().from(eChestSlot).to(targetHotbarSlot);
                    if (this.debugMessages.get()) {
                        this.info("Moving ender chest to hotbar slot " + targetHotbarSlot);
                    }

                    this.timer = this.clickDelay.get();
                }
            } else {
                if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != targetHotbarSlot) {
                    ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(targetHotbarSlot);
                }

                BlockPos groundPos = this.echestPos.below();
                if (!this.mc.level.getBlockState(groundPos).isRedstoneConductor(this.mc.level, groundPos)) {
                    if (this.debugMessages.get()) {
                        this.error("No solid ground for ender chest at " + this.echestPos);
                    }

                    this.state = AutoRegear.RegearState.IDLE;
                } else {
                    BlockHitResult placeHit = new BlockHitResult(Vec3.atLowerCornerOf(groundPos).add(0.5, 1.0, 0.5), Direction.UP, groundPos, false);
                    this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, placeHit);
                    if (this.debugMessages.get()) {
                        this.info("Placing ender chest");
                    }

                    this.state = AutoRegear.RegearState.WAIT_ECHEST_PLACE;
                    this.timer = this.containerOpenDelay.get();
                    this.placementAttempts++;
                }
            }
        }
    }

    private void handleWaitEchestPlace() {
        if (this.mc.level.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST) {
            if (this.debugMessages.get()) {
                this.info("Ender chest placed successfully");
            }

            this.state = AutoRegear.RegearState.OPENING_ECHEST;
            this.timer = this.containerOpenDelay.get();
            this.placementAttempts = 0;
        } else if (this.placementAttempts > 15) {
            if (this.debugMessages.get()) {
                this.error("Ender chest failed to place after 15 attempts");
            }

            this.state = AutoRegear.RegearState.IDLE;
            this.placementAttempts = 0;
        } else {
            if (this.debugMessages.get()) {
                this.warning("Ender chest not detected, retrying placement...");
            }

            this.state = AutoRegear.RegearState.PLACING_ECHEST;
            this.timer = this.placeDelay.get();
        }
    }

    private void handleOpeningEchest() {
        if (!this.walkToEchestStand()) {
            if (this.mc.level.getBlockState(this.echestPos).getBlock() != Blocks.ENDER_CHEST) {
                if (this.debugMessages.get()) {
                    this.warning("Ender chest missing, re-placing");
                }

                this.state = AutoRegear.RegearState.ROTATING_FOR_ECHEST;
                this.timer = this.placeDelay.get();
            } else {
                BlockHitResult hit = this.blockHit(this.echestPos, this.mc.player.getEyePosition());
                if (hit != null && this.lookAtReal(hit.getLocation())) {
                    if (this.ecAlignTicks++ >= 1) {
                        this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hit);
                        this.mc.player.swing(InteractionHand.MAIN_HAND);
                        this.state = AutoRegear.RegearState.TAKING_SHULKER;
                        this.transferStep = 0;
                        this.timer = this.containerOpenDelay.get();
                        if (this.debugMessages.get()) {
                            this.info("Opening ender chest");
                        }
                    }
                } else {
                    this.ecAlignTicks = 0;
                }
            }
        }
    }

    private boolean walkToEchestStand() {
        BlockPos standPos = this.echestPos.offset(-1, -1, 0);
        Vec3 targetCenter = Vec3.atCenterOf(standPos.above());
        double distance = this.mc.player.position().distanceTo(targetCenter);
        if (distance > 0.3 && this.stateTickCounter < 40) {
            if (this.stateTickCounter == 1) {
                try {
                    IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                    if (baritone != null) {
                        baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(standPos.above()));
                        if (this.debugMessages.get()) {
                            this.info("Walking to ender chest");
                        }
                    }
                } catch (Exception e) {
                    if (this.debugMessages.get()) {
                        this.warning("Failed to use baritone for positioning: " + e.getMessage());
                    }
                }
            }

            this.ecAlignTicks = 0;
            return true;
        } else {
            try {
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (baritone != null && baritone.getPathingBehavior().isPathing()) {
                    baritone.getPathingBehavior().cancelEverything();
                }
            } catch (Exception var6) {
            }

            this.mc.options.keyShift.setDown(false);
            return false;
        }
    }

    private void handleTakingShulker() {
        if (this.mc.screen instanceof ContainerScreen screen) {
            this.ecOpenFailures = 0;
            ChestMenu var12 = screen.getMenu();
            AutoRegear.RestockCategory category = this.currentCategory();
            if (category == null) {
                this.mc.player.closeContainer();
                this.state = AutoRegear.RegearState.RESTORING_ELYTRA;
                this.timer = 0;
            } else {
                String targetType = category.name().toLowerCase();
                int syncId = var12.containerId;
                if (this.transferStep == 0) {
                    this.shulkerEnderSlot = -1;

                    for (int slot = 0; slot < var12.getRowCount() * 9; slot++) {
                        ItemStack stack = var12.getSlot(slot).getItem();
                        if (this.isShulkerBox(stack.getItem())) {
                            ItemContainerContents container = stack.get(DataComponents.CONTAINER);
                            if (container != null) {
                                boolean hasTargetItem = false;

                                for (ItemStack contentStack : container.nonEmptyItems()) {
                                    if (this.matchesCategory(category, contentStack)) {
                                        hasTargetItem = true;
                                        break;
                                    }
                                }

                                if (hasTargetItem) {
                                    String shulkerId = targetType + "_slot_" + slot;
                                    if (!this.processedShulkers.contains(shulkerId)) {
                                        this.shulkerEnderSlot = slot;
                                        this.processedShulkers.add(shulkerId);
                                        if (this.debugMessages.get()) {
                                            this.info("Found " + targetType + " shulker in ender chest slot " + slot);
                                        }
                                        break;
                                    }

                                    if (this.debugMessages.get()) {
                                        this.info("Skipping already processed " + targetType + " shulker at slot " + slot);
                                    }
                                }
                            }
                        }
                    }

                    if (this.shulkerEnderSlot == -1) {
                        if (this.debugMessages.get()) {
                            this.warning("No more unprocessed " + targetType + " shulkers found in ender chest");
                        }

                        this.mc.player.closeContainer();
                        this.categoryIndex++;
                        this.skipSatisfiedCategories();
                        if (this.currentCategory() != null) {
                            this.state = AutoRegear.RegearState.OPENING_ECHEST;
                        } else {
                            this.state = AutoRegear.RegearState.RESTORING_ELYTRA;
                        }

                        this.timer = this.containerOpenDelay.get();
                        return;
                    }

                    this.mc.gameMode.handleInventoryMouseClick(syncId, this.shulkerEnderSlot, this.shulkerHotbarSlot.get(), ClickType.SWAP, this.mc.player);
                    this.timer = this.clickDelay.get() * 2;
                    this.transferStep = 1;
                } else if (this.transferStep == 1) {
                    ItemStack hotbarStack = this.mc.player.getInventory().getItem(this.shulkerHotbarSlot.get());
                    if (!this.isShulkerBox(hotbarStack.getItem())) {
                        if (this.debugMessages.get()) {
                            this.warning("Shulker swap failed, retrying...");
                        }

                        this.transferStep = 0;
                        this.timer = this.clickDelay.get();
                        return;
                    }

                    if (this.debugMessages.get()) {
                        this.info("Took " + targetType + " shulker from ender chest");
                    }

                    this.mc.player.closeContainer();
                    this.state = AutoRegear.RegearState.WAIT_SHULKER_TAKEN;
                    this.timer = this.clickDelay.get();
                }
            }
        } else {
            if (this.stateTickCounter > 40) {
                this.ecOpenFailures++;
                if (this.ecOpenFailures >= 5) {
                    if (this.debugMessages.get()) {
                        this.error("Ender chest won't open after " + this.ecOpenFailures + " attempts, finishing up");
                    }

                    this.ecOpenFailures = 0;
                    this.categoryIndex = this.categoriesToProcess.size();
                    this.state = AutoRegear.RegearState.RESTORING_ELYTRA;
                    this.timer = 0;
                    return;
                }

                if (this.debugMessages.get()) {
                    this.error("Failed to open ender chest, retrying (" + this.ecOpenFailures + "/5)");
                }

                this.state = AutoRegear.RegearState.OPENING_ECHEST;
                this.timer = this.containerOpenDelay.get();
            }
        }
    }

    private void handleWaitShulkerTaken() {
        if (this.mc.screen != null) {
            this.mc.player.closeContainer();
        }

        ItemStack hotbarStack = this.mc.player.getInventory().getItem(this.shulkerHotbarSlot.get());
        if (!this.isShulkerBox(hotbarStack.getItem())) {
            if (this.debugMessages.get()) {
                this.error("Shulker not in hotbar slot " + this.shulkerHotbarSlot.get());
            }

            this.state = AutoRegear.RegearState.OPENING_ECHEST;
            this.timer = this.containerOpenDelay.get();
        } else {
            String targetType = this.currentCategory() != null ? this.currentCategory().name().toLowerCase() : "unknown";
            if (this.debugMessages.get()) {
                this.info(targetType + " shulker confirmed in hotbar slot " + this.shulkerHotbarSlot.get());
            }

            this.state = AutoRegear.RegearState.POSITIONING_FOR_SHULKER;
            this.timer = 0;
            this.placementAttempts = 0;
        }
    }

    private void handlePositioningForShulker() {
        if (this.stateTickCounter == 1) {
            if (this.debugMessages.get()) {
                this.info("Positioning for shulker placement");
            }

            if (this.bepMine != null && this.bepMine.isActive()) {
                this.bepMine.toggle();
                if (this.debugMessages.get()) {
                    this.info("Disabled BepMine for shulker operations");
                }
            }
        }

        Vec3 targetCenter = Vec3.atCenterOf(this.platformCenter);
        Vec3 playerPos = this.mc.player.position();
        double deltaX = targetCenter.x - playerPos.x;
        double deltaZ = targetCenter.z - playerPos.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (horizontalDistance < 0.2) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
                baritone.getPathingBehavior().cancelEverything();
                baritone.getCommandManager().execute("cancel");
            }

            this.mc.options.keyUp.setDown(false);
            this.mc.options.keyDown.setDown(false);
            this.mc.options.keyLeft.setDown(false);
            this.mc.options.keyRight.setDown(false);
            this.mc.options.keyShift.setDown(false);
            if (this.debugMessages.get()) {
                this.info("Centered for shulker (distance: " + String.format("%.3f", horizontalDistance) + ")");
            }

            this.state = AutoRegear.RegearState.ROTATING_FOR_SHULKER;
            this.timer = 10;
        } else if (this.stateTickCounter <= 5) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
                baritone.getCommandManager().execute("cancel");
                baritone.getCommandManager()
                    .execute(
                        "goto "
                            + this.platformCenter.getX()
                            + " "
                            + (this.platformCenter.getY() + 1)
                            + " "
                            + this.platformCenter.getZ()
                    );
                if (this.debugMessages.get()) {
                    this.info("Baritone goto for shulker - distance: " + String.format("%.2f", horizontalDistance));
                }
            }
        } else {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            boolean baritoneActive = baritone != null && baritone.getPathingBehavior().isPathing();
            if (!baritoneActive && this.stateTickCounter > 20) {
                if (this.debugMessages.get()) {
                    this.info("Using manual movement for shulker positioning");
                }

                float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
                float playerYaw = this.mc.player.getYRot();
                float yawDiff = Mth.wrapDegrees(targetYaw - playerYaw);
                if (Math.abs(yawDiff) > 5.0F) {
                    this.mc.player.setYRot(playerYaw + Math.signum(yawDiff) * 5.0F);
                }

                if (Math.abs(yawDiff) < 30.0F) {
                    if (horizontalDistance > 0.5) {
                        this.mc.options.keyUp.setDown(true);
                    } else if (horizontalDistance > 0.2) {
                        this.mc.options.keyUp.setDown(this.stateTickCounter % 3 == 0);
                    }
                }

                this.mc.options.keyShift.setDown(horizontalDistance < 1.0);
            }

            if (this.stateTickCounter > 150) {
                if (this.debugMessages.get()) {
                    this.warning("Shulker positioning timeout");
                }

                if (baritone != null) {
                    baritone.getCommandManager().execute("cancel");
                }

                this.mc.options.keyUp.setDown(false);
                this.mc.options.keyShift.setDown(false);
                this.state = AutoRegear.RegearState.ROTATING_FOR_SHULKER;
                this.timer = 10;
            }
        }
    }

    private void handleRotatingForShulker() {
        this.shulkerPlacePos = this.platformCenter.offset(1, 1, 0);
        BlockState shulkerPosState = this.mc.level.getBlockState(this.shulkerPlacePos);
        if (!shulkerPosState.isAir() && !shulkerPosState.canBeReplaced() && !(shulkerPosState.getBlock() instanceof ShulkerBoxBlock)) {
            if (this.debugMessages.get()) {
                this.info("Block at shulker position needs to be cleared: " + shulkerPosState.getBlock().getName().getString());
            }

            Vec3 blockCenter = Vec3.atCenterOf(this.shulkerPlacePos);
            float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), blockCenter);
            this.mc.player.setYRot(rotations[0]);
            this.mc.player.setXRot(rotations[1]);
            if (this.stateTickCounter == 1) {
                this.mc.gameMode.startDestroyBlock(this.shulkerPlacePos, Direction.UP);
                this.mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                this.mc.gameMode.continueDestroyBlock(this.shulkerPlacePos, Direction.UP);
                this.mc.player.swing(InteractionHand.MAIN_HAND);
            }

            if (this.stateTickCounter > 300 && this.debugMessages.get()) {
                this.warning("Block clearing timeout at shulker position");
            }
        } else if (this.mc.level.getBlockState(this.shulkerPlacePos).getBlock() instanceof ShulkerBoxBlock) {
            if (this.debugMessages.get()) {
                this.info("Shulker already present");
            }

            this.state = AutoRegear.RegearState.OPENING_SHULKER;
            this.timer = this.containerOpenDelay.get();
        } else if (this.stateTickCounter > 40) {
            if (this.debugMessages.get()) {
                this.info("Rotation timeout, placing anyway");
            }

            this.state = AutoRegear.RegearState.PLACING_SHULKER;
            this.timer = 2;
        } else {
            BlockPos groundPos = this.shulkerPlacePos.below();
            Vec3 groundCenter = Vec3.atLowerCornerOf(groundPos).add(0.5, 1.0, 0.5);
            Vec3 eyePos = this.mc.player.getEyePosition();
            Vec3 lookVec = groundCenter.subtract(eyePos);
            double deltaX = lookVec.x;
            double deltaY = lookVec.y;
            double deltaZ = lookVec.z;
            double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
            float targetPitch = (float)Math.toDegrees(Math.atan2(-deltaY, horizontalDist));
            float yawDiff = Math.abs(Mth.wrapDegrees(this.mc.player.getYRot() - targetYaw));
            float pitchDiff = Math.abs(this.mc.player.getXRot() - targetPitch);
            this.mc.player.setYRot(targetYaw);
            this.mc.player.setXRot(targetPitch);
            if (yawDiff < 5.0F && pitchDiff < 5.0F) {
                if (this.debugMessages.get()) {
                    this.info("Rotation complete for shulker");
                }

                this.state = AutoRegear.RegearState.PLACING_SHULKER;
                this.timer = 2;
            } else {
                Rotations.rotate(targetYaw, targetPitch, 50, () -> {});
            }
        }
    }

    private void handlePlacingShulker() {
        if (this.placementAttempts > 15) {
            if (this.debugMessages.get()) {
                this.error("Failed to place shulker after 15 attempts");
            }

            this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
            this.placementAttempts = 0;
        } else {
            if (this.bepMine != null && this.bepMine.isActive()) {
                this.bepMine.toggle();
                if (this.debugMessages.get()) {
                    this.info("Disabled BepMine to prevent shulker mining");
                }
            }

            this.mc.options.keyShift.setDown(false);
            ItemStack shulkerStack = this.mc.player.getInventory().getItem(this.shulkerHotbarSlot.get());
            if (!this.isShulkerBox(shulkerStack.getItem())) {
                if (this.debugMessages.get()) {
                    this.error("No shulker in hotbar slot " + this.shulkerHotbarSlot.get());
                }

                this.state = AutoRegear.RegearState.OPENING_ECHEST;
                this.placementAttempts = 0;
            } else {
                if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != this.shulkerHotbarSlot.get()) {
                    ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(this.shulkerHotbarSlot.get());
                }

                BlockPos groundPos = this.shulkerPlacePos.below();
                BlockState shulkerPosState = this.mc.level.getBlockState(this.shulkerPlacePos);
                if (!shulkerPosState.isAir() && !shulkerPosState.canBeReplaced()) {
                    if (this.debugMessages.get()) {
                        this.warning("Shulker position blocked, going back to rotation/clearing");
                    }

                    this.state = AutoRegear.RegearState.ROTATING_FOR_SHULKER;
                    this.timer = 2;
                } else if (!this.mc.level.getBlockState(groundPos).isRedstoneConductor(this.mc.level, groundPos)) {
                    if (this.debugMessages.get()) {
                        this.error("No solid ground for shulker at " + this.shulkerPlacePos);
                    }

                    this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
                    this.placementAttempts = 0;
                } else {
                    Vec3 groundCenter = Vec3.atLowerCornerOf(groundPos).add(0.5, 1.0, 0.5);
                    Vec3 eyePos = this.mc.player.getEyePosition();
                    Vec3 lookVec = groundCenter.subtract(eyePos);
                    double deltaX = lookVec.x;
                    double deltaY = lookVec.y;
                    double deltaZ = lookVec.z;
                    double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
                    float targetPitch = (float)Math.toDegrees(Math.atan2(-deltaY, horizontalDist));
                    this.mc.player.setYRot(targetYaw);
                    this.mc.player.setXRot(targetPitch);
                    BlockHitResult placeHit = new BlockHitResult(Vec3.atLowerCornerOf(groundPos).add(0.5, 1.0, 0.5), Direction.UP, groundPos, false);
                    this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, placeHit);
                    if (this.debugMessages.get()) {
                        this.info("Placing shulker");
                    }

                    this.state = AutoRegear.RegearState.WAIT_SHULKER_PLACE;
                    this.timer = this.placeDelay.get();
                    this.placementAttempts++;
                }
            }
        }
    }

    private void handleWaitShulkerPlace() {
        if (this.mc.level.getBlockState(this.shulkerPlacePos).getBlock() instanceof ShulkerBoxBlock) {
            if (this.debugMessages.get()) {
                this.info("Shulker placed successfully");
            }

            this.state = AutoRegear.RegearState.OPENING_SHULKER;
            this.timer = this.containerOpenDelay.get();
            this.placementAttempts = 0;
        } else if (this.placementAttempts > 15) {
            if (this.debugMessages.get()) {
                this.error("Shulker failed to place after 15 attempts");
            }

            this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
            this.placementAttempts = 0;
        } else {
            if (this.debugMessages.get()) {
                this.warning("Shulker not detected, retrying placement...");
            }

            this.state = AutoRegear.RegearState.PLACING_SHULKER;
            this.timer = this.placeDelay.get();
        }
    }

    private void handleOpeningShulker() {
        this.mc.options.keyShift.setDown(false);
        Vec3 shulkerCenter = Vec3.atCenterOf(this.shulkerPlacePos);
        Vec3 eyePos = this.mc.player.getEyePosition();
        Vec3 rotationVec = shulkerCenter.subtract(eyePos);
        double deltaX = rotationVec.x;
        double deltaY = rotationVec.y;
        double deltaZ = rotationVec.z;
        double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        float targetPitch = (float)Math.toDegrees(Math.atan2(-deltaY, horizontalDist));
        Rotations.rotate(targetYaw, targetPitch, 50, () -> {
            BlockHitResult hitResult = new BlockHitResult(shulkerCenter, Direction.UP, this.shulkerPlacePos, false);
            this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hitResult);
        });
        this.state = AutoRegear.RegearState.TRANSFERRING_ITEMS;
        this.transferSlotIndex = 0;
        this.transferStep = 0;
        this.timer = this.containerOpenDelay.get();
        if (this.debugMessages.get()) {
            this.info("Opening shulker");
        }
    }

    private void handleTransferringItems() {
        if (!(this.mc.screen instanceof AbstractContainerScreen<?> screen)) {
            if (this.stateTickCounter > 40) {
                if (this.debugMessages.get()) {
                    this.error("Failed to open shulker - screen didn't appear");
                }

                this.state = AutoRegear.RegearState.OPENING_SHULKER;
                this.timer = this.containerOpenDelay.get();
            }
        } else {
            int var9 = this.mc.player.containerMenu.containerId;
            AutoRegear.RestockCategory category = this.currentCategory();
            if (category == null) {
                this.mc.player.closeContainer();
                this.state = AutoRegear.RegearState.BREAKING_SHULKER;
                this.timer = this.breakDelay.get();
                this.transferStep = 0;
            } else {
                String itemType = category.name().toLowerCase();
                if (this.transferStep == 0 && this.isCategorySatisfied(category)) {
                    if (this.debugMessages.get()) {
                        this.info("Restock goal reached for " + itemType + ", stopping transfer");
                    }

                    this.mc.player.closeContainer();
                    this.state = AutoRegear.RegearState.BREAKING_SHULKER;
                    this.timer = this.breakDelay.get();
                    this.transferStep = 0;
                } else if (this.transferStep == 1) {
                    this.mc.gameMode.handleInventoryMouseClick(var9, this.pendingBrokenElytraSlot, 0, ClickType.PICKUP, this.mc.player);
                    if (this.debugMessages.get()) {
                        this.info("Swapping with broken elytra in slot " + this.pendingBrokenElytraSlot);
                    }

                    this.timer = this.clickDelay.get();
                    this.transferStep = 2;
                } else if (this.transferStep == 2) {
                    this.mc.gameMode.handleInventoryMouseClick(var9, this.transferSlotIndex, 0, ClickType.PICKUP, this.mc.player);
                    if (this.debugMessages.get()) {
                        this.info("Placed broken elytra back into shulker, gained 1 valid elytra");
                    }

                    this.timer = this.clickDelay.get();
                    this.transferStep = 0;
                    this.pendingBrokenElytraSlot = -1;
                    this.transferSlotIndex++;
                } else {
                    if (this.transferSlotIndex < 27) {
                        Slot slot = this.mc.player.containerMenu.getSlot(this.transferSlotIndex);
                        ItemStack stack = slot.getItem();
                        if (!stack.isEmpty()) {
                            Item item = stack.getItem();
                            if (category == AutoRegear.RestockCategory.ELYTRA && item == Items.ELYTRA) {
                                if (this.isValidElytra(stack)) {
                                    int brokenElytraSlot = this.findBrokenElytraInInventory();
                                    if (brokenElytraSlot != -1) {
                                        this.pendingBrokenElytraSlot = brokenElytraSlot;
                                        this.mc.gameMode.handleInventoryMouseClick(var9, this.transferSlotIndex, 0, ClickType.PICKUP, this.mc.player);
                                        if (this.debugMessages.get()) {
                                            this.info("Picking up good elytra from slot " + this.transferSlotIndex);
                                        }

                                        this.timer = this.clickDelay.get();
                                        this.transferStep = 1;
                                        return;
                                    }

                                    if (!this.hasSpaceForQuickMove(stack)) {
                                        if (this.debugMessages.get()) {
                                            this.info("Skipping elytra in shulker slot " + this.transferSlotIndex + " - keeping a slot free for shulker pickup");
                                        }

                                        this.transferStep = 0;
                                        this.transferSlotIndex++;
                                        return;
                                    }

                                    this.mc.gameMode.handleInventoryMouseClick(var9, this.transferSlotIndex, 0, ClickType.QUICK_MOVE, this.mc.player);
                                    if (this.debugMessages.get()) {
                                        this.info(
                                            "Transferred good elytra from shulker slot "
                                                + this.transferSlotIndex
                                                + " (now have "
                                                + this.countValidElytras()
                                                + "/"
                                                + this.goalElytras.get()
                                                + ")"
                                        );
                                    }

                                    this.timer = this.clickDelay.get();
                                    this.transferStep = 0;
                                    this.transferSlotIndex++;
                                    return;
                                }
                            } else if (category != AutoRegear.RestockCategory.ELYTRA && this.matchesCategory(category, stack)) {
                                if (!this.hasSpaceForQuickMove(stack)) {
                                    if (this.debugMessages.get()) {
                                        this.info(
                                            "Skipping " + itemType + " in shulker slot " + this.transferSlotIndex + " - keeping a slot free for shulker pickup"
                                        );
                                    }

                                    this.transferStep = 0;
                                    this.transferSlotIndex++;
                                    return;
                                }

                                this.mc.gameMode.handleInventoryMouseClick(var9, this.transferSlotIndex, 0, ClickType.QUICK_MOVE, this.mc.player);
                                if (this.debugMessages.get()) {
                                    this.info("Transferred " + itemType + " from shulker slot " + this.transferSlotIndex);
                                }

                                this.timer = this.clickDelay.get();
                                this.transferStep = 0;
                                this.transferSlotIndex++;
                                return;
                            }
                        }

                        this.transferSlotIndex++;
                        this.transferStep = 0;
                    } else {
                        this.mc.player.closeContainer();
                        if (this.debugMessages.get()) {
                            this.info("Finished transferring " + itemType + " from shulker");
                        }

                        this.state = AutoRegear.RegearState.BREAKING_SHULKER;
                        this.timer = this.breakDelay.get();
                        this.transferStep = 0;
                    }
                }
            }
        }
    }

    private int findBrokenElytraInInventory() {
        if (this.mc.player.containerMenu == null) {
            return -1;
        }

        for (int playerSlot = 27; playerSlot < this.mc.player.containerMenu.slots.size(); playerSlot++) {
            Slot slot = this.mc.player.containerMenu.getSlot(playerSlot);
            ItemStack stack = slot.getItem();
            if (stack.getItem() == Items.ELYTRA) {
                int maxDurability = stack.getMaxDamage();
                int currentDurability = maxDurability - stack.getDamageValue();
                double percent = (double)currentDurability / maxDurability * 100.0;
                if (percent < this.elytraDurabilityThreshold.get().intValue()) {
                    return playerSlot;
                }
            }
        }

        return -1;
    }

    private void handleBreakingShulker() {
        if (this.bepMine != null && this.bepMine.isActive()) {
            this.bepMine.toggle();
            if (this.debugMessages.get()) {
                this.info("Disabled BepMine to prevent interference");
            }
        }

        if (this.mc.level.getBlockState(this.shulkerPlacePos).getBlock() instanceof ShulkerBoxBlock) {
            this.mc.gameMode.startDestroyBlock(this.shulkerPlacePos, Direction.UP);
            this.mc.player.swing(InteractionHand.MAIN_HAND);
            if (this.debugMessages.get()) {
                this.info("Breaking shulker manually");
            }

            this.state = AutoRegear.RegearState.WAIT_SHULKER_BREAK;
            this.timer = this.breakDelay.get();
            this.placementAttempts = 0;
        } else {
            if (this.debugMessages.get()) {
                this.info("Shulker already broken");
            }

            this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
            this.timer = 0;
        }
    }

    private void handleWaitShulkerBreak() {
        if (!(this.mc.level.getBlockState(this.shulkerPlacePos).getBlock() instanceof ShulkerBoxBlock)) {
            if (this.debugMessages.get()) {
                this.info("Shulker broken - immediately walking to pickup");
            }

            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
                baritone.getPathingBehavior().cancelEverything();
                baritone.getCustomGoalProcess().setGoal(null);
            }

            this.state = AutoRegear.RegearState.WAIT_SHULKER_PICKUP;
            this.timer = 0;
            this.placementAttempts = 0;
        } else {
            this.mc.gameMode.continueDestroyBlock(this.shulkerPlacePos, Direction.UP);
            this.mc.player.swing(InteractionHand.MAIN_HAND);
            if (this.placementAttempts > 150) {
                if (this.debugMessages.get()) {
                    this.warning("Failed to break shulker after extended attempts, skipping return");
                }

                this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
                this.placementAttempts = 0;
            } else {
                this.placementAttempts++;
                this.timer = 0;
            }
        }
    }

    private void handleWaitShulkerPickup() {
        ItemStack hotbarStack = this.mc.player.getInventory().getItem(this.shulkerHotbarSlot.get());
        if (this.isShulkerBox(hotbarStack.getItem())) {
            if (this.debugMessages.get()) {
                this.info("Shulker picked up in hotbar slot " + this.shulkerHotbarSlot.get());
            }

            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null && baritone.getPathingBehavior().isPathing()) {
                baritone.getPathingBehavior().cancelEverything();
            }

            this.state = AutoRegear.RegearState.OPENING_ECHEST_RETURN;
            this.timer = this.containerOpenDelay.get();
            this.placementAttempts = 0;
        } else {
            for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
                ItemStack stack = this.mc.player.getInventory().getItem(i);
                if (this.isShulkerBox(stack.getItem())) {
                    if (this.debugMessages.get()) {
                        this.info("Shulker found in slot " + i + ", moving to hotbar slot " + this.shulkerHotbarSlot.get());
                    }

                    InvUtils.move().from(i).to(this.shulkerHotbarSlot.get());
                    this.timer = this.clickDelay.get() * 2;
                    IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                    if (baritone != null && baritone.getPathingBehavior().isPathing()) {
                        baritone.getPathingBehavior().cancelEverything();
                    }

                    return;
                }
            }

            if (this.stateTickCounter == 1 && this.shulkerPlacePos != null) {
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (baritone != null) {
                    baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.shulkerPlacePos));
                    if (this.debugMessages.get()) {
                        this.info("Walking to shulker item at " + this.shulkerPlacePos.toShortString());
                    }
                }
            } else if (this.stateTickCounter > 20 && this.stateTickCounter % 40 == 0 && this.shulkerPlacePos != null) {
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (baritone != null && !baritone.getPathingBehavior().isPathing()) {
                    baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.shulkerPlacePos));
                    if (this.debugMessages.get()) {
                        this.info("Re-navigating to shulker");
                    }
                }
            }

            if (this.stateTickCounter > 40 && this.stateTickCounter % 20 == 0 && this.countEmptySlots() == 0) {
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = this.mc.player.getInventory().getItem(i);
                    if (stack.getItem() == Items.FIREWORK_ROCKET && stack.getCount() >= 32) {
                        InvUtils.drop().slot(i);
                        if (this.debugMessages.get()) {
                            this.info("Dropped rockets from slot " + i + " to make room for shulker");
                        }

                        this.timer = 10;
                        return;
                    }
                }

                for (int i = 0; i < 36; i++) {
                    ItemStack stack = this.mc.player.getInventory().getItem(i);
                    if (!stack.isEmpty()
                        && !this.isShulkerBox(stack.getItem())
                        && stack.getItem() != Items.ELYTRA
                        && stack.getItem() != Items.ENDER_CHEST
                        && stack.getItem() != Items.OBSIDIAN
                        && stack.getItem() != Items.TOTEM_OF_UNDYING
                        && !stack.getComponents().has(DataComponents.FOOD)) {
                        InvUtils.drop().slot(i);
                        if (this.debugMessages.get()) {
                            this.info("Dropped " + stack.getItem() + " to make room for shulker");
                        }

                        this.timer = 10;
                        return;
                    }
                }
            }

            if (this.stateTickCounter > 120) {
                if (this.debugMessages.get()) {
                    this.warning("Shulker pickup timeout (120 ticks) - shulker not found, moving to next");
                }

                try {
                    IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                    if (baritone != null && baritone.getPathingBehavior().isPathing()) {
                        baritone.getPathingBehavior().cancelEverything();
                    }
                } catch (Exception var5) {
                }

                this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
                this.timer = 0;
            }
        }
    }

    private void handleOpeningEchestReturn() {
        if (!this.walkToEchestStand()) {
            if (this.mc.level.getBlockState(this.echestPos).getBlock() != Blocks.ENDER_CHEST) {
                if (this.debugMessages.get()) {
                    this.warning("Ender chest missing, cannot return shulker");
                }

                this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
                this.timer = 0;
            } else {
                BlockHitResult hit = this.blockHit(this.echestPos, this.mc.player.getEyePosition());
                if (hit != null && this.lookAtReal(hit.getLocation())) {
                    if (this.ecAlignTicks++ >= 1) {
                        this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hit);
                        this.mc.player.swing(InteractionHand.MAIN_HAND);
                        this.state = AutoRegear.RegearState.RETURNING_SHULKER;
                        this.transferStep = 0;
                        this.timer = this.containerOpenDelay.get();
                        if (this.debugMessages.get()) {
                            this.info("Opening ender chest to return shulker");
                        }
                    }
                } else {
                    this.ecAlignTicks = 0;
                }
            }
        }
    }

    private void handleReturningShulker() {
        if (this.mc.screen instanceof ContainerScreen screen) {
            this.returnOpenAttempts = 0;
            ChestMenu handler = screen.getMenu();
            int syncId = handler.containerId;
            if (this.transferStep == 0) {
                ItemStack hotbarStack = this.mc.player.getInventory().getItem(this.shulkerHotbarSlot.get());
                if (!this.isShulkerBox(hotbarStack.getItem())) {
                    for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
                        ItemStack stack = this.mc.player.getInventory().getItem(i);
                        if (this.isShulkerBox(stack.getItem())) {
                            if (this.debugMessages.get()) {
                                this.info("Shulker found in slot " + i + ", moving to hotbar slot " + this.shulkerHotbarSlot.get());
                            }

                            InvUtils.move().from(i).to(this.shulkerHotbarSlot.get());
                            this.timer = this.clickDelay.get() * 2;
                            return;
                        }
                    }

                    if (this.debugMessages.get()) {
                        this.warning("No shulker in inventory to return");
                    }

                    this.mc.player.closeContainer();
                    this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
                    this.timer = 0;
                    return;
                }

                int playerInvStartSlot = handler.getRowCount() * 9;
                int shulkerSlotInScreen = playerInvStartSlot + this.shulkerHotbarSlot.get() + 27;
                this.mc.gameMode.handleInventoryMouseClick(syncId, shulkerSlotInScreen, 0, ClickType.QUICK_MOVE, this.mc.player);
                if (this.debugMessages.get()) {
                    this.info("Returning shulker to ender chest via shift-click");
                }

                this.timer = this.clickDelay.get() * 2;
                this.transferStep = 1;
            } else if (this.transferStep == 1) {
                ItemStack hotbarStack = this.mc.player.getInventory().getItem(this.shulkerHotbarSlot.get());
                if (this.isShulkerBox(hotbarStack.getItem())) {
                    if (this.stateTickCounter > 60) {
                        if (this.debugMessages.get()) {
                            this.warning("Could not return shulker to ender chest (may be full), proceeding anyway");
                        }

                        this.mc.player.closeContainer();
                        this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
                        this.timer = this.clickDelay.get();
                        return;
                    }

                    this.transferStep = 0;
                    this.timer = this.clickDelay.get();
                    return;
                }

                if (this.debugMessages.get()) {
                    this.info("Shulker successfully returned to ender chest");
                }

                this.mc.player.closeContainer();
                this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
                this.timer = this.clickDelay.get();
            }
        } else {
            if (this.stateTickCounter > 40) {
                this.returnOpenAttempts++;
                if (this.returnOpenAttempts >= 3) {
                    if (this.debugMessages.get()) {
                        this.error("Failed to open ender chest for shulker return, moving on");
                    }

                    this.returnOpenAttempts = 0;
                    this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
                    this.timer = 0;
                } else {
                    if (this.debugMessages.get()) {
                        this.warning("Ender chest didn't open for return, retrying (" + this.returnOpenAttempts + "/3)");
                    }

                    this.state = AutoRegear.RegearState.OPENING_ECHEST_RETURN;
                    this.timer = this.containerOpenDelay.get();
                }
            }
        }
    }

    private void handleCheckNextShulker() {
        this.skipSatisfiedCategories();
        if (this.currentCategory() != null) {
            if (this.debugMessages.get()) {
                this.info("Continuing restock, current category: " + this.currentCategory().name().toLowerCase());
            }

            this.state = AutoRegear.RegearState.OPENING_ECHEST;
            this.timer = this.containerOpenDelay.get();
        } else {
            if (this.debugMessages.get()) {
                this.info("All restock categories complete");
            }

            this.state = AutoRegear.RegearState.RESTORING_ELYTRA;
            this.timer = 0;
        }
    }

    private AutoRegear.RegearState nextStateAfterPlatform() {
        if (this.dumpTrash.get() && this.findDumpableSlot() != -1) {
            return AutoRegear.RegearState.DUMPING_TRASH;
        } else {
            return this.createWalls.get() ? AutoRegear.RegearState.CREATING_WALLS : AutoRegear.RegearState.CLEARING_ECHEST_AREA;
        }
    }

    private void handleDumpingTrash() {
        int slot = this.findDumpableSlot();
        if (slot == -1) {
            if (this.debugMessages.get()) {
                this.info("Trash dump complete");
            }

            this.state = this.createWalls.get() ? AutoRegear.RegearState.CREATING_WALLS : AutoRegear.RegearState.CLEARING_ECHEST_AREA;
            this.timer = this.placeDelay.get();
        } else if (this.turnTowardsReal(this.mc.player.getYRot(), 0.0F)) {
            ItemStack stack = this.mc.player.getInventory().getItem(slot);
            if (this.debugMessages.get()) {
                this.info("Dumping " + stack.getHoverName().getString() + " x" + stack.getCount());
            }

            InvUtils.drop().slot(slot);
            this.timer = Math.max(this.clickDelay.get(), 2);
        }
    }

    private int findDumpableSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && this.isDumpable(stack)) {
                return i;
            }
        }

        return -1;
    }

    private boolean isDumpable(ItemStack stack) {
        Item item = stack.getItem();
        if (this.isShulkerBox(item)) {
            return false;
        }

        if (item == Items.ENDER_CHEST
            || item == Items.ELYTRA
            || item == Items.TOTEM_OF_UNDYING
            || item == Items.FIREWORK_ROCKET
            || item == Items.OBSIDIAN) {
            return false;
        }

        if (stack.has(DataComponents.FOOD)) {
            return false;
        }

        if (stack.has(DataComponents.EQUIPPABLE) || stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON)) {
            return false;
        }

        if (!stack.isEnchanted() && !stack.has(DataComponents.STORED_ENCHANTMENTS)) {
            if (stack.getRarity() != Rarity.COMMON) {
                return false;
            } else {
                return item != Items.NETHERITE_INGOT && item != Items.NETHERITE_SCRAP && item != Items.ANCIENT_DEBRIS && item != Items.NETHERITE_BLOCK
                    ? !this.keepItems.get().contains(item)
                    : false;
            }
        } else {
            return false;
        }
    }

    private Vec3 blockAimPoint(BlockPos pos, Vec3 eye) {
        BlockState blockState = this.mc.level.getBlockState(pos);
        if (blockState.isAir()) {
            return null;
        }

        VoxelShape shape = blockState.getShape(this.mc.level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos.getX(), pos.getY(), pos.getZ());
        return new Vec3(
            Mth.clamp(eye.x, box.minX, box.maxX),
            Mth.clamp(eye.y, box.minY, box.maxY),
            Mth.clamp(eye.z, box.minZ, box.maxZ)
        );
    }

    private Direction nearestFace(BlockPos pos, Vec3 point) {
        double dx = point.x - (pos.getX() + 0.5);
        double dy = point.y - (pos.getY() + 0.5);
        double dz = point.z - (pos.getZ() + 0.5);
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ax >= ay && ax >= az) {
            return dx > 0.0 ? Direction.EAST : Direction.WEST;
        } else if (az >= ay) {
            return dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            return dy > 0.0 ? Direction.UP : Direction.DOWN;
        }
    }

    private BlockHitResult blockHit(BlockPos pos, Vec3 eye) {
        Vec3 aim = this.blockAimPoint(pos, eye);
        if (aim == null) {
            return null;
        }

        BlockHitResult clip = this.mc.level.clip(new ClipContext(eye, aim, Block.OUTLINE, Fluid.NONE, this.mc.player));
        return clip.getType() == Type.BLOCK && clip.getBlockPos().equals(pos)
            ? clip
            : new BlockHitResult(aim, this.nearestFace(pos, aim), pos, false);
    }

    private boolean lookAtReal(Vec3 point) {
        float[] rot = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), point);
        return this.turnTowardsReal(rot[0], rot[1]);
    }

    private boolean turnTowardsReal(float targetYaw, float targetPitch) {
        float maxStep = (float)BepConfig.getRotationTurnSpeed();
        if (maxStep <= 0.0F) {
            maxStep = 45.0F;
        }

        float curYaw = this.mc.player.getYRot();
        float curPitch = this.mc.player.getXRot();
        float dYaw = Mth.wrapDegrees(targetYaw - curYaw);
        float dPitch = targetPitch - curPitch;
        float newYaw = curYaw + Mth.clamp(dYaw, -maxStep, maxStep);
        float newPitch = Mth.clamp(curPitch + Mth.clamp(dPitch, -maxStep, maxStep), -90.0F, 90.0F);
        this.mc.player.setYRot(newYaw);
        this.mc.player.setXRot(newPitch);
        this.mc.player.setYHeadRot(newYaw);
        return Math.abs(dYaw) <= 3.0F && Math.abs(dPitch) <= 3.0F;
    }

    private void handleRestoringElytra() {
        if (this.echestPos != null && this.mc.level.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST) {
            int shulkerCount = this.countShulkerBoxes();
            if (shulkerCount > this.sweepBaselineShulkers) {
                if (shulkerCount < this.lastSweepShulkerCount) {
                    this.leftoverSweepAttempts = 0;
                }

                this.lastSweepShulkerCount = shulkerCount;
                if (this.leftoverSweepAttempts < 3) {
                    this.leftoverSweepAttempts++;
                    if (this.debugMessages.get()) {
                        this.warning("Leftover shulker in inventory, returning to ender chest (attempt " + this.leftoverSweepAttempts + "/3)");
                    }

                    this.state = AutoRegear.RegearState.OPENING_ECHEST_RETURN;
                    this.timer = this.containerOpenDelay.get();
                    return;
                }

                if (this.debugMessages.get()) {
                    this.warning("Could not return leftover shulker to ender chest, keeping it in inventory");
                }
            }
        }

        if (this.swapToChestplate.get() && !this.savedChestplate.isEmpty()) {
            this.restoreChestplate();
            if (this.debugMessages.get()) {
                this.info("Restored elytra");
            }
        }

        this.state = AutoRegear.RegearState.CLEANUP;
        this.timer = 5;
        this.cleanupBlockIndex = 0;
    }

    private void handleCleanup() {
        if (!this.cleanupInitialized) {
            this.cleanupInitialized = true;
            if (this.debugMessages.get()) {
                this.info("Starting cleanup of all blocks above floor for flight clearance");
            }

            this.cleanupBlocks.clear();
            this.cleanupBlockAttempts = 0;
            if (this.platformCenter != null) {
                Set<BlockPos> floorBlocks = new HashSet<>();
                floorBlocks.add(this.platformCenter.immutable());
                floorBlocks.add(this.platformCenter.offset(1, 0, 0).immutable());
                floorBlocks.add(this.platformCenter.offset(0, 0, 1).immutable());
                floorBlocks.add(this.platformCenter.offset(1, 0, 1).immutable());

                for (BlockPos pos : this.placedBlocks) {
                    if (!floorBlocks.contains(pos.immutable())) {
                        BlockState blockState = this.mc.level.getBlockState(pos);
                        if (!blockState.isAir() && !blockState.canBeReplaced()) {
                            this.cleanupBlocks.add(pos.immutable());
                        }
                    }
                }

                BlockPos[] wallPositionsY1 = new BlockPos[]{
                    this.platformCenter.offset(-1, 1, 0),
                    this.platformCenter.offset(-1, 1, 1),
                    this.platformCenter.offset(0, 1, -1),
                    this.platformCenter.offset(1, 1, -1),
                    this.platformCenter.offset(2, 1, 0),
                    this.platformCenter.offset(2, 1, 1),
                    this.platformCenter.offset(0, 1, 2),
                    this.platformCenter.offset(1, 1, 2),
                    this.platformCenter.offset(0, 1, 0),
                    this.platformCenter.offset(1, 1, 0),
                    this.platformCenter.offset(0, 1, 1),
                    this.platformCenter.offset(1, 1, 1)
                };

                for (BlockPos pos : wallPositionsY1) {
                    BlockPos immutablePos = pos.immutable();
                    if (!this.cleanupBlocks.contains(immutablePos)) {
                        BlockState blockState = this.mc.level.getBlockState(pos);
                        if (!blockState.isAir() && !blockState.canBeReplaced()) {
                            this.cleanupBlocks.add(immutablePos);
                        }
                    }
                }

                for (BlockPos basePos : wallPositionsY1) {
                    BlockPos upperWall = basePos.above();
                    BlockPos immutablePos = upperWall.immutable();
                    if (!this.cleanupBlocks.contains(immutablePos)) {
                        BlockState blockState = this.mc.level.getBlockState(upperWall);
                        if (!blockState.isAir() && !blockState.canBeReplaced()) {
                            this.cleanupBlocks.add(immutablePos);
                        }
                    }
                }

                BlockPos[] roofPositions = new BlockPos[]{
                    this.platformCenter.offset(0, 3, 0),
                    this.platformCenter.offset(1, 3, 0),
                    this.platformCenter.offset(0, 3, 1),
                    this.platformCenter.offset(1, 3, 1)
                };

                for (BlockPos pos : roofPositions) {
                    BlockPos immutablePos = pos.immutable();
                    if (!this.cleanupBlocks.contains(immutablePos)) {
                        BlockState blockState = this.mc.level.getBlockState(pos);
                        if (!blockState.isAir() && !blockState.canBeReplaced()) {
                            this.cleanupBlocks.add(immutablePos);
                        }
                    }
                }
            }

            if (this.cleanupBlocks.isEmpty()) {
                if (this.debugMessages.get()) {
                    this.info("No blocks to clean up");
                }

                this.cleanupInitialized = false;
                this.state = AutoRegear.RegearState.TAKING_OFF;
                this.timer = 5;
                return;
            }

            if (this.debugMessages.get()) {
                this.info("Found " + this.cleanupBlocks.size() + " blocks to clean up for flight clearance");
            }

            this.cleanupBlockIndex = 0;
        }

        if (this.timer <= 0) {
            if (this.cleanupBlockIndex < this.cleanupBlocks.size()) {
                BlockPos pos = this.cleanupBlocks.get(this.cleanupBlockIndex);
                BlockState blockState = this.mc.level.getBlockState(pos);
                if (blockState.isAir()) {
                    if (this.debugMessages.get()) {
                        this.info("Block " + (this.cleanupBlockIndex + 1) + "/" + this.cleanupBlocks.size() + " already broken");
                    }

                    this.cleanupBlockIndex++;
                    this.cleanupBlockAttempts = 0;
                    this.timer = 1;
                    return;
                }

                if (this.cleanupBlockAttempts > 150) {
                    if (this.debugMessages.get()) {
                        this.warning("Cleanup timeout on block " + (this.cleanupBlockIndex + 1) + "/" + this.cleanupBlocks.size() + ", skipping");
                    }

                    this.cleanupBlockIndex++;
                    this.cleanupBlockAttempts = 0;
                    this.timer = 2;
                    return;
                }

                FindItemResult pickaxe = InvUtils.find(itemStack -> itemStack.is(ItemTags.PICKAXES));
                if (pickaxe.found() && pickaxe.isHotbar() && ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != pickaxe.slot()) {
                    ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(pickaxe.slot());
                }

                Vec3 playerPos = this.mc.player.position();
                Vec3 blockCenter = Vec3.atCenterOf(pos);
                double dx = playerPos.x - blockCenter.x;
                double dy = playerPos.y + this.mc.player.getEyeHeight(this.mc.player.getPose()) - blockCenter.y;
                double dz = playerPos.z - blockCenter.z;
                Direction breakDirection;
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > Math.abs(dz)) {
                    breakDirection = dx > 0.0 ? Direction.EAST : Direction.WEST;
                } else if (Math.abs(dz) > Math.abs(dy)) {
                    breakDirection = dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
                } else {
                    breakDirection = dy > 0.0 ? Direction.UP : Direction.DOWN;
                }

                if (this.cleanupBlockAttempts == 0) {
                    float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), blockCenter);
                    Direction finalBreakDirection = breakDirection;
                    Rotations.rotate(rotations[0], rotations[1], 50, () -> {
                        this.mc.gameMode.startDestroyBlock(pos, finalBreakDirection);
                        this.mc.player.swing(InteractionHand.MAIN_HAND);
                    });
                    if (this.debugMessages.get()) {
                        this.info("Started breaking block " + (this.cleanupBlockIndex + 1) + "/" + this.cleanupBlocks.size());
                    }
                } else {
                    float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), blockCenter);
                    Direction finalBreakDirection = breakDirection;
                    Rotations.rotate(rotations[0], rotations[1], 50, () -> {
                        this.mc.gameMode.continueDestroyBlock(pos, finalBreakDirection);
                        this.mc.player.swing(InteractionHand.MAIN_HAND);
                    });
                }

                this.cleanupBlockAttempts++;
                this.timer = 0;
            } else {
                if (this.debugMessages.get()) {
                    this.info("Cleanup complete, removed " + this.cleanupBlocks.size() + " blocks");
                }

                this.cleanupBlocks.clear();
                this.cleanupBlockAttempts = 0;
                this.cleanupBlockIndex = 0;
                this.cleanupInitialized = false;
                this.state = AutoRegear.RegearState.TAKING_OFF;
                this.timer = 5;
            }
        }
    }

    private void handleTakingOff() {
        if (this.stateTickCounter == 1) {
            if (this.debugMessages.get()) {
                this.info("Preparing for takeoff");
            }

            ItemStack chestItem = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
            if (chestItem.getItem() != Items.ELYTRA) {
                if (this.swapToChestplate.get() && !this.savedChestplate.isEmpty()) {
                    this.restoreChestplate();
                    if (this.debugMessages.get()) {
                        this.info("Restored elytra for takeoff");
                    }
                } else if (this.debugMessages.get()) {
                    this.warning("No elytra equipped!");
                }
            }

            this.mc.player.setYRot(this.savedYaw);
            this.mc.player.setXRot(this.savedPitch);
            if (this.debugMessages.get()) {
                this.info("Restored rotation for takeoff");
            }
        }

        if (this.stateTickCounter >= 5 && this.stateTickCounter <= 15) {
            this.mc.options.keyJump.setDown(true);
        }

        if (this.stateTickCounter == 16) {
            this.mc.options.keyJump.setDown(false);
        }

        if (this.stateTickCounter == 20) {
            this.mc.options.keyJump.setDown(true);
            if (this.debugMessages.get()) {
                this.info("Activating elytra");
            }
        }

        if (this.stateTickCounter == 21) {
            this.mc.options.keyJump.setDown(false);
            ItemStack chest = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
            if (chest.getItem() == Items.ELYTRA && this.debugMessages.get()) {
                this.info("Elytra activated");
            }
        }

        if (this.stateTickCounter == 25) {
            FindItemResult rockets = InvUtils.find(Items.FIREWORK_ROCKET);
            if (rockets.found()) {
                if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != rockets.slot()) {
                    ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(rockets.slot());
                }

                this.mc.options.keyUse.setDown(true);
            }
        }

        if (this.stateTickCounter == 26) {
            this.mc.options.keyUse.setDown(false);
            if (this.debugMessages.get()) {
                this.info("Takeoff complete");
            }
        }

        if (this.stateTickCounter > 30) {
            this.state = AutoRegear.RegearState.COMPLETE;
        }
    }

    private void handleComplete() {
        if (this.stateTickCounter == 1) {
            if (this.debugMessages.get()) {
                this.info("Auto-regear complete! Rockets: " + this.countRockets() + " Elytras: " + this.countValidElytras());
            }

            this.mc.options.keyUp.setDown(false);
            this.mc.options.keyDown.setDown(false);
            this.mc.options.keyLeft.setDown(false);
            this.mc.options.keyRight.setDown(false);
            this.mc.options.keyShift.setDown(false);
            this.mc.player.setYRot(this.savedYaw);
            this.mc.player.setXRot(this.savedPitch);
            if (this.debugMessages.get()) {
                this.info("Restored rotation: yaw=" + this.savedYaw + ", pitch=" + this.savedPitch);
            }

            this.ensureRocketsInHotbar();
        }

        if (this.autoReEnable.get()) {
            if (this.reEnableStage == 0 && this.stateTickCounter >= 5) {
                this.reEnableFlightModules();
                return;
            }

            if (this.reEnableStage == 1 && this.timer == 0) {
                this.reEnableFlightModules();
                this.timer = 5;
                return;
            }

            if (this.reEnableStage == 2 && this.timer == 0) {
                if (this.hadBaritoneGoal && this.savedBaritoneCommand != null) {
                    try {
                        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                        if (baritone != null) {
                            this.mc.player.connection.sendChat(this.savedBaritoneCommand);
                            if (this.debugMessages.get()) {
                                this.info("Restored baritone elytra mode");
                            }
                        }
                    } catch (Exception e) {
                        if (this.debugMessages.get()) {
                            this.warning("Failed to restore baritone state: " + e.getMessage());
                        }
                    }
                }

                this.reEnableStage = 3;
                this.timer = 5;
                return;
            }

            if (this.reEnableStage < 3) {
                return;
            }
        }

        if (!this.autoReEnable.get() && this.stateTickCounter == 10 && this.hadBaritoneGoal && this.savedBaritoneCommand != null) {
            try {
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (baritone != null) {
                    this.mc.player.connection.sendChat(this.savedBaritoneCommand);
                    if (this.debugMessages.get()) {
                        this.info("Restored baritone elytra mode");
                    }
                }
            } catch (Exception e) {
                if (this.debugMessages.get()) {
                    this.warning("Failed to restore baritone state: " + e.getMessage());
                }
            }
        }

        if (this.stateTickCounter == 12 && this.bepMineWasActive && this.bepMine != null && !this.bepMine.isActive()) {
            this.bepMine.toggle();
            if (this.debugMessages.get()) {
                this.info("Re-enabled BepMine");
            }
        }

        if (this.stateTickCounter > 15) {
            this.state = AutoRegear.RegearState.IDLE;
            this.placedBlocks.clear();
            this.pendingBlocks.clear();
            this.platformCenter = null;
            this.categoriesToProcess.clear();
            this.categoryIndex = 0;
            this.leftoverSweepAttempts = 0;
            this.sweepBaselineShulkers = Integer.MAX_VALUE;
            this.lastSweepShulkerCount = Integer.MAX_VALUE;
            this.timer = 0;
            this.shulkerEnderSlot = -1;
            this.transferStep = 0;
            this.transferSlotIndex = 0;
            this.placementAttempts = 0;
            this.ecAlignTicks = 0;
            this.ecOpenFailures = 0;
            this.returnOpenAttempts = 0;
            this.stateTickCounter = 0;
            this.hadBaritoneGoal = false;
            this.savedBaritoneCommand = null;
            this.reEnableStage = 0;
            this.processedShulkers.clear();
            this.bepMineWasActive = false;
            this.cleanupBlockIndex = 0;
            this.cleanupBlockAttempts = 0;
        }
    }

    private boolean shouldTriggerRegear() {
        if (this.mc.player != null && this.mc.gameMode != null) {
            GameType gameMode = this.mc.gameMode.getPlayerMode();
            if (gameMode != GameType.SURVIVAL) {
                return false;
            }

            int rockets = this.countRockets();
            int validElytras = this.countValidElytras();
            return rockets < this.minRockets.get()
                || validElytras < Math.min(this.minElytras.get(), this.goalElytras.get())
                || this.restockTotems.get()
                    && this.totemsTriggerRegear.get()
                    && this.countCategory(AutoRegear.RestockCategory.TOTEMS) < Math.min(this.minTotems.get(), this.goalTotems.get())
                || this.restockFood.get()
                    && this.foodTriggerRegear.get()
                    && this.countCategory(AutoRegear.RestockCategory.FOOD) < Math.min(this.minFood.get(), this.goalFood.get());
        } else {
            return false;
        }
    }

    private int countRockets() {
        if (this.mc.player == null) {
            return 0;
        }

        int count = 0;

        for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.FIREWORK_ROCKET) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private int countValidElytras() {
        if (this.mc.player == null) {
            return 0;
        }

        int count = 0;

        for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
            if (this.isValidElytra(this.mc.player.getInventory().getItem(i))) {
                count++;
            }
        }

        return count;
    }

    private boolean isValidElytra(ItemStack stack) {
        if (stack.getItem() != Items.ELYTRA) {
            return false;
        }

        int maxDurability = stack.getMaxDamage();
        int currentDurability = maxDurability - stack.getDamageValue();
        double percent = (double)currentDurability / maxDurability * 100.0;
        return percent >= this.elytraDurabilityThreshold.get().intValue();
    }

    private AutoRegear.RestockCategory currentCategory() {
        return this.categoryIndex < this.categoriesToProcess.size() ? this.categoriesToProcess.get(this.categoryIndex) : null;
    }

    private boolean matchesCategory(AutoRegear.RestockCategory category, ItemStack stack) {
        Item item = stack.getItem();

        return switch (category) {
            case ELYTRA -> this.isValidElytra(stack);
            case TOTEMS -> item == Items.TOTEM_OF_UNDYING;
            case FOOD -> item == this.foodItem.get();
            case ROCKETS -> item == Items.FIREWORK_ROCKET;
        };
    }

    private int countCategory(AutoRegear.RestockCategory category) {
        if (category == AutoRegear.RestockCategory.ELYTRA) {
            return this.countValidElytras();
        }

        if (this.mc.player == null) {
            return 0;
        }

        int count = 0;

        for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (this.matchesCategory(category, stack)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private int countEmptySlots() {
        int emptySlots = 0;

        for (int i = 0; i < 36; i++) {
            if (this.mc.player.getInventory().getItem(i).isEmpty()) {
                emptySlots++;
            }
        }

        return emptySlots;
    }

    private boolean isCategorySatisfied(AutoRegear.RestockCategory category) {
        return switch (category) {
            case ELYTRA -> this.countValidElytras() >= this.goalElytras.get() || this.countEmptySlots() <= 1 && !this.hasBrokenElytra();
            case TOTEMS -> this.countCategory(category) >= this.goalTotems.get() || !this.canAcceptMore(category);
            case FOOD -> this.countCategory(category) >= this.goalFood.get() || !this.canAcceptMore(category);
            case ROCKETS -> !this.canAcceptMore(category);
        };
    }

    private boolean canAcceptMore(AutoRegear.RestockCategory category) {
        if (this.countEmptySlots() > 1) {
            return true;
        }

        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getCount() < stack.getMaxStackSize() && this.matchesCategory(category, stack)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasSpaceForQuickMove(ItemStack stack) {
        if (this.countEmptySlots() > 1) {
            return true;
        }

        int partialCapacity = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack inv = this.mc.player.getInventory().getItem(i);
            if (!inv.isEmpty() && inv.getCount() < inv.getMaxStackSize() && ItemStack.isSameItemSameComponents(inv, stack)) {
                partialCapacity += inv.getMaxStackSize() - inv.getCount();
            }
        }

        return stack.getCount() <= partialCapacity;
    }

    private void skipSatisfiedCategories() {
        while (this.currentCategory() != null && this.isCategorySatisfied(this.currentCategory())) {
            this.categoryIndex++;
        }
    }

    private boolean hasBrokenElytra() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.ELYTRA && !this.isValidElytra(stack)) {
                return true;
            }
        }

        return false;
    }

    private int countShulkerBoxes() {
        int count = 0;

        for (int i = 0; i < 36; i++) {
            if (this.isShulkerBox(this.mc.player.getInventory().getItem(i).getItem())) {
                count++;
            }
        }

        return count;
    }

    private void ensureRocketsInHotbar() {
        if (this.mc.player != null) {
            int targetSlot = this.rocketHotbarSlot.get();
            ItemStack hotbarStack = this.mc.player.getInventory().getItem(targetSlot);
            if (hotbarStack.getItem() == Items.FIREWORK_ROCKET) {
                if (this.debugMessages.get()) {
                    this.info("Rockets already in hotbar slot " + targetSlot);
                }
            } else {
                int bestRocketSlot = -1;
                int bestRocketCount = 0;

                for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
                    if (i != targetSlot && i < 36) {
                        ItemStack stack = this.mc.player.getInventory().getItem(i);
                        if (stack.getItem() == Items.FIREWORK_ROCKET && stack.getCount() > bestRocketCount) {
                            bestRocketSlot = i;
                            bestRocketCount = stack.getCount();
                        }
                    }
                }

                if (bestRocketSlot != -1) {
                    InvUtils.move().from(bestRocketSlot).to(targetSlot);
                    if (this.debugMessages.get()) {
                        this.info("Moved rockets from slot " + bestRocketSlot + " to hotbar slot " + targetSlot);
                    }
                } else if (this.debugMessages.get()) {
                    this.warning("No rockets found in inventory to move to hotbar slot " + targetSlot);
                }
            }
        }
    }

    private void disableAllFlightModules() {
        for (Entry<String, Module> entry : this.flightModules.entrySet()) {
            Module module = entry.getValue();
            if (module != null && module.isActive()) {
                module.toggle();
                this.disabledModules.add(entry.getKey());
                if (this.debugMessages.get()) {
                    this.info("Disabled " + entry.getKey());
                }
            }
        }
    }

    private void reEnableFlightModules() {
        if (this.reEnableStage == 0) {
            if (this.disabledModules.contains("TrailFollower")) {
                Module module = this.flightModules.get("TrailFollower");
                if (module != null && !module.isActive()) {
                    module.toggle();
                    if (this.debugMessages.get()) {
                        this.info("Re-enabled TrailFollower (priority)");
                    }
                }
            }

            if (this.disabledModules.contains("WaypointFollower")) {
                Module module = this.flightModules.get("WaypointFollower");
                if (module != null && !module.isActive()) {
                    module.toggle();
                    if (this.debugMessages.get()) {
                        this.info("Re-enabled WaypointFollower (priority)");
                    }
                }
            }

            this.disabledModules.remove("TrailFollower");
            this.disabledModules.remove("WaypointFollower");
            this.reEnableStage = 1;
            this.timer = 5;
        } else {
            for (String moduleName : new ArrayList<>(this.disabledModules)) {
                Module module = this.flightModules.get(moduleName);
                if (module != null && !module.isActive()) {
                    module.toggle();
                    if (this.debugMessages.get()) {
                        this.info("Re-enabled " + moduleName);
                    }
                }
            }

            this.disabledModules.clear();
            this.reEnableStage = 2;
        }
    }

    private void restoreChestplate() {
        if (!this.savedChestplate.isEmpty()) {
            for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
                ItemStack stack = this.mc.player.getInventory().getItem(i);
                if (stack.getItem() == Items.ELYTRA) {
                    InvUtils.move().from(i).toArmor(2);
                    this.savedChestplate = ItemStack.EMPTY;
                    return;
                }
            }
        }
    }

    private boolean placeBlockGrim(BlockPos pos) {
        int targetHotbarSlot = this.obsidianHotbarSlot.get();
        ItemStack currentStack = this.mc.player.getInventory().getItem(targetHotbarSlot);
        if (!(currentStack.getItem() instanceof BlockItem) || currentStack.isEmpty()) {
            FindItemResult blocks = InvUtils.find(itemStack -> {
                if (itemStack.getItem() instanceof BlockItem blockItem) {
                    if (itemStack.getItem() == Items.ENDER_CHEST) {
                        return false;
                    }

                    net.minecraft.world.level.block.Block block = blockItem.getBlock();
                    return block.defaultBlockState().isRedstoneConductor(this.mc.level, pos);
                } else {
                    return false;
                }
            });
            if (!blocks.found()) {
                if (this.debugMessages.get()) {
                    this.error("No solid blocks found in inventory!");
                }

                return false;
            }

            if (blocks.slot() != targetHotbarSlot) {
                InvUtils.move().from(blocks.slot()).to(targetHotbarSlot);
                this.timer = 1;
                return false;
            }
        }

        if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != targetHotbarSlot) {
            ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(targetHotbarSlot);
        }

        Vec3 targetVec = Vec3.atCenterOf(pos);
        float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), targetVec);
        Rotations.rotate(rotations[0], rotations[1], 50, () -> {
            BlockHitResult hitResult = new BlockHitResult(targetVec, Direction.UP, pos, false);
            this.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            this.mc.player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.OFF_HAND, hitResult, this.mc.player.containerMenu.getStateId() + 2));
            this.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            this.mc.player.swing(InteractionHand.MAIN_HAND);
        });
        return true;
    }

    private boolean isShulkerBox(Item item) {
        return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    @Override
    public String getInfoString() {
        int rockets = this.countRockets();
        int elytras = this.countValidElytras();
        return rockets + "R/" + elytras + "E - " + this.state.name();
    }

    private enum RegearState {
        IDLE,
        SWAP_TO_CHESTPLATE,
        DISABLING_MODULES,
        DROPPING,
        CENTERING_ON_PLATFORM,
        CREATING_INITIAL_PLATFORM,
        DUMPING_TRASH,
        CREATING_WALLS,
        CLEARING_ECHEST_AREA,
        ROTATING_FOR_ECHEST,
        PLACING_ECHEST,
        WAIT_ECHEST_PLACE,
        OPENING_ECHEST,
        TAKING_SHULKER,
        WAIT_SHULKER_TAKEN,
        POSITIONING_FOR_SHULKER,
        ROTATING_FOR_SHULKER,
        PLACING_SHULKER,
        WAIT_SHULKER_PLACE,
        OPENING_SHULKER,
        TRANSFERRING_ITEMS,
        BREAKING_SHULKER,
        WAIT_SHULKER_BREAK,
        WAIT_SHULKER_PICKUP,
        OPENING_ECHEST_RETURN,
        RETURNING_SHULKER,
        CHECK_NEXT_SHULKER,
        RESTORING_ELYTRA,
        CLEANUP,
        TAKING_OFF,
        COMPLETE;
    }

    private enum RestockCategory {
        ELYTRA,
        TOTEMS,
        FOOD,
        ROCKETS;
    }
}
