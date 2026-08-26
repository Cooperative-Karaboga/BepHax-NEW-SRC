package bep.hax.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import bep.hax.Bep;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.modules.chesttracker.ChestTrackerDataManager;
import bep.hax.modules.chesttracker.ChestTrackerDataV2;
import bep.hax.modules.chesttracker.TrackedContainer;
import bep.hax.util.BaritoneHelper;
import bep.hax.util.ItemSignature;
import bep.hax.util.PlacementUtils;
import bep.hax.util.RotationUtils;
import bep.hax.util.ShulkerDataParser;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BlockPosSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class KitMaker extends Module {
    private final SettingGroup sgPositions = this.settings.createGroup("Positions");
    private final SettingGroup sgKit = this.settings.createGroup("Kit");
    private final SettingGroup sgBehavior = this.settings.createGroup("Behavior");
    private final SettingGroup sgDelays = this.settings.createGroup("Delays");
    private final Setting<BlockPos> inputChestPos = this.sgPositions
        .add(new Builder().name("input-chest").description("Chest holding the empty shulker boxes to fill.").defaultValue(new BlockPos(0, 64, 0)).build());
    private final Setting<BlockPos> placePos = this.sgPositions
        .add(
            new Builder()
                .name("place-pos")
                .description("Where to place the empty kit shulker so it can be filled.")
                .defaultValue(new BlockPos(0, 64, 0))
                .build()
        );
    private final Setting<BlockPos> anvilPos = this.sgPositions
        .add(new Builder().name("anvil").description("Anvil used to rename the finished kit shulker.").defaultValue(new BlockPos(0, 64, 0)).build());
    private final Setting<BlockPos> outputChestPos = this.sgPositions
        .add(
            new Builder()
                .name("output-chest")
                .description("Chest where finished, named kit shulkers are deposited.")
                .defaultValue(new BlockPos(0, 64, 0))
                .build()
        );
    private final Setting<String> kitName = this.sgKit
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("kit-name")
                .description("Name applied to the finished shulker at the anvil (costs XP).")
                .defaultValue("Kit")
                .build()
        );
    private final Setting<Integer> maxKits = this.sgBehavior
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-kits")
                .description("Stop after this many kits (0 = until input chest is empty).")
                .defaultValue(0)
                .min(0)
                .sliderRange(0, 27)
                .build()
        );
    private final Setting<Integer> maxContainerRange = this.sgBehavior
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-container-range")
                .description(
                    "Only treat indexed containers within this many blocks of the player as valid item sources (0 = unlimited). Avoids pathing across the map for a single item."
                )
                .defaultValue(1000)
                .min(0)
                .sliderRange(0, 5000)
                .build()
        );
    private final Setting<Boolean> abortOnMissing = this.sgBehavior
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("abort-on-missing-item")
                .description("Stop the whole module if a kit item can't be found in the index. Otherwise skip that item.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> debug = this.sgBehavior
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug-messages")
                .description("Verbose chat logging of each pipeline step.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> clickDelay = this.sgDelays
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("click-delay")
                .description("Ticks between inventory clicks.")
                .defaultValue(3)
                .min(0)
                .max(20)
                .build()
        );
    private final Setting<Integer> containerOpenDelay = this.sgDelays
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("container-open-delay")
                .description("Ticks to wait after opening a container.")
                .defaultValue(10)
                .min(2)
                .max(40)
                .build()
        );
    private final Setting<Integer> breakDelay = this.sgDelays
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("break-delay")
                .description("Ticks to wait after a shulker is broken before picking it up.")
                .defaultValue(4)
                .min(0)
                .max(20)
                .build()
        );
    private final Setting<Integer> navTimeout = this.sgDelays
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("nav-timeout")
                .description("Max ticks to spend pathing to a single target before giving up.")
                .defaultValue(600)
                .min(100)
                .max(2400)
                .sliderRange(200, 1200)
                .build()
        );
    private static final double REACH = 4.0;
    private final List<KitMaker.KitEntry> kit = new ArrayList<>();
    private final List<KitMaker.GatherNeed> gatherNeeds = new ArrayList<>();
    private KitMaker.State state = KitMaker.State.IDLE;
    private KitMaker.State lastState = KitMaker.State.IDLE;
    private int timer = 0;
    private int stateTicks = 0;
    private int kitsMade = 0;
    private int kitIndex = 0;
    private Item curItem = null;
    private String curSig = "";
    private int curNeed = 0;
    private boolean candidatesLoaded = false;
    private final List<BlockPos> candidates = new ArrayList<>();
    private int candidateIdx = 0;
    private BlockPos navTarget = null;
    private BlockPos sourceChestPos = null;
    private BlockPos borrowPlacePos = null;
    private int shulkerCountBefore = -1;
    private int fillIndex = 0;
    private int anvilStep = 0;
    private int openRetries = 0;
    private int placeTries = 0;
    private boolean scanArmed = false;
    private int scanTicks = 0;
    private final KitMaker.ScanHandler scanHandler = new KitMaker.ScanHandler();
    private GuiTheme guiTheme = null;
    private WVerticalList rootList = null;
    private WLabel scanStatus = null;
    private static final Color SLOT_BORDER = new Color(55, 55, 55, 255);
    private static final Color SLOT_FACE = new Color(139, 139, 139, 255);

    public KitMaker() {
        super(
            Bep.CATEGORY,
            "kit-maker",
            "Auto-builds named kit shulkers: gather indexed items (incl. from shulkers), fill an empty shulker, anvil-rename, output."
        );
    }

    @Override
    public void onActivate() {
        this.resetAll();
        this.state = KitMaker.State.IDLE;
    }

    @Override
    public void onDeactivate() {
        this.cancelBaritone();
        this.resetAll();
    }

    private void resetAll() {
        this.timer = 0;
        this.stateTicks = 0;
        this.kitsMade = 0;
        this.kitIndex = 0;
        this.curItem = null;
        this.curSig = "";
        this.curNeed = 0;
        this.resetCandidateState();
        this.navTarget = null;
        this.sourceChestPos = null;
        this.borrowPlacePos = null;
        this.shulkerCountBefore = -1;
        this.fillIndex = 0;
        this.anvilStep = 0;
        this.openRetries = 0;
        this.placeTries = 0;
        this.gatherNeeds.clear();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null && this.mc.gameMode != null && this.mc.getConnection() != null) {
            if (this.state != this.lastState) {
                this.lastState = this.state;
                this.stateTicks = 0;
            } else {
                this.stateTicks++;
            }

            if (this.timer > 0) {
                this.timer--;
            } else {
                switch (this.state) {
                    case IDLE:
                        this.handleIdle();
                        break;
                    case GATHER_NEXT:
                        this.handleGatherNext();
                        break;
                    case GATHER_NAV:
                        this.handleGatherNav();
                        break;
                    case GATHER_OPEN:
                        this.interactBlockSmooth(this.navTarget);
                        this.state = KitMaker.State.GATHER_TAKE;
                        this.timer = this.containerOpenDelay.get();
                        break;
                    case GATHER_TAKE:
                        this.handleGatherTake();
                        break;
                    case BORROW_PLACE_NAV:
                        this.handleBorrowPlaceNav();
                        break;
                    case BORROW_PLACE:
                        this.handleBorrowPlace();
                        break;
                    case BORROW_WAIT_PLACE:
                        this.handleBorrowWaitPlace();
                        break;
                    case BORROW_OPEN:
                        this.interactBlockSmooth(this.borrowPlacePos);
                        this.state = KitMaker.State.BORROW_LOOT;
                        this.timer = this.containerOpenDelay.get();
                        break;
                    case BORROW_LOOT:
                        this.handleBorrowLoot();
                        break;
                    case BORROW_BREAK:
                        this.handleBorrowBreak();
                        break;
                    case BORROW_PICKUP:
                        this.handleBorrowPickup();
                        break;
                    case BORROW_RETURN_NAV:
                        if (this.arriveAt(this.sourceChestPos)) {
                            this.state = KitMaker.State.BORROW_RETURN_OPEN;
                        }
                        break;
                    case BORROW_RETURN_OPEN:
                        this.interactBlockSmooth(this.sourceChestPos);
                        this.state = KitMaker.State.BORROW_RETURN_DEPOSIT;
                        this.timer = this.containerOpenDelay.get();
                        break;
                    case BORROW_RETURN_DEPOSIT:
                        this.handleBorrowReturnDeposit();
                        break;
                    case FETCH_NAV:
                        if (this.arriveAt(this.inputChestPos.get())) {
                            this.state = KitMaker.State.FETCH_OPEN;
                        }
                        break;
                    case FETCH_OPEN:
                        this.interactBlockSmooth(this.inputChestPos.get());
                        this.state = KitMaker.State.FETCH_TAKE;
                        this.timer = this.containerOpenDelay.get();
                        break;
                    case FETCH_TAKE:
                        this.handleFetchTake();
                        break;
                    case PLACE_NAV:
                        if (this.arriveAt(this.placePos.get())) {
                            this.state = KitMaker.State.PLACE;
                            this.timer = this.clickDelay.get();
                        }
                        break;
                    case PLACE:
                        this.handlePlace();
                        break;
                    case WAIT_PLACE:
                        this.handleWaitPlace();
                        break;
                    case FILL_NAV:
                        if (this.arriveAt(this.placePos.get())) {
                            this.state = KitMaker.State.FILL_OPEN;
                        }
                        break;
                    case FILL_OPEN:
                        this.interactBlockSmooth(this.placePos.get());
                        this.state = KitMaker.State.FILL_DEPOSIT;
                        this.fillIndex = 0;
                        this.timer = this.containerOpenDelay.get();
                        break;
                    case FILL_DEPOSIT:
                        this.handleFillDeposit();
                        break;
                    case BREAK:
                        this.handleBreak();
                        break;
                    case WAIT_PICKUP:
                        this.handleWaitPickup();
                        break;
                    case ANVIL_NAV:
                        if (this.arriveAt(this.anvilPos.get())) {
                            this.state = KitMaker.State.ANVIL_OPEN;
                        }
                        break;
                    case ANVIL_OPEN:
                        this.interactBlockSmooth(this.anvilPos.get());
                        this.state = KitMaker.State.ANVIL_RENAME;
                        this.anvilStep = 0;
                        this.timer = this.containerOpenDelay.get();
                        break;
                    case ANVIL_RENAME:
                        this.handleAnvilRename();
                        break;
                    case OUTPUT_NAV:
                        if (this.arriveAt(this.outputChestPos.get())) {
                            this.state = KitMaker.State.OUTPUT_OPEN;
                        }
                        break;
                    case OUTPUT_OPEN:
                        this.interactBlockSmooth(this.outputChestPos.get());
                        this.state = KitMaker.State.OUTPUT_DEPOSIT;
                        this.timer = this.containerOpenDelay.get();
                        break;
                    case OUTPUT_DEPOSIT:
                        this.handleOutputDeposit();
                        break;
                    case DONE:
                        this.handleDone();
                }
            }
        }
    }

    private void handleIdle() {
        if (this.kit.isEmpty()) {
            this.error("No kit preset. Open the module settings, click 'Scan shulker preset', then open a full shulker.");
            this.toggle();
        } else if (!BaritoneHelper.isAvailable()) {
            this.error("Baritone is required for KitMaker. Disabling.");
            this.toggle();
        } else {
            ChestTrackerDataV2 data = ChestTrackerDataManager.getData();
            if (data == null) {
                this.error("ChestTracker index unavailable. Disabling.");
                this.toggle();
            } else if (this.countAnyShulkerInv() > 0) {
                this.error("Remove all shulker boxes from your inventory before starting.");
                this.toggle();
            } else {
                this.buildGatherNeeds();
                boolean blocked = false;

                for (KitMaker.GatherNeed n : this.gatherNeeds) {
                    int have = this.countInvSig(n.signature());
                    if (have < n.count()) {
                        int indexed = 0;

                        for (TrackedContainer tc : data.searchSignature(n.signature())) {
                            if (this.withinRange(tc.getPosition())) {
                                indexed += tc.getSignatureCount(n.signature());
                            }
                        }

                        if (have + indexed < n.count()) {
                            if (this.abortOnMissing.get()) {
                                this.error(
                                    "Can't source "
                                        + n.label()
                                        + ": have "
                                        + have
                                        + ", indexed "
                                        + indexed
                                        + ", need "
                                        + n.count()
                                        + ". Aborting (turn off 'abort-on-missing-item' to build partial kits)."
                                );
                                blocked = true;
                            } else {
                                this.warning(
                                    "Short on "
                                        + n.label()
                                        + ": have "
                                        + have
                                        + ", indexed "
                                        + indexed
                                        + ", need "
                                        + n.count()
                                        + " - will gather what's available."
                                );
                            }
                        }
                    }
                }

                if (blocked) {
                    this.toggle();
                } else {
                    this.kitsMade = 0;
                    this.kitIndex = 0;
                    this.resetCandidateState();
                    this.info("KitMaker started." + (this.maxKits.get() > 0 ? " Target: " + this.maxKits.get() + " kits." : ""));
                    this.state = KitMaker.State.GATHER_NEXT;
                }
            }
        }
    }

    private void handleGatherNext() {
        if (this.kitIndex >= this.gatherNeeds.size()) {
            boolean haveAny = false;

            for (KitMaker.GatherNeed n : this.gatherNeeds) {
                if (this.countInvSig(n.signature()) > 0) {
                    haveAny = true;
                    break;
                }
            }

            if (!haveAny) {
                this.warning("Nothing gathered for this kit. Stopping.");
                this.state = KitMaker.State.DONE;
            } else {
                this.state = KitMaker.State.FETCH_NAV;
            }
        } else {
            KitMaker.GatherNeed n = this.gatherNeeds.get(this.kitIndex);
            this.curItem = n.item();
            this.curSig = n.signature();
            this.curNeed = n.count();
            if (this.countInvSig(this.curSig) >= this.curNeed) {
                this.advanceKitItem();
            } else {
                if (!this.candidatesLoaded) {
                    this.candidates.clear();
                    ChestTrackerDataV2 data = ChestTrackerDataManager.getData();
                    if (data != null) {
                        for (TrackedContainer tc : data.searchSignature(this.curSig)) {
                            BlockPos p = tc.getPosition();
                            if (this.withinRange(p)) {
                                this.candidates.add(p);
                            }
                        }
                    }

                    BlockPos me = this.mc.player.blockPosition();
                    this.candidates.sort(Comparator.comparingDouble(me::distSqr));
                    this.candidateIdx = 0;
                    this.candidatesLoaded = true;
                }

                if (this.candidateIdx >= this.candidates.size()) {
                    String label = this.describeEntry();
                    if (!this.abortOnMissing.get()) {
                        this.warning("Could not find " + label + " - skipping it.");
                        this.advanceKitItem();
                    } else if (this.kitsMade > 0) {
                        this.info("Out of " + label + " - made " + this.kitsMade + " kit(s). Stopping.");
                        this.state = KitMaker.State.DONE;
                    } else {
                        this.error("Could not find " + label + " in the chest index. Aborting.");
                        this.state = KitMaker.State.DONE;
                    }
                } else {
                    this.navTarget = this.candidates.get(this.candidateIdx);
                    this.state = KitMaker.State.GATHER_NAV;
                }
            }
        }
    }

    private void handleGatherNav() {
        if (this.reached(this.navTarget)) {
            this.state = KitMaker.State.GATHER_OPEN;
        } else if (this.timedOut(this.navTimeout.get())) {
            this.dbg("Path to " + this.navTarget.toShortString() + " timed out - trying next container.");
            this.cancelBaritone();
            this.nextCandidate();
            this.state = KitMaker.State.GATHER_NEXT;
        }
    }

    private void handleGatherTake() {
        if (this.timedOut(this.stuckTimeout())) {
            this.warning("Stuck pulling " + this.describeEntry() + " (inventory full?). Disabling.");
            this.toggle();
        } else {
            AbstractContainerMenu h = this.openContainerHandler();
            if (h == null) {
                if (this.timedOut(40)) {
                    this.nextCandidate();
                    this.state = KitMaker.State.GATHER_NEXT;
                }
            } else if (this.countInvSig(this.curSig) >= this.curNeed) {
                this.reindexOpenContainer(this.navTarget);
                this.mc.player.closeContainer();
                this.advanceKitItem();
            } else {
                int cont = this.containerSlotCount(h);

                for (int i = 0; i < cont; i++) {
                    if (this.curSig.equals(sigOf(h.getSlot(i).getItem()))) {
                        this.mc.gameMode.handleInventoryMouseClick(h.containerId, i, 0, ClickType.QUICK_MOVE, this.mc.player);
                        this.timer = this.clickDelay.get();
                        return;
                    }
                }

                for (int i = 0; i < cont; i++) {
                    ItemStack s = h.getSlot(i).getItem();
                    if (this.isShulkerBox(s.getItem()) && this.shulkerItemHasSig(s, this.curSig)) {
                        this.mc.gameMode.handleInventoryMouseClick(h.containerId, i, 0, ClickType.QUICK_MOVE, this.mc.player);
                        this.sourceChestPos = this.navTarget;
                        this.borrowPlacePos = null;
                        this.placeTries = 0;
                        this.reindexOpenContainer(this.navTarget);
                        this.mc.player.closeContainer();
                        this.state = KitMaker.State.BORROW_PLACE_NAV;
                        this.timer = this.clickDelay.get();
                        return;
                    }
                }

                this.reindexOpenContainer(this.navTarget);
                this.mc.player.closeContainer();
                this.nextCandidate();
                this.state = KitMaker.State.GATHER_NEXT;
            }
        }
    }

    private void handleBorrowPlaceNav() {
        if (this.borrowPlacePos == null) {
            BlockPos spot = this.findNearestPlacePos();
            if (spot == null) {
                this.warning("No safe spot to set the borrowed shulker down nearby - falling back to the configured place-pos.");
                this.borrowPlacePos = this.placePos.get();
            } else {
                this.borrowPlacePos = spot;
                this.dbg("Borrowed shulker drop spot: " + spot.toShortString());
            }
        }

        if (this.arriveAt(this.borrowPlacePos)) {
            this.state = KitMaker.State.BORROW_PLACE;
            this.timer = this.clickDelay.get();
        }
    }

    private void handleBorrowPlace() {
        if (this.mc.level.getBlockState(this.borrowPlacePos).getBlock() instanceof ShulkerBoxBlock) {
            this.state = KitMaker.State.BORROW_OPEN;
            this.timer = this.clickDelay.get();
        } else {
            FindItemResult shulker = InvUtils.find(this::isFilledShulker);
            if (!shulker.found()) {
                this.warning("Lost the borrowed shulker before placing it. Disabling.");
                this.toggle();
            } else if (++this.placeTries > 8) {
                this.warning("Could not place the borrowed shulker near " + this.borrowPlacePos.toShortString() + ". Disabling.");
                this.toggle();
            } else if (!this.ensureHotbar(shulker)) {
                this.timer = this.clickDelay.get();
            } else {
                if (!this.isValidPlaceSpot(this.borrowPlacePos)) {
                    BlockPos spot = this.findNearestPlacePos();
                    if (spot != null) {
                        this.borrowPlacePos = spot;
                    }
                }

                if (PlacementUtils.placeBlock(this.borrowPlacePos, shulker, true, true, false)) {
                    this.dbg("Placed borrowed shulker at " + this.borrowPlacePos.toShortString());
                }

                this.state = KitMaker.State.BORROW_WAIT_PLACE;
                this.timer = this.clickDelay.get();
            }
        }
    }

    private void handleBorrowWaitPlace() {
        if (this.mc.level.getBlockState(this.borrowPlacePos).getBlock() instanceof ShulkerBoxBlock) {
            this.state = KitMaker.State.BORROW_OPEN;
            this.timer = this.clickDelay.get();
        } else if (this.timedOut(40)) {
            this.state = KitMaker.State.BORROW_PLACE;
        }
    }

    private void handleBorrowLoot() {
        if (this.timedOut(this.stuckTimeout())) {
            this.warning("Stuck looting borrowed shulker. Disabling.");
            this.toggle();
        } else {
            AbstractContainerMenu h = this.openContainerHandler();
            if (h == null) {
                if (this.timedOut(40)) {
                    this.retryOpen(KitMaker.State.BORROW_OPEN);
                }
            } else {
                if (this.countInvSig(this.curSig) < this.curNeed) {
                    int cont = this.containerSlotCount(h);

                    for (int i = 0; i < cont; i++) {
                        if (this.curSig.equals(sigOf(h.getSlot(i).getItem()))) {
                            this.mc.gameMode.handleInventoryMouseClick(h.containerId, i, 0, ClickType.QUICK_MOVE, this.mc.player);
                            this.timer = this.clickDelay.get();
                            return;
                        }
                    }
                }

                this.mc.player.closeContainer();
                this.shulkerCountBefore = -1;
                this.state = KitMaker.State.BORROW_BREAK;
                this.timer = this.clickDelay.get();
            }
        }
    }

    private void handleBorrowBreak() {
        BlockPos pos = this.borrowPlacePos;
        if (this.shulkerCountBefore < 0) {
            this.shulkerCountBefore = this.countAnyShulkerInv();
        }

        if (!(this.mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) {
            this.dbg("Borrowed shulker broken.");
            this.startGoal(new GoalBlock(pos));
            this.state = KitMaker.State.BORROW_PICKUP;
            this.timer = this.breakDelay.get();
        } else {
            this.faceBlock(pos);
            if (this.stateTicks == 0) {
                this.mc.gameMode.startDestroyBlock(pos, Direction.UP);
            } else {
                this.mc.gameMode.continueDestroyBlock(pos, Direction.UP);
            }

            this.mc.player.swing(InteractionHand.MAIN_HAND);
            if (this.timedOut(400)) {
                this.warning("Could not break borrowed shulker. Disabling.");
                this.toggle();
            }
        }
    }

    private void handleBorrowPickup() {
        if (this.countAnyShulkerInv() > this.shulkerCountBefore) {
            this.cancelBaritone();
            this.dbg("Picked up borrowed shulker - returning it.");
            this.state = KitMaker.State.BORROW_RETURN_NAV;
        } else {
            if (!this.isPathing()) {
                this.startGoal(new GoalBlock(this.borrowPlacePos));
            }

            if (this.timedOut(300)) {
                this.warning("Failed to pick up the borrowed shulker. Disabling.");
                this.toggle();
            }
        }
    }

    private void handleBorrowReturnDeposit() {
        if (this.timedOut(this.stuckTimeout())) {
            this.warning("Stuck returning borrowed shulker. Disabling.");
            this.toggle();
        } else {
            AbstractContainerMenu h = this.openContainerHandler();
            if (h == null) {
                if (this.timedOut(40)) {
                    this.retryOpen(KitMaker.State.BORROW_RETURN_OPEN);
                }
            } else {
                int cont = this.containerSlotCount(h);
                int slot = this.findShulkerInPlayer(h, cont);
                if (slot == -1) {
                    this.reindexOpenContainer(this.sourceChestPos);
                    this.mc.player.closeContainer();
                    this.state = KitMaker.State.GATHER_NEXT;
                } else {
                    boolean hasRoom = false;

                    for (int i = 0; i < cont; i++) {
                        if (h.getSlot(i).getItem().isEmpty()) {
                            hasRoom = true;
                            break;
                        }
                    }

                    if (!hasRoom) {
                        this.warning("Source chest full - keeping borrowed shulker in inventory and moving on.");
                        this.mc.player.closeContainer();
                        this.nextCandidate();
                        this.state = KitMaker.State.GATHER_NEXT;
                    } else {
                        this.mc.gameMode.handleInventoryMouseClick(h.containerId, slot, 0, ClickType.QUICK_MOVE, this.mc.player);
                        this.timer = this.clickDelay.get();
                    }
                }
            }
        }
    }

    private void handleFetchTake() {
        AbstractContainerMenu h = this.openContainerHandler();
        if (h == null) {
            if (this.timedOut(40)) {
                this.retryOpen(KitMaker.State.FETCH_OPEN);
            }
        } else {
            int cont = this.containerSlotCount(h);

            for (int i = 0; i < cont; i++) {
                ItemStack s = h.getSlot(i).getItem();
                if (this.isEmptyShulker(s)) {
                    this.mc.gameMode.handleInventoryMouseClick(h.containerId, i, 0, ClickType.QUICK_MOVE, this.mc.player);
                    this.dbg("Pulled an empty shulker from input chest.");
                    this.mc.player.closeContainer();
                    this.placeTries = 0;
                    this.state = KitMaker.State.PLACE_NAV;
                    this.timer = this.clickDelay.get();
                    return;
                }
            }

            this.warning(
                "No empty shulkers in input chest - gathered items are left in your inventory (re-run once restocked). Made " + this.kitsMade + " kit(s)."
            );
            this.mc.player.closeContainer();
            this.state = KitMaker.State.DONE;
        }
    }

    private void handlePlace() {
        BlockState st = this.mc.level.getBlockState(this.placePos.get());
        if (st.getBlock() instanceof ShulkerBoxBlock) {
            this.state = KitMaker.State.WAIT_PLACE;
        } else {
            FindItemResult shulker = InvUtils.find(this::isEmptyShulker);
            if (!shulker.found()) {
                this.warning("No empty shulker in inventory to place. Disabling.");
                this.toggle();
            } else if (++this.placeTries > 8) {
                this.warning(
                    "Could not place a shulker at " + this.placePos.get().toShortString() + " - check the spot is clear with a solid block below. Disabling."
                );
                this.toggle();
            } else if (!this.ensureHotbar(shulker)) {
                this.timer = this.clickDelay.get();
            } else {
                if (PlacementUtils.placeBlock(this.placePos.get(), shulker, true, true, false)) {
                    this.dbg("Placing shulker at " + this.placePos.get().toShortString());
                }

                this.state = KitMaker.State.WAIT_PLACE;
                this.timer = this.clickDelay.get();
            }
        }
    }

    private void handleWaitPlace() {
        if (this.mc.level.getBlockState(this.placePos.get()).getBlock() instanceof ShulkerBoxBlock) {
            this.dbg("Shulker placed.");
            this.state = KitMaker.State.FILL_NAV;
        } else if (this.timedOut(40)) {
            this.state = KitMaker.State.PLACE;
        }
    }

    private void handleFillDeposit() {
        if (this.timedOut(this.stuckTimeout())) {
            this.warning("Stuck filling shulker. Disabling.");
            this.toggle();
        } else {
            AbstractContainerMenu h = this.openContainerHandler();
            if (h == null) {
                if (this.timedOut(40)) {
                    this.retryOpen(KitMaker.State.FILL_OPEN);
                }
            } else if (this.fillIndex >= this.kit.size()) {
                this.mc.player.closeContainer();
                this.dbg("Shulker filled.");
                this.shulkerCountBefore = -1;
                this.state = KitMaker.State.BREAK;
                this.timer = this.clickDelay.get();
            } else {
                int cont = this.containerSlotCount(h);
                KitMaker.KitEntry e = this.kit.get(this.fillIndex);
                int boxSlot = e.slot;
                if (boxSlot >= 0 && boxSlot < cont) {
                    String sig = e.signature;
                    ItemStack inSlot = h.getSlot(boxSlot).getItem();
                    if (!inSlot.isEmpty() && !sig.equals(ItemSignature.of(inSlot))) {
                        this.dbg("Fill slot " + boxSlot + " already holds a different item - skipping.");
                        this.fillIndex++;
                    } else {
                        int inBox = inSlot.isEmpty() ? 0 : inSlot.getCount();
                        int room = e.count - inBox;
                        if (room <= 0) {
                            this.fillIndex++;
                        } else {
                            int playerSlot = this.findSigSlot(h, cont, h.slots.size(), sig);
                            if (playerSlot == -1) {
                                this.dbg("Out of " + BuiltInRegistries.ITEM.getKey(e.item) + " for slot " + boxSlot + ".");
                                this.fillIndex++;
                            } else {
                                int playerCount = h.getSlot(playerSlot).getItem().getCount();
                                int place = Math.min(room, playerCount);
                                this.mc.gameMode.handleInventoryMouseClick(h.containerId, playerSlot, 0, ClickType.PICKUP, this.mc.player);
                                if (place >= playerCount) {
                                    this.mc.gameMode.handleInventoryMouseClick(h.containerId, boxSlot, 0, ClickType.PICKUP, this.mc.player);
                                } else {
                                    for (int k = 0; k < place; k++) {
                                        this.mc.gameMode.handleInventoryMouseClick(h.containerId, boxSlot, 1, ClickType.PICKUP, this.mc.player);
                                    }

                                    this.mc.gameMode.handleInventoryMouseClick(h.containerId, playerSlot, 0, ClickType.PICKUP, this.mc.player);
                                }

                                this.timer = this.clickDelay.get();
                            }
                        }
                    }
                } else {
                    this.fillIndex++;
                }
            }
        }
    }

    private void handleBreak() {
        BlockPos pos = this.placePos.get();
        if (this.shulkerCountBefore < 0) {
            this.shulkerCountBefore = this.countFilledShulkerInv();
        }

        if (!(this.mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) {
            this.dbg("Shulker broken.");
            this.startGoal(new GoalBlock(pos));
            this.state = KitMaker.State.WAIT_PICKUP;
            this.timer = this.breakDelay.get();
        } else {
            this.faceBlock(pos);
            if (this.stateTicks == 0) {
                this.mc.gameMode.startDestroyBlock(pos, Direction.UP);
            } else {
                this.mc.gameMode.continueDestroyBlock(pos, Direction.UP);
            }

            this.mc.player.swing(InteractionHand.MAIN_HAND);
            if (this.timedOut(400)) {
                this.warning("Could not break shulker. Disabling.");
                this.toggle();
            }
        }
    }

    private void handleWaitPickup() {
        if (this.countFilledShulkerInv() > this.shulkerCountBefore) {
            this.cancelBaritone();
            this.dbg("Picked up filled shulker.");
            this.state = KitMaker.State.ANVIL_NAV;
        } else {
            if (!this.isPathing()) {
                this.startGoal(new GoalBlock(this.placePos.get()));
            }

            if (this.timedOut(300)) {
                this.warning("Failed to pick up the broken shulker. Disabling.");
                this.toggle();
            }
        }
    }

    private void handleAnvilRename() {
        if (this.mc.player.containerMenu instanceof AnvilMenu anvil) {
            this.openRetries = 0;
            int var4 = anvil.containerId;
            switch (this.anvilStep) {
                case 0:
                    if (this.isFilledShulker(anvil.getSlot(0).getItem())) {
                        this.anvilStep = 1;
                        return;
                    }

                    int slot = this.findFilledShulkerSlot(anvil, 3, anvil.slots.size());
                    if (slot == -1) {
                        this.warning("No filled shulker to rename - skipping anvil.");
                        this.mc.player.closeContainer();
                        this.finishKit();
                        return;
                    }

                    this.mc.gameMode.handleInventoryMouseClick(var4, slot, 0, ClickType.QUICK_MOVE, this.mc.player);
                    this.timer = this.clickDelay.get();
                    this.anvilStep = 1;
                    break;
                case 1:
                    if (!this.isFilledShulker(anvil.getSlot(0).getItem())) {
                        if (this.timedOut(60)) {
                            this.anvilStep = 3;
                        }

                        return;
                    }

                    if (this.mc.player.experienceLevel < 1 && !this.mc.player.getAbilities().instabuild) {
                        this.warning("No XP for the anvil rename - outputting the kit un-renamed.");
                        this.mc.gameMode.handleInventoryMouseClick(var4, 0, 0, ClickType.QUICK_MOVE, this.mc.player);
                        this.timer = this.clickDelay.get();
                        this.anvilStep = 3;
                        return;
                    }

                    if (!this.kitName.get().isEmpty()) {
                        this.mc.getConnection().send(new ServerboundRenameItemPacket(this.kitName.get()));
                        this.dbg("Sent rename: " + this.kitName.get());
                    }

                    this.timer = this.clickDelay.get() * 2;
                    this.anvilStep = 2;
                    break;
                case 2:
                    if (!anvil.getSlot(2).getItem().isEmpty()) {
                        this.mc.gameMode.handleInventoryMouseClick(var4, 2, 0, ClickType.QUICK_MOVE, this.mc.player);
                    } else {
                        this.warning("Anvil produced no output (no XP / too expensive). Outputting shulker un-renamed.");
                        if (this.isFilledShulker(anvil.getSlot(0).getItem())) {
                            this.mc.gameMode.handleInventoryMouseClick(var4, 0, 0, ClickType.QUICK_MOVE, this.mc.player);
                        }
                    }

                    this.timer = this.clickDelay.get();
                    this.anvilStep = 3;
                    break;
                case 3:
                    this.mc.player.closeContainer();
                    this.finishKit();
            }
        } else {
            if (this.timedOut(40)) {
                this.retryOpen(KitMaker.State.ANVIL_OPEN);
            }
        }
    }

    private void handleOutputDeposit() {
        if (this.timedOut(this.stuckTimeout())) {
            this.warning("Stuck depositing to output. Disabling.");
            this.toggle();
        } else {
            AbstractContainerMenu h = this.openContainerHandler();
            if (h == null) {
                if (this.timedOut(40)) {
                    this.retryOpen(KitMaker.State.OUTPUT_OPEN);
                }
            } else {
                int cont = this.containerSlotCount(h);
                int slot = this.findFilledShulkerSlot(h, cont, h.slots.size());
                if (slot == -1) {
                    this.mc.player.closeContainer();
                    this.dbg("Kit deposited to output. (" + this.kitsMade + " total)");
                    if (this.maxKits.get() > 0 && this.kitsMade >= this.maxKits.get()) {
                        this.info("Reached target of " + this.maxKits.get() + " kits.");
                        this.state = KitMaker.State.DONE;
                    } else {
                        this.kitIndex = 0;
                        this.resetCandidateState();
                        this.state = KitMaker.State.GATHER_NEXT;
                    }
                } else {
                    boolean hasRoom = false;

                    for (int i = 0; i < cont; i++) {
                        if (h.getSlot(i).getItem().isEmpty()) {
                            hasRoom = true;
                            break;
                        }
                    }

                    if (!hasRoom) {
                        this.warning("Output chest is full. Stopping.");
                        this.mc.player.closeContainer();
                        this.state = KitMaker.State.DONE;
                    } else {
                        this.mc.gameMode.handleInventoryMouseClick(h.containerId, slot, 0, ClickType.QUICK_MOVE, this.mc.player);
                        this.timer = this.clickDelay.get();
                    }
                }
            }
        }
    }

    private void handleDone() {
        this.cancelBaritone();
        this.info("KitMaker finished. Made " + this.kitsMade + " kit(s).");
        this.toggle();
    }

    private void advanceKitItem() {
        this.kitIndex++;
        this.resetCandidateState();
        this.state = KitMaker.State.GATHER_NEXT;
    }

    private void nextCandidate() {
        this.candidateIdx++;
    }

    private void resetCandidateState() {
        this.candidatesLoaded = false;
        this.candidates.clear();
        this.candidateIdx = 0;
    }

    private void finishKit() {
        this.kitsMade++;
        this.state = KitMaker.State.OUTPUT_NAV;
    }

    private String describeEntry() {
        if (this.kitIndex < this.gatherNeeds.size()) {
            return this.gatherNeeds.get(this.kitIndex).label();
        } else {
            return this.curItem != null ? BuiltInRegistries.ITEM.getKey(this.curItem).toString() : "item";
        }
    }

    private String describe(KitMaker.KitEntry e) {
        String id = BuiltInRegistries.ITEM.getKey(e.item).getPath();
        return e.enchants.isEmpty() ? id : id + " (enchanted)";
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        this.guiTheme = theme;
        this.rootList = theme.verticalList();
        this.buildWidget();
        return this.rootList;
    }

    private void buildWidget() {
        if (this.rootList != null && this.guiTheme != null) {
            GuiTheme theme = this.guiTheme;
            this.rootList.clear();
            WHorizontalList top = this.rootList.add(theme.horizontalList()).expandX().widget();
            WButton scan = top.add(theme.button(this.scanArmed ? "Scanning... open a shulker" : "Scan shulker preset")).widget();
            this.scanStatus = top.add(theme.label(this.scanArmed ? "Open a shulker now" : this.presetSummary())).widget();
            scan.action = () -> {
                if (this.scanArmed) {
                    this.disarmScan();
                } else {
                    this.armScan();
                }

                this.buildWidget();
            };
            if (this.kit.isEmpty()) {
                this.rootList.add(theme.label("No preset yet - click Scan, then open a full shulker to copy its contents."));
            } else {
                this.rootList.add(new KitMaker.WKitPreview());
            }

            this.rootList.invalidate();
        }
    }

    private ItemStack buildDisplayStack(KitMaker.KitEntry e) {
        ItemStack ds = e.sample != null ? e.sample.copy() : new ItemStack(e.item);
        ds.setCount(Math.max(1, e.count));
        return ds;
    }

    private String tooltipFor(KitMaker.KitEntry e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.count).append("x ").append(BuiltInRegistries.ITEM.getKey(e.item).getPath());

        for (KitMaker.EnchPair p : e.enchants) {
            sb.append(", ").append(p.id().replace("minecraft:", "")).append(" ").append(p.level());
        }

        return sb.toString();
    }

    private String presetSummary() {
        return this.kit.isEmpty() ? "No preset" : "Preset: " + this.kit.size() + " slot(s)";
    }

    private void armScan() {
        if (!this.scanArmed) {
            this.scanArmed = true;
            this.scanTicks = 0;
            MeteorClient.EVENT_BUS.subscribe(this.scanHandler);
            this.info("Scan armed - open the shulker you want to copy as the kit preset.");
        }
    }

    private void disarmScan() {
        if (this.scanArmed) {
            this.scanArmed = false;
            MeteorClient.EVENT_BUS.unsubscribe(this.scanHandler);
        }
    }

    private void tickScan() {
        if (this.scanArmed && this.mc.player != null) {
            if (this.mc.player.containerMenu instanceof ShulkerBoxMenu ssh) {
                this.scanTicks++;
                if (this.scanTicks < 4) {
                    return;
                }

                List<KitMaker.KitEntry> scanned = new ArrayList<>();
                int cont = Math.min(27, ssh.slots.size());

                for (int i = 0; i < cont; i++) {
                    ItemStack s = ssh.getSlot(i).getItem();
                    if (!s.isEmpty()) {
                        scanned.add(this.makeEntry(i, s.copy(), s.getCount()));
                    }
                }

                if (!scanned.isEmpty()) {
                    this.kit.clear();
                    this.kit.addAll(scanned);
                    this.disarmScan();
                    this.buildWidget();
                    this.info("Captured kit preset: " + this.kit.size() + " slot(s).");
                } else if (this.scanTicks > 20) {
                    this.disarmScan();
                    this.buildWidget();
                    this.warning("That shulker is empty - kit preset unchanged.");
                }
            } else {
                this.scanTicks = 0;
            }
        }
    }

    private KitMaker.KitEntry makeEntry(int slot, ItemStack rep, int count) {
        return new KitMaker.KitEntry(slot, rep.getItem(), count, readEnchants(rep), ItemSignature.of(rep), rep.copy());
    }

    private static List<KitMaker.EnchPair> readEnchants(ItemStack s) {
        ItemEnchantments ench = s.getItem() == Items.ENCHANTED_BOOK ? s.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY) : s.getEnchantments();
        List<KitMaker.EnchPair> out = new ArrayList<>();

        for (Entry<Holder<Enchantment>> e : ench.entrySet()) {
            String id = e.getKey().getRegisteredName();
            if (id != null && !id.isEmpty()) {
                out.add(new KitMaker.EnchPair(id, e.getIntValue()));
            }
        }

        return out;
    }

    private static List<String> enchPartsOf(List<KitMaker.EnchPair> enchants) {
        List<String> parts = new ArrayList<>();

        for (KitMaker.EnchPair p : enchants) {
            parts.add(p.id() + "=" + p.level());
        }

        return parts;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();
        ListTag preset = new ListTag();

        for (KitMaker.KitEntry e : this.kit) {
            CompoundTag c = new CompoundTag();
            c.putString("id", BuiltInRegistries.ITEM.getKey(e.item).toString());
            c.putInt("count", e.count);
            c.putInt("slot", e.slot);
            ListTag ench = new ListTag();

            for (KitMaker.EnchPair p : e.enchants) {
                CompoundTag ec = new CompoundTag();
                ec.putString("id", p.id());
                ec.putInt("lvl", p.level());
                ench.add(ec);
            }

            c.put("ench", ench);
            preset.add(c);
        }

        tag.put("preset", preset);
        return tag;
    }

    @Override
    public Module fromTag(CompoundTag tag) {
        super.fromTag(tag);
        this.kit.clear();
        tag.getList("preset").ifPresent(preset -> {
            for (int i = 0; i < preset.size(); i++) {
                int idx = i;
                preset.getCompound(i).ifPresent(c -> {
                    Identifier id = Identifier.tryParse(c.getString("id").orElse(""));
                    int count = c.getInt("count").orElse(0);
                    int slot = c.getInt("slot").orElse(idx);
                    if (id != null && count > 0) {
                        Item item = BuiltInRegistries.ITEM.getValue(id);
                        if (item != Items.AIR) {
                            List<KitMaker.EnchPair> enchants = new ArrayList<>();
                            c.getList("ench").ifPresent(el -> {
                                for (int j = 0; j < el.size(); j++) {
                                    el.getCompound(j).ifPresent(ec -> {
                                        String eid = ec.getString("id").orElse("");
                                        int lvl = ec.getInt("lvl").orElse(0);
                                        if (!eid.isEmpty() && lvl > 0) {
                                            enchants.add(new KitMaker.EnchPair(eid, lvl));
                                        }
                                    });
                                }
                            });
                            String sig = ItemSignature.format(id.toString(), enchPartsOf(enchants));
                            this.kit.add(new KitMaker.KitEntry(slot, item, count, enchants, sig, null));
                        }
                    }
                });
            }
        });
        return this;
    }

    private boolean isShulkerBox(Item item) {
        return item instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean isEmptyShulker(ItemStack s) {
        if (!this.isShulkerBox(s.getItem())) {
            return false;
        }

        ItemContainerContents c = s.get(DataComponents.CONTAINER);
        return c == null || !c.nonEmptyItems().iterator().hasNext();
    }

    private boolean isFilledShulker(ItemStack s) {
        if (!this.isShulkerBox(s.getItem())) {
            return false;
        }

        ItemContainerContents c = s.get(DataComponents.CONTAINER);
        return c != null && c.nonEmptyItems().iterator().hasNext();
    }

    private boolean shulkerItemHasSig(ItemStack box, String sig) {
        for (ItemStack nested : ShulkerDataParser.parseShulkerContentsAsList(box)) {
            if (!nested.isEmpty() && sig.equals(ItemSignature.of(nested))) {
                return true;
            }
        }

        return false;
    }

    private static String sigOf(ItemStack s) {
        return s != null && !s.isEmpty() ? ItemSignature.of(s) : "";
    }

    private int countInvSig(String sig) {
        int total = 0;

        for (ItemStack s : ((PlayerInventoryAccessor)this.mc.player.getInventory()).getMain()) {
            if (!s.isEmpty() && sig.equals(ItemSignature.of(s))) {
                total += s.getCount();
            }
        }

        return total;
    }

    private int countAnyShulkerInv() {
        int n = 0;

        for (ItemStack s : ((PlayerInventoryAccessor)this.mc.player.getInventory()).getMain()) {
            if (this.isShulkerBox(s.getItem())) {
                n += s.getCount();
            }
        }

        return n;
    }

    private int countFilledShulkerInv() {
        int n = 0;

        for (ItemStack s : ((PlayerInventoryAccessor)this.mc.player.getInventory()).getMain()) {
            if (this.isFilledShulker(s)) {
                n += s.getCount();
            }
        }

        return n;
    }

    private boolean ensureHotbar(FindItemResult item) {
        if (item.getHand() == null && !item.isHotbar()) {
            int hotbar = ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot();
            this.mc.gameMode.handleInventoryMouseClick(this.mc.player.inventoryMenu.containerId, item.slot(), hotbar, ClickType.SWAP, this.mc.player);
            return false;
        } else {
            return true;
        }
    }

    private int findSigSlot(AbstractContainerMenu h, int from, int to, String sig) {
        for (int i = from; i < to; i++) {
            ItemStack s = h.getSlot(i).getItem();
            if (!s.isEmpty() && sig.equals(ItemSignature.of(s))) {
                return i;
            }
        }

        return -1;
    }

    private int findFilledShulkerSlot(AbstractContainerMenu h, int from, int to) {
        for (int i = from; i < to; i++) {
            if (this.isFilledShulker(h.getSlot(i).getItem())) {
                return i;
            }
        }

        return -1;
    }

    private int findShulkerInPlayer(AbstractContainerMenu h, int cont) {
        for (int i = cont; i < h.slots.size(); i++) {
            if (this.isShulkerBox(h.getSlot(i).getItem().getItem())) {
                return i;
            }
        }

        return -1;
    }

    private AbstractContainerMenu openContainerHandler() {
        if (!(this.mc.screen instanceof AbstractContainerScreen)) {
            return null;
        } else {
            AbstractContainerMenu h = this.mc.player.containerMenu;
            if (h != null && h != this.mc.player.inventoryMenu && h.slots.size() >= 37) {
                this.openRetries = 0;
                return h;
            } else {
                return null;
            }
        }
    }

    private int containerSlotCount(AbstractContainerMenu h) {
        return h.slots.size() - 36;
    }

    private void reindexOpenContainer(BlockPos pos) {
        if (pos != null && this.mc.player != null && this.mc.level != null) {
            ChestTrackerDataV2 data = ChestTrackerDataManager.getData();
            if (data != null) {
                TrackedContainer tc = data.getContainer(pos, this.dimension());
                if (tc != null) {
                    AbstractContainerMenu h = this.mc.player.containerMenu;
                    if (h != null && h.slots.size() >= 37) {
                        int cont = this.containerSlotCount(h);
                        List<ItemStack> contents = new ArrayList<>();

                        for (int i = 0; i < cont; i++) {
                            ItemStack s = h.getSlot(i).getItem();
                            if (!s.isEmpty()) {
                                contents.add(s.copy());
                            }
                        }

                        tc.updateContents(contents);
                    }
                }
            }
        }
    }

    private String dimension() {
        return this.mc.level.dimension().identifier().toString();
    }

    private void buildGatherNeeds() {
        this.gatherNeeds.clear();
        LinkedHashMap<String, int[]> sums = new LinkedHashMap<>();
        Map<String, Item> items = new HashMap<>();
        Map<String, String> labels = new HashMap<>();

        for (KitMaker.KitEntry e : this.kit) {
            sums.computeIfAbsent(e.signature, k -> new int[1])[0] += e.count;
            items.putIfAbsent(e.signature, e.item);
            labels.putIfAbsent(e.signature, this.describe(e));
        }

        for (java.util.Map.Entry<String, int[]> en : sums.entrySet()) {
            this.gatherNeeds.add(new KitMaker.GatherNeed(items.get(en.getKey()), en.getKey(), en.getValue()[0], labels.get(en.getKey())));
        }
    }

    private boolean withinRange(BlockPos pos) {
        int r = this.maxContainerRange.get();
        return r <= 0 ? true : this.mc.player.blockPosition().distSqr(pos) <= (double)r * r;
    }

    private BlockPos findNearestPlacePos() {
        BlockPos origin = this.mc.player.blockPosition();
        Vec3 eye = this.mc.player.getEyePosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 2; dy >= -2; dy--) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (this.isValidPlaceSpot(pos)) {
                        double d = eye.distanceToSqr(Vec3.atCenterOf(pos));
                        if (d <= 16.0 && d < bestDist) {
                            bestDist = d;
                            best = pos;
                        }
                    }
                }
            }
        }

        return best;
    }

    private boolean isValidPlaceSpot(BlockPos pos) {
        if (this.mc.level != null && this.mc.player != null) {
            BlockPos feet = this.mc.player.blockPosition();
            if (pos.equals(feet) || pos.equals(feet.above()) || pos.equals(feet.below())) {
                return false;
            } else if (!this.mc.level.getBlockState(pos).canBeReplaced()) {
                return false;
            } else if (!this.mc.level.getBlockState(pos.above()).canBeReplaced()) {
                return false;
            } else {
                return this.mc.level.getBlockState(pos.below()).canBeReplaced() ? false : PlacementUtils.canPlace(pos, true);
            }
        } else {
            return false;
        }
    }

    private void interactBlockSmooth(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 v = center.subtract(this.mc.player.getEyePosition());
        double hd = Math.sqrt(v.x * v.x + v.z * v.z);
        float yaw = (float)Math.toDegrees(Math.atan2(-v.x, v.z));
        float pitch = (float)Math.toDegrees(Math.atan2(-v.y, hd));
        Rotations.rotate(yaw, pitch, 50, () -> {
            BlockHitResult hit = new BlockHitResult(center, Direction.UP, pos, false);
            this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hit);
            this.mc.player.swing(InteractionHand.MAIN_HAND);
        });
    }

    private void faceBlock(BlockPos pos) {
        float[] rot = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), Vec3.atCenterOf(pos));
        this.mc.player.setYRot(rot[0]);
        this.mc.player.setXRot(rot[1]);
    }

    private boolean reached(BlockPos target) {
        if (target == null) {
            return true;
        }

        IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (b == null) {
            return true;
        }

        double dist = this.mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(target));
        if (dist <= 4.0) {
            this.cancelBaritone();
            return true;
        }

        if (!b.getPathingBehavior().isPathing() && !b.getCustomGoalProcess().isActive()) {
            b.getCustomGoalProcess().setGoalAndPath(new GoalNear(target, 2));
        }

        return false;
    }

    private boolean arriveAt(BlockPos target) {
        if (this.reached(target)) {
            return true;
        }

        if (this.timedOut(this.navTimeout.get())) {
            this.warning("Could not reach " + target.toShortString() + ". Disabling.");
            this.toggle();
        }

        return false;
    }

    private void startGoal(GoalBlock goal) {
        IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (b != null) {
            b.getCustomGoalProcess().setGoalAndPath(goal);
        }
    }

    private boolean isPathing() {
        IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
        return b != null && (b.getPathingBehavior().isPathing() || b.getCustomGoalProcess().isActive());
    }

    private void cancelBaritone() {
        try {
            IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (b != null && (b.getPathingBehavior().isPathing() || b.getCustomGoalProcess().isActive())) {
                b.getPathingBehavior().cancelEverything();
            }
        } catch (Exception var2) {
        }
    }

    private void retryOpen(KitMaker.State openState) {
        if (++this.openRetries > 8) {
            this.warning("Container won't open (block gone?). Stopping.");
            this.state = KitMaker.State.DONE;
        } else {
            if (this.mc.screen != null) {
                this.mc.player.closeContainer();
            }

            this.state = openState;
            this.timer = this.clickDelay.get();
        }
    }

    private boolean timedOut(int ticks) {
        return this.stateTicks > ticks;
    }

    private int stuckTimeout() {
        return 200 + this.clickDelay.get() * 30;
    }

    private void dbg(String msg) {
        if (this.debug.get()) {
            this.info(msg);
        }
    }

    private record EnchPair(String id, int level) {
    }

    private record GatherNeed(Item item, String signature, int count, String label) {
    }

    private static final class KitEntry {
        final int slot;
        final Item item;
        final int count;
        final List<KitMaker.EnchPair> enchants;
        final String signature;
        final ItemStack sample;

        KitEntry(int slot, Item item, int count, List<KitMaker.EnchPair> enchants, String signature, ItemStack sample) {
            this.slot = slot;
            this.item = item;
            this.count = count;
            this.enchants = enchants;
            this.signature = signature;
            this.sample = sample;
        }
    }

    private class ScanHandler {
        @EventHandler
        private void onTick(Post event) {
            KitMaker.this.tickScan();
        }
    }

    private enum State {
        IDLE,
        GATHER_NEXT,
        GATHER_NAV,
        GATHER_OPEN,
        GATHER_TAKE,
        BORROW_PLACE_NAV,
        BORROW_PLACE,
        BORROW_WAIT_PLACE,
        BORROW_OPEN,
        BORROW_LOOT,
        BORROW_BREAK,
        BORROW_PICKUP,
        BORROW_RETURN_NAV,
        BORROW_RETURN_OPEN,
        BORROW_RETURN_DEPOSIT,
        FETCH_NAV,
        FETCH_OPEN,
        FETCH_TAKE,
        PLACE_NAV,
        PLACE,
        WAIT_PLACE,
        FILL_NAV,
        FILL_OPEN,
        FILL_DEPOSIT,
        BREAK,
        WAIT_PICKUP,
        ANVIL_NAV,
        ANVIL_OPEN,
        ANVIL_RENAME,
        OUTPUT_NAV,
        OUTPUT_OPEN,
        OUTPUT_DEPOSIT,
        DONE;
    }

    private class WKitPreview extends WWidget {
        private static final int SLOT = 18;

        @Override
        protected void onCalculateSize() {
            this.width = this.theme.scale(162.0);
            this.height = this.theme.scale(54.0);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            KitMaker.KitEntry[] map = new KitMaker.KitEntry[27];

            for (KitMaker.KitEntry e : KitMaker.this.kit) {
                if (e.slot >= 0 && e.slot < 27) {
                    map[e.slot] = e;
                }
            }

            double cell = this.theme.scale(18.0);
            double pad = this.theme.scale(1.0);
            float itemScale = (float)this.theme.scale(1.0);
            String hover = null;

            for (int i = 0; i < 27; i++) {
                double sx = this.x + i % 9 * cell;
                double sy = this.y + i / 9 * cell;
                renderer.quad(sx, sy, cell, cell, KitMaker.SLOT_BORDER);
                renderer.quad(sx + pad, sy + pad, cell - pad * 2.0, cell - pad * 2.0, KitMaker.SLOT_FACE);
                KitMaker.KitEntry e = map[i];
                if (e != null) {
                    ItemStack stack = KitMaker.this.buildDisplayStack(e);
                    int ix = (int)(sx + pad);
                    int iy = (int)(sy + pad);
                    renderer.post(() -> renderer.item(stack, ix, iy, itemScale, true));
                    if (mouseX >= sx && mouseX < sx + cell && mouseY >= sy && mouseY < sy + cell) {
                        hover = KitMaker.this.tooltipFor(e);
                    }
                }
            }

            this.tooltip = hover;
        }
    }
}
