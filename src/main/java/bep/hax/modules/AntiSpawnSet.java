package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.MsgUtil;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

public class AntiSpawnSet extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> blockBeds = this.sgGeneral
        .add(new Builder().name("block-beds").description("Prevents setting spawn on beds.").defaultValue(true).build());
    private final Setting<Boolean> blockAnchors = this.sgGeneral
        .add(new Builder().name("block-anchors").description("Prevents setting spawn on respawn anchors.").defaultValue(true).build());
    private final Setting<Boolean> notify = this.sgGeneral
        .add(new Builder().name("notify").description("Sends a chat message when a spawn set attempt is blocked.").defaultValue(true).build());

    public AntiSpawnSet() {
        super(Bep.CATEGORY, "anti-spawn-set", "Prevents setting your spawn point on beds or respawn anchors.");
    }

    @EventHandler
    private void onSendPacket(Send event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (event.packet instanceof ServerboundUseItemOnPacket packet) {
                BlockPos pos = packet.getHitResult().getBlockPos();
                BlockState state = this.mc.level.getBlockState(pos);
                if (this.blockBeds.get() && state.getBlock() instanceof BedBlock) {
                    event.cancel();
                    if (this.notify.get()) {
                        MsgUtil.sendModuleMsg("Blocked bed spawn set.", this.name);
                    }
                }

                if (this.blockAnchors.get() && state.getBlock() instanceof RespawnAnchorBlock) {
                    event.cancel();
                    if (this.notify.get()) {
                        MsgUtil.sendModuleMsg("Blocked respawn anchor interaction.", this.name);
                    }
                }
            }
        }
    }
}
