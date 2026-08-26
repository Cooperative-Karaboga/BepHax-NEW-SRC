package bep.hax.capes;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CapeManager {
    private static final Logger LOG = LoggerFactory.getLogger("BepHax-Capes");
    private static final String API_URL = "https://bep.dek.to/api/capes";
    private static final String CAPE_TEXTURE_URL = "https://bep.dek.to/api/cape-texture";
    private static final Duration TIMEOUT = Duration.ofSeconds(30L);
    private static final String CACHE_FOLDER = "bephax-capes";
    private static final String CAPE_LIST_CACHE_FILE = "cape-list.json";
    private static final Gson GSON = new Gson();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private static CapeManager instance;
    private final Map<UUID, String> playerCapeNames = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> playerBadges = new ConcurrentHashMap<>();
    private final Map<String, CapeManager.CapeTexture> capeTextures = new ConcurrentHashMap<>();
    private final List<CapeManager.CapeTexture> toRegister = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> knownCapeNames = ConcurrentHashMap.newKeySet();
    private final Path cacheDir;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BepHax-Cape-Fetcher");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean initialized = false;
    private volatile String currentToken = null;
    private volatile long tokenExpires = 0L;
    private int capeIdCounter = 0;

    private CapeManager() {
        this.cacheDir = FabricLoader.getInstance().getGameDir().resolve("meteor-client").resolve("bephax-capes");

        try {
            Files.createDirectories(this.cacheDir);
        } catch (IOException e) {
            LOG.warn("Failed to create cape cache directory: {}", e.getMessage());
        }
    }

    public static synchronized CapeManager getInstance() {
        if (instance == null) {
            instance = new CapeManager();
        }

        return instance;
    }

    public void initialize() {
        if (!this.initialized) {
            this.initialized = true;
            MeteorClient.EVENT_BUS.subscribe(this);
            this.fetchCapesAsync();
            this.scheduler.scheduleWithFixedDelay(this::fetchCapesAsync, 30L, 30L, TimeUnit.MINUTES);
        }
    }

    @EventHandler
    private void onTick(Post event) {
        this.tick();
    }

    public void fetchCapesAsync() {
        CompletableFuture.runAsync(this::fetchCapes, this.scheduler);
    }

    private void fetchCapes() {
        this.loadCachedCapeList();
    }

    private void cacheCapeListResponse(String responseJson) {
        try {
            Path cacheFile = this.cacheDir.resolve("cape-list.json");
            Files.writeString(cacheFile, responseJson, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Failed to cache cape list: {}", e.getMessage());
        }
    }

    private void loadCachedCapeList() {
        try {
            Path cacheFile = this.cacheDir.resolve("cape-list.json");
            if (!Files.exists(cacheFile)) {
                return;
            }

            String cachedJson = Files.readString(cacheFile, StandardCharsets.UTF_8);
            CapeData.CapeResponse capeResponse = GSON.fromJson(cachedJson, CapeData.CapeResponse.class);
            if (capeResponse.success()) {
                LOG.info("Loaded cape list from cache");
                this.processCapeResponse(capeResponse);
            }
        } catch (IOException e) {
            LOG.warn("Failed to load cached cape list: {}", e.getMessage());
        }
    }

    private void processCapeResponse(CapeData.CapeResponse response) {
        if (response.capes() != null) {
            this.playerCapeNames.clear();

            for (CapeData.Cape cape : response.capes()) {
                String capeName = cape.name();
                if (cape.uuids() != null) {
                    for (CapeData.CapeUser user : cape.uuids()) {
                        UUID uuid = user.toUUID();
                        if (uuid != null) {
                            this.playerCapeNames.put(uuid, capeName);
                        }
                    }
                }

                if (!this.capeTextures.containsKey(capeName)) {
                    this.capeTextures.put(capeName, new CapeManager.CapeTexture(capeName, this.createIdentifier(capeName)));
                }
            }

            this.playerBadges.clear();
            if (response.badges() != null) {
                for (CapeData.BadgeUser user : response.badges()) {
                    UUID uuid = user.toUUID();
                    if (uuid != null && user.badges() != null && !user.badges().isEmpty()) {
                        this.playerBadges.put(uuid, List.copyOf(user.badges()));
                    }
                }
            }

            LOG.info("Processed {} capes, {} player mappings, {} badge holders", response.capes().size(), this.playerCapeNames.size(), this.playerBadges.size());
        }
    }

    private void downloadCapeTexture(CapeManager.CapeTexture texture) {
        if (!texture.downloading && !texture.downloaded) {
            Path cachedFile = this.getCapeTextureCachePath(texture.name);
            if (Files.exists(cachedFile)) {
                texture.downloading = true;
                CompletableFuture.runAsync(() -> {
                    try (InputStream in = Files.newInputStream(cachedFile)) {
                        texture.image = NativeImage.read(in);
                        synchronized (this.toRegister) {
                            this.toRegister.add(texture);
                        }

                        this.knownCapeNames.add(texture.name);
                        LOG.debug("Loaded cape '{}' from disk cache", texture.name);
                    } catch (IOException e) {
                        LOG.warn("Failed to load cape '{}' from cache, will download: {}", texture.name, e.getMessage());

                        try {
                            Files.deleteIfExists(cachedFile);
                        } catch (IOException var6) {
                        }

                        texture.downloading = false;
                        this.downloadCapeTextureFromApi(texture);
                    }
                }, this.scheduler);
            } else {
                this.downloadCapeTextureFromApi(texture);
            }
        }
    }

    private void downloadCapeTextureFromApi(CapeManager.CapeTexture texture) {
        if (!texture.downloading && !texture.downloaded) {
            texture.downloading = true;
            CompletableFuture.runAsync(
                () -> {
                    try {
                        if (this.currentToken == null || System.currentTimeMillis() > this.tokenExpires) {
                            texture.downloading = false;
                            LOG.debug("Token expired, cape '{}' download deferred", texture.name);
                            return;
                        }

                        JsonObject requestBody = new JsonObject();
                        requestBody.addProperty("token", this.currentToken);
                        requestBody.addProperty("name", texture.name);
                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("https://bep.dek.to/api/cape-texture"))
                            .header("Content-Type", "application/json")
                            .header("Accept", "image/png")
                            .POST(BodyPublishers.ofString(GSON.toJson(requestBody), StandardCharsets.UTF_8))
                            .timeout(TIMEOUT)
                            .build();
                        HttpResponse<InputStream> response = CLIENT.send(request, BodyHandlers.ofInputStream());
                        if (response.statusCode() == 401) {
                            this.currentToken = null;
                            texture.downloading = false;
                            LOG.debug("Token invalid, cape '{}' download deferred", texture.name);
                            return;
                        }

                        if (response.statusCode() != 200) {
                            texture.downloading = false;
                            LOG.warn("Failed to download cape '{}': HTTP {}", texture.name, response.statusCode());
                            return;
                        }

                        try (InputStream in = response.body()) {
                            byte[] imageData = in.readAllBytes();
                            this.saveCapeToCache(texture.name, imageData);
                            this.knownCapeNames.add(texture.name);
                            texture.image = NativeImage.read(imageData);
                            synchronized (this.toRegister) {
                                this.toRegister.add(texture);
                            }

                            LOG.info("Downloaded and cached cape '{}'", texture.name);
                        }
                    } catch (IOException | InterruptedException e) {
                        texture.downloading = false;
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }

                        LOG.warn("Failed to download cape '{}': {}", texture.name, e.getMessage());
                    }
                },
                this.scheduler
            );
        }
    }

    private Path getCapeTextureCachePath(String capeName) {
        String safeName = capeName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return this.cacheDir.resolve(safeName + ".png");
    }

    private void saveCapeToCache(String capeName, byte[] imageData) {
        try {
            Path cacheFile = this.getCapeTextureCachePath(capeName);

            try (OutputStream out = Files.newOutputStream(cacheFile)) {
                out.write(imageData);
            }
        } catch (IOException e) {
            LOG.warn("Failed to cache cape '{}': {}", capeName, e.getMessage());
        }
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getTextureManager() != null) {
            synchronized (this.toRegister) {
                for (CapeManager.CapeTexture texture : this.toRegister) {
                    try {
                        mc.getTextureManager().register(texture.identifier, new DynamicTexture(null, texture.image));
                        texture.image = null;
                        texture.downloading = false;
                        texture.downloaded = true;
                    } catch (Exception var7) {
                    }
                }

                this.toRegister.clear();
            }
        }
    }

    public Identifier getCapeTexture(UUID playerUuid) {
        if (this.initialized && playerUuid != null) {
            String capeName = this.playerCapeNames.get(playerUuid);
            if (capeName == null) {
                return null;
            }

            CapeManager.CapeTexture texture = this.capeTextures.get(capeName);
            if (texture == null) {
                return null;
            }

            if (texture.downloaded) {
                return texture.identifier;
            }

            if (!texture.downloading) {
                this.downloadCapeTexture(texture);
            }

            return null;
        } else {
            return null;
        }
    }

    public String getCapeName(UUID playerUuid) {
        return playerUuid == null ? null : this.playerCapeNames.get(playerUuid);
    }

    public List<String> getBadges(UUID playerUuid) {
        if (playerUuid == null) {
            return List.of();
        }

        List<String> badges = this.playerBadges.get(playerUuid);
        return badges == null ? List.of() : badges;
    }

    private Identifier createIdentifier(String capeName) {
        String safeName = capeName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return Identifier.fromNamespaceAndPath("bephax", "capes/" + safeName + "_" + this.capeIdCounter++);
    }

    private static class CapeTexture {
        final String name;
        final Identifier identifier;
        volatile boolean downloading = false;
        volatile boolean downloaded = false;
        NativeImage image;

        CapeTexture(String name, Identifier identifier) {
            this.name = name;
            this.identifier = identifier;
        }
    }
}
