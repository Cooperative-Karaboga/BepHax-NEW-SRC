package bep.hax.mixin;

import bep.hax.modules.ElytraBounce;
import bep.hax.util.RotationUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void bephax$stableBouncePose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        if (entity == MeteorClient.mc.player) {
            ElytraBounce elytraBounce = Modules.get().get(ElytraBounce.class);
            if (elytraBounce != null && elytraBounce.isBounceRenderStabilized()) {
                RotationUtils rotations = RotationUtils.getInstance();
                state.bodyRot = Mth.wrapDegrees(rotations.getServerYaw());
                state.yRot = 0.0F;
                state.xRot = 0.0F;
                state.isFallFlying = true;
                state.fallFlyingTimeInTicks = 20.0F;
                state.shouldApplyFlyingYRot = false;
                state.flyingYRot = 0.0F;
                state.walkAnimationPos = 0.0F;
                state.walkAnimationSpeed = 0.0F;
                state.elytraRotX = (float) (Math.PI / 9);
                state.elytraRotY = 0.0F;
                state.elytraRotZ = (float) (-Math.PI / 2);
            }
        }
    }
}
