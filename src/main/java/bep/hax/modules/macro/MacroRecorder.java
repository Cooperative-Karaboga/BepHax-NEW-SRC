package bep.hax.modules.macro;

import bep.hax.Bep;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.InventoryManager;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class MacroRecorder extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<String> macroName = this.sgGeneral
        .add(new Builder().name("macro-name").description("Name for the saved macro file.").defaultValue("macro").build());
    private final Setting<Boolean> recordMovement = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("record-movement")
                .description("Record position and rotation changes.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> recordGuiClicks = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("record-gui-clicks")
                .description("Record inventory and GUI slot clicks.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> recordInteractions = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("record-interactions")
                .description("Record block and entity interactions.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> recordChat = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("record-chat")
                .description("Record chat messages sent.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> autoStopTicks = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("auto-stop-ticks")
                .description("Automatically stop recording after this many ticks (0 = disabled).")
                .defaultValue(0)
                .min(0)
                .sliderMax(6000)
                .build()
        );
    private MacroFile macroFile;
    private final List<MacroAction> pendingActions = new ArrayList<>();

    public MacroRecorder() {
        super(Bep.CATEGORY, "macro-recorder", "Records player actions to a macro file.");
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null && this.mc.level != null) {
            this.macroFile = new MacroFile();
            this.macroFile.name = this.macroName.get();
            this.macroFile.recordedAt = System.currentTimeMillis();
            this.macroFile.dimension = this.mc.level.dimension().identifier().toString();
            this.macroFile.startX = this.mc.player.getX();
            this.macroFile.startY = this.mc.player.getY();
            this.macroFile.startZ = this.mc.player.getZ();
            this.macroFile.startYaw = this.mc.player.getYRot();
            this.macroFile.startPitch = this.mc.player.getXRot();
            this.pendingActions.clear();
            this.info("Recording started. Name: " + this.macroName.get());
        } else {
            this.toggle();
        }
    }

    @Override
    public void onDeactivate() {
        if (this.macroFile != null && !this.macroFile.frames.isEmpty()) {
            this.macroFile.totalTicks = this.macroFile.frames.size();
            MacroFileManager.save(this.macroFile);
            this.info("Recording saved: " + this.macroFile.name + ".json (" + this.macroFile.totalTicks + " ticks)");
        }

        this.macroFile = null;
        this.pendingActions.clear();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null && this.macroFile != null) {
            MacroFrame frame = new MacroFrame();
            if (this.recordMovement.get()) {
                frame.yaw = this.mc.player.getYRot();
                frame.pitch = this.mc.player.getXRot();
                frame.posX = this.mc.player.getX();
                frame.posY = this.mc.player.getY();
                frame.posZ = this.mc.player.getZ();
            }

            frame.movementForward = this.mc.options.keyUp.isDown() ? 1.0F : (this.mc.options.keyDown.isDown() ? -1 : 0);
            frame.movementSideways = this.mc.options.keyLeft.isDown() ? 1.0F : (this.mc.options.keyRight.isDown() ? -1 : 0);
            frame.jumping = this.mc.options.keyJump.isDown();
            frame.sneaking = this.mc.options.keyShift.isDown();
            frame.sprinting = this.mc.player.isSprinting();
            frame.using = this.mc.options.keyUse.isDown();
            frame.attacking = this.mc.options.keyAttack.isDown();
            frame.selectedSlot = ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot();
            frame.currentScreenClass = this.mc.screen != null ? this.mc.screen.getClass().getSimpleName() : null;
            synchronized (this.pendingActions) {
                frame.actions.addAll(this.pendingActions);
                this.pendingActions.clear();
            }

            this.macroFile.frames.add(frame);
            if (this.autoStopTicks.get() > 0 && this.macroFile.frames.size() >= this.autoStopTicks.get()) {
                this.info("Auto-stop reached (" + this.autoStopTicks.get() + " ticks).");
                this.toggle();
            }
        }
    }

    @EventHandler
    private void onPacketSend(Send event) {
        if (this.macroFile != null) {
            MacroAction action = null;
            if (this.recordGuiClicks.get() && event.packet instanceof ServerboundContainerClickPacket packet) {
                String screenHandler = this.mc.player != null && this.mc.player.containerMenu != null
                    ? this.mc.player.containerMenu.getClass().getSimpleName()
                    : "Unknown";
                action = MacroAction.clickSlot(packet.slotNum(), packet.buttonNum(), packet.clickType().name(), screenHandler);
            } else if (this.recordGuiClicks.get() && event.packet instanceof ServerboundContainerButtonClickPacket packet) {
                action = MacroAction.containerButton(packet.buttonId());
            } else if (this.recordGuiClicks.get() && event.packet instanceof ServerboundRenameItemPacket packet) {
                action = MacroAction.renameItem(packet.getName());
            } else if (this.recordGuiClicks.get() && event.packet instanceof ServerboundSelectTradePacket packet) {
                action = MacroAction.selectTrade(packet.getItem());
            } else if (event.packet instanceof ServerboundContainerClosePacket) {
                action = MacroAction.closeScreen();
            } else if (this.recordInteractions.get() && event.packet instanceof ServerboundUseItemOnPacket packet) {
                BlockPos pos = packet.getHitResult().getBlockPos();
                Vec3 hit = packet.getHitResult().getLocation();
                action = MacroAction.interactBlock(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    hit.x,
                    hit.y,
                    hit.z,
                    packet.getHitResult().getDirection().name(),
                    packet.getHand().name()
                );
            } else if (this.recordInteractions.get() && event.packet instanceof ServerboundUseItemPacket packet) {
                action = MacroAction.interactItem(packet.getHand().name());
            } else if (event.packet instanceof ServerboundPlayerActionPacket packet) {
                Action packetAction = packet.getAction();
                if (packetAction == Action.DROP_ITEM) {
                    action = MacroAction.dropItem(false);
                } else if (packetAction == Action.DROP_ALL_ITEMS) {
                    action = MacroAction.dropItem(true);
                } else if (this.recordInteractions.get() && (packetAction == Action.START_DESTROY_BLOCK || packetAction == Action.STOP_DESTROY_BLOCK)) {
                    action = MacroAction.attackBlock(
                        packet.getPos().getX(),
                        packet.getPos().getY(),
                        packet.getPos().getZ(),
                        packet.getDirection().name()
                    );
                }
            } else if (event.packet instanceof ServerboundSetCarriedItemPacket packet) {
                action = MacroAction.swapHotbarSlot(packet.getSlot());
            } else if (this.recordChat.get() && event.packet instanceof ServerboundChatPacket packet) {
                action = MacroAction.sendChat(packet.message());
            } else if (this.recordInteractions.get() && event.packet instanceof ServerboundInteractPacket packet && this.mc.player != null && this.mc.level != null) {
                InventoryManager.IPlayerInteractEntityC2SPacket accessor = (InventoryManager.IPlayerInteractEntityC2SPacket)packet;
                int entityId = accessor.getTargetEntityId();
                Entity entity = this.mc.level.getEntity(entityId);
                if (entity != null) {
                    String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                    double dx = entity.getX() - this.mc.player.getX();
                    double dy = entity.getY() - this.mc.player.getY();
                    double dz = entity.getZ() - this.mc.player.getZ();
                    action = MacroAction.interactEntity(type, dx, dy, dz, entity.getX(), entity.getY(), entity.getZ(), "MAIN_HAND");
                }
            }

            if (action != null) {
                synchronized (this.pendingActions) {
                    this.pendingActions.add(action);
                }
            }
        }
    }

    @Override
    public String getInfoString() {
        return this.macroFile != null ? this.macroFile.frames.size() + " ticks" : null;
    }
}
