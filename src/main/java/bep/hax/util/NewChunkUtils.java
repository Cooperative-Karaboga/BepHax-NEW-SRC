package bep.hax.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.lang.reflect.Method;
import java.time.Duration;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.PaletteNewChunks;
import xaeroplus.util.ChunkScanner;
import xaeroplus.util.ChunkUtils;

public final class NewChunkUtils {
    private static final ReferenceSet<Block> MODERN_OVERWORLD_BLOCKS = ReferenceOpenHashSet.of(
        Blocks.COPPER_ORE,
        Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.AMETHYST_BLOCK,
        Blocks.SMOOTH_BASALT,
        Blocks.TUFF,
        Blocks.KELP,
        Blocks.KELP_PLANT,
        Blocks.POINTED_DRIPSTONE,
        Blocks.DRIPSTONE_BLOCK,
        Blocks.DEEPSLATE,
        Blocks.AZALEA,
        Blocks.BIG_DRIPLEAF,
        Blocks.BIG_DRIPLEAF_STEM,
        Blocks.SMALL_DRIPLEAF,
        Blocks.MOSS_BLOCK,
        Blocks.CAVE_VINES,
        Blocks.CAVE_VINES_PLANT
    );
    private static final ReferenceSet<Block> MODERN_NETHER_BLOCKS = ReferenceOpenHashSet.of(
        Blocks.ANCIENT_DEBRIS,
        Blocks.BLACKSTONE,
        Blocks.BASALT,
        Blocks.CRIMSON_NYLIUM,
        Blocks.WARPED_NYLIUM,
        Blocks.NETHER_GOLD_ORE,
        Blocks.IRON_CHAIN
    );
    private static final Method BIOME_SCAN = resolveScan("scanNewChunkBiomePalette", "checkNewChunkBiomePalette", LevelChunk.class, boolean.class);
    private static final Method BLOCKSTATE_SCAN = resolveScan("scanNewChunkBlockStatePalette", "checkNewChunkBlockStatePalette", LevelChunk.class);
    private static final Cache<Long, Boolean> VERDICTS = Caffeine.newBuilder().maximumSize(20000L).expireAfterAccess(Duration.ofMinutes(10L)).build();
    private static ResourceKey<Level> cachedDimension;

    private NewChunkUtils() {
    }

    public static boolean isFreshlyGenerated(LevelChunk chunk) {
        ResourceKey<Level> dimension = chunk.getLevel().dimension();
        dropStaleVerdicts(dimension);
        return VERDICTS.get(ChunkUtils.chunkPosToLong(chunk.getPos()), key -> scan(dimension, chunk));
    }

    public static boolean isKnownFreshlyGenerated(ChunkPos pos, ResourceKey<Level> dimension) {
        dropStaleVerdicts(dimension);
        return Boolean.TRUE.equals(VERDICTS.getIfPresent(ChunkUtils.chunkPosToLong(pos)));
    }

    public static void reset() {
        VERDICTS.invalidateAll();
        cachedDimension = null;
    }

    private static void dropStaleVerdicts(ResourceKey<Level> dimension) {
        if (!dimension.equals(cachedDimension)) {
            cachedDimension = dimension;
            VERDICTS.invalidateAll();
        }
    }

    private static boolean scan(ResourceKey<Level> dimension, LevelChunk chunk) {
        PaletteNewChunks paletteNewChunks = ModuleManager.getModule(PaletteNewChunks.class);
        if (paletteNewChunks == null) {
            return false;
        }

        if (BIOME_SCAN != null && BLOCKSTATE_SCAN != null) {
            try {
                if (paletteNewChunks.isEnabled()
                    && paletteNewChunks.isNewChunk(chunk.getPos().x, chunk.getPos().z, dimension)
                    && hasModernTerrain(chunk, dimension)) {
                    return true;
                }

                if (dimension != Level.OVERWORLD) {
                    return "PLAINS_IN_PALETTE".equals(biomePalette(paletteNewChunks, chunk, false));
                }

                return switch (biomePalette(paletteNewChunks, chunk, true)) {
                    case "NO_PLAINS" -> false;
                    case "PLAINS_IN_PALETTE" -> true;
                    case "PLAINS_PRESENT" -> blockStatePalette(paletteNewChunks, chunk) && hasModernTerrain(chunk, dimension);
                    default -> false;
                };
            } catch (Throwable e) {
                return false;
            }
        } else {
            return false;
        }
    }

    private static String biomePalette(PaletteNewChunks module, LevelChunk chunk, boolean checkData) throws Throwable {
        Object result;
        synchronized (module) {
            result = BIOME_SCAN.invoke(module, chunk, checkData);
        }

        return result instanceof Enum<?> value ? value.name() : "";
    }

    private static boolean blockStatePalette(PaletteNewChunks module, LevelChunk chunk) throws Throwable {
        Object result;
        synchronized (module) {
            result = BLOCKSTATE_SCAN.invoke(module, chunk);
        }

        return Boolean.TRUE.equals(result);
    }

    private static Method resolveScan(String currentName, String legacyName, Class<?>... parameters) {
        for (String name : new String[]{currentName, legacyName}) {
            try {
                Method method = PaletteNewChunks.class.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (Throwable var8) {
            }
        }

        return null;
    }

    private static boolean hasModernTerrain(LevelChunk chunk, ResourceKey<Level> dimension) {
        return dimension != Level.OVERWORLD && dimension != Level.NETHER
            ? true
            : ChunkScanner.chunkContainsBlocks(chunk, dimension == Level.OVERWORLD ? MODERN_OVERWORLD_BLOCKS : MODERN_NETHER_BLOCKS, 5);
    }
}
