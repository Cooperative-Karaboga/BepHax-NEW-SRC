package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.MapUtil;
import bep.hax.util.MsgUtil;
import bep.hax.util.Utils;
import bep.hax.util.XaeroWaypointManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class VanityESP extends Module {
    private final SettingGroup sgFeatures = this.settings.getDefaultGroup();
    private final SettingGroup sgMapFrames = this.settings.createGroup("Map Frames");
    private final SettingGroup sgBanners = this.settings.createGroup("Banners");
    private final SettingGroup sgShulkerFrames = this.settings.createGroup("Shulker Frames");
    private final SettingGroup sgOminousVaults = this.settings.createGroup("Ominous Vaults");
    private final SettingGroup sgTreasure = this.settings.createGroup("Buried Treasure");
    private final SettingGroup sgEnderman = this.settings.createGroup("Enderman");
    private final SettingGroup sgXPOrbs = this.settings.createGroup("XP Orbs");
    private final SettingGroup sgStackedEntities = this.settings.createGroup("Stacked Entities");
    private final Setting<Boolean> highlightMapFrames = this.sgFeatures
        .add(new Builder().name("map-frames").description("Highlights item frames containing maps.").defaultValue(true).build());
    private final Setting<Boolean> highlightBanners = this.sgFeatures
        .add(new Builder().name("banners").description("Highlights banners.").defaultValue(true).build());
    private final Setting<Boolean> highlightShulkerFrames = this.sgFeatures
        .add(new Builder().name("shulker-frames").description("Highlights item frames containing shulker boxes.").defaultValue(true).build());
    private final Setting<Boolean> highlightOminousVaults = this.sgFeatures
        .add(new Builder().name("ominous-vaults").description("Highlights ominous vaults.").defaultValue(true).build());
    private final Setting<Boolean> highlightTreasure = this.sgFeatures
        .add(new Builder().name("buried-treasure").description("Highlights buried treasure chests.").defaultValue(true).build());
    private final Setting<Boolean> highlightEndermanHolding = this.sgFeatures
        .add(new Builder().name("enderman-holding-blocks").description("Highlights endermen that are holding blocks.").defaultValue(true).build());
    private final Setting<Boolean> highlightXPOrbs = this.sgFeatures
        .add(new Builder().name("xp-orbs").description("Highlights experience orbs.").defaultValue(true).build());
    private final Setting<Boolean> highlightStackedEntities = this.sgFeatures
        .add(
            new Builder()
                .name("stacked-entities")
                .description("Highlights and notifies about stacked minecarts with chests/hoppers and other entities that can hold items.")
                .defaultValue(true)
                .build()
        );
    private final Setting<SettingColor> mapFillColor = this.sgMapFrames
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("side-color")
                .description("Fill color for map frames.")
                .defaultValue(new SettingColor(255, 255, 0, 50))
                .visible(this.highlightMapFrames::get)
                .build()
        );
    private final Setting<SettingColor> mapOutlineColor = this.sgMapFrames
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Outline color for map frames.")
                .defaultValue(new SettingColor(255, 255, 0, 255))
                .visible(this.highlightMapFrames::get)
                .build()
        );
    private final Setting<Boolean> mapRenderFill = this.sgMapFrames
        .add(new Builder().name("render-sides").description("Render sides of map frames.").defaultValue(false).visible(this.highlightMapFrames::get).build());
    private final Setting<Boolean> mapRenderOutline = this.sgMapFrames
        .add(new Builder().name("render-lines").description("Render lines of map frames.").defaultValue(true).visible(this.highlightMapFrames::get).build());
    private final Setting<Boolean> mapRenderTracer = this.sgMapFrames
        .add(new Builder().name("tracers").description("Add tracers to map frames.").defaultValue(false).visible(this.highlightMapFrames::get).build());
    private final Setting<SettingColor> mapTracerColor = this.sgMapFrames
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("tracer-color")
                .description("Tracer color for map frames.")
                .defaultValue(new SettingColor(255, 255, 0, 125))
                .visible(this.highlightMapFrames::get)
                .build()
        );
    private final Setting<SettingColor> bannerFillColor = this.sgBanners
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("side-color")
                .description("Fill color for banners.")
                .defaultValue(new SettingColor(255, 0, 0, 50))
                .visible(this.highlightBanners::get)
                .build()
        );
    private final Setting<SettingColor> bannerOutlineColor = this.sgBanners
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Outline color for banners.")
                .defaultValue(new SettingColor(255, 0, 0, 255))
                .visible(this.highlightBanners::get)
                .build()
        );
    private final Setting<Boolean> bannerRenderFill = this.sgBanners
        .add(new Builder().name("render-sides").description("Render sides of banners.").defaultValue(true).visible(this.highlightBanners::get).build());
    private final Setting<Boolean> bannerRenderOutline = this.sgBanners
        .add(new Builder().name("render-lines").description("Render lines of banners.").defaultValue(true).visible(this.highlightBanners::get).build());
    private final Setting<Boolean> bannerRenderTracer = this.sgBanners
        .add(new Builder().name("tracers").description("Add tracers to banners.").defaultValue(false).visible(this.highlightBanners::get).build());
    private final Setting<SettingColor> bannerTracerColor = this.sgBanners
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("tracer-color")
                .description("Tracer color for banners.")
                .defaultValue(new SettingColor(255, 0, 0, 125))
                .visible(this.highlightBanners::get)
                .build()
        );
    private final Setting<SettingColor> shulkerFillColor = this.sgShulkerFrames
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("side-color")
                .description("Fill color for shulker frames.")
                .defaultValue(new SettingColor(152, 98, 43, 50))
                .visible(this.highlightShulkerFrames::get)
                .build()
        );
    private final Setting<SettingColor> shulkerOutlineColor = this.sgShulkerFrames
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Outline color for shulker frames.")
                .defaultValue(new SettingColor(85, 43, 19, 255))
                .visible(this.highlightShulkerFrames::get)
                .build()
        );
    private final Setting<SettingColor> shulkerTracerColor = this.sgShulkerFrames
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("tracer-color")
                .description("Tracer color for shulker frames.")
                .defaultValue(new SettingColor(166, 150, 101, 255))
                .visible(this.highlightShulkerFrames::get)
                .build()
        );
    private final Setting<Boolean> shulkerRenderFill = this.sgShulkerFrames
        .add(
            new Builder()
                .name("render-sides")
                .description("Render sides of shulker frames.")
                .defaultValue(true)
                .visible(this.highlightShulkerFrames::get)
                .build()
        );
    private final Setting<Boolean> shulkerRenderOutline = this.sgShulkerFrames
        .add(
            new Builder()
                .name("render-lines")
                .description("Render lines of shulker frames.")
                .defaultValue(true)
                .visible(this.highlightShulkerFrames::get)
                .build()
        );
    private final Setting<Boolean> shulkerRenderTracer = this.sgShulkerFrames
        .add(new Builder().name("tracers").description("Add tracers to shulker frames.").defaultValue(false).visible(this.highlightShulkerFrames::get).build());
    private final Setting<SettingColor> vaultFillColor = this.sgOminousVaults
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("side-color")
                .description("Fill color for ominous vaults.")
                .defaultValue(new SettingColor(0, 120, 120, 50))
                .visible(this.highlightOminousVaults::get)
                .build()
        );
    private final Setting<SettingColor> vaultOutlineColor = this.sgOminousVaults
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Outline color for ominous vaults.")
                .defaultValue(new SettingColor(31, 161, 159, 255))
                .visible(this.highlightOminousVaults::get)
                .build()
        );
    private final Setting<SettingColor> vaultTracerColor = this.sgOminousVaults
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("tracer-color")
                .description("Tracer color for ominous vaults.")
                .defaultValue(new SettingColor(40, 200, 195, 255))
                .visible(this.highlightOminousVaults::get)
                .build()
        );
    private final Setting<Boolean> vaultRenderFill = this.sgOminousVaults
        .add(
            new Builder()
                .name("render-sides")
                .description("Render sides of ominous vaults.")
                .defaultValue(true)
                .visible(this.highlightOminousVaults::get)
                .build()
        );
    private final Setting<Boolean> vaultRenderOutline = this.sgOminousVaults
        .add(
            new Builder()
                .name("render-lines")
                .description("Render lines of ominous vaults.")
                .defaultValue(true)
                .visible(this.highlightOminousVaults::get)
                .build()
        );
    private final Setting<Boolean> vaultRenderTracer = this.sgOminousVaults
        .add(new Builder().name("tracers").description("Add tracers to ominous vaults.").defaultValue(false).visible(this.highlightOminousVaults::get).build());
    private final Setting<Boolean> treasureChat = this.sgTreasure
        .add(new Builder().name("chat-notification").description("Notify with a chat message.").defaultValue(true).visible(this.highlightTreasure::get).build());
    private final Setting<Boolean> treasureCoords = this.sgTreasure
        .add(
            new Builder()
                .name("show-coords")
                .description("Display chest coordinates in chat notifications.")
                .defaultValue(false)
                .visible(() -> this.highlightTreasure.get() && this.treasureChat.get())
                .build()
        );
    private final Setting<Boolean> treasureWaypoints = this.sgTreasure
        .add(
            new Builder()
                .name("add-waypoints")
                .description("Adds waypoints to your Xaeros map for treasure chests.")
                .defaultValue(false)
                .visible(() -> this.highlightTreasure.get() && Utils.XAERO_AVAILABLE)
                .build()
        );
    private final Setting<Boolean> treasureTempWaypoints = this.sgTreasure
        .add(
            new Builder()
                .name("temporary-waypoints")
                .description("Temporary waypoints are removed when you disconnect.")
                .defaultValue(true)
                .visible(() -> this.highlightTreasure.get() && Utils.XAERO_AVAILABLE && this.treasureWaypoints.get())
                .build()
        );
    private final Setting<Boolean> treasureSound = this.sgTreasure
        .add(new Builder().name("sound-notification").description("Notify with sound.").defaultValue(true).visible(this.highlightTreasure::get).build());
    private final Setting<Double> treasureVolume = this.sgTreasure
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("volume")
                .min(0.0)
                .max(10.0)
                .sliderMin(0.0)
                .sliderMax(5.0)
                .defaultValue(1.0)
                .visible(() -> this.highlightTreasure.get() && this.treasureSound.get())
                .build()
        );
    private final Setting<SettingColor> treasureFillColor = this.sgTreasure
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("side-color")
                .description("Fill color for treasure chests.")
                .defaultValue(new SettingColor(147, 233, 190, 25))
                .visible(this.highlightTreasure::get)
                .build()
        );
    private final Setting<SettingColor> treasureOutlineColor = this.sgTreasure
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Outline color for treasure chests.")
                .defaultValue(new SettingColor(147, 233, 190, 255))
                .visible(this.highlightTreasure::get)
                .build()
        );
    private final Setting<SettingColor> treasureTracerColor = this.sgTreasure
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("tracer-color")
                .description("Tracer color for treasure chests.")
                .defaultValue(new SettingColor(147, 233, 190, 125))
                .visible(this.highlightTreasure::get)
                .build()
        );
    private final Setting<Boolean> treasureRenderFill = this.sgTreasure
        .add(new Builder().name("render-sides").description("Render sides of treasure chests.").defaultValue(true).visible(this.highlightTreasure::get).build());
    private final Setting<Boolean> treasureRenderOutline = this.sgTreasure
        .add(new Builder().name("render-lines").description("Render lines of treasure chests.").defaultValue(true).visible(this.highlightTreasure::get).build());
    private final Setting<Boolean> treasureRenderTracer = this.sgTreasure
        .add(new Builder().name("tracers").description("Add tracers to treasure chests.").defaultValue(true).visible(this.highlightTreasure::get).build());
    private final Setting<SettingColor> endermanFillColor = this.sgEnderman
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("side-color")
                .description("Fill color for endermen holding blocks.")
                .defaultValue(new SettingColor(128, 0, 128, 50))
                .visible(this.highlightEndermanHolding::get)
                .build()
        );
    private final Setting<SettingColor> endermanOutlineColor = this.sgEnderman
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Outline color for endermen holding blocks.")
                .defaultValue(new SettingColor(200, 0, 200, 255))
                .visible(this.highlightEndermanHolding::get)
                .build()
        );
    private final Setting<Boolean> endermanRenderFill = this.sgEnderman
        .add(
            new Builder()
                .name("render-sides")
                .description("Render sides of endermen holding blocks.")
                .defaultValue(true)
                .visible(this.highlightEndermanHolding::get)
                .build()
        );
    private final Setting<Boolean> endermanRenderOutline = this.sgEnderman
        .add(
            new Builder()
                .name("render-lines")
                .description("Render lines of endermen holding blocks.")
                .defaultValue(true)
                .visible(this.highlightEndermanHolding::get)
                .build()
        );
    private final Setting<Boolean> endermanRenderTracer = this.sgEnderman
        .add(
            new Builder()
                .name("tracers")
                .description("Add tracers to endermen holding blocks.")
                .defaultValue(false)
                .visible(this.highlightEndermanHolding::get)
                .build()
        );
    private final Setting<SettingColor> endermanTracerColor = this.sgEnderman
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("tracer-color")
                .description("Tracer color for endermen holding blocks.")
                .defaultValue(new SettingColor(200, 0, 200, 125))
                .visible(this.highlightEndermanHolding::get)
                .build()
        );
    private final Setting<SettingColor> xpOrbFillColor = this.sgXPOrbs
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("side-color")
                .description("Fill color for XP orbs.")
                .defaultValue(new SettingColor(0, 255, 0, 75))
                .visible(this.highlightXPOrbs::get)
                .build()
        );
    private final Setting<SettingColor> xpOrbOutlineColor = this.sgXPOrbs
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Outline color for XP orbs.")
                .defaultValue(new SettingColor(0, 255, 0, 255))
                .visible(this.highlightXPOrbs::get)
                .build()
        );
    private final Setting<Boolean> xpOrbRenderFill = this.sgXPOrbs
        .add(new Builder().name("render-sides").description("Render sides of XP orbs.").defaultValue(true).visible(this.highlightXPOrbs::get).build());
    private final Setting<Boolean> xpOrbRenderOutline = this.sgXPOrbs
        .add(new Builder().name("render-lines").description("Render lines of XP orbs.").defaultValue(true).visible(this.highlightXPOrbs::get).build());
    private final Setting<Boolean> xpOrbRenderTracer = this.sgXPOrbs
        .add(new Builder().name("tracers").description("Add tracers to XP orbs.").defaultValue(false).visible(this.highlightXPOrbs::get).build());
    private final Setting<SettingColor> xpOrbTracerColor = this.sgXPOrbs
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("tracer-color")
                .description("Tracer color for XP orbs.")
                .defaultValue(new SettingColor(0, 255, 0, 125))
                .visible(this.highlightXPOrbs::get)
                .build()
        );
    private final Setting<Boolean> stackedEntitiesChat = this.sgStackedEntities
        .add(
            new Builder()
                .name("chat-notification")
                .description("Notify with a chat message when stacked entities are detected.")
                .defaultValue(true)
                .visible(this.highlightStackedEntities::get)
                .build()
        );
    private final Setting<Boolean> stackedEntitiesCoords = this.sgStackedEntities
        .add(
            new Builder()
                .name("show-coords")
                .description("Display coordinates in chat notifications.")
                .defaultValue(true)
                .visible(() -> this.highlightStackedEntities.get() && this.stackedEntitiesChat.get())
                .build()
        );
    private final Setting<Boolean> stackedEntitiesSound = this.sgStackedEntities
        .add(
            new Builder()
                .name("sound-notification")
                .description("Notify with sound when stacked entities are detected.")
                .defaultValue(true)
                .visible(this.highlightStackedEntities::get)
                .build()
        );
    private final Setting<Double> stackedEntitiesVolume = this.sgStackedEntities
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("volume")
                .min(0.0)
                .max(10.0)
                .sliderMin(0.0)
                .sliderMax(5.0)
                .defaultValue(1.0)
                .visible(() -> this.highlightStackedEntities.get() && this.stackedEntitiesSound.get())
                .build()
        );
    private final Setting<Integer> stackedEntitiesMinCount = this.sgStackedEntities
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("min-stacked-count")
                .description("Minimum number of entities stacked together to trigger detection.")
                .min(2)
                .defaultValue(2)
                .sliderMin(2)
                .sliderMax(10)
                .visible(this.highlightStackedEntities::get)
                .build()
        );
    private final Setting<Boolean> stackedEntitiesWaypoints = this.sgStackedEntities
        .add(
            new Builder()
                .name("add-waypoints")
                .description("Adds waypoints to your Xaeros map for stacked entities.")
                .defaultValue(true)
                .visible(() -> this.highlightStackedEntities.get() && Utils.XAERO_AVAILABLE)
                .build()
        );
    private final Setting<Boolean> stackedEntitiesTempWaypoints = this.sgStackedEntities
        .add(
            new Builder()
                .name("temporary-waypoints")
                .description("Temporary waypoints are removed when you disconnect.")
                .defaultValue(false)
                .visible(() -> this.highlightStackedEntities.get() && Utils.XAERO_AVAILABLE && this.stackedEntitiesWaypoints.get())
                .build()
        );
    private final Setting<SettingColor> stackedEntitiesFillColor = this.sgStackedEntities
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("side-color")
                .description("Fill color for stacked entities.")
                .defaultValue(new SettingColor(255, 165, 0, 75))
                .visible(this.highlightStackedEntities::get)
                .build()
        );
    private final Setting<SettingColor> stackedEntitiesOutlineColor = this.sgStackedEntities
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Outline color for stacked entities.")
                .defaultValue(new SettingColor(255, 140, 0, 255))
                .visible(this.highlightStackedEntities::get)
                .build()
        );
    private final Setting<Boolean> stackedEntitiesRenderFill = this.sgStackedEntities
        .add(
            new Builder()
                .name("render-sides")
                .description("Render sides of stacked entities.")
                .defaultValue(true)
                .visible(this.highlightStackedEntities::get)
                .build()
        );
    private final Setting<Boolean> stackedEntitiesRenderOutline = this.sgStackedEntities
        .add(
            new Builder()
                .name("render-lines")
                .description("Render lines of stacked entities.")
                .defaultValue(true)
                .visible(this.highlightStackedEntities::get)
                .build()
        );
    private final Setting<Boolean> stackedEntitiesRenderTracer = this.sgStackedEntities
        .add(
            new Builder()
                .name("tracers")
                .description("Add tracers to stacked entities.")
                .defaultValue(true)
                .visible(this.highlightStackedEntities::get)
                .build()
        );
    private final Setting<SettingColor> stackedEntitiesTracerColor = this.sgStackedEntities
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("tracer-color")
                .description("Tracer color for stacked entities.")
                .defaultValue(new SettingColor(255, 165, 0, 200))
                .visible(this.highlightStackedEntities::get)
                .build()
        );
    private final Set<BlockPos> ominousVaults = Collections.synchronizedSet(new HashSet<>());
    private final Map<ChunkPos, Set<BlockPos>> chunkVaults = new HashMap<>();
    private Set<ChunkPos> lastLoadedChunks = new HashSet<>();
    private long lastRecheckTime = 0L;
    private final int recheckIntervalMs = 4000;
    private final Map<ChunkPos, Integer> pendingChunks = new HashMap<>();
    private final Deque<ChunkPos> initialScanQueue = new ArrayDeque<>();
    private boolean initialScanActive = false;
    private int initialScanChunksPerTick = 0;
    private long lastFullRescan = 0L;
    private final int fullRescanIntervalMs = 3000;
    private final Set<BlockPos> lootedTreasure = new HashSet<>();
    private final List<BlockPos> notifiedTreasure = new ArrayList<>();
    private final Set<BlockPos> notifiedStackedEntities = new HashSet<>();
    private final List<List<Entity>> cachedStackedGroups = new ArrayList<>();
    private long lastStackedScanMs = 0L;
    private final List<AABB> bannerBoxes = new ArrayList<>();
    private int bannerScanTicks = 0;
    private int lastBannerChunkX = Integer.MIN_VALUE;
    private int lastBannerChunkZ = Integer.MIN_VALUE;
    private static final int MAX_VAULTS = 1000;
    private static final int MAX_TREASURE = 500;
    private static final int MAX_NOTIFIED_TREASURE = 100;
    private static final int BANNER_RESCAN_TICKS = 10;

    public VanityESP() {
        super(Bep.HUNT_CATEGORY, "VanityESP", "Unified ESP for decorative items and special blocks.");
        ClientPlayConnectionEvents.JOIN
            .register(
                (handler, sender, client) -> {
                    if (this.mc.level != null && this.mc.player != null) {
                        this.initialScanQueue.clear();
                        int chunkRadius = this.mc.options.renderDistance().get();
                        int totalChunks = (2 * chunkRadius + 1) * (2 * chunkRadius + 1);
                        int durationTicks = 70;
                        this.initialScanChunksPerTick = Math.max(1, (int)Math.ceil((double)totalChunks / durationTicks));

                        for (int cx = this.mc.player.chunkPosition().x - chunkRadius;
                            cx <= this.mc.player.chunkPosition().x + chunkRadius;
                            cx++
                        ) {
                            for (int cz = this.mc.player.chunkPosition().z - chunkRadius;
                                cz <= this.mc.player.chunkPosition().z + chunkRadius;
                                cz++
                            ) {
                                this.initialScanQueue.addLast(new ChunkPos(cx, cz));
                            }
                        }

                        this.initialScanActive = true;
                    }
                }
            );
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.highlightTreasure.get()) {
                BlockPos pos = this.mc.player.blockPosition();
                int viewDistance = this.mc.options.renderDistance().get();
                int startChunkX = pos.getX() - viewDistance * 16 >> 4;
                int endChunkX = pos.getX() + viewDistance * 16 >> 4;
                int startChunkZ = pos.getZ() - viewDistance * 16 >> 4;
                int endChunkZ = pos.getZ() + viewDistance * 16 >> 4;

                for (int x = startChunkX; x < endChunkX; x++) {
                    for (int z = startChunkZ; z < endChunkZ; z++) {
                        if (this.mc.level.hasChunk(x, z)) {
                            LevelChunk chunk = this.mc.level.getChunk(x, z);
                            this.scanChunkForTreasure(chunk);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        this.notifiedTreasure.clear();
        this.lootedTreasure.clear();
        this.ominousVaults.clear();
        this.chunkVaults.clear();
        this.pendingChunks.clear();
        this.initialScanQueue.clear();
        this.lastLoadedChunks.clear();
        this.notifiedStackedEntities.clear();
        this.cachedStackedGroups.clear();
        this.bannerBoxes.clear();
        this.lastBannerChunkX = Integer.MIN_VALUE;
        this.lastBannerChunkZ = Integer.MIN_VALUE;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.level != null && this.mc.player != null) {
            this.updateBannerCache();
            if (this.highlightOminousVaults.get()) {
                if (this.initialScanActive) {
                    int processed = 0;
                    int minY0 = this.mc.level.getMinY();
                    int maxY0 = this.mc.level.getHeight();

                    while (processed < this.initialScanChunksPerTick && !this.initialScanQueue.isEmpty()) {
                        ChunkPos cp = this.initialScanQueue.pollFirst();
                        LevelChunk chunk = this.mc.level.getChunk(cp.x, cp.z);
                        if (chunk instanceof LevelChunk) {
                            this.scanChunkForVaults(cp, minY0, maxY0);
                        }

                        processed++;
                    }

                    if (this.initialScanQueue.isEmpty()) {
                        this.initialScanActive = false;
                    }
                }

                Set<ChunkPos> currentChunks = new HashSet<>();
                BlockPos playerPos = this.mc.player.blockPosition();
                int chunkRadius = this.mc.options.renderDistance().get();
                int minY = this.mc.level.getMinY();
                int maxY = this.mc.level.getHeight();

                for (int cx = (playerPos.getX() >> 4) - chunkRadius; cx <= (playerPos.getX() >> 4) + chunkRadius; cx++) {
                    for (int cz = (playerPos.getZ() >> 4) - chunkRadius; cz <= (playerPos.getZ() >> 4) + chunkRadius; cz++) {
                        currentChunks.add(new ChunkPos(cx, cz));
                    }
                }

                Set<ChunkPos> newChunks = new HashSet<>(currentChunks);
                newChunks.removeAll(this.lastLoadedChunks);
                Set<ChunkPos> unloadedChunks = new HashSet<>(this.lastLoadedChunks);
                unloadedChunks.removeAll(currentChunks);

                for (ChunkPos chunkPos : unloadedChunks) {
                    Set<BlockPos> removed = this.chunkVaults.remove(chunkPos);
                    if (removed != null) {
                        this.ominousVaults.removeAll(removed);
                    }

                    this.pendingChunks.remove(chunkPos);
                }

                for (ChunkPos chunkPos : newChunks) {
                    this.pendingChunks.put(chunkPos, 10);
                }

                Set<ChunkPos> toScan = new HashSet<>();

                for (Entry<ChunkPos, Integer> entry : new HashMap<>(this.pendingChunks).entrySet()) {
                    int ticksLeft = entry.getValue() - 1;
                    if (ticksLeft <= 0) {
                        toScan.add(entry.getKey());
                    } else {
                        this.pendingChunks.put(entry.getKey(), ticksLeft);
                    }
                }

                for (ChunkPos chunkPos : toScan) {
                    this.scanChunkForVaults(chunkPos, minY, maxY);
                    this.pendingChunks.remove(chunkPos);
                }

                long now = System.currentTimeMillis();
                if (now - this.lastRecheckTime >= 4000L) {
                    this.lastRecheckTime = now;
                    Set<BlockPos> toRemoveVaults = new HashSet<>();

                    for (BlockPos vaultPos : this.ominousVaults) {
                        BlockState state = this.mc.level.getBlockState(vaultPos);
                        Property<?> ominousProperty = null;

                        for (Property<?> prop : state.getProperties()) {
                            if (prop.getName().equals("ominous")) {
                                ominousProperty = prop;
                                break;
                            }
                        }

                        if (ominousProperty == null || !Boolean.TRUE.equals(state.getValue(ominousProperty))) {
                            toRemoveVaults.add(vaultPos);
                        }
                    }

                    this.ominousVaults.removeAll(toRemoveVaults);

                    for (Set<BlockPos> set : this.chunkVaults.values()) {
                        set.removeAll(toRemoveVaults);
                    }
                }

                if (now - this.lastFullRescan >= 3000L) {
                    this.lastFullRescan = now;

                    for (ChunkPos chunkPos : this.lastLoadedChunks) {
                        this.scanChunkForVaults(chunkPos, minY, maxY);
                    }
                }

                this.lastLoadedChunks = currentChunks;
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (this.highlightTreasure.get()) {
            if (this.mc.level != null && this.mc.player != null) {
                this.scanChunkForTreasure(event.chunk());
            }
        }
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (this.highlightTreasure.get()) {
            if (this.mc.player != null && this.mc.level != null) {
                if (this.notifiedTreasure.contains(event.result.getBlockPos())
                    && event.result.getType() == Type.BLOCK
                    && this.mc.level.getBlockState(event.result.getBlockPos()).getBlock() instanceof ChestBlock) {
                    this.lootedTreasure.add(event.result.getBlockPos());
                    if (XaeroWaypointManager.isAvailable() && this.treasureWaypoints.get()) {
                        XaeroWaypointManager.removeWaypoint("VanityESP", event.result.getBlockPos());
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.level != null && this.mc.player != null) {
            if (this.highlightMapFrames.get()) {
                ShapeMode mapMode = this.getShapeMode(this.mapRenderFill.get(), this.mapRenderOutline.get());
                if (mapMode != null || this.mapRenderTracer.get()) {
                    Color fill = new Color(this.mapFillColor.get());
                    Color outline = new Color(this.mapOutlineColor.get());
                    boolean tracer = this.mapRenderTracer.get();
                    SettingColor tracerCol = this.mapTracerColor.get();

                    for (ItemFrame frame : this.mc
                        .level
                        .getEntitiesOfClass(
                            ItemFrame.class,
                            this.mc.player.getBoundingBox().inflate(64.0),
                            e -> e.getItem().getItem().getDescriptionId().equals("item.minecraft.filled_map")
                        )) {
                        float pitch = frame.getXRot();
                        AABB box;
                        if (pitch != 90.0F && pitch != -90.0F) {
                            box = frame.getBoundingBox().inflate(0.12, 0.12, 0.01);
                        } else {
                            box = frame.getBoundingBox().inflate(0.12, 0.01, 0.12);
                        }

                        if (mapMode != null) {
                            event.renderer.box(box, fill, outline, mapMode, 0);
                        }

                        if (tracer) {
                            Vec3 boxCenter = box.getCenter();
                            event.renderer
                                .line(
                                    RenderUtils.center.x,
                                    RenderUtils.center.y,
                                    RenderUtils.center.z,
                                    boxCenter.x,
                                    boxCenter.y,
                                    boxCenter.z,
                                    tracerCol
                                );
                        }
                    }
                }
            }

            if (this.highlightBanners.get()) {
                ShapeMode bannerMode = this.getShapeMode(this.bannerRenderFill.get(), this.bannerRenderOutline.get());
                if (bannerMode != null || this.bannerRenderTracer.get()) {
                    this.renderBanners(event, bannerMode);
                }
            }

            if (this.highlightShulkerFrames.get()) {
                ShapeMode shulkerMode = this.getShapeMode(this.shulkerRenderFill.get(), this.shulkerRenderOutline.get());
                if (shulkerMode != null || this.shulkerRenderTracer.get()) {
                    this.renderShulkerFrames(event, shulkerMode);
                }
            }

            if (this.highlightOminousVaults.get()) {
                ShapeMode vaultMode = this.getShapeMode(this.vaultRenderFill.get(), this.vaultRenderOutline.get());
                if (vaultMode != null || this.vaultRenderTracer.get()) {
                    this.renderOminousVaults(event, vaultMode);
                }
            }

            if (this.highlightTreasure.get()) {
                ShapeMode treasureMode = this.getShapeMode(this.treasureRenderFill.get(), this.treasureRenderOutline.get());
                if (treasureMode != null || this.treasureRenderTracer.get()) {
                    this.renderTreasure(event, treasureMode);
                }
            }

            if (this.highlightEndermanHolding.get()) {
                ShapeMode endermanMode = this.getShapeMode(this.endermanRenderFill.get(), this.endermanRenderOutline.get());
                if (endermanMode != null || this.endermanRenderTracer.get()) {
                    this.renderEndermanHoldingBlocks(event, endermanMode);
                }
            }

            if (this.highlightXPOrbs.get()) {
                ShapeMode xpOrbMode = this.getShapeMode(this.xpOrbRenderFill.get(), this.xpOrbRenderOutline.get());
                if (xpOrbMode != null || this.xpOrbRenderTracer.get()) {
                    this.renderXPOrbs(event, xpOrbMode);
                }
            }

            if (this.highlightStackedEntities.get()) {
                ShapeMode stackedMode = this.getShapeMode(this.stackedEntitiesRenderFill.get(), this.stackedEntitiesRenderOutline.get());
                if (stackedMode != null || this.stackedEntitiesRenderTracer.get()) {
                    this.detectAndRenderStackedEntities(event, stackedMode);
                }
            }
        }
    }

    private ShapeMode getShapeMode(boolean renderFill, boolean renderOutline) {
        return bep.hax.util.RenderUtils.shapeMode(renderFill, renderOutline);
    }

    private void renderBanners(Render3DEvent event, ShapeMode shapeMode) {
        if (!this.bannerBoxes.isEmpty()) {
            Color fill = new Color(this.bannerFillColor.get());
            Color outline = new Color(this.bannerOutlineColor.get());
            boolean tracer = this.bannerRenderTracer.get();
            SettingColor tracerCol = this.bannerTracerColor.get();

            for (AABB box : this.bannerBoxes) {
                if (shapeMode != null) {
                    event.renderer.box(box, fill, outline, shapeMode, 0);
                }

                if (tracer) {
                    Vec3 boxCenter = box.getCenter();
                    event.renderer
                        .line(
                            RenderUtils.center.x,
                            RenderUtils.center.y,
                            RenderUtils.center.z,
                            boxCenter.x,
                            boxCenter.y,
                            boxCenter.z,
                            tracerCol
                        );
                }
            }
        }
    }

    private void updateBannerCache() {
        if (!this.highlightBanners.get()) {
            if (!this.bannerBoxes.isEmpty()) {
                this.bannerBoxes.clear();
            }
        } else {
            BlockPos playerPos = this.mc.player.blockPosition();
            int chunkX = playerPos.getX() >> 4;
            int chunkZ = playerPos.getZ() >> 4;
            boolean crossedChunk = chunkX != this.lastBannerChunkX || chunkZ != this.lastBannerChunkZ;
            if (crossedChunk || ++this.bannerScanTicks >= 10) {
                this.bannerScanTicks = 0;
                this.lastBannerChunkX = chunkX;
                this.lastBannerChunkZ = chunkZ;
                this.scanBanners(chunkX, chunkZ);
            }
        }
    }

    private void scanBanners(int centerChunkX, int centerChunkZ) {
        this.bannerBoxes.clear();
        int radius = 8;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = this.mc.level.getChunk(centerChunkX + dx, centerChunkZ + dz);
                if (chunk != null) {
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be instanceof BannerBlockEntity banner) {
                            AABB box = this.bannerBox(banner.getBlockPos());
                            if (box != null) {
                                this.bannerBoxes.add(box);
                            }
                        }
                    }
                }
            }
        }
    }

    private AABB bannerBox(BlockPos pos) {
        BlockState state = this.mc.level.getBlockState(pos);
        if (state.hasProperty(WallBannerBlock.FACING)) {
            Direction facing = state.getValue(WallBannerBlock.FACING);
            double centerX = pos.getX() + 0.5;
            double centerZ = pos.getZ() + 0.5;
            double offset = 0.1;
            double depth = 0.03;
            double width = 0.45;
            double y1 = pos.getY() - 0.95;
            double y2 = pos.getY() + 0.85;
            AABB box;
            switch (facing) {
                case NORTH:
                    box = new AABB(centerX - width, y1, pos.getZ() + 1 - offset - depth, centerX + width, y2, pos.getZ() + 1 - offset);
                    break;
                case SOUTH:
                    box = new AABB(centerX - width, y1, pos.getZ() + offset, centerX + width, y2, pos.getZ() + offset + depth);
                    break;
                case WEST:
                    box = new AABB(pos.getX() + 1 - offset - depth, y1, centerZ - width, pos.getX() + 1 - offset, y2, centerZ + width);
                    break;
                case EAST:
                    box = new AABB(pos.getX() + offset, y1, centerZ - width, pos.getX() + offset + depth, y2, centerZ + width);
                    break;
                default:
                    return null;
            }

            return box;
        } else {
            if (!state.hasProperty(BannerBlock.ROTATION)) {
                return null;
            }

            int rotation = state.getValue(BannerBlock.ROTATION);
            double centerX = pos.getX() + 0.5;
            double centerZ = pos.getZ() + 0.5;
            double y1 = pos.getY();
            double y2 = pos.getY() + 1.85;
            AABB box;
            if (rotation == 0 || rotation == 8) {
                double width = 0.45;
                double depth = 0.03;
                box = new AABB(centerX - width, y1, centerZ - depth, centerX + width, y2, centerZ + depth);
            } else if (rotation != 4 && rotation != 12) {
                double size = 0.3;
                box = new AABB(centerX - size, y1, centerZ - size, centerX + size, y2, centerZ + size);
            } else {
                double width = 0.03;
                double depth = 0.45;
                box = new AABB(centerX - width, y1, centerZ - depth, centerX + width, y2, centerZ + depth);
            }

            return box;
        }
    }

    private void renderShulkerFrames(Render3DEvent event, ShapeMode shapeMode) {
        List<Entity> frames = this.getShulkerFrames();
        if (!frames.isEmpty()) {
            for (Entity frame : frames) {
                AABB box = frame.getBoundingBox();
                if (shapeMode != null) {
                    event.renderer.box(box, this.shulkerFillColor.get(), this.shulkerOutlineColor.get(), shapeMode, 0);
                }

                if (this.shulkerRenderTracer.get()) {
                    Vec3 boxCenter = box.getCenter();
                    event.renderer
                        .line(
                            RenderUtils.center.x,
                            RenderUtils.center.y,
                            RenderUtils.center.z,
                            boxCenter.x,
                            boxCenter.y,
                            boxCenter.z,
                            this.shulkerTracerColor.get()
                        );
                }
            }
        }
    }

    private void renderOminousVaults(Render3DEvent event, ShapeMode shapeMode) {
        for (BlockPos pos : this.ominousVaults) {
            if (shapeMode != null) {
                event.renderer.box(pos, this.vaultFillColor.get(), this.vaultOutlineColor.get(), shapeMode, 0);
            }

            if (this.vaultRenderTracer.get()) {
                event.renderer
                    .line(
                        RenderUtils.center.x,
                        RenderUtils.center.y,
                        RenderUtils.center.z,
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        this.vaultTracerColor.get()
                    );
            }
        }
    }

    private void renderTreasure(Render3DEvent event, ShapeMode shapeMode) {
        for (BlockPos pos : this.notifiedTreasure
            .stream()
            .filter(posx -> posx.closerThan(this.mc.player.blockPosition(), this.mc.options.renderDistance().get() * 16 + 32))
            .toList()) {
            if (!this.lootedTreasure.contains(pos)) {
                if (shapeMode != null) {
                    event.renderer
                        .box(
                            pos.getX(),
                            pos.getY(),
                            pos.getZ(),
                            pos.getX() + 1,
                            pos.getY() + 1,
                            pos.getZ() + 1,
                            this.treasureFillColor.get(),
                            this.treasureOutlineColor.get(),
                            shapeMode,
                            0
                        );
                }

                if (this.treasureRenderTracer.get()) {
                    event.renderer
                        .line(
                            RenderUtils.center.x,
                            RenderUtils.center.y,
                            RenderUtils.center.z,
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5,
                            this.treasureTracerColor.get()
                        );
                }
            }
        }
    }

    private List<Entity> getShulkerFrames() {
        List<Entity> result = new ArrayList<>();
        if (this.mc.level == null) {
            return result;
        }

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof ItemFrame || entity instanceof GlowItemFrame) {
                ItemStack stack = ((ItemFrame)entity).getItem();
                if (this.isShulkerBox(stack)) {
                    result.add(entity);
                }
            }
        }

        return result;
    }

    private boolean isShulkerBox(ItemStack stack) {
        return stack == null
            ? false
            : stack.getItem() == Items.SHULKER_BOX
                || stack.getItem() == Items.WHITE_SHULKER_BOX
                || stack.getItem() == Items.ORANGE_SHULKER_BOX
                || stack.getItem() == Items.MAGENTA_SHULKER_BOX
                || stack.getItem() == Items.LIGHT_BLUE_SHULKER_BOX
                || stack.getItem() == Items.YELLOW_SHULKER_BOX
                || stack.getItem() == Items.LIME_SHULKER_BOX
                || stack.getItem() == Items.PINK_SHULKER_BOX
                || stack.getItem() == Items.GRAY_SHULKER_BOX
                || stack.getItem() == Items.LIGHT_GRAY_SHULKER_BOX
                || stack.getItem() == Items.CYAN_SHULKER_BOX
                || stack.getItem() == Items.PURPLE_SHULKER_BOX
                || stack.getItem() == Items.BLUE_SHULKER_BOX
                || stack.getItem() == Items.BROWN_SHULKER_BOX
                || stack.getItem() == Items.GREEN_SHULKER_BOX
                || stack.getItem() == Items.RED_SHULKER_BOX
                || stack.getItem() == Items.BLACK_SHULKER_BOX;
    }

    private boolean scanChunkForVaults(ChunkPos chunkPos, int minY, int maxY) {
        ChunkAccess chunk;
        try {
            chunk = this.mc.level.getChunk(chunkPos.x, chunkPos.z);
        } catch (Exception e) {
            return false;
        }

        if (chunk instanceof LevelChunk) {
            Set<BlockPos> foundVaults = new HashSet<>();

            for (BlockEntity blockEntity : ((LevelChunk)chunk).getBlockEntities().values()) {
                if (BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()) != null
                    && BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).getPath().equals("vault")) {
                    BlockPos pos = blockEntity.getBlockPos();
                    BlockState state = this.mc.level.getBlockState(pos);
                    Property<?> ominousProperty = null;

                    for (Property<?> prop : state.getProperties()) {
                        if (prop.getName().equals("ominous")) {
                            ominousProperty = prop;
                            break;
                        }
                    }

                    if (ominousProperty != null && Boolean.TRUE.equals(state.getValue(ominousProperty))) {
                        foundVaults.add(pos);
                    }
                }
            }

            if (!foundVaults.isEmpty()) {
                this.chunkVaults.put(chunkPos, foundVaults);
                this.ominousVaults.addAll(foundVaults);
                if (this.ominousVaults.size() > 1000) {
                    this.ominousVaults.clear();
                    this.chunkVaults.clear();
                }

                return true;
            }
        }

        return false;
    }

    private void scanChunkForTreasure(LevelChunk chunk) {
        Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();

        for (BlockPos pos : blockEntities.keySet()) {
            if (!this.notifiedTreasure.contains(pos) && blockEntities.get(pos) instanceof ChestBlockEntity) {
                int localX = SectionPos.sectionRelative(pos.getX());
                int localZ = SectionPos.sectionRelative(pos.getZ());
                if (localX == 9 && localZ == 9 && this.isBuriedNaturally(pos)) {
                    if (XaeroWaypointManager.isAvailable() && this.treasureWaypoints.get()) {
                        XaeroWaypointManager.addWaypoint("VanityESP", pos, "Buried Treasure", "❌", MapUtil.WpColor.Dark_Red, this.treasureTempWaypoints.get());
                    }

                    if (this.treasureSound.get()) {
                        this.mc.player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, this.treasureVolume.get().floatValue(), 1.0F);
                    }

                    if (this.treasureChat.get()) {
                        String notification;
                        if (this.treasureCoords.get()) {
                            notification = "§3§oFound buried treasure at §8[§7§o"
                                + pos.getX()
                                + "§8, §7§o"
                                + pos.getY()
                                + "§8, §7§o"
                                + pos.getZ()
                                + "§8]";
                        } else {
                            notification = "§3§oFound buried treasure§7§o!";
                        }

                        MsgUtil.sendModuleMsg(notification, this.name);
                    }

                    this.notifiedTreasure.add(pos);
                    if (this.notifiedTreasure.size() > 100) {
                        this.notifiedTreasure.remove(0);
                    }
                }
            }
        }

        if (this.lootedTreasure.size() > 500) {
            this.lootedTreasure.clear();
        }
    }

    private boolean isBuriedNaturally(BlockPos pos) {
        if (this.mc.level == null) {
            return false;
        }

        Block block = this.mc.level.getBlockState(pos.above()).getBlock();
        return block == Blocks.SAND
            || block == Blocks.DIRT
            || block == Blocks.GRAVEL
            || block == Blocks.STONE
            || block == Blocks.DIORITE
            || block == Blocks.GRANITE
            || block == Blocks.ANDESITE
            || block == Blocks.SANDSTONE
            || block == Blocks.COAL_ORE;
    }

    private void renderEndermanHoldingBlocks(Render3DEvent event, ShapeMode shapeMode) {
        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof EnderMan enderman && enderman.getCarriedBlock() != null) {
                AABB box = enderman.getBoundingBox();
                if (shapeMode != null) {
                    event.renderer.box(box, this.endermanFillColor.get(), this.endermanOutlineColor.get(), shapeMode, 0);
                }

                if (this.endermanRenderTracer.get()) {
                    Vec3 boxCenter = box.getCenter();
                    event.renderer
                        .line(
                            RenderUtils.center.x,
                            RenderUtils.center.y,
                            RenderUtils.center.z,
                            boxCenter.x,
                            boxCenter.y,
                            boxCenter.z,
                            this.endermanTracerColor.get()
                        );
                }
            }
        }
    }

    private void renderXPOrbs(Render3DEvent event, ShapeMode shapeMode) {
        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof ExperienceOrb) {
                AABB box = entity.getBoundingBox();
                if (shapeMode != null) {
                    event.renderer.box(box, this.xpOrbFillColor.get(), this.xpOrbOutlineColor.get(), shapeMode, 0);
                }

                if (this.xpOrbRenderTracer.get()) {
                    Vec3 boxCenter = box.getCenter();
                    event.renderer
                        .line(
                            RenderUtils.center.x,
                            RenderUtils.center.y,
                            RenderUtils.center.z,
                            boxCenter.x,
                            boxCenter.y,
                            boxCenter.z,
                            this.xpOrbTracerColor.get()
                        );
                }
            }
        }
    }

    private void detectAndRenderStackedEntities(Render3DEvent event, ShapeMode shapeMode) {
        if (this.mc.level != null && this.mc.player != null) {
            long now = System.currentTimeMillis();
            if (now - this.lastStackedScanMs >= 200L) {
                this.lastStackedScanMs = now;
                this.recomputeStackedGroups();
            }

            SettingColor fill = this.stackedEntitiesFillColor.get();
            SettingColor outline = this.stackedEntitiesOutlineColor.get();
            boolean tracer = this.stackedEntitiesRenderTracer.get();
            SettingColor tracerCol = this.stackedEntitiesTracerColor.get();

            for (List<Entity> entities : this.cachedStackedGroups) {
                for (Entity entity : entities) {
                    AABB box = entity.getBoundingBox();
                    if (shapeMode != null) {
                        event.renderer.box(box, fill, outline, shapeMode, 0);
                    }

                    if (tracer) {
                        Vec3 boxCenter = box.getCenter();
                        event.renderer
                            .line(
                                RenderUtils.center.x,
                                RenderUtils.center.y,
                                RenderUtils.center.z,
                                boxCenter.x,
                                boxCenter.y,
                                boxCenter.z,
                                tracerCol
                            );
                    }
                }
            }
        }
    }

    private void recomputeStackedGroups() {
        this.cachedStackedGroups.clear();
        Map<BlockPos, List<Entity>> positionMap = new HashMap<>();

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (this.isInventoryEntity(entity)) {
                BlockPos pos = entity.blockPosition();
                positionMap.computeIfAbsent(pos, k -> new ArrayList<>()).add(entity);
            }
        }

        int minCount = this.stackedEntitiesMinCount.get();
        Set<BlockPos> currentStackedPositions = new HashSet<>();

        for (Entry<BlockPos, List<Entity>> entry : positionMap.entrySet()) {
            BlockPos pos = entry.getKey();
            List<Entity> entities = entry.getValue();
            if (entities.size() >= minCount) {
                currentStackedPositions.add(pos);
                if (!this.notifiedStackedEntities.contains(pos)) {
                    this.notifyStackedEntities(pos, entities);
                    this.notifiedStackedEntities.add(pos);
                }

                this.cachedStackedGroups.add(entities);
            }
        }

        this.notifiedStackedEntities.retainAll(currentStackedPositions);
    }

    private boolean isInventoryEntity(Entity entity) {
        if (entity instanceof AbstractMinecartContainer) {
            return true;
        } else if (entity instanceof MinecartHopper) {
            return true;
        } else {
            return entity instanceof ChestBoat ? true : entity instanceof Container;
        }
    }

    private void notifyStackedEntities(BlockPos pos, List<Entity> entities) {
        if (this.mc.player != null) {
            String entityType = this.getEntityTypeName(entities.get(0));
            int count = entities.size();
            if (this.stackedEntitiesChat.get()) {
                String notification;
                if (this.stackedEntitiesCoords.get()) {
                    notification = String.format(
                        "§6§oFound §e§l%d §6§ostacked %s at §8[§7§o%d§8, §7§o%d§8, §7§o%d§8]",
                        count,
                        entityType,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()
                    );
                } else {
                    notification = String.format("§6§oFound §e§l%d §6§ostacked %s§7§o!", count, entityType);
                }

                MsgUtil.sendModuleMsg(notification, this.name);
            }

            if (this.stackedEntitiesSound.get()) {
                this.mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, this.stackedEntitiesVolume.get().floatValue(), 0.5F);
            }

            if (XaeroWaypointManager.isAvailable() && this.stackedEntitiesWaypoints.get()) {
                String wpName = count + " Stacked " + entityType;
                XaeroWaypointManager.addWaypoint("VanityESP", pos, wpName, "⚠", MapUtil.WpColor.Gold, this.stackedEntitiesTempWaypoints.get());
            }
        }
    }

    private String getEntityTypeName(Entity entity) {
        if (entity instanceof AbstractMinecartContainer) {
            return "Chest Minecarts";
        } else if (entity instanceof MinecartHopper) {
            return "Hopper Minecarts";
        } else if (entity instanceof ChestBoat) {
            return "Chest Boats";
        } else {
            return entity instanceof Container ? "Inventory Entities" : "Entities";
        }
    }
}
