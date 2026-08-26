package bep.hax.mixin;

import bep.hax.accessor.PatEntityIdAccess;
import bep.hax.modules.Proximity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererPatMixin {
    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", ordinal = 0))
    private void bephax$applyPatSquish(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera, CallbackInfo ci) {
        int id = ((PatEntityIdAccess)state).bephax$getPatEntityId();
        if (id != -1) {
            float squish = Proximity.getSquish(id, state.boundingBoxHeight);
            if (squish != 1.0F) {
                matrices.scale(1.0F, squish, 1.0F);
            }
        }
    }
}
