package bep.hax.util.prox.emote;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EmoteAnimation {
    public static final String HEAD = "head";
    public static final String TORSO = "torso";
    public static final String BODY = "body";
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_ARM = "left_arm";
    public static final String RIGHT_LEG = "right_leg";
    public static final String LEFT_LEG = "left_leg";
    public static final String CAPE = "cape";
    public static final String BUTT = "butt";
    public static final List<String> BEND_CARRIED = List.of("head", "right_arm", "left_arm", "cape");
    private final String name;
    private final UUID uuid;
    private final Map<String, EmoteBoneTrack> bones;
    private final float length;
    private final float beginTick;
    private final float endTick;
    private final boolean loop;
    private final float returnTick;
    private final boolean applyBendToOtherBones;

    public EmoteAnimation(
        String name,
        UUID uuid,
        Map<String, EmoteBoneTrack> bones,
        float length,
        float beginTick,
        float endTick,
        boolean loop,
        float returnTick,
        boolean applyBendToOtherBones
    ) {
        this.name = name;
        this.uuid = uuid;
        this.bones = bones;
        this.length = length;
        this.beginTick = beginTick;
        this.endTick = endTick;
        this.loop = loop;
        this.returnTick = returnTick;
        this.applyBendToOtherBones = applyBendToOtherBones;
    }

    public String name() {
        return this.name;
    }

    public UUID uuid() {
        return this.uuid;
    }

    public float length() {
        return this.length;
    }

    public float beginTick() {
        return this.beginTick;
    }

    public float endTick() {
        return this.endTick;
    }

    public boolean loops() {
        return this.loop;
    }

    public float returnTick() {
        return this.returnTick;
    }

    public boolean applyBendToOtherBones() {
        return this.applyBendToOtherBones;
    }

    public Map<String, EmoteBoneTrack> bones() {
        return this.bones;
    }

    public EmoteBoneTrack bone(String name) {
        return this.bones.get(name);
    }

    public float sample(List<EmoteKeyframe> frames, float tick, boolean loopStarted) {
        if (frames.isEmpty()) {
            return 0.0F;
        }

        EmoteKeyframe first = this.returnTick == 0.0F ? frames.getFirst() : EmoteKeyframe.at(frames, this.returnTick);
        float total = 0.0F;
        boolean found = false;
        EmoteKeyframe frame = frames.getLast();
        float startTick = tick;

        for (EmoteKeyframe candidate : frames) {
            total += candidate.length();
            if (total > tick) {
                if (this.loop && loopStarted && candidate == first) {
                    float tail = this.length - EmoteKeyframe.totalLength(frames);
                    frame = new EmoteKeyframe(candidate.length() + tail, frames.getLast().end(), candidate.end(), candidate.easing());
                    startTick = tick + tail;
                } else {
                    frame = candidate;
                    startTick = tick - (total - candidate.length());
                }

                found = true;
                break;
            }
        }

        if (!found && this.loop) {
            frame = new EmoteKeyframe(first.length() + this.length - total, frames.getLast().end(), first.end(), first.easing());
            startTick = tick - total;
        }

        float progress = frame.length() > 0.0F ? startTick / frame.length() : 0.0F;
        return frame.start() + (frame.end() - frame.start()) * frame.easing().apply(progress);
    }
}
