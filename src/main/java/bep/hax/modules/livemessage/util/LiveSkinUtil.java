package bep.hax.modules.livemessage.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LiveSkinUtil {
    private static final Map<UUID, LiveSkinUtil> SKIN_CACHE = new ConcurrentHashMap<>();

    private LiveSkinUtil(UUID uuid) {
    }

    public static LiveSkinUtil get(UUID uuid) {
        return SKIN_CACHE.computeIfAbsent(uuid, LiveSkinUtil::new);
    }

    public static void clearCache() {
        SKIN_CACHE.clear();
    }
}
