package bep.hax.hud;

import bep.hax.Bep;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;

public class EntityList extends HudElement {
    public static final HudElementInfo<EntityList> INFO = new HudElementInfo<>(
        Bep.HUD_GROUP, "EntityList", "Displays nearby entities in a list.", EntityList::new
    );
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> showTitle = this.sgGeneral
        .add(new Builder().name("show-title").description("Display the HUD title.").defaultValue(true).build());
    private final Setting<Boolean> showItems = this.sgGeneral
        .add(new Builder().name("show-items").description("Show dropped items.").defaultValue(true).build());
    private final Setting<Boolean> showMobs = this.sgGeneral.add(new Builder().name("show-mobs").description("Show mobs.").defaultValue(true).build());
    private final Setting<Boolean> showPlayers = this.sgGeneral.add(new Builder().name("show-players").description("Show players.").defaultValue(true).build());
    private final Setting<Boolean> showProjectiles = this.sgGeneral
        .add(new Builder().name("show-projectiles").description("Show thrown projectiles (ender pearls, arrows, etc).").defaultValue(true).build());
    private final Setting<Boolean> showRockets = this.sgGeneral
        .add(new Builder().name("show-rockets").description("Show firework rockets.").defaultValue(false).build());
    private final Setting<Double> maxDistance = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("max-distance")
                .description("Maximum distance to show entities.")
                .defaultValue(100.0)
                .min(0.0)
                .sliderRange(0.0, 500.0)
                .build()
        );
    private final Setting<Boolean> sortByDistance = this.sgGeneral
        .add(new Builder().name("sort-by-distance").description("Sort entities by distance.").defaultValue(true).build());
    private final Setting<Boolean> showDistance = this.sgGeneral
        .add(new Builder().name("show-distance").description("Show distance to entities.").defaultValue(true).build());
    private final Setting<Boolean> includeYLevel = this.sgGeneral
        .add(new Builder().name("include-y-level").description("Include Y level in distance calculation (3D distance).").defaultValue(false).build());
    private final Setting<Double> textScale = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("text-scale")
                .description("Scale of the text.")
                .defaultValue(1.0)
                .min(0.1)
                .sliderRange(0.1, 3.0)
                .build()
        );
    private final Setting<Boolean> textShadow = this.sgGeneral
        .add(new Builder().name("text-shadow").description("Render shadow behind the text.").defaultValue(true).build());
    private final Setting<SettingColor> titleColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("title-color")
                .description("Color for the title text.")
                .defaultValue(new SettingColor(0, 255, 0, 255))
                .build()
        );
    private final Setting<SettingColor> playerColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("player-color")
                .description("Color for player entities.")
                .defaultValue(new SettingColor(0, 255, 0, 255))
                .build()
        );
    private final Setting<SettingColor> mobColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("mob-color")
                .description("Color for mob entities.")
                .defaultValue(new SettingColor(255, 0, 0, 255))
                .build()
        );
    private final Setting<SettingColor> itemColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("item-color")
                .description("Color for item entities.")
                .defaultValue(new SettingColor(255, 255, 255, 255))
                .build()
        );
    private final Setting<SettingColor> projectileColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("projectile-color")
                .description("Color for projectile entities.")
                .defaultValue(new SettingColor(150, 100, 255, 255))
                .build()
        );
    private final Setting<SettingColor> rocketColor = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("rocket-color")
                .description("Color for firework rockets.")
                .defaultValue(new SettingColor(255, 165, 0, 255))
                .build()
        );
    private static final Pattern CAMEL_CASE = Pattern.compile("([A-Z])");
    private static final Comparator<EntityList.Aggregated> BY_DISTANCE = Comparator.comparingDouble(a -> a.minDist);
    private static final Map<Class<?>, String> PROJECTILE_NAMES = new HashMap<>();
    private static final SettingColor DEFAULT_COLOR = new SettingColor(255, 255, 255, 255);
    private static final String[] PREVIEW_NAMES = new String[]{"Steve", "Zombie x3", "Diamond x64"};
    private static final int[] PREVIEW_DISTANCES = new int[]{12, 34, 78};
    private final Map<String, EntityList.Aggregated> aggregation = new HashMap<>();
    private final List<EntityList.Aggregated> rows = new ArrayList<>();

    public EntityList() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        this.aggregation.clear();
        this.rows.clear();
        if (MeteorClient.mc.level != null && MeteorClient.mc.player != null) {
            double playerX = MeteorClient.mc.player.getX();
            double playerY = MeteorClient.mc.player.getY();
            double playerZ = MeteorClient.mc.player.getZ();
            boolean useY = this.includeYLevel.get();
            double maxDist = this.maxDistance.get();
            boolean items = this.showItems.get();
            boolean mobs = this.showMobs.get();
            boolean players = this.showPlayers.get();
            boolean projectiles = this.showProjectiles.get();
            boolean rockets = this.showRockets.get();

            for (Entity entity : MeteorClient.mc.level.entitiesForRendering()) {
                if (entity != MeteorClient.mc.player) {
                    double dx = entity.getX() - playerX;
                    double dz = entity.getZ() - playerZ;
                    double distance;
                    if (useY) {
                        double dy = entity.getY() - playerY;
                        distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    } else {
                        distance = Math.sqrt(dx * dx + dz * dz);
                    }

                    if (!(distance > maxDist)) {
                        boolean isRocket = entity instanceof FireworkRocketEntity;
                        if (!isRocket || rockets) {
                            boolean isItem = entity instanceof ItemEntity && items;
                            boolean isMob = entity instanceof Mob && mobs;
                            boolean isPlayer = entity instanceof Player && players;
                            boolean isProjectile = !isRocket && (entity instanceof Projectile || entity instanceof ThrownEnderpearl) && projectiles;
                            if (isItem || isMob || isPlayer || isProjectile || isRocket) {
                                String name = this.getEntityName(entity);
                                EntityList.Aggregated agg = this.aggregation.get(name);
                                if (agg == null) {
                                    agg = new EntityList.Aggregated();
                                    agg.name = name;
                                    agg.color = this.getEntityColor(entity);
                                    agg.minDist = distance;
                                    if (isItem) {
                                        agg.count = ((ItemEntity)entity).getItem().getCount();
                                    } else {
                                        agg.count = 1;
                                    }

                                    this.aggregation.put(name, agg);
                                    this.rows.add(agg);
                                } else {
                                    agg.minDist = Math.min(agg.minDist, distance);
                                    if (isItem) {
                                        agg.count = agg.count + ((ItemEntity)entity).getItem().getCount();
                                    } else {
                                        agg.count++;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (this.sortByDistance.get()) {
                this.rows.sort(BY_DISTANCE);
            }

            boolean distances = this.showDistance.get();

            for (EntityList.Aggregated agg : this.rows) {
                String text = agg.name;
                if (agg.count > 1) {
                    text = text + " x" + agg.count;
                }

                if (distances) {
                    text = text + " (" + (int)agg.minDist + "m)";
                }

                agg.text = text;
            }
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.level != null && MeteorClient.mc.player != null) {
            double curX = this.x;
            double curY = this.y;
            double maxWidth = 0.0;
            double height = 0.0;
            double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
            double spacing = 2.0;
            if (this.showTitle.get()) {
                String title = "Entity List";
                double titleWidth = renderer.textWidth(title, this.textShadow.get(), this.textScale.get());
                renderer.text(title, curX, curY, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
                curY += textHeight + spacing;
                height += textHeight + spacing;
                maxWidth = Math.max(maxWidth, titleWidth);
            }

            for (EntityList.Aggregated agg : this.rows) {
                double textWidth = renderer.textWidth(agg.text, this.textShadow.get(), this.textScale.get());
                renderer.text(agg.text, curX, curY, agg.color, this.textShadow.get(), this.textScale.get());
                curY += textHeight + spacing;
                height += textHeight + spacing;
                maxWidth = Math.max(maxWidth, textWidth);
            }

            this.setSize(maxWidth, height > spacing ? height - spacing : 0.0);
        } else {
            if (this.isInEditor()) {
                double curY = this.y;
                double maxWidth = 0.0;
                double height = 0.0;
                double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
                double spacing = 2.0;
                if (this.showTitle.get()) {
                    String title = "Entity List";
                    renderer.text(title, this.x, curY, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
                    curY += textHeight + spacing;
                    height += textHeight + spacing;
                    maxWidth = Math.max(maxWidth, renderer.textWidth(title, this.textShadow.get(), this.textScale.get()));
                }

                SettingColor[] previewColors = new SettingColor[]{this.playerColor.get(), this.mobColor.get(), this.itemColor.get()};

                for (int i = 0; i < PREVIEW_NAMES.length; i++) {
                    String text = PREVIEW_NAMES[i];
                    if (this.showDistance.get()) {
                        text = text + " (" + PREVIEW_DISTANCES[i] + "m)";
                    }

                    renderer.text(text, this.x, curY, previewColors[i], this.textShadow.get(), this.textScale.get());
                    curY += textHeight + spacing;
                    height += textHeight + spacing;
                    maxWidth = Math.max(maxWidth, renderer.textWidth(text, this.textShadow.get(), this.textScale.get()));
                }

                this.setSize(maxWidth, height > spacing ? height - spacing : 0.0);
            } else {
                this.setSize(0.0, 0.0);
            }
        }
    }

    private String getEntityName(Entity entity) {
        if (entity instanceof ItemEntity item) {
            return item.getItem().getHoverName().getString();
        } else if (entity instanceof Player player) {
            return player.getName().getString();
        } else if (entity instanceof FireworkRocketEntity) {
            return "Firework Rocket";
        } else if (entity instanceof ThrownEnderpearl) {
            return "Ender Pearl";
        } else if (entity instanceof ThrownTrident) {
            return "Trident";
        } else {
            return entity instanceof Projectile
                ? PROJECTILE_NAMES.computeIfAbsent(entity.getClass(), c -> CAMEL_CASE.matcher(c.getSimpleName().replace("Entity", "")).replaceAll(" $1").trim())
                : entity.getType().getDescription().getString();
        }
    }

    private SettingColor getEntityColor(Entity entity) {
        if (entity instanceof ItemEntity) {
            return this.itemColor.get();
        } else if (entity instanceof Mob) {
            return this.mobColor.get();
        } else if (entity instanceof Player) {
            return this.playerColor.get();
        } else if (entity instanceof FireworkRocketEntity) {
            return this.rocketColor.get();
        } else {
            return !(entity instanceof Projectile) && !(entity instanceof ThrownEnderpearl) ? DEFAULT_COLOR : this.projectileColor.get();
        }
    }

    private static class Aggregated {
        String name;
        String text;
        int count;
        double minDist;
        SettingColor color;
    }
}
