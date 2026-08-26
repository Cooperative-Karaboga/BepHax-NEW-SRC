package bep.hax.mixin;

import bep.hax.accessor.PatEntityIdAccess;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererPatStateMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void bephax$captureEntityId(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        ((PatEntityIdAccess)state).bephax$setPatEntityId(entity.getId());
    }
}
