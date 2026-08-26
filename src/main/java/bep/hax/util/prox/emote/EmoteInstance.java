package bep.hax.util.prox.emote;

public final class EmoteInstance {
    private final EmoteAnimation animation;
    private final EmotePose pose = new EmotePose();
    private final int fadeTicks;
    private float tick;
    private boolean loopStarted;
    private boolean finished;
    private float fadeAge;
    private boolean fadingOut;
    private float fadeOutFrom = 1.0F;

    public EmoteInstance(EmoteAnimation animation, float startTick, int fadeTicks) {
        this.animation = animation;
        this.fadeTicks = Math.max(fadeTicks, 1);
        this.tick = Math.clamp(startTick, 0.0F, Math.max(animation.length() - 1.0F, 0.0F));
    }

    public EmoteAnimation animation() {
        return this.animation;
    }

    public boolean finished() {
        return this.finished;
    }

    public boolean fadingOut() {
        return this.fadingOut;
    }

    public float ticks() {
        return this.tick;
    }

    public boolean tick() {
        if (this.fadingOut) {
            this.fadeAge++;
            return !(this.fadeAge >= this.fadeTicks);
        }

        if (this.fadeAge < this.fadeTicks) {
            this.fadeAge++;
        }

        this.tick++;
        if (this.tick >= this.animation.length()) {
            if (!this.animation.loops()) {
                this.finished = true;
                return false;
            }

            float overshoot = this.tick - this.animation.length();
            this.tick = this.animation.returnTick() + overshoot;
            this.loopStarted = true;
        }

        return true;
    }

    public void fadeOut() {
        if (!this.fadingOut) {
            this.fadeOutFrom = this.weight(0.0F);
            this.fadingOut = true;
            this.fadeAge = 0.0F;
        }
    }

    private float weight(float partialTick) {
        float age = Math.min(this.fadeAge + partialTick, this.fadeTicks);
        float linear = age / this.fadeTicks;
        return this.fadingOut ? this.fadeOutFrom * (1.0F - EmoteEasing.IN_OUT_SINE.apply(linear)) : EmoteEasing.IN_OUT_SINE.apply(linear);
    }

    public EmotePose sample(float partialTick) {
        float at = this.tick + partialTick;
        if (!this.animation.loops() && at > this.animation.length()) {
            at = this.animation.length();
        }

        this.pose.sample(this.animation, at, this.loopStarted);
        this.pose.setWeight(this.weight(partialTick));
        return this.pose;
    }
}
