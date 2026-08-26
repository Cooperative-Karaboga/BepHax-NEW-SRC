package bep.hax.util;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class MsgUtil {
    public static String getPrefix() {
        return ChatFormatting.DARK_GRAY + "<" + Utils.rCC() + ChatFormatting.ITALIC + "✨" + ChatFormatting.DARK_GRAY + ">";
    }

    public static String getRawPrefix() {
        return "[Bephax]";
    }

    public static String getRawPrefix(String module) {
        return "[" + module + "]";
    }

    public static String getModulePrefix(String module) {
        return ChatFormatting.DARK_GRAY
            + "["
            + Utils.rCC()
            + ChatFormatting.ITALIC
            + meteordevelopment.meteorclient.utils.Utils.nameToTitle(module)
            + ChatFormatting.DARK_GRAY
            + "]";
    }

    public static void sendMsg(String msg) {
        if (MeteorClient.mc.player != null) {
            try {
                StringBuilder sb = new StringBuilder();
                MeteorClient.mc
                    .player
                    .displayClientMessage(Component.literal(sb.append(getPrefix()).append(' ').append(ChatFormatting.GRAY).append(msg).toString()), false);
            } catch (Exception var2) {
            }
        }
    }

    public static void sendMsg(String msg, Style style) {
        if (MeteorClient.mc.player != null) {
            try {
                String message = getPrefix() + " " + ChatFormatting.GRAY + msg;
                MeteorClient.mc.player.displayClientMessage(Component.literal(message).setStyle(style), false);
            } catch (Exception var3) {
            }
        }
    }

    public static void sendModuleMsg(String msg, String module) {
        if (MeteorClient.mc.player != null) {
            try {
                StringBuilder sb = new StringBuilder();
                MeteorClient.mc
                    .player
                    .displayClientMessage(
                        Component.literal(sb.append(getModulePrefix(module)).append(' ').append(ChatFormatting.GRAY).append(msg).toString()), false
                    );
            } catch (Exception var3) {
            }
        }
    }
}
