package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.MapUtil;
import bep.hax.util.MsgUtil;
import bep.hax.util.Utils;
import bep.hax.util.XaeroWaypointManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class ODMob extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgNetherMobs = this.settings.createGroup("Nether Mobs");
    private final SettingGroup sgOverworldMobs = this.settings.createGroup("Overworld Mobs");
    private final SettingGroup sgEndMobs = this.settings.createGroup("End Mobs");
    private final SettingGroup sgNotifications = this.settings.createGroup("Notifications");
    private final SettingGroup sgWaypoints = this.settings.createGroup("Waypoints");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Boolean> detectInNether = this.sgGeneral
        .add(new Builder().name("detect-in-nether").description("Detect out-of-dimension mobs when in the Nether.").defaultValue(true).build());
    private final Setting<Boolean> detectInOverworld = this.sgGeneral
        .add(new Builder().name("detect-in-overworld").description("Detect out-of-dimension mobs when in the Overworld.").defaultValue(true).build());
    private final Setting<Boolean> detectInEnd = this.sgGeneral
        .add(new Builder().name("detect-in-end").description("Detect out-of-dimension mobs when in the End.").defaultValue(true).build());
    private final Setting<Set<EntityType<?>>> netherMobs = this.sgNetherMobs
        .add(
            new meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder()
                .name("nether-mobs")
                .description("Nether mobs to detect when found outside the Nether.")
                .defaultValue(
                    EntityType.PIGLIN,
                    EntityType.ZOMBIFIED_PIGLIN,
                    EntityType.PIGLIN_BRUTE,
                    EntityType.HOGLIN,
                    EntityType.ZOGLIN,
                    EntityType.BLAZE,
                    EntityType.GHAST,
                    EntityType.MAGMA_CUBE,
                    EntityType.WITHER_SKELETON,
                    EntityType.STRIDER
                )
                .build()
        );
    private final Setting<Set<EntityType<?>>> overworldMobs = this.sgOverworldMobs
        .add(
            new meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder()
                .name("overworld-mobs")
                .description("Overworld mobs to detect when found outside the Overworld.")
                .defaultValue(
                    EntityType.COW,
                    EntityType.PIG,
                    EntityType.SHEEP,
                    EntityType.CHICKEN,
                    EntityType.HORSE,
                    EntityType.DONKEY,
                    EntityType.MULE,
                    EntityType.LLAMA,
                    EntityType.FOX,
                    EntityType.WOLF,
                    EntityType.CAT,
                    EntityType.OCELOT,
                    EntityType.PARROT,
                    EntityType.RABBIT,
                    EntityType.POLAR_BEAR,
                    EntityType.PANDA,
                    EntityType.BEE,
                    EntityType.VILLAGER,
                    EntityType.IRON_GOLEM,
                    EntityType.SNOW_GOLEM,
                    EntityType.SQUID,
                    EntityType.GLOW_SQUID,
                    EntityType.DOLPHIN,
                    EntityType.TURTLE,
                    EntityType.COD,
                    EntityType.SALMON,
                    EntityType.PUFFERFISH,
                    EntityType.TROPICAL_FISH,
                    EntityType.AXOLOTL,
                    EntityType.BAT,
                    EntityType.FROG,
                    EntityType.TADPOLE,
                    EntityType.GOAT,
                    EntityType.ALLAY,
                    EntityType.CAMEL,
                    EntityType.SNIFFER,
                    EntityType.ARMADILLO
                )
                .build()
        );
    private final Setting<Set<EntityType<?>>> endMobs = this.sgEndMobs
        .add(
            new meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder()
                .name("end-mobs")
                .description("End mobs to detect when found outside the End.")
                .defaultValue(EntityType.ENDERMITE, EntityType.SHULKER, EntityType.ENDER_DRAGON)
                .build()
        );
    private final Setting<Boolean> chatNotification = this.sgNotifications
        .add(new Builder().name("chat-notification").description("Send a chat message when out-of-dimension mobs are detected.").defaultValue(true).build());
    private final Setting<Boolean> showCoords = this.sgNotifications
        .add(
            new Builder()
                .name("show-coords")
                .description("Show coordinates in chat notifications.")
                .defaultValue(true)
                .visible(this.chatNotification::get)
                .build()
        );
    private final Setting<Boolean> soundNotification = this.sgNotifications
        .add(new Builder().name("sound-notification").description("Play a sound when out-of-dimension mobs are detected.").defaultValue(true).build());
    private final Setting<Double> soundVolume = this.sgNotifications
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("volume")
                .description("Volume of the notification sound.")
                .defaultValue(1.0)
                .min(0.0)
                .max(5.0)
                .sliderMin(0.0)
                .sliderMax(2.0)
                .visible(this.soundNotification::get)
                .build()
        );
    private final Setting<Boolean> addWaypoints = this.sgWaypoints
        .add(
            new Builder()
                .name("add-waypoints")
                .description("Add Xaero waypoints for detected out-of-dimension mobs.")
                .defaultValue(true)
                .visible(() -> Utils.XAERO_AVAILABLE)
                .build()
        );
    private final Setting<Boolean> tempWaypoints = this.sgWaypoints
        .add(
            new Builder()
                .name("temporary-waypoints")
                .description("Temporary waypoints are removed when you disconnect.")
                .defaultValue(true)
                .visible(() -> Utils.XAERO_AVAILABLE && this.addWaypoints.get())
                .build()
        );
    private final Setting<String> waypointEmoji = this.sgWaypoints
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("waypoint-emoji")
                .description("Emoji/symbol to use for waypoint display.")
                .defaultValue("\ud83c\udf00")
                .visible(() -> Utils.XAERO_AVAILABLE && this.addWaypoints.get())
                .build()
        );
    private final Setting<MapUtil.WpColor> waypointColor = this.sgWaypoints
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                                .name("waypoint-color"))
                            .description("Color of out-of-dimension mob waypoints."))
                        .defaultValue(MapUtil.WpColor.Purple))
                    .visible(() -> Utils.XAERO_AVAILABLE && this.addWaypoints.get()))
                .build()
        );
    private final Setting<SettingColor> fillColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("side-color")
                .description("Fill color for out-of-dimension mobs.")
                .defaultValue(new SettingColor(255, 100, 255, 50))
                .build()
        );
    private final Setting<SettingColor> outlineColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("line-color")
                .description("Outline color for out-of-dimension mobs.")
                .defaultValue(new SettingColor(255, 150, 255, 255))
                .build()
        );
    private final Setting<Boolean> renderFill = this.sgRender
        .add(new Builder().name("render-sides").description("Render sides of out-of-dimension mobs.").defaultValue(true).build());
    private final Setting<Boolean> renderOutline = this.sgRender
        .add(new Builder().name("render-lines").description("Render lines of out-of-dimension mobs.").defaultValue(true).build());
    private final Setting<Boolean> renderTracer = this.sgRender
        .add(new Builder().name("tracers").description("Add tracers to out-of-dimension mobs.").defaultValue(true).build());
    private final Setting<SettingColor> tracerColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("tracer-color")
                .description("Tracer color for out-of-dimension mobs.")
                .defaultValue(new SettingColor(255, 150, 255, 125))
                .build()
        );
    private final Set<Integer> notifiedEntities = new HashSet<>();
    private final Map<BlockPos, String> waypointPositions = new HashMap<>();
    private static final String CATEGORY = "ODMob";

    public ODMob() {
        super(Bep.HUNT_CATEGORY, "ODMob", "Detects out-of-dimension mobs that came through portals.");
    }

    @Override
    public void onActivate() {
        this.notifiedEntities.clear();
        this.waypointPositions.clear();
        XaeroWaypointManager.resetCategoryTracking("ODMob");
    }

    @Override
    public void onDeactivate() {
        this.notifiedEntities.clear();
        this.waypointPositions.clear();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.level != null && this.mc.player != null) {
            ShapeMode shapeMode = this.getShapeMode();
            ResourceKey<Level> currentDimension = this.mc.level.dimension();
            if (currentDimension != Level.NETHER || this.detectInNether.get()) {
                if (currentDimension != Level.OVERWORLD || this.detectInOverworld.get()) {
                    if (currentDimension != Level.END || this.detectInEnd.get()) {
                        Set<Integer> currentODMobs = new HashSet<>();
                        SettingColor fill = this.fillColor.get();
                        SettingColor outline = this.outlineColor.get();
                        boolean tracer = this.renderTracer.get();
                        SettingColor tracerCol = this.tracerColor.get();

                        for (Entity entity : this.mc.level.entitiesForRendering()) {
                            if (entity instanceof LivingEntity && entity != this.mc.player) {
                                EntityType<?> type = entity.getType();
                                boolean isOutOfDimension = false;
                                String originDimension = null;
                                if (currentDimension == Level.NETHER) {
                                    if (this.overworldMobs.get().contains(type)) {
                                        isOutOfDimension = true;
                                        originDimension = "Overworld";
                                    } else if (this.endMobs.get().contains(type)) {
                                        isOutOfDimension = true;
                                        originDimension = "End";
                                    }

                                    if (isOutOfDimension && type == EntityType.CHICKEN && this.isNaturalChickenJockey(entity)) {
                                        isOutOfDimension = false;
                                    }
                                } else if (currentDimension == Level.OVERWORLD) {
                                    if (this.netherMobs.get().contains(type)) {
                                        isOutOfDimension = true;
                                        originDimension = "Nether";
                                    } else if (this.endMobs.get().contains(type)) {
                                        isOutOfDimension = true;
                                        originDimension = "End";
                                    }
                                } else if (currentDimension == Level.END) {
                                    if (this.overworldMobs.get().contains(type)) {
                                        isOutOfDimension = true;
                                        originDimension = "Overworld";
                                    } else if (this.netherMobs.get().contains(type)) {
                                        isOutOfDimension = true;
                                        originDimension = "Nether";
                                    }
                                }

                                if (isOutOfDimension) {
                                    currentODMobs.add(entity.getId());
                                    AABB box = entity.getBoundingBox();
                                    if (shapeMode != null) {
                                        event.renderer.box(box, fill, outline, shapeMode, 0);
                                    }

                                    if (tracer) {
                                        event.renderer
                                            .line(
                                                RenderUtils.center.x,
                                                RenderUtils.center.y,
                                                RenderUtils.center.z,
                                                box.getCenter().x,
                                                box.getCenter().y,
                                                box.getCenter().z,
                                                tracerCol
                                            );
                                    }

                                    if (!this.notifiedEntities.contains(entity.getId())) {
                                        this.notifiedEntities.add(entity.getId());
                                        String entityName = this.getEntityDisplayName(type);
                                        if (this.chatNotification.get()) {
                                            String message;
                                            if (this.showCoords.get()) {
                                                BlockPos pos = entity.blockPosition();
                                                message = String.format(
                                                    "§d§oFound %s from %s at §8[§7§o%d§8, §7§o%d§8, §7§o%d§8]",
                                                    entityName,
                                                    originDimension,
                                                    pos.getX(),
                                                    pos.getY(),
                                                    pos.getZ()
                                                );
                                            } else {
                                                message = String.format("§d§oFound %s from %s§7§o!", entityName, originDimension);
                                            }

                                            MsgUtil.sendModuleMsg(message, this.name);
                                        }

                                        if (this.soundNotification.get()) {
                                            this.mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), this.soundVolume.get().floatValue(), 1.5F);
                                        }

                                        if (Utils.XAERO_AVAILABLE && this.addWaypoints.get()) {
                                            BlockPos pos = entity.blockPosition();
                                            boolean hasNearbyWaypoint = this.waypointPositions
                                                .keySet()
                                                .stream()
                                                .anyMatch(existingPos -> existingPos.closerThan(pos, 5.0));
                                            if (!hasNearbyWaypoint) {
                                                String wpName = entityName + " from " + originDimension;
                                                XaeroWaypointManager.addWaypoint(
                                                    "ODMob", pos, wpName, this.waypointEmoji.get(), this.waypointColor.get(), this.tempWaypoints.get()
                                                );
                                                this.waypointPositions.put(pos, wpName);
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        this.notifiedEntities.retainAll(currentODMobs);
                    }
                }
            }
        }
    }

    private String getEntityDisplayName(EntityType<?> type) {
        String translationKey = type.getDescriptionId();
        String[] parts = translationKey.split("\\.");
        if (parts.length > 0) {
            String name = parts[parts.length - 1];
            String[] words = name.split("_");
            StringBuilder result = new StringBuilder();

            for (String word : words) {
                if (!word.isEmpty()) {
                    result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase()).append(" ");
                }
            }

            return result.toString().trim();
        } else {
            return translationKey;
        }
    }

    private boolean isNaturalChickenJockey(Entity chicken) {
        for (Entity passenger : chicken.getPassengers()) {
            if (passenger.getType() == EntityType.ZOMBIFIED_PIGLIN) {
                return true;
            }
        }

        return false;
    }

    private ShapeMode getShapeMode() {
        return bep.hax.util.RenderUtils.shapeMode(this.renderFill.get(), this.renderOutline.get());
    }
}
