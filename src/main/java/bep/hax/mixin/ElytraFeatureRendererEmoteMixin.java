package bep.hax.mixin;

import bep.hax.accessor.EmotePoseAccess;
import bep.hax.util.prox.emote.EmotePose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WingsLayer.class)
public abstract class ElytraFeatureRendererEmoteMixin<S extends HumanoidRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
    private ElytraFeatureRendererEmoteMixin(RenderLayerParent<S, M> context) {
        super(context);
    }

    @Inject(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V"
        )
    )
    private void bephax$followAnimatedChest(PoseStack matrices, SubmitNodeCollector queue, int light, S state, float yRot, float xRot, CallbackInfo ci) {
        if (state instanceof EmotePoseAccess access) {
            EmotePose pose = access.bephax$getEmotePose();
            if (pose != null && this.getParentModel() instanceof PlayerModel playerModel) {
                playerModel.body.translateAndRotate(matrices);
                EmotePose.Bone cape = pose.bone("cape");
                if (cape != null && cape.animated) {
                    float weight = pose.weight();
                    matrices.translate(0.0F, 0.0F, 0.125F);
                    matrices.translate(cape.x * weight / 16.0F, -cape.y * weight / 16.0F, cape.z * weight / 16.0F);
                    matrices.last().pose().rotateZYX(cape.rotZ * weight, cape.rotY * weight, cape.rotX * weight);
                    matrices.translate(0.0F, 0.0F, -0.125F);
                }
            }
        }
    }
}
