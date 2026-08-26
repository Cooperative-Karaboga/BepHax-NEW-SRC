package bep.hax.mixin.accessor;

import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BundlePacket.class)
public interface BundleS2CPacketAccessor {
    @Mutable
    @Accessor("packets")
    void setPackets(Iterable<Packet<?>> var1);
}
