package bep.hax.modules;

import bep.hax.Bep;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;

public class GhostContainer extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public final Setting<String> buttonText = this.sgGeneral
        .add(new Builder().name("button-text").description("Label shown on the ghost-close button.").defaultValue("Ghost").build());
    public final Setting<Integer> buttonWidth = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("button-width")
                .description("Width of the ghost-close button.")
                .defaultValue(54)
                .min(20)
                .sliderRange(20, 160)
                .build()
        );
    public final Setting<Integer> offsetX = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("offset-x")
                .description("Horizontal offset from the container's top-left corner.")
                .defaultValue(94)
                .sliderRange(-200, 200)
                .build()
        );
    public final Setting<Integer> offsetY = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("offset-y")
                .description("Vertical offset from the container's top-left corner.")
                .defaultValue(-92)
                .sliderRange(-200, 200)
                .build()
        );
    public final Setting<Boolean> interceptClosePacket = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("intercept-close-packet")
                .description(
                    "Cancel every CloseHandledScreenC2SPacket while active, so ESC/E also ghost-close. Catch-all even when other code calls closeHandledScreen()."
                )
                .defaultValue(false)
                .build()
        );
    public final Setting<Boolean> notify = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("notify")
                .description("Print a chat message when a container is left open server-side.")
                .defaultValue(true)
                .build()
        );

    public GhostContainer() {
        super(
            Bep.CATEGORY,
            "ghost-container",
            "Adds a button to container GUIs that exits without sending the close packet, leaving the container open server-side (Paper/Folia desync)."
        );
    }

    public boolean canGhost() {
        if (this.mc.player == null || this.mc.level == null) {
            return false;
        }

        if (!(this.mc.screen instanceof AbstractContainerScreen)) {
            return false;
        }

        AbstractContainerMenu handler = this.mc.player.containerMenu;
        return handler != null && !(handler instanceof InventoryMenu);
    }

    public void ghostClose() {
        if (this.canGhost()) {
            int syncId = this.mc.player.containerMenu.containerId;
            this.mc.setScreen(null);
            if (this.notify.get()) {
                this.info("Container left open server-side (syncId (highlight)%d(default)).", syncId);
            }
        }
    }

    @EventHandler
    private void onSend(Send event) {
        if (this.interceptClosePacket.get()) {
            if (event.packet instanceof ServerboundContainerClosePacket) {
                if (this.mc.player != null && !(this.mc.player.containerMenu instanceof InventoryMenu)) {
                    event.cancel();
                }
            }
        }
    }
}
