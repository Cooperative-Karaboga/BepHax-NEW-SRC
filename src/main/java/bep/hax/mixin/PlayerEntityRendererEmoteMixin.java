package bep.hax.mixin;

import bep.hax.accessor.EmotePoseAccess;
import bep.hax.util.prox.emote.EmoteApplier;
import bep.hax.util.prox.emote.EmoteEngine;
import bep.hax.util.prox.emote.EmotePose;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerEntityRendererEmoteMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void bephax$sampleEmote(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        ((EmotePoseAccess)state).bephax$setEmotePose(EmoteEngine.get().pose(entity.getUUID(), partialTick));
    }

    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void bephax$applyRootBone(AvatarRenderState state, PoseStack matrices, float bodyYaw, float scale, CallbackInfo ci) {
        EmotePose pose = ((EmotePoseAccess)state).bephax$getEmotePose();
        if (pose != null) {
            EmotePose.Bone body = pose.bone("body");
            if (body != null && body.animated) {
                float weight = pose.weight();
                matrices.translate(body.x / 16.0F * weight, body.y / 16.0F * weight, body.z / 16.0F * weight);
                if (body.rotX != 0.0F || body.rotY != 0.0F || body.rotZ != 0.0F) {
                    matrices.last().pose().rotateZYX(body.rotZ * weight, body.rotY * weight, body.rotX * weight);
                }
            }
        }
    }

    @Inject(
        method = "renderHand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
        )
    )
    private void bephax$animateFirstPersonArm(
        PoseStack matrices, SubmitNodeCollector queue, int light, Identifier texture, ModelPart arm, boolean showSleeve, CallbackInfo ci
    ) {
        if (MeteorClient.mc.player != null) {
            EmotePose pose = EmoteEngine.get().pose(MeteorClient.mc.player.getUUID(), MeteorClient.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
            if (pose != null) {
                PlayerModel model = ((AvatarRenderer<?>)(Object)this).getModel();
                String bone = arm == model.leftArm ? "left_arm" : "right_arm";
                EmoteApplier.apply(pose, bone, arm);
            }
        }
    }
}
