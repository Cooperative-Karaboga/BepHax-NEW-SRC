package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.FadeAnimator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.KeybindSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class WheelPicker extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgSlots = this.settings.createGroup("Slot Actions");
    private final SettingGroup sgSpam = this.settings.createGroup("Spam Protection");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Keybind> activationKey = this.sgGeneral
        .add(new Builder().name("activation-key").description("Key to activate the wheel picker.").defaultValue(Keybind.fromKey(86)).build());
    private final Setting<Integer> wheelRadius = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("wheel-radius")
                .description("Radius of the wheel in pixels.")
                .defaultValue(200)
                .min(60)
                .max(500)
                .sliderRange(60, 500)
                .build()
        );
    private final Setting<Integer> wheelX = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("wheel-x-offset")
                .description("X offset from center of screen (negative = left, positive = right).")
                .defaultValue(0)
                .min(-500)
                .max(500)
                .sliderRange(-500, 500)
                .build()
        );
    private final Setting<Integer> wheelY = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("wheel-y-offset")
                .description("Y offset from center of screen (negative = up, positive = down).")
                .defaultValue(0)
                .min(-500)
                .max(500)
                .sliderRange(-500, 500)
                .build()
        );
    private final WheelPicker.SlotConfig[] slots = new WheelPicker.SlotConfig[8];
    private final Setting<Boolean> spamProtection = this.sgSpam
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("spam-protection")
                .description("Enable spam protection for messages.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> messageDelay = this.sgSpam
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("message-delay")
                .description("Minimum delay between messages in milliseconds.")
                .defaultValue(100)
                .min(100)
                .max(5000)
                .sliderRange(100, 5000)
                .visible(this.spamProtection::get)
                .build()
        );
    private final Setting<Boolean> insertRandomBrackets = this.sgSpam
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("insert-random-brackets")
                .description("Insert random text within [] brackets to bypass spam filters.")
                .defaultValue(true)
                .visible(this.spamProtection::get)
                .build()
        );
    private final Setting<SettingColor> backgroundColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("background-color")
                .description("Background color of wheel sections.")
                .defaultValue(new SettingColor(40, 40, 40, 120))
                .build()
        );
    private final Setting<SettingColor> selectedColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("selected-color")
                .description("Color of the selected section.")
                .defaultValue(new SettingColor(100, 200, 255, 160))
                .build()
        );
    private final Setting<SettingColor> textColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("text-color")
                .description("Text color for labels.")
                .defaultValue(new SettingColor(255, 255, 255, 255))
                .build()
        );
    private final Setting<SettingColor> moduleActiveColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("module-active-color")
                .description("Text color for active modules.")
                .defaultValue(new SettingColor(100, 255, 100, 255))
                .build()
        );
    private final Setting<Double> textScale = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("text-scale")
                .description("Scale of the text labels.")
                .defaultValue(2.0)
                .min(0.1)
                .max(5.0)
                .sliderRange(0.1, 3.0)
                .build()
        );
    private final Setting<Double> iconScale = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("icon-scale")
                .description("Scale of the item icons.")
                .defaultValue(2.0)
                .min(0.1)
                .max(5.0)
                .sliderRange(0.1, 3.0)
                .build()
        );
    private final Setting<Boolean> showIcons = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-icons")
                .description("Show item icons on the wheel.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showText = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-text")
                .description("Show text labels on the wheel.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> fadeAnimation = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("fade-animation")
                .description("Smoothly fade the wheel in and out instead of snapping when you open/close it.")
                .defaultValue(true)
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
                .visible(this.fadeAnimation::get)
                .build()
        );
    private final FadeAnimator fade = new FadeAnimator();
    private static boolean wheelActive = false;
    private int selectedSlot = -1;
    private long lastMessageTime = 0L;
    private double cursorX = 0.0;
    private double cursorY = 0.0;
    private double lastRawX = 0.0;
    private double lastRawY = 0.0;
    private final List<Module>[] cachedModules = new ArrayList[8];
    private final boolean[] cachedMajorityActive = new boolean[8];
    private final int[] cachedActiveCount = new int[8];
    private final int[] cachedTotalCount = new int[8];
    private long lastModuleCacheUpdate = 0L;
    private static final long MODULE_CACHE_INTERVAL = 100L;
    private static final String RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String[] SLOT_NAMES = new String[]{"Top", "Top-Right", "Right", "Bottom-Right", "Bottom", "Bottom-Left", "Left", "Top-Left"};
    private int pendingSlotAction = -1;
    private static final double DEAD_ZONE = 20.0;
    private static final int BORDER_WIDTH = 2;
    private static final double EDGE_EPSILON = 1.0E-9;

    public static boolean isWheelOpen() {
        return wheelActive;
    }

    public WheelPicker() {
        super(Bep.CATEGORY, "wheel-picker", "GTA-style wheel menu for quick macros and actions.");

        for (int i = 0; i < 8; i++) {
            this.slots[i] = new WheelPicker.SlotConfig(i);
        }
    }

    @Override
    public void onActivate() {
        wheelActive = false;
        this.selectedSlot = -1;
        this.pendingSlotAction = -1;
        this.cursorX = 0.0;
        this.cursorY = 0.0;
    }

    @Override
    public void onDeactivate() {
        wheelActive = false;
        this.selectedSlot = -1;
        this.pendingSlotAction = -1;
        this.cursorX = 0.0;
        this.cursorY = 0.0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.pendingSlotAction >= 0) {
                int slotToExecute = this.pendingSlotAction;
                this.pendingSlotAction = -1;
                this.executeSlotAction(slotToExecute);
            } else if (this.mc.screen != null) {
                wheelActive = false;
            } else {
                boolean keyPressed = this.activationKey.get().isPressed();
                if (keyPressed && !wheelActive) {
                    wheelActive = true;
                    this.selectedSlot = -1;
                    this.resetCursor();
                    KeyMapping.releaseAll();
                } else if (!keyPressed && wheelActive) {
                    if (this.selectedSlot >= 0 && this.selectedSlot < 8) {
                        this.pendingSlotAction = this.selectedSlot;
                    }

                    wheelActive = false;
                }

                if (wheelActive) {
                    this.updateCursor();
                }
            }
        }
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

        if (distance < 20.0) {
            this.selectedSlot = -1;
        } else {
            double angle = Math.atan2(this.cursorY, this.cursorX);
            double degrees = Math.toDegrees(angle);
            if (degrees < 0.0) {
                degrees += 360.0;
            }

            degrees = (degrees + 90.0) % 360.0;
            this.selectedSlot = (int)((degrees + 22.5) / 45.0) % 8;
        }
    }

    private void executeSlotAction(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < 8) {
            WheelPicker.SlotConfig slot = this.slots[slotIndex];
            WheelPicker.MacroAction action = slot.action.get();
            if (action != WheelPicker.MacroAction.NONE) {
                switch (action) {
                    case TOGGLE_MODULE:
                        String moduleNames = slot.moduleName.get();
                        if (!moduleNames.isEmpty()) {
                            String[] names = moduleNames.split(",");
                            List<Module> validModules = new ArrayList<>();
                            int enabledCount = 0;

                            for (String name : names) {
                                String trimmedName = name.trim();
                                if (!trimmedName.isEmpty()) {
                                    Module module = Modules.get().get(trimmedName);
                                    if (module != null) {
                                        validModules.add(module);
                                        if (module.isActive()) {
                                            enabledCount++;
                                        }
                                    } else {
                                        this.warning("Module not found: " + trimmedName);
                                    }
                                }
                            }

                            if (!validModules.isEmpty()) {
                                boolean targetState = enabledCount <= validModules.size() / 2;
                                StringBuilder result = new StringBuilder();

                                for (Module module : validModules) {
                                    if (module.isActive() != targetState) {
                                        module.toggle();
                                        module.sendToggledMsg();
                                    }

                                    if (result.length() > 0) {
                                        result.append(", ");
                                    }

                                    result.append(module.name);
                                }

                                this.info(String.format("%s: %s", result.toString(), targetState ? "§aENABLED" : "§cDISABLED"));
                            }
                        }
                        break;
                    case SEND_MESSAGE:
                        String message = slot.message.get();
                        if (!message.isEmpty()) {
                            this.sendMessageWithProtection(message);
                        }
                        break;
                    case RUN_COMMAND:
                        String command = slot.command.get();
                        if (!command.isEmpty()) {
                            if (this.mc.player.connection == null) {
                                return;
                            }

                            if (this.spamProtection.get()) {
                                command = this.applyRandomPlaceholderOnly(command);
                            }

                            this.mc.player.connection.sendCommand(command);
                        }
                }
            }
        }
    }

    private void sendMessageWithProtection(String message) {
        if (this.mc.player.connection != null) {
            if (this.spamProtection.get()) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - this.lastMessageTime < this.messageDelay.get().intValue()) {
                    this.warning("Message blocked by spam protection.");
                    return;
                }

                this.lastMessageTime = currentTime;
                message = this.applyRandomSubstitution(message);
                String[] invisibleChars = new String[]{"\u200b", "\u200c", "\u200d"};
                message = message + invisibleChars[ThreadLocalRandom.current().nextInt(invisibleChars.length)];
            }

            this.mc.player.connection.sendChat(message);
        }
    }

    private String applyRandomPlaceholderOnly(String text) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        while (text.contains("[RANDOM]")) {
            String randomString = this.generateRandomString(5 + random.nextInt(4));
            text = text.replaceFirst("\\[RANDOM\\]", randomString);
        }

        return text;
    }

    private String applyRandomSubstitution(String text) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        while (text.contains("[RANDOM]")) {
            String randomString = this.generateRandomString(5 + random.nextInt(4));
            text = text.replaceFirst("\\[RANDOM\\]", randomString);
        }

        if (this.insertRandomBrackets.get()) {
            StringBuilder result = new StringBuilder();
            int lastEnd = 0;

            for (int bracketStart = text.indexOf(91); bracketStart != -1; bracketStart = text.indexOf(91, lastEnd)) {
                int bracketEnd = text.indexOf(93, bracketStart);
                if (bracketEnd == -1) {
                    break;
                }

                result.append(text.substring(lastEnd, bracketStart));
                String randomString = this.generateRandomString(3 + random.nextInt(3));
                result.append("[").append(randomString).append("]");
                lastEnd = bracketEnd + 1;
            }

            result.append(text.substring(lastEnd));
            text = result.toString();
        }

        return text;
    }

    private String generateRandomString(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                    .charAt(random.nextInt("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".length()))
            );
        }

        return sb.toString();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        this.fade.update(wheelActive, this.fadeDuration.get(), this.fadeAnimation.get());
        if (wheelActive || this.fade.rendering()) {
            if (wheelActive) {
                this.updateCursor();
            }

            GuiGraphics context = event.drawContext;
            int scaledWidth = this.mc.getWindow().getGuiScaledWidth();
            int scaledHeight = this.mc.getWindow().getGuiScaledHeight();
            int centerX = scaledWidth / 2 + this.wheelX.get();
            int centerY = scaledHeight / 2 + this.wheelY.get();
            int radius = this.wheelRadius.get();
            this.renderWheel(context, centerX, centerY, radius);
        }
    }

    private void renderWheel(GuiGraphics context, int centerX, int centerY, int radius) {
        this.updateModuleCache();
        int background = this.fade.apply(this.backgroundColor.get().getPacked());
        int border = this.fade.apply(-1879048192);
        this.drawFilledCircle(context, centerX, centerY, radius, background);
        if (this.selectedSlot >= 0 && this.selectedSlot < 8) {
            this.drawWheelSection(context, centerX, centerY, radius, this.selectedSlot);
        }

        this.drawRing(context, centerX, centerY, radius, radius - 2, border);
        this.drawRing(context, centerX, centerY, 20, 18, border);

        for (int i = 0; i < 8; i++) {
            this.drawSectionLabel(context, centerX, centerY, radius, i);
        }

        this.drawCursor(context, centerX, centerY);
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

    private void drawWheelSection(GuiGraphics context, int centerX, int centerY, int radius, int sectionIndex) {
        double startAngle = Math.toRadians(sectionIndex * 45 - 90 - 22.5);
        double endAngle = startAngle + Math.toRadians(45.0);
        int packedColor = this.fade.apply(this.selectedColor.get().getPacked());
        double startNx = -Math.sin(startAngle);
        double startNy = Math.cos(startAngle);
        double endNx = Math.sin(endAngle);
        double endNy = -Math.cos(endAngle);
        int radiusSq = radius * radius;
        int innerSq = 400;

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

    private void updateModuleCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastModuleCacheUpdate >= 100L) {
            this.lastModuleCacheUpdate = currentTime;

            for (int i = 0; i < 8; i++) {
                WheelPicker.SlotConfig slot = this.slots[i];
                if (slot.action.get() == WheelPicker.MacroAction.TOGGLE_MODULE && !slot.moduleName.get().isEmpty()) {
                    String moduleNames = slot.moduleName.get();
                    String[] names = moduleNames.split(",");
                    if (this.cachedModules[i] == null) {
                        this.cachedModules[i] = new ArrayList<>();
                    }

                    this.cachedModules[i].clear();
                    int activeCount = 0;

                    for (String name : names) {
                        String trimmedName = name.trim();
                        if (!trimmedName.isEmpty()) {
                            Module module = Modules.get().get(trimmedName);
                            if (module != null) {
                                this.cachedModules[i].add(module);
                                if (module.isActive()) {
                                    activeCount++;
                                }
                            }
                        }
                    }

                    this.cachedActiveCount[i] = activeCount;
                    this.cachedTotalCount[i] = this.cachedModules[i].size();
                    this.cachedMajorityActive[i] = this.cachedTotalCount[i] > 0 && activeCount > this.cachedTotalCount[i] / 2;
                } else {
                    if (this.cachedModules[i] != null) {
                        this.cachedModules[i].clear();
                    }

                    this.cachedMajorityActive[i] = false;
                    this.cachedActiveCount[i] = 0;
                    this.cachedTotalCount[i] = 0;
                }
            }
        }
    }

    private void drawSectionLabel(GuiGraphics context, int centerX, int centerY, int radius, int sectionIndex) {
        WheelPicker.SlotConfig slot = this.slots[sectionIndex];
        double midAngle = Math.toRadians(sectionIndex * 45 - 90);
        int labelRadius = radius * 2 / 3;
        int labelX = centerX + (int)(Math.cos(midAngle) * labelRadius);
        int labelY = centerY + (int)(Math.sin(midAngle) * labelRadius);
        boolean isModuleActive = slot.action.get() == WheelPicker.MacroAction.TOGGLE_MODULE
            && this.cachedModules[sectionIndex] != null
            && !this.cachedModules[sectionIndex].isEmpty()
            && this.cachedMajorityActive[sectionIndex];
        boolean hasIcon = this.showIcons.get() && slot.icon.get() != Items.AIR;
        boolean hasText = this.showText.get() && !this.getSlotLabel(slot, sectionIndex).isEmpty();
        if (hasIcon || hasText) {
            float iconScaleValue = this.iconScale.get().floatValue();
            float textScaleValue = this.textScale.get().floatValue();
            int iconSize = (int)(16.0F * iconScaleValue);
            int spacing = 2;
            String label = this.getSlotLabel(slot, sectionIndex);
            int textHeight = (int)(9.0F * textScaleValue);
            int totalHeight = 0;
            if (hasIcon) {
                totalHeight += iconSize;
            }

            if (hasIcon && hasText) {
                totalHeight += spacing;
            }

            if (hasText) {
                totalHeight += textHeight;
            }

            int currentY = labelY - totalHeight / 2;
            if (hasIcon) {
                Item item = slot.icon.get();
                ItemStack stack = new ItemStack(item);
                context.pose().pushMatrix();
                context.pose().translate(labelX, currentY);
                context.pose().scale(iconScaleValue, iconScaleValue);
                context.renderItem(stack, -8, 0);
                context.pose().popMatrix();
                currentY += iconSize + spacing;
            }

            if (hasText) {
                Color textColor = isModuleActive ? this.moduleActiveColor.get() : this.textColor.get();
                int textWidth = this.mc.font.width(label);
                context.pose().pushMatrix();
                context.pose().translate(labelX, currentY);
                context.pose().scale(textScaleValue, textScaleValue);
                context.drawString(this.mc.font, label, -textWidth / 2, 0, this.fade.apply(textColor.getPacked()), false);
                context.pose().popMatrix();
            }
        }
    }

    private String getSlotLabel(WheelPicker.SlotConfig slot, int slotIndex) {
        WheelPicker.MacroAction action = slot.action.get();
        if (action == WheelPicker.MacroAction.NONE) {
            return "";
        }

        String custom = slot.customText.get();
        if (!custom.isEmpty()) {
            return custom;
        }

        switch (action) {
            case TOGGLE_MODULE:
                String moduleStr = slot.moduleName.get();
                if (moduleStr.isEmpty()) {
                    return "";
                } else {
                    if (this.cachedModules[slotIndex] != null && !this.cachedModules[slotIndex].isEmpty()) {
                        int total = this.cachedTotalCount[slotIndex];
                        int active = this.cachedActiveCount[slotIndex];
                        String state = total > 1 ? " (" + active + "/" + total + ")" : (this.cachedMajorityActive[slotIndex] ? " ✓" : "");
                        String displayName;
                        if (total > 1) {
                            displayName = total + " modules";
                        } else {
                            String firstModule = this.cachedModules[slotIndex].get(0).name;
                            displayName = firstModule.length() > 8 ? firstModule.substring(0, 8) : firstModule;
                        }

                        return displayName + state;
                    }

                    return moduleStr.length() > 10 ? moduleStr.substring(0, 8) + ".." : moduleStr;
                }
            case SEND_MESSAGE:
                String msg = slot.message.get();
                return msg.isEmpty() ? "" : (msg.length() > 10 ? msg.substring(0, 8) + ".." : msg);
            case RUN_COMMAND:
                String cmd = slot.command.get();
                return cmd.isEmpty() ? "" : "/" + (cmd.length() > 9 ? cmd.substring(0, 7) + ".." : cmd);
            default:
                return "";
        }
    }

    public enum MacroAction {
        NONE("None"),
        TOGGLE_MODULE("Toggle Module"),
        SEND_MESSAGE("Send Message"),
        RUN_COMMAND("Run Command");

        private final String name;

        MacroAction(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    private class SlotConfig {
        public final Setting<WheelPicker.MacroAction> action;
        public final Setting<String> moduleName;
        public final Setting<String> message;
        public final Setting<String> command;
        public final Setting<String> customText;
        public final Setting<Item> icon;

        public SlotConfig(int index) {
            String slotName = WheelPicker.SLOT_NAMES[index];
            this.action = WheelPicker.this.sgSlots
                .add(
                    ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                                    .name(slotName.toLowerCase().replace("-", "") + "-action"))
                                .description("Action for " + slotName + " slot"))
                            .defaultValue(WheelPicker.MacroAction.NONE))
                        .build()
                );
            this.icon = WheelPicker.this.sgSlots
                .add(
                    new meteordevelopment.meteorclient.settings.ItemSetting.Builder()
                        .name(slotName.toLowerCase().replace("-", "") + "-icon")
                        .description("Icon item for " + slotName + " slot")
                        .defaultValue(Items.AIR)
                        .visible(() -> this.action.get() != WheelPicker.MacroAction.NONE)
                        .build()
                );
            this.customText = WheelPicker.this.sgSlots
                .add(
                    new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                        .name(slotName.toLowerCase().replace("-", "") + "-custom-text")
                        .description("Custom display text for " + slotName + " slot (leave empty for auto)")
                        .defaultValue("")
                        .visible(() -> this.action.get() != WheelPicker.MacroAction.NONE)
                        .build()
                );
            this.moduleName = WheelPicker.this.sgSlots
                .add(
                    new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                        .name(slotName.toLowerCase().replace("-", "") + "-module")
                        .description("Module name(s) for " + slotName + " (comma-separated for multiple, toggles based on majority state)")
                        .defaultValue("")
                        .visible(() -> this.action.get() == WheelPicker.MacroAction.TOGGLE_MODULE)
                        .build()
                );
            this.message = WheelPicker.this.sgSlots
                .add(
                    new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                        .name(slotName.toLowerCase().replace("-", "") + "-message")
                        .description("Message for " + slotName + " (use [] or [RANDOM] for random text)")
                        .defaultValue("")
                        .visible(() -> this.action.get() == WheelPicker.MacroAction.SEND_MESSAGE)
                        .build()
                );
            this.command = WheelPicker.this.sgSlots
                .add(
                    new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                        .name(slotName.toLowerCase().replace("-", "") + "-command")
                        .description("Command for " + slotName + " (without /)")
                        .defaultValue("")
                        .visible(() -> this.action.get() == WheelPicker.MacroAction.RUN_COMMAND)
                        .build()
                );
        }
    }
}
