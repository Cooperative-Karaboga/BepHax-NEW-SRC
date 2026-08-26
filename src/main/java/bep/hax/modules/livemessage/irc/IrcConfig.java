package bep.hax.modules.livemessage.irc;

import bep.hax.modules.livemessage.util.LivemessageUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IrcConfig {
    private static final Logger LOG = LoggerFactory.getLogger("BepHax-IRC");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_SERVER_URL = "wss://bep.dek.to/irc";
    private static final String DEFAULT_CHANNEL = "#bephax";
    private static String serverUrl = "wss://web.libera.chat/webirc/websocket/";
    private static String channel = "#libera";
    private static String nickname = "";
    private static String serverPassword = "";
    private static String channelPassword = "";
    private static boolean useCustomServer = false;
    private static boolean showJoinLeave = false;
    private static boolean soundNotifications = false;
    private static boolean toastNotifications = false;
    private static String cachedOfficialEmail = null;
    private static String cachedOfficialPassword = null;

    public static void load() {
        try {
            File configFile = LivemessageUtil.LIVEMESSAGE_FOLDER.resolve("irc_config.json").toFile();
            if (configFile.exists()) {
                JsonObject json = GSON.fromJson(new FileReader(configFile), JsonObject.class);
                if (json.has("serverUrl")) {
                    serverUrl = json.get("serverUrl").getAsString();
                }

                if (json.has("channel")) {
                    channel = json.get("channel").getAsString();
                }

                if (json.has("nickname")) {
                    nickname = json.get("nickname").getAsString();
                }

                if (json.has("serverPassword")) {
                    serverPassword = json.get("serverPassword").getAsString();
                }

                if (json.has("channelPassword")) {
                    channelPassword = json.get("channelPassword").getAsString();
                }

                if (json.has("useCustomServer")) {
                    useCustomServer = json.get("useCustomServer").getAsBoolean();
                }

                if (json.has("useOfficialServer") && !json.has("useCustomServer")) {
                    useCustomServer = !json.get("useOfficialServer").getAsBoolean();
                }

                if (json.has("showJoinLeave")) {
                    showJoinLeave = json.get("showJoinLeave").getAsBoolean();
                }

                if (json.has("soundNotifications")) {
                    soundNotifications = json.get("soundNotifications").getAsBoolean();
                }

                if (json.has("toastNotifications")) {
                    toastNotifications = json.get("toastNotifications").getAsBoolean();
                }

                LOG.info("Loaded IRC config (custom server: {})", useCustomServer);
            }
        } catch (Exception e) {
            LOG.error("Failed to load IRC config", e);
        }
    }

    public static void save() {
        try {
            LivemessageUtil.initDirs();
            File configFile = LivemessageUtil.LIVEMESSAGE_FOLDER.resolve("irc_config.json").toFile();
            JsonObject json = new JsonObject();
            json.addProperty("serverUrl", serverUrl);
            json.addProperty("channel", channel);
            json.addProperty("nickname", nickname);
            json.addProperty("serverPassword", serverPassword);
            json.addProperty("channelPassword", channelPassword);
            json.addProperty("useCustomServer", useCustomServer);
            json.addProperty("showJoinLeave", showJoinLeave);
            json.addProperty("soundNotifications", soundNotifications);
            json.addProperty("toastNotifications", toastNotifications);

            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(json, writer);
            }

            LOG.info("Saved IRC config");
        } catch (Exception e) {
            LOG.error("Failed to save IRC config", e);
        }
    }

    private static void loadOfficialCredentials() {
        if (cachedOfficialEmail == null || cachedOfficialPassword == null) {
            cachedOfficialEmail = "";
            cachedOfficialPassword = "";
        }
    }

    public static String getServerUrl() {
        return useCustomServer ? serverUrl : "wss://bep.dek.to/irc";
    }

    public static String getChannel() {
        return useCustomServer ? channel : "#bephax";
    }

    public static String getChannelPassword() {
        return useCustomServer ? channelPassword : "";
    }

    public static String getNickname() {
        if (useCustomServer && nickname != null && !nickname.isEmpty()) {
            return nickname;
        }

        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getName().getString() : "BepUser" + (int)(Math.random() * 9999.0);
    }

    public static String getOfficialEmail() {
        loadOfficialCredentials();
        return cachedOfficialEmail != null ? cachedOfficialEmail : "";
    }

    public static String getOfficialPassword() {
        loadOfficialCredentials();
        return cachedOfficialPassword != null ? cachedOfficialPassword : "";
    }

    public static String getServerPassword() {
        return serverPassword;
    }

    public static boolean isOfficialServer() {
        return !useCustomServer;
    }

    public static boolean isCustomServer() {
        return useCustomServer;
    }

    public static boolean showJoinLeaveMessages() {
        return showJoinLeave;
    }

    public static boolean hasSoundNotifications() {
        return soundNotifications;
    }

    public static boolean hasToastNotifications() {
        return toastNotifications;
    }

    public static void setServerUrl(String url) {
        serverUrl = url;
        save();
    }

    public static void setChannel(String ch) {
        channel = ch != null && !ch.isEmpty() && !ch.startsWith("#") ? "#" + ch : ch;
        save();
    }

    public static void setChannelPassword(String p) {
        channelPassword = p;
        save();
    }

    public static void setNickname(String nick) {
        nickname = nick;
        save();
    }

    public static void setServerPassword(String p) {
        serverPassword = p;
        save();
    }

    public static void setUseCustomServer(boolean custom) {
        useCustomServer = custom;
        save();
    }

    public static void setShowJoinLeave(boolean show) {
        showJoinLeave = show;
        save();
    }

    public static void setSoundNotifications(boolean sound) {
        soundNotifications = sound;
        save();
    }

    public static void setToastNotifications(boolean toast) {
        toastNotifications = toast;
        save();
    }

    public static void configureCustom(String serverUrl, String channel, String nickname, String serverPassword, String channelPassword) {
        IrcConfig.serverUrl = serverUrl;
        IrcConfig.channel = channel != null && !channel.isEmpty() && !channel.startsWith("#") ? "#" + channel : channel;
        IrcConfig.nickname = nickname;
        IrcConfig.serverPassword = serverPassword;
        IrcConfig.channelPassword = channelPassword;
        useCustomServer = true;
        save();
    }

    public static void resetToOfficial() {
        useCustomServer = false;
        save();
    }

    static {
        load();
    }
}
