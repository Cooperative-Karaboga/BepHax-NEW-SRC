package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.FadeAnimator;
import bep.hax.util.prox.EmoteManager;
import java.util.List;
import java.util.UUID;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.KeybindSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;

public class EmoteWheel extends Module {
    private static final double DEAD_ZONE = 18.0;
    private static final int MAX_LABEL_LENGTH = 14;
    private static final int BORDER_WIDTH = 2;
    private static final double EDGE_EPSILON = 1.0E-9;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Keybind> activationKey = this.sgGeneral
        .add(
            new Builder()
                .name("activation-key")
                .description("Hold to open the wheel, aim at an emote and release to play it. Release in the middle to cancel.")
                .defaultValue(Keybind.fromKey(66))
                .build()
        );
    private final Setting<Boolean> advanced = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("advanced")
                .description("Show advanced layout and color settings.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> wheelRadius = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("wheel-radius")
                .description("Radius of the wheel in pixels.")
                .defaultValue(110)
                .min(60)
                .max(400)
                .sliderRange(60, 300)
                .build()
        );
    private final Setting<Double> textScale = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("text-scale")
                .description("Scale of the emote labels.")
                .defaultValue(1.0)
                .min(0.5)
                .max(3.0)
                .sliderRange(0.5, 2.0)
                .build()
        );
    private final Setting<Integer> slotsPerPage = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("slots-per-page")
                .description("Wheel slices per page, including the Stop slice. Scroll while open to change page.")
                .defaultValue(8)
                .min(4)
                .max(12)
                .sliderRange(4, 12)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Integer> wheelX = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("x-offset")
                .description("X offset from the center of the screen.")
                .defaultValue(0)
                .min(-500)
                .max(500)
                .sliderRange(-500, 500)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Integer> wheelY = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("y-offset")
                .description("Y offset from the center of the screen.")
                .defaultValue(0)
                .min(-500)
                .max(500)
                .sliderRange(-500, 500)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<SettingColor> backgroundColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("background-color")
                .description("Background color of the wheel.")
                .defaultValue(new SettingColor(40, 40, 40, 120))
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<SettingColor> selectedColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("selected-color")
                .description("Color of the hovered slice.")
                .defaultValue(new SettingColor(100, 200, 255, 160))
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<SettingColor> textColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("text-color")
                .description("Color of the emote labels.")
                .defaultValue(new SettingColor(255, 255, 255, 255))
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<SettingColor> playingColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("playing-color")
                .description("Label color of the emote that is currently playing.")
                .defaultValue(new SettingColor(100, 255, 100, 255))
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Boolean> fadeAnimation = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("fade-animation")
                .description("Smoothly fade the wheel in and out instead of snapping.")
                .defaultValue(true)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Double> fadeDuration = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("fade-duration")
                .description("How long the fade in/out takes, in seconds.")
                .defaultValue(0.15)
                .min(0.0)
                .sliderRange(0.0, 1.0)
                .visible(() -> this.advanced.get() && this.fadeAnimation.get())
                .build()
        );
    private final FadeAnimator fade = new FadeAnimator();
    private List<EmoteManager.Emote> emotes = List.of();
    private boolean wheelActive = false;
    private int page = 0;
    private int selectedSlot = -1;
    private double cursorX = 0.0;
    private double cursorY = 0.0;
    private double lastRawX = 0.0;
    private double lastRawY = 0.0;

    public EmoteWheel() {
        super(Bep.CATEGORY, "emote-wheel", "Radial menu to quickly play emotes. Hold the key, aim and release.");
    }

    @Override
    public void onActivate() {
        this.wheelActive = false;
        this.selectedSlot = -1;
    }

    @Override
    public void onDeactivate() {
        this.wheelActive = false;
        this.selectedSlot = -1;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.mc.screen != null) {
                this.wheelActive = false;
            } else {
                boolean keyPressed = this.activationKey.get().isPressed();
                if (keyPressed && !this.wheelActive) {
                    this.emotes = EmoteManager.getInstance().emotes();
                    if (this.emotes.isEmpty()) {
                        this.info("No emotes loaded.");
                        return;
                    }

                    this.wheelActive = true;
                    this.selectedSlot = -1;
                    if (this.page >= this.pageCount()) {
                        this.page = 0;
                    }

                    this.resetCursor();
                    KeyMapping.releaseAll();
                } else if (!keyPressed && this.wheelActive) {
                    this.wheelActive = false;
                    this.executeSelection();
                }

                if (this.wheelActive) {
                    this.updateCursor();
                }
            }
        }
    }

    @EventHandler(priority = 100)
    private void onMouseScroll(MouseScrollEvent event) {
        if (this.wheelActive) {
            event.cancel();
            if (this.pageCount() > 1) {
                this.page = Math.floorMod(this.page + (event.value < 0.0 ? 1 : -1), this.pageCount());
                this.selectedSlot = -1;
            }
        }
    }

    private void executeSelection() {
        if (this.selectedSlot >= 0) {
            if (this.selectedSlot == 0) {
                UUID uuid = EmoteManager.getInstance().stopLocal();
                Proximity.onLocalEmote(2, uuid, 0);
            } else {
                int index = this.page * this.emotesPerPage() + this.selectedSlot - 1;
                if (index < this.emotes.size()) {
                    UUID uuid = EmoteManager.getInstance().playLocal(this.emotes.get(index).name());
                    if (uuid != null) {
                        Proximity.onLocalEmote(0, uuid, 0);
                    }
                }
            }
        }
    }

    private int emotesPerPage() {
        return this.slotsPerPage.get() - 1;
    }

    private int pageCount() {
        return Math.max(1, (this.emotes.size() + this.emotesPerPage() - 1) / this.emotesPerPage());
    }

    private int slotCount() {
        return 1 + Math.min(this.emotesPerPage(), Math.max(0, this.emotes.size() - this.page * this.emotesPerPage()));
    }

    private void resetCursor() {
        this.cursorX = 0.0;
        this.cursorY = 0.0;
        this.lastRawX = this.mc.mouseHandler.xpos();
        this.lastRawY = this.mc.mouseHandler.ypos();
    }

    private void updateCursor() {
        double rawX = this.mc.mouseHandler.xpos();
        double rawY = this.mc.mouseHandler.ypos();
        int screenWidth = this.mc.getWindow().getScreenWidth();
        int screenHeight = this.mc.getWindow().getScreenHeight();
        if (screenWidth > 0 && screenHeight > 0) {
            this.cursorX = this.cursorX + (rawX - this.lastRawX) * this.mc.getWindow().getGuiScaledWidth() / screenWidth;
            this.cursorY = this.cursorY + (rawY - this.lastRawY) * this.mc.getWindow().getGuiScaledHeight() / screenHeight;
        }

        this.lastRawX = rawX;
        this.lastRawY = rawY;
        double distance = Math.sqrt(this.cursorX * this.cursorX + this.cursorY * this.cursorY);
        double radius = this.wheelRadius.get().intValue();
        if (distance > radius) {
            this.cursorX = this.cursorX / distance * radius;
            this.cursorY = this.cursorY / distance * radius;
            distance = radius;
        }

        if (distance < 18.0) {
            this.selectedSlot = -1;
        } else {
            int slots = this.slotCount();
            double slice = 360.0 / slots;
            double degrees = Math.toDegrees(Math.atan2(this.cursorY, this.cursorX));
            if (degrees < 0.0) {
                degrees += 360.0;
            }

            degrees = (degrees + 90.0) % 360.0;
            this.selectedSlot = (int)((degrees + slice / 2.0) / slice) % slots;
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        this.fade.update(this.wheelActive, this.fadeDuration.get(), this.fadeAnimation.get());
        if (this.wheelActive || this.fade.rendering()) {
            if (this.wheelActive) {
                this.updateCursor();
            }

            GuiGraphics context = event.drawContext;
            int centerX = this.mc.getWindow().getGuiScaledWidth() / 2 + this.wheelX.get();
            int centerY = this.mc.getWindow().getGuiScaledHeight() / 2 + this.wheelY.get();
            int radius = this.wheelRadius.get();
            int background = this.fade.apply(this.backgroundColor.get().getPacked());
            int border = this.fade.apply(-1879048192);
            this.drawFilledCircle(context, centerX, centerY, radius, background);
            int slots = this.slotCount();
            if (this.selectedSlot >= 0 && this.selectedSlot < slots) {
                this.drawSlice(context, centerX, centerY, radius, this.selectedSlot, slots, this.fade.apply(this.selectedColor.get().getPacked()));
            }

            this.drawRing(context, centerX, centerY, radius, radius - 2, border);
            this.drawRing(context, centerX, centerY, 18, 16, border);
            UUID playing = EmoteManager.getInstance().localPlaying();

            for (int i = 0; i < slots; i++) {
                this.drawSliceLabel(context, centerX, centerY, radius, i, slots, playing);
            }

            if (this.pageCount() > 1) {
                this.drawCenteredLabel(
                    context,
                    "Page " + (this.page + 1) + "/" + this.pageCount() + " (scroll)",
                    centerX,
                    centerY + radius + 6,
                    this.fade.apply(this.textColor.get().getPacked())
                );
            }

            this.drawCursor(context, centerX, centerY);
        }
    }

    private void drawSliceLabel(GuiGraphics context, int centerX, int centerY, int radius, int index, int slots, UUID playing) {
        String label;
        int color;
        if (index == 0) {
            label = "Stop";
            color = this.fade.apply(this.textColor.get().getPacked());
        } else {
            int emoteIndex = this.page * this.emotesPerPage() + index - 1;
            if (emoteIndex >= this.emotes.size()) {
                return;
            }

            EmoteManager.Emote emote = this.emotes.get(emoteIndex);
            label = emote.name();
            if (label.length() > 14) {
                label = label.substring(0, 12) + "..";
            }

            color = this.fade.apply((emote.uuid().equals(playing) ? this.playingColor : this.textColor).get().getPacked());
        }

        double midAngle = Math.toRadians(index * 360.0 / slots - 90.0);
        int labelRadius = radius * 2 / 3;
        int labelX = centerX + (int)(Math.cos(midAngle) * labelRadius);
        int labelY = centerY + (int)(Math.sin(midAngle) * labelRadius);
        this.drawCenteredLabel(context, label, labelX, labelY - (int)(9.0 * this.textScale.get() / 2.0), color);
    }

    private void drawCenteredLabel(GuiGraphics context, String label, int x, int y, int color) {
        float scale = this.textScale.get().floatValue();
        int textWidth = this.mc.font.width(label);
        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);
        context.drawString(this.mc.font, label, -textWidth / 2, 0, color, false);
        context.pose().popMatrix();
    }

    private void drawCursor(GuiGraphics context, int centerX, int centerY) {
        int x = centerX + (int)Math.round(this.cursorX);
        int y = centerY + (int)Math.round(this.cursorY);
        this.drawFilledCircle(context, x, y, 4, this.fade.apply(-16777216));
        this.drawFilledCircle(context, x, y, 2, this.fade.apply(this.textColor.get().getPacked()));
    }

    private void drawFilledCircle(GuiGraphics context, int centerX, int centerY, int radius, int packedColor) {
        int radiusSq = radius * radius;
        int runX = 0;
        int runTop = 0;

        for (int y = -radius; y <= radius; y++) {
            int xMax = (int)Math.sqrt(radiusSq - y * y);
            if (xMax != runX) {
                if (runX > 0) {
                    context.fill(centerX - runX, centerY + runTop, centerX + runX + 1, centerY + y, packedColor);
                }

                runX = xMax;
                runTop = y;
            }
        }

        if (runX > 0) {
            context.fill(centerX - runX, centerY + runTop, centerX + runX + 1, centerY + radius + 1, packedColor);
        }
    }

    private void drawRing(GuiGraphics context, int centerX, int centerY, int outerRadius, int innerRadius, int packedColor) {
        int outerSq = outerRadius * outerRadius;
        int innerSq = innerRadius * innerRadius;
        int runOuter = 0;
        int runInner = 0;
        int runTop = 0;

        for (int y = -outerRadius; y <= outerRadius; y++) {
            int ySq = y * y;
            int xOuter = (int)Math.sqrt(outerSq - ySq);
            int xInner = ySq < innerSq ? (int)Math.sqrt(innerSq - ySq) : 0;
            if (xOuter != runOuter || xInner != runInner) {
                this.fillRingRun(context, centerX, centerY, runOuter, runInner, runTop, y, packedColor);
                runOuter = xOuter;
                runInner = xInner;
                runTop = y;
            }
        }

        this.fillRingRun(context, centerX, centerY, runOuter, runInner, runTop, outerRadius + 1, packedColor);
    }

    private void fillRingRun(GuiGraphics context, int centerX, int centerY, int xOuter, int xInner, int top, int bottom, int packedColor) {
        if (xOuter > 0) {
            if (xInner <= 0) {
                context.fill(centerX - xOuter, centerY + top, centerX + xOuter + 1, centerY + bottom, packedColor);
            } else {
                context.fill(centerX - xOuter, centerY + top, centerX - xInner, centerY + bottom, packedColor);
                context.fill(centerX + xInner + 1, centerY + top, centerX + xOuter + 1, centerY + bottom, packedColor);
            }
        }
    }

    private void drawSlice(GuiGraphics context, int centerX, int centerY, int radius, int index, int slots, int packedColor) {
        double sliceAngle = (Math.PI * 2) / slots;
        double startAngle = index * sliceAngle - (Math.PI / 2) - sliceAngle / 2.0;
        double endAngle = startAngle + sliceAngle;
        double startNx = -Math.sin(startAngle);
        double startNy = Math.cos(startAngle);
        double endNx = Math.sin(endAngle);
        double endNy = -Math.cos(endAngle);
        int radiusSq = radius * radius;
        int innerSq = 324;

        for (int y = -radius; y <= radius; y++) {
            int ySq = y * y;
            int xMax = (int)Math.sqrt(radiusSq - ySq);
            if (xMax > 0) {
                double lo = -xMax;
                double hi = xMax;
                if (startNx > 1.0E-9) {
                    lo = Math.max(lo, -startNy * y / startNx);
                } else if (startNx < -1.0E-9) {
                    hi = Math.min(hi, -startNy * y / startNx);
                } else if (startNy * y < 0.0) {
                    continue;
                }

                if (endNx > 1.0E-9) {
                    lo = Math.max(lo, -endNy * y / endNx);
                } else if (endNx < -1.0E-9) {
                    hi = Math.min(hi, -endNy * y / endNx);
                } else if (endNy * y < 0.0) {
                    continue;
                }

                int xa = Math.max(-xMax, (int)Math.ceil(lo - 1.0E-9));
                int xb = Math.min(xMax, (int)Math.floor(hi + 1.0E-9));
                if (xa <= xb) {
                    int xInner = ySq < innerSq ? (int)Math.sqrt(innerSq - ySq) : 0;
                    if (xInner > 0 && xa <= xInner && xb >= -xInner) {
                        if (xa <= -xInner - 1) {
                            context.fill(centerX + xa, centerY + y, centerX - xInner, centerY + y + 1, packedColor);
                        }

                        if (xb >= xInner + 1) {
                            context.fill(centerX + xInner + 1, centerY + y, centerX + xb + 1, centerY + y + 1, packedColor);
                        }
                    } else {
                        context.fill(centerX + xa, centerY + y, centerX + xb + 1, centerY + y + 1, packedColor);
                    }
                }
            }
        }
    }
}
