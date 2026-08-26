package bep.hax.util.prox.emote;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class EmotePose {
    private static final Map<String, float[]> PIVOTS = Map.of(
        "right_arm",
        new float[]{5.0F, 22.0F, 0.0F},
        "left_arm",
        new float[]{-5.0F, 22.0F, 0.0F},
        "head",
        new float[]{0.0F, 24.0F, 0.0F},
        "cape",
        new float[]{0.0F, 24.0F, 2.0F},
        "torso",
        new float[]{0.0F, 24.0F, 0.0F},
        "body",
        new float[]{0.0F, 12.0F, 0.0F},
        "right_leg",
        new float[]{2.0F, 12.0F, 0.0F},
        "left_leg",
        new float[]{-2.0F, 12.0F, 0.0F},
        "butt",
        new float[]{0.0F, 13.0F, 2.0F}
    );
    private final Map<String, EmotePose.Bone> bones = new HashMap<>();
    private float weight;

    public EmotePose() {
        for (String name : PIVOTS.keySet()) {
            this.bones.put(name, new EmotePose.Bone());
        }
    }

    public float weight() {
        return this.weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }

    public EmotePose.Bone bone(String name) {
        return this.bones.get(name);
    }

    public boolean isAnimated(String name) {
        EmotePose.Bone bone = this.bones.get(name);
        return bone != null && bone.animated;
    }

    void resetAll() {
        for (EmotePose.Bone bone : this.bones.values()) {
            bone.reset();
        }

        this.weight = 0.0F;
    }

    void sample(EmoteAnimation animation, float tick, boolean loopStarted) {
        this.resetAll();

        for (Entry<String, EmoteBoneTrack> entry : animation.bones().entrySet()) {
            EmotePose.Bone bone = this.bones.get(entry.getKey());
            if (bone != null) {
                EmoteBoneTrack track = entry.getValue();
                bone.x = animation.sample(track.posX, tick, loopStarted);
                bone.y = animation.sample(track.posY, tick, loopStarted);
                bone.z = animation.sample(track.posZ, tick, loopStarted);
                bone.rotX = animation.sample(track.rotX, tick, loopStarted);
                bone.rotY = animation.sample(track.rotY, tick, loopStarted);
                bone.rotZ = animation.sample(track.rotZ, tick, loopStarted);
                bone.bend = animation.sample(track.bend, tick, loopStarted);
                bone.animated = true;
            }
        }

        this.applyChestBend(animation);
    }

    private void applyChestBend(EmoteAnimation animation) {
        if (animation.applyBendToOtherBones()) {
            EmotePose.Bone torso = this.bones.get("torso");
            if (torso != null && torso.animated && !(Math.abs(torso.bend) <= 0.001F)) {
                for (String name : EmoteAnimation.BEND_CARRIED) {
                    EmotePose.Bone bone = this.bones.get(name);
                    if (bone != null) {
                        float[] pivot = PIVOTS.get(name);
                        if (pivot != null) {
                            Matrix4f matrix = new Matrix4f();
                            matrix.translate(0.0F, 18.0F, 0.0F);
                            matrix.rotateX(torso.bend);
                            matrix.translate(0.0F, -18.0F, 0.0F);
                            matrix.translate(pivot[0], pivot[1], -pivot[2]);
                            matrix.translate(-bone.x, bone.y, -bone.z);
                            if (bone.rotX != 0.0F || bone.rotY != 0.0F || bone.rotZ != 0.0F) {
                                matrix.rotateZYX(bone.rotZ, bone.rotY, bone.rotX);
                            }

                            bone.x = -matrix.m30() + pivot[0];
                            bone.y = matrix.m31() - pivot[1];
                            bone.z = -matrix.m32() + pivot[2];
                            Vector3f euler = matrix.getEulerAnglesZYX(new Vector3f());
                            bone.rotX = euler.x;
                            bone.rotY = euler.y;
                            bone.rotZ = euler.z;
                            bone.animated = true;
                        }
                    }
                }
            }
        }
    }

    public static final class Bone {
        public float x;
        public float y;
        public float z;
        public float rotX;
        public float rotY;
        public float rotZ;
        public float bend;
        public boolean animated;

        void reset() {
            this.x = this.y = this.z = 0.0F;
            this.rotX = this.rotY = this.rotZ = 0.0F;
            this.bend = 0.0F;
            this.animated = false;
        }
    }
}
