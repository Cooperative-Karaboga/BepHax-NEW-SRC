package bep.hax.mixin;

import bep.hax.accessor.EmotePoseAccess;
import bep.hax.util.prox.emote.EmotePose;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class PlayerEntityRenderStateEmoteMixin implements EmotePoseAccess {
    @Unique
    private EmotePose bephax$emotePose;

    @Override
    public EmotePose bephax$getEmotePose() {
        return this.bephax$emotePose;
    }

    @Override
    public void bephax$setEmotePose(EmotePose pose) {
        this.bephax$emotePose = pose;
    }
}
