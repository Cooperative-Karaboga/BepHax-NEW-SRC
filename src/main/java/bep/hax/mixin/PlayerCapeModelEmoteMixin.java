package bep.hax.mixin;

import bep.hax.accessor.EmotePoseAccess;
import bep.hax.util.prox.emote.EmoteApplier;
import bep.hax.util.prox.emote.EmotePose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerCapeModel.class)
public abstract class PlayerCapeModelEmoteMixin {
    @Shadow
    @Final
    private ModelPart cape;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void bephax$applyEmoteCape(AvatarRenderState state, CallbackInfo ci) {
        EmotePose pose = ((EmotePoseAccess)state).bephax$getEmotePose();
        if (pose != null) {
            EmoteApplier.applyCape(pose, this.cape);
        }
    }
}
