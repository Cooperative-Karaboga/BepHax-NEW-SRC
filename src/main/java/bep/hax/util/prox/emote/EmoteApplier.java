package bep.hax.util.prox.emote;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

public final class EmoteApplier {
    private EmoteApplier() {
    }

    public static void apply(EmotePose pose, String boneName, ModelPart part) {
        EmotePose.Bone bone = pose.bone(boneName);
        if (bone != null && bone.animated && part != null) {
            PartPose initial = part.getInitialPose();
            float weight = pose.weight();
            part.x = lerp(weight, part.x, bone.x + initial.x());
            part.y = lerp(weight, part.y, -bone.y + initial.y());
            part.z = lerp(weight, part.z, bone.z + initial.z());
            part.xRot = lerp(weight, part.xRot, bone.rotX);
            part.yRot = lerp(weight, part.yRot, bone.rotY + initial.yRot());
            part.zRot = lerp(weight, part.zRot, bone.rotZ);
        }
    }

    public static void applyCape(EmotePose pose, ModelPart part) {
        EmotePose.Bone bone = pose.bone("cape");
        if (bone != null && bone.animated && part != null) {
            float weight = pose.weight();
            part.x = part.x + bone.x * weight;
            part.y = part.y + -bone.y * weight;
            part.z = part.z + bone.z * weight;
            part.xRot = part.xRot + bone.rotX * weight;
            part.yRot = part.yRot + bone.rotY * weight;
            part.zRot = part.zRot + bone.rotZ * weight;
        }
    }

    private static float lerp(float weight, float from, float to) {
        return from + (to - from) * weight;
    }
}
