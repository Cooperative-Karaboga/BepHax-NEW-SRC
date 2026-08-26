package bep.hax.util.prox.emote;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum EmoteEasing {
    LINEAR("linear", t -> t),
    CONSTANT("constant", t -> 0.0F),
    IN_SINE("easeinsine", in(EmoteEasing::sine)),
    OUT_SINE("easeoutsine", out(EmoteEasing::sine)),
    IN_OUT_SINE("easeinoutsine", inOut(EmoteEasing::sine)),
    IN_QUAD("easeinquad", in(EmoteEasing::quadratic)),
    OUT_QUAD("easeoutquad", out(EmoteEasing::quadratic)),
    IN_OUT_QUAD("easeinoutquad", inOut(EmoteEasing::quadratic)),
    IN_CUBIC("easeincubic", in(EmoteEasing::cubic)),
    OUT_CUBIC("easeoutcubic", out(EmoteEasing::cubic)),
    IN_OUT_CUBIC("easeinoutcubic", inOut(EmoteEasing::cubic)),
    IN_QUART("easeinquart", in(pow(4.0F))),
    OUT_QUART("easeoutquart", out(pow(4.0F))),
    IN_OUT_QUART("easeinoutquart", inOut(pow(4.0F))),
    IN_QUINT("easeinquint", in(pow(5.0F))),
    OUT_QUINT("easeoutquint", out(pow(5.0F))),
    IN_OUT_QUINT("easeinoutquint", inOut(pow(5.0F))),
    IN_EXPO("easeinexpo", in(EmoteEasing::exp)),
    OUT_EXPO("easeoutexpo", out(EmoteEasing::exp)),
    IN_OUT_EXPO("easeinoutexpo", inOut(EmoteEasing::exp)),
    IN_CIRC("easeincirc", in(EmoteEasing::circle)),
    OUT_CIRC("easeoutcirc", out(EmoteEasing::circle)),
    IN_OUT_CIRC("easeinoutcirc", inOut(EmoteEasing::circle)),
    IN_BACK("easeinback", in(back())),
    OUT_BACK("easeoutback", out(back())),
    IN_OUT_BACK("easeinoutback", inOut(back())),
    IN_ELASTIC("easeinelastic", in(elastic())),
    OUT_ELASTIC("easeoutelastic", out(elastic())),
    IN_OUT_ELASTIC("easeinoutelastic", inOut(elastic())),
    IN_BOUNCE("easeinbounce", in(bounce())),
    OUT_BOUNCE("easeoutbounce", out(bounce())),
    IN_OUT_BOUNCE("easeinoutbounce", inOut(bounce()));

    private static final Map<String, EmoteEasing> BY_NAME = new HashMap<>();
    private final String id;
    private final EmoteEasing.Curve curve;

    EmoteEasing(String id, EmoteEasing.Curve curve) {
        this.id = id;
        this.curve = curve;
    }

    public float apply(float t) {
        return this.curve.apply(t);
    }

    public static EmoteEasing fromString(String name) {
        if (name == null) {
            return LINEAR;
        }

        String key = name.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        EmoteEasing easing = BY_NAME.get(key);
        return easing != null ? easing : BY_NAME.getOrDefault("ease" + key, LINEAR);
    }

    private static float sine(float n) {
        return 1.0F - (float)Math.cos(n * Math.PI / 2.0);
    }

    private static float quadratic(float n) {
        return n * n;
    }

    private static float cubic(float n) {
        return n * n * n;
    }

    private static float circle(float n) {
        return 1.0F - (float)Math.sqrt(1.0F - n * n);
    }

    private static float exp(float n) {
        return (float)Math.pow(2.0, 10.0F * (n - 1.0F));
    }

    private static EmoteEasing.Curve pow(float n) {
        return t -> (float)Math.pow(t, n);
    }

    private static EmoteEasing.Curve back() {
        float overshoot = 1.70158F;
        return t -> t * t * (2.70158F * t - 1.70158F);
    }

    private static EmoteEasing.Curve elastic() {
        return t -> 1.0F - (float)Math.pow(Math.cos(t * Math.PI / 2.0), 3.0) * (float)Math.cos(t * Math.PI);
    }

    private static EmoteEasing.Curve bounce() {
        float n = 0.5F;
        EmoteEasing.Curve one = x -> 7.5625F * x * x;
        EmoteEasing.Curve two = x -> 15.125F * (float)Math.pow(x - 0.54545456F, 2.0) + 1.0F - 0.5F;
        EmoteEasing.Curve three = x -> 30.25F * (float)Math.pow(x - 0.8181818F, 2.0) + 1.0F - 0.25F;
        EmoteEasing.Curve four = x -> 60.5F * (float)Math.pow(x - 0.95454544F, 2.0) + 1.0F - 0.125F;
        return t -> Math.min(Math.min(one.apply(t), two.apply(t)), Math.min(three.apply(t), four.apply(t)));
    }

    private static EmoteEasing.Curve in(EmoteEasing.Curve curve) {
        return curve;
    }

    private static EmoteEasing.Curve out(EmoteEasing.Curve curve) {
        return t -> 1.0F - curve.apply(1.0F - t);
    }

    private static EmoteEasing.Curve inOut(EmoteEasing.Curve curve) {
        return t -> t < 0.5F ? curve.apply(t * 2.0F) / 2.0F : 1.0F - curve.apply((1.0F - t) * 2.0F) / 2.0F;
    }

    static {
        for (EmoteEasing easing : values()) {
            BY_NAME.put(easing.id, easing);
        }
    }

    public interface Curve {
        float apply(float var1);
    }
}
