package bep.hax.mixin.meteor;

import java.util.Set;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.misc.PacketCanceller;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PacketCanceller.class, remap = false)
public class PacketCancellerMixin extends Module {
    @Shadow
    @Final
    private Setting<Set<Class<? extends Packet<?>>>> c2sPackets;

    public PacketCancellerMixin(Category category, String name, String description, String... aliases) {
        super(category, name, description, aliases);
    }

    @Inject(method = "onSendPacket", at = @At("HEAD"))
    private void silenceBoatPaddles(Send event, CallbackInfo ci) {
        if (this.c2sPackets.get().contains(ServerboundPaddleBoatPacket.class)
            && event.packet instanceof ServerboundPaddleBoatPacket
            && this.mc.player != null
            && this.mc.player.getControlledVehicle() instanceof AbstractBoat boat) {
            boat.setPaddleState(false, false);
            if (this.mc.getConnection() != null) {
                this.mc.getConnection().getConnection().send(new ServerboundPaddleBoatPacket(false, false));
            }
        }
    }
}
