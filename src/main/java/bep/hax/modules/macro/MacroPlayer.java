package bep.hax.modules.macro;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import bep.hax.Bep;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class MacroPlayer extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgSafety = this.settings.createGroup("Safety");
    private final SettingGroup sgRealism = this.settings.createGroup("Realism");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<String> macroName = this.sgGeneral
        .add(new Builder().name("macro-name").description("Name of the macro file to play.").defaultValue("macro").build());
    private final Setting<Boolean> loop = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("loop")
                .description("Loop playback when reaching the end.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> loopDelay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("loop-delay")
                .description("Ticks to wait between loops.")
                .defaultValue(20)
                .min(0)
                .sliderMax(200)
                .build()
        );
    private final Setting<Boolean> screenVerify = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("screen-verify")
                .description("Verify correct screen is open before GUI clicks.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> screenWaitTicks = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("screen-wait-ticks")
                .description("Max ticks to wait for expected screen to appear.")
                .defaultValue(20)
                .min(1)
                .sliderMax(100)
                .build()
        );
    private final Setting<Double> positionTolerance = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("position-tolerance")
                .description("Max position drift in blocks before stopping playback.")
                .defaultValue(2.0)
                .min(0.5)
                .sliderMax(10.0)
                .build()
        );
    private final Setting<Boolean> stopOnDeath = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("stop-on-death")
                .description("Stop playback when the player dies.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> requireNearOrigin = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("require-near-origin")
                .description("Require being near the recording origin to start playback.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> baritoneToOrigin = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("baritone-to-origin")
                .description("Use Baritone to walk to the recording origin before each playback cycle.")
                .defaultValue(false)
                .visible(this.requireNearOrigin::get)
                .build()
        );
    private final Setting<Double> originRadius = this.sgSafety
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("origin-radius")
                .description("Max distance from origin to allow starting without Baritone.")
                .defaultValue(3.0)
                .min(0.5)
                .sliderMax(10.0)
                .visible(() -> this.requireNearOrigin.get() && !this.baritoneToOrigin.get())
                .build()
        );
    private final Setting<Boolean> rotationJitter = this.sgRealism
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("rotation-jitter")
                .description("Add slight random jitter to rotations.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Double> jitterAmount = this.sgRealism
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("jitter-amount")
                .description("Max degrees of random rotation jitter.")
                .defaultValue(0.5)
                .min(0.1)
                .sliderMax(3.0)
                .visible(this.rotationJitter::get)
                .build()
        );
    private final Setting<Boolean> renderOrigin = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render-origin")
                .description("Render the recording origin position in the world.")
                .defaultValue(true)
                .build()
        );
    private final Setting<SettingColor> originColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("origin-color")
                .description("Color of the origin marker.")
                .defaultValue(new SettingColor(0, 255, 0, 100))
                .visible(this.renderOrigin::get)
                .build()
        );
    private final Setting<SettingColor> originLineColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("origin-line-color")
                .description("Line color of the origin marker.")
                .defaultValue(new SettingColor(0, 255, 0, 255))
                .visible(this.renderOrigin::get)
                .build()
        );
    private MacroFile macroFile;
    private MacroPlayer.State state;
    private int currentFrame;
    private int loopCount;
    private int delayCounter;
    private boolean waitingForScreen;
    private int screenWaitCounter;
    private static final double FINE_POS_TOLERANCE = 0.15;

    public MacroPlayer() {
        super(Bep.CATEGORY, "macro-player", "Plays back recorded macro files.");
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null && this.mc.level != null) {
            this.macroFile = MacroFileManager.load(this.macroName.get());
            if (this.macroFile != null && !this.macroFile.frames.isEmpty()) {
                this.currentFrame = 0;
                this.loopCount = 0;
                this.delayCounter = 0;
                this.waitingForScreen = false;
                this.screenWaitCounter = 0;
                if (this.requireNearOrigin.get() && this.baritoneToOrigin.get()) {
                    this.beginBaritoneToOrigin();
                } else if (this.requireNearOrigin.get()) {
                    double dist = this.getDistanceToOriginXZ();
                    if (dist > this.originRadius.get()) {
                        this.error("Too far from recording origin (%.1f blocks). Move closer to start.", dist);
                        this.toggle();
                        return;
                    }

                    this.state = MacroPlayer.State.PLAYING;
                    this.info("Playing macro: " + this.macroFile.name + " (" + this.macroFile.totalTicks + " ticks)");
                } else {
                    this.state = MacroPlayer.State.PLAYING;
                    this.info("Playing macro: " + this.macroFile.name + " (" + this.macroFile.totalTicks + " ticks)");
                }
            } else {
                this.error("Failed to load macro: " + this.macroName.get() + ".json");
                this.toggle();
            }
        } else {
            this.toggle();
        }
    }

    @Override
    public void onDeactivate() {
        if (this.state == MacroPlayer.State.BARITONE_PATHING) {
            this.cancelBaritone();
        }

        this.releaseAllKeys();
        this.macroFile = null;
        this.state = null;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null && this.macroFile != null && this.state != null) {
            if (this.stopOnDeath.get() && this.mc.player.isDeadOrDying()) {
                this.error("Player died, stopping playback.");
                this.toggle();
            } else {
                switch (this.state) {
                    case BARITONE_PATHING:
                        this.tickBaritonePathing();
                        break;
                    case FINE_POSITIONING:
                        this.tickFinePositioning();
                        break;
                    case PLAYING:
                        this.tickPlaying();
                        break;
                    case LOOP_DELAY:
                        this.tickLoopDelay();
                }
            }
        }
    }

    private void beginBaritoneToOrigin() {
        if (!this.isBaritoneAvailable()) {
            this.error("Baritone is not installed. Cannot path to origin.");
            this.toggle();
        } else {
            double dist = this.getDistanceToOriginXZ();
            if (dist < 1.5) {
                this.state = MacroPlayer.State.FINE_POSITIONING;
            } else {
                if (this.startBaritoneToOrigin()) {
                    this.state = MacroPlayer.State.BARITONE_PATHING;
                    this.info("Pathing to origin (%.1f blocks away)...", dist);
                } else {
                    this.error("Failed to start Baritone pathing.");
                    this.toggle();
                }
            }
        }
    }

    private void tickBaritonePathing() {
        if (!this.isBaritonePathing()) {
            this.cancelBaritone();
            this.state = MacroPlayer.State.FINE_POSITIONING;
        }
    }

    private void tickFinePositioning() {
        double dx = this.macroFile.startX - this.mc.player.getX();
        double dz = this.macroFile.startZ - this.mc.player.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        if (distXZ <= 0.15) {
            this.releaseAllKeys();
            this.mc.player.setYRot(this.macroFile.startYaw);
            this.mc.player.setXRot(this.macroFile.startPitch);
            this.currentFrame = 0;
            this.state = MacroPlayer.State.PLAYING;
            if (this.loopCount == 0) {
                this.info("Playing macro: " + this.macroFile.name + " (" + this.macroFile.totalTicks + " ticks)");
            } else {
                this.info("Repositioned. Starting loop #%d.", this.loopCount + 1);
            }
        } else {
            float targetYaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            this.mc.player.setYRot(targetYaw);
            this.mc.player.setXRot(0.0F);
            this.mc.options.keyUp.setDown(true);
            this.mc.options.keyDown.setDown(false);
            this.mc.options.keyLeft.setDown(false);
            this.mc.options.keyRight.setDown(false);
            this.mc.options.keyJump.setDown(false);
            this.mc.options.keyShift.setDown(distXZ < 0.5);
            this.mc.options.keyUse.setDown(false);
            this.mc.options.keyAttack.setDown(false);
            this.mc.player.setSprinting(false);
        }
    }

    private void tickLoopDelay() {
        this.delayCounter++;
        if (this.delayCounter >= this.loopDelay.get()) {
            this.delayCounter = 0;
            if (this.requireNearOrigin.get() && this.baritoneToOrigin.get()) {
                this.beginBaritoneToOrigin();
            } else {
                this.currentFrame = 0;
                this.state = MacroPlayer.State.PLAYING;
            }
        }
    }

    private void tickPlaying() {
        if (this.currentFrame >= this.macroFile.frames.size()) {
            if (this.loop.get()) {
                this.loopCount++;
                this.info("Loop #" + this.loopCount + " completed.");
                this.state = MacroPlayer.State.LOOP_DELAY;
                this.delayCounter = 0;
            } else {
                this.info("Playback completed.");
                this.toggle();
            }
        } else {
            MacroFrame frame = this.macroFile.frames.get(this.currentFrame);
            if (this.waitingForScreen) {
                this.screenWaitCounter++;
                if (this.screenWaitCounter > this.screenWaitTicks.get()) {
                    this.error("Expected screen did not appear, stopping.");
                    this.toggle();
                    return;
                }

                String actualScreen = this.mc.screen != null ? this.mc.screen.getClass().getSimpleName() : null;
                if (frame.currentScreenClass != null && !frame.currentScreenClass.equals(actualScreen)) {
                    return;
                }

                this.waitingForScreen = false;
                this.screenWaitCounter = 0;
            }

            if (this.screenVerify.get() && this.hasGuiActions(frame)) {
                String expectedScreen = frame.currentScreenClass;
                String actualScreen = this.mc.screen != null ? this.mc.screen.getClass().getSimpleName() : null;
                if (expectedScreen != null && !expectedScreen.equals(actualScreen)) {
                    this.waitingForScreen = true;
                    this.screenWaitCounter = 0;
                    return;
                }
            }

            float targetYaw = frame.yaw;
            float targetPitch = frame.pitch;
            if (this.rotationJitter.get()) {
                float jitter = (float)this.jitterAmount.get().doubleValue();
                targetYaw += (ThreadLocalRandom.current().nextFloat() - 0.5F) * 2.0F * jitter;
                targetPitch += (ThreadLocalRandom.current().nextFloat() - 0.5F) * 2.0F * jitter;
            }

            this.mc.player.setYRot(targetYaw);
            this.mc.player.setXRot(targetPitch);
            this.mc.options.keyUp.setDown(frame.movementForward > 0.0F);
            this.mc.options.keyDown.setDown(frame.movementForward < 0.0F);
            this.mc.options.keyLeft.setDown(frame.movementSideways > 0.0F);
            this.mc.options.keyRight.setDown(frame.movementSideways < 0.0F);
            this.mc.options.keyJump.setDown(frame.jumping);
            this.mc.options.keyShift.setDown(frame.sneaking);
            this.mc.player.setSprinting(frame.sprinting);
            this.mc.options.keyUse.setDown(frame.using);
            this.mc.options.keyAttack.setDown(frame.attacking);
            if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != frame.selectedSlot) {
                ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(frame.selectedSlot);
            }

            for (MacroAction action : frame.actions) {
                this.executeAction(action);
            }

            double drift = Math.sqrt(
                Math.pow(this.mc.player.getX() - frame.posX, 2.0) + Math.pow(this.mc.player.getZ() - frame.posZ, 2.0)
            );
            if (drift > this.positionTolerance.get()) {
                this.warning("Position drift too large (" + String.format("%.1f", drift) + " blocks), stopping.");
                this.toggle();
            } else {
                this.currentFrame++;
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.renderOrigin.get() && this.macroFile != null) {
            double x = this.macroFile.startX;
            double y = this.macroFile.startY;
            double z = this.macroFile.startZ;
            AABB box = new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
            event.renderer.box(box, this.originColor.get(), this.originLineColor.get(), ShapeMode.Both, 0);
        }
    }

    private double getDistanceToOriginXZ() {
        if (this.mc.player != null && this.macroFile != null) {
            double dx = this.mc.player.getX() - this.macroFile.startX;
            double dz = this.mc.player.getZ() - this.macroFile.startZ;
            return Math.sqrt(dx * dx + dz * dz);
        } else {
            return Double.MAX_VALUE;
        }
    }

    private boolean isBaritoneAvailable() {
        try {
            Class.forName("baritone.api.BaritoneAPI");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean startBaritoneToOrigin() {
        if (this.isBaritoneAvailable() && this.macroFile != null) {
            try {
                BlockPos originBlock = BlockPos.containing(this.macroFile.startX, this.macroFile.startY, this.macroFile.startZ);
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(originBlock));
                return true;
            } catch (Exception e) {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean isBaritonePathing() {
        if (!this.isBaritoneAvailable()) {
            return false;
        }

        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
        } catch (Exception e) {
            return false;
        }
    }

    private void cancelBaritone() {
        if (this.isBaritoneAvailable()) {
            try {
                IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
                b.getPathingBehavior().cancelEverything();
                b.getCustomGoalProcess().setGoal(null);
                b.getInputOverrideHandler().clearAllKeys();
            } catch (Exception var2) {
            }
        }
    }

    private boolean hasGuiActions(MacroFrame frame) {
        for (MacroAction action : frame.actions) {
            switch (action.type) {
                case CLICK_SLOT:
                case CONTAINER_BUTTON:
                case RENAME_ITEM:
                case SELECT_TRADE:
                    return true;
            }
        }

        return false;
    }

    private void executeAction(MacroAction action) {
        if (this.mc.player != null && this.mc.gameMode != null) {
            switch (action.type) {
                case CLICK_SLOT:
                    if (this.mc.player.containerMenu == null) {
                        return;
                    }

                    int syncId = this.mc.player.containerMenu.containerId;
                    ClickType sat = ClickType.valueOf(action.actionType);
                    this.mc.gameMode.handleInventoryMouseClick(syncId, action.slotId, action.button, sat, this.mc.player);
                    break;
                case CONTAINER_BUTTON:
                    if (this.mc.player.containerMenu == null) {
                        return;
                    }

                    this.mc.gameMode.handleInventoryButtonClick(this.mc.player.containerMenu.containerId, action.button);
                    break;
                case RENAME_ITEM:
                    if (this.mc.getConnection() != null && action.message != null) {
                        this.mc.getConnection().send(new ServerboundRenameItemPacket(action.message));
                    }
                    break;
                case SELECT_TRADE:
                    if (this.mc.getConnection() != null) {
                        this.mc.getConnection().send(new ServerboundSelectTradePacket(action.slotId));
                    }
                    break;
                case CLOSE_SCREEN:
                    this.mc.player.closeContainer();
                    break;
                case INTERACT_BLOCK: {
                    InteractionHand hand = InteractionHand.valueOf(action.hand);
                    Vec3 hitPos = new Vec3(action.hitX, action.hitY, action.hitZ);
                    BlockPos blockPos = new BlockPos(action.blockX, action.blockY, action.blockZ);
                    Direction dir = Direction.valueOf(action.direction);
                    BlockHitResult hit = new BlockHitResult(hitPos, dir, blockPos, false);
                    this.mc.gameMode.useItemOn(this.mc.player, hand, hit);
                    break;
                }
                case ATTACK_BLOCK: {
                    BlockPos pos = new BlockPos(action.blockX, action.blockY, action.blockZ);
                    Direction dir = Direction.valueOf(action.direction);
                    this.mc.gameMode.startDestroyBlock(pos, dir);
                    break;
                }
                case INTERACT_ITEM: {
                    InteractionHand hand = InteractionHand.valueOf(action.hand);
                    this.mc.gameMode.useItem(this.mc.player, hand);
                    break;
                }
                case SWAP_HOTBAR_SLOT:
                    ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(action.hotbarSlot);
                    break;
                case DROP_ITEM:
                    if (this.mc.getConnection() == null) {
                        return;
                    }

                    Action dropAction = action.dropEntireStack ? Action.DROP_ALL_ITEMS : Action.DROP_ITEM;
                    this.mc.getConnection().send(new ServerboundPlayerActionPacket(dropAction, BlockPos.ZERO, Direction.DOWN));
                    break;
                case SEND_CHAT:
                    if (this.mc.player.connection != null && action.message != null) {
                        if (action.message.startsWith("/")) {
                            this.mc.player.connection.sendCommand(action.message.substring(1));
                        } else {
                            this.mc.player.connection.sendChat(action.message);
                        }
                    }
                    break;
                case INTERACT_ENTITY:
                    if (this.mc.level == null || action.entityType == null) {
                        return;
                    }

                    double targetX;
                    double targetY;
                    double targetZ;
                    if (action.entityAbsX == 0.0 && action.entityAbsY == 0.0 && action.entityAbsZ == 0.0) {
                        targetX = this.mc.player.getX() + action.entityDx;
                        targetY = this.mc.player.getY() + action.entityDy;
                        targetZ = this.mc.player.getZ() + action.entityDz;
                    } else {
                        targetX = action.entityAbsX;
                        targetY = action.entityAbsY;
                        targetZ = action.entityAbsZ;
                    }

                    Entity closest = null;
                    double closestDist = 5.0;

                    for (Entity entity : this.mc.level.entitiesForRendering()) {
                        String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                        if (type.equals(action.entityType)) {
                            double dist = Math.sqrt(
                                Math.pow(entity.getX() - targetX, 2.0)
                                    + Math.pow(entity.getY() - targetY, 2.0)
                                    + Math.pow(entity.getZ() - targetZ, 2.0)
                            );
                            if (dist < closestDist) {
                                closestDist = dist;
                                closest = entity;
                            }
                        }
                    }

                    if (closest != null) {
                        InteractionHand handx = action.hand != null ? InteractionHand.valueOf(action.hand) : InteractionHand.MAIN_HAND;
                        Vec3 entityCenter = new Vec3(
                            closest.getX(), closest.getY() + closest.getBbHeight() / 2.0F, closest.getZ()
                        );
                        EntityHitResult entityHitResult = new EntityHitResult(closest, entityCenter);
                        this.mc.gameMode.interactAt(this.mc.player, closest, entityHitResult, handx);
                        this.mc.gameMode.interact(this.mc.player, closest, handx);
                    }
            }
        }
    }

    private void releaseAllKeys() {
        if (this.mc.options != null) {
            this.mc.options.keyUp.setDown(false);
            this.mc.options.keyDown.setDown(false);
            this.mc.options.keyLeft.setDown(false);
            this.mc.options.keyRight.setDown(false);
            this.mc.options.keyJump.setDown(false);
            this.mc.options.keyShift.setDown(false);
            this.mc.options.keyUse.setDown(false);
            this.mc.options.keyAttack.setDown(false);
            if (this.mc.player != null) {
                this.mc.player.setSprinting(false);
            }
        }
    }

    @Override
    public String getInfoString() {
        if (this.macroFile != null && this.state != null) {
            return switch (this.state) {
                case BARITONE_PATHING -> "Walking to origin";
                case FINE_POSITIONING -> String.format("Adjusting (%.2f)", this.getDistanceToOriginXZ());
                case PLAYING -> this.waitingForScreen
                    ? "Waiting for screen"
                    : this.currentFrame + "/" + this.macroFile.totalTicks + (this.loop.get() ? " (L" + this.loopCount + ")" : "");
                case LOOP_DELAY -> "Loop delay";
            };
        } else {
            return null;
        }
    }

    private enum State {
        BARITONE_PATHING,
        FINE_POSITIONING,
        PLAYING,
        LOOP_DELAY;
    }
}
