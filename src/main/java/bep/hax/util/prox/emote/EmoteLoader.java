package bep.hax.util.prox.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

public final class EmoteLoader {
    private static final int MAX_VERSION = 3;
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final Map<String, float[]> BONE_DEFAULTS = Map.of(
        "right_arm",
        new float[]{-5.0F, 2.0F, 0.0F},
        "left_arm",
        new float[]{5.0F, 2.0F, 0.0F},
        "left_leg",
        new float[]{1.9F, 12.0F, 0.1F},
        "right_leg",
        new float[]{-1.9F, 12.0F, 0.1F}
    );

    private EmoteLoader() {
    }

    public static EmoteAnimation load(byte[] bytes, String fallbackName) throws Exception {
        JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!root.has("emote")) {
            throw new IllegalArgumentException("not an emote animation");
        }

        int version = root.has("version") ? root.get("version").getAsInt() : 1;
        if (version > 3) {
            throw new IllegalArgumentException("emote is version " + version + ", supported up to 3");
        }

        JsonObject emote = root.getAsJsonObject("emote");
        boolean applyBend = version < 3 || root.has("applyBendToOtherBones") && root.get("applyBendToOtherBones").getAsBoolean();
        boolean easeBefore = emote.has("easeBeforeKeyframe") && emote.get("easeBeforeKeyframe").getAsBoolean();
        float beginTick = emote.has("beginTick") ? emote.get("beginTick").getAsFloat() : 0.0F;
        float endTick = Math.max(emote.has("endTick") ? emote.get("endTick").getAsFloat() : beginTick + 1.0F, beginTick + 1.0F);
        if (endTick <= 0.0F) {
            throw new IllegalArgumentException("endTick must be greater than 0");
        }

        boolean loop = false;
        float returnTick = 0.0F;
        if (emote.has("isLoop") && emote.has("returnTick") && emote.get("isLoop").getAsBoolean()) {
            returnTick = Math.max(emote.get("returnTick").getAsInt() - 1, 0);
            if (returnTick > endTick) {
                throw new IllegalArgumentException("returnTick must be smaller than endTick");
            }

            loop = true;
        }

        float length = endTick;
        if (!loop) {
            float stopTick = emote.has("stopTick") ? emote.get("stopTick").getAsFloat() : 0.0F;
            length = stopTick <= endTick ? endTick + 3.0F : stopTick;
        }

        boolean degrees = !emote.has("degrees") || emote.get("degrees").getAsBoolean();
        Map<String, EmoteBoneTrack> bones = readMoves(emote.getAsJsonArray("moves"), degrees, version, length);
        EmoteBoneTrack body = bones.get("body");
        if (body != null && !body.bend.isEmpty()) {
            EmoteBoneTrack torso = bones.computeIfAbsent("torso", n -> new EmoteBoneTrack());
            torso.bend.addAll(body.bend);
            body.bend.clear();
            if (body.isEmpty()) {
                bones.remove("body");
            }
        }

        if (!easeBefore) {
            for (EmoteBoneTrack track : bones.values()) {
                for (List<EmoteKeyframe> channel : track.channels()) {
                    shiftEasings(channel);
                }
            }
        }

        String name = fallbackName;
        if (root.has("name") && root.get("name").isJsonPrimitive()) {
            name = root.get("name").getAsString();
        }

        UUID uuid = null;
        if (root.has("uuid") && root.get("uuid").isJsonPrimitive()) {
            try {
                uuid = UUID.fromString(root.get("uuid").getAsString());
            } catch (IllegalArgumentException var19) {
            }
        }

        if (uuid == null) {
            uuid = UUID.nameUUIDFromBytes(MessageDigest.getInstance("SHA-256").digest(bytes));
        }

        return new EmoteAnimation(name, uuid, bones, length, beginTick, endTick, loop, returnTick, applyBend);
    }

    private static Map<String, EmoteBoneTrack> readMoves(JsonArray moves, boolean degrees, int version, float length) {
        Map<String, EmoteBoneTrack> bones = new HashMap<>();
        if (moves == null) {
            return bones;
        }

        List<JsonObject> sorted = new ArrayList<>();

        for (JsonElement element : moves) {
            sorted.add(element.getAsJsonObject());
        }

        sorted.sort((a, b) -> Integer.compare(a.get("tick").getAsInt(), b.get("tick").getAsInt()));

        for (JsonObject move : sorted) {
            float tick = move.get("tick").getAsFloat();
            if (!(tick > length)) {
                EmoteEasing easing = move.has("easing") ? EmoteEasing.fromString(move.get("easing").getAsString()) : EmoteEasing.LINEAR;
                int turn = move.has("turn") ? move.get("turn").getAsInt() : 0;

                for (Entry<String, JsonElement> entry : move.entrySet()) {
                    String key = entry.getKey();
                    if (!key.equals("tick") && !key.equals("easing") && !key.equals("turn") && !key.equals("comment") && entry.getValue().isJsonObject()) {
                        String bone = normaliseBone(key);
                        if (version < 3 && bone.equals("torso")) {
                            bone = "body";
                        }

                        EmoteBoneTrack track = bones.computeIfAbsent(bone, n -> new EmoteBoneTrack());
                        readBone(track, bone, entry.getValue().getAsJsonObject(), degrees, tick, easing, turn);
                    }
                }
            }
        }

        return bones;
    }

    private static void readBone(EmoteBoneTrack track, String bone, JsonObject node, boolean degrees, float tick, EmoteEasing easing, int turn) {
        boolean isBody = bone.equals("body");
        boolean isCape = bone.equals("cape");
        float[] def = BONE_DEFAULTS.getOrDefault(bone, new float[]{0.0F, 0.0F, 0.0F});
        addChannel(track.posX, node, "x", def[0], isBody ? EmoteLoader.Kind.POSITION : EmoteLoader.Kind.OFFSET, degrees, tick, easing, turn, isCape || isBody);
        addChannel(track.posY, node, "y", def[1], isBody ? EmoteLoader.Kind.POSITION : EmoteLoader.Kind.OFFSET, degrees, tick, easing, turn, !isBody);
        addChannel(track.posZ, node, "z", def[2], isBody ? EmoteLoader.Kind.POSITION : EmoteLoader.Kind.OFFSET, degrees, tick, easing, turn, isCape);
        addChannel(track.rotX, node, "pitch", 0.0F, EmoteLoader.Kind.ROTATION, degrees, tick, easing, turn, isCape || isBody);
        addChannel(track.rotY, node, "yaw", 0.0F, EmoteLoader.Kind.ROTATION, degrees, tick, easing, turn, isBody);
        addChannel(track.rotZ, node, "roll", 0.0F, EmoteLoader.Kind.ROTATION, degrees, tick, easing, turn, isCape);
        addChannel(track.bend, node, "bend", 0.0F, EmoteLoader.Kind.BEND, degrees, tick, easing, turn, false);
    }

    private static void addChannel(
        List<EmoteKeyframe> channel,
        JsonObject node,
        String key,
        float def,
        EmoteLoader.Kind kind,
        boolean degrees,
        float tick,
        EmoteEasing easing,
        int turn,
        boolean negate
    ) {
        if (node.has(key) && node.get(key).isJsonPrimitive()) {
            float value = node.get(key).getAsFloat();
            if (kind == EmoteLoader.Kind.OFFSET) {
                value -= def;
            }

            if (negate) {
                value = -value;
            }

            if (kind == EmoteLoader.Kind.ROTATION) {
                if (degrees) {
                    value *= (float) (Math.PI / 180.0);
                }

                value += (float) (Math.PI * 2) * turn;
            }

            if (kind == EmoteLoader.Kind.POSITION) {
                value *= 16.0F;
            }

            float previousEnd = channel.isEmpty() ? 0.0F : channel.getLast().end();
            float delta = tick - EmoteKeyframe.totalLength(channel);
            channel.add(new EmoteKeyframe(delta, previousEnd, value, easing));
        }
    }

    private static void shiftEasings(List<EmoteKeyframe> channel) {
        if (!channel.isEmpty()) {
            EmoteEasing carried = EmoteEasing.IN_OUT_SINE;
            EmoteKeyframe last = null;

            for (int i = 0; i < channel.size(); i++) {
                last = channel.get(i);
                channel.set(i, new EmoteKeyframe(last.length(), last.start(), last.end(), carried));
                carried = last.easing();
            }

            channel.add(new EmoteKeyframe(0.001F, last.end(), last.end(), last.easing()));
        }
    }

    private static String normaliseBone(String key) {
        StringBuilder sb = new StringBuilder(key.length() + 2);

        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && sb.charAt(sb.length() - 1) != '_') {
                    sb.append('_');
                }

                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }

        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private enum Kind {
        OFFSET,
        POSITION,
        ROTATION,
        BEND;
    }
}
