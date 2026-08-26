package bep.hax.util.prox.emote;

import bep.hax.mixin.accessor.ModelPartAccessor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class EmoteButt {
    public static final String PART_NAME = "bephax_butt";
    private static final float ROUND = (float) (Math.PI / 4);

    private EmoteButt() {
    }

    public static ModelPart attach(ModelPart body) {
        MeshDefinition data = new MeshDefinition();
        PartDefinition butt = data.getRoot()
            .addOrReplaceChild(
                "bephax_butt",
                CubeListBuilder.create()
                    .texOffs(0, 20)
                    .addBox(-3.6F, -1.0F, -0.8F, 3.4F, 3.4F, 2.6F)
                    .texOffs(16, 52)
                    .addBox(0.2F, -1.0F, -0.8F, 3.4F, 3.4F, 2.6F)
                    .texOffs(2, 20)
                    .addBox(-1.2F, -0.6F, -0.8F, 2.4F, 2.4F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 11.0F, 2.0F, 0.0F, 0.0F, 0.0F)
            );
        butt.addOrReplaceChild(
            "bephax_butt_r1",
            CubeListBuilder.create().texOffs(0, 20).addBox(-1.55F, -1.55F, -1.1F, 3.1F, 3.1F, 2.2F),
            PartPose.offsetAndRotation(-1.9F, 0.7F, 0.5F, 0.0F, 0.0F, (float) (Math.PI / 4))
        );
        butt.addOrReplaceChild(
            "bephax_butt_r2",
            CubeListBuilder.create().texOffs(0, 20).addBox(-1.55F, -1.5F, -1.1F, 3.1F, 3.0F, 2.2F),
            PartPose.offsetAndRotation(-1.9F, 0.7F, 0.5F, (float) (Math.PI / 4), 0.0F, 0.0F)
        );
        butt.addOrReplaceChild(
            "bephax_butt_l1",
            CubeListBuilder.create().texOffs(16, 52).addBox(-1.55F, -1.55F, -1.1F, 3.1F, 3.1F, 2.2F),
            PartPose.offsetAndRotation(1.9F, 0.7F, 0.5F, 0.0F, 0.0F, (float) (Math.PI / 4))
        );
        butt.addOrReplaceChild(
            "bephax_butt_l2",
            CubeListBuilder.create().texOffs(16, 52).addBox(-1.55F, -1.5F, -1.1F, 3.1F, 3.0F, 2.2F),
            PartPose.offsetAndRotation(1.9F, 0.7F, 0.5F, (float) (Math.PI / 4), 0.0F, 0.0F)
        );
        ModelPart part = LayerDefinition.create(data, 64, 64).bakeRoot().getChild("bephax_butt");
        part.visible = false;
        ((ModelPartAccessor)(Object)body).getChildren().put("bephax_butt", part);
        return part;
    }
}
