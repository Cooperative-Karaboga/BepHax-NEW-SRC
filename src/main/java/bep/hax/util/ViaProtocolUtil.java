package bep.hax.util;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

public final class ViaProtocolUtil {
    public static final int PROTOCOL_1_9 = 107;
    public static final int PROTOCOL_1_18_2 = 758;
    public static final int PROTOCOL_1_21_2 = 768;
    public static final int UNKNOWN = -1;
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("viafabricplus");
    private static Method getTargetVersion;
    private static Method getVersion;
    private static boolean resolved;

    private ViaProtocolUtil() {
    }

    public static boolean isPresent() {
        return LOADED;
    }

    public static int targetProtocol() {
        Object version = targetVersion();
        if (version == null) {
            return -1;
        }

        try {
            if (getVersion == null) {
                getVersion = version.getClass().getMethod("getVersion");
            }

            return getVersion.invoke(version) instanceof Integer number && number >= 0 ? number : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static String targetName() {
        Object version = targetVersion();
        return version == null ? "" : String.valueOf(version);
    }

    public static boolean isLegacyBand(int protocol) {
        return protocol >= 107 && protocol < 758;
    }

    private static Object targetVersion() {
        if (!LOADED) {
            return null;
        }

        try {
            if (!resolved) {
                resolved = true;
                getTargetVersion = Class.forName("com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator").getMethod("getTargetVersion");
            }

            return getTargetVersion == null ? null : getTargetVersion.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
