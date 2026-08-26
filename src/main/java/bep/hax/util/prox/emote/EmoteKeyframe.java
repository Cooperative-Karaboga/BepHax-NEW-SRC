package bep.hax.util.prox.emote;

import java.util.List;

public record EmoteKeyframe(float length, float start, float end, EmoteEasing easing) {
    public static float totalLength(List<EmoteKeyframe> frames) {
        float total = 0.0F;

        for (EmoteKeyframe frame : frames) {
            total += frame.length();
        }

        return total;
    }

    public static EmoteKeyframe at(List<EmoteKeyframe> frames, float tick) {
        float total = 0.0F;

        for (EmoteKeyframe frame : frames) {
            total += frame.length();
            if (total >= tick) {
                return frame;
            }
        }

        return frames.getLast();
    }
}
