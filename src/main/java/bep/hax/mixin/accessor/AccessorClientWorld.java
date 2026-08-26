package bep.hax.mixin.accessor;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientLevel.class)
public interface AccessorClientWorld {
    @Invoker("playSound")
    void hookPlaySound(double var1, double var3, double var5, SoundEvent var7, SoundSource var8, float var9, float var10, boolean var11, long var12);
}
