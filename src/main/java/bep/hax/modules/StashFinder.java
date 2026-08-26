package bep.hax.modules;

import baritone.api.BaritoneAPI;
import bep.hax.config.BepConfig;
import bep.hax.util.BaritoneHelper;
import bep.hax.util.MapUtil;
import bep.hax.util.NewChunkUtils;
import bep.hax.util.Utils;
import bep.hax.util.XaeroWaypointManager;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.phys.Vec3;

public class StashFinder extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgDetection = this.settings.createGroup("Detection");
    private final SettingGroup sgThresholds = this.settings.createGroup("Thresholds");
    private final SettingGroup sgPrivacy = this.settings.createGroup("Privacy");
    private final SettingGroup sgWaypoints = this.settings.createGroup("Waypoints");
    private final SettingGroup sgDiscord = this.settings.createGroup("Discord");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Integer> minimumStorageCount = this.sgGeneral
        .add(
            new Builder()
                .name("minimum-storage-count")
                .description("Minimum storage blocks in a chunk to record it.")
                .defaultValue(10)
                .min(1)
                .sliderRange(1, 20)
                .build()
        );
    private final Setting<Integer> minimumDistance = this.sgGeneral
        .add(
            new Builder()
                .name("minimum-distance")
                .description("Minimum distance from spawn to record chunks.")
                .defaultValue(5000)
                .min(0)
                .sliderRange(0, 50000)
                .build()
        );
    private final Setting<Boolean> sendNotifications = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("notifications")
                .description("Show notifications when stashes are found.")
                .defaultValue(true)
                .build()
        );
    private final Setting<StashFinder.NotificationMode> notificationMode = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                                .name("notification-mode"))
                            .description("How to notify about found stashes."))
                        .defaultValue(StashFinder.NotificationMode.Toast))
                    .visible(this.sendNotifications::get))
                .build()
        );
    private final Setting<Integer> clusterRadius = this.sgGeneral
        .add(
            new Builder()
                .name("cluster-radius")
                .description("Chunks within this radius are merged into one waypoint. 5 = 80 blocks.")
                .defaultValue(5)
                .min(1)
                .sliderRange(1, 32)
                .build()
        );
    private final Setting<Boolean> detectChests = this.sgDetection
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("chests").description("Detect regular chests.").defaultValue(true).build());
    private final Setting<Boolean> detectTrappedChests = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("trapped-chests")
                .description("Detect trapped chests (separately counted).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> detectBarrels = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("barrels")
                .description("Detect barrels (common in villages - disable for base hunting).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> detectShulkers = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("shulkers")
                .description("Detect shulker boxes (high-value player items).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> detectEnderChests = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("ender-chests")
                .description("Detect ender chests (always player-placed).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> detectFurnaces = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("furnaces")
                .description("Detect furnaces, blast furnaces, and smokers.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> detectDispensers = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("dispensers-droppers")
                .description("Detect dispensers and droppers.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> detectHoppers = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("hoppers")
                .description("Detect hoppers (player farms/sorting systems).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> detectBrewingStands = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("brewing-stands")
                .description("Detect brewing stands.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> detectCrafters = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("crafters")
                .description("Detect crafters (1.21+).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> detectDecoratedPots = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("decorated-pots")
                .description("Detect decorated pots (can store items).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> detectBanners = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("banners")
                .description("Detect banners (common at pillager outposts - disable for base hunting).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> detectSigns = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("signs")
                .description("Detect signs (often mark player bases/storage - common in villages too).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> detectHangingSigns = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("hanging-signs")
                .description("Detect hanging signs (player-placed decoration/labels).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> detectMapItemFrames = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("map-item-frames")
                .description("Detect item frames containing maps (map art/base maps).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> detectItemFrames = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("item-frames")
                .description("Detect all item frames (not just maps).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> detectEnderPearls = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("ender-pearls")
                .description("Detect loaded ender pearl entities (pearls in stasis chambers).")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> detectNamedEntities = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("named-entities")
                .description("Detect entities with custom name tags (player's named mobs).")
                .defaultValue(true)
                .build()
        );
    private final Setting<List<Block>> blacklistedBlocks = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                .name("blacklisted-support-blocks")
                .description("Ignore containers near these blocks (checks below, 2 below, and adjacent for wall-mounted blocks like banners).")
                .defaultValue(
                    Blocks.OXIDIZED_COPPER,
                    Blocks.OXIDIZED_CUT_COPPER,
                    Blocks.TUFF_BRICKS,
                    Blocks.WAXED_COPPER_BLOCK,
                    Blocks.WAXED_OXIDIZED_COPPER,
                    Blocks.WAXED_OXIDIZED_CUT_COPPER,
                    Blocks.WAXED_COPPER_BULB,
                    Blocks.BARREL,
                    Blocks.SMOOTH_STONE,
                    Blocks.STONE_BRICKS,
                    Blocks.MOSSY_STONE_BRICKS,
                    Blocks.CRACKED_STONE_BRICKS,
                    Blocks.DEEPSLATE_BRICKS,
                    Blocks.DEEPSLATE_TILES,
                    Blocks.POLISHED_DEEPSLATE,
                    Blocks.SCULK,
                    Blocks.COBBLESTONE,
                    Blocks.MOSSY_COBBLESTONE,
                    Blocks.SANDSTONE,
                    Blocks.CUT_SANDSTONE,
                    Blocks.CHISELED_SANDSTONE,
                    Blocks.MOSSY_COBBLESTONE,
                    Blocks.PRISMARINE,
                    Blocks.PRISMARINE_BRICKS,
                    Blocks.DARK_PRISMARINE,
                    Blocks.END_STONE_BRICKS,
                    Blocks.PURPUR_BLOCK,
                    Blocks.PURPUR_PILLAR,
                    Blocks.POLISHED_BLACKSTONE_BRICKS,
                    Blocks.POLISHED_BLACKSTONE,
                    Blocks.BLACKSTONE,
                    Blocks.GILDED_BLACKSTONE,
                    Blocks.NETHER_BRICKS,
                    Blocks.DARK_OAK_PLANKS,
                    Blocks.OAK_PLANKS,
                    Blocks.DARK_OAK_LOG,
                    Blocks.ACACIA_LOG,
                    Blocks.ACACIA_PLANKS,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.NETHERRACK,
                    Blocks.CRYING_OBSIDIAN
                )
                .build()
        );
    private final Setting<Boolean> oldChunksOnly = this.sgDetection
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("old-chunks-only")
                .description(
                    "Only count containers and items in chunks the server already had saved, ignoring chunks it generated as you loaded them (newchunk palette detection)."
                )
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> minChests = this.sgThresholds
        .add(
            new Builder()
                .name("min-chests")
                .description("Minimum chests to trigger notification (5+ filters desert temples (4/chunk), shipwrecks (3), villages, end cities).")
                .defaultValue(5)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minTrappedChests = this.sgThresholds
        .add(
            new Builder()
                .name("min-trapped-chests")
                .description("Minimum trapped chests to trigger notification (always player-placed).")
                .defaultValue(1)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minBarrels = this.sgThresholds
        .add(
            new Builder()
                .name("min-barrels")
                .description("Minimum barrels to trigger notification (fisher cottages/outpost towers stack up to 4-5 in one chunk).")
                .defaultValue(6)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minShulkers = this.sgThresholds
        .add(
            new Builder()
                .name("min-shulkers")
                .description("Minimum shulker boxes to trigger notification (always player items).")
                .defaultValue(1)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minEnderChests = this.sgThresholds
        .add(
            new Builder()
                .name("min-ender-chests")
                .description("Minimum ender chests to trigger notification (always player-placed).")
                .defaultValue(2)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minFurnaces = this.sgThresholds
        .add(
            new Builder()
                .name("min-furnaces")
                .description("Minimum furnaces to trigger notification (villages have 1, player bases more).")
                .defaultValue(0)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minDispensers = this.sgThresholds
        .add(
            new Builder()
                .name("min-dispensers")
                .description("Minimum dispensers/droppers to trigger notification (usually player contraptions).")
                .defaultValue(0)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minHoppers = this.sgThresholds
        .add(
            new Builder()
                .name("min-hoppers")
                .description("Minimum hoppers to trigger notification (player farms usually have 3+).")
                .defaultValue(3)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minBrewingStands = this.sgThresholds
        .add(
            new Builder()
                .name("min-brewing-stands")
                .description("Minimum brewing stands to trigger notification (always player-placed).")
                .defaultValue(0)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minCrafters = this.sgThresholds
        .add(
            new Builder()
                .name("min-crafters")
                .description("Minimum crafters to trigger notification (always player-placed, 1.21+ block).")
                .defaultValue(1)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minDecoratedPots = this.sgThresholds
        .add(
            new Builder()
                .name("min-decorated-pots")
                .description("Minimum decorated pots to trigger notification (trail ruins/trial chambers cluster several in one chunk).")
                .defaultValue(6)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minBanners = this.sgThresholds
        .add(
            new Builder()
                .name("min-banners")
                .description("Minimum banners to trigger notification (outpost tops have 2-3 in one chunk, end city walls too).")
                .defaultValue(4)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minSigns = this.sgThresholds
        .add(
            new Builder()
                .name("min-signs")
                .description("Minimum signs to trigger notification (villages have some, player bases more).")
                .defaultValue(3)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minHangingSigns = this.sgThresholds
        .add(
            new Builder()
                .name("min-hanging-signs")
                .description("Minimum hanging signs to trigger notification.")
                .defaultValue(2)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minMapItemFrames = this.sgThresholds
        .add(
            new Builder()
                .name("min-map-item-frames")
                .description("Minimum map item frames to trigger notification (never vanilla; map walls have 6+ in a chunk).")
                .defaultValue(6)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minItemFrames = this.sgThresholds
        .add(
            new Builder().name("min-item-frames").description("Minimum item frames to trigger notification.").defaultValue(4).min(0).sliderRange(0, 20).build()
        );
    private final Setting<Integer> minEnderPearls = this.sgThresholds
        .add(
            new Builder()
                .name("min-ender-pearls")
                .description("Minimum ender pearls to trigger notification (1+ = stasis chamber).")
                .defaultValue(1)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Integer> minNamedEntities = this.sgThresholds
        .add(
            new Builder()
                .name("min-named-entities")
                .description("Minimum named entities to trigger notification.")
                .defaultValue(1)
                .min(0)
                .sliderRange(0, 20)
                .build()
        );
    private final Setting<Boolean> hideCoordinates = this.sgPrivacy
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("hide-coordinates")
                .description("Hide coordinates in the main module widget.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> noCoordChat = this.sgPrivacy
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("no-coord-chat")
                .description("Never show coordinates in chat messages.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Keybind> openCoordListBind = this.sgPrivacy
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("open-coordinate-list")
                .description("Keybind to open the coordinate list.")
                .defaultValue(Keybind.none())
                .build()
        );
    private final Setting<Boolean> renderTracer = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render-tracer")
                .description("Render tracers to stash locations.")
                .defaultValue(false)
                .build()
        );
    private final Setting<SettingColor> traceColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("tracer-color")
                .description("Color of stash tracers.")
                .defaultValue(new SettingColor(255, 215, 0, 255))
                .visible(this.renderTracer::get)
                .build()
        );
    private final Setting<Integer> traceArrivalDistance = this.sgRender
        .add(
            new Builder()
                .name("hide-at-distance")
                .description("Hide tracer when within this distance.")
                .defaultValue(16)
                .min(1)
                .sliderRange(1, 100)
                .visible(this.renderTracer::get)
                .build()
        );
    private final Setting<Integer> traceMaxDistance = this.sgRender
        .add(
            new Builder()
                .name("max-trace-distance")
                .description("Maximum distance to render tracers.")
                .defaultValue(5000)
                .min(100)
                .sliderRange(100, 30000)
                .visible(this.renderTracer::get)
                .build()
        );
    private final Setting<Boolean> renderChunkColumn = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render-column")
                .description("Render vertical column at stash locations.")
                .defaultValue(false)
                .build()
        );
    private final Setting<SettingColor> columnColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("column-color")
                .description("Color of the chunk column.")
                .defaultValue(new SettingColor(255, 215, 0, 255))
                .visible(this.renderChunkColumn::get)
                .build()
        );
    private final Setting<Keybind> clearTracesBind = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("clear-traces")
                .description("Keybind to clear all active tracers.")
                .defaultValue(Keybind.none())
                .build()
        );
    private final Setting<Boolean> addWaypoints = this.sgWaypoints
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("add-waypoints")
                .description("Add Xaero waypoints for found stashes.")
                .defaultValue(true)
                .visible(() -> Utils.XAERO_AVAILABLE)
                .build()
        );
    private final Setting<Boolean> tempWaypoints = this.sgWaypoints
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("temporary-waypoints")
                .description("Waypoints are removed when you disconnect.")
                .defaultValue(false)
                .visible(() -> Utils.XAERO_AVAILABLE && this.addWaypoints.get())
                .build()
        );
    private final Setting<Boolean> useSymbols = this.sgWaypoints
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("use-symbols")
                .description("Use symbols as waypoint name based on container types that meet thresholds.")
                .defaultValue(true)
                .visible(() -> Utils.XAERO_AVAILABLE && this.addWaypoints.get())
                .build()
        );
    private static final String CHEST_SYMBOL = "\ud83d\udce6";
    private static final String TRAPPED_CHEST_SYMBOL = "\ud83e\udea4";
    private static final String BARREL_SYMBOL = "\ud83d\udee2";
    private static final String SHULKER_SYMBOL = "\ud83c\udf81";
    private static final String ENDER_CHEST_SYMBOL = "☁";
    private static final String FURNACE_SYMBOL = "\ud83d\udd25";
    private static final String DISPENSER_SYMBOL = "⬇";
    private static final String HOPPER_SYMBOL = "⏬";
    private static final String BREWING_STAND_SYMBOL = "\ud83e\uddea";
    private static final String CRAFTER_SYMBOL = "\ud83d\udd27";
    private static final String DECORATED_POT_SYMBOL = "\ud83c\udffa";
    private static final String BANNER_SYMBOL = "\ud83d\udea9";
    private static final String SIGN_SYMBOL = "\ud83e\udea7";
    private static final String HANGING_SIGN_SYMBOL = "\ud83e\ude9d";
    private static final String MAP_ITEM_FRAME_SYMBOL = "\ud83d\uddfa";
    private static final String ITEM_FRAME_SYMBOL = "\ud83d\uddbc";
    private static final String ENDER_PEARL_SYMBOL = "\ud83d\udc41";
    private static final String NAMED_ENTITY_SYMBOL = "\ud83c\udff7";
    private final Setting<Boolean> showQuantity = this.sgWaypoints
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-quantity")
                .description("Append total container count to waypoint name.")
                .defaultValue(true)
                .visible(() -> Utils.XAERO_AVAILABLE && this.addWaypoints.get())
                .build()
        );
    private final Setting<MapUtil.WpColor> waypointColor = this.sgWaypoints
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                                .name("waypoint-color"))
                            .description("Color of stash waypoints."))
                        .defaultValue(MapUtil.WpColor.Gold))
                    .visible(() -> Utils.XAERO_AVAILABLE && this.addWaypoints.get()))
                .build()
        );
    private final Setting<Boolean> discordEnabled = this.sgDiscord
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("discord-webhook")
                .description("Send stash notifications to a Discord webhook.")
                .defaultValue(false)
                .build()
        );
    private final Setting<String> discordWebhookUrl = this.sgDiscord
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("webhook-url")
                .description("Discord webhook URL.")
                .defaultValue("")
                .visible(this.discordEnabled::get)
                .build()
        );
    private final Setting<Boolean> discordIncludeCoords = this.sgDiscord
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("include-coordinates")
                .description("Include coordinates in Discord messages (careful with privacy!).")
                .defaultValue(false)
                .visible(this.discordEnabled::get)
                .build()
        );
    private final Setting<Boolean> discordIncludeBreakdown = this.sgDiscord
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("include-breakdown")
                .description("Include container type breakdown in Discord messages.")
                .defaultValue(true)
                .visible(this.discordEnabled::get)
                .build()
        );
    private final Setting<String> discordUsername = this.sgDiscord
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("webhook-username")
                .description("Custom username for the webhook bot.")
                .defaultValue("StashFinder")
                .visible(this.discordEnabled::get)
                .build()
        );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<ChunkPos, Vec3> tracerPositions = new HashMap<>();
    private final Map<ChunkPos, Set<UUID>> countedEntityUuids = new HashMap<>();
    private final Map<ChunkPos, StashFinder.StashChunk> pendingEntityChunks = new HashMap<>();
    private final Set<StashFinder.StashChunk> dirtyStashes = new LinkedHashSet<>();
    private final ExecutorService discordExecutor = Executors.newSingleThreadExecutor();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
    public List<StashFinder.StashChunk> chunks = new ArrayList<>();
    private boolean loaded = false;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private static final String CATEGORY = "StashFinder";

    public StashFinder() {
        super(Categories.World, "stash-finder", "Enhanced stash detection with privacy-focused coordinate management.");
    }

    @Override
    public void onActivate() {
        this.chunks = new ArrayList<>();
        this.loaded = false;
        this.countedEntityUuids.clear();
        this.pendingEntityChunks.clear();
        this.dirtyStashes.clear();
        NewChunkUtils.reset();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.chunks = new ArrayList<>();
        this.loaded = false;
        this.countedEntityUuids.clear();
        this.pendingEntityChunks.clear();
        this.dirtyStashes.clear();
        this.tracerPositions.clear();
        NewChunkUtils.reset();
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.clearTracesBind.get().isPressed()) {
            this.tracerPositions.clear();
        }

        if (this.openCoordListBind.get().isPressed() && this.mc.screen == null) {
            this.openCoordinateList();
        }

        if (!this.dirtyStashes.isEmpty()) {
            Set<StashFinder.StashChunk> processed = new HashSet<>();

            for (StashFinder.StashChunk chunk : this.dirtyStashes) {
                if (!processed.contains(chunk)) {
                    int idx = this.chunks.indexOf(chunk);
                    if (idx >= 0) {
                        chunk = this.chunks.get(idx);
                        List<StashFinder.StashChunk> cluster = this.getCluster(chunk);
                        processed.addAll(cluster);
                        this.addWaypoint(chunk, cluster);
                        if (this.sendNotifications.get()) {
                            this.sendNotification(chunk);
                        }

                        this.sendDiscordNotification(chunk);
                    }
                }
            }

            this.dirtyStashes.clear();
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (this.mc.player != null && this.mc.level != null) {
            this.ensureLoaded();
            double chunkXAbs = Math.abs(event.chunk().getPos().x * 16);
            double chunkZAbs = Math.abs(event.chunk().getPos().z * 16);
            if (!(Math.sqrt(chunkXAbs * chunkXAbs + chunkZAbs * chunkZAbs) < this.minimumDistance.get().intValue())) {
                if (!this.oldChunksOnly.get() || !NewChunkUtils.isFreshlyGenerated(event.chunk())) {
                    StashFinder.StashChunk chunk = new StashFinder.StashChunk(event.chunk().getPos());
                    chunk.dimension = this.getCurrentDimension();
                    List<Block> blockBlacklist = this.blacklistedBlocks.get();

                    for (BlockEntity blockEntity : event.chunk().getBlockEntities().values()) {
                        if (!blockBlacklist.isEmpty()) {
                            boolean isWallMounted = blockEntity instanceof BannerBlockEntity || blockEntity instanceof SignBlockEntity;
                            if (this.isNearBlacklistedBlock(blockEntity.getBlockPos(), blockBlacklist, isWallMounted)) {
                                continue;
                            }
                        }

                        if (blockEntity instanceof TrappedChestBlockEntity && this.detectTrappedChests.get()) {
                            chunk.trappedChests++;
                        } else if (blockEntity instanceof ChestBlockEntity && this.detectChests.get()) {
                            chunk.chests++;
                        } else if (blockEntity instanceof BarrelBlockEntity && this.detectBarrels.get()) {
                            chunk.barrels++;
                        } else if (blockEntity instanceof ShulkerBoxBlockEntity && this.detectShulkers.get()) {
                            chunk.shulkers++;
                        } else if (blockEntity instanceof EnderChestBlockEntity && this.detectEnderChests.get()) {
                            chunk.enderChests++;
                        } else if (blockEntity instanceof AbstractFurnaceBlockEntity && this.detectFurnaces.get()) {
                            chunk.furnaces++;
                        } else if (blockEntity instanceof DispenserBlockEntity && this.detectDispensers.get()) {
                            chunk.dispensersDroppers++;
                        } else if (blockEntity instanceof HopperBlockEntity && this.detectHoppers.get()) {
                            chunk.hoppers++;
                        } else if (blockEntity instanceof BrewingStandBlockEntity && this.detectBrewingStands.get()) {
                            chunk.brewingStands++;
                        } else if (blockEntity instanceof CrafterBlockEntity && this.detectCrafters.get()) {
                            chunk.crafters++;
                        } else if (blockEntity instanceof DecoratedPotBlockEntity && this.detectDecoratedPots.get()) {
                            chunk.decoratedPots++;
                        } else if (blockEntity instanceof BannerBlockEntity && this.detectBanners.get()) {
                            chunk.banners++;
                        } else if (blockEntity instanceof HangingSignBlockEntity && this.detectHangingSigns.get()) {
                            chunk.hangingSigns++;
                        } else if (blockEntity instanceof SignBlockEntity && this.detectSigns.get()) {
                            chunk.signs++;
                        }
                    }

                    int existingIdx = this.chunks.indexOf(chunk);
                    StashFinder.StashChunk prevChunk = existingIdx < 0 ? null : this.chunks.get(existingIdx);
                    StashFinder.StashChunk entitySource = prevChunk;
                    if (entitySource == null) {
                        StashFinder.StashChunk pending = this.pendingEntityChunks.get(chunk.chunkPos);
                        if (pending != null && Objects.equals(pending.dimension, chunk.dimension)) {
                            entitySource = pending;
                        }
                    }

                    if (entitySource != null) {
                        chunk.mapItemFrames = entitySource.mapItemFrames;
                        chunk.itemFrames = entitySource.itemFrames;
                        chunk.enderPearls = entitySource.enderPearls;
                        chunk.namedEntities = entitySource.namedEntities;
                    }

                    if (chunk.getTotal() >= this.minimumStorageCount.get() || this.meetsThresholds(chunk)) {
                        if (existingIdx < 0) {
                            this.chunks.add(chunk);
                        } else {
                            this.chunks.set(existingIdx, chunk);
                        }

                        this.pendingEntityChunks.remove(chunk.chunkPos);
                        if (this.renderTracer.get()) {
                            double y = this.mc.player.getEyeY();
                            this.tracerPositions.put(chunk.chunkPos, new Vec3(chunk.x, y, chunk.z));
                        }

                        this.save();
                        boolean isNew = prevChunk == null || !chunk.countsEqual(prevChunk);
                        if (isNew && this.meetsThresholds(chunk)) {
                            this.dirtyStashes.add(chunk);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (this.mc.player != null && this.mc.level != null) {
            this.ensureLoaded();
            Entity entity = event.entity;
            boolean detected = false;
            String detectionType = null;
            ChunkPos chunkPos;
            if (this.detectEnderPearls.get() && entity instanceof ThrownEnderpearl) {
                chunkPos = new ChunkPos(entity.blockPosition());
                detectionType = "enderPearl";
                detected = true;
            } else if (this.detectNamedEntities.get() && entity instanceof LivingEntity living) {
                if (!living.hasCustomName()) {
                    return;
                }

                chunkPos = new ChunkPos(entity.blockPosition());
                detectionType = "namedEntity";
                detected = true;
            } else {
                if (!(entity instanceof ItemFrame itemFrame)) {
                    return;
                }

                chunkPos = new ChunkPos(itemFrame.blockPosition());
                if (this.detectMapItemFrames.get() && itemFrame.getItem().has(DataComponents.MAP_ID)) {
                    detectionType = "mapItemFrame";
                    detected = true;
                } else {
                    if (!this.detectItemFrames.get() || itemFrame.getItem().isEmpty()) {
                        return;
                    }

                    detectionType = "itemFrame";
                    detected = true;
                }
            }

            if (detected) {
                double chunkXAbs = Math.abs(chunkPos.x * 16);
                double chunkZAbs = Math.abs(chunkPos.z * 16);
                if (!(Math.sqrt(chunkXAbs * chunkXAbs + chunkZAbs * chunkZAbs) < this.minimumDistance.get().intValue())) {
                    if (!this.oldChunksOnly.get() || !NewChunkUtils.isKnownFreshlyGenerated(chunkPos, this.mc.level.dimension())) {
                        if (this.countedEntityUuids.computeIfAbsent(chunkPos, k -> new HashSet<>()).add(entity.getUUID())) {
                            String dimension = this.getCurrentDimension();
                            StashFinder.StashChunk chunk = null;
                            boolean existing = false;

                            for (StashFinder.StashChunk c : this.chunks) {
                                if (c.chunkPos.equals(chunkPos) && Objects.equals(c.dimension, dimension)) {
                                    chunk = c;
                                    existing = true;
                                    break;
                                }
                            }

                            if (chunk == null) {
                                StashFinder.StashChunk pending = this.pendingEntityChunks.get(chunkPos);
                                if (pending != null && Objects.equals(pending.dimension, dimension)) {
                                    chunk = pending;
                                }
                            }

                            StashFinder.StashChunk prevChunk = chunk != null ? snapshot(chunk) : null;
                            if (chunk == null) {
                                chunk = new StashFinder.StashChunk(chunkPos);
                                chunk.dimension = dimension;
                            }

                            switch (detectionType) {
                                case "enderPearl":
                                    chunk.enderPearls++;
                                    break;
                                case "namedEntity":
                                    chunk.namedEntities++;
                                    break;
                                case "mapItemFrame":
                                    chunk.mapItemFrames++;
                                    break;
                                case "itemFrame":
                                    chunk.itemFrames++;
                            }

                            if (chunk.getTotal() < this.minimumStorageCount.get() && !this.meetsThresholds(chunk)) {
                                if (!existing) {
                                    this.pendingEntityChunks.put(chunkPos, chunk);
                                }
                            } else {
                                if (!existing) {
                                    this.chunks.add(chunk);
                                    this.pendingEntityChunks.remove(chunkPos);
                                }

                                if (this.renderTracer.get()) {
                                    double y = this.mc.player.getEyeY();
                                    this.tracerPositions.put(chunk.chunkPos, new Vec3(chunk.x, y, chunk.z));
                                }

                                this.save();
                                boolean isNew = prevChunk == null || !chunk.countsEqual(prevChunk);
                                if (isNew && this.meetsThresholds(chunk)) {
                                    this.dirtyStashes.add(chunk);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static StashFinder.StashChunk snapshot(StashFinder.StashChunk c) {
        StashFinder.StashChunk copy = new StashFinder.StashChunk(c.chunkPos);
        copy.dimension = c.dimension;
        copy.chests = c.chests;
        copy.trappedChests = c.trappedChests;
        copy.barrels = c.barrels;
        copy.shulkers = c.shulkers;
        copy.enderChests = c.enderChests;
        copy.furnaces = c.furnaces;
        copy.dispensersDroppers = c.dispensersDroppers;
        copy.hoppers = c.hoppers;
        copy.brewingStands = c.brewingStands;
        copy.crafters = c.crafters;
        copy.decoratedPots = c.decoratedPots;
        copy.banners = c.banners;
        copy.signs = c.signs;
        copy.hangingSigns = c.hangingSigns;
        copy.mapItemFrames = c.mapItemFrames;
        copy.itemFrames = c.itemFrames;
        copy.enderPearls = c.enderPearls;
        copy.namedEntities = c.namedEntities;
        return copy;
    }

    private boolean meetsThresholds(StashFinder.StashChunk chunk) {
        boolean allZero = this.minChests.get() == 0
            && this.minTrappedChests.get() == 0
            && this.minBarrels.get() == 0
            && this.minShulkers.get() == 0
            && this.minEnderChests.get() == 0
            && this.minFurnaces.get() == 0
            && this.minDispensers.get() == 0
            && this.minHoppers.get() == 0
            && this.minBrewingStands.get() == 0
            && this.minCrafters.get() == 0
            && this.minDecoratedPots.get() == 0
            && this.minBanners.get() == 0
            && this.minSigns.get() == 0
            && this.minHangingSigns.get() == 0
            && this.minMapItemFrames.get() == 0
            && this.minItemFrames.get() == 0
            && this.minEnderPearls.get() == 0
            && this.minNamedEntities.get() == 0;
        if (allZero) {
            return true;
        } else if (this.minChests.get() > 0 && chunk.chests >= this.minChests.get()) {
            return true;
        } else if (this.minTrappedChests.get() > 0 && chunk.trappedChests >= this.minTrappedChests.get()) {
            return true;
        } else if (this.minBarrels.get() > 0 && chunk.barrels >= this.minBarrels.get()) {
            return true;
        } else if (this.minShulkers.get() > 0 && chunk.shulkers >= this.minShulkers.get()) {
            return true;
        } else if (this.minEnderChests.get() > 0 && chunk.enderChests >= this.minEnderChests.get()) {
            return true;
        } else if (this.minFurnaces.get() > 0 && chunk.furnaces >= this.minFurnaces.get()) {
            return true;
        } else if (this.minDispensers.get() > 0 && chunk.dispensersDroppers >= this.minDispensers.get()) {
            return true;
        } else if (this.minHoppers.get() > 0 && chunk.hoppers >= this.minHoppers.get()) {
            return true;
        } else if (this.minBrewingStands.get() > 0 && chunk.brewingStands >= this.minBrewingStands.get()) {
            return true;
        } else if (this.minCrafters.get() > 0 && chunk.crafters >= this.minCrafters.get()) {
            return true;
        } else if (this.minDecoratedPots.get() > 0 && chunk.decoratedPots >= this.minDecoratedPots.get()) {
            return true;
        } else if (this.minBanners.get() > 0 && chunk.banners >= this.minBanners.get()) {
            return true;
        } else if (this.minSigns.get() > 0 && chunk.signs >= this.minSigns.get()) {
            return true;
        } else if (this.minHangingSigns.get() > 0 && chunk.hangingSigns >= this.minHangingSigns.get()) {
            return true;
        } else if (this.minMapItemFrames.get() > 0 && chunk.mapItemFrames >= this.minMapItemFrames.get()) {
            return true;
        } else if (this.minItemFrames.get() > 0 && chunk.itemFrames >= this.minItemFrames.get()) {
            return true;
        } else {
            return this.minEnderPearls.get() > 0 && chunk.enderPearls >= this.minEnderPearls.get()
                ? true
                : this.minNamedEntities.get() > 0 && chunk.namedEntities >= this.minNamedEntities.get();
        }
    }

    private boolean isNearBlacklistedBlock(BlockPos pos, List<Block> blacklist, boolean checkHorizontalAdjacent) {
        if (this.mc.level == null) {
            return false;
        }

        if (blacklist.contains(this.mc.level.getBlockState(pos.below()).getBlock())) {
            return true;
        }

        if (blacklist.contains(this.mc.level.getBlockState(pos.below(2)).getBlock())) {
            return true;
        }

        if (checkHorizontalAdjacent) {
            if (blacklist.contains(this.mc.level.getBlockState(pos.north()).getBlock())) {
                return true;
            }

            if (blacklist.contains(this.mc.level.getBlockState(pos.south()).getBlock())) {
                return true;
            }

            if (blacklist.contains(this.mc.level.getBlockState(pos.east()).getBlock())) {
                return true;
            }

            if (blacklist.contains(this.mc.level.getBlockState(pos.west()).getBlock())) {
                return true;
            }
        }

        return false;
    }

    private List<StashFinder.StashChunk> getCluster(StashFinder.StashChunk seed) {
        int radius = this.clusterRadius.get();
        List<StashFinder.StashChunk> cluster = new ArrayList<>();
        ArrayDeque<StashFinder.StashChunk> queue = new ArrayDeque<>();
        Set<ChunkPos> visited = new HashSet<>();
        queue.add(seed);
        visited.add(seed.chunkPos);

        while (!queue.isEmpty()) {
            StashFinder.StashChunk current = queue.poll();
            cluster.add(current);

            for (StashFinder.StashChunk c : this.chunks) {
                if (Objects.equals(c.dimension, seed.dimension)
                    && !visited.contains(c.chunkPos)
                    && Math.abs(c.chunkPos.x - current.chunkPos.x) <= radius
                    && Math.abs(c.chunkPos.z - current.chunkPos.z) <= radius) {
                    visited.add(c.chunkPos);
                    queue.add(c);
                }
            }
        }

        return cluster;
    }

    private void addWaypoint(StashFinder.StashChunk chunk, List<StashFinder.StashChunk> cluster) {
        if (XaeroWaypointManager.isAvailable() && this.addWaypoints.get()) {
            StashFinder.StashChunk mergedChunk = new StashFinder.StashChunk(chunk.chunkPos);
            mergedChunk.dimension = chunk.dimension;
            int bestTotal = -1;
            int minChunkX = Integer.MAX_VALUE;
            int maxChunkX = Integer.MIN_VALUE;
            int minChunkZ = Integer.MAX_VALUE;
            int maxChunkZ = Integer.MIN_VALUE;

            for (StashFinder.StashChunk c : cluster) {
                mergedChunk.chests = mergedChunk.chests + c.chests;
                mergedChunk.trappedChests = mergedChunk.trappedChests + c.trappedChests;
                mergedChunk.barrels = mergedChunk.barrels + c.barrels;
                mergedChunk.shulkers = mergedChunk.shulkers + c.shulkers;
                mergedChunk.enderChests = mergedChunk.enderChests + c.enderChests;
                mergedChunk.furnaces = mergedChunk.furnaces + c.furnaces;
                mergedChunk.dispensersDroppers = mergedChunk.dispensersDroppers + c.dispensersDroppers;
                mergedChunk.hoppers = mergedChunk.hoppers + c.hoppers;
                mergedChunk.brewingStands = mergedChunk.brewingStands + c.brewingStands;
                mergedChunk.crafters = mergedChunk.crafters + c.crafters;
                mergedChunk.decoratedPots = mergedChunk.decoratedPots + c.decoratedPots;
                mergedChunk.banners = mergedChunk.banners + c.banners;
                mergedChunk.signs = mergedChunk.signs + c.signs;
                mergedChunk.hangingSigns = mergedChunk.hangingSigns + c.hangingSigns;
                mergedChunk.mapItemFrames = mergedChunk.mapItemFrames + c.mapItemFrames;
                mergedChunk.itemFrames = mergedChunk.itemFrames + c.itemFrames;
                mergedChunk.enderPearls = mergedChunk.enderPearls + c.enderPearls;
                mergedChunk.namedEntities = mergedChunk.namedEntities + c.namedEntities;
                minChunkX = Math.min(minChunkX, c.chunkPos.x);
                maxChunkX = Math.max(maxChunkX, c.chunkPos.x);
                minChunkZ = Math.min(minChunkZ, c.chunkPos.z);
                maxChunkZ = Math.max(maxChunkZ, c.chunkPos.z);
                if (c.getTotal() > bestTotal) {
                    bestTotal = c.getTotal();
                    mergedChunk.x = c.x;
                    mergedChunk.z = c.z;
                }
            }

            String name;
            String initials;
            if (this.useSymbols.get()) {
                StringBuilder symbolBuilder = new StringBuilder();
                if (this.minChests.get() > 0 && mergedChunk.chests >= this.minChests.get()) {
                    symbolBuilder.append("\ud83d\udce6");
                }

                if (this.minTrappedChests.get() > 0 && mergedChunk.trappedChests >= this.minTrappedChests.get()) {
                    symbolBuilder.append("\ud83e\udea4");
                }

                if (this.minBarrels.get() > 0 && mergedChunk.barrels >= this.minBarrels.get()) {
                    symbolBuilder.append("\ud83d\udee2");
                }

                if (this.minShulkers.get() > 0 && mergedChunk.shulkers >= this.minShulkers.get()) {
                    symbolBuilder.append("\ud83c\udf81");
                }

                if (this.minEnderChests.get() > 0 && mergedChunk.enderChests >= this.minEnderChests.get()) {
                    symbolBuilder.append("☁");
                }

                if (this.minFurnaces.get() > 0 && mergedChunk.furnaces >= this.minFurnaces.get()) {
                    symbolBuilder.append("\ud83d\udd25");
                }

                if (this.minDispensers.get() > 0 && mergedChunk.dispensersDroppers >= this.minDispensers.get()) {
                    symbolBuilder.append("⬇");
                }

                if (this.minHoppers.get() > 0 && mergedChunk.hoppers >= this.minHoppers.get()) {
                    symbolBuilder.append("⏬");
                }

                if (this.minBrewingStands.get() > 0 && mergedChunk.brewingStands >= this.minBrewingStands.get()) {
                    symbolBuilder.append("\ud83e\uddea");
                }

                if (this.minCrafters.get() > 0 && mergedChunk.crafters >= this.minCrafters.get()) {
                    symbolBuilder.append("\ud83d\udd27");
                }

                if (this.minDecoratedPots.get() > 0 && mergedChunk.decoratedPots >= this.minDecoratedPots.get()) {
                    symbolBuilder.append("\ud83c\udffa");
                }

                if (this.minBanners.get() > 0 && mergedChunk.banners >= this.minBanners.get()) {
                    symbolBuilder.append("\ud83d\udea9");
                }

                if (this.minSigns.get() > 0 && mergedChunk.signs >= this.minSigns.get()) {
                    symbolBuilder.append("\ud83e\udea7");
                }

                if (this.minHangingSigns.get() > 0 && mergedChunk.hangingSigns >= this.minHangingSigns.get()) {
                    symbolBuilder.append("\ud83e\ude9d");
                }

                if (this.minMapItemFrames.get() > 0 && mergedChunk.mapItemFrames >= this.minMapItemFrames.get()) {
                    symbolBuilder.append("\ud83d\uddfa");
                }

                if (this.minItemFrames.get() > 0 && mergedChunk.itemFrames >= this.minItemFrames.get()) {
                    symbolBuilder.append("\ud83d\uddbc");
                }

                if (this.minEnderPearls.get() > 0 && mergedChunk.enderPearls >= this.minEnderPearls.get()) {
                    symbolBuilder.append("\ud83d\udc41");
                }

                if (this.minNamedEntities.get() > 0 && mergedChunk.namedEntities >= this.minNamedEntities.get()) {
                    symbolBuilder.append("\ud83c\udff7");
                }

                String symbols = symbolBuilder.toString();
                if (symbols.isEmpty()) {
                    symbols = "\ud83d\udce6";
                }

                initials = this.topSymbol(mergedChunk);
                if (this.showQuantity.get()) {
                    name = symbols + " " + mergedChunk.getTotal();
                } else {
                    name = symbols;
                }
            } else {
                initials = "S";
                if (this.showQuantity.get()) {
                    name = "Stash (" + mergedChunk.getTotal() + ")";
                } else {
                    name = "Stash";
                }
            }

            int margin = this.clusterRadius.get() * 16;
            XaeroWaypointManager.removeWaypointsInArea(
                "StashFinder",
                minChunkX * 16 - margin,
                minChunkZ * 16 - margin,
                maxChunkX * 16 + 15 + margin,
                maxChunkZ * 16 + 15 + margin,
                mergedChunk.dimension
            );
            XaeroWaypointManager.addWaypoint(
                "StashFinder",
                new BlockPos(mergedChunk.x, 64, mergedChunk.z),
                name,
                initials,
                this.waypointColor.get(),
                this.tempWaypoints.get(),
                mergedChunk.dimension
            );
        }
    }

    private String topSymbol(StashFinder.StashChunk c) {
        if (this.minShulkers.get() > 0 && c.shulkers >= this.minShulkers.get()) {
            return "\ud83c\udf81";
        } else if (this.minEnderChests.get() > 0 && c.enderChests >= this.minEnderChests.get()) {
            return "☁";
        } else if (this.minTrappedChests.get() > 0 && c.trappedChests >= this.minTrappedChests.get()) {
            return "\ud83e\udea4";
        } else if (this.minEnderPearls.get() > 0 && c.enderPearls >= this.minEnderPearls.get()) {
            return "\ud83d\udc41";
        } else if (this.minMapItemFrames.get() > 0 && c.mapItemFrames >= this.minMapItemFrames.get()) {
            return "\ud83d\uddfa";
        } else if (this.minCrafters.get() > 0 && c.crafters >= this.minCrafters.get()) {
            return "\ud83d\udd27";
        } else if (this.minBrewingStands.get() > 0 && c.brewingStands >= this.minBrewingStands.get()) {
            return "\ud83e\uddea";
        } else if (this.minHoppers.get() > 0 && c.hoppers >= this.minHoppers.get()) {
            return "⏬";
        } else if (this.minChests.get() > 0 && c.chests >= this.minChests.get()) {
            return "\ud83d\udce6";
        } else if (this.minBarrels.get() > 0 && c.barrels >= this.minBarrels.get()) {
            return "\ud83d\udee2";
        } else if (this.minDispensers.get() > 0 && c.dispensersDroppers >= this.minDispensers.get()) {
            return "⬇";
        } else if (this.minFurnaces.get() > 0 && c.furnaces >= this.minFurnaces.get()) {
            return "\ud83d\udd25";
        } else if (this.minDecoratedPots.get() > 0 && c.decoratedPots >= this.minDecoratedPots.get()) {
            return "\ud83c\udffa";
        } else if (this.minNamedEntities.get() > 0 && c.namedEntities >= this.minNamedEntities.get()) {
            return "\ud83c\udff7";
        } else if (this.minItemFrames.get() > 0 && c.itemFrames >= this.minItemFrames.get()) {
            return "\ud83d\uddbc";
        } else if (this.minHangingSigns.get() > 0 && c.hangingSigns >= this.minHangingSigns.get()) {
            return "\ud83e\ude9d";
        } else if (this.minSigns.get() > 0 && c.signs >= this.minSigns.get()) {
            return "\ud83e\udea7";
        } else {
            return this.minBanners.get() > 0 && c.banners >= this.minBanners.get() ? "\ud83d\udea9" : "\ud83d\udce6";
        }
    }

    private void sendDiscordNotification(StashFinder.StashChunk chunk) {
        if (this.discordEnabled.get() && !this.discordWebhookUrl.get().isEmpty()) {
            this.discordExecutor
                .submit(
                    () -> {
                        try {
                            StringBuilder content = new StringBuilder();
                            content.append("**\ud83d\uddc3️ Stash Found!**\\n");
                            content.append("**Total Containers:** ").append(chunk.getTotal()).append("\\n");
                            content.append("**Dimension:** ").append(chunk.dimension).append("\\n");
                            if (this.discordIncludeCoords.get()) {
                                content.append("**Coordinates:** `").append(chunk.x).append(", ").append(chunk.z).append("`\\n");
                            }

                            if (this.discordIncludeBreakdown.get()) {
                                content.append("\\n**Breakdown:**\\n");
                                if (chunk.chests > 0) {
                                    content.append("• Chests: ").append(chunk.chests).append("\\n");
                                }

                                if (chunk.trappedChests > 0) {
                                    content.append("• Trapped Chests: ").append(chunk.trappedChests).append("\\n");
                                }

                                if (chunk.barrels > 0) {
                                    content.append("• Barrels: ").append(chunk.barrels).append("\\n");
                                }

                                if (chunk.shulkers > 0) {
                                    content.append("• Shulkers: ").append(chunk.shulkers).append("\\n");
                                }

                                if (chunk.enderChests > 0) {
                                    content.append("• Ender Chests: ").append(chunk.enderChests).append("\\n");
                                }

                                if (chunk.furnaces > 0) {
                                    content.append("• Furnaces: ").append(chunk.furnaces).append("\\n");
                                }

                                if (chunk.dispensersDroppers > 0) {
                                    content.append("• Dispensers/Droppers: ").append(chunk.dispensersDroppers).append("\\n");
                                }

                                if (chunk.hoppers > 0) {
                                    content.append("• Hoppers: ").append(chunk.hoppers).append("\\n");
                                }

                                if (chunk.brewingStands > 0) {
                                    content.append("• Brewing Stands: ").append(chunk.brewingStands).append("\\n");
                                }

                                if (chunk.crafters > 0) {
                                    content.append("• Crafters: ").append(chunk.crafters).append("\\n");
                                }

                                if (chunk.decoratedPots > 0) {
                                    content.append("• Decorated Pots: ").append(chunk.decoratedPots).append("\\n");
                                }

                                if (chunk.banners > 0) {
                                    content.append("• Banners: ").append(chunk.banners).append("\\n");
                                }

                                if (chunk.signs > 0) {
                                    content.append("• Signs: ").append(chunk.signs).append("\\n");
                                }

                                if (chunk.hangingSigns > 0) {
                                    content.append("• Hanging Signs: ").append(chunk.hangingSigns).append("\\n");
                                }

                                if (chunk.mapItemFrames > 0) {
                                    content.append("• Map Item Frames: ").append(chunk.mapItemFrames).append("\\n");
                                }

                                if (chunk.itemFrames > 0) {
                                    content.append("• Item Frames: ").append(chunk.itemFrames).append("\\n");
                                }

                                if (chunk.enderPearls > 0) {
                                    content.append("• Ender Pearls: ").append(chunk.enderPearls).append("\\n");
                                }

                                if (chunk.namedEntities > 0) {
                                    content.append("• Named Entities: ").append(chunk.namedEntities).append("\\n");
                                }
                            }

                            String json = "{\"username\":\"" + this.discordUsername.get() + "\",\"content\":\"" + content + "\"}";
                            HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(this.discordWebhookUrl.get()))
                                .header("Content-Type", "application/json")
                                .POST(BodyPublishers.ofString(json))
                                .build();
                            this.httpClient.send(request, BodyHandlers.ofString());
                        } catch (Exception e) {
                            MeteorClient.LOG.error("Failed to send Discord webhook", e);
                        }
                    }
                );
        }
    }

    private void sendNotification(StashFinder.StashChunk chunk) {
        boolean hideCoords = this.noCoordChat.get() || BepConfig.streamerMode.get();
        String message = hideCoords ? "Found stash! (" + chunk.getTotal() + " containers)" : "Found stash at [" + chunk.x + ", " + chunk.z + "]";
        switch ((StashFinder.NotificationMode)this.notificationMode.get()) {
            case Chat:
                if (!hideCoords) {
                    ChatUtils.info("StashFinder", message);
                } else {
                    ChatUtils.info("StashFinder", "Found stash! Use secure menu to view coordinates.");
                }
                break;
            case Toast: {
                MeteorToast toast = new meteordevelopment.meteorclient.utils.render.MeteorToast.Builder(this.title)
                    .icon(Items.CHEST)
                    .text("Stash Found!")
                    .duration(5000L)
                    .build();
                this.mc.getToastManager().addToast(toast);
                break;
            }
            case Sound:
                this.playDingSound();
                break;
            case Both: {
                if (!hideCoords) {
                    ChatUtils.info("StashFinder", message);
                } else {
                    ChatUtils.info("StashFinder", "Found stash! Use secure menu to view coordinates.");
                }

                MeteorToast toast = new meteordevelopment.meteorclient.utils.render.MeteorToast.Builder(this.title)
                    .icon(Items.CHEST)
                    .text("Stash Found!")
                    .duration(5000L)
                    .build();
                this.mc.getToastManager().addToast(toast);
                break;
            }
            case All: {
                if (!hideCoords) {
                    ChatUtils.info("StashFinder", message);
                } else {
                    ChatUtils.info("StashFinder", "Found stash! Use secure menu to view coordinates.");
                }

                MeteorToast toast = new meteordevelopment.meteorclient.utils.render.MeteorToast.Builder(this.title)
                    .icon(Items.CHEST)
                    .text("Stash Found!")
                    .duration(5000L)
                    .build();
                this.mc.getToastManager().addToast(toast);
                this.playDingSound();
            }
            case Silent:
        }
    }

    private void playDingSound() {
        if (this.mc.player != null) {
            this.mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
            new Thread(() -> {
                try {
                    Thread.sleep(150L);
                    if (this.mc.player != null) {
                        this.mc.execute(() -> this.mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F));
                    }

                    Thread.sleep(150L);
                    if (this.mc.player != null) {
                        this.mc.execute(() -> this.mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.4F));
                    }
                } catch (InterruptedException var2) {
                }
            }).start();
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        this.ensureLoaded();
        WVerticalList list = theme.verticalList();
        WHorizontalList stats = theme.horizontalList();
        stats.add(theme.label("Total Stashes: " + this.chunks.size()));
        list.add(stats);
        WHorizontalList buttons = theme.horizontalList();
        WButton openSecure = buttons.add(theme.button("Open Coordinate List")).widget();
        openSecure.action = this::openCoordinateList;
        WButton clearAll = buttons.add(theme.button("Clear All")).widget();
        clearAll.action = () -> {
            this.chunks.clear();
            this.tracerPositions.clear();
            this.countedEntityUuids.clear();
            this.pendingEntityChunks.clear();
            this.loaded = true;
            this.save();
        };
        WButton resetTracers = buttons.add(theme.button("Reset Tracers")).widget();
        resetTracers.action = () -> this.tracerPositions.clear();
        WButton testEmojis = buttons.add(theme.button("Test Emojis")).widget();
        testEmojis.action = this::createTestWaypoints;
        list.add(buttons);
        if (!this.chunks.isEmpty()) {
            WTable dimTable = theme.table();
            Map<String, Integer> dimCounts = new HashMap<>();

            for (StashFinder.StashChunk chunk : this.chunks) {
                dimCounts.merge(chunk.dimension, 1, Integer::sum);
            }

            for (Entry<String, Integer> entry : dimCounts.entrySet()) {
                dimTable.add(theme.label(entry.getKey() + ":")).padRight(10.0);
                dimTable.add(theme.label(entry.getValue() + " stashes"));
                dimTable.row();
            }

            list.add(dimTable);
        }

        if (this.hideCoordinates.get()) {
            list.add(theme.label("Coordinates hidden. Use coordinate list to view.").color(theme.textSecondaryColor()));
        }

        return list;
    }

    private String coordText(int x, int z) {
        return BepConfig.streamerMode.get() ? "*****, *****" : x + ", " + z;
    }

    private void openCoordinateList() {
        this.ensureLoaded();
        GuiTheme theme = GuiThemes.get();
        this.mc.setScreen(new StashFinder.CoordinateListScreen(theme));
    }

    private void createTestWaypoints() {
        if (this.mc.player == null) {
            this.error("Player not found");
        } else if (!Utils.XAERO_AVAILABLE) {
            this.error("Xaero's Minimap not available");
        } else {
            int x = (int)this.mc.player.getX();
            int y = (int)this.mc.player.getY();
            int z = (int)this.mc.player.getZ();
            String dimension = this.mc.level != null ? this.mc.level.dimension().identifier().toString() : "minecraft:overworld";
            String[][] testSymbols = new String[][]{
                {"\ud83d\udce6", "Chest"},
                {"\ud83e\udea4", "TrappedChest"},
                {"\ud83d\udee2", "Barrel"},
                {"\ud83c\udf81", "Shulker"},
                {"☁", "EnderChest"},
                {"\ud83d\udd25", "Furnace"},
                {"⬇", "Dispenser"},
                {"⏬", "Hopper"},
                {"\ud83e\uddea", "BrewingStand"},
                {"\ud83d\udd27", "Crafter"},
                {"\ud83c\udffa", "DecoratedPot"},
                {"\ud83d\udea9", "Banner"},
                {"\ud83e\udea7", "Sign"},
                {"\ud83e\ude9d", "HangingSign"},
                {"\ud83d\uddfa", "MapItemFrame"}
            };
            int offset = 0;

            for (String[] symbolData : testSymbols) {
                String waypointName = symbolData[0] + " " + symbolData[1];
                XaeroWaypointManager.addWaypoint(
                    "StashFinder", new BlockPos(x + offset, y, z), waypointName, symbolData[0], this.waypointColor.get(), true, dimension
                );
                offset += 16;
            }

            this.info("Created %d test waypoints at your location. Check which emojis render correctly!", testSymbols.length);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!this.tracerPositions.isEmpty() && this.mc.player != null) {
            double playerX = this.mc.player.getX();
            double playerZ = this.mc.player.getZ();
            this.tracerPositions.entrySet().removeIf(entry -> {
                Vec3 posx = entry.getValue();
                double distx = Math.hypot(posx.x - playerX, posx.z - playerZ);
                return distx <= this.traceArrivalDistance.get().intValue();
            });
            if (this.renderTracer.get() || this.renderChunkColumn.get()) {
                for (Vec3 pos : this.tracerPositions.values()) {
                    double dist = Math.hypot(pos.x - playerX, pos.z - playerZ);
                    if (!(dist > this.traceMaxDistance.get().intValue())) {
                        if (this.renderTracer.get()) {
                            event.renderer
                                .line(
                                    RenderUtils.center.x,
                                    RenderUtils.center.y,
                                    RenderUtils.center.z,
                                    pos.x,
                                    pos.y,
                                    pos.z,
                                    this.traceColor.get()
                                );
                        }

                        if (this.renderChunkColumn.get()) {
                            int chunkX = (int)pos.x - 8 >> 4 << 4;
                            int chunkZ = (int)pos.z - 8 >> 4 << 4;
                            double x1 = chunkX;
                            double x2 = chunkX + 16;
                            double z1 = chunkZ;
                            double z2 = chunkZ + 16;
                            int bottomY = this.mc.level.getMinY();
                            int topY = bottomY + this.mc.level.dimensionType().height();
                            event.renderer.line(x1, bottomY, z1, x1, topY, z1, this.columnColor.get());
                            event.renderer.line(x1, bottomY, z2, x1, topY, z2, this.columnColor.get());
                            event.renderer.line(x2, bottomY, z1, x2, topY, z1, this.columnColor.get());
                            event.renderer.line(x2, bottomY, z2, x2, topY, z2, this.columnColor.get());
                            event.renderer.line(x1, bottomY, z1, x2, bottomY, z1, this.columnColor.get());
                            event.renderer.line(x1, bottomY, z1, x1, bottomY, z2, this.columnColor.get());
                            event.renderer.line(x2, bottomY, z2, x1, bottomY, z2, this.columnColor.get());
                            event.renderer.line(x2, bottomY, z2, x2, bottomY, z1, this.columnColor.get());
                            event.renderer.line(x1, topY, z1, x2, topY, z1, this.columnColor.get());
                            event.renderer.line(x1, topY, z1, x1, topY, z2, this.columnColor.get());
                            event.renderer.line(x2, topY, z2, x1, topY, z2, this.columnColor.get());
                            event.renderer.line(x2, topY, z2, x2, topY, z1, this.columnColor.get());
                        }
                    }
                }
            }
        }
    }

    private String getCurrentDimension() {
        return this.mc.level == null ? "unknown" : this.mc.level.dimension().identifier().getPath();
    }

    private void ensureLoaded() {
        if (!this.loaded) {
            Map<StashFinder.StashChunk, StashFinder.StashChunk> byPos = new LinkedHashMap<>();

            for (StashFinder.StashChunk c : this.readFromDisk()) {
                byPos.put(c, c);
            }

            for (StashFinder.StashChunk c : this.chunks) {
                byPos.put(c, c);
            }

            this.chunks = new ArrayList<>(byPos.values());
            this.loaded = true;
        }
    }

    private List<StashFinder.StashChunk> readFromDisk() {
        File file = this.getJsonFile();
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(file)) {
            List<StashFinder.StashChunk> list = GSON.fromJson(reader, (new TypeToken<List<StashFinder.StashChunk>>() {}).getType());
            if (list == null) {
                return new ArrayList<>();
            }

            for (StashFinder.StashChunk chunk : list) {
                chunk.calculatePos();
                if (chunk.dimension == null) {
                    chunk.dimension = "overworld";
                }
            }

            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void writeToDisk(List<StashFinder.StashChunk> list) {
        try {
            File file = this.getJsonFile();
            file.getParentFile().mkdirs();

            try (Writer writer = new FileWriter(file)) {
                GSON.toJson(list, writer);
            }
        } catch (IOException e) {
            MeteorClient.LOG.error("Error saving stash list", e);
        }
    }

    private void save() {
        List<StashFinder.StashChunk> snapshot = new ArrayList<>(this.chunks);
        boolean full = this.loaded;
        this.ioExecutor.submit(() -> {
            List<StashFinder.StashChunk> toWrite;
            if (full) {
                toWrite = snapshot;
            } else {
                Map<StashFinder.StashChunk, StashFinder.StashChunk> byPos = new LinkedHashMap<>();

                for (StashFinder.StashChunk c : this.readFromDisk()) {
                    byPos.put(c, c);
                }

                for (StashFinder.StashChunk c : snapshot) {
                    byPos.put(c, c);
                }

                toWrite = new ArrayList<>(byPos.values());
            }

            this.writeToDisk(toWrite);
        });
    }

    private File getJsonFile() {
        return new File(new File(new File(MeteorClient.FOLDER, "stashes"), meteordevelopment.meteorclient.utils.Utils.getFileWorldName()), "stashes-bep.json");
    }

    @Override
    public String getInfoString() {
        return String.valueOf(this.chunks.size());
    }

    private class ChunkDetailScreen extends WindowScreen {
        private final StashFinder.StashChunk chunk;
        private static final Color GOLD = new Color(255, 170, 0);
        private static final Color YELLOW = new Color(255, 255, 85);
        private static final Color RED = new Color(255, 85, 85);
        private static final Color MAGENTA = new Color(255, 85, 255);
        private static final Color DARK_PURPLE = new Color(170, 0, 170);
        private static final Color GRAY = new Color(170, 170, 170);
        private static final Color CYAN = new Color(85, 255, 255);
        private static final Color GREEN = new Color(85, 255, 85);
        private static final Color BLUE = new Color(85, 85, 255);

        public ChunkDetailScreen(GuiTheme theme, StashFinder.StashChunk chunk) {
            super(theme, "Stash Details");
            this.chunk = chunk;
        }

        @Override
        public void initWidgets() {
            WTable t = this.<WTable>add(this.theme.table()).expandX().widget();
            t.add(this.theme.label("Coordinates:").color(GOLD));
            t.add(this.theme.label(StashFinder.this.coordText(this.chunk.x, this.chunk.z)));
            t.row();
            t.add(this.theme.label("Dimension:").color(GOLD));
            t.add(this.theme.label(this.chunk.dimension));
            t.row();
            t.add(this.theme.horizontalSeparator()).expandX();
            t.row();
            t.add(this.theme.label("Total:").color(GOLD));
            t.add(this.theme.label(String.valueOf(this.chunk.getTotal())));
            t.row();
            t.add(this.theme.horizontalSeparator()).expandX();
            t.row();
            this.addCountRow(t, "\ud83d\udce6 Chests", this.chunk.chests, YELLOW);
            this.addCountRow(t, "\ud83d\udca3 Trapped Chests", this.chunk.trappedChests, RED);
            this.addCountRow(t, "\ud83d\udee2️ Barrels", this.chunk.barrels, GOLD);
            this.addCountRow(t, "\ud83d\udfea Shulkers", this.chunk.shulkers, MAGENTA);
            this.addCountRow(t, "\ud83d\udc41️ Ender Chests", this.chunk.enderChests, DARK_PURPLE);
            this.addCountRow(t, "\ud83d\udd25 Furnaces", this.chunk.furnaces, GRAY);
            this.addCountRow(t, "⬇️ Dispensers/Droppers", this.chunk.dispensersDroppers, GRAY);
            this.addCountRow(t, "⤵️ Hoppers", this.chunk.hoppers, GRAY);
            this.addCountRow(t, "⚗️ Brewing Stands", this.chunk.brewingStands, CYAN);
            this.addCountRow(t, "⚙️ Crafters", this.chunk.crafters, GREEN);
            this.addCountRow(t, "\ud83c\udffa Decorated Pots", this.chunk.decoratedPots, GOLD);
            this.addCountRow(t, "\ud83d\udea9 Banners", this.chunk.banners, RED);
            this.addCountRow(t, "\ud83e\udea7 Signs", this.chunk.signs, GOLD);
            this.addCountRow(t, "\ud83e\ude9d Hanging Signs", this.chunk.hangingSigns, GOLD);
            this.addCountRow(t, "\ud83d\uddfa️ Map Item Frames", this.chunk.mapItemFrames, BLUE);
            this.add(this.theme.horizontalSeparator()).expandX();
            WHorizontalList buttons = this.<WHorizontalList>add(this.theme.horizontalList()).widget();
            WButton copyBtn = buttons.add(this.theme.button("Copy Coordinates")).widget();
            copyBtn.action = () -> {
                StashFinder.this.mc.keyboardHandler.setClipboard(this.chunk.x + " " + this.chunk.z);
                if (BepConfig.streamerMode.get()) {
                    StashFinder.this.info("Copied coordinates to clipboard.");
                } else {
                    StashFinder.this.info("Copied coordinates: (highlight)%d %d", this.chunk.x, this.chunk.z);
                }
            };
            if (BaritoneHelper.isAvailable()) {
                WButton flyBtn = buttons.add(this.theme.button("Fly Here")).widget();
                flyBtn.action = () -> {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("goal " + this.chunk.x + " " + this.chunk.z);
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("elytra");
                    this.onClose();
                };
            }

            WButton back = buttons.add(this.theme.button("Back")).widget();
            back.action = this::onClose;
        }

        private void addCountRow(WTable t, String name, int count, Color color) {
            if (count > 0) {
                t.add(this.theme.label(name + ":"));
                t.add(this.theme.label(String.valueOf(count)).color(color));
                t.row();
            }
        }
    }

    private class CoordinateListScreen extends WindowScreen {
        public CoordinateListScreen(GuiTheme theme) {
            super(theme, "Stash Coordinates");
        }

        @Override
        public void initWidgets() {
            StashFinder.this.chunks.sort(Comparator.comparingInt(c -> -c.getTotal()));
            this.add(this.theme.label("⚠ Coordinates - Do not share! ⚠").color(new Color(255, 170, 0)));
            this.add(this.theme.horizontalSeparator()).expandX();
            if (StashFinder.this.chunks.isEmpty()) {
                this.add(this.theme.label("No stashes found yet."));
            } else {
                WHorizontalList filterRow = this.<WHorizontalList>add(this.theme.horizontalList()).widget();
                filterRow.add(this.theme.label("Filter:"));
                WButton allBtn = filterRow.add(this.theme.button("All")).widget();
                WButton owBtn = filterRow.add(this.theme.button("Overworld")).widget();
                WButton netherBtn = filterRow.add(this.theme.button("Nether")).widget();
                WButton endBtn = filterRow.add(this.theme.button("End")).widget();
                WTable table = this.<WTable>add(this.theme.table()).expandX().widget();
                Runnable refreshAll = () -> this.fillTable(table, null);
                Runnable refreshOW = () -> this.fillTable(table, "overworld");
                Runnable refreshNether = () -> this.fillTable(table, "the_nether");
                Runnable refreshEnd = () -> this.fillTable(table, "the_end");
                allBtn.action = refreshAll;
                owBtn.action = refreshOW;
                netherBtn.action = refreshNether;
                endBtn.action = refreshEnd;
                this.fillTable(table, null);
            }
        }

        private void fillTable(WTable table, String dimensionFilter) {
            table.clear();
            table.add(this.theme.label("Coordinates", true)).padRight(10.0);
            table.add(this.theme.label("Dim", true)).padRight(5.0);
            table.add(this.theme.label("Total", true)).padRight(10.0);
            table.add(this.theme.label("Actions", true));
            table.row();

            for (StashFinder.StashChunk chunk : StashFinder.this.chunks) {
                if (dimensionFilter == null || chunk.dimension.equals(dimensionFilter)) {
                    table.add(this.theme.label(StashFinder.this.coordText(chunk.x, chunk.z))).padRight(10.0);

                    String dimText = switch (chunk.dimension) {
                        case "overworld" -> "OW";
                        case "the_nether" -> "Neth";
                        case "the_end" -> "End";
                        default -> "?";
                    };

                    Color dimColor = switch (chunk.dimension) {
                        case "overworld" -> new Color(85, 255, 85);
                        case "the_nether" -> new Color(255, 85, 85);
                        case "the_end" -> new Color(255, 85, 255);
                        default -> new Color(170, 170, 170);
                    };
                    table.add(this.theme.label(dimText).color(dimColor)).padRight(5.0);
                    table.add(this.theme.label(String.valueOf(chunk.getTotal()))).padRight(10.0);
                    WHorizontalList actions = this.theme.horizontalList();
                    WButton details = actions.add(this.theme.button("Info")).widget();
                    details.action = () -> StashFinder.this.mc.setScreen(StashFinder.this.new ChunkDetailScreen(this.theme, chunk));
                    WButton copy = actions.add(this.theme.button("Copy")).widget();
                    copy.action = () -> {
                        StashFinder.this.mc.keyboardHandler.setClipboard(chunk.x + " " + chunk.z);
                        if (BepConfig.streamerMode.get()) {
                            StashFinder.this.info("Copied coordinates to clipboard.");
                        } else {
                            StashFinder.this.info("Copied coordinates: (highlight)%d %d", chunk.x, chunk.z);
                        }
                    };
                    actions.add(this.theme.label("Trace:"));
                    WCheckbox tracer = actions.add(this.theme.checkbox(StashFinder.this.tracerPositions.containsKey(chunk.chunkPos))).widget();
                    tracer.action = () -> {
                        if (tracer.checked) {
                            double y = StashFinder.this.mc.player != null ? StashFinder.this.mc.player.getEyeY() : 64.0;
                            StashFinder.this.tracerPositions.put(chunk.chunkPos, new Vec3(chunk.x, y, chunk.z));
                        } else {
                            StashFinder.this.tracerPositions.remove(chunk.chunkPos);
                        }
                    };
                    WButton gotoBtn = actions.add(this.theme.button("Go")).widget();
                    gotoBtn.action = () -> PathManagers.get().moveTo(new BlockPos(chunk.x, 64, chunk.z), true);
                    WMinus delete = actions.add(this.theme.minus()).widget();
                    delete.action = () -> {
                        StashFinder.this.chunks.remove(chunk);
                        StashFinder.this.tracerPositions.remove(chunk.chunkPos);
                        StashFinder.this.countedEntityUuids.remove(chunk.chunkPos);
                        StashFinder.this.save();
                        this.fillTable(table, dimensionFilter);
                    };
                    table.add(actions);
                    table.row();
                }
            }
        }
    }

    public enum NotificationMode {
        Chat,
        Toast,
        Sound,
        Both,
        All,
        Silent;
    }

    public static class StashChunk {
        public ChunkPos chunkPos;
        public transient int x;
        public transient int z;
        public String dimension = "overworld";
        public int chests;
        public int trappedChests;
        public int barrels;
        public int shulkers;
        public int enderChests;
        public int furnaces;
        public int dispensersDroppers;
        public int hoppers;
        public int brewingStands;
        public int crafters;
        public int decoratedPots;
        public int banners;
        public int signs;
        public int hangingSigns;
        public int mapItemFrames;
        public int itemFrames;
        public int enderPearls;
        public int namedEntities;

        public StashChunk(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
            this.calculatePos();
        }

        public void calculatePos() {
            this.x = this.chunkPos.x * 16 + 8;
            this.z = this.chunkPos.z * 16 + 8;
        }

        public int getTotal() {
            return this.chests
                + this.trappedChests
                + this.barrels
                + this.shulkers
                + this.enderChests
                + this.furnaces
                + this.dispensersDroppers
                + this.hoppers
                + this.brewingStands
                + this.crafters
                + this.decoratedPots
                + this.banners
                + this.signs
                + this.hangingSigns
                + this.mapItemFrames
                + this.itemFrames
                + this.enderPearls
                + this.namedEntities;
        }

        public boolean countsEqual(StashFinder.StashChunk c) {
            return c == null
                ? false
                : this.chests == c.chests
                    && this.trappedChests == c.trappedChests
                    && this.barrels == c.barrels
                    && this.shulkers == c.shulkers
                    && this.enderChests == c.enderChests
                    && this.furnaces == c.furnaces
                    && this.dispensersDroppers == c.dispensersDroppers
                    && this.hoppers == c.hoppers
                    && this.brewingStands == c.brewingStands
                    && this.crafters == c.crafters
                    && this.decoratedPots == c.decoratedPots
                    && this.banners == c.banners
                    && this.signs == c.signs
                    && this.hangingSigns == c.hangingSigns
                    && this.mapItemFrames == c.mapItemFrames
                    && this.itemFrames == c.itemFrames
                    && this.enderPearls == c.enderPearls
                    && this.namedEntities == c.namedEntities;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o != null && this.getClass() == o.getClass()) {
                StashFinder.StashChunk that = (StashFinder.StashChunk)o;
                return Objects.equals(this.chunkPos, that.chunkPos) && Objects.equals(this.dimension, that.dimension);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.chunkPos, this.dimension);
        }
    }
}
