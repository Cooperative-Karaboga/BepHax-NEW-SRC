package bep.hax.mixin;

import bep.hax.modules.NoHurtCam;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void onTiltViewWhenHurt(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules != null) {
            NoHurtCam noHurtCam = modules.get(NoHurtCam.class);
            if (noHurtCam != null && noHurtCam.shouldDisableHurtCam()) {
                ci.cancel();
            }
        }
    }
}
