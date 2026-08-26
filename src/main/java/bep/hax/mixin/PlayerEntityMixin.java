package bep.hax.mixin;

import bep.hax.util.PushFluidsEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEntityMixin extends LivingEntity {
    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true)
    private void hookIsPushedByFluids(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this == MeteorClient.mc.player) {
            PushFluidsEvent pushFluidsEvent = new PushFluidsEvent();
            MeteorClient.EVENT_BUS.post(pushFluidsEvent);
            if (pushFluidsEvent.isCanceled()) {
                cir.setReturnValue(false);
                cir.cancel();
            }
        }
    }
}
