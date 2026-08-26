package bep.hax.mixin;

import bep.hax.modules.ControlFly;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.FireworkParticles.OverlayParticle;
import net.minecraft.client.particle.FireworkParticles.SparkParticle;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SparkParticle.class, OverlayParticle.class})
public abstract class FireworksSparkParticleMixin {
    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void bephax$hideRocketParticles(QuadParticleRenderState arg, Camera camera, float f, CallbackInfo ci) {
        if (ControlFly.INSTANCE != null && ControlFly.INSTANCE.shouldHideRocketParticles()) {
            ci.cancel();
        }
    }
}
