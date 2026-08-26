package bep.hax.mixin;

import bep.hax.accessor.EmotePoseAccess;
import bep.hax.util.LogUtil;
import bep.hax.util.prox.emote.EmoteApplier;
import bep.hax.util.prox.emote.EmoteButt;
import bep.hax.util.prox.emote.EmotePose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerEntityModelEmoteMixin {
    @Unique
    private ModelPart bephax$butt;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bephax$attachButt(CallbackInfo ci) {
        if (!((Object)this instanceof PlayerCapeModel)) {
            try {
                this.bephax$butt = EmoteButt.attach(((PlayerModel)(Object)this).body);
            } catch (Throwable t) {
                LogUtil.warn("Could not attach emote butt part: " + t, "ProxChat");
            }
        }
    }

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void bephax$applyEmote(AvatarRenderState state, CallbackInfo ci) {
        EmotePose pose = ((EmotePoseAccess)state).bephax$getEmotePose();
        boolean butt = pose != null && pose.isAnimated("butt") && this.bephax$butt != null;
        if (this.bephax$butt != null) {
            this.bephax$butt.visible = butt;
        }

        if (pose != null) {
            PlayerModel self = (PlayerModel)(Object)this;
            EmoteApplier.apply(pose, "head", self.head);
            EmoteApplier.apply(pose, "torso", self.body);
            EmoteApplier.apply(pose, "right_arm", self.rightArm);
            EmoteApplier.apply(pose, "left_arm", self.leftArm);
            EmoteApplier.apply(pose, "right_leg", self.rightLeg);
            EmoteApplier.apply(pose, "left_leg", self.leftLeg);
            if (butt) {
                EmoteApplier.apply(pose, "butt", this.bephax$butt);
                this.bephax$butt.xScale = this.bephax$butt.yScale = this.bephax$butt.zScale = pose.weight();
            }
        }
    }
}
