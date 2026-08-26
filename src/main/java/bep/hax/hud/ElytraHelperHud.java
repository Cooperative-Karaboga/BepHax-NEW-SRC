package bep.hax.hud;

import bep.hax.Bep;
import bep.hax.modules.ElytraBounce;
import bep.hax.modules.ElytraRecast;
import bep.hax.modules.ElytraSwap;
import bep.hax.modules.TrailFollower;
import bep.hax.modules.arealoader.AreaLoader;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

public class ElytraHelperHud extends HudElement {
    public static final HudElementInfo<ElytraHelperHud> INFO = new HudElementInfo<>(
        Bep.HUD_GROUP,
        "elytra-helper",
        "Central flight info panel: elytra durability, flight time estimates, session stats, and hunt module status.",
        ElytraHelperHud::new
    );
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgSections = this.settings.createGroup("Sections");
    private final SettingGroup sgColors = this.settings.createGroup("Colors");
    private final Setting<Double> textScale = this.sgGeneral
        .add(new Builder().name("text-scale").description("Scale of the text.").defaultValue(1.0).min(0.5).sliderRange(0.5, 3.0).build());
    private final Setting<Boolean> textShadow = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("text-shadow")
                .description("Render shadow behind text.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> compactMode = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("compact-mode")
                .description("Use shorter labels for a smaller HUD footprint.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> showTitle = this.sgSections
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-title")
                .description("Display the HUD title.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showEquipped = this.sgSections
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-equipped")
                .description("Show equipped elytra durability and estimated remaining flight time.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showInventory = this.sgSections
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-inventory")
                .description("Show total flight time from all elytras in inventory.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showSession = this.sgSections
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-session")
                .description("Show session flying time and distance traveled.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showSpeed = this.sgSections
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-speed")
                .description("Show current flight speed.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showModuleStatus = this.sgSections
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-module-status")
                .description("Show status of hunt modules (AreaLoader, TrailFollower, ElytraSwap, etc.).")
                .defaultValue(true)
                .build()
        );
    private final Setting<SettingColor> titleColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("title-color")
                .description("Color for the title.")
                .defaultValue(new SettingColor(255, 170, 0, 255))
                .build()
        );
    private final Setting<SettingColor> labelColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("label-color")
                .description("Color for labels.")
                .defaultValue(new SettingColor(180, 180, 180, 255))
                .build()
        );
    private final Setting<SettingColor> valueColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("value-color")
                .description("Color for values.")
                .defaultValue(new SettingColor(255, 255, 255, 255))
                .build()
        );
    private final Setting<SettingColor> goodColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("good-color")
                .description("Color for healthy durability / active modules.")
                .defaultValue(new SettingColor(85, 255, 85, 255))
                .build()
        );
    private final Setting<SettingColor> warnColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("warn-color")
                .description("Color for medium durability warnings.")
                .defaultValue(new SettingColor(255, 255, 85, 255))
                .build()
        );
    private final Setting<SettingColor> dangerColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("danger-color")
                .description("Color for low durability / inactive modules.")
                .defaultValue(new SettingColor(255, 85, 85, 255))
                .build()
        );
    private final Setting<SettingColor> sectionColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("section-color")
                .description("Color for section headers.")
                .defaultValue(new SettingColor(255, 170, 0, 255))
                .build()
        );
    private long sessionStartTime = 0L;
    private long lastSessionUpdateMs = 0L;
    private long sessionFlyingMs = 0L;
    private boolean wasFlying = false;
    private Vec3 sessionStartPos = null;
    private Vec3 lastRenderPos = null;
    private double sessionDistance = 0.0;

    public ElytraHelperHud() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            long now = System.currentTimeMillis();
            boolean isFlying = MeteorClient.mc.player.isFallFlying();
            if (isFlying && !this.wasFlying) {
                if (this.sessionStartTime == 0L) {
                    this.sessionStartTime = now;
                    this.sessionStartPos = MeteorClient.mc.player.position();
                    this.sessionDistance = 0.0;
                }

                this.lastRenderPos = MeteorClient.mc.player.position();
                this.lastSessionUpdateMs = now;
            }

            if (isFlying) {
                this.sessionFlyingMs = this.sessionFlyingMs + (now - this.lastSessionUpdateMs);
                Vec3 currentPos = MeteorClient.mc.player.position();
                if (this.lastRenderPos != null) {
                    this.sessionDistance = this.sessionDistance + currentPos.distanceTo(this.lastRenderPos);
                }

                this.lastRenderPos = currentPos;
            } else {
                this.lastRenderPos = null;
            }

            this.lastSessionUpdateMs = now;
            this.wasFlying = isFlying;
        } else {
            this.wasFlying = false;
            this.lastRenderPos = null;
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            long now = System.currentTimeMillis();
            double curX = this.x;
            double curY = this.y;
            double lineH = renderer.textHeight(this.textShadow.get(), this.textScale.get());
            double spacing = 2.0;
            double maxW = 0.0;
            if (this.showTitle.get()) {
                String title = this.compactMode.get() ? "Elytra" : "Elytra Helper";
                maxW = Math.max(maxW, this.drawText(renderer, title, curX, curY, this.titleColor.get()));
                curY += lineH + spacing;
            }

            if (this.showEquipped.get()) {
                ItemStack chest = MeteorClient.mc.player.getItemBySlot(EquipmentSlot.CHEST);
                if (chest.getItem() == Items.ELYTRA) {
                    int durability = chest.getMaxDamage() - chest.getDamageValue();
                    int maxDurability = chest.getMaxDamage();
                    int unbreaking = this.getUnbreakingLevel(chest);
                    double flightSeconds = this.calcFlightSeconds(durability, unbreaking);
                    String durText = durability + "/" + maxDurability;
                    if (unbreaking > 0) {
                        durText = durText + " (U" + unbreaking + ")";
                    }

                    String timeText = this.formatTime((long)flightSeconds);
                    SettingColor durColor = this.getDurabilityColor(durability, maxDurability);
                    String label = this.compactMode.get() ? "Eq: " : "Equipped: ";
                    maxW = Math.max(maxW, this.drawLabelValue(renderer, label, durText + " | " + timeText, curX, curY, durColor));
                } else {
                    String label = this.compactMode.get() ? "Eq: " : "Equipped: ";
                    maxW = Math.max(maxW, this.drawLabelValue(renderer, label, "None", curX, curY, this.dangerColor.get()));
                }

                curY += lineH + spacing;
            }

            if (this.showInventory.get()) {
                int totalElytras = 0;
                double totalFlightSeconds = 0.0;
                int bestDurability = 0;

                for (int i = 0; i < 36; i++) {
                    ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
                    if (stack.getItem() == Items.ELYTRA) {
                        int dur = stack.getMaxDamage() - stack.getDamageValue();
                        int ub = this.getUnbreakingLevel(stack);
                        totalFlightSeconds += this.calcFlightSeconds(dur, ub);
                        totalElytras++;
                        bestDurability = Math.max(bestDurability, dur);
                    }
                }

                ItemStack chest = MeteorClient.mc.player.getItemBySlot(EquipmentSlot.CHEST);
                if (chest.getItem() == Items.ELYTRA) {
                    int dur = chest.getMaxDamage() - chest.getDamageValue();
                    int ub = this.getUnbreakingLevel(chest);
                    totalFlightSeconds += this.calcFlightSeconds(dur, ub);
                    totalElytras++;
                    bestDurability = Math.max(bestDurability, dur);
                }

                String invLabel = this.compactMode.get() ? "Inv: " : "Inventory: ";
                String invValue = totalElytras + "x | " + this.formatTime((long)totalFlightSeconds);
                SettingColor invColor = totalElytras > 2 ? this.goodColor.get() : (totalElytras > 0 ? this.warnColor.get() : this.dangerColor.get());
                maxW = Math.max(maxW, this.drawLabelValue(renderer, invLabel, invValue, curX, curY, invColor));
                curY += lineH + spacing;
            }

            if (this.showSession.get()) {
                maxW = Math.max(
                    maxW, this.drawText(renderer, this.compactMode.get() ? "-- Session --" : "--- Session ---", curX, curY, this.sectionColor.get())
                );
                curY += lineH + spacing;
                long flyingSeconds = this.sessionFlyingMs / 1000L;
                String flyLabel = this.compactMode.get() ? "Fly: " : "Flying: ";
                maxW = Math.max(maxW, this.drawLabelValue(renderer, flyLabel, this.formatTime(flyingSeconds), curX, curY, this.valueColor.get()));
                curY += lineH + spacing;
                if (this.sessionStartTime > 0L) {
                    long sessionSeconds = (now - this.sessionStartTime) / 1000L;
                    String sessLabel = this.compactMode.get() ? "Sess: " : "Session: ";
                    maxW = Math.max(maxW, this.drawLabelValue(renderer, sessLabel, this.formatTime(sessionSeconds), curX, curY, this.valueColor.get()));
                    curY += lineH + spacing;
                }

                String distLabel = this.compactMode.get() ? "Dist: " : "Distance: ";
                maxW = Math.max(maxW, this.drawLabelValue(renderer, distLabel, this.formatDistance(this.sessionDistance), curX, curY, this.valueColor.get()));
                curY += lineH + spacing;
                if (this.sessionStartPos != null) {
                    double distFromStart = MeteorClient.mc.player.position().distanceTo(this.sessionStartPos);
                    String startLabel = this.compactMode.get() ? "From start: " : "From Start: ";
                    maxW = Math.max(maxW, this.drawLabelValue(renderer, startLabel, this.formatDistance(distFromStart), curX, curY, this.valueColor.get()));
                    curY += lineH + spacing;
                }
            }

            if (this.showSpeed.get()) {
                double velX = MeteorClient.mc.player.getDeltaMovement().x;
                double velZ = MeteorClient.mc.player.getDeltaMovement().z;
                double velY = MeteorClient.mc.player.getDeltaMovement().y;
                double speed = Math.sqrt(velX * velX + velZ * velZ + velY * velY) * 20.0;
                String speedLabel = this.compactMode.get() ? "Spd: " : "Speed: ";
                maxW = Math.max(maxW, this.drawLabelValue(renderer, speedLabel, String.format("%.1f b/s", speed), curX, curY, this.valueColor.get()));
                curY += lineH + spacing;
            }

            if (this.showModuleStatus.get()) {
                maxW = Math.max(
                    maxW, this.drawText(renderer, this.compactMode.get() ? "-- Modules --" : "--- Modules ---", curX, curY, this.sectionColor.get())
                );
                curY += lineH + spacing;
                maxW = Math.max(maxW, this.drawModuleStatus(renderer, "AreaLoader", AreaLoader.class, curX, curY));
                curY += lineH + spacing;
                maxW = Math.max(maxW, this.drawModuleStatus(renderer, "TrailFollower", TrailFollower.class, curX, curY));
                curY += lineH + spacing;
                maxW = Math.max(maxW, this.drawModuleStatus(renderer, "ElytraSwap", ElytraSwap.class, curX, curY));
                curY += lineH + spacing;
                maxW = Math.max(maxW, this.drawModuleStatus(renderer, "ElytraBounce", ElytraBounce.class, curX, curY));
                curY += lineH + spacing;
                maxW = Math.max(maxW, this.drawModuleStatus(renderer, "ElytraRecast", ElytraRecast.class, curX, curY));
                curY += lineH + spacing;
            }

            this.setSize(Math.max(maxW, 80.0), curY - this.y > 0.0 ? curY - this.y - spacing : 0.0);
        } else {
            if (this.isInEditor()) {
                this.renderEditorPreview(renderer);
            } else {
                this.setSize(0.0, 0.0);
            }
        }
    }

    private int getUnbreakingLevel(ItemStack stack) {
        return Utils.getEnchantmentLevel(stack, Enchantments.UNBREAKING);
    }

    private double calcFlightSeconds(int durability, int unbreakingLevel) {
        int usable = Math.max(0, durability - 1);
        if (usable == 0) {
            return 0.0;
        }

        if (unbreakingLevel <= 0) {
            return usable;
        }

        double lossChance = (60.0 + 40.0 / (unbreakingLevel + 1)) / 100.0;
        return usable / lossChance;
    }

    private double drawText(HudRenderer renderer, String text, double dx, double dy, SettingColor color) {
        renderer.text(text, dx, dy, color, this.textShadow.get(), this.textScale.get());
        return renderer.textWidth(text, this.textShadow.get(), this.textScale.get());
    }

    private double drawLabelValue(HudRenderer renderer, String label, String value, double dx, double dy, SettingColor valueCol) {
        double lw = renderer.textWidth(label, this.textShadow.get(), this.textScale.get());
        renderer.text(label, dx, dy, this.labelColor.get(), this.textShadow.get(), this.textScale.get());
        renderer.text(value, dx + lw, dy, valueCol, this.textShadow.get(), this.textScale.get());
        double vw = renderer.textWidth(value, this.textShadow.get(), this.textScale.get());
        return lw + vw;
    }

    private double drawModuleStatus(HudRenderer renderer, String name, Class<? extends Module> clazz, double dx, double dy) {
        Module module = Modules.get().get(clazz);
        boolean active = module != null && module.isActive();
        String label = (this.compactMode.get() ? name.substring(0, Math.min(name.length(), 6)) : name) + ": ";
        String status = active ? "ON" : "OFF";
        SettingColor statusColor = active ? this.goodColor.get() : this.dangerColor.get();
        return this.drawLabelValue(renderer, label, status, dx, dy, statusColor);
    }

    private String formatTime(long totalSeconds) {
        if (totalSeconds < 0L) {
            totalSeconds = 0L;
        }

        long hours = totalSeconds / 3600L;
        long minutes = totalSeconds % 3600L / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        } else {
            return minutes > 0L ? String.format("%dm %02ds", minutes, seconds) : String.format("%ds", seconds);
        }
    }

    private String formatDistance(double blocks) {
        return blocks >= 1000.0 ? String.format("%.1fk", blocks / 1000.0) : String.format("%.0f", blocks);
    }

    private SettingColor getDurabilityColor(int current, int max) {
        if (max <= 0) {
            return this.dangerColor.get();
        } else {
            double pct = (double)current / max;
            if (pct > 0.5) {
                return this.goodColor.get();
            } else {
                return pct > 0.2 ? this.warnColor.get() : this.dangerColor.get();
            }
        }
    }

    private void renderEditorPreview(HudRenderer renderer) {
        double curX = this.x;
        double curY = this.y;
        double lineH = renderer.textHeight(this.textShadow.get(), this.textScale.get());
        double spacing = 2.0;
        double maxW = 0.0;
        if (this.showTitle.get()) {
            maxW = Math.max(maxW, this.drawText(renderer, this.compactMode.get() ? "Elytra" : "Elytra Helper", curX, curY, this.titleColor.get()));
            curY += lineH + spacing;
        }

        if (this.showEquipped.get()) {
            maxW = Math.max(
                maxW, this.drawLabelValue(renderer, this.compactMode.get() ? "Eq: " : "Equipped: ", "318/432 (U3) | 7m 34s", curX, curY, this.goodColor.get())
            );
            curY += lineH + spacing;
        }

        if (this.showInventory.get()) {
            maxW = Math.max(
                maxW, this.drawLabelValue(renderer, this.compactMode.get() ? "Inv: " : "Inventory: ", "4x | 45m 12s", curX, curY, this.goodColor.get())
            );
            curY += lineH + spacing;
        }

        if (this.showSession.get()) {
            maxW = Math.max(maxW, this.drawText(renderer, this.compactMode.get() ? "-- Session --" : "--- Session ---", curX, curY, this.sectionColor.get()));
            curY += lineH + spacing;
            maxW = Math.max(maxW, this.drawLabelValue(renderer, this.compactMode.get() ? "Fly: " : "Flying: ", "12m 34s", curX, curY, this.valueColor.get()));
            curY += lineH + spacing;
            if (this.sessionStartTime > 0L) {
                maxW = Math.max(
                    maxW, this.drawLabelValue(renderer, this.compactMode.get() ? "Sess: " : "Session: ", "25m 10s", curX, curY, this.valueColor.get())
                );
                curY += lineH + spacing;
            }

            maxW = Math.max(maxW, this.drawLabelValue(renderer, this.compactMode.get() ? "Dist: " : "Distance: ", "42.3k", curX, curY, this.valueColor.get()));
            curY += lineH + spacing;
            if (this.sessionStartPos != null) {
                maxW = Math.max(
                    maxW, this.drawLabelValue(renderer, this.compactMode.get() ? "From start: " : "From Start: ", "38.1k", curX, curY, this.valueColor.get())
                );
                curY += lineH + spacing;
            }
        }

        if (this.showSpeed.get()) {
            maxW = Math.max(maxW, this.drawLabelValue(renderer, this.compactMode.get() ? "Spd: " : "Speed: ", "33.2 b/s", curX, curY, this.valueColor.get()));
            curY += lineH + spacing;
        }

        if (this.showModuleStatus.get()) {
            maxW = Math.max(maxW, this.drawText(renderer, this.compactMode.get() ? "-- Modules --" : "--- Modules ---", curX, curY, this.sectionColor.get()));
            curY += lineH + spacing;
            maxW = Math.max(maxW, this.drawModuleStatus(renderer, "AreaLoader", AreaLoader.class, curX, curY));
            curY += lineH + spacing;
            maxW = Math.max(maxW, this.drawModuleStatus(renderer, "TrailFollower", TrailFollower.class, curX, curY));
            curY += lineH + spacing;
            maxW = Math.max(maxW, this.drawModuleStatus(renderer, "ElytraSwap", ElytraSwap.class, curX, curY));
            curY += lineH + spacing;
            maxW = Math.max(maxW, this.drawModuleStatus(renderer, "ElytraBounce", ElytraBounce.class, curX, curY));
            curY += lineH + spacing;
            maxW = Math.max(maxW, this.drawModuleStatus(renderer, "ElytraRecast", ElytraRecast.class, curX, curY));
            curY += lineH + spacing;
        }

        this.setSize(Math.max(maxW, 80.0), curY - this.y > 0.0 ? curY - this.y - spacing : 0.0);
    }
}
