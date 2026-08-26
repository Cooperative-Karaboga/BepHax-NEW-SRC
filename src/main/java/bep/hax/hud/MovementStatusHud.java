package bep.hax.hud;

import bep.hax.Bep;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MovementStatusHud extends HudElement {
    public static final HudElementInfo<MovementStatusHud> INFO = new HudElementInfo<>(
        Bep.HUD_GROUP, "movement-status", "Displays your current sneaking and sprinting status.", MovementStatusHud::new
    );
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgText = this.settings.createGroup("Text");
    private final SettingGroup sgActionBar = this.settings.createGroup("Action Bar");
    private final SettingGroup sgColors = this.settings.createGroup("Colors");
    private final Setting<MovementStatusHud.DisplayMode> displayMode = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("display-mode")).description("How to display the movement status."))
                    .defaultValue(MovementStatusHud.DisplayMode.Text))
                .build()
        );
    private final Setting<Boolean> showSneaking = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-sneaking")
                .description("Display sneaking status.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showSprinting = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-sprinting")
                .description("Display sprinting status.")
                .defaultValue(true)
                .build()
        );
    private final Setting<MovementStatusHud.Layout> layout = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("layout")).description("Layout orientation for Text/Icon modes."))
                        .defaultValue(MovementStatusHud.Layout.Vertical))
                    .visible(() -> this.displayMode.get() != MovementStatusHud.DisplayMode.ActionBar))
                .build()
        );
    private final Setting<String> sneakingText = this.sgText
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("sneaking-text")
                .description("Text to display when sneaking.")
                .defaultValue("[Sneaking]")
                .visible(
                    () -> this.displayMode.get() == MovementStatusHud.DisplayMode.Text || this.displayMode.get() == MovementStatusHud.DisplayMode.ActionBar
                )
                .build()
        );
    private final Setting<String> sprintingText = this.sgText
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("sprinting-text")
                .description("Text to display when sprinting.")
                .defaultValue("[Sprinting]")
                .visible(
                    () -> this.displayMode.get() == MovementStatusHud.DisplayMode.Text || this.displayMode.get() == MovementStatusHud.DisplayMode.ActionBar
                )
                .build()
        );
    private final Setting<Double> textScale = this.sgText
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("text-scale")
                .description("Scale of the text.")
                .defaultValue(1.0)
                .min(0.5)
                .max(3.0)
                .sliderRange(0.5, 3.0)
                .visible(() -> this.displayMode.get() == MovementStatusHud.DisplayMode.Text)
                .build()
        );
    private final Setting<Boolean> textShadow = this.sgText
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("text-shadow")
                .description("Render shadow behind text.")
                .defaultValue(true)
                .visible(() -> this.displayMode.get() == MovementStatusHud.DisplayMode.Text)
                .build()
        );
    private final Setting<Double> iconScale = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("icon-scale")
                .description("Scale of the icons.")
                .defaultValue(1.0)
                .min(0.5)
                .max(3.0)
                .sliderRange(0.5, 3.0)
                .visible(() -> this.displayMode.get() == MovementStatusHud.DisplayMode.Icon)
                .build()
        );
    private final Setting<String> actionBarSeparator = this.sgActionBar
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("separator")
                .description("Separator between sneaking and sprinting text.")
                .defaultValue(" ")
                .visible(() -> this.displayMode.get() == MovementStatusHud.DisplayMode.ActionBar)
                .build()
        );
    private final Setting<String> actionBarPrefix = this.sgActionBar
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("prefix")
                .description("Text to show before the status.")
                .defaultValue("")
                .visible(() -> this.displayMode.get() == MovementStatusHud.DisplayMode.ActionBar)
                .build()
        );
    private final Setting<String> actionBarSuffix = this.sgActionBar
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("suffix")
                .description("Text to show after the status.")
                .defaultValue("")
                .visible(() -> this.displayMode.get() == MovementStatusHud.DisplayMode.ActionBar)
                .build()
        );
    private final Setting<Integer> actionBarFadeDelay = this.sgActionBar
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("fade-delay")
                .description("How long in ticks the action bar message stays visible (20 ticks = 1 second). Set to 0 for instant clear when not active.")
                .defaultValue(20)
                .min(0)
                .max(100)
                .sliderRange(0, 100)
                .visible(() -> this.displayMode.get() == MovementStatusHud.DisplayMode.ActionBar)
                .build()
        );
    private final Setting<SettingColor> sneakingColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("sneaking-color")
                .description("Color for sneaking status.")
                .defaultValue(new SettingColor(100, 200, 255, 255))
                .build()
        );
    private final Setting<SettingColor> sprintingColor = this.sgColors
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("sprinting-color")
                .description("Color for sprinting status.")
                .defaultValue(new SettingColor(255, 200, 0, 255))
                .build()
        );
    private ItemStack sprintIcon = null;
    private ItemStack sneakIcon = null;
    private long lastActionBarTime = 0L;
    private long lastActionBarSendTime = 0L;
    private String lastActionBarMessage = "";
    private boolean wasActive = false;

    public MovementStatusHud() {
        super(INFO);
    }

    @Override
    public void toggle() {
        if (this.isActive()) {
            this.clearActionBar();
        }

        super.toggle();
    }

    @Override
    public void tick(HudRenderer renderer) {
        if (MeteorClient.mc.level != null && MeteorClient.mc.player != null) {
            if (this.displayMode.get() == MovementStatusHud.DisplayMode.ActionBar && !this.isInEditor()) {
                this.handleActionBar(MeteorClient.mc.player.isShiftKeyDown(), MeteorClient.mc.player.isSprinting());
            } else {
                this.clearActionBar();
            }
        } else {
            this.wasActive = false;
            this.lastActionBarMessage = "";
        }
    }

    private void initIcons() {
        if (this.sprintIcon == null) {
            this.sprintIcon = new ItemStack(Items.FEATHER);
        }

        if (this.sneakIcon == null) {
            this.sneakIcon = new ItemStack(Items.LEATHER_BOOTS);
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.level != null && MeteorClient.mc.player != null) {
            boolean isSneaking = MeteorClient.mc.player.isShiftKeyDown();
            boolean isSprinting = MeteorClient.mc.player.isSprinting();
            if (this.displayMode.get() == MovementStatusHud.DisplayMode.ActionBar) {
                if (this.isInEditor()) {
                    this.renderEditorPreview(renderer);
                } else {
                    this.setSize(0.0, 0.0);
                }
            } else if (this.isInEditor()) {
                this.renderEditorPreview(renderer);
            } else {
                boolean showSneak = this.showSneaking.get() && isSneaking;
                boolean showSprint = this.showSprinting.get() && isSprinting;
                if (!showSneak && !showSprint) {
                    switch ((MovementStatusHud.DisplayMode)this.displayMode.get()) {
                        case Text:
                            String longestText = this.sneakingText.get().length() > this.sprintingText.get().length()
                                ? this.sneakingText.get()
                                : this.sprintingText.get();
                            this.setSize(
                                renderer.textWidth(longestText, this.textShadow.get(), this.textScale.get()),
                                renderer.textHeight(this.textShadow.get(), this.textScale.get())
                            );
                            break;
                        case Icon:
                            this.initIcons();
                            double iconSize = 16.0 * this.iconScale.get();
                            this.setSize(iconSize, iconSize);
                    }
                } else {
                    switch ((MovementStatusHud.DisplayMode)this.displayMode.get()) {
                        case Text:
                            this.renderTextMode(renderer, showSneak, showSprint);
                            break;
                        case Icon:
                            this.renderIconMode(renderer, showSneak, showSprint);
                    }
                }
            }
        } else {
            if (this.isInEditor()) {
                this.renderEditorPreview(renderer);
            } else {
                this.setSize(0.0, 0.0);
            }
        }
    }

    private void renderEditorPreview(HudRenderer renderer) {
        switch ((MovementStatusHud.DisplayMode)this.displayMode.get()) {
            case Text:
            {
                double currentY = this.y;
                double maxW = 0.0;
                double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
                if (this.layout.get() == MovementStatusHud.Layout.Vertical) {
                    if (this.showSneaking.get()) {
                        renderer.text(this.sneakingText.get(), this.x, currentY, this.sneakingColor.get(), this.textShadow.get(), this.textScale.get());
                        maxW = Math.max(maxW, renderer.textWidth(this.sneakingText.get(), this.textShadow.get(), this.textScale.get()));
                        currentY += textHeight + 2.0;
                    }

                    if (this.showSprinting.get()) {
                        renderer.text(this.sprintingText.get(), this.x, currentY, this.sprintingColor.get(), this.textShadow.get(), this.textScale.get());
                        maxW = Math.max(maxW, renderer.textWidth(this.sprintingText.get(), this.textShadow.get(), this.textScale.get()));
                        currentY += textHeight + 2.0;
                    }

                    this.setSize(maxW, currentY - this.y > 0.0 ? currentY - this.y - 2.0 : 0.0);
                } else {
                    double currentX = this.x;
                    if (this.showSneaking.get()) {
                        renderer.text(this.sneakingText.get(), currentX, this.y, this.sneakingColor.get(), this.textShadow.get(), this.textScale.get());
                        currentX += renderer.textWidth(this.sneakingText.get(), this.textShadow.get(), this.textScale.get()) + 8.0;
                    }

                    if (this.showSprinting.get()) {
                        renderer.text(this.sprintingText.get(), currentX, this.y, this.sprintingColor.get(), this.textShadow.get(), this.textScale.get());
                        currentX += renderer.textWidth(this.sprintingText.get(), this.textShadow.get(), this.textScale.get()) + 8.0;
                    }

                    this.setSize(currentX - this.x > 0.0 ? currentX - this.x - 8.0 : 0.0, textHeight);
                }
                break;
            }
            case Icon:
            {
                this.initIcons();
                double iconSize = 16.0 * this.iconScale.get();
                float iScale = this.iconScale.get().floatValue();
                ItemStack sneak = this.sneakIcon;
                ItemStack sprint = this.sprintIcon;
                if (this.layout.get() == MovementStatusHud.Layout.Vertical) {
                    double currentY = this.y;
                    if (this.showSneaking.get()) {
                        int ix = this.x;
                        int iy = (int)currentY;
                        renderer.post(() -> renderer.item(sneak, ix, iy, iScale, true));
                        currentY += iconSize + 2.0;
                    }

                    if (this.showSprinting.get()) {
                        int ix = this.x;
                        int iy = (int)currentY;
                        renderer.post(() -> renderer.item(sprint, ix, iy, iScale, true));
                        currentY += iconSize + 2.0;
                    }

                    this.setSize(iconSize, currentY - this.y > 0.0 ? currentY - this.y - 2.0 : 0.0);
                } else {
                    double currentX = this.x;
                    if (this.showSneaking.get()) {
                        int ix = (int)currentX;
                        int iy = this.y;
                        renderer.post(() -> renderer.item(sneak, ix, iy, iScale, true));
                        currentX += iconSize + 4.0;
                    }

                    if (this.showSprinting.get()) {
                        int ix = (int)currentX;
                        int iy = this.y;
                        renderer.post(() -> renderer.item(sprint, ix, iy, iScale, true));
                        currentX += iconSize + 4.0;
                    }

                    this.setSize(currentX - this.x > 0.0 ? currentX - this.x - 4.0 : 0.0, iconSize);
                }
                break;
            }
            case ActionBar:
            {
                renderer.text("[ActionBar Mode]", this.x, this.y, this.sneakingColor.get(), this.textShadow.get(), this.textScale.get());
                this.setSize(
                    renderer.textWidth("[ActionBar Mode]", this.textShadow.get(), this.textScale.get()),
                    renderer.textHeight(this.textShadow.get(), this.textScale.get())
                );
            }
        }
    }

    private void renderTextMode(HudRenderer renderer, boolean showSneak, boolean showSprint) {
        double textHeight = renderer.textHeight(this.textShadow.get(), this.textScale.get());
        double maxW = 0.0;
        if (this.layout.get() == MovementStatusHud.Layout.Vertical) {
            double currentY = this.y;
            if (showSneak) {
                renderer.text(this.sneakingText.get(), this.x, currentY, this.sneakingColor.get(), this.textShadow.get(), this.textScale.get());
                maxW = Math.max(maxW, renderer.textWidth(this.sneakingText.get(), this.textShadow.get(), this.textScale.get()));
                currentY += textHeight + 2.0;
            }

            if (showSprint) {
                renderer.text(this.sprintingText.get(), this.x, currentY, this.sprintingColor.get(), this.textShadow.get(), this.textScale.get());
                maxW = Math.max(maxW, renderer.textWidth(this.sprintingText.get(), this.textShadow.get(), this.textScale.get()));
                currentY += textHeight + 2.0;
            }

            this.setSize(maxW, currentY - this.y > 0.0 ? currentY - this.y - 2.0 : 0.0);
        } else {
            double currentX = this.x;
            if (showSneak) {
                renderer.text(this.sneakingText.get(), currentX, this.y, this.sneakingColor.get(), this.textShadow.get(), this.textScale.get());
                currentX += renderer.textWidth(this.sneakingText.get(), this.textShadow.get(), this.textScale.get()) + 8.0;
            }

            if (showSprint) {
                renderer.text(this.sprintingText.get(), currentX, this.y, this.sprintingColor.get(), this.textShadow.get(), this.textScale.get());
                currentX += renderer.textWidth(this.sprintingText.get(), this.textShadow.get(), this.textScale.get()) + 8.0;
            }

            this.setSize(currentX - this.x > 0.0 ? currentX - this.x - 8.0 : 0.0, textHeight);
        }
    }

    private void renderIconMode(HudRenderer renderer, boolean showSneak, boolean showSprint) {
        this.initIcons();
        double iconSize = 16.0 * this.iconScale.get();
        float iScale = this.iconScale.get().floatValue();
        ItemStack sneak = this.sneakIcon;
        ItemStack sprint = this.sprintIcon;
        if (this.layout.get() == MovementStatusHud.Layout.Vertical) {
            double currentY = this.y;
            if (showSneak) {
                int ix = this.x;
                int iy = (int)currentY;
                renderer.post(() -> renderer.item(sneak, ix, iy, iScale, true));
                currentY += iconSize + 2.0;
            }

            if (showSprint) {
                int ix = this.x;
                int iy = (int)currentY;
                renderer.post(() -> renderer.item(sprint, ix, iy, iScale, true));
                currentY += iconSize + 2.0;
            }

            this.setSize(iconSize, currentY - this.y > 0.0 ? currentY - this.y - 2.0 : 0.0);
        } else {
            double currentX = this.x;
            if (showSneak) {
                int ix = (int)currentX;
                int iy = this.y;
                renderer.post(() -> renderer.item(sneak, ix, iy, iScale, true));
                currentX += iconSize + 4.0;
            }

            if (showSprint) {
                int ix = (int)currentX;
                int iy = this.y;
                renderer.post(() -> renderer.item(sprint, ix, iy, iScale, true));
                currentX += iconSize + 4.0;
            }

            this.setSize(currentX - this.x > 0.0 ? currentX - this.x - 4.0 : 0.0, iconSize);
        }
    }

    private void handleActionBar(boolean isSneaking, boolean isSprinting) {
        if (MeteorClient.mc.player != null) {
            boolean isActive = this.showSneaking.get() && isSneaking || this.showSprinting.get() && isSprinting;
            long currentTime = System.currentTimeMillis();
            long fadeDelayMs = this.actionBarFadeDelay.get().intValue() * 50L;
            if (isActive) {
                MutableComponent message = Component.literal(this.actionBarPrefix.get());
                boolean addedSomething = false;
                if (this.showSneaking.get() && isSneaking) {
                    message.append(
                        Component.literal(this.sneakingText.get())
                            .withStyle(style -> style.withColor(this.sneakingColor.get().getPacked() & 16777215))
                    );
                    addedSomething = true;
                }

                if (this.showSprinting.get() && isSprinting) {
                    if (addedSomething) {
                        message.append(Component.literal(this.actionBarSeparator.get()));
                    }

                    message.append(
                        Component.literal(this.sprintingText.get())
                            .withStyle(style -> style.withColor(this.sprintingColor.get().getPacked() & 16777215))
                    );
                }

                message.append(Component.literal(this.actionBarSuffix.get()));
                String finalMessage = message.getString();
                if (finalMessage.isEmpty()) {
                    this.clearActionBar();
                    return;
                }

                if (!finalMessage.equals(this.lastActionBarMessage) || currentTime - this.lastActionBarSendTime >= 1000L) {
                    MeteorClient.mc.player.displayClientMessage(message, true);
                    this.lastActionBarMessage = finalMessage;
                    this.lastActionBarSendTime = currentTime;
                }

                this.lastActionBarTime = currentTime;
                this.wasActive = true;
            } else if (this.wasActive && currentTime - this.lastActionBarTime >= fadeDelayMs) {
                this.clearActionBar();
            }
        }
    }

    private void clearActionBar() {
        if (this.wasActive && MeteorClient.mc.player != null) {
            MeteorClient.mc.player.displayClientMessage(Component.literal(""), true);
        }

        this.wasActive = false;
        this.lastActionBarMessage = "";
        this.lastActionBarSendTime = 0L;
    }

    public enum DisplayMode {
        Text,
        Icon,
        ActionBar;
    }

    public enum Layout {
        Vertical,
        Horizontal;
    }
}
