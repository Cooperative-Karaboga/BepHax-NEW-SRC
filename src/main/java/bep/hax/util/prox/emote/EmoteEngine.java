package bep.hax.util.prox.emote;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

public final class EmoteEngine {
    private static final int FADE_TICKS = 6;
    private static final EmoteEngine INSTANCE = new EmoteEngine();
    private final Map<UUID, EmoteInstance> active = new HashMap<>();

    private EmoteEngine() {
    }

    public static EmoteEngine get() {
        return INSTANCE;
    }

    public void play(UUID player, EmoteAnimation animation, float startTick) {
        if (player != null && animation != null) {
            this.active.put(player, new EmoteInstance(animation, startTick, 6));
        }
    }

    public void stop(UUID player) {
        EmoteInstance instance = this.active.get(player);
        if (instance != null) {
            instance.fadeOut();
        }
    }

    public void remove(UUID player) {
        this.active.remove(player);
    }

    public void clear() {
        this.active.clear();
    }

    public boolean isPlaying(UUID player) {
        EmoteInstance instance = this.active.get(player);
        return instance != null && !instance.finished() && !instance.fadingOut();
    }

    public EmoteAnimation playing(UUID player) {
        EmoteInstance instance = this.active.get(player);
        return instance != null && !instance.fadingOut() ? instance.animation() : null;
    }

    public float ticks(UUID player) {
        EmoteInstance instance = this.active.get(player);
        return instance == null ? 0.0F : instance.ticks();
    }

    public Set<UUID> players() {
        return Set.copyOf(this.active.keySet());
    }

    public void tick() {
        Iterator<Entry<UUID, EmoteInstance>> it = this.active.entrySet().iterator();

        while (it.hasNext()) {
            if (!it.next().getValue().tick()) {
                it.remove();
            }
        }
    }

    public EmotePose pose(UUID player, float partialTick) {
        EmoteInstance instance = this.active.get(player);
        if (instance == null) {
            return null;
        }

        EmotePose pose = instance.sample(partialTick);
        return pose.weight() <= 0.0F ? null : pose;
    }
}
