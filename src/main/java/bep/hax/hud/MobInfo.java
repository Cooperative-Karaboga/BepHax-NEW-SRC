package bep.hax.hud;

import bep.hax.Bep;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public class MobInfo extends HudElement {
    public static final HudElementInfo<MobInfo> INFO = new HudElementInfo<>(Bep.HUD_GROUP, "MobInfo", "Track mob spawns and density.", MobInfo::new);
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgFilter = this.settings.createGroup("Filter");
    private final SettingGroup sgDisplay = this.settings.createGroup("Display");
    private final SettingGroup sgGraph = this.settings.createGroup("Graph");
    private final SettingGroup sgColors = this.settings.createGroup("Colors");
    private final Setting<Boolean> trackSpawnRate = this.sgGeneral
        .add(new Builder().name("track-spawn-rate").description("Calculate spawns per hour.").defaultValue(true).build());
    private final Setting<Integer> rateUpdateInterval = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("rate-update")
                .description("Ticks between rate updates.")
                .defaultValue(100)
                .min(20)
                .max(600)
                .sliderRange(20, 600)
                .visible(this.trackSpawnRate::get)
                .build()
        );
    private final Setting<Boolean> trackDensity = this.sgGeneral
        .add(new Builder().name("track-density").description("Track mob density in area.").defaultValue(true).build());
    private final Setting<Integer> scanRadius = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("scan-radius")
                .description("Radius to scan in chunks.")
                .defaultValue(8)
                .min(1)
                .max(32)
                .sliderRange(1, 32)
                .visible(this.trackDensity::get)
                .build()
        );
    private final Setting<Integer> densityAlert = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("density-alert")
                .description("Highlight when mob count exceeds this.")
                .defaultValue(30)
                .min(10)
                .max(100)
                .sliderRange(10, 100)
                .visible(this.trackDensity::get)
                .build()
        );
    private final Setting<Boolean> resetOnDimension = this.sgGeneral
        .add(new Builder().name("reset-on-dimension").description("Reset when changing dimensions.").defaultValue(true).build());
    private final Setting<Double> textScale = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("text-scale")
                .description("Scale of the text.")
                .defaultValue(1.0)
                .min(0.5)
                .sliderRange(0.5, 3.0)
                .build()
        );
    private final Setting<Boolean> textShadow = this.sgGeneral
        .add(new Builder().name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());
    private final Setting<Set<EntityType<?>>> entities = this.sgFilter
        .add(
            new meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder()
                .name("entities")
                .description("Entities to track.")
                .defaultValue(getDefaults())
                .build()
        );
    private final Setting<Boolean> excludePlayer = this.sgFilter
        .add(new Builder().name("exclude-player").description("Exclude the player from mob count.").defaultValue(true).build());
    private final Setting<Boolean> showTitle = this.sgDisplay.add(new Builder().name("title").defaultValue(true).build());
    private final Setting<Boolean> showRate = this.sgDisplay.add(new Builder().name("spawn-rate").defaultValue(true).visible(this.trackSpawnRate::get).build());
    private final Setting<Boolean> showNearby = this.sgDisplay.add(new Builder().name("nearby-count").defaultValue(true).build());
    private final Setting<Boolean> showTotal = this.sgDisplay
        .add(new Builder().name("total-spawned").defaultValue(true).visible(this.trackSpawnRate::get).build());
    private final Setting<Boolean> showDensityValue = this.sgDisplay
        .add(new Builder().name("density-value").defaultValue(true).visible(this.trackDensity::get).build());
    private final Setting<Boolean> showTime = this.sgDisplay.add(new Builder().name("session-time").defaultValue(true).build());
    private final Setting<Boolean> showRateGraph = this.sgGraph
        .add(new Builder().name("spawn-rate-graph").description("Show spawn rate graph.").defaultValue(true).visible(this.trackSpawnRate::get).build());
    private final Setting<Boolean> showCountGraph = this.sgGraph
        .add(new Builder().name("mob-count-graph").description("Show mob count graph.").defaultValue(true).build());
    private final Setting<Integer> graphWidth = this.sgGraph
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("width")
                .description("Graph width.")
                .defaultValue(200)
                .min(100)
                .max(400)
                .sliderRange(100, 400)
                .build()
        );
    private final Setting<Integer> graphHeight = this.sgGraph
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("height")
                .description("Graph height.")
                .defaultValue(60)
                .min(30)
                .max(150)
                .sliderRange(30, 150)
                .build()
        );
    private final Setting<Integer> graphPoints = this.sgGraph
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("data-points")
                .description("Number of data points.")
                .defaultValue(30)
                .min(10)
                .max(60)
                .sliderRange(10, 60)
                .build()
        );
    private final Setting<Integer> graphUpdate = this.sgGraph
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("update-rate")
                .description("Ticks between graph updates.")
                .defaultValue(20)
                .min(5)
                .max(100)
                .sliderRange(5, 100)
                .build()
        );
    private final Setting<Boolean> showGrid = this.sgGraph.add(new Builder().name("grid").description("Show grid lines.").defaultValue(true).build());
    private final Setting<SettingColor> titleColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("title-color")
                .description("Title text color.")
                .defaultValue(new SettingColor(255, 255, 255))
                .build()
        );
    private final Setting<SettingColor> titleAlertColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("title-alert-color")
                .description("Title color when density alert triggered.")
                .defaultValue(new SettingColor(255, 100, 100))
                .build()
        );
    private final Setting<SettingColor> rateHighColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("rate-high-color")
                .description("Spawn rate color (>3000/hr).")
                .defaultValue(new SettingColor(100, 200, 255))
                .build()
        );
    private final Setting<SettingColor> rateGoodColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("rate-good-color")
                .description("Spawn rate color (>1000/hr).")
                .defaultValue(new SettingColor(100, 255, 100))
                .build()
        );
    private final Setting<SettingColor> rateMedColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("rate-medium-color")
                .description("Spawn rate color (>300/hr).")
                .defaultValue(new SettingColor(255, 255, 100))
                .build()
        );
    private final Setting<SettingColor> rateLowColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("rate-low-color")
                .description("Spawn rate color (<300/hr).")
                .defaultValue(new SettingColor(255, 100, 100))
                .build()
        );
    private final Setting<SettingColor> nearbyHighColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("nearby-high-color")
                .description("Nearby count color (>=alert).")
                .defaultValue(new SettingColor(255, 100, 100))
                .build()
        );
    private final Setting<SettingColor> nearbyMedColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("nearby-medium-color")
                .description("Nearby count color (>=alert/2).")
                .defaultValue(new SettingColor(255, 255, 100))
                .build()
        );
    private final Setting<SettingColor> nearbyLowColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("nearby-low-color")
                .description("Nearby count color (<alert/2).")
                .defaultValue(new SettingColor(100, 200, 255))
                .build()
        );
    private final Setting<SettingColor> totalColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("total-color")
                .description("Total spawned color.")
                .defaultValue(new SettingColor(255, 255, 255))
                .build()
        );
    private final Setting<SettingColor> densityColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("density-color")
                .description("Density value color.")
                .defaultValue(new SettingColor(100, 255, 100))
                .build()
        );
    private final Setting<SettingColor> timeColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("time-color")
                .description("Session time color.")
                .defaultValue(new SettingColor(200, 200, 200))
                .build()
        );
    private final Setting<SettingColor> graphBgColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("graph-background-color")
                .description("Graph background color.")
                .defaultValue(new SettingColor(20, 20, 20, 180))
                .build()
        );
    private final Setting<SettingColor> graphGridColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("graph-grid-color")
                .description("Graph grid line color.")
                .defaultValue(new SettingColor(60, 60, 60, 100))
                .build()
        );
    private final Setting<SettingColor> graphLabelColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("graph-label-color")
                .description("Graph label color.")
                .defaultValue(new SettingColor(100, 255, 100))
                .build()
        );
    private final Setting<SettingColor> graphPeakColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("graph-peak-color")
                .description("Peak value label color.")
                .defaultValue(new SettingColor(255, 100, 100))
                .build()
        );
    private final Set<UUID> tracked = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> nearby = Collections.synchronizedSet(new HashSet<>());
    private final LinkedList<Double> rateHistory = new LinkedList<>();
    private final LinkedList<Integer> countHistory = new LinkedList<>();
    private long sessionStart = System.currentTimeMillis();
    private int totalSpawned = 0;
    private double spawnRate = 0.0;
    private double density = 0.0;
    private int ticks = 0;
    private int lastTotal = 0;
    private String lastDim = "";
    private int graphTicks = 0;
    private int peakCount = 0;
    private static final double TICKS_PER_HOUR = 72000.0;

    public MobInfo() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    private static Set<EntityType<?>> getDefaults() {
        Set<EntityType<?>> set = new HashSet<>();
        set.add(EntityType.ZOMBIE);
        set.add(EntityType.SKELETON);
        set.add(EntityType.CREEPER);
        set.add(EntityType.SPIDER);
        set.add(EntityType.ENDERMAN);
        return set;
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.level != null && MeteorClient.mc.player != null) {
            double posY = this.y;
            double width = 0.0;
            double h = renderer.textHeight(this.textShadow.get(), this.textScale.get());
            double spacing = 2.0;
            if (this.showTitle.get()) {
                String title = "Mob Info";
                SettingColor col = this.titleColor.get();
                if (this.trackDensity.get() && this.nearby.size() >= this.densityAlert.get()) {
                    col = this.titleAlertColor.get();
                }

                width = Math.max(width, renderer.textWidth(title, this.textShadow.get(), this.textScale.get()));
                renderer.text(title, this.x, posY, col, this.textShadow.get(), this.textScale.get());
                posY += h + spacing;
            }

            if (this.showRate.get() && this.trackSpawnRate.get()) {
                String text = this.format(this.spawnRate) + "/hr";
                width = Math.max(width, renderer.textWidth(text, this.textShadow.get(), this.textScale.get()));
                renderer.text(text, this.x, posY, this.getColorForRate(this.spawnRate), this.textShadow.get(), this.textScale.get());
                posY += h + spacing;
            }

            if (this.showNearby.get()) {
                String text = "Nearby: " + this.nearby.size();
                width = Math.max(width, renderer.textWidth(text, this.textShadow.get(), this.textScale.get()));
                renderer.text(text, this.x, posY, this.getColorForNearby(this.nearby.size()), this.textShadow.get(), this.textScale.get());
                posY += h + spacing;
            }

            if (this.showTotal.get() && this.trackSpawnRate.get()) {
                String text = "Total: " + this.totalSpawned;
                width = Math.max(width, renderer.textWidth(text, this.textShadow.get(), this.textScale.get()));
                renderer.text(text, this.x, posY, this.totalColor.get(), this.textShadow.get(), this.textScale.get());
                posY += h + spacing;
            }

            if (this.showDensityValue.get() && this.trackDensity.get()) {
                String text = String.format("Density: %.2f/chunk", this.density);
                width = Math.max(width, renderer.textWidth(text, this.textShadow.get(), this.textScale.get()));
                renderer.text(text, this.x, posY, this.densityColor.get(), this.textShadow.get(), this.textScale.get());
                posY += h + spacing;
            }

            if (this.showTime.get()) {
                String text = this.formatSessionTime(System.currentTimeMillis() - this.sessionStart);
                width = Math.max(width, renderer.textWidth(text, this.textShadow.get(), this.textScale.get()));
                renderer.text(text, this.x, posY, this.timeColor.get(), this.textShadow.get(), this.textScale.get());
                posY += h + spacing;
            }

            if (this.showRateGraph.get() && this.trackSpawnRate.get() && this.rateHistory.size() >= 2) {
                posY += 4.0;
                posY = this.renderRateGraph(renderer, posY);
                width = Math.max(width, this.graphWidth.get().intValue());
            }

            if (this.showCountGraph.get() && this.countHistory.size() >= 2) {
                posY += 4.0;
                posY = this.renderCountGraph(renderer, posY);
                width = Math.max(width, this.graphWidth.get().intValue());
            }

            this.setSize(width, posY - this.y);
        } else {
            if (this.isInEditor()) {
                this.renderEditorPreview(renderer);
            } else {
                this.setSize(0.0, 0.0);
            }
        }
    }

    private void renderEditorPreview(HudRenderer renderer) {
        double posY = this.y;
        double width = 0.0;
        double h = renderer.textHeight(this.textShadow.get(), this.textScale.get());
        double spacing = 2.0;
        int sampleNearby = 24;
        if (this.showTitle.get()) {
            String title = "Mob Info";
            width = Math.max(width, renderer.textWidth(title, this.textShadow.get(), this.textScale.get()));
            renderer.text(title, this.x, posY, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
            posY += h + spacing;
        }

        if (this.showRate.get() && this.trackSpawnRate.get()) {
            String text = this.format(1240.0) + "/hr";
            width = Math.max(width, renderer.textWidth(text, this.textShadow.get(), this.textScale.get()));
            renderer.text(text, this.x, posY, this.getColorForRate(1240.0), this.textShadow.get(), this.textScale.get());
            posY += h + spacing;
        }

        if (this.showNearby.get()) {
            String text = "Nearby: " + sampleNearby;
            width = Math.max(width, renderer.textWidth(text, this.textShadow.get(), this.textScale.get()));
            renderer.text(text, this.x, posY, this.getColorForNearby(sampleNearby), this.textShadow.get(), this.textScale.get());
            posY += h + spacing;
        }

        if (this.showTotal.get() && this.trackSpawnRate.get()) {
            String text = "Total: 843";
            width = Math.max(width, renderer.textWidth(text, this.textShadow.get(), this.textScale.get()));
            renderer.text(text, this.x, posY, this.totalColor.get(), this.textShadow.get(), this.textScale.get());
            posY += h + spacing;
        }

        if (this.showDensityValue.get() && this.trackDensity.get()) {
            String text = String.format("Density: %.2f/chunk", 0.12);
            width = Math.max(width, renderer.textWidth(text, this.textShadow.get(), this.textScale.get()));
            renderer.text(text, this.x, posY, this.densityColor.get(), this.textShadow.get(), this.textScale.get());
            posY += h + spacing;
        }

        if (this.showTime.get()) {
            String text = this.formatSessionTime(754000L);
            width = Math.max(width, renderer.textWidth(text, this.textShadow.get(), this.textScale.get()));
            renderer.text(text, this.x, posY, this.timeColor.get(), this.textShadow.get(), this.textScale.get());
            posY += h + spacing;
        }

        if (this.showRateGraph.get() && this.trackSpawnRate.get()) {
            posY += 4.0;
            posY = this.renderGraphFrame(renderer, "Spawn Rate", posY);
            width = Math.max(width, this.graphWidth.get().intValue());
        }

        if (this.showCountGraph.get()) {
            posY += 4.0;
            posY = this.renderGraphFrame(renderer, "Mob Count", posY);
            width = Math.max(width, this.graphWidth.get().intValue());
        }

        this.setSize(width, posY - this.y);
    }

    private double renderGraphFrame(HudRenderer renderer, String title, double startY) {
        double gx = this.x;
        double gy = startY;
        double w = this.graphWidth.get().intValue();
        double h = this.graphHeight.get().intValue();
        double spacing = 2.0;
        renderer.text(title, gx, gy, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
        gy += renderer.textHeight(this.textShadow.get(), this.textScale.get()) + spacing;
        renderer.quad(gx, gy, w, h, this.graphBgColor.get());
        if (this.showGrid.get()) {
            for (int i = 1; i <= 4; i++) {
                double gridY = gy + h - h * i / 4.0;
                renderer.quad(gx, gridY, w, 1.0, this.graphGridColor.get());
            }
        }

        return gy + h + 4.0;
    }

    private double renderRateGraph(HudRenderer renderer, double startY) {
        double gx = this.x;
        double gy = startY;
        double w = this.graphWidth.get().intValue();
        double h = this.graphHeight.get().intValue();
        double spacing = 2.0;
        String title = "Spawn Rate";
        renderer.text(title, gx, gy, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
        gy += renderer.textHeight(this.textShadow.get(), this.textScale.get()) + spacing;
        renderer.quad(gx, gy, w, h, this.graphBgColor.get());
        double max = 1.0;

        for (double v : this.rateHistory) {
            if (v > max) {
                max = v;
            }
        }

        if (this.showGrid.get()) {
            for (int i = 1; i <= 4; i++) {
                double gridY = gy + h - h * i / 4.0;
                renderer.quad(gx, gridY, w, 1.0, this.graphGridColor.get());
            }
        }

        for (int i = 0; i < this.rateHistory.size() - 1; i++) {
            double v1 = this.rateHistory.get(i);
            double v2 = this.rateHistory.get(i + 1);
            double x1 = gx + (double)i / (this.rateHistory.size() - 1) * w;
            double x2 = gx + (double)(i + 1) / (this.rateHistory.size() - 1) * w;
            double y1 = gy + h - v1 / max * h;
            double y2 = gy + h - v2 / max * h;
            this.drawLine(renderer, x1, y1, x2, y2, this.getColorForRate(v2));
        }

        String label = this.format(this.spawnRate) + "/hr";
        double lw = renderer.textWidth(label, this.textShadow.get(), this.textScale.get());
        renderer.text(
            label,
            gx + w - lw,
            gy - renderer.textHeight(this.textShadow.get(), this.textScale.get()) - spacing,
            this.graphLabelColor.get(),
            this.textShadow.get(),
            this.textScale.get()
        );
        return gy + h + 4.0;
    }

    private double renderCountGraph(HudRenderer renderer, double startY) {
        double gx = this.x;
        double gy = startY;
        double w = this.graphWidth.get().intValue();
        double h = this.graphHeight.get().intValue();
        double spacing = 2.0;
        String title = "Mob Count";
        renderer.text(title, gx, gy, this.titleColor.get(), this.textShadow.get(), this.textScale.get());
        gy += renderer.textHeight(this.textShadow.get(), this.textScale.get()) + spacing;
        renderer.quad(gx, gy, w, h, this.graphBgColor.get());
        int max = 1;

        for (int c : this.countHistory) {
            if (c > max) {
                max = c;
            }
        }

        if (this.showGrid.get()) {
            for (int i = 1; i <= 4; i++) {
                double gridY = gy + h - h * i / 4.0;
                renderer.quad(gx, gridY, w, 1.0, this.graphGridColor.get());
            }
        }

        for (int i = 0; i < this.countHistory.size() - 1; i++) {
            int c1 = this.countHistory.get(i);
            int c2 = this.countHistory.get(i + 1);
            double x1 = gx + (double)i / (this.countHistory.size() - 1) * w;
            double x2 = gx + (double)(i + 1) / (this.countHistory.size() - 1) * w;
            double y1 = gy + h - (double)c1 / max * h;
            double y2 = gy + h - (double)c2 / max * h;
            SettingColor fill = this.getColorForCount(c2);
            this.drawFill(renderer, x1, y1, x2, y2, gy + h, fill);
            this.drawLine(renderer, x1, y1, x2, y2, fill);
        }

        String label = "Now: " + this.nearby.size();
        double lw = renderer.textWidth(label, this.textShadow.get(), this.textScale.get());
        renderer.text(
            label,
            gx + w - lw,
            gy - renderer.textHeight(this.textShadow.get(), this.textScale.get()) - spacing,
            this.graphLabelColor.get(),
            this.textShadow.get(),
            this.textScale.get()
        );
        String peak = "Peak: " + this.peakCount;
        renderer.text(peak, gx + spacing, gy + spacing, this.graphPeakColor.get(), this.textShadow.get(), this.textScale.get());
        return gy + h + 4.0;
    }

    private void drawLine(HudRenderer renderer, double x1, double y1, double x2, double y2, SettingColor color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len != 0.0) {
            int steps = (int)Math.ceil(len);

            for (int i = 0; i <= steps; i++) {
                double t = (double)i / steps;
                double px = x1 + dx * t;
                double py = y1 + dy * t;
                renderer.quad(px - 1.0, py - 1.0, 2.0, 2.0, color);
            }
        }
    }

    private void drawFill(HudRenderer renderer, double x1, double y1, double x2, double y2, double bottom, SettingColor color) {
        SettingColor fillColor = new SettingColor(color.r, color.g, color.b, 60);
        int steps = Math.max(1, (int)(x2 - x1));

        for (int i = 0; i <= steps; i++) {
            double t = (double)i / steps;
            double px = x1 + (x2 - x1) * t;
            double py = y1 + (y2 - y1) * t;
            double ph = bottom - py;
            renderer.quad(px, py, 1.0, ph, fillColor);
        }
    }

    private SettingColor getColorForNearby(int count) {
        if (this.trackDensity.get()) {
            int alert = this.densityAlert.get();
            if (count >= alert) {
                return this.nearbyHighColor.get();
            } else {
                return count >= alert / 2 ? this.nearbyMedColor.get() : this.nearbyLowColor.get();
            }
        } else {
            return this.titleColor.get();
        }
    }

    private SettingColor getColorForCount(int count) {
        if (this.trackDensity.get()) {
            int alert = this.densityAlert.get();
            if (count >= alert) {
                return this.nearbyHighColor.get();
            } else {
                return count >= alert / 2 ? this.nearbyMedColor.get() : this.nearbyLowColor.get();
            }
        } else {
            return this.graphLabelColor.get();
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (MeteorClient.mc.level != null && MeteorClient.mc.player != null) {
            String dim = MeteorClient.mc.level.dimension().identifier().toString();
            if (this.resetOnDimension.get() && !dim.equals(this.lastDim)) {
                this.reset();
                this.lastDim = dim;
            } else {
                this.nearby.clear();

                for (Entity e : MeteorClient.mc.level.entitiesForRendering()) {
                    if (e instanceof LivingEntity
                        && this.entities.get().contains(e.getType())
                        && (!this.excludePlayer.get() || e != MeteorClient.mc.player)) {
                        UUID id = e.getUUID();
                        if (this.trackSpawnRate.get() && !this.tracked.contains(id)) {
                            this.tracked.add(id);
                            this.totalSpawned++;
                        }

                        if (e.isAlive()) {
                            double dist = MeteorClient.mc.player.distanceTo(e);
                            double scanRadiusBlocks = this.scanRadius.get().intValue() * 16.0;
                            if (this.trackDensity.get() && dist <= scanRadiusBlocks) {
                                this.nearby.add(id);
                            } else if (!this.trackDensity.get()) {
                                this.nearby.add(id);
                            }
                        }
                    }
                }

                if (this.trackDensity.get()) {
                    double r = this.scanRadius.get().intValue();
                    double area = Math.PI * r * r;
                    this.density = area > 0.0 ? this.nearby.size() / area : 0.0;
                }

                if (this.trackSpawnRate.get()) {
                    this.ticks++;
                    if (this.ticks >= this.rateUpdateInterval.get()) {
                        int spawned = this.totalSpawned - this.lastTotal;
                        this.lastTotal = this.totalSpawned;
                        this.spawnRate = (double)spawned / this.rateUpdateInterval.get().intValue() * 72000.0;
                        this.ticks = 0;
                    }
                }

                if (this.nearby.size() > this.peakCount) {
                    this.peakCount = this.nearby.size();
                }

                this.graphTicks++;
                if (this.graphTicks >= this.graphUpdate.get()) {
                    if (this.trackSpawnRate.get()) {
                        this.rateHistory.add(this.spawnRate);

                        while (this.rateHistory.size() > this.graphPoints.get()) {
                            this.rateHistory.removeFirst();
                        }
                    }

                    this.countHistory.add(this.nearby.size());

                    while (this.countHistory.size() > this.graphPoints.get()) {
                        this.countHistory.removeFirst();
                    }

                    this.graphTicks = 0;
                }

                if (this.tracked.size() > 10000) {
                    this.tracked.clear();
                }
            }
        }
    }

    private void reset() {
        this.tracked.clear();
        this.nearby.clear();
        this.rateHistory.clear();
        this.countHistory.clear();
        this.totalSpawned = 0;
        this.lastTotal = 0;
        this.spawnRate = 0.0;
        this.density = 0.0;
        this.ticks = 0;
        this.graphTicks = 0;
        this.peakCount = 0;
        this.sessionStart = System.currentTimeMillis();
    }

    private String formatSessionTime(long millis) {
        long sec = millis / 1000L;
        long min = sec / 60L;
        long hrs = min / 60L;
        if (hrs > 0L) {
            return hrs + "h " + min % 60L + "m";
        } else {
            return min > 0L ? min + "m " + sec % 60L + "s" : sec + "s";
        }
    }

    private String format(double num) {
        if (num < 1000.0) {
            return String.format("%.0f", num);
        } else {
            return num < 1000000.0 ? String.format("%.1fk", num / 1000.0) : String.format("%.1fm", num / 1000000.0);
        }
    }

    private SettingColor getColorForRate(double rate) {
        if (rate > 3000.0) {
            return this.rateHighColor.get();
        } else if (rate > 1000.0) {
            return this.rateGoodColor.get();
        } else {
            return rate > 300.0 ? this.rateMedColor.get() : this.rateLowColor.get();
        }
    }
}
