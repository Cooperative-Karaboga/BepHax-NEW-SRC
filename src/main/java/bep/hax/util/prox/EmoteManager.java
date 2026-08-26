package bep.hax.util.prox;

import bep.hax.util.LogUtil;
import bep.hax.util.prox.emote.EmoteAnimation;
import bep.hax.util.prox.emote.EmoteEngine;
import bep.hax.util.prox.emote.EmoteLoader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

public final class EmoteManager {
    private static final String[] BUNDLED = new String[]{
        "wave",
        "clap",
        "point",
        "no",
        "bow",
        "dab",
        "salute",
        "shrug",
        "facepalm",
        "cheer",
        "tpose",
        "think",
        "dance",
        "floss",
        "default-dance",
        "take-the-l",
        "griddy",
        "robot",
        "zombie",
        "twerk",
        "bapo",
        "headbang",
        "laugh"
    };
    private static final String USER_FOLDER = "bephax-emotes";
    private static final long MAX_FILE_SIZE = 524288L;
    private static final int MAX_EMOTES = 128;
    private static final int MAX_NAME_LENGTH = 32;
    private static EmoteManager instance;
    private final Map<String, EmoteManager.Emote> byName = new LinkedHashMap<>();
    private final Map<UUID, EmoteManager.Emote> byUuid = new HashMap<>();
    private final Map<UUID, Long> lastRefresh = new HashMap<>();
    private boolean loaded;
    private boolean ticking;

    private EmoteManager() {
    }

    public static EmoteManager getInstance() {
        if (instance == null) {
            instance = new EmoteManager();
        }

        return instance;
    }

    public static void init() {
        EmoteManager m = getInstance();
        if (!m.ticking) {
            MeteorClient.EVENT_BUS.subscribe(m);
            m.ticking = true;
        }
    }

    @EventHandler
    private void onTick(Post event) {
        EmoteEngine.get().tick();
    }

    public void reload() {
        this.byName.clear();
        this.byUuid.clear();
        this.loaded = true;

        for (String name : BUNDLED) {
            try (InputStream in = EmoteManager.class.getResourceAsStream("/assets/bephax/emotes/" + name + ".json")) {
                if (in != null) {
                    this.loadEmote(in.readAllBytes(), name);
                } else {
                    LogUtil.warn("Missing bundled emote: " + name, "ProxChat");
                }
            } catch (Throwable t) {
                LogUtil.warn("Skipping bundled emote " + name + ": " + t, "ProxChat");
            }
        }

        File dir = new File(MeteorClient.FOLDER, "bephax-emotes");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));

            for (File file : files) {
                if (this.byUuid.size() >= 128) {
                    LogUtil.warn("Emote cap (128) reached, ignoring remaining files.", "ProxChat");
                    break;
                }

                if (file.length() > 524288L) {
                    LogUtil.warn("Skipping oversized emote file " + file.getName(), "ProxChat");
                } else {
                    String stem = file.getName().substring(0, file.getName().length() - 5);

                    try {
                        this.loadEmote(Files.readAllBytes(file.toPath()), sanitizeName(stem, "emote"));
                    } catch (Throwable t) {
                        LogUtil.warn("Skipping emote file " + file.getName() + ": " + t, "ProxChat");
                    }
                }
            }
        }

        LogUtil.info("Emote library loaded: " + this.byUuid.size() + " emotes.", "ProxChat");
    }

    private void ensureLoaded() {
        if (!this.loaded) {
            this.reload();
        }
    }

    private void loadEmote(byte[] bytes, String fallbackName) throws Exception {
        EmoteAnimation animation = EmoteLoader.load(bytes, fallbackName);
        if (animation.bones().isEmpty()) {
            throw new IOException("file contains no animation");
        }

        String name = sanitizeName(animation.name(), fallbackName);
        EmoteManager.Emote emote = new EmoteManager.Emote(name, animation.uuid(), animation);
        this.byName.put(name.toLowerCase(Locale.ROOT), emote);
        this.byUuid.put(animation.uuid(), emote);
    }

    private static String sanitizeName(String name, String fallback) {
        StringBuilder sb = new StringBuilder(Math.min(name.length(), 32));

        for (int i = 0; i < name.length() && sb.length() < 32; i++) {
            char c = name.charAt(i);
            if (c >= ' ' && c != 127 && c != 167) {
                sb.append(c);
            }
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? fallback : result;
    }

    public boolean play(Avatar player, UUID emoteUuid) {
        return this.play(player, emoteUuid, 0.0F);
    }

    public boolean play(Avatar player, UUID emoteUuid, float startTick) {
        if (player != null && emoteUuid != null) {
            this.ensureLoaded();
            EmoteManager.Emote emote = this.byUuid.get(emoteUuid);
            if (emote == null) {
                return false;
            }

            EmoteEngine.get().play(player.getUUID(), emote.animation(), startTick);
            this.lastRefresh.put(player.getUUID(), System.currentTimeMillis());
            return true;
        } else {
            return false;
        }
    }

    public boolean isPlaying(Avatar player, UUID emoteUuid) {
        if (player != null && emoteUuid != null) {
            EmoteAnimation playing = EmoteEngine.get().playing(player.getUUID());
            return playing != null && emoteUuid.equals(playing.uuid());
        } else {
            return false;
        }
    }

    public boolean loops(UUID emoteUuid) {
        this.ensureLoaded();
        EmoteManager.Emote emote = this.byUuid.get(emoteUuid);
        return emote != null && emote.animation().loops();
    }

    public void touch(UUID playerUuid) {
        if (playerUuid != null) {
            this.lastRefresh.put(playerUuid, System.currentTimeMillis());
        }
    }

    public void stopStale(long timeoutMs) {
        if (MeteorClient.mc.level != null && MeteorClient.mc.player != null) {
            long now = System.currentTimeMillis();

            for (UUID playerUuid : EmoteEngine.get().players()) {
                if (!playerUuid.equals(MeteorClient.mc.player.getUUID())) {
                    EmoteAnimation animation = EmoteEngine.get().playing(playerUuid);
                    if (animation != null && animation.loops()) {
                        Long last = this.lastRefresh.get(playerUuid);
                        if (last == null || now - last >= timeoutMs) {
                            Player player = MeteorClient.mc.level.getPlayerByUUID(playerUuid);
                            if (player != null) {
                                this.stop(player, null);
                            } else {
                                EmoteEngine.get().remove(playerUuid);
                                this.lastRefresh.remove(playerUuid);
                            }
                        }
                    }
                }
            }
        }
    }

    public void stop(Avatar player, UUID emoteUuid) {
        if (player != null) {
            EmoteAnimation current = EmoteEngine.get().playing(player.getUUID());
            if (emoteUuid == null || current == null || emoteUuid.equals(current.uuid())) {
                this.lastRefresh.remove(player.getUUID());
                EmoteEngine.get().stop(player.getUUID());
            }
        }
    }

    public UUID playLocal(String name) {
        this.ensureLoaded();
        if (MeteorClient.mc.player != null && name != null) {
            EmoteManager.Emote emote = this.byName.get(name.trim().toLowerCase(Locale.ROOT));
            if (emote == null) {
                return null;
            } else {
                return this.play(MeteorClient.mc.player, emote.uuid()) ? emote.uuid() : null;
            }
        } else {
            return null;
        }
    }

    public UUID stopLocal() {
        if (MeteorClient.mc.player == null) {
            return null;
        }

        EmoteAnimation current = EmoteEngine.get().playing(MeteorClient.mc.player.getUUID());
        this.stop(MeteorClient.mc.player, null);
        return current == null ? null : current.uuid();
    }

    public boolean hasEmote(String name) {
        this.ensureLoaded();
        return name != null && this.byName.containsKey(name.trim().toLowerCase(Locale.ROOT));
    }

    public List<String> emoteNames() {
        this.ensureLoaded();
        return this.byName.values().stream().map(EmoteManager.Emote::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public List<EmoteManager.Emote> emotes() {
        this.ensureLoaded();
        return this.byName.values().stream().sorted(Comparator.comparing(EmoteManager.Emote::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public UUID localPlaying() {
        if (MeteorClient.mc.player == null) {
            return null;
        }

        EmoteAnimation animation = EmoteEngine.get().playing(MeteorClient.mc.player.getUUID());
        return animation == null ? null : animation.uuid();
    }

    public float localTicks() {
        return MeteorClient.mc.player == null ? 0.0F : Math.max(EmoteEngine.get().ticks(MeteorClient.mc.player.getUUID()), 0.0F);
    }

    public int count() {
        this.ensureLoaded();
        return this.byUuid.size();
    }

    public void clearSession() {
        EmoteEngine.get().clear();
        this.lastRefresh.clear();
    }

    public record Emote(String name, UUID uuid, EmoteAnimation animation) {
    }
}
