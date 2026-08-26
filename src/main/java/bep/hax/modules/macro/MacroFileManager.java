package bep.hax.modules.macro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import meteordevelopment.meteorclient.MeteorClient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MacroFileManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MacroFileManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static File getMacrosFolder() {
        File folder = new File(MeteorClient.FOLDER, "macros");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        return folder;
    }

    public static void save(MacroFile macroFile) {
        File folder = getMacrosFolder();
        File target = new File(folder, macroFile.name + ".json");
        File temp = new File(folder, macroFile.name + ".json.tmp");

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            GSON.toJson(macroFile, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write macro file: {}", macroFile.name, e);
            return;
        }

        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to rename macro temp file: {}", macroFile.name, e);
        }
    }

    @Nullable
    public static MacroFile load(String name) {
        File file = new File(getMacrosFolder(), name + ".json");
        if (!file.exists()) {
            return null;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, MacroFile.class);
        } catch (Exception e) {
            LOGGER.error("Failed to load macro file: {}", name, e);
            return null;
        }
    }
}
