package bep.hax.mixin.accessor;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Connection.class)
public interface ClientConnectionAccessor {
    @Invoker("sendPacket")
    void invokeSendImmediately(Packet<?> var1, @Nullable ChannelFutureListener var2, boolean var3);
}
