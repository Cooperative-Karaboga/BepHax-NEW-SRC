package bep.hax.util;

import java.util.UUID;

public final class DektoOwner {
    public static final UUID ID = UUID.fromString("2ffa806c-cbad-4274-8175-eb2494b7fd66");
    public static final String SUFFIX = " \ud83d\udc41";

    private DektoOwner() {
    }

    public static boolean is(UUID uuid) {
        return ID.equals(uuid);
    }
}
