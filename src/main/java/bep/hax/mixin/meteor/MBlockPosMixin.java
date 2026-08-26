package bep.hax.mixin.meteor;

import bep.hax.accessor.IHighwayBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder;
import meteordevelopment.meteorclient.utils.misc.MBlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MBlockPos.class, remap = false)
public abstract class MBlockPosMixin {
    @Inject(method = "coerceBlockLevel", at = @At("RETURN"))
    private void bephax$lockToHighwayLine(Entity entity, CallbackInfoReturnable<MBlockPos> cir) {
        if (MeteorClient.mc.player != null && entity == MeteorClient.mc.player) {
            HighwayBuilder hb = Modules.get().get(HighwayBuilder.class);
            if (hb != null && hb.isActive() && hb instanceof IHighwayBuilder ihb) {
                if (ihb.bephax$shouldLockLine()) {
                    ihb.bephax$snapToLine((MBlockPos)(Object)this);
                }
            }
        }
    }
}
