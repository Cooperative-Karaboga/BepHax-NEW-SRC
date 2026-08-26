package bep.hax.util.prox.emote;

import java.util.ArrayList;
import java.util.List;

public final class EmoteBoneTrack {
    public final List<EmoteKeyframe> posX = new ArrayList<>();
    public final List<EmoteKeyframe> posY = new ArrayList<>();
    public final List<EmoteKeyframe> posZ = new ArrayList<>();
    public final List<EmoteKeyframe> rotX = new ArrayList<>();
    public final List<EmoteKeyframe> rotY = new ArrayList<>();
    public final List<EmoteKeyframe> rotZ = new ArrayList<>();
    public final List<EmoteKeyframe> bend = new ArrayList<>();

    public boolean isEmpty() {
        return this.posX.isEmpty()
            && this.posY.isEmpty()
            && this.posZ.isEmpty()
            && this.rotX.isEmpty()
            && this.rotY.isEmpty()
            && this.rotZ.isEmpty()
            && this.bend.isEmpty();
    }

    public List<List<EmoteKeyframe>> channels() {
        return List.of(this.posX, this.posY, this.posZ, this.rotX, this.rotY, this.rotZ, this.bend);
    }
}
