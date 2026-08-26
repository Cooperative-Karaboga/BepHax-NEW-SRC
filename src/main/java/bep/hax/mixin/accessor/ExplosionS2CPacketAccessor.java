package bep.hax.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundExplodePacket.class)
public interface ExplosionS2CPacketAccessor {
    @Accessor("center")
    Vec3 getCenter();
}
