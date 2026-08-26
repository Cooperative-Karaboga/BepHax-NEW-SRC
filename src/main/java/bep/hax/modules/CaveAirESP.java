package bep.hax.modules;

import bep.hax.Bep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

public class CaveAirESP extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgDetection = this.settings.createGroup("Detection");
    private final SettingGroup sgCaveAir = this.settings.createGroup("Cave Air Render");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Boolean> overworld = this.sgGeneral
        .add(new Builder().name("overworld").description("Scan in the Overworld").defaultValue(true).build());
    private final Setting<Boolean> nether = this.sgGeneral.add(new Builder().name("nether").description("Scan in the Nether").defaultValue(true).build());
    private final Setting<Boolean> end = this.sgGeneral.add(new Builder().name("end").description("Scan in the End").defaultValue(false).build());
    private final Setting<Integer> minY = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("min-y")
                .description("Minimum Y level to scan")
                .defaultValue(-60)
                .sliderRange(-64, 320)
                .build()
        );
    private final Setting<Integer> maxY = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-y")
                .description("Maximum Y level to scan")
                .defaultValue(128)
                .sliderRange(-64, 320)
                .build()
        );
    private final Setting<Integer> renderDistance = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("render-distance")
                .description("Maximum distance to render")
                .defaultValue(128)
                .sliderRange(16, 256)
                .build()
        );
    private final Setting<Boolean> debug = this.sgGeneral.add(new Builder().name("debug").description("Show debug information").defaultValue(false).build());
    private final Setting<Boolean> highlightAllAirInCave = this.sgDetection
        .add(
            new Builder()
                .name("highlight-all-air-in-cave")
                .description("Highlight every regular AIR block that is inside a CAVE_AIR region")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> maxPortalWidth = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-portal-width")
                .description("Maximum portal width to detect (outer frame). Larger = more false positives")
                .defaultValue(8)
                .sliderRange(4, 23)
                .build()
        );
    private final Setting<Integer> maxPortalHeight = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-portal-height")
                .description("Maximum portal height to detect (outer frame). Larger = more false positives")
                .defaultValue(10)
                .sliderRange(5, 23)
                .build()
        );
    private final Setting<Boolean> showAllCaveAir = this.sgCaveAir
        .add(new Builder().name("show-all-cave-air").description("Render all cave air blocks (merged into large boxes)").defaultValue(false).build());
    private final Setting<Integer> caveAirRenderDist = this.sgCaveAir
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("cave-air-render-distance")
                .description("Max distance to render cave air")
                .defaultValue(64)
                .sliderRange(16, 256)
                .visible(this.showAllCaveAir::get)
                .build()
        );
    private final Setting<SettingColor> caveAirFillColor = this.sgCaveAir
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("cave-air-fill")
                .description("Fill color for cave air")
                .defaultValue(new SettingColor(50, 150, 255, 30))
                .visible(this.showAllCaveAir::get)
                .build()
        );
    private final Setting<SettingColor> caveAirLineColor = this.sgCaveAir
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("cave-air-line")
                .description("Outline color for cave air")
                .defaultValue(new SettingColor(50, 150, 255, 80))
                .visible(this.showAllCaveAir::get)
                .build()
        );
    private final Setting<ShapeMode> caveAirShapeMode = this.sgCaveAir
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                                .name("cave-air-shape"))
                            .description("How to render cave air boxes"))
                        .defaultValue(ShapeMode.Both))
                    .visible(this.showAllCaveAir::get))
                .build()
        );
    private final Setting<SettingColor> disturbanceColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("disturbance-color")
                .description("Color for detected disturbances")
                .defaultValue(new SettingColor(255, 50, 50, 150))
                .build()
        );
    private final Setting<SettingColor> lineColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Outline color for disturbances")
                .defaultValue(new SettingColor(255, 100, 100, 255))
                .build()
        );
    private final Setting<ShapeMode> shapeMode = this.sgRender
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("shape-mode"))
                        .description("How to render disturbance boxes"))
                    .defaultValue(ShapeMode.Both))
                .build()
        );
    private final Setting<Boolean> renderBeacon = this.sgRender
        .add(new Builder().name("beacon").description("Draw vertical beacon line above portal detections").defaultValue(true).build());
    private final Setting<Integer> beaconHeight = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("beacon-height")
                .description("Height of beacon line")
                .defaultValue(64)
                .sliderRange(8, 128)
                .visible(this.renderBeacon::get)
                .build()
        );
    private final Setting<Boolean> portalDetectionSound = this.sgGeneral
        .add(new Builder().name("portal-detection-sound").description("Play a sound when a portal-shaped disturbance is detected").defaultValue(true).build());
    private final Map<Long, CaveAirESP.ChunkData> chunkCache = new ConcurrentHashMap<>();
    private final Set<Long> pendingChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> processingChunks = ConcurrentHashMap.newKeySet();
    private final Set<String> notifiedPortals = ConcurrentHashMap.newKeySet();
    private ExecutorService scanExecutor;
    private Color fillColor;
    private Color outlineColor;
    private Color caveAirFill;
    private Color caveAirLine;
    private int tick;
    private volatile List<CaveAirESP.RenderCluster> renderClusters = Collections.emptyList();
    private volatile List<CaveAirESP.RenderBox> renderCaveAir = Collections.emptyList();
    private volatile List<long[]> renderAirInCave = Collections.emptyList();
    private static final int[][] OFFSETS = new int[][]{{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};

    public CaveAirESP() {
        super(Bep.HUNT_CATEGORY, "cave-air", "Detects portal-shaped disturbances in cave air");
    }

    private boolean isDimensionEnabled() {
        if (this.mc.level == null) {
            return false;
        } else {
            ResourceKey<Level> key = this.mc.level.dimension();
            if (key == Level.OVERWORLD) {
                return this.overworld.get();
            } else if (key == Level.NETHER) {
                return this.nether.get();
            } else {
                return key == Level.END ? this.end.get() : false;
            }
        }
    }

    @Override
    public void onActivate() {
        this.chunkCache.clear();
        this.pendingChunks.clear();
        this.processingChunks.clear();
        this.notifiedPortals.clear();
        this.renderClusters = Collections.emptyList();
        this.renderCaveAir = Collections.emptyList();
        this.renderAirInCave = Collections.emptyList();
        this.tick = 0;
        this.computeColors();
        this.scanExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "CaveAirESP-Scan");
            t.setDaemon(true);
            t.setPriority(1);
            return t;
        });
        if (this.debug.get()) {
            this.info("CaveAirESP activated");
        }
    }

    @Override
    public void onDeactivate() {
        if (this.scanExecutor != null) {
            this.scanExecutor.shutdownNow();
            this.scanExecutor = null;
        }

        this.chunkCache.clear();
        this.pendingChunks.clear();
        this.processingChunks.clear();
        this.notifiedPortals.clear();
        this.renderClusters = Collections.emptyList();
        this.renderCaveAir = Collections.emptyList();
        this.renderAirInCave = Collections.emptyList();
    }

    private void rebuildClusterCache() {
        if (this.mc.player != null) {
            Vec3 playerPos = this.mc.player.getEyePosition();
            int rdist = this.renderDistance.get();
            int rdistSq = rdist * rdist;
            List<CaveAirESP.RenderCluster> newClusters = new ArrayList<>();
            List<long[]> newAirInCave = new ArrayList<>();

            for (Long key : this.chunkCache.keySet()) {
                CaveAirESP.ChunkData data = this.chunkCache.get(key);
                if (data != null) {
                    double chunkCenterX = data.chunkX * 16 + 8;
                    double chunkCenterZ = data.chunkZ * 16 + 8;
                    double chunkDistSq = (playerPos.x - chunkCenterX) * (playerPos.x - chunkCenterX)
                        + (playerPos.z - chunkCenterZ) * (playerPos.z - chunkCenterZ);
                    if (!(chunkDistSq > rdistSq * 4)) {
                        if (data.clusters != null) {
                            for (CaveAirESP.DisturbanceCluster cluster : data.clusters) {
                                double distSq = this.nearestPointDistanceSq(playerPos, cluster.bounds);
                                if (distSq <= rdistSq) {
                                    newClusters.add(new CaveAirESP.RenderCluster(cluster.bounds, cluster.portalShaped));
                                }
                            }
                        }

                        if (this.highlightAllAirInCave.get() && data.allAirInCave != null) {
                            for (long[] pos : data.allAirInCave) {
                                double distSq = playerPos.distanceToSqr(pos[0] + 0.5, pos[1] + 0.5, pos[2] + 0.5);
                                if (distSq <= rdistSq) {
                                    newAirInCave.add(pos);
                                }
                            }
                        }
                    }
                }
            }

            this.renderClusters = newClusters;
            this.renderAirInCave = newAirInCave;
        }
    }

    private void rebuildCaveAirCache() {
        if (this.mc.player != null && this.showAllCaveAir.get()) {
            Vec3 playerPos = this.mc.player.getEyePosition();
            int caRdist = this.caveAirRenderDist.get();
            int bufferedDist = caRdist + caRdist / 10 + 8;
            int bufferedDistSq = bufferedDist * bufferedDist;
            List<CaveAirESP.RenderBox> newCaveAir = new ArrayList<>();

            for (Long key : this.chunkCache.keySet()) {
                CaveAirESP.ChunkData data = this.chunkCache.get(key);
                if (data != null && data.caveAirBoxes != null) {
                    double chunkCenterX = data.chunkX * 16 + 8;
                    double chunkCenterZ = data.chunkZ * 16 + 8;
                    double chunkDistSq = (playerPos.x - chunkCenterX) * (playerPos.x - chunkCenterX)
                        + (playerPos.z - chunkCenterZ) * (playerPos.z - chunkCenterZ);
                    if (!(chunkDistSq > bufferedDistSq * 4)) {
                        for (CaveAirESP.MergedBox box : data.caveAirBoxes) {
                            double distSq = this.nearestPointDistanceSq(playerPos, box);
                            if (distSq <= bufferedDistSq) {
                                newCaveAir.add(new CaveAirESP.RenderBox(box));
                            }
                        }
                    }
                }
            }

            this.renderCaveAir = newCaveAir;
        } else {
            this.renderCaveAir = Collections.emptyList();
        }
    }

    private void computeColors() {
        SettingColor dc = this.disturbanceColor.get();
        SettingColor lc = this.lineColor.get();
        this.fillColor = new Color(dc.r, dc.g, dc.b, dc.a);
        this.outlineColor = new Color(lc.r, lc.g, lc.b, lc.a);
        SettingColor caf = this.caveAirFillColor.get();
        SettingColor cal = this.caveAirLineColor.get();
        this.caveAirFill = new Color(caf.r, caf.g, caf.b, caf.a);
        this.caveAirLine = new Color(cal.r, cal.g, cal.b, cal.a);
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (this.isDimensionEnabled()) {
            ChunkAccess chunk = event.chunk();
            long key = chunk.getPos().toLong();
            if (!this.processingChunks.contains(key)) {
                this.pendingChunks.add(key);
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (this.isDimensionEnabled()) {
            BlockPos pos = event.pos;
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            long key = ChunkPos.asLong(cx, cz);
            this.chunkCache.remove(key);
            if (!this.processingChunks.contains(key)) {
                this.pendingChunks.add(key);
            }
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.player != null && this.isDimensionEnabled()) {
            this.tick++;
            if (this.tick % 20 == 0) {
                this.computeColors();
            }

            if (this.tick % 3 == 0) {
                this.processPendingChunks();
            }

            if (this.tick % 2 == 0) {
                this.rebuildClusterCache();
            }

            if (this.tick % 10 == 0) {
                this.rebuildCaveAirCache();
            }

            if (this.tick % 20 == 0) {
                this.evictDistantChunks();
            }

            if (this.tick >= 1200) {
                this.tick = 0;
            }
        }
    }

    private void processPendingChunks() {
        if (!this.pendingChunks.isEmpty() && this.scanExecutor != null && !this.scanExecutor.isShutdown()) {
            if (this.mc.player != null) {
                int playerChunkX = this.mc.player.chunkPosition().x;
                int playerChunkZ = this.mc.player.chunkPosition().z;
                List<Long> sorted = new ArrayList<>(this.pendingChunks);
                sorted.sort((a, b) -> {
                    int ax = ChunkPos.getX(a) - playerChunkX;
                    int az = ChunkPos.getZ(a) - playerChunkZ;
                    int bx = ChunkPos.getX(b) - playerChunkX;
                    int bz = ChunkPos.getZ(b) - playerChunkZ;
                    return Integer.compare(ax * ax + az * az, bx * bx + bz * bz);
                });
                int processed = 0;

                for (long key : sorted) {
                    if (processed >= 4) {
                        break;
                    }

                    this.pendingChunks.remove(key);
                    if (this.processingChunks.add(key)) {
                        int cx = ChunkPos.getX(key);
                        int cz = ChunkPos.getZ(key);
                        this.scanExecutor.submit(() -> {
                            try {
                                this.scanChunk(cx, cz);
                            } finally {
                                this.processingChunks.remove(key);
                            }
                        });
                        processed++;
                    }
                }
            }
        }
    }

    private void scanChunk(int cx, int cz) {
        if (this.mc.level != null) {
            ChunkAccess chunk;
            try {
                chunk = this.mc.level.getChunk(cx, cz);
            } catch (Exception e) {
                return;
            }

            if (chunk != null) {
                int baseX = cx << 4;
                int baseZ = cz << 4;
                int minYVal = this.minY.get();
                int maxYVal = this.maxY.get();
                boolean collectCaveAir = this.showAllCaveAir.get();
                boolean collectAllAir = this.highlightAllAirInCave.get();
                Set<Long> disturbancePositions = new HashSet<>();
                Set<Long> caveAirPositions = new HashSet<>();
                List<long[]> allAirInCaveList = new ArrayList<>();
                MutableBlockPos pos = new MutableBlockPos();
                MutableBlockPos neighborPos = new MutableBlockPos();

                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int wx = baseX + lx;
                        int wz = baseZ + lz;

                        for (int y = minYVal; y <= maxYVal; y++) {
                            pos.set(wx, y, wz);
                            BlockState state = chunk.getBlockState(pos);
                            Block block = state.getBlock();
                            if (block == Blocks.CAVE_AIR) {
                                if (collectCaveAir) {
                                    caveAirPositions.add(this.packPos(wx, y, wz));
                                }
                            } else if (block == Blocks.AIR) {
                                boolean[] hasCaveAir = new boolean[6];
                                int caveAirCount = 0;

                                for (int i = 0; i < OFFSETS.length; i++) {
                                    int[] offset = OFFSETS[i];
                                    neighborPos.set(wx + offset[0], y + offset[1], wz + offset[2]);
                                    BlockState neighborState = this.getBlockStateSafe(neighborPos);
                                    if (neighborState != null && neighborState.getBlock() == Blocks.CAVE_AIR) {
                                        hasCaveAir[i] = true;
                                        caveAirCount++;
                                    }
                                }

                                if (caveAirCount > 0) {
                                    disturbancePositions.add(this.packPos(wx, y, wz));
                                }

                                if (collectAllAir) {
                                    boolean enclosedY = hasCaveAir[0] && hasCaveAir[1];
                                    boolean enclosedZ = hasCaveAir[2] && hasCaveAir[3];
                                    boolean enclosedX = hasCaveAir[4] && hasCaveAir[5];
                                    if (enclosedY || enclosedZ || enclosedX) {
                                        allAirInCaveList.add(new long[]{wx, y, wz});
                                    }
                                }
                            }
                        }
                    }
                }

                List<CaveAirESP.DisturbanceCluster> clusters = this.clusterDisturbances(disturbancePositions);
                List<CaveAirESP.MergedBox> caveAirBoxes = this.greedyMesh(caveAirPositions);
                if (!clusters.isEmpty() || !caveAirBoxes.isEmpty() || !allAirInCaveList.isEmpty()) {
                    long key = ChunkPos.asLong(cx, cz);
                    this.chunkCache.put(key, new CaveAirESP.ChunkData(cx, cz, clusters, caveAirBoxes, allAirInCaveList));

                    for (CaveAirESP.DisturbanceCluster cluster : clusters) {
                        if (cluster.portalShaped) {
                            String portalKey = cluster.bounds.minX + "," + cluster.bounds.minY + "," + cluster.bounds.minZ;
                            if (this.notifiedPortals.add(portalKey)) {
                                if (this.portalDetectionSound.get() && this.mc.player != null) {
                                    this.mc.execute(() -> {
                                        if (this.mc.player != null) {
                                            this.mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.5F);
                                        }
                                    });
                                }

                                if (this.debug.get()) {
                                    this.info("Portal detected at " + cluster.bounds.minX + ", " + cluster.bounds.minY + ", " + cluster.bounds.minZ);
                                }
                            }
                        }
                    }

                    if (this.debug.get()) {
                        long portalCount = clusters.stream().filter(c -> c.portalShaped).count();
                        this.info(
                            "Chunk "
                                + cx
                                + ","
                                + cz
                                + ": "
                                + portalCount
                                + " portals, "
                                + clusters.size()
                                + " clusters, "
                                + allAirInCaveList.size()
                                + " air blocks"
                        );
                    }
                }
            }
        }
    }

    private long packPos(int x, int y, int z) {
        return (long)(x & 67108863) << 38 | (long)(y & 4095) << 26 | z & 67108863;
    }

    private int unpackX(long packed) {
        return (int)(packed >> 38);
    }

    private int unpackY(long packed) {
        return (int)(packed >> 26 & 4095L) << 20 >> 20;
    }

    private int unpackZ(long packed) {
        return (int)(packed & 67108863L) << 6 >> 6;
    }

    private List<CaveAirESP.DisturbanceCluster> clusterDisturbances(Set<Long> positions) {
        List<CaveAirESP.DisturbanceCluster> clusters = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        int maxW = this.maxPortalWidth.get();
        int maxH = this.maxPortalHeight.get();
        int MIN_W = 4;
        int MIN_H = 5;

        for (long pos : positions) {
            if (!visited.contains(pos)) {
                List<Long> cluster = new ArrayList<>();
                Queue<Long> queue = new LinkedList<>();
                queue.add(pos);
                visited.add(pos);
                int minX = Integer.MAX_VALUE;
                int minY = Integer.MAX_VALUE;
                int minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int maxY = Integer.MIN_VALUE;
                int maxZ = Integer.MIN_VALUE;

                while (!queue.isEmpty()) {
                    long current = queue.poll();
                    cluster.add(current);
                    int cx = this.unpackX(current);
                    int cy = this.unpackY(current);
                    int cz = this.unpackZ(current);
                    minX = Math.min(minX, cx);
                    minY = Math.min(minY, cy);
                    minZ = Math.min(minZ, cz);
                    maxX = Math.max(maxX, cx);
                    maxY = Math.max(maxY, cy);
                    maxZ = Math.max(maxZ, cz);

                    for (int[] offset : OFFSETS) {
                        long neighbor = this.packPos(cx + offset[0], cy + offset[1], cz + offset[2]);
                        if (positions.contains(neighbor) && !visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }

                int blockCount = cluster.size();
                int widthX = maxX - minX + 1;
                int height = maxY - minY + 1;
                int widthZ = maxZ - minZ + 1;
                boolean facesX = widthX == 1;
                boolean facesZ = widthZ == 1;
                if (facesX || facesZ) {
                    int W = facesX ? widthZ : widthX;
                    int H = height;
                    if (W >= 4 && H >= 5 && W <= maxW && H <= maxH) {
                        boolean validDimensions = this.isValidPortalDimensions(W, H, blockCount);
                        if (validDimensions) {
                            boolean validShape = this.isValidPortalShape(cluster, minX, minY, minZ, W, H, blockCount, facesX);
                            if (validShape) {
                                CaveAirESP.MergedBox bounds = new CaveAirESP.MergedBox(minX, minY, minZ, maxX, maxY, maxZ);
                                clusters.add(new CaveAirESP.DisturbanceCluster(bounds, true));
                            }
                        }
                    }
                }
            }
        }

        return clusters;
    }

    private boolean isValidPortalDimensions(int W, int H, int blockCount) {
        int filledRect = W * H;
        int noCorners = filledRect - 4;
        return blockCount == filledRect || blockCount == noCorners;
    }

    private boolean isValidPortalShape(List<Long> cluster, int minX, int minY, int minZ, int W, int H, int blockCount, boolean facesX) {
        int filledRect = W * H;
        int noCorners = filledRect - 4;
        Set<Long> positions = new HashSet<>(cluster);
        if (blockCount == filledRect) {
            return this.validateFilledRectangle(positions, minX, minY, minZ, W, H, facesX);
        } else {
            return blockCount == noCorners ? this.validateFrameWithoutCorners(positions, minX, minY, minZ, W, H, facesX) : false;
        }
    }

    private boolean validateFilledRectangle(Set<Long> positions, int minX, int minY, int minZ, int W, int H, boolean facesX) {
        if (facesX) {
            int x = minX;
            int maxZcoord = minZ + W - 1;
            int maxYcoord = minY + H - 1;

            for (int y = minY; y <= maxYcoord; y++) {
                for (int z = minZ; z <= maxZcoord; z++) {
                    if (!positions.contains(this.packPos(x, y, z))) {
                        return false;
                    }
                }
            }
        } else {
            int z = minZ;
            int maxXcoord = minX + W - 1;
            int maxYcoord = minY + H - 1;

            for (int y = minY; y <= maxYcoord; y++) {
                for (int x = minX; x <= maxXcoord; x++) {
                    if (!positions.contains(this.packPos(x, y, z))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean validateFrameWithoutCorners(Set<Long> positions, int minX, int minY, int minZ, int W, int H, boolean facesX) {
        if (facesX) {
            int x = minX;
            int maxZcoord = minZ + W - 1;
            int maxYcoord = minY + H - 1;

            for (int y = minY; y <= maxYcoord; y++) {
                boolean isTopOrBottom = y == minY || y == maxYcoord;

                for (int z = minZ; z <= maxZcoord; z++) {
                    boolean isLeftOrRight = z == minZ || z == maxZcoord;
                    boolean isCorner = isTopOrBottom && isLeftOrRight;
                    boolean hasBlock = positions.contains(this.packPos(x, y, z));
                    if (isCorner) {
                        if (hasBlock) {
                            return false;
                        }
                    } else if (!hasBlock) {
                        return false;
                    }
                }
            }
        } else {
            int z = minZ;
            int maxXcoord = minX + W - 1;
            int maxYcoord = minY + H - 1;

            for (int y = minY; y <= maxYcoord; y++) {
                boolean isTopOrBottom = y == minY || y == maxYcoord;

                for (int x = minX; x <= maxXcoord; x++) {
                    boolean isLeftOrRight = x == minX || x == maxXcoord;
                    boolean isCorner = isTopOrBottom && isLeftOrRight;
                    boolean hasBlock = positions.contains(this.packPos(x, y, z));
                    if (isCorner) {
                        if (hasBlock) {
                            return false;
                        }
                    } else if (!hasBlock) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private List<CaveAirESP.MergedBox> greedyMesh(Set<Long> positions) {
        if (positions.isEmpty()) {
            return Collections.emptyList();
        }

        List<CaveAirESP.MergedBox> boxes = new ArrayList<>();
        Set<Long> remaining = new HashSet<>(positions);
        List<Long> sorted = new ArrayList<>(positions);
        sorted.sort((a, b) -> {
            int ay = this.unpackY(a);
            int by = this.unpackY(b);
            if (ay != by) {
                return Integer.compare(ay, by);
            }

            int az = this.unpackZ(a);
            int bz = this.unpackZ(b);
            return az != bz ? Integer.compare(az, bz) : Integer.compare(this.unpackX(a), this.unpackX(b));
        });

        for (long start : sorted) {
            if (remaining.contains(start)) {
                remaining.remove(start);
                int startX = this.unpackX(start);
                int startY = this.unpackY(start);
                int startZ = this.unpackZ(start);
                int endX = startX;

                while (remaining.contains(this.packPos(endX + 1, startY, startZ))) {
                    remaining.remove(this.packPos(++endX, startY, startZ));
                }

                int endZ = startZ;
                boolean canExpandZ = true;

                while (canExpandZ) {
                    for (int x = startX; x <= endX; x++) {
                        if (!remaining.contains(this.packPos(x, startY, endZ + 1))) {
                            canExpandZ = false;
                            break;
                        }
                    }

                    if (canExpandZ) {
                        endZ++;

                        for (int x = startX; x <= endX; x++) {
                            remaining.remove(this.packPos(x, startY, endZ));
                        }
                    }
                }

                int endY = startY;
                boolean canExpandY = true;

                while (canExpandY) {
                    for (int x = startX; x <= endX; x++) {
                        for (int z = startZ; z <= endZ; z++) {
                            if (!remaining.contains(this.packPos(x, endY + 1, z))) {
                                canExpandY = false;
                                break;
                            }
                        }

                        if (!canExpandY) {
                            break;
                        }
                    }

                    if (canExpandY) {
                        endY++;

                        for (int x = startX; x <= endX; x++) {
                            for (int z = startZ; z <= endZ; z++) {
                                remaining.remove(this.packPos(x, endY, z));
                            }
                        }
                    }
                }

                boxes.add(new CaveAirESP.MergedBox(startX, startY, startZ, endX, endY, endZ));
            }
        }

        return boxes;
    }

    private BlockState getBlockStateSafe(BlockPos pos) {
        if (this.mc.level == null) {
            return null;
        }

        try {
            return this.mc.level.getBlockState(pos);
        } catch (Exception e) {
            return null;
        }
    }

    private int cacheRadiusChunks() {
        int wanted = this.renderDistance.get();
        if (this.showAllCaveAir.get()) {
            wanted = Math.max(wanted, this.caveAirRenderDist.get());
        }

        int radius = wanted / 16 + 2;
        if (this.mc.options != null) {
            radius = Math.min(radius, this.mc.options.renderDistance().get() + 1);
        }

        return radius;
    }

    private void evictDistantChunks() {
        if (this.mc.player != null) {
            int px = this.mc.player.chunkPosition().x;
            int pz = this.mc.player.chunkPosition().z;
            int dist = this.cacheRadiusChunks();
            int distSq = dist * dist;
            int removed = 0;
            Iterator<Entry<Long, CaveAirESP.ChunkData>> it = this.chunkCache.entrySet().iterator();

            while (it.hasNext()) {
                CaveAirESP.ChunkData data = it.next().getValue();
                int dx = data.chunkX - px;
                int dz = data.chunkZ - pz;
                if (dx * dx + dz * dz > distSq) {
                    it.remove();
                    removed++;
                }
            }

            if (this.debug.get() && removed > 0) {
                this.info("Evicted " + removed + " distant chunks");
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.player != null && this.isDimensionEnabled()) {
            List<CaveAirESP.RenderCluster> clusters = this.renderClusters;
            List<CaveAirESP.RenderBox> caveAir = this.renderCaveAir;
            List<long[]> airInCave = this.renderAirInCave;
            ShapeMode shape = this.shapeMode.get();
            boolean beacon = this.renderBeacon.get();
            int bHeight = this.beaconHeight.get();
            int i = 0;

            for (int n = clusters.size(); i < n; i++) {
                CaveAirESP.RenderCluster c = clusters.get(i);
                event.renderer.box(c.minX, c.minY, c.minZ, c.maxX, c.maxY, c.maxZ, this.fillColor, this.outlineColor, shape, 0);
                if (c.portalShaped && beacon) {
                    event.renderer.line(c.centerX, c.maxY, c.centerZ, c.centerX, c.maxY + bHeight, c.centerZ, this.outlineColor);
                }
            }

            if (this.highlightAllAirInCave.get()) {
                i = 0;

                for (int n = airInCave.size(); i < n; i++) {
                    long[] pos = airInCave.get(i);
                    int x = (int)pos[0];
                    int y = (int)pos[1];
                    int z = (int)pos[2];
                    event.renderer.box(x, y, z, x + 1, y + 1, z + 1, this.fillColor, this.outlineColor, shape, 0);
                }
            }

            if (this.showAllCaveAir.get()) {
                ShapeMode caShape = this.caveAirShapeMode.get();
                int ix = 0;

                for (int n = caveAir.size(); ix < n; ix++) {
                    CaveAirESP.RenderBox b = caveAir.get(ix);
                    event.renderer.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, this.caveAirFill, this.caveAirLine, caShape, 0);
                }
            }
        }
    }

    private double nearestPointDistanceSq(Vec3 point, CaveAirESP.MergedBox box) {
        double dx = Math.max(0.0, Math.max(box.minX - point.x, point.x - (box.maxX + 1)));
        double dy = Math.max(0.0, Math.max(box.minY - point.y, point.y - (box.maxY + 1)));
        double dz = Math.max(0.0, Math.max(box.minZ - point.z, point.z - (box.maxZ + 1)));
        return dx * dx + dy * dy + dz * dz;
    }

    private static class ChunkData {
        final int chunkX;
        final int chunkZ;
        final List<CaveAirESP.DisturbanceCluster> clusters;
        final List<CaveAirESP.MergedBox> caveAirBoxes;
        final List<long[]> allAirInCave;

        ChunkData(int cx, int cz, List<CaveAirESP.DisturbanceCluster> clusters, List<CaveAirESP.MergedBox> caveAirBoxes, List<long[]> allAirInCave) {
            this.chunkX = cx;
            this.chunkZ = cz;
            this.clusters = clusters;
            this.caveAirBoxes = caveAirBoxes;
            this.allAirInCave = allAirInCave;
        }
    }

    private static class DisturbanceCluster {
        final CaveAirESP.MergedBox bounds;
        final boolean portalShaped;

        DisturbanceCluster(CaveAirESP.MergedBox bounds, boolean portalShaped) {
            this.bounds = bounds;
            this.portalShaped = portalShaped;
        }
    }

    private static class MergedBox {
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;

        MergedBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }

    private static class RenderBox {
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;

        RenderBox(CaveAirESP.MergedBox b) {
            this.minX = b.minX;
            this.minY = b.minY;
            this.minZ = b.minZ;
            this.maxX = b.maxX + 1;
            this.maxY = b.maxY + 1;
            this.maxZ = b.maxZ + 1;
        }
    }

    private static class RenderCluster {
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;
        final double centerX;
        final double centerZ;
        final boolean portalShaped;

        RenderCluster(CaveAirESP.MergedBox b, boolean portalShaped) {
            this.minX = b.minX;
            this.minY = b.minY;
            this.minZ = b.minZ;
            this.maxX = b.maxX + 1;
            this.maxY = b.maxY + 1;
            this.maxZ = b.maxZ + 1;
            this.centerX = (b.minX + b.maxX + 1) / 2.0;
            this.centerZ = (b.minZ + b.maxZ + 1) / 2.0;
            this.portalShaped = portalShaped;
        }
    }
}
