package bep.hax.managers;

import bep.hax.config.BepConfig;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;

public class PacketManager {
    public PacketManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler(priority = 200)
    private void onReceivePacket(Receive event) {
        if (Utils.canUpdate()) {
            if (BepConfig.ignoreOverlayMessages.get()) {
                if (event.packet instanceof ClientboundSetActionBarTextPacket packet) {
                    if (!BepConfig.overlayMessageFilter.get().isEmpty() && !BepConfig.overlayMessageFilter.get().stream().allMatch(String::isBlank)) {
                        for (String filter : BepConfig.overlayMessageFilter.get()) {
                            if (!filter.isBlank() && packet.text().getString().equalsIgnoreCase(filter)) {
                                event.cancel();
                            }
                        }
                    }
                }
            }
        }
    }
}
