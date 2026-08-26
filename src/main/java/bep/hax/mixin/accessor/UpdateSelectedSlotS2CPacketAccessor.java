package bep.hax.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundSetHeldSlotPacket.class)
public interface UpdateSelectedSlotS2CPacketAccessor {
    @Accessor("slot")
    int getSlot();
}
