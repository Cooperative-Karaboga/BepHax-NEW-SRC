package bep.hax.modules.livemessage.util;

import bep.hax.modules.livemessage.LiveMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public class LivemessageUtil {
    public static final Path LIVEMESSAGE_FOLDER = MeteorClient.FOLDER.toPath().resolve("livemessage");
    public static final Path MESSAGES_FOLDER = LIVEMESSAGE_FOLDER.resolve("messages");
    public static final Path SETTINGS_FOLDER = LIVEMESSAGE_FOLDER.resolve("settings");
    public static final Path PATTERNS_FOLDER = LIVEMESSAGE_FOLDER.resolve("patterns");
    public static final List<Pattern> FROM_PATTERNS = new ArrayList<>(
        Arrays.asList(
            Pattern.compile("^From (\\w{3,16}): (.*)"),
            Pattern.compile("^from (\\w{3,16}): (.*)"),
            Pattern.compile("^(\\w{3,16}) whispers: (.*)"),
            Pattern.compile("^\\[(\\w{3,16}) -> me\\] (.*)"),
            Pattern.compile("^(\\w{3,16}) whispers to you: (.*)")
        )
    );
    public static final List<Pattern> TO_PATTERNS = new ArrayList<>(
        Arrays.asList(
            Pattern.compile("^To (\\w{3,16}): (.*)"),
            Pattern.compile("^to (\\w{3,16}): (.*)"),
            Pattern.compile("^\\[me -> (\\w{3,16})\\] (.*)"),
            Pattern.compile("^You whisper to (\\w{3,16}): (.*)")
        )
    );

    public static void initDirs() {
        try {
            Files.createDirectories(LIVEMESSAGE_FOLDER);
            Files.createDirectories(MESSAGES_FOLDER);
            Files.createDirectories(SETTINGS_FOLDER);
            Files.createDirectories(PATTERNS_FOLDER);
            File toPatterns = PATTERNS_FOLDER.resolve("toPatterns.txt").toFile();
            if (!toPatterns.exists()) {
                toPatterns.createNewFile();
            }

            File fromPatterns = PATTERNS_FOLDER.resolve("fromPatterns.txt").toFile();
            if (!fromPatterns.exists()) {
                fromPatterns.createNewFile();
            }
        } catch (IOException e) {
            LiveMessage.LOG.error("Failed to initialize Livemessage directories", e);
        }
    }

    public static void initFolders() {
        initDirs();
        loadPatterns();
    }

    public static void loadPatterns() {
        int toPatternCount = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(PATTERNS_FOLDER.resolve("toPatterns.txt").toFile()))) {
            int lineNum = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (!line.trim().isEmpty()) {
                    try {
                        TO_PATTERNS.add(Pattern.compile(line));
                        toPatternCount++;
                    } catch (Exception e) {
                        LiveMessage.LOG.error("Invalid TO pattern on line {}: '{}' - {}", lineNum, line, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LiveMessage.LOG.error("Failed to load TO patterns file", e);
        }

        int fromPatternCount = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(PATTERNS_FOLDER.resolve("fromPatterns.txt").toFile()))) {
            int lineNum = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (!line.trim().isEmpty()) {
                    try {
                        FROM_PATTERNS.add(Pattern.compile(line));
                        fromPatternCount++;
                    } catch (Exception e) {
                        LiveMessage.LOG.error("Invalid FROM pattern on line {}: '{}' - {}", lineNum, line, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LiveMessage.LOG.error("Failed to load FROM patterns file", e);
        }

        if (toPatternCount > 0 || fromPatternCount > 0) {
            LiveMessage.LOG.info("Loaded {} custom TO patterns and {} custom FROM patterns", toPatternCount, fromPatternCount);
        }
    }

    public static LivemessageUtil.ChatSettings getChatSettings(UUID uuid) {
        try {
            Gson gson = new Gson();
            JsonReader reader = new JsonReader(new FileReader(SETTINGS_FOLDER.resolve(uuid.toString() + ".json").toFile()));
            return gson.fromJson(reader, LivemessageUtil.ChatSettings.class);
        } catch (Exception var3) {
            return new LivemessageUtil.ChatSettings();
        }
    }

    public static void saveChatSettings(UUID uuid, LivemessageUtil.ChatSettings chatSettings) {
        try (Writer writer = new FileWriter(SETTINGS_FOLDER.resolve(uuid.toString() + ".json").toFile())) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(chatSettings, writer);
        } catch (IOException e) {
            LiveMessage.LOG.error("Failed to save chat settings for UUID: {}", uuid, e);
        }
    }

    public static boolean checkOnlineStatus(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return false;
        }

        PlayerInfo entry = mc.getConnection().getPlayerInfo(uuid);
        return entry != null;
    }

    public static class ChatSettings {
        public int customColor = 0;
        public String lastName;
    }
}
