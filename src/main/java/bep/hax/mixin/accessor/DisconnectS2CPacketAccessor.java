package bep.hax.mixin.accessor;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundDisconnectPacket.class)
public interface DisconnectS2CPacketAccessor {
    @Mutable
    @Accessor("reason")
    void setReason(Component var1);
}
