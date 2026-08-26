package bep.hax.util;

import bep.hax.capes.CapeManager;
import bep.hax.emoji.EmojiData;
import java.util.UUID;

public final class NametagEmoji {
    private static final String DEKTO = " \ud83d\udc41";

    private NametagEmoji() {
    }

    public static String suffixFor(UUID uuid) {
        if (uuid == null) {
            return "";
        }

        StringBuilder suffix = new StringBuilder();
        if (DektoOwner.is(uuid)) {
            suffix.append(" \ud83d\udc41");
        }

        for (String badge : CapeManager.getInstance().getBadges(uuid)) {
            String clean = EmojiData.stripUnrenderable(badge);
            if (!clean.isBlank()) {
                suffix.append(' ').append(clean);
            }
        }

        return suffix.toString();
    }
}
