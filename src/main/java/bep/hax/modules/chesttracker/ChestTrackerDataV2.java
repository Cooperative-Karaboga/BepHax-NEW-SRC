package bep.hax.modules.chesttracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestTrackerDataV2 {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestTracker");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 2;
    private final Map<String, Map<BlockPos, TrackedContainer>> containers;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Minecraft mc;
    private File dataFile;
    private File backupFile;
    private File tempFile;
    private int saveFailures = 0;

    public ChestTrackerDataV2() {
        this.containers = new ConcurrentHashMap<>();
        this.mc = Minecraft.getInstance();
        this.initializeFiles();
    }

    private void initializeFiles() {
        try {
            String serverIdentifier = this.getServerIdentifier();
            File baseFolder = new File(MeteorClient.FOLDER, "ChestTracker");
            if (!baseFolder.exists() && !baseFolder.mkdirs()) {
                LOGGER.error("Failed to create ChestTracker folder");
            }

            File folder = new File(baseFolder, serverIdentifier);
            if (!folder.exists() && !folder.mkdirs()) {
                LOGGER.error("Failed to create server-specific ChestTracker folder: {}", serverIdentifier);
            }

            this.dataFile = new File(folder, "tracked_containers.json");
            this.backupFile = new File(folder, "tracked_containers.backup.json");
            this.tempFile = new File(folder, "tracked_containers.tmp");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize files", e);
        }
    }

    private String getServerIdentifier() {
        if (this.mc == null) {
            return "unknown";
        } else if (this.mc.getCurrentServer() != null) {
            String address = this.mc.getCurrentServer().ip;
            return this.sanitizeFileName(address);
        } else if (this.mc.isLocalServer() && this.mc.getSingleplayerServer() != null) {
            String worldName = this.mc.getSingleplayerServer().getWorldData().getLevelName();
            return "singleplayer_" + this.sanitizeFileName(worldName);
        } else {
            return "unknown";
        }
    }

    private String sanitizeFileName(String name) {
        return name != null && !name.isEmpty() ? name.replaceAll("[<>:\"/\\\\|?*]", "_").replaceAll("\\s+", "_").toLowerCase() : "unknown";
    }

    public void reinitializeForNewServer() {
        this.initializeFiles();
    }

    public void trackContainer(BlockPos pos, String dimension, String containerType, List<ItemStack> contents) {
        this.lock.writeLock().lock();

        try {
            Map<BlockPos, TrackedContainer> dimContainers = this.containers.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
            TrackedContainer container = dimContainers.get(pos);
            if (container == null) {
                container = new TrackedContainer(pos, dimension, containerType);
                dimContainers.put(pos, container);
            }

            container.updateContents(contents);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public TrackedContainer getContainer(BlockPos pos, String dimension) {
        this.lock.readLock().lock();

        try {
            Map<BlockPos, TrackedContainer> dimContainers = this.containers.get(dimension);
            return dimContainers != null ? dimContainers.get(pos) : null;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public List<TrackedContainer> searchItem(Item item) {
        String currentDim = this.getCurrentDimension();
        return this.searchItem(item, currentDim);
    }

    public List<TrackedContainer> searchItem(Item item, String dimension) {
        this.lock.readLock().lock();

        try {
            Map<BlockPos, TrackedContainer> dimContainers = this.containers.get(dimension);
            return dimContainers == null ? new ArrayList<>() : dimContainers.values().stream().filter(c -> c.containsItem(item)).sorted((a, b) -> {
                String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
                return Integer.compare(b.getItemCount(itemId), a.getItemCount(itemId));
            }).collect(Collectors.toList());
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public List<TrackedContainer> searchSignature(String sig) {
        return this.searchSignature(sig, this.getCurrentDimension());
    }

    public List<TrackedContainer> searchSignature(String sig, String dimension) {
        this.lock.readLock().lock();

        try {
            Map<BlockPos, TrackedContainer> dimContainers = this.containers.get(dimension);
            return dimContainers == null
                ? new ArrayList<>()
                : dimContainers.values()
                    .stream()
                    .filter(c -> c.containsSignature(sig))
                    .sorted((a, b) -> Integer.compare(b.getSignatureCount(sig), a.getSignatureCount(sig)))
                    .collect(Collectors.toList());
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public List<TrackedContainer> getAllContainers() {
        return this.getAllContainers(this.getCurrentDimension());
    }

    public List<TrackedContainer> getAllContainers(String dimension) {
        this.lock.readLock().lock();

        try {
            Map<BlockPos, TrackedContainer> dimContainers = this.containers.get(dimension);
            return dimContainers != null ? new ArrayList<>(dimContainers.values()) : new ArrayList<>();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public int getTotalContainerCount() {
        this.lock.readLock().lock();

        try {
            return this.containers.values().stream().mapToInt(Map::size).sum();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public void clearAll() {
        this.lock.writeLock().lock();

        try {
            this.containers.clear();
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void clearCurrentDimension() {
        String dimension = this.getCurrentDimension();
        this.lock.writeLock().lock();

        try {
            this.containers.remove(dimension);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void saveData() {
        this.lock.readLock().lock();

        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 2);
            root.addProperty("saveTime", System.currentTimeMillis());
            JsonObject dimensions = new JsonObject();

            for (Entry<String, Map<BlockPos, TrackedContainer>> dimEntry : this.containers.entrySet()) {
                JsonArray dimArray = new JsonArray();

                for (TrackedContainer container : dimEntry.getValue().values()) {
                    dimArray.add(container.toJson());
                }

                dimensions.add(dimEntry.getKey(), dimArray);
            }

            root.add("dimensions", dimensions);
            Writer writer = new OutputStreamWriter(new FileOutputStream(this.tempFile), StandardCharsets.UTF_8);

            try {
                GSON.toJson(root, writer);
            } catch (Throwable var14) {
                try {
                    writer.close();
                } catch (Throwable var13) {
                    var14.addSuppressed(var13);
                }

                throw var14;
            }

            writer.close();
            if (this.dataFile.exists() && this.dataFile.length() > 0L) {
                Files.copy(this.dataFile.toPath(), this.backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(this.tempFile.toPath(), this.dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            this.saveFailures = 0;
        } catch (Exception e) {
            this.saveFailures++;
            LOGGER.error("Failed to save data (attempt {})", this.saveFailures, e);
            if (this.saveFailures > 3) {
                LOGGER.error("Multiple save failures, data may be lost!");
            }
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public void loadData() {
        this.lock.writeLock().lock();

        try {
            this.containers.clear();
            if (this.loadFromFile(this.dataFile)) {
                LOGGER.info("Loaded data from main file");
                return;
            }

            if (this.loadFromFile(this.backupFile)) {
                LOGGER.warn("Main file corrupted, loaded from backup");
                this.saveData();
                return;
            }

            File oldFile = new File(MeteorClient.FOLDER, "ChestTracker/tracked_containers.json");
            if (!oldFile.exists() || !this.loadFromFile(oldFile)) {
                LOGGER.info("No existing data found, starting fresh");
                return;
            }

            LOGGER.info("Migrated data from old format");
            this.saveData();
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private boolean loadFromFile(File file) {
        if (file.exists() && file.length() != 0L) {
            try {
                String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                if (root.has("version")) {
                    root.get("version").getAsInt();
                } else {
                    int version = 1;
                }

                if (root.has("dimensions")) {
                    JsonObject dimensions = root.getAsJsonObject("dimensions");

                    for (Entry<String, JsonElement> dimEntry : dimensions.entrySet()) {
                        String dimension = dimEntry.getKey();
                        JsonArray dimArray = dimEntry.getValue().getAsJsonArray();
                        Map<BlockPos, TrackedContainer> dimContainers = new ConcurrentHashMap<>();

                        for (JsonElement element : dimArray) {
                            try {
                                TrackedContainer container = TrackedContainer.fromJson(element.getAsJsonObject());
                                dimContainers.put(container.getPosition(), container);
                            } catch (Exception e) {
                                LOGGER.warn("Skipped corrupted container entry", e);
                            }
                        }

                        if (!dimContainers.isEmpty()) {
                            this.containers.put(dimension, dimContainers);
                        }
                    }
                }

                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to load from file: {}", file.getName(), e);
                return false;
            }
        } else {
            return false;
        }
    }

    public void exportData(String filename) throws IOException {
        this.lock.readLock().lock();

        try {
            File exportFile = new File(new File(MeteorClient.FOLDER, "ChestTracker"), filename);
            JsonObject export = new JsonObject();
            export.addProperty("version", 2);
            export.addProperty("exportTime", System.currentTimeMillis());
            export.addProperty("totalContainers", this.getTotalContainerCount());
            JsonObject dimensions = new JsonObject();

            for (Entry<String, Map<BlockPos, TrackedContainer>> dimEntry : this.containers.entrySet()) {
                JsonArray dimArray = new JsonArray();

                for (TrackedContainer container : dimEntry.getValue().values()) {
                    dimArray.add(container.toJson());
                }

                dimensions.add(dimEntry.getKey(), dimArray);
            }

            export.add("dimensions", dimensions);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8)) {
                GSON.toJson(export, writer);
            }
        } finally {
            this.lock.readLock().unlock();
        }
    }

    private String getCurrentDimension() {
        if (this.mc.level == null) {
            return "unknown";
        }

        ResourceKey<Level> key = this.mc.level.dimension();
        return key.identifier().toString();
    }
}
