package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.emoji.EmojiData;
import bep.hax.util.FallbackTextRenderer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class SignRender extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final SettingGroup sgClustering = this.settings.createGroup("Clustering");
    private final SettingGroup sgOptimization = this.settings.createGroup("Optimization");
    private final Setting<Double> maxDistance = this.sgGeneral
        .add(
            new Builder()
                .name("max-distance")
                .description("Maximum distance to render signs (blocks).")
                .defaultValue(1024.0)
                .min(16.0)
                .max(1024.0)
                .sliderRange(16.0, 1024.0)
                .build()
        );
    private final Setting<Integer> maxSigns = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-signs")
                .description("Maximum number of signs to render.")
                .defaultValue(500)
                .min(5)
                .max(500)
                .sliderRange(5, 1000)
                .build()
        );
    private final Setting<Boolean> filterEmpty = this.sgGeneral
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("filter-empty").description("Hide empty signs.").defaultValue(true).build());
    private final Setting<Boolean> multilineDisplay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("multiline-display")
                .description("Display sign text as multiple lines as they appear on the sign.")
                .defaultValue(true)
                .build()
        );
    private final Setting<SettingColor> textColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("text-color")
                .description("Text color.")
                .defaultValue(new SettingColor(255, 255, 255, 255))
                .build()
        );
    private final Setting<SettingColor> backgroundColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("background-color")
                .description("Background color.")
                .defaultValue(new SettingColor(0, 0, 0, 49))
                .build()
        );
    private final Setting<Boolean> showBackground = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-background")
                .description("Show background behind text.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> forceDefaultFont = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("force-default-font")
                .description("Force sign text to render using the default font, even if a custom GUI font is selected in your Meteor config.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> enableClustering = this.sgClustering
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("enable-clustering")
                .description("Group nearby signs to prevent overlap.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> clusterRadius = this.sgClustering
        .add(
            new Builder()
                .name("cluster-radius")
                .description("Screen distance in pixels to group signs.")
                .defaultValue(100.0)
                .min(20.0)
                .max(500.0)
                .sliderRange(20.0, 200.0)
                .visible(this.enableClustering::get)
                .build()
        );
    private final Setting<SignRender.ClusterMode> clusterMode = this.sgClustering
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                                .name("cluster-mode"))
                            .description("How to display clustered signs."))
                        .defaultValue(SignRender.ClusterMode.Count))
                    .visible(this.enableClustering::get))
                .build()
        );
    private final Setting<Integer> cycleTime = this.sgClustering
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("cycle-time")
                .description("Time in milliseconds between cycling signs.")
                .defaultValue(2000)
                .min(500)
                .max(10000)
                .sliderRange(500, 5000)
                .visible(() -> this.enableClustering.get() && this.clusterMode.get() == SignRender.ClusterMode.Cycle)
                .build()
        );
    private final Setting<Integer> maxClusterDisplay = this.sgClustering
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-cluster-display")
                .description("Maximum signs to show in a cluster.")
                .defaultValue(5)
                .min(1)
                .max(10)
                .sliderRange(1, 10)
                .visible(() -> this.enableClustering.get() && this.clusterMode.get() != SignRender.ClusterMode.Count)
                .build()
        );
    private final Setting<Boolean> showClusterCount = this.sgClustering
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-cluster-count")
                .description("Show number of signs in cluster.")
                .defaultValue(true)
                .visible(this.enableClustering::get)
                .build()
        );
    private final Setting<SettingColor> clusterCountColor = this.sgClustering
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("cluster-count-color")
                .description("Color for cluster count indicator.")
                .defaultValue(new SettingColor(255, 200, 100, 116))
                .visible(() -> this.enableClustering.get() && this.showClusterCount.get())
                .build()
        );
    private final Setting<Double> stackSpacing = this.sgClustering
        .add(
            new Builder()
                .name("stack-spacing")
                .description("Vertical spacing between stacked signs.")
                .defaultValue(5.0)
                .min(0.0)
                .max(20.0)
                .sliderRange(0.0, 20.0)
                .visible(() -> this.enableClustering.get() && this.clusterMode.get() == SignRender.ClusterMode.Stack)
                .build()
        );
    private final Setting<Boolean> cullOffScreen = this.sgOptimization
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("cull-off-screen")
                .description("Don't process signs that are off-screen.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> prioritizeClosest = this.sgOptimization
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("prioritize-closest")
                .description("Always show closest signs first.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> cacheSignText = this.sgOptimization
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("cache-sign-text")
                .description("Cache sign text for better performance.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> updateInterval = this.sgOptimization
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("update-interval")
                .description("Ticks between full sign updates.")
                .defaultValue(20)
                .min(1)
                .max(100)
                .sliderRange(1, 100)
                .visible(this.cacheSignText::get)
                .build()
        );
    private final Vector3d tempVec = new Vector3d();
    private final List<SignRender.SignRenderData> allSigns = new ArrayList<>();
    private final List<SignRender.SignRenderData> visibleSigns = new ArrayList<>();
    private final List<SignRender.SignCluster> clusters = new ArrayList<>();
    private final List<SignRender.TextDraw> textDraws = new ArrayList<>();
    private final Color backgroundQuadColor = new Color();
    private double[] lineWidths = new double[8];
    private int textDrawCount = 0;
    private double measureScale = Double.NaN;
    private double measureHeight = 0.0;
    private boolean batchingBackground = false;
    private int updateTicker = 0;
    private int globalCycleIndex = 0;
    private long lastGlobalCycleTime = 0L;
    private static final Pattern FORMAT_CODE = Pattern.compile("§.");
    private static final Pattern AMPERSAND_CODE = Pattern.compile("&[0-9a-fklmnor]");
    private static final Pattern JSON_FIELD = Pattern.compile("\\{\".*?\":\"(.*?)\".*?\\}");
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[\"(.*?)\"\\]");
    private static final Pattern CURLY_BLOCK = Pattern.compile("\\{[^\\s].*?\\}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{C}&&[^\\s]]");
    private static final Pattern CONTROL_RANGE = Pattern.compile("[\\u0000-\\u001F\\u007F-\\u009F]");
    private static final Pattern BRACKETS = Pattern.compile("[\\[\\]{}\"']");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public SignRender() {
        super(Bep.HUNT_CATEGORY, "sign-render", "Renders sign text through walls with advanced clustering.");
    }

    @Override
    public void onDeactivate() {
        this.allSigns.clear();
        this.visibleSigns.clear();
        this.clusters.clear();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (this.mc.level != null && this.mc.player != null) {
            this.updateTicker++;
            boolean fullUpdate = !this.cacheSignText.get() || this.updateTicker >= this.updateInterval.get();
            if (fullUpdate) {
                this.updateTicker = 0;
                this.collectSigns();
            } else {
                this.updateSignPositions();
            }

            this.buildVisibleList();
            if (this.enableClustering.get() && !this.visibleSigns.isEmpty()) {
                this.createClusters();
            }

            this.renderSigns();
        }
    }

    private void collectSigns() {
        this.allSigns.clear();
        Vec3 playerPos = this.mc.player.position();
        double maxDist = this.maxDistance.get();
        List<SignRender.SignRenderData> tempSignList = new ArrayList<>();

        for (BlockEntity blockEntity : Utils.blockEntities()) {
            try {
                if (blockEntity instanceof SignBlockEntity || blockEntity instanceof HangingSignBlockEntity) {
                    BlockPos signPos = blockEntity.getBlockPos();
                    Vec3 signVec = Vec3.atCenterOf(signPos);
                    double distance = playerPos.distanceTo(signVec);
                    if (!(distance > maxDist)) {
                        List<String> lines = this.extractSignLines(blockEntity);
                        if (!lines.isEmpty() || !this.filterEmpty.get()) {
                            SignRender.SignRenderData signData = new SignRender.SignRenderData(lines, signVec);
                            signData.distance = distance;
                            signData.updateScreenPosition(this.tempVec);
                            signData.scale = 1.0;
                            tempSignList.add(signData);
                        }
                    }
                }
            } catch (Exception var13) {
            }
        }

        if (this.prioritizeClosest.get()) {
            tempSignList.sort(Comparator.comparingDouble(s -> s.distance));
        }

        int limit = Math.min(tempSignList.size(), this.maxSigns.get());

        for (SignRender.SignRenderData sign : tempSignList) {
            if (this.allSigns.size() >= limit) {
                break;
            }

            if (sign.onScreen) {
                this.allSigns.add(sign);
            }
        }

        for (SignRender.SignRenderData sign : tempSignList) {
            if (this.allSigns.size() >= limit) {
                break;
            }

            if (!sign.onScreen) {
                this.allSigns.add(sign);
            }
        }

        if (this.globalCycleIndex >= this.allSigns.size() && !this.allSigns.isEmpty()) {
            this.globalCycleIndex = 0;
        }
    }

    private void updateSignPositions() {
        if (this.mc.player != null) {
            Vec3 playerPos = this.mc.player.position();

            for (SignRender.SignRenderData sign : this.allSigns) {
                sign.distance = playerPos.distanceTo(sign.worldPos);
                sign.updateScreenPosition(this.tempVec);
                sign.scale = 1.0;
            }
        }
    }

    private void buildVisibleList() {
        this.visibleSigns.clear();
        boolean cull = this.cullOffScreen.get();

        for (SignRender.SignRenderData sign : this.allSigns) {
            if (sign.onScreen || !cull) {
                this.visibleSigns.add(sign);
            }
        }
    }

    private void createClusters() {
        this.clusters.clear();
        List<SignRender.SignRenderData> toCluster = new ArrayList<>(this.visibleSigns);
        Set<SignRender.SignRenderData> clustered = new HashSet<>();
        double radiusSq = this.clusterRadius.get() * this.clusterRadius.get();

        while (!toCluster.isEmpty()) {
            SignRender.SignRenderData seed = toCluster.remove(0);
            if (!clustered.contains(seed) && seed.onScreen) {
                SignRender.SignCluster cluster = new SignRender.SignCluster();
                cluster.addSign(seed);
                clustered.add(seed);

                for (SignRender.SignRenderData other : toCluster) {
                    if (other.onScreen && !clustered.contains(other)) {
                        double dx = seed.screenX - other.screenX;
                        double dy = seed.screenY - other.screenY;
                        double distSq = dx * dx + dy * dy;
                        if (distSq <= radiusSq) {
                            cluster.addSign(other);
                            clustered.add(other);
                        }
                    }
                }

                cluster.calculateCenter();
                if (cluster.signs.size() > 1) {
                    this.clusters.add(cluster);
                }
            }
        }
    }

    private void renderSigns() {
        if (!this.visibleSigns.isEmpty()) {
            TextRenderer base = this.forceDefaultFont.get() ? VanillaTextRenderer.INSTANCE : TextRenderer.get();
            TextRenderer textRenderer;
            if (base == VanillaTextRenderer.INSTANCE) {
                textRenderer = FallbackTextRenderer.vanilla();
            } else {
                textRenderer = new FallbackTextRenderer(base);
            }

            textRenderer.setAlpha(1.0);
            this.textDrawCount = 0;
            this.measureScale = Double.NaN;
            this.batchingBackground = this.showBackground.get();
            if (this.batchingBackground) {
                Renderer2D.COLOR.begin();
            }

            try {
                if (this.enableClustering.get() && !this.clusters.isEmpty()) {
                    this.renderWithClusters(textRenderer);
                } else {
                    this.renderAllSigns(textRenderer);
                }
            } finally {
                if (textRenderer.isBuilding()) {
                    textRenderer.end();
                }

                if (this.batchingBackground) {
                    Renderer2D.COLOR.render();
                }

                this.flushText(textRenderer);
            }
        }
    }

    private double measure(TextRenderer textRenderer, double scale) {
        if (scale != this.measureScale) {
            if (textRenderer.isBuilding()) {
                textRenderer.end();
            }

            textRenderer.begin(scale, true, true);
            this.measureScale = scale;
            this.measureHeight = textRenderer.getHeight();
        }

        return this.measureHeight;
    }

    private void queueBackground(double x, double y, double width, double height, int textAlpha) {
        if (this.batchingBackground) {
            SettingColor background = this.backgroundColor.get();
            this.backgroundQuadColor.set(background.r, background.g, background.b, (int)(background.a * (textAlpha / 255.0)));
            Renderer2D.COLOR.quad(x, y, width, height, this.backgroundQuadColor);
        }
    }

    private void queueText(String text, double x, double y, double scale, Color color) {
        SignRender.TextDraw draw;
        if (this.textDrawCount < this.textDraws.size()) {
            draw = this.textDraws.get(this.textDrawCount);
        } else {
            draw = new SignRender.TextDraw();
            this.textDraws.add(draw);
        }

        this.textDrawCount++;
        draw.text = text;
        draw.x = x;
        draw.y = y;
        draw.scale = scale;
        draw.color = color;
        draw.drawn = false;
    }

    private void flushText(TextRenderer textRenderer) {
        int start = 0;

        while (start < this.textDrawCount) {
            double scale = this.textDraws.get(start).scale;
            int next = -1;
            textRenderer.begin(scale, false, true);

            try {
                for (int i = start; i < this.textDrawCount; i++) {
                    SignRender.TextDraw draw = this.textDraws.get(i);
                    if (!draw.drawn) {
                        if (draw.scale == scale) {
                            textRenderer.render(draw.text, draw.x, draw.y, draw.color);
                            draw.drawn = true;
                        } else if (next == -1) {
                            next = i;
                        }
                    }
                }
            } finally {
                textRenderer.end();
            }

            if (next == -1) {
                return;
            }

            start = next;
        }
    }

    private void renderWithClusters(TextRenderer textRenderer) {
        long currentTime = System.currentTimeMillis();
        Set<SignRender.SignRenderData> rendered = new HashSet<>();

        for (SignRender.SignCluster cluster : this.clusters) {
            switch ((SignRender.ClusterMode)this.clusterMode.get()) {
                case Stack:
                    this.renderStackedCluster(cluster, textRenderer, rendered);
                    break;
                case Cycle:
                    this.renderCyclingCluster(cluster, textRenderer, currentTime, rendered);
                    break;
                case Count:
                    this.renderCountCluster(cluster, textRenderer, rendered);
                    break;
                case Smart:
                    this.renderSmartCluster(cluster, textRenderer, rendered);
            }
        }

        for (SignRender.SignRenderData sign : this.visibleSigns) {
            if (!rendered.contains(sign) && sign.onScreen) {
                this.renderSignAtPosition(sign, textRenderer, sign.screenX, sign.screenY);
            }
        }
    }

    private void renderAllSigns(TextRenderer textRenderer) {
        if (this.enableClustering.get() && this.clusterMode.get() == SignRender.ClusterMode.Cycle && !this.visibleSigns.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            if (this.lastGlobalCycleTime == 0L) {
                this.lastGlobalCycleTime = currentTime;
            }

            if (currentTime - this.lastGlobalCycleTime >= this.cycleTime.get().intValue()) {
                this.globalCycleIndex = (this.globalCycleIndex + 1) % this.visibleSigns.size();
                this.lastGlobalCycleTime = currentTime;
            }

            if (this.globalCycleIndex >= this.visibleSigns.size()) {
                this.globalCycleIndex = 0;
            }

            SignRender.SignRenderData currentSign = this.visibleSigns.get(this.globalCycleIndex);
            if (currentSign.onScreen) {
                this.renderSignAtPosition(currentSign, textRenderer, currentSign.screenX, currentSign.screenY);
                if (this.showClusterCount.get() && this.visibleSigns.size() > 1) {
                    double lineHeight = this.measure(textRenderer, currentSign.scale);
                    double signHeight = this.multilineDisplay.get() && !currentSign.lines.isEmpty()
                        ? currentSign.lines.size() * lineHeight + 8.0
                        : lineHeight + 8.0;
                    String indicator = String.format("[%d/%d]", this.globalCycleIndex + 1, this.visibleSigns.size());
                    this.renderTextAtScreenPos(
                        indicator, currentSign.screenX, currentSign.screenY + signHeight / 2.0 + 15.0, 0.7, this.clusterCountColor.get(), textRenderer
                    );
                }
            }
        } else {
            for (SignRender.SignRenderData sign : this.visibleSigns) {
                if (sign.onScreen) {
                    this.renderSignAtPosition(sign, textRenderer, sign.screenX, sign.screenY);
                }
            }
        }
    }

    private void renderStackedCluster(SignRender.SignCluster cluster, TextRenderer textRenderer, Set<SignRender.SignRenderData> rendered) {
        double baseX = cluster.centerX;
        double baseY = cluster.centerY;
        double offsetY = 0.0;
        int count = 0;

        for (SignRender.SignRenderData sign : cluster.signs) {
            if (count >= this.maxClusterDisplay.get()) {
                break;
            }

            this.renderSignAtPosition(sign, textRenderer, baseX, baseY + offsetY);
            rendered.add(sign);
            double lineHeight = this.measure(textRenderer, sign.scale);
            double signHeight = this.multilineDisplay.get() && !sign.lines.isEmpty() ? sign.lines.size() * lineHeight + 8.0 : lineHeight + 8.0;
            offsetY += signHeight + this.stackSpacing.get();
            count++;
        }

        if (this.showClusterCount.get() && cluster.signs.size() > this.maxClusterDisplay.get()) {
            String countText = "+" + (cluster.signs.size() - this.maxClusterDisplay.get()) + " more";
            this.renderTextAtScreenPos(countText, baseX, baseY + offsetY, 0.8, this.clusterCountColor.get(), textRenderer);
        }
    }

    private void renderCyclingCluster(SignRender.SignCluster cluster, TextRenderer textRenderer, long currentTime, Set<SignRender.SignRenderData> rendered) {
        SignRender.SignRenderData currentSign = cluster.getCurrentSign(currentTime, this.cycleTime.get());
        if (currentSign != null) {
            this.renderSignAtPosition(currentSign, textRenderer, cluster.centerX, cluster.centerY);
            rendered.addAll(cluster.signs);
            if (this.showClusterCount.get() && cluster.signs.size() > 1) {
                double lineHeight = this.measure(textRenderer, currentSign.scale);
                double signHeight = this.multilineDisplay.get() && !currentSign.lines.isEmpty()
                    ? currentSign.lines.size() * lineHeight + 8.0
                    : lineHeight + 8.0;
                String indicator = String.format("[%d/%d]", cluster.cycleIndex + 1, cluster.signs.size());
                this.renderTextAtScreenPos(
                    indicator, cluster.centerX, cluster.centerY + signHeight / 2.0 + 15.0, 0.7, this.clusterCountColor.get(), textRenderer
                );
            }
        }
    }

    private void renderCountCluster(SignRender.SignCluster cluster, TextRenderer textRenderer, Set<SignRender.SignRenderData> rendered) {
        SignRender.SignRenderData primary = cluster.primarySign;
        this.renderSignAtPosition(primary, textRenderer, cluster.centerX, cluster.centerY);
        rendered.addAll(cluster.signs);
        if (cluster.signs.size() > 1) {
            double lineHeight = this.measure(textRenderer, primary.scale);
            double signHeight = this.multilineDisplay.get() && !primary.lines.isEmpty() ? primary.lines.size() * lineHeight + 8.0 : lineHeight + 8.0;
            String countText = "(" + cluster.signs.size() + " signs)";
            this.renderTextAtScreenPos(countText, cluster.centerX, cluster.centerY + signHeight / 2.0 + 10.0, 0.8, this.clusterCountColor.get(), textRenderer);
        }
    }

    private void renderSmartCluster(SignRender.SignCluster cluster, TextRenderer textRenderer, Set<SignRender.SignRenderData> rendered) {
        int displayCount = Math.min(cluster.signs.size(), this.maxClusterDisplay.get());
        if (displayCount == 1) {
            this.renderSignAtPosition(cluster.signs.get(0), textRenderer, cluster.centerX, cluster.centerY);
            rendered.add(cluster.signs.get(0));
        } else {
            double radius = 30.0 + displayCount * 5.0;
            double angleStep = (Math.PI * 2) / displayCount;

            for (int i = 0; i < displayCount; i++) {
                SignRender.SignRenderData sign = cluster.signs.get(i);
                double angle = i * angleStep - (Math.PI / 2);
                double offsetX = Math.cos(angle) * radius;
                double offsetY = Math.sin(angle) * radius;
                this.renderSignAtPosition(sign, textRenderer, cluster.centerX + offsetX, cluster.centerY + offsetY);
                rendered.add(sign);
            }

            if (this.showClusterCount.get() && cluster.signs.size() > displayCount) {
                String countText = "+" + (cluster.signs.size() - displayCount);
                this.renderTextAtScreenPos(countText, cluster.centerX, cluster.centerY, 0.9, this.clusterCountColor.get(), textRenderer);
            }
        }
    }

    private void renderSignAtPosition(SignRender.SignRenderData sign, TextRenderer textRenderer, double centerX, double centerY) {
        if (this.multilineDisplay.get() && !sign.lines.isEmpty()) {
            this.renderMultilineSign(sign, textRenderer, centerX, centerY);
        } else if (!sign.fullText.isEmpty()) {
            this.renderSingleLineSign(sign, textRenderer, centerX, centerY);
        }
    }

    private void renderMultilineSign(SignRender.SignRenderData sign, TextRenderer textRenderer, double centerX, double centerY) {
        double lineHeight = this.measure(textRenderer, sign.scale);
        int lineCount = sign.lines.size();
        if (this.lineWidths.length < lineCount) {
            this.lineWidths = new double[lineCount];
        }

        double maxWidth = 0.0;

        for (int i = 0; i < lineCount; i++) {
            String line = sign.lines.get(i);
            double width = line.isEmpty() ? 0.0 : textRenderer.getWidth(line);
            this.lineWidths[i] = width;
            maxWidth = Math.max(maxWidth, width);
        }

        double totalHeight = lineCount * lineHeight;
        double bgPadding = 4.0;
        double bgWidth = maxWidth + bgPadding * 2.0;
        double bgHeight = totalHeight + bgPadding * 2.0;
        double bgLeft = centerX - bgWidth / 2.0;
        double bgTop = centerY - bgHeight / 2.0;
        Color color = this.textColor.get();
        this.queueBackground(bgLeft, bgTop, bgWidth, bgHeight, color.a);

        for (int i = 0; i < lineCount; i++) {
            String line = sign.lines.get(i);
            if (!line.isEmpty()) {
                double textX = centerX - this.lineWidths[i] / 2.0;
                double textY = bgTop + bgPadding + i * lineHeight;
                this.queueText(line, textX, textY, sign.scale, color);
            }
        }
    }

    private void renderSingleLineSign(SignRender.SignRenderData sign, TextRenderer textRenderer, double centerX, double centerY) {
        this.renderTextAtScreenPos(sign.fullText, centerX, centerY, sign.scale, this.textColor.get(), textRenderer);
    }

    private void renderTextAtScreenPos(String text, double screenX, double screenY, double scale, Color color, TextRenderer textRenderer) {
        double textHeight = this.measure(textRenderer, scale);
        double textWidth = textRenderer.getWidth(text);
        double bgPadding = 4.0;
        double elementWidth = textWidth + bgPadding * 2.0;
        double elementHeight = textHeight + bgPadding * 2.0;
        double elementLeft = screenX - elementWidth / 2.0;
        double elementTop = screenY - elementHeight / 2.0;
        this.queueBackground(elementLeft, elementTop, elementWidth, elementHeight, color.a);
        this.queueText(text, elementLeft + bgPadding, elementTop + bgPadding, scale, color);
    }

    private List<String> extractSignLines(BlockEntity blockEntity) {
        List<String> lines = new ArrayList<>();

        try {
            SignText frontText = null;
            SignText backText = null;
            if (blockEntity instanceof SignBlockEntity sign) {
                frontText = sign.getFrontText();
                backText = sign.getBackText();
            } else if (blockEntity instanceof HangingSignBlockEntity sign) {
                frontText = sign.getFrontText();
                backText = sign.getBackText();
            }

            if (frontText != null) {
                List<String> frontLines = this.extractTextLines(frontText);
                if (!frontLines.isEmpty()) {
                    lines.addAll(frontLines);
                }
            }

            if (backText != null && lines.isEmpty()) {
                List<String> backLines = this.extractTextLines(backText);
                if (!backLines.isEmpty()) {
                    lines.addAll(backLines);
                }
            }
        } catch (Exception var7) {
        }

        return lines;
    }

    private List<String> extractTextLines(SignText signText) {
        List<String> lines = new ArrayList<>();

        try {
            Component[] messages = signText.getMessages(false);
            if (messages != null) {
                for (Component message : messages) {
                    if (message != null) {
                        String line = this.safeExtractString(message);
                        if (!line.isEmpty()) {
                            lines.add(line);
                        }
                    }
                }
            }
        } catch (Exception var9) {
        }

        return lines;
    }

    private String safeExtractString(Component text) {
        if (text == null) {
            return "";
        }

        try {
            String result = text.getString();
            return result == null ? "" : this.cleanSignText(result);
        } catch (Exception e) {
            try {
                String literal = text.tryCollapseToString();
                if (literal != null) {
                    return this.cleanSignText(literal);
                }
            } catch (Exception var4) {
            }

            return "";
        }
    }

    private String cleanSignText(String text) {
        if (text != null && !text.isEmpty()) {
            text = FORMAT_CODE.matcher(text).replaceAll("");
            text = AMPERSAND_CODE.matcher(text).replaceAll("");
            if (text.contains("{\"") || text.contains("[\"")) {
                text = JSON_FIELD.matcher(text).replaceAll("$1");
                text = JSON_ARRAY.matcher(text).replaceAll("$1");
            }

            text = CURLY_BLOCK.matcher(text).replaceAll("");
            text = CONTROL_CHARS.matcher(text).replaceAll("");
            text = CONTROL_RANGE.matcher(text).replaceAll("");
            text = BRACKETS.matcher(text).replaceAll("");
            text = EmojiData.stripUnrenderable(text);
            text = WHITESPACE.matcher(text).replaceAll(" ").trim();
            if (text.length() > 100) {
                text = text.substring(0, 97) + "...";
            }

            return text;
        } else {
            return "";
        }
    }

    public enum ClusterMode {
        Stack("Stack vertically"),
        Cycle("Cycle through signs"),
        Count("Show count only"),
        Smart("Smart layout");

        private final String description;

        ClusterMode(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return this.description;
        }
    }

    private static class SignCluster {
        final List<SignRender.SignRenderData> signs = new ArrayList<>();
        double centerX;
        double centerY;
        SignRender.SignRenderData primarySign;
        int cycleIndex = 0;
        long lastCycleTime = 0L;

        void addSign(SignRender.SignRenderData sign) {
            this.signs.add(sign);
            sign.onScreen = true;
        }

        void calculateCenter() {
            if (!this.signs.isEmpty()) {
                this.signs.sort(Comparator.comparingDouble(s -> s.distance));
                this.primarySign = this.signs.get(0);
                this.centerX = this.primarySign.screenX;
                this.centerY = this.primarySign.screenY;
            }
        }

        SignRender.SignRenderData getCurrentSign(long currentTime, int cycleTimeMs) {
            if (this.signs.isEmpty()) {
                return null;
            }

            if (this.signs.size() == 1) {
                return this.signs.get(0);
            }

            if (this.lastCycleTime == 0L) {
                this.lastCycleTime = currentTime;
            }

            if (currentTime - this.lastCycleTime >= cycleTimeMs) {
                this.cycleIndex = (this.cycleIndex + 1) % this.signs.size();
                this.lastCycleTime = currentTime;
            }

            return this.signs.get(this.cycleIndex);
        }
    }

    private static class SignRenderData {
        final List<String> lines;
        final String fullText;
        final Vec3 worldPos;
        double distance;
        double screenX;
        double screenY;
        boolean onScreen = false;
        double scale;

        SignRenderData(List<String> lines, Vec3 worldPos) {
            this.lines = new ArrayList<>(lines);
            this.fullText = String.join(" ", lines).trim();
            this.worldPos = worldPos;
        }

        void updateScreenPosition(Vector3d tempVec) {
            tempVec.set(this.worldPos.x, this.worldPos.y + 0.5, this.worldPos.z);
            if (NametagUtils.to2D(tempVec, 1.0, false)) {
                this.screenX = tempVec.x;
                this.screenY = tempVec.y;
                this.onScreen = true;
            } else {
                this.onScreen = false;
            }
        }
    }

    private static class TextDraw {
        String text;
        double x;
        double y;
        double scale;
        Color color;
        boolean drawn;
    }
}
