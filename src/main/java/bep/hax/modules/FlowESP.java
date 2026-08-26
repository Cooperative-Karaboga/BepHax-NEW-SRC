package bep.hax.modules;

import bep.hax.Bep;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class FlowESP extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgNether = this.settings.createGroup("Nether");
    private final SettingGroup sgOverworld = this.settings.createGroup("Overworld");
    private final SettingGroup sgCache = this.settings.createGroup("Cache");
    private final SettingGroup sgFilters = this.settings.createGroup("Filters");
    private final SettingGroup sgAnim = this.settings.createGroup("Animation");
    private final Setting<SettingColor> lavaColor = this.sgGeneral
        .add(
            new Builder()
                .name("lava-color")
                .description("Base color for lava (variations auto-generated)")
                .defaultValue(new SettingColor(0, 48, 255, 28))
                .build()
        );
    private final Setting<SettingColor> waterColor = this.sgGeneral
        .add(
            new Builder()
                .name("water-color")
                .description("Base color for water (variations auto-generated)")
                .defaultValue(new SettingColor(0, 150, 255, 32))
                .build()
        );
    private final Setting<Integer> renderDistance = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("render-distance")
                .description("Max render distance in blocks")
                .defaultValue(400)
                .sliderRange(32, 320)
                .build()
        );
    private final Setting<Boolean> debug = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("debug").description("Show cache stats").defaultValue(false).build());
    private final Setting<Boolean> netherEnabled = this.sgNether
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("enabled").description("Detect lava in nether").defaultValue(true).build());
    private final Setting<Integer> netherMinY = this.sgNether
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("min-y")
                .description("Skip lava ocean below this")
                .defaultValue(32)
                .sliderRange(0, 127)
                .build()
        );
    private final Setting<Integer> roofY = this.sgNether
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("roof-y")
                .description("Y level for roof detection")
                .defaultValue(120)
                .sliderRange(100, 127)
                .build()
        );
    private final Setting<Boolean> overworldEnabled = this.sgOverworld
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("enabled")
                .description("Detect water in overworld")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> overworldMinY = this.sgOverworld
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("min-y")
                .description("Minimum Y to scan")
                .defaultValue(0)
                .sliderRange(-64, 128)
                .build()
        );
    private final Setting<Integer> overworldMaxY = this.sgOverworld
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-y")
                .description("Skip surface ocean above this")
                .defaultValue(62)
                .sliderRange(0, 320)
                .build()
        );
    private final Setting<Boolean> detectLavaOverworld = this.sgOverworld
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("detect-lava").description("Also detect lava").defaultValue(true).build());
    private final Setting<Integer> maxCachedChunks = this.sgCache
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-chunks")
                .description("Max chunks in memory")
                .defaultValue(2000)
                .sliderRange(500, 10000)
                .build()
        );
    private final Setting<Integer> evictDistance = this.sgCache
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("evict-distance")
                .description("Remove chunks beyond this (in chunks)")
                .defaultValue(32)
                .sliderRange(16, 64)
                .build()
        );
    private final Setting<Integer> saveInterval = this.sgCache
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("save-interval")
                .description("Auto-save seconds (0 = off)")
                .defaultValue(300)
                .sliderRange(0, 600)
                .build()
        );
    private final Setting<Boolean> persistCache = this.sgCache
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("persist").description("Save to disk").defaultValue(true).build());
    private final Setting<Double> minFlowRatio = this.sgFilters
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("min-flow-ratio")
                .description("Minimum flowing/source ratio")
                .defaultValue(0.5)
                .sliderRange(0.0, 5.0)
                .build()
        );
    private final Setting<Integer> minSize = this.sgFilters
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("min-size")
                .description("Minimum blocks to show")
                .defaultValue(3)
                .sliderRange(1, 50)
                .build()
        );
    private final Setting<Integer> maxSize = this.sgFilters
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-size")
                .description("Skip oceans/lakes above this")
                .defaultValue(500)
                .sliderRange(50, 5000)
                .build()
        );
    private final Setting<Boolean> gradientMode = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("gradient-mode")
                .description("Use gradient colors based on per-column flow maturity")
                .defaultValue(true)
                .build()
        );
    private final Setting<SettingColor> lavaFreshColor = this.sgGeneral
        .add(
            new Builder()
                .name("lava-fresh-color")
                .description("Color for fresh lava flows (recently loaded, short span)")
                .defaultValue(new SettingColor(0, 163, 255, 7))
                .visible(this.gradientMode::get)
                .build()
        );
    private final Setting<SettingColor> lavaMatureColor = this.sgGeneral
        .add(
            new Builder()
                .name("lava-mature-color")
                .description("Color for mature lava flows (loaded longer, longer span)")
                .defaultValue(new SettingColor(11, 0, 210, 255))
                .visible(this.gradientMode::get)
                .build()
        );
    private final Setting<SettingColor> waterFreshColor = this.sgGeneral
        .add(
            new Builder()
                .name("water-fresh-color")
                .description("Color for fresh water flows (recently loaded, short span)")
                .defaultValue(new SettingColor(0, 180, 255, 120))
                .visible(this.gradientMode::get)
                .build()
        );
    private final Setting<SettingColor> waterMatureColor = this.sgGeneral
        .add(
            new Builder()
                .name("water-mature-color")
                .description("Color for mature water flows (loaded longer, longer span)")
                .defaultValue(new SettingColor(0, 40, 150, 120))
                .visible(this.gradientMode::get)
                .build()
        );
    private final Setting<FlowESP.GradientSource> gradientSource = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                                .name("gradient-source"))
                            .description(
                                "What the gradient represents. FlowLength = per-column blocks flowed from the source; ChunkAge = time since chunk first seen; Both = blend."
                            ))
                        .defaultValue(FlowESP.GradientSource.Both))
                    .visible(this.gradientMode::get))
                .build()
        );
    private final Setting<Integer> maxFlowLength = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-flow-length")
                .description("Number of blocks a fluid has flowed from its source (0 = source only) at which the column reaches the mature color.")
                .defaultValue(10)
                .min(1)
                .sliderRange(1, 64)
                .build()
        );
    private final Setting<Integer> maxAgeSeconds = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-age-seconds")
                .description("Chunk age (seconds since first seen) at which the age gradient reaches full maturity.")
                .defaultValue(3600)
                .min(5)
                .sliderRange(30, 3600)
                .visible(() -> this.gradientMode.get() && this.gradientSource.get() != FlowESP.GradientSource.FlowLength)
                .build()
        );
    private final Setting<Double> alphaFade = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("alpha-fade")
                .description("How much alpha decreases with flow maturity (0 = no fade, 1 = full fade to transparent)")
                .defaultValue(0.7)
                .min(0.0)
                .max(1.0)
                .sliderRange(0.0, 1.0)
                .build()
        );
    private final Setting<Double> distanceFade = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("distance-fade")
                .description("How much alpha decreases with distance from player (0 = no fade, 1 = invisible at max range)")
                .defaultValue(0.3)
                .min(0.0)
                .max(1.0)
                .sliderRange(0.0, 1.0)
                .build()
        );
    private final Setting<Boolean> fadeLoad = this.sgAnim
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("fade-load")
                .description("Smoothly fade flows in when they load into render range and out when they leave it.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> fadeDuration = this.sgAnim
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("fade-duration")
                .description("How long the load/unload fade takes, in seconds.")
                .defaultValue(0.4)
                .min(0.0)
                .sliderRange(0.0, 2.0)
                .visible(this.fadeLoad::get)
                .build()
        );
    private FlowESP.LRUCache<Long, FlowESP.ChunkData> overworldCache;
    private FlowESP.LRUCache<Long, FlowESP.ChunkData> netherCache;
    private Set<Long> processing;
    private Set<Long> scannedOverworldChunks;
    private Set<Long> scannedNetherChunks;
    private ExecutorService saveThread;
    private ScheduledExecutorService autoSave;
    private volatile boolean dirty;
    private int tick;
    private final Map<Long, FlowESP.RenderState> renderStates = new HashMap<>();
    private long lastRenderNanos;
    private Object lastRenderDim;
    private static final Path CACHE_DIR = Path.of("meteor-client", "flow-esp-cache");
    private Color lavaSource;
    private Color lavaFlow;
    private Color lavaAged;
    private Color lavaRoof;
    private Color waterSource;
    private Color waterFlow;
    private Color waterAged;
    private boolean frameGradient;
    private float frameMaxFlowLen;
    private float frameAlphaFade;
    private float frameDistFade;
    private FlowESP.GradientSource frameGradientSource;
    private Color gradLavaFresh;
    private Color gradLavaMature;
    private Color gradWaterFresh;
    private Color gradWaterMature;

    public FlowESP() {
        super(Bep.HUNT_CATEGORY, "flow-esp", "Chunk activity detector via fluid spread analysis");
    }

    @Override
    public void onActivate() {
        int max = this.maxCachedChunks.get();
        this.overworldCache = new FlowESP.LRUCache<>(max);
        this.netherCache = new FlowESP.LRUCache<>(max);
        this.processing = ConcurrentHashMap.newKeySet();
        this.scannedOverworldChunks = ConcurrentHashMap.newKeySet();
        this.scannedNetherChunks = ConcurrentHashMap.newKeySet();
        this.dirty = false;
        this.tick = 0;
        this.renderStates.clear();
        this.lastRenderNanos = 0L;
        this.computeColors();
        this.saveThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FlowESP-IO");
            t.setDaemon(true);
            return t;
        });
        if (this.persistCache.get()) {
            this.loadCache();
        }

        int interval = this.saveInterval.get();
        if (interval > 0 && this.persistCache.get()) {
            this.autoSave = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "FlowESP-AutoSave");
                t.setDaemon(true);
                return t;
            });
            this.autoSave.scheduleAtFixedRate(this::saveCache, interval, interval, TimeUnit.SECONDS);
        }

        if (this.debug.get()) {
            this.info("FlowESP active - OW: " + this.overworldCache.size() + ", Nether: " + this.netherCache.size());
        }
    }

    @Override
    public void onDeactivate() {
        if (this.persistCache.get() && this.dirty) {
            this.saveCache();
        }

        if (this.saveThread != null) {
            this.saveThread.shutdownNow();
        }

        if (this.autoSave != null) {
            this.autoSave.shutdownNow();
        }

        if (this.overworldCache != null) {
            synchronized (this.overworldCache) {
                this.overworldCache.clear();
            }
        }

        if (this.netherCache != null) {
            synchronized (this.netherCache) {
                this.netherCache.clear();
            }
        }

        if (this.processing != null) {
            this.processing.clear();
        }

        if (this.scannedOverworldChunks != null) {
            this.scannedOverworldChunks.clear();
        }

        if (this.scannedNetherChunks != null) {
            this.scannedNetherChunks.clear();
        }

        this.renderStates.clear();
    }

    private void computeColors() {
        SettingColor lc = this.lavaColor.get();
        SettingColor wc = this.waterColor.get();
        this.lavaSource = new Color(lc.r, lc.g, lc.b, lc.a);
        this.lavaFlow = this.darken(lc, 0.7F);
        this.lavaAged = this.brighten(lc, 1.3F);
        this.lavaRoof = this.brighten(lc, 1.5F);
        this.waterSource = new Color(wc.r, wc.g, wc.b, wc.a);
        this.waterFlow = this.darken(wc, 0.7F);
        this.waterAged = this.brighten(wc, 1.3F);
    }

    private Color darken(SettingColor c, float factor) {
        return new Color((int)(c.r * factor), (int)(c.g * factor), (int)(c.b * factor), c.a);
    }

    private Color brighten(SettingColor c, float factor) {
        return new Color(
            Math.min(255, (int)(c.r * factor)), Math.min(255, (int)(c.g * factor)), Math.min(255, (int)(c.b * factor)), Math.min(255, (int)(c.a * 1.2F))
        );
    }

    private Color lerpColor(Color a, Color b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return new Color((int)(a.r + (b.r - a.r) * t), (int)(a.g + (b.g - a.g) * t), (int)(a.b + (b.b - a.b) * t), (int)(a.a + (b.a - a.a) * t));
    }

    private Path cacheFile(boolean nether) {
        return CACHE_DIR.resolve(nether ? "nether.dat" : "overworld.dat");
    }

    private void loadCache() {
        this.saveThread.submit(() -> {
            try {
                Files.createDirectories(CACHE_DIR);
                this.loadFile(this.cacheFile(false), this.overworldCache);
                this.loadFile(this.cacheFile(true), this.netherCache);
                if (this.debug.get()) {
                    this.info("Cache loaded");
                }
            } catch (Exception e) {
                if (this.debug.get()) {
                    this.error("Load failed: " + e.getMessage());
                }
            }
        });
    }

    private void loadFile(Path f, FlowESP.LRUCache<Long, FlowESP.ChunkData> cache) {
        if (Files.exists(f)) {
            boolean isNether = f.getFileName().toString().contains("nether");
            Set<Long> scannedSet = isNether ? this.scannedNetherChunks : this.scannedOverworldChunks;

            try (
                InputStream fis = Files.newInputStream(f);
                GZIPInputStream gis = new GZIPInputStream(fis);
                ObjectInputStream ois = new ObjectInputStream(gis);
            ) {
                int n = ois.readInt();
                long now = System.currentTimeMillis();

                for (int i = 0; i < n; i++) {
                    FlowESP.ChunkData d = (FlowESP.ChunkData)ois.readObject();
                    if (d.firstSeen <= 0L) {
                        d.firstSeen = now;
                    }

                    synchronized (cache) {
                        cache.put(d.key(), d);
                    }

                    scannedSet.add(d.key());
                }
            } catch (Exception var22) {
            }
        }
    }

    private void saveCache() {
        if (this.dirty) {
            this.dirty = false;
            this.saveThread.submit(() -> {
                try {
                    Files.createDirectories(CACHE_DIR);
                    this.saveFile(this.cacheFile(false), this.overworldCache);
                    this.saveFile(this.cacheFile(true), this.netherCache);
                    if (this.debug.get()) {
                        this.info("Cache saved");
                    }
                } catch (Exception e) {
                    if (this.debug.get()) {
                        this.error("Save failed: " + e.getMessage());
                    }
                }
            });
        }
    }

    private void saveFile(Path f, FlowESP.LRUCache<Long, FlowESP.ChunkData> cache) {
        try (
            OutputStream fos = Files.newOutputStream(f);
            GZIPOutputStream gos = new GZIPOutputStream(fos);
            ObjectOutputStream oos = new ObjectOutputStream(gos);
        ) {
            synchronized (cache) {
                oos.writeInt(cache.size());

                for (FlowESP.ChunkData d : cache.values()) {
                    oos.writeObject(d);
                }
            }
        } catch (Exception var17) {
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (this.mc.level != null && this.mc.level.dimension() != Level.END) {
            ChunkAccess chunk = event.chunk();
            long key = chunk.getPos().toLong();
            boolean nether = this.mc.level.dimension() == Level.NETHER;
            FlowESP.LRUCache<Long, FlowESP.ChunkData> cache = this.getCache();
            Set<Long> scannedSet = nether ? this.scannedNetherChunks : this.scannedOverworldChunks;
            if (!scannedSet.contains(key) && this.processing.add(key)) {
                FlowESP.ChunkData data = this.scan(chunk, nether);
                if (data != null && this.shouldCache(data)) {
                    synchronized (cache) {
                        cache.put(key, data);
                    }

                    scannedSet.add(key);
                    this.dirty = true;
                }

                this.processing.remove(key);
            }
        }
    }

    private FlowESP.LRUCache<Long, FlowESP.ChunkData> getCache() {
        return this.mc.level != null && this.mc.level.dimension() == Level.NETHER ? this.netherCache : this.overworldCache;
    }

    private FlowESP.ChunkData scan(ChunkAccess chunk, boolean nether) {
        int minY;
        int maxY;
        if (nether) {
            if (!this.netherEnabled.get()) {
                return null;
            }

            minY = this.netherMinY.get();
            maxY = 128;
        } else {
            if (!this.overworldEnabled.get()) {
                return null;
            }

            minY = this.overworldMinY.get();
            maxY = this.overworldMaxY.get();
        }

        int roofLevel = this.roofY.get();
        List<FlowESP.FlowColumn> columns = new ArrayList<>();
        int srcTotal = 0;
        int flowTotal = 0;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunk.getPos().getMinBlockX() + lx;
                int wz = chunk.getPos().getMinBlockZ() + lz;
                int colBottom = -1;
                int colTop = -1;
                boolean colLava = false;
                boolean colHasSource = false;
                boolean colRoof = false;
                int colSrc = 0;
                int colFlow = 0;

                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(wx, y, wz);
                    BlockState state = chunk.getBlockState(pos);
                    boolean isLava = state.getBlock() == Blocks.LAVA;
                    boolean isWater = state.getBlock() == Blocks.WATER;
                    if (!isLava && !isWater) {
                        if (colBottom != -1 && colFlow > 0 && (!colRoof || colFlow > 0)) {
                            columns.add(new FlowESP.FlowColumn(lx, lz, colBottom, colTop, colFlow, colLava, colHasSource, colRoof));
                            srcTotal += colSrc;
                            flowTotal += colFlow;
                        }

                        colBottom = -1;
                        colTop = -1;
                        colSrc = 0;
                        colFlow = 0;
                        colHasSource = false;
                        colRoof = false;
                    } else if ((!nether || !isWater)
                        && (nether || !isWater || this.overworldEnabled.get())
                        && (nether || !isLava || this.detectLavaOverworld.get())) {
                        if (isWater) {
                            FluidState fs = state.getFluidState();
                            if (fs.isSource() && this.countAdjSources(chunk, pos) >= 3) {
                                continue;
                            }
                        }

                        FluidState fs = state.getFluidState();
                        boolean isSrc = fs.isSource();
                        boolean isRoofLevel = nether && isLava && y >= roofLevel;
                        if (colBottom == -1) {
                            colBottom = y;
                            colLava = isLava;
                        }

                        colTop = y;
                        if (isSrc) {
                            colSrc++;
                            colHasSource = true;
                        } else {
                            colFlow++;
                        }

                        if (isRoofLevel) {
                            colRoof = true;
                        }
                    }
                }

                if (colBottom != -1 && colFlow > 0 && (!colRoof || colFlow > 0)) {
                    columns.add(new FlowESP.FlowColumn(lx, lz, colBottom, colTop, colFlow, colLava, colHasSource, colRoof));
                    srcTotal += colSrc;
                    flowTotal += colFlow;
                }
            }
        }

        return columns.isEmpty() ? null : new FlowESP.ChunkData(chunk.getPos().x, chunk.getPos().z, srcTotal, flowTotal, columns);
    }

    private int countAdjSources(ChunkAccess chunk, BlockPos pos) {
        int count = 0;
        int[][] off = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] o : off) {
            BlockPos n = pos.offset(o[0], 0, o[1]);
            int lx = n.getX() - chunk.getPos().getMinBlockX();
            int lz = n.getZ() - chunk.getPos().getMinBlockZ();
            if (lx >= 0 && lx <= 15 && lz >= 0 && lz <= 15) {
                BlockState s = chunk.getBlockState(n);
                if (s.getBlock() == Blocks.WATER && s.getFluidState().isSource()) {
                    count++;
                }
            }
        }

        return count;
    }

    private boolean shouldCache(FlowESP.ChunkData d) {
        if (d.columns.length == 0) {
            return false;
        }

        if (d.total() >= this.minSize.get() && d.total() <= this.maxSize.get()) {
            for (FlowESP.FlowColumn c : d.columns) {
                if (c.isRoof()) {
                    return true;
                }
            }

            return d.flowRatio() >= this.minFlowRatio.get();
        } else {
            return false;
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.level != null && this.mc.player != null) {
            if (this.tick % 100 == 0) {
                this.computeColors();
            }

            this.tick++;
            if (this.tick >= 200) {
                this.tick = 0;
                this.evictDistant();
            }
        }
    }

    private void evictDistant() {
        if (this.mc.player != null) {
            int px = this.mc.player.chunkPosition().x;
            int pz = this.mc.player.chunkPosition().z;
            int dist = this.evictDistance.get();
            int distSq = dist * dist;
            FlowESP.LRUCache<Long, FlowESP.ChunkData> cache = this.getCache();
            List<Long> remove = new ArrayList<>();
            synchronized (cache) {
                for (Entry<Long, FlowESP.ChunkData> e : cache.entrySet()) {
                    FlowESP.ChunkData d = e.getValue();
                    int dx = d.chunkX - px;
                    int dz = d.chunkZ - pz;
                    if (dx * dx + dz * dz > distSq) {
                        remove.add(e.getKey());
                    }
                }

                remove.forEach(cache::remove);
            }

            if (this.debug.get() && !remove.isEmpty()) {
                this.info("Evicted " + remove.size() + " chunks");
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.level != null && this.mc.player != null && this.mc.level.dimension() != Level.END) {
            Object dim = this.mc.level.dimension();
            if (!dim.equals(this.lastRenderDim)) {
                this.renderStates.clear();
                this.lastRenderNanos = 0L;
                this.lastRenderDim = dim;
            }

            FlowESP.LRUCache<Long, FlowESP.ChunkData> cache = this.getCache();
            Vec3 pp = this.mc.player.position();
            int rdist = this.renderDistance.get();
            int rdistSq = rdist * rdist;
            this.frameGradient = this.gradientMode.get();
            this.frameMaxFlowLen = this.maxFlowLength.get().floatValue();
            this.frameAlphaFade = this.alphaFade.get().floatValue();
            this.frameDistFade = this.distanceFade.get().floatValue();
            this.frameGradientSource = this.gradientSource.get();
            if (this.frameGradient) {
                SettingColor lf = this.lavaFreshColor.get();
                SettingColor lm = this.lavaMatureColor.get();
                SettingColor wf = this.waterFreshColor.get();
                SettingColor wm = this.waterMatureColor.get();
                this.gradLavaFresh = new Color(lf.r, lf.g, lf.b, lf.a);
                this.gradLavaMature = new Color(lm.r, lm.g, lm.b, lm.a);
                this.gradWaterFresh = new Color(wf.r, wf.g, wf.b, wf.a);
                this.gradWaterMature = new Color(wm.r, wm.g, wm.b, wm.a);
            }

            FlowESP.ChunkData[] snapshot;
            synchronized (cache) {
                snapshot = cache.values().toArray(new FlowESP.ChunkData[0]);
            }

            boolean fade = this.fadeLoad.get();
            double dur = this.fadeDuration.get();
            long nowNanos = System.nanoTime();
            float dt = this.lastRenderNanos == 0L ? 0.0F : (float)(nowNanos - this.lastRenderNanos) / 1.0E9F;
            this.lastRenderNanos = nowNanos;
            dt = Math.max(0.0F, Math.min(dt, 0.1F));
            float step = fade && !(dur <= 0.0) ? (float)(dt / dur) : 1.0F;
            Set<Long> inRange = new HashSet<>();

            for (FlowESP.ChunkData data : snapshot) {
                double cx = data.chunkX * 16 + 8;
                double cz = data.chunkZ * 16 + 8;
                double dSq = (pp.x - cx) * (pp.x - cx) + (pp.z - cz) * (pp.z - cz);
                if (!(dSq > rdistSq)) {
                    long key = data.key();
                    inRange.add(key);
                    FlowESP.RenderState st = this.renderStates.get(key);
                    if (st == null) {
                        this.renderStates.put(key, new FlowESP.RenderState(data, fade ? 0.0F : 1.0F));
                    } else {
                        st.data = data;
                    }
                }
            }

            long nowMs = System.currentTimeMillis();
            Iterator<Entry<Long, FlowESP.RenderState>> it = this.renderStates.entrySet().iterator();

            while (it.hasNext()) {
                Entry<Long, FlowESP.RenderState> e = it.next();
                FlowESP.RenderState st = e.getValue();
                boolean active = inRange.contains(e.getKey());
                float target = active ? 1.0F : 0.0F;
                if (st.alpha < target) {
                    st.alpha = Math.min(target, st.alpha + step);
                } else if (st.alpha > target) {
                    st.alpha = Math.max(target, st.alpha - step);
                }

                if (!active && st.alpha <= 0.001F) {
                    it.remove();
                } else if (!(st.alpha <= 0.001F)) {
                    this.renderChunk(event, st.data, pp, rdistSq, st.alpha, nowMs);
                }
            }
        }
    }

    private void renderChunk(Render3DEvent event, FlowESP.ChunkData data, Vec3 pp, int rdistSq, float animAlpha, long nowMs) {
        int baseX = data.chunkX * 16;
        int baseZ = data.chunkZ * 16;
        long age = data.firstSeen > 0L ? Math.max(0L, nowMs - data.firstSeen) : 0L;
        float chunkAgeFactor = Math.min(1.0F, (float)age / (this.maxAgeSeconds.get().intValue() * 1000.0F));

        for (FlowESP.FlowColumn col : data.columns) {
            int wx = baseX + col.x;
            int wz = baseZ + col.z;
            double bDistSq = pp.distanceToSqr(wx + 0.5, col.bottomY, wz + 0.5);
            if (!(bDistSq > rdistSq)) {
                float distRatio = (float)(bDistSq / rdistSq);
                if (this.frameGradient) {
                    this.renderGradientColumn(event, col, wx, wz, chunkAgeFactor, animAlpha, distRatio);
                } else {
                    float lengthFactor = Math.min(1.0F, col.flowLen / this.frameMaxFlowLen);
                    float alphaMul = 1.0F - lengthFactor * this.frameAlphaFade;
                    float distMul = 1.0F - distRatio * this.frameDistFade;
                    Color color = this.withAlpha(this.pickColor(col, data.flowRatio()), animAlpha * Math.max(0.02F, alphaMul) * Math.max(0.05F, distMul));
                    event.renderer.box(wx, col.bottomY, wz, wx + 1, col.topY + 1, wz + 1, color, color, ShapeMode.Both, 0);
                }
            }
        }
    }

    private void renderGradientColumn(Render3DEvent event, FlowESP.FlowColumn col, int wx, int wz, float chunkAgeFactor, float animAlpha, float distRatio) {
        int span = col.topY - col.bottomY + 1;
        float lengthFactor = Math.min(1.0F, col.flowLen / this.frameMaxFlowLen);

        float maturity = switch (this.frameGradientSource) {
            case FlowLength -> lengthFactor;
            case ChunkAge -> chunkAgeFactor;
            case Both -> lengthFactor * 0.6F + chunkAgeFactor * 0.4F;
        };
        Color freshColor = col.isLava() ? this.gradLavaFresh : this.gradWaterFresh;
        Color matureColor = col.isLava() ? this.gradLavaMature : this.gradWaterMature;
        float alphaMul = 1.0F - maturity * this.frameAlphaFade;
        float distMul = 1.0F - distRatio * this.frameDistFade;
        float totalAlpha = animAlpha * Math.max(0.02F, alphaMul) * Math.max(0.05F, distMul);
        float gradientStrength = Math.min(0.15F, span * 0.005F);
        float topMaturity = Math.max(0.0F, maturity - gradientStrength);
        float bottomMaturity = Math.min(1.0F, maturity + gradientStrength);
        Color topColor = this.withAlpha(this.lerpColor(freshColor, matureColor, topMaturity), totalAlpha);
        Color bottomColor = this.withAlpha(this.lerpColor(freshColor, matureColor, bottomMaturity), totalAlpha);
        double x1 = wx;
        double x2 = wx + 1;
        double y1 = col.bottomY;
        double y2 = col.topY + 1;
        double z1 = wz;
        double z2 = wz + 1;
        event.renderer.gradientQuadVertical(x1, y1, z1, x2, y2, z1, topColor, bottomColor);
        event.renderer.gradientQuadVertical(x1, y1, z2, x2, y2, z2, topColor, bottomColor);
        event.renderer.gradientQuadVertical(x1, y1, z1, x1, y2, z2, topColor, bottomColor);
        event.renderer.gradientQuadVertical(x2, y1, z1, x2, y2, z2, topColor, bottomColor);
        event.renderer.quadHorizontal(x1, y2, z1, x2, z2, topColor);
        event.renderer.quadHorizontal(x1, y1, z1, x2, z2, bottomColor);
        event.renderer.line(x1, y1, z1, x1, y2, z1, bottomColor, topColor);
        event.renderer.line(x2, y1, z1, x2, y2, z1, bottomColor, topColor);
        event.renderer.line(x1, y1, z2, x1, y2, z2, bottomColor, topColor);
        event.renderer.line(x2, y1, z2, x2, y2, z2, bottomColor, topColor);
        event.renderer.line(x1, y1, z1, x2, y1, z1, bottomColor, bottomColor);
        event.renderer.line(x1, y1, z2, x2, y1, z2, bottomColor, bottomColor);
        event.renderer.line(x1, y2, z1, x2, y2, z1, topColor, topColor);
        event.renderer.line(x1, y2, z2, x2, y2, z2, topColor, topColor);
        event.renderer.line(x1, y1, z1, x1, y1, z2, bottomColor, bottomColor);
        event.renderer.line(x2, y1, z1, x2, y1, z2, bottomColor, bottomColor);
        event.renderer.line(x1, y2, z1, x1, y2, z2, topColor, topColor);
        event.renderer.line(x2, y2, z1, x2, y2, z2, topColor, topColor);
    }

    private Color withAlpha(Color c, float mul) {
        int a = Math.max(0, Math.min(255, Math.round(c.a * mul)));
        return new Color(c.r, c.g, c.b, a);
    }

    private Color pickColor(FlowESP.FlowColumn col, double ratio) {
        if (col.isLava()) {
            if (col.isRoof()) {
                return this.lavaRoof;
            } else if (ratio > 2.0) {
                return this.lavaAged;
            } else {
                return col.hasSource() ? this.lavaSource : this.lavaFlow;
            }
        } else if (ratio > 2.0) {
            return this.waterAged;
        } else {
            return col.hasSource() ? this.waterSource : this.waterFlow;
        }
    }

    private static class ChunkData implements Serializable {
        private static final long serialVersionUID = 1L;
        final int chunkX;
        final int chunkZ;
        final short sourceCount;
        final short flowCount;
        final FlowESP.FlowColumn[] columns;
        long firstSeen;

        ChunkData(int cx, int cz, int src, int flow, List<FlowESP.FlowColumn> cols) {
            this.chunkX = cx;
            this.chunkZ = cz;
            this.sourceCount = (short)Math.min(src, 32767);
            this.flowCount = (short)Math.min(flow, 32767);
            this.columns = cols.toArray(new FlowESP.FlowColumn[0]);
            this.firstSeen = System.currentTimeMillis();
        }

        double flowRatio() {
            return (double)this.flowCount / (this.sourceCount + 1);
        }

        int total() {
            return this.sourceCount + this.flowCount;
        }

        long key() {
            return ChunkPos.asLong(this.chunkX, this.chunkZ);
        }
    }

    private static class FlowColumn implements Serializable {
        private static final long serialVersionUID = 1L;
        final short x;
        final short z;
        final short bottomY;
        final short topY;
        final short flowLen;
        final byte flags;

        FlowColumn(int x, int z, int bottomY, int topY, int flowLen, boolean isLava, boolean hasSource, boolean isRoof) {
            this.x = (short)x;
            this.z = (short)z;
            this.bottomY = (short)bottomY;
            this.topY = (short)topY;
            this.flowLen = (short)flowLen;
            this.flags = (byte)((isLava ? 1 : 0) | (hasSource ? 2 : 0) | (isRoof ? 4 : 0));
        }

        boolean isLava() {
            return (this.flags & 1) != 0;
        }

        boolean hasSource() {
            return (this.flags & 2) != 0;
        }

        boolean isRoof() {
            return (this.flags & 4) != 0;
        }
    }

    public enum GradientSource {
        FlowLength,
        ChunkAge,
        Both;
    }

    private static class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private final int max;

        LRUCache(int max) {
            super(max / 4, 0.75F, true);
            this.max = max;
        }

        @Override
        protected boolean removeEldestEntry(Entry<K, V> e) {
            return this.size() > this.max;
        }
    }

    private static final class RenderState {
        FlowESP.ChunkData data;
        float alpha;

        RenderState(FlowESP.ChunkData data, float alpha) {
            this.data = data;
            this.alpha = alpha;
        }
    }
}
