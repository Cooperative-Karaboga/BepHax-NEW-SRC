package bep.hax.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import bep.hax.Bep;
import bep.hax.config.BepConfig;
import bep.hax.util.RotationUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BlockPosSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PearlLoader extends Module {
    private static final int MAX_INTERACT_RETRIES = 3;
    private static final int ROTATE_TIMEOUT_TICKS = 30;
    private static final int VERIFY_TICKS = 4;
    private static final int MAX_REAPPROACHES = 3;
    private static final int MAX_CLOSE_RETRIES = 2;
    private static final int MAX_REVERT_REDIRECTS = 2;
    private static final int MAX_LEG_REPATHS = 3;
    private static final int PATH_GRACE_TICKS = 20;
    private static final double MAX_INTERACT_DISTANCE = 4.25;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgCoordinates = this.settings.createGroup("Coordinates");
    private final SettingGroup sgWhitelist = this.settings.createGroup("Whitelist");
    private final SettingGroup sgPearlDrop = this.settings.createGroup("Pearl Drop");
    private final Setting<BlockPos> walkPoint1 = this.sgCoordinates
        .add(
            new Builder()
                .name("Walk Point 1")
                .description("First position for anti-AFK walking loop")
                .defaultValue(new BlockPos(0, 64, 0))
                .visible(() -> !BepConfig.streamerMode.get())
                .build()
        );
    private final Setting<BlockPos> walkPoint2 = this.sgCoordinates
        .add(
            new Builder()
                .name("Walk Point 2")
                .description("Second position for anti-AFK walking loop")
                .defaultValue(new BlockPos(10, 64, 0))
                .visible(() -> !BepConfig.streamerMode.get())
                .build()
        );
    private final Setting<Boolean> useWhitelist = this.sgWhitelist
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("Use Whitelist")
                .description("Only accept triggers from whitelisted players")
                .defaultValue(true)
                .build()
        );
    private final Setting<List<String>> whitelistedPlayers = this.sgWhitelist
        .add(
            new meteordevelopment.meteorclient.settings.StringListSetting.Builder()
                .name("Whitelisted Players")
                .description("Players who can trigger pearl loading")
                .defaultValue(new ArrayList<>())
                .visible(() -> false)
                .build()
        );
    private final Setting<Boolean> dropPearlOnLoad = this.sgPearlDrop
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("Drop Pearl To Player")
                .description("After every load, face the triggering player and toss an ender pearl from your inventory so they can re-arm the stasis")
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> reachThreshold = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("Reach Threshold")
                .description("Distance threshold for considering a position reached")
                .defaultValue(1.0)
                .min(0.1)
                .max(5.0)
                .sliderRange(0.1, 5.0)
                .build()
        );
    private final Setting<Integer> coverStandHeight = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("Cover Stand Height")
                .description("When a load location uses a cover trapdoor, stand this many blocks higher so your body keeps the cover trapdoor closed")
                .defaultValue(1)
                .min(0)
                .max(5)
                .sliderRange(0, 5)
                .build()
        );
    private final Setting<Integer> arrivalWaitTicks = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("Arrival Wait Ticks")
                .description("Ticks to settle after arriving at a position before acting")
                .defaultValue(19)
                .min(0)
                .max(100)
                .sliderRange(0, 100)
                .build()
        );
    private final Setting<Integer> sequenceTimeout = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("Sequence Timeout")
                .description("Seconds without progress in one step before a stalled load sequence aborts back to the anti-AFK loop")
                .defaultValue(30)
                .min(10)
                .max(120)
                .sliderRange(10, 120)
                .build()
        );
    private final Setting<Boolean> debugMode = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("Debug Mode")
                .description("Show detailed debug information")
                .defaultValue(false)
                .visible(() -> false)
                .build()
        );
    private List<PearlLoader.LoadLocation> loadLocations = new ArrayList<>();
    private PearlLoader.State currentState = PearlLoader.State.WALK_LOOP_2;
    private PearlLoader.State lastState = null;
    private boolean isActive = false;
    private boolean pearlLoadTriggered = false;
    private BlockPos currentTarget = null;
    private PearlLoader.LoadLocation currentLoadLocation = null;
    private String currentTriggerSender = null;
    private int stateTicks = 0;
    private int sequenceTicks = 0;
    private PearlLoader.IPhase ipPhase = PearlLoader.IPhase.ROTATE;
    private int ipTicks = 0;
    private int ipRetries = 0;
    private int reApproaches = 0;
    private int closeRetries = 0;
    private int revertRedirects = 0;
    private PearlLoader.State coverOpenReturn = PearlLoader.State.LOAD_TRIGGER;
    private int legRepaths = 0;
    private int pathGrace = 0;
    private PearlLoader.State resumeState = null;
    private BlockPos approachTarget = null;
    private BlockPos resolvedLoadPos = null;
    private final ArrayDeque<PearlLoader.PendingTrigger> pendingTriggers = new ArrayDeque<>();
    private GuiTheme guiTheme;
    private WVerticalList settingsList;

    public PearlLoader() {
        super(Bep.CATEGORY, "PearlLoader", "Anti-AFK loop with pearl loading capability");
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();
        ListTag locationsList = new ListTag();

        for (PearlLoader.LoadLocation location : this.loadLocations) {
            locationsList.add(location.toTag());
        }

        tag.put("loadLocations", locationsList);
        return tag;
    }

    @Override
    public Module fromTag(CompoundTag tag) {
        super.fromTag(tag);
        this.loadLocations.clear();
        if (tag.contains("loadLocations")) {
            Optional<ListTag> locationsListOpt = tag.getList("loadLocations");
            if (locationsListOpt.isPresent()) {
                ListTag locationsList = locationsListOpt.get();

                for (int i = 0; i < locationsList.size(); i++) {
                    Optional<CompoundTag> compoundOpt = locationsList.getCompound(i);
                    if (compoundOpt.isPresent()) {
                        PearlLoader.LoadLocation location = new PearlLoader.LoadLocation();
                        location.fromTag(compoundOpt.get());
                        this.loadLocations.add(location);
                    }
                }
            }
        }

        return this;
    }

    @Override
    public void onActivate() {
        this.isActive = true;
        this.pearlLoadTriggered = false;
        this.currentLoadLocation = null;
        this.currentTriggerSender = null;
        this.sequenceTicks = 0;
        this.legRepaths = 0;
        this.pathGrace = 0;
        this.resumeState = null;
        this.approachTarget = null;
        this.pendingTriggers.clear();
        this.currentState = PearlLoader.State.WALK_LOOP_2;
        this.lastState = null;
        this.currentTarget = this.walkPoint2.get();
        this.startPathing(this.currentTarget);
        this.info("Pearl Loader activated - Starting anti-AFK loop");
    }

    @Override
    public void onDeactivate() {
        this.isActive = false;
        this.pearlLoadTriggered = false;
        this.pendingTriggers.clear();
        this.stopPathing();
        RotationUtils.getInstance().clearRotations();
        this.info("Pearl Loader deactivated");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        this.tryTriggerFromChat(event.getMessage().getString());
    }

    public void tryTriggerFromChat(String fullMessage) {
        if (this.isActive) {
            String messageLower = fullMessage.toLowerCase();

            for (PearlLoader.LoadLocation location : this.loadLocations) {
                String keyword = location.triggerKeyword.toLowerCase();
                if (!keyword.isEmpty() && messageLower.contains(keyword)) {
                    String sender = this.extractSenderName(fullMessage);
                    if (!this.useWhitelist.get() || sender != null && this.isPlayerWhitelisted(sender)) {
                        if (this.pearlLoadTriggered) {
                            if (location == this.currentLoadLocation) {
                                return;
                            }

                            for (PearlLoader.PendingTrigger queued : this.pendingTriggers) {
                                if (queued.location() == location) {
                                    return;
                                }
                            }

                            if (this.pendingTriggers.size() >= 4) {
                                return;
                            }

                            this.pendingTriggers.add(new PearlLoader.PendingTrigger(location, sender));
                            this.info("Queued pearl load " + location.triggerKeyword + " (busy with " + this.currentLoadLocation.triggerKeyword + ")");
                        } else {
                            if (sender != null) {
                                this.info("Pearl load triggered by " + sender + " (" + location.triggerKeyword + ")");
                            } else {
                                this.info("Pearl load triggered by keyword: " + location.triggerKeyword);
                            }

                            this.triggerPearlLoad(location, sender);
                        }

                        return;
                    }

                    if (this.debugMode.get()) {
                        this.info("Trigger ignored - player not whitelisted: " + sender);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.isActive && this.mc.player != null && this.mc.level != null) {
            if (this.pathGrace > 0) {
                this.pathGrace--;
            }

            if (this.currentState != this.lastState) {
                this.onStateEnter();
                this.lastState = this.currentState;
            }

            if (this.pearlLoadTriggered) {
                this.sequenceTicks++;
                if (this.sequenceTicks > this.sequenceTimeout.get() * 20) {
                    this.warning("Pearl load stalled in " + this.currentState + ", returning to anti-AFK loop");
                    this.finishLoad();
                    return;
                }

                this.handleLoadSequence();
            } else {
                this.handleWalkingLoop();
            }
        }
    }

    private void onStateEnter() {
        this.stateTicks = 0;
        this.sequenceTicks = 0;
        this.ipPhase = PearlLoader.IPhase.ROTATE;
        this.ipTicks = 0;
        this.ipRetries = 0;
    }

    private void handleWalkingLoop() {
        if (this.currentState != PearlLoader.State.WALK_LOOP_1 && this.currentState != PearlLoader.State.WALK_LOOP_2) {
            this.currentState = PearlLoader.State.WALK_LOOP_1;
            this.currentTarget = this.walkPoint1.get();
            this.startPathing(this.currentTarget);
        } else if (this.currentTarget == null) {
            this.currentTarget = this.walkPoint1.get();
            this.startPathing(this.currentTarget);
        } else if (this.isPathingDone()) {
            if (this.getDistanceToTarget(this.currentTarget) > 2.0 && ++this.legRepaths <= 3) {
                if (this.debugMode.get()) {
                    this.info("Walk leg stopped short, re-pathing (" + this.legRepaths + "/3)");
                }

                this.startPathing(this.currentTarget);
            } else {
                this.legRepaths = 0;
                if (this.currentState == PearlLoader.State.WALK_LOOP_1) {
                    this.currentState = PearlLoader.State.WALK_LOOP_2;
                    this.currentTarget = this.walkPoint2.get();
                    if (this.debugMode.get()) {
                        this.info("Reached Point 1, walking to Point 2");
                    }
                } else {
                    this.currentState = PearlLoader.State.WALK_LOOP_1;
                    this.currentTarget = this.walkPoint1.get();
                    if (this.debugMode.get()) {
                        this.info("Reached Point 2, walking to Point 1");
                    }
                }

                this.startPathing(this.currentTarget);
            }
        }
    }

    private void handleLoadSequence() {
        switch (this.currentState) {
            case APPROACH:
                this.handleApproach();
                break;
            case COVER_OPEN:
                this.runStep(this.coverPos(), true, this.coverOpenReturn);
                break;
            case LOAD_TRIGGER:
                if (this.redirectIfCoverClosed(PearlLoader.State.LOAD_TRIGGER)) {
                    return;
                }

                this.runStep(this.loadPos(), false, PearlLoader.State.LOAD_WAIT);
                break;
            case LOAD_WAIT:
                this.handleLoadWait();
                break;
            case LOAD_RESET:
                if (this.redirectIfCoverClosed(PearlLoader.State.LOAD_RESET)) {
                    return;
                }

                this.runStep(this.loadPos(), true, this.currentLoadLocation.useCover ? PearlLoader.State.COVER_CLOSE : PearlLoader.State.DROP_PEARL);
                break;
            case COVER_CLOSE:
                this.handleCoverClose();
                break;
            case WALK_TO_POS:
                this.handleWalkToPos();
                break;
            case WALK_TO_STAND:
                this.handleWalkToStand();
                break;
            case DROP_PEARL:
                this.handleDropPearl();
                break;
            default:
                this.finishLoad();
        }
    }

    private BlockPos loadPos() {
        return this.resolvedLoadPos != null ? this.resolvedLoadPos : this.currentLoadLocation.position;
    }

    private BlockPos coverPos() {
        return this.loadPos().above();
    }

    private boolean redirectIfCoverClosed(PearlLoader.State returnState) {
        if (!this.currentLoadLocation.useCover) {
            return false;
        }

        BlockState cover = this.mc.level.getBlockState(this.coverPos());
        if (cover.getBlock() instanceof TrapDoorBlock && !cover.getValue(TrapDoorBlock.OPEN)) {
            if (++this.revertRedirects > 2) {
                this.error("Cover trapdoor keeps closing mid-sequence, aborting");
                this.finishLoad();
                return true;
            } else {
                this.warning("Cover trapdoor closed mid-sequence, reopening (" + this.revertRedirects + "/2)");
                this.coverOpenReturn = returnState;
                this.currentState = PearlLoader.State.COVER_OPEN;
                return true;
            }
        } else {
            return false;
        }
    }

    private void handleCoverClose() {
        BlockState load = this.mc.level.getBlockState(this.loadPos());
        if (load.getBlock() instanceof TrapDoorBlock && !load.getValue(TrapDoorBlock.OPEN)) {
            if (++this.revertRedirects > 2) {
                this.error("Load trapdoor keeps reverting to closed, aborting");
                this.finishLoad();
            } else {
                this.warning("Load trapdoor closed again before covering, reopening (" + this.revertRedirects + "/2)");
                this.currentState = PearlLoader.State.LOAD_RESET;
            }
        } else {
            this.runStep(this.coverPos(), false, PearlLoader.State.DROP_PEARL);
        }
    }

    private void triggerPearlLoad(PearlLoader.LoadLocation location, String sender) {
        if (!this.pearlLoadTriggered) {
            this.pearlLoadTriggered = true;
            this.currentLoadLocation = location;
            this.currentTriggerSender = sender;
            this.sequenceTicks = 0;
            this.reApproaches = 0;
            this.closeRetries = 0;
            this.revertRedirects = 0;
            this.coverOpenReturn = PearlLoader.State.LOAD_TRIGGER;
            this.resumeState = null;
            this.approachTarget = null;
            this.resolvedLoadPos = null;
            this.stopPathing();
            if (location.mode == PearlLoader.LoadMode.TRAPDOOR) {
                this.currentState = PearlLoader.State.APPROACH;
                this.startPathingNear(location.position);
                if (this.debugMode.get()) {
                    this.info("Starting trapdoor pearl load for: " + location.triggerKeyword);
                }
            } else {
                this.currentState = PearlLoader.State.WALK_TO_POS;
                this.startPathing(location.position);
                if (this.debugMode.get()) {
                    this.info("Starting walk-to pearl load for: " + location.triggerKeyword);
                }
            }
        }
    }

    private void finishLoad() {
        this.pearlLoadTriggered = false;
        this.currentLoadLocation = null;
        this.currentTriggerSender = null;
        this.sequenceTicks = 0;
        this.reApproaches = 0;
        this.closeRetries = 0;
        this.revertRedirects = 0;
        this.coverOpenReturn = PearlLoader.State.LOAD_TRIGGER;
        this.resumeState = null;
        this.approachTarget = null;
        this.resolvedLoadPos = null;
        RotationUtils.getInstance().clearRotations();
        PearlLoader.PendingTrigger next = this.pendingTriggers.poll();
        if (next != null) {
            this.info("Starting queued pearl load: " + next.location().triggerKeyword);
            this.triggerPearlLoad(next.location(), next.sender());
        } else {
            this.info("Pearl loading complete");
            this.currentState = PearlLoader.State.WALK_LOOP_1;
            this.currentTarget = this.walkPoint1.get();
            this.startPathing(this.currentTarget);
        }
    }

    private void handleApproach() {
        if (this.isBaritonePathing()) {
            this.sequenceTicks = 0;
        }

        if (this.resolvedLoadPos == null) {
            BlockPos configured = this.currentLoadLocation.position;
            if (!this.isChunkLoaded(configured)) {
                if (this.isPathingDone()) {
                    this.startPathingNear(configured);
                }

                this.stateTicks = 0;
                return;
            }

            this.resolvedLoadPos = this.resolveLoadTrapdoor();
            if (this.resolvedLoadPos == null) {
                this.error("No trapdoor found near " + configured.toShortString() + ", aborting");
                this.finishLoad();
                return;
            }

            if (!this.resolvedLoadPos.equals(configured) && this.debugMode.get()) {
                this.warning("Load trapdoor resolved to " + this.resolvedLoadPos.toShortString() + " (configured " + configured.toShortString() + ")");
            }
        }

        if (this.approachTarget == null) {
            this.approachTarget = this.getLoadApproachPosition();
            if (this.approachTarget == null) {
                this.error("No standable spot in reach of the trapdoor" + (this.currentLoadLocation.useCover ? "s" : "") + ", aborting");
                this.finishLoad();
                return;
            }

            this.stopPathing();
            this.startPathing(this.approachTarget);
        }

        BlockPos target = this.approachTarget;
        double distance = this.getDistanceToTarget(target);
        if (distance > this.reachThreshold.get()) {
            if (this.isPathingDone()) {
                this.startPathing(target);
            }

            this.stateTicks = 0;
        } else {
            this.stopPathing();
            if (++this.stateTicks >= this.arrivalWaitTicks.get()) {
                PearlLoader.State next = this.resumeState != null
                    ? this.resumeState
                    : (this.currentLoadLocation.useCover ? PearlLoader.State.COVER_OPEN : PearlLoader.State.LOAD_TRIGGER);
                this.resumeState = null;
                this.currentState = next;
                if (this.debugMode.get()) {
                    this.info("Arrived at trapdoor, settled");
                }
            }
        }
    }

    private void handleLoadWait() {
        if (this.stateTicks < 15 && this.mc.level != null) {
            BlockState state = this.mc.level.getBlockState(this.loadPos());
            if (state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN)) {
                if (++this.closeRetries > 2) {
                    this.error("Load trapdoor keeps reverting to open, aborting");
                    this.finishLoad();
                    return;
                }

                this.warning("Load trapdoor reverted to open, retrying close (" + this.closeRetries + "/2)");
                this.currentState = PearlLoader.State.LOAD_TRIGGER;
                return;
            }
        }

        int waitTicks = (int)(this.currentLoadLocation.trapdoorCloseTime * 20.0);
        if (++this.stateTicks >= waitTicks) {
            this.currentState = PearlLoader.State.LOAD_RESET;
            if (this.debugMode.get()) {
                this.info("Close wait complete, reopening load trapdoor");
            }
        }
    }

    private void handleWalkToPos() {
        BlockPos target = this.currentLoadLocation.position;
        if (this.isBaritonePathing()) {
            this.sequenceTicks = 0;
        }

        double distance = this.getDistanceToTarget(target);
        if (distance > this.reachThreshold.get()) {
            if (this.isPathingDone()) {
                this.startPathing(target);
            }

            this.stateTicks = 0;
        } else {
            this.stopPathing();
            if (++this.stateTicks >= this.arrivalWaitTicks.get()) {
                this.currentState = PearlLoader.State.WALK_TO_STAND;
                if (this.debugMode.get()) {
                    this.info("Arrived at load position, standing");
                }
            }
        }
    }

    private void handleWalkToStand() {
        int standTicks = (int)(this.currentLoadLocation.standTime * 20.0);
        if (++this.stateTicks >= standTicks) {
            this.currentState = PearlLoader.State.DROP_PEARL;
            if (this.debugMode.get()) {
                this.info("Stand time complete");
            }
        }
    }

    private void handleDropPearl() {
        if (!this.dropPearlOnLoad.get()) {
            this.finishLoad();
        } else {
            Player target = this.resolveDropTarget();
            if (target == null) {
                if (++this.stateTicks >= 20) {
                    this.warning("No player nearby to receive the pearl, skipping drop");
                    this.finishLoad();
                }
            } else {
                float[] rot = RotationUtils.getRotationsTo(this.mc.player.getEyePosition(), target.getEyePosition());
                RotationUtils.getInstance().setRotationSilent(rot[0], rot[1]);
                this.stateTicks++;
                if (RotationUtils.getInstance().isAligned()) {
                    this.ipTicks++;
                } else {
                    this.ipTicks = 0;
                }

                if (this.ipTicks >= 2 || this.stateTicks >= 30) {
                    this.dropPearls();
                    this.finishLoad();
                }
            }
        }
    }

    private void dropPearls() {
        FindItemResult pearl = InvUtils.find(Items.ENDER_PEARL);
        if (!pearl.found()) {
            this.warning("No ender pearls in inventory to drop");
        } else {
            InvUtils.dropOne().slot(pearl.slot());
            this.info("Dropped a pearl to player");
        }
    }

    private Player resolveDropTarget() {
        if (this.mc.level == null) {
            return null;
        }

        if (this.currentTriggerSender != null && !this.currentTriggerSender.isEmpty()) {
            for (Player p : this.mc.level.players()) {
                if (p != this.mc.player && p.getName().getString().equalsIgnoreCase(this.currentTriggerSender)) {
                    return p;
                }
            }
        }

        Player nearest = null;
        double bestSq = 256.0;

        for (Player p : this.mc.level.players()) {
            if (p != this.mc.player) {
                double d = p.position().distanceToSqr(this.mc.player.position());
                if (d < bestSq) {
                    bestSq = d;
                    nearest = p;
                }
            }
        }

        return nearest;
    }

    private PearlLoader.StepResult stepTrapdoor(BlockPos pos, boolean wantOpen) {
        if (this.mc.player != null && this.mc.level != null) {
            BlockState state = this.mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof TrapDoorBlock)) {
                this.error("No trapdoor found at " + pos.toShortString());
                return PearlLoader.StepResult.FAIL;
            }

            if (state.is(Blocks.IRON_TRAPDOOR)) {
                this.error("Iron trapdoor at " + pos.toShortString() + " can't be opened by hand");
                return PearlLoader.StepResult.FAIL;
            }

            boolean correct = state.getValue(TrapDoorBlock.OPEN) == wantOpen;
            if (this.ipPhase == PearlLoader.IPhase.VERIFY) {
                if (correct) {
                    if (this.debugMode.get()) {
                        this.info("Trapdoor at " + pos.toShortString() + " is now " + (wantOpen ? "OPEN" : "CLOSED"));
                    }

                    return PearlLoader.StepResult.DONE;
                } else if (++this.ipTicks < 4) {
                    return PearlLoader.StepResult.BUSY;
                } else if (++this.ipRetries > 3) {
                    this.error("Failed to toggle trapdoor at " + pos.toShortString());
                    return PearlLoader.StepResult.FAIL;
                } else {
                    this.ipPhase = PearlLoader.IPhase.INTERACT;
                    this.ipTicks = 0;
                    return PearlLoader.StepResult.BUSY;
                }
            } else if (correct) {
                if (this.debugMode.get()) {
                    this.info("Trapdoor at " + pos.toShortString() + " already " + (wantOpen ? "OPEN" : "CLOSED"));
                }

                return PearlLoader.StepResult.DONE;
            } else {
                Vec3 eye = this.mc.player.getEyePosition();
                BlockHitResult hit = this.trapdoorHit(pos, state, eye);
                if (hit == null) {
                    if (this.debugMode.get()) {
                        this.warning("No line of sight to trapdoor at " + pos.toShortString());
                    }

                    return PearlLoader.StepResult.TOO_FAR;
                } else {
                    if (eye.distanceTo(hit.getLocation()) > 4.25) {
                        return PearlLoader.StepResult.TOO_FAR;
                    }

                    Vec3 aim = hit.getLocation();
                    switch (this.ipPhase) {
                        case ROTATE:
                            float[] rotx = RotationUtils.getRotationsTo(eye, aim);
                            RotationUtils.getInstance().setRotationSilent(rotx[0], rotx[1]);
                            this.ipTicks++;
                            if (RotationUtils.getInstance().isAligned() || this.ipTicks > 30) {
                                this.ipPhase = PearlLoader.IPhase.SETTLE;
                                this.ipTicks = 0;
                            }
                            break;
                        case SETTLE:
                            float[] rot = RotationUtils.getRotationsTo(eye, aim);
                            RotationUtils.getInstance().setRotationSilent(rot[0], rot[1]);
                            if (++this.ipTicks >= 2) {
                                this.ipPhase = PearlLoader.IPhase.INTERACT;
                                this.ipTicks = 0;
                            }
                            break;
                        case INTERACT:
                            this.mc.options.keyShift.setDown(false);
                            this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hit);
                            this.mc.player.swing(InteractionHand.MAIN_HAND);
                            this.ipPhase = PearlLoader.IPhase.VERIFY;
                            this.ipTicks = 0;
                    }

                    return PearlLoader.StepResult.BUSY;
                }
            }
        } else {
            return PearlLoader.StepResult.BUSY;
        }
    }

    private void runStep(BlockPos pos, boolean wantOpen, PearlLoader.State next) {
        switch (this.stepTrapdoor(pos, wantOpen)) {
            case BUSY:
            default:
                break;
            case DONE:
                this.currentState = next;
                break;
            case FAIL:
                this.finishLoad();
                break;
            case TOO_FAR:
                if (++this.reApproaches > 3) {
                    this.error("Can't get in reach of trapdoor at " + pos.toShortString() + ", aborting");
                    this.finishLoad();
                    return;
                }

                this.warning("Too far from trapdoor, re-approaching (" + this.reApproaches + "/3)");
                this.resumeState = this.currentState;
                this.approachTarget = null;
                this.currentState = PearlLoader.State.APPROACH;
        }
    }

    private BlockHitResult trapdoorHit(BlockPos pos, BlockState state, Vec3 eye) {
        VoxelShape shape = state.getShape(this.mc.level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos.getX(), pos.getY(), pos.getZ());
        AABB inset = box.inflate(-0.01);
        Vec3 closest = new Vec3(
            clamp(eye.x, inset.minX, inset.maxX),
            clamp(eye.y, inset.minY, inset.maxY),
            clamp(eye.z, inset.minZ, inset.maxZ)
        );
        Vec3 center = box.getCenter();
        Vec3[] candidates = new Vec3[]{
            closest,
            center,
            new Vec3(center.x, inset.maxY, center.z),
            new Vec3(center.x, inset.minY, center.z),
            new Vec3(inset.minX, center.y, center.z),
            new Vec3(inset.maxX, center.y, center.z),
            new Vec3(center.x, center.y, inset.minZ),
            new Vec3(center.x, center.y, inset.maxZ)
        };

        for (Vec3 aim : candidates) {
            if (!(eye.distanceTo(aim) > 4.25)) {
                BlockHitResult clip = this.mc.level.clip(new ClipContext(eye, aim, Block.OUTLINE, Fluid.NONE, this.mc.player));
                if (clip.getType() == Type.BLOCK && clip.getBlockPos().equals(pos)) {
                    return clip;
                }
            }
        }

        return null;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    private BlockPos resolveLoadTrapdoor() {
        BlockPos base = this.currentLoadLocation.position;
        int[] dys = new int[]{0, -1, -2, 1, 2};
        if (this.currentLoadLocation.useCover) {
            for (int dy : dys) {
                BlockPos p = base.above(dy);
                if (this.isTrapdoorAt(p) && this.isTrapdoorAt(p.above())) {
                    return p;
                }
            }
        }

        for (int dy : dys) {
            BlockPos p = base.above(dy);
            if (this.isTrapdoorAt(p)) {
                return p;
            }
        }

        return null;
    }

    private boolean isTrapdoorAt(BlockPos pos) {
        return this.mc.level.getBlockState(pos).getBlock() instanceof TrapDoorBlock;
    }

    private boolean isChunkLoaded(BlockPos pos) {
        return this.mc.level != null && this.mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private BlockPos getLoadApproachPosition() {
        if (this.mc.level != null && this.mc.player != null) {
            BlockPos trapPos = this.loadPos();
            BlockPos cPos = this.coverPos();
            boolean cover = this.currentLoadLocation.useCover;
            int heightOffset = cover ? this.coverStandHeight.get() : 0;
            BlockState loadState = this.mc.level.getBlockState(trapPos);
            BlockState coverState = this.mc.level.getBlockState(cPos);
            boolean coverClosed = cover && coverState.getBlock() instanceof TrapDoorBlock && !coverState.getValue(TrapDoorBlock.OPEN);
            BlockPos bestLos = null;
            BlockPos bestReach = null;
            double bestLosDist = Double.MAX_VALUE;
            double bestReachDist = Double.MAX_VALUE;

            for (int dy = Math.max(0, heightOffset - 1); dy <= heightOffset + 1; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx != 0 || dz != 0) {
                            BlockPos checkPos = trapPos.offset(dx, dy, dz);
                            if (this.isStandable(checkPos)
                                && !(this.interactReachFrom(checkPos, trapPos) > 4.25)
                                && (!cover || !(this.interactReachFrom(checkPos, cPos) > 4.25))) {
                                double dist = this.mc.player.position().distanceTo(Vec3.atCenterOf(checkPos));
                                if (dist < bestReachDist) {
                                    bestReachDist = dist;
                                    bestReach = checkPos;
                                }

                                if (!(dist >= bestLosDist)) {
                                    Vec3 eye = Vec3.atLowerCornerOf(checkPos).add(0.5, 1.62, 0.5);
                                    if ((!cover || this.trapdoorHit(cPos, coverState, eye) != null)
                                        && (coverClosed || this.trapdoorHit(trapPos, loadState, eye) != null)) {
                                        bestLosDist = dist;
                                        bestLos = checkPos;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (bestLos != null) {
                return bestLos;
            }

            if (bestReach != null && this.debugMode.get()) {
                this.warning("No stand spot with clear line of sight, using closest in-reach spot");
            }

            return bestReach;
        } else {
            return null;
        }
    }

    private boolean isStandable(BlockPos pos) {
        return this.mc.level.getBlockState(pos).getCollisionShape(this.mc.level, pos).isEmpty()
            && this.mc.level.getBlockState(pos.above()).getCollisionShape(this.mc.level, pos.above()).isEmpty()
            && !this.mc.level.getBlockState(pos.below()).getCollisionShape(this.mc.level, pos.below()).isEmpty();
    }

    private double interactReachFrom(BlockPos standPos, BlockPos target) {
        Vec3 eye = Vec3.atLowerCornerOf(standPos).add(0.5, 1.62, 0.5);
        BlockState state = this.mc.level.getBlockState(target);
        VoxelShape shape = state.getShape(this.mc.level, target);
        AABB box = shape.isEmpty()
            ? new AABB(target)
            : shape.bounds().move(target.getX(), target.getY(), target.getZ());
        return eye.distanceTo(
            new Vec3(
                clamp(eye.x, box.minX, box.maxX),
                clamp(eye.y, box.minY, box.maxY),
                clamp(eye.z, box.minZ, box.maxZ)
            )
        );
    }

    private double getDistanceToTarget(BlockPos target) {
        return this.mc.player != null && target != null ? this.mc.player.position().distanceTo(Vec3.atCenterOf(target)) : Double.MAX_VALUE;
    }

    private void startPathing(BlockPos target) {
        if (target != null) {
            this.pathGrace = 20;

            try {
                Class.forName("baritone.api.BaritoneAPI");
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
            } catch (ClassNotFoundException e) {
                this.error("Baritone not available!");
            }
        }
    }

    private void startPathingNear(BlockPos target) {
        if (target != null) {
            this.pathGrace = 20;

            try {
                Class.forName("baritone.api.BaritoneAPI");
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(target, 3));
            } catch (ClassNotFoundException e) {
                this.error("Baritone not available!");
            }
        }
    }

    private void stopPathing() {
        try {
            Class.forName("baritone.api.BaritoneAPI");
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        } catch (ClassNotFoundException var2) {
        }
    }

    private boolean isBaritonePathing() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isPathingDone() {
        return this.pathGrace > 0 ? false : !this.isBaritonePathing();
    }

    private String extractSenderName(String message) {
        String cleaned = message.replaceAll("§[0-9a-fk-or]", "");
        if (cleaned.contains(" whispers: ")) {
            String beforeWhispers = cleaned.substring(0, cleaned.indexOf(" whispers: "));
            String[] parts = beforeWhispers.split(" ");
            if (parts.length >= 1) {
                String name = parts[parts.length - 1].trim();
                if (this.debugMode.get()) {
                    this.info("Extracted whisper sender: " + name);
                }

                return name;
            }
        }

        if (cleaned.startsWith("<")) {
            int end = cleaned.indexOf(62);
            if (end > 1) {
                String[] parts = cleaned.substring(1, end).trim().split(" ");
                String name = parts[parts.length - 1].replaceAll("[\\[\\]]", "").trim();
                if (!name.isEmpty()) {
                    if (this.debugMode.get()) {
                        this.info("Extracted public sender: " + name);
                    }

                    return name;
                }
            }
        }

        if (cleaned.contains(": ")) {
            String beforeColon = cleaned.substring(0, cleaned.indexOf(": "));
            if (beforeColon.contains("<") && beforeColon.contains(">")) {
                int start = beforeColon.lastIndexOf("<");
                int end = beforeColon.lastIndexOf(">");
                if (start < end) {
                    String name = beforeColon.substring(start + 1, end).trim();
                    if (this.debugMode.get()) {
                        this.info("Extracted public sender: " + name);
                    }

                    return name;
                }
            }

            String[] parts = beforeColon.split(" ");
            if (parts.length > 0) {
                String name = parts[parts.length - 1].replaceAll("[<>\\[\\]]", "").trim();
                if (this.debugMode.get()) {
                    this.info("Extracted sender: " + name);
                }

                return name;
            }
        }

        if (this.debugMode.get()) {
            this.warning("Could not extract sender from message: " + message);
        }

        return null;
    }

    private boolean isPlayerWhitelisted(String playerName) {
        if (playerName == null) {
            return false;
        }

        for (String whitelisted : this.whitelistedPlayers.get()) {
            if (whitelisted.equalsIgnoreCase(playerName)) {
                return true;
            }
        }

        return false;
    }

    List<String> getWhitelistedPlayers() {
        return this.whitelistedPlayers.get();
    }

    void addWhitelistedPlayer(String name) {
        String trimmed = name.trim();
        if (!trimmed.isEmpty()) {
            List<String> list = new ArrayList<>(this.whitelistedPlayers.get());

            for (String existing : list) {
                if (existing.equalsIgnoreCase(trimmed)) {
                    return;
                }
            }

            list.add(trimmed);
            this.whitelistedPlayers.set(list);
        }
    }

    void removeWhitelistedPlayer(String name) {
        List<String> list = new ArrayList<>(this.whitelistedPlayers.get());
        list.removeIf(s -> s.equalsIgnoreCase(name));
        this.whitelistedPlayers.set(list);
    }

    boolean isDebugEnabled() {
        return this.debugMode.get();
    }

    void setDebugEnabled(boolean enabled) {
        this.debugMode.set(enabled);
    }

    List<String> debugStatusLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Active: " + this.isActive);
        lines.add("Loading pearl: " + this.pearlLoadTriggered);
        lines.add("State: " + this.currentState);
        lines.add("Target: " + (this.currentTarget == null ? "none" : this.currentTarget.toShortString()));
        lines.add("Load location: " + (this.currentLoadLocation == null ? "none" : this.currentLoadLocation.triggerKeyword));
        lines.add("Trigger sender: " + (this.currentTriggerSender == null ? "none" : this.currentTriggerSender));
        lines.add("State ticks: " + this.stateTicks);
        lines.add("Sequence ticks: " + this.sequenceTicks + " / " + this.sequenceTimeout.get() * 20);
        lines.add("Interact phase: " + this.ipPhase + " (retries " + this.ipRetries + ")");
        lines.add("Re-approaches: " + this.reApproaches + " / 3");
        lines.add("Close retries: " + this.closeRetries + " / 2");
        lines.add("Revert redirects: " + this.revertRedirects + " / 2");
        lines.add("Resolved load pos: " + (this.resolvedLoadPos == null ? "none" : this.resolvedLoadPos.toShortString()));
        lines.add("Approach target: " + (this.approachTarget == null ? "none" : this.approachTarget.toShortString()));
        lines.add("Queued triggers: " + this.pendingTriggers.size());
        return lines;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        this.guiTheme = theme;
        this.settingsList = theme.verticalList();
        this.buildSettingsWidget();
        return this.settingsList;
    }

    private void refreshWidget() {
        if (this.settingsList != null && this.guiTheme != null) {
            this.settingsList.clear();
            this.buildSettingsWidget();
            this.settingsList.invalidate();
        }
    }

    private void buildSettingsWidget() {
        GuiTheme theme = this.guiTheme;
        WVerticalList mainList = this.settingsList;
        WHorizontalList topButtons = mainList.add(theme.horizontalList()).expandX().widget();
        WButton whitelistButton = topButtons.add(theme.button("Edit Whitelist")).expandX().widget();
        whitelistButton.action = () -> this.mc.setScreen(new PearlWhitelistScreen(theme, this));
        WButton debugButton = topButtons.add(theme.button("Debug Menu")).expandX().widget();
        debugButton.action = () -> this.mc.setScreen(new PearlDebugScreen(theme, this));
        mainList.add(theme.horizontalSeparator()).expandX();
        WButton addButton = mainList.add(theme.button("Add Load Location")).widget();
        addButton.action = () -> {
            this.loadLocations
                .add(
                    new PearlLoader.LoadLocation("!pearl" + (this.loadLocations.size() + 1), PearlLoader.LoadMode.TRAPDOOR, new BlockPos(0, 64, 0), 2.0, 1.0)
                );
            this.refreshWidget();
        };

        for (int i = 0; i < this.loadLocations.size(); i++) {
            PearlLoader.LoadLocation location = this.loadLocations.get(i);
            mainList.add(theme.horizontalSeparator()).expandX();
            WHorizontalList headerList = mainList.add(theme.horizontalList()).expandX().widget();
            headerList.add(theme.label("Location " + (i + 1) + ":")).expandX();
            WButton removeButton = headerList.add(theme.button("-")).widget();
            removeButton.action = () -> {
                this.loadLocations.remove(location);
                this.refreshWidget();
            };
            WButton triggerButton = headerList.add(theme.button("Trigger")).widget();
            triggerButton.action = () -> {
                if (!this.pearlLoadTriggered && this.isActive) {
                    this.info("Manually triggering pearl load: " + location.triggerKeyword);
                    this.triggerPearlLoad(location, null);
                }
            };
            WTextBox keywordBox = mainList.add(theme.textBox(location.triggerKeyword)).expandX().widget();
            keywordBox.action = () -> location.triggerKeyword = keywordBox.get();
            mainList.add(theme.label("Load Pos: " + (BepConfig.streamerMode.get() ? "*****" : location.position.toShortString())));
            WHorizontalList posButtons = mainList.add(theme.horizontalList()).expandX().widget();
            WButton setPosButton = posButtons.add(theme.button("Set Load to Player Pos")).widget();
            setPosButton.action = () -> {
                if (this.mc.player != null) {
                    location.position = this.mc.player.blockPosition();
                    this.refreshWidget();
                }
            };
            mainList.add(theme.label("Mode: " + location.mode.toString()));
            WHorizontalList modeButtons = mainList.add(theme.horizontalList()).expandX().widget();
            WButton trapdoorButton = modeButtons.add(theme.button("Trapdoor")).widget();
            trapdoorButton.action = () -> {
                location.mode = PearlLoader.LoadMode.TRAPDOOR;
                this.refreshWidget();
            };
            WButton walkToButton = modeButtons.add(theme.button("Walk To")).widget();
            walkToButton.action = () -> {
                location.mode = PearlLoader.LoadMode.WALK_TO;
                this.refreshWidget();
            };
            if (location.mode == PearlLoader.LoadMode.TRAPDOOR) {
                mainList.add(theme.label("Close Time: " + location.trapdoorCloseTime + "s"));
                WHorizontalList coverButtons = mainList.add(theme.horizontalList()).expandX().widget();
                WButton coverToggle = coverButtons.add(theme.button(location.useCover ? "Cover: ON" : "Cover: OFF")).widget();
                coverToggle.action = () -> {
                    location.useCover = !location.useCover;
                    this.refreshWidget();
                };
                if (location.useCover) {
                    mainList.add(
                        theme.label("Cover Pos: " + (BepConfig.streamerMode.get() ? "*****" : location.coverPos().toShortString()) + " (auto, above load pos)")
                    );
                }
            } else {
                mainList.add(theme.label("Stand Time: " + location.standTime + "s"));
            }
        }

        mainList.add(theme.horizontalSeparator()).expandX();
        WButton testLoop = mainList.add(theme.button("Test Walking Loop")).widget();
        testLoop.action = () -> {
            if (this.isActive) {
                this.info("Testing walking loop");
                this.finishLoad();
            }
        };
    }

    private enum IPhase {
        ROTATE,
        SETTLE,
        INTERACT,
        VERIFY;
    }

    public static class LoadLocation implements ISerializable<PearlLoader.LoadLocation> {
        public String triggerKeyword = "!pearl";
        public PearlLoader.LoadMode mode = PearlLoader.LoadMode.TRAPDOOR;
        public BlockPos position = new BlockPos(0, 64, 0);
        public boolean useCover = false;
        public double trapdoorCloseTime = 2.0;
        public double standTime = 1.0;

        public LoadLocation() {
        }

        public LoadLocation(String keyword, PearlLoader.LoadMode mode, BlockPos pos, double closeTime, double standTime) {
            this.triggerKeyword = keyword;
            this.mode = mode;
            this.position = pos;
            this.trapdoorCloseTime = closeTime;
            this.standTime = standTime;
        }

        public BlockPos coverPos() {
            return this.position.above();
        }

        @Override
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("keyword", this.triggerKeyword);
            tag.putString("mode", this.mode.name());
            tag.putInt("x", this.position.getX());
            tag.putInt("y", this.position.getY());
            tag.putInt("z", this.position.getZ());
            tag.putBoolean("useCover", this.useCover);
            tag.putDouble("closeTime", this.trapdoorCloseTime);
            tag.putDouble("standTime", this.standTime);
            return tag;
        }

        public PearlLoader.LoadLocation fromTag(CompoundTag tag) {
            this.triggerKeyword = tag.getString("keyword").orElse("");
            this.mode = PearlLoader.LoadMode.valueOf(tag.getString("mode").orElse("TRAPDOOR"));
            this.position = new BlockPos(tag.getInt("x").orElse(0), tag.getInt("y").orElse(0), tag.getInt("z").orElse(0));
            this.useCover = tag.getBoolean("useCover").orElse(false);
            this.trapdoorCloseTime = tag.getDouble("closeTime").orElse(2.0);
            this.standTime = tag.getDouble("standTime").orElse(1.0);
            return this;
        }
    }

    public enum LoadMode {
        TRAPDOOR("Trapdoor - Interact with trapdoor to load pearl"),
        WALK_TO("Walk To - Walk to position and return");

        private final String description;

        LoadMode(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return this.description;
        }
    }

    private record PendingTrigger(PearlLoader.LoadLocation location, String sender) {
    }

    private enum State {
        WALK_LOOP_1,
        WALK_LOOP_2,
        APPROACH,
        COVER_OPEN,
        LOAD_TRIGGER,
        LOAD_WAIT,
        LOAD_RESET,
        COVER_CLOSE,
        WALK_TO_POS,
        WALK_TO_STAND,
        DROP_PEARL;
    }

    private enum StepResult {
        BUSY,
        DONE,
        FAIL,
        TOO_FAR;
    }
}
