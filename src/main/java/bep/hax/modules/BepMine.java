package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.managers.SwapManager;
import bep.hax.util.FadeAnimator;
import bep.hax.util.GrimUtils;
import bep.hax.util.RotationUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BepMine extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgAutoMine = this.settings.createGroup("Auto Mine");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<BepMine.SpeedmineMode> modeConfig = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("mode")).description("The mining mode for speedmine")).defaultValue(BepMine.SpeedmineMode.PACKET))
                .build()
        );
    private final Setting<Boolean> multitaskConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("multitask")
                .description("Keeps mining while using items. Flags Grim's MultiActions checks on updated servers - leave off for 2b2t.")
                .defaultValue(false)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET)
                .build()
        );
    public final Setting<Boolean> doubleBreakConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("double-break")
                .description(
                    "Mine two blocks at once: clicking a second block hands the first to the server's own break timer (it finishes on schedule by itself) while the new block mines in parallel. Queued clicks keep feeding the second slot, so a full queue mines two blocks at a time the whole way. Fast blocks hand off instantly; obsidian-class blocks need ~half their progress first or the anticheat eats the handoff - click the slow block first for the best overlap."
                )
                .defaultValue(true)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET)
                .build()
        );
    private final Setting<Boolean> clickQueueConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("click-queue")
                .description(
                    "Clicking more blocks while mining queues them (mined in order; with double-break on the queue keeps two breaking at once) instead of replacing the current block."
                )
                .defaultValue(true)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET)
                .build()
        );
    private final Setting<Integer> queueLimitConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("queue-limit")
                .description("Maximum blocks the click queue holds; extra clicks are ignored.")
                .defaultValue(10)
                .min(1)
                .max(200)
                .sliderRange(1, 20)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET && this.clickQueueConfig.get())
                .build()
        );
    private final Setting<Keybind> clearQueueKey = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("clear-queue-key")
                .description("Cancels the current mine(s) and clears the click queue when pressed.")
                .defaultValue(Keybind.none())
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET)
                .build()
        );
    private final Setting<Double> rangeConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("range")
                .description(
                    "The range to mine blocks, measured to the closest point of the block like Grim does. Above ~4.5 (the survival block-interaction range) updated Grim's FarBreak cancels digs."
                )
                .defaultValue(6.0)
                .min(0.1)
                .sliderRange(0.1, 6.0)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET)
                .build()
        );
    private final Setting<Double> speedConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("speed")
                .description(
                    "Break progress at which the break packet is sent. 0.7 is the server's own immediate-destroy line - the fastest a block can legally pop - and needs grim-bypass on. 1.0 = exact vanilla timing."
                )
                .defaultValue(0.7)
                .min(0.01)
                .max(2.0)
                .sliderRange(0.1, 1.5)
                .build()
        );
    private final Setting<Integer> breakDelayConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("break-delay")
                .description(
                    "Minimum ticks between finishing a break and starting the next block. 0 lets the mirrored Grim start-spacing budget set the pace on its own (bursts, then settles near 275ms); raise it only to be deliberately slower."
                )
                .defaultValue(0)
                .min(0)
                .max(10)
                .sliderRange(0, 10)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET)
                .build()
        );
    private final Setting<Integer> breakDelayRandomConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("break-delay-random")
                .description(
                    "Extra random ticks (0 to this) added to break-delay per block, so queued blocks don't break at a constant rhythm. Only ever adds delay - never dips under break-delay. 0 = off."
                )
                .defaultValue(0)
                .min(0)
                .max(10)
                .sliderRange(0, 10)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET)
                .build()
        );
    private final Setting<BepMine.GrimBypass> grimBypassConfig = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("grim-bypass"))
                            .description(
                                "Sends a throwaway dig above the build limit (the server drops it, Grim reads it as air) so Grim's predicted break time collapses and breaking under it stops accruing. AUTO only spends one when the timing would otherwise be cancelled - the price is one AirLiquidBreak flag per bypassed block. Required for speed below 1.0 and for double-break on obsidian-class blocks."
                            ))
                        .defaultValue(BepMine.GrimBypass.ALWAYS))
                    .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET))
                .build()
        );
    private final Setting<BepMine.SwingMode> swingConfig = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("swing"))
                            .description(
                                "Hand swing while mining: FULL = visible swing, PACKET = server-side only (no animation), NONE = no swinging (flags updated Grim's NoSwingBreak; the mining tick packet then carries Grim's break-speed sampling instead)."
                            ))
                        .defaultValue(BepMine.SwingMode.PACKET))
                    .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET))
                .build()
        );
    private final Setting<Boolean> instantConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("instant")
                .description(
                    "Re-breaks the last mined block when it gets replaced (city block). Instant while the anticheat's own bookkeeping makes the re-break free (the pos was seen air, which collapses its predicted break time); otherwise it waits the real timing out first, and a block that never visibly broke is re-queued as a fresh mine instead of spamming premature breaks."
                )
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> remineDelayConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("remine-delay")
                .description(
                    "Ticks to wait after the mined block reappears before re-breaking it. 0 = swap and send the re-break the moment it's back (instant ender chest remine)."
                )
                .defaultValue(0)
                .min(0)
                .max(20)
                .sliderRange(0, 20)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET && this.instantConfig.get())
                .build()
        );
    private final Setting<Keybind> instantToggleKey = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("instant-toggle-key")
                .description("Key to toggle the instant mining option")
                .defaultValue(Keybind.none())
                .build()
        );
    private final Setting<Boolean> persistentConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("persistent")
                .description("Keeps packet mine exploit active even when module is disabled (prevents exploit from breaking)")
                .defaultValue(true)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET)
                .onChanged(
                    enabled -> {
                        if (enabled && this.mc.player != null) {
                            this.mc
                                .player
                                .displayClientMessage(
                                    Component.literal(
                                        "§7[§bBepMine§7] §aPersistent mode enabled! Module cannot be disabled until you turn this off or disconnect."
                                    ),
                                    false
                                );
                        }
                    }
                )
                .build()
        );
    private final Setting<BepMine.Swap> swapConfig = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("auto-swap")).description("Swaps to the best tool once the mining is complete"))
                        .defaultValue(BepMine.Swap.SILENT))
                    .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET))
                .build()
        );
    private final Setting<Double> swapProgressConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("swap-at-progress")
                .description(
                    "Break progress at which the silent tool swap happens (auto-tool style: grab the tool right before the block finishes). 0.85 = swap at 85% of the break."
                )
                .defaultValue(0.5)
                .min(0.01)
                .max(0.95)
                .sliderRange(0.5, 0.95)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET && this.swapConfig.get() != BepMine.Swap.OFF)
                .build()
        );
    private final Setting<Double> swapProgressRandomConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("swap-randomness")
                .description(
                    "Random jitter applied to the swap point per block (+/- this much progress) so the swap timing doesn't form a fixed pattern. 0 = off."
                )
                .defaultValue(0.0)
                .min(0.0)
                .max(0.6)
                .sliderRange(0.0, 0.2)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET && this.swapConfig.get() != BepMine.Swap.OFF)
                .build()
        );
    private final Setting<Integer> swapDelayConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("swap-min-hold")
                .description(
                    "Minimum ticks the tool must be held and swung before the break packet - the safety floor for fast blocks where the progress swap lands on the final tick."
                )
                .defaultValue(1)
                .min(1)
                .max(10)
                .sliderRange(1, 10)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET && this.swapConfig.get() != BepMine.Swap.OFF)
                .build()
        );
    private final Setting<Integer> swapReleaseDelayConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("swap-release-delay")
                .description(
                    "Ticks the tool stays held after the break packet before swapping back (the server re-checks the held item while it processes the break)."
                )
                .defaultValue(3)
                .min(1)
                .max(20)
                .sliderRange(1, 20)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET && this.swapConfig.get() != BepMine.Swap.OFF)
                .build()
        );
    private final Setting<Boolean> rotateConfig = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("rotate")
                .description("Silently rotates at the block while mining (Grim RotationBreak)")
                .defaultValue(false)
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET)
                .build()
        );
    private final Setting<Keybind> autoMineKey = this.sgAutoMine
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("auto-mine-key")
                .description("Key to toggle auto-mining enemies")
                .defaultValue(Keybind.none())
                .build()
        );
    private final Setting<Boolean> autoMine = this.sgAutoMine
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-mine")
                .description("Automatically mines blocks around nearby enemies")
                .defaultValue(false)
                .build()
        );
    private final Setting<Double> enemyRange = this.sgAutoMine
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("enemy-range")
                .description("Range to search for enemy players")
                .defaultValue(6.0)
                .min(1.0)
                .sliderRange(1.0, 10.0)
                .visible(this.autoMine::get)
                .build()
        );
    private final Setting<Boolean> targetHead = this.sgAutoMine
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("target-head")
                .description("Also targets blocks at head level (Y+1)")
                .defaultValue(true)
                .visible(this.autoMine::get)
                .build()
        );
    private final Setting<Boolean> autoRotate = this.sgAutoMine
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-rotate")
                .description("Rotates to enemy blocks (uses silent rotations)")
                .defaultValue(false)
                .visible(this.autoMine::get)
                .build()
        );
    private final Setting<Boolean> antiCrawl = this.sgAutoMine
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("anti-crawl")
                .description("Automatically mines block above your head when crawling to stand up")
                .defaultValue(false)
                .visible(this.autoMine::get)
                .build()
        );
    private final Setting<Boolean> render = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("render")
                .description("Whether or not to render the block being mined")
                .defaultValue(true)
                .build()
        );
    private final Setting<ShapeMode> shapeMode = this.sgRender
        .add(((Builder)((Builder)((Builder)new Builder().name("shape-mode")).description("How the shapes are rendered")).defaultValue(ShapeMode.Both)).build());
    private final Setting<SettingColor> colorConfig = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("mine-color")
                .description("The mine render color")
                .defaultValue(new SettingColor(Color.BLUE))
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET)
                .build()
        );
    private final Setting<SettingColor> colorDoneConfig = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("done-color")
                .description("The done render color")
                .defaultValue(new SettingColor(Color.CYAN))
                .visible(() -> this.modeConfig.get() == BepMine.SpeedmineMode.PACKET)
                .build()
        );
    private final Setting<Integer> fadeTimeConfig = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("fade-time")
                .description("Time to fade")
                .defaultValue(250)
                .min(0)
                .sliderRange(0, 1000)
                .visible(() -> false)
                .build()
        );
    private final Map<BlockPos, BepMine.FadeEntry> fadeList = new HashMap<>();
    private final SettingColor renderBoxColor = new SettingColor();
    private final SettingColor renderLineColor = new SettingColor();
    private static final int ALIGN_TIMEOUT_TICKS = 10;
    private static final int MAX_DIG_PACKETS_PER_SECOND = 300;
    private static final int SECONDARY_GRACE_TICKS = 20;
    private static final int REBREAK_EATEN_TICKS = 20;
    private static final int REBREAK_AIM_PARK_TICKS = 60;
    private static final double GRIM_FLAG_MS = 1000.0;
    private static final double BREAK_BUDGET_MS = 800.0;
    private static final double DELAY_BUDGET_MS = 700.0;
    private static final long GRIM_START_GAP_MS = 275L;
    private static final long GRIM_START_FULL_MS = 300L;
    private static final double GRIM_BREAK_SLACK_MS = 25.0;
    private static final int BYPASS_Y_OFFSET = 955;
    private static final int BYPASS_Y_MAX = 2000;
    private static final int MAX_BYPASS_DIGS_PER_SECOND = 40;
    private BepMine.MiningData activeMine;
    private BepMine.MiningData secondaryMine;
    private final ArrayDeque<BepMine.MiningData> pending = new ArrayDeque<>();
    private int destroyDelayTicks;
    private BlockPos lastStartPos;
    private long lastFinishMs;
    private long grimStartMs;
    private boolean grimSampleIsAir;
    private double delayBalanceMirror;
    private double breakBalanceMirror;
    private boolean effMirrorInit;
    private int grimAttrEffLevel;
    private int pendingEffLevel = -1;
    private long effChangeMs;
    private BlockPos instaGracePos;
    private int instaGraceTicks;
    private BlockPos rebreakPos;
    private Direction rebreakDir;
    private int rebreakSeenTicks;
    private volatile boolean rebreakAirSeen;
    private boolean rebreakReplaceSeen;
    private int rebreakStaleTicks;
    private static final int REBREAK_STALE_TICKS = 2;
    private int rebreakSolidTicks;
    private int rebreakAimParkTicks;
    private volatile boolean rebreakAirPending;
    private volatile boolean rebreakReplacePending;
    private int lastDigSequence;
    private int rebreakAckSequence;
    private volatile int lastAckedSequence;
    private volatile int timePacketCount;
    private int rebreakTimeMark;
    private Block rebreakBlock;
    private float rebreakGrimDelta;
    private boolean digSentThisTick;
    private boolean instantTogglePressed = false;
    private boolean autoMineTogglePressed = false;
    private boolean clearQueuePressed = false;
    private Player currentTarget = null;
    private long lastAutoMineTime = 0L;
    private long lastAntiCrawlTime = 0L;
    private static final long AUTO_MINE_DELAY_MS = 250L;
    private static final long ANTI_CRAWL_DELAY_MS = 100L;
    private final ArrayDeque<Long> digPacketTimes = new ArrayDeque<>();
    private final ArrayDeque<Long> bypassPacketTimes = new ArrayDeque<>();

    public BepMine() {
        super(Bep.CATEGORY, "bep-mine", "Mines blocks faster");
    }

    public Setting<Double> getSpeedConfig() {
        return this.speedConfig;
    }

    public Setting<BepMine.SpeedmineMode> getModeConfig() {
        return this.modeConfig;
    }

    public float getEffectiveThreshold() {
        return this.speedConfig.get().floatValue();
    }

    private float rollSwapPoint() {
        double p = this.swapProgressConfig.get();
        double r = this.swapProgressRandomConfig.get();
        if (r > 0.0) {
            p += (Math.random() * 2.0 - 1.0) * r;
        }

        return (float)Mth.clamp(p, 0.1, 0.95);
    }

    private int rollBreakDelay() {
        int jitter = this.breakDelayRandomConfig.get();
        return this.breakDelayConfig.get() + (jitter > 0 ? (int)(Math.random() * (jitter + 1)) : 0);
    }

    @Override
    public void toggle() {
        if (this.isActive() && this.persistentConfig.get() && this.mc.getConnection() != null) {
            if (this.mc.player != null) {
                this.mc
                    .player
                    .displayClientMessage(
                        Component.literal(
                            "§7[§bBepMine§7] §cCannot disable while Persistent mode is active! Disable Persistent first or disconnect from server."
                        ),
                        false
                    );
            }
        } else {
            super.toggle();
        }
    }

    @Override
    public void onActivate() {
        this.resetState();
    }

    @Override
    public void onDeactivate() {
        if (!this.persistentConfig.get() || this.mc.getConnection() == null) {
            if (this.activeMine != null && this.activeMine.started && this.mc.player != null && this.mc.getConnection() != null) {
                this.sendAbort(this.activeMine.pos);
            }

            this.resetState();
            SwapManager.getInstance().releaseNow(this);
            RotationUtils.getInstance().release(this);
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.resetState();
        this.resetGrimMirrors();
    }

    private void resetState() {
        this.activeMine = null;
        this.secondaryMine = null;
        this.pending.clear();
        this.fadeList.clear();
        this.destroyDelayTicks = 0;
        this.instaGracePos = null;
        this.instaGraceTicks = 0;
        this.clearRebreak();
        this.lastAutoMineTime = 0L;
        this.lastAntiCrawlTime = 0L;
        this.currentTarget = null;
    }

    private void resetGrimMirrors() {
        this.lastStartPos = null;
        this.lastFinishMs = 0L;
        this.grimStartMs = 0L;
        this.grimSampleIsAir = false;
        this.lastDigSequence = 0;
        this.rebreakAckSequence = 0;
        this.lastAckedSequence = 0;
        this.delayBalanceMirror = 0.0;
        this.breakBalanceMirror = 0.0;
        this.bypassPacketTimes.clear();
        this.effMirrorInit = false;
        this.grimAttrEffLevel = 0;
        this.pendingEffLevel = -1;
    }

    @EventHandler
    public void onPlayerTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (!this.mc.player.isCreative() && !this.mc.player.isSpectator()) {
                if (this.destroyDelayTicks > 0) {
                    this.destroyDelayTicks--;
                }

                if (this.instaGraceTicks > 0 && --this.instaGraceTicks == 0) {
                    this.instaGracePos = null;
                }

                this.updateGrimEffMirror();
                this.handleKeybinds();
                if (this.modeConfig.get() != BepMine.SpeedmineMode.DAMAGE) {
                    if (!this.mc.player.isUsingItem() || this.multitaskConfig.get()) {
                        this.updateAutoMine();
                        this.tickRebreak();
                        this.tickSecondary();
                        this.tickActiveMine();
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTickPost(Post event) {
        this.digSentThisTick = false;
    }

    private boolean actionConflictTick() {
        return SwapManager.getInstance().isActionConflictTick();
    }

    private void handleKeybinds() {
        if (!this.autoMineKey.get().isPressed() || this.mc.screen != null) {
            this.autoMineTogglePressed = false;
        } else if (!this.autoMineTogglePressed) {
            this.autoMineTogglePressed = true;
            this.autoMine.set(!this.autoMine.get());
            String status = this.autoMine.get() ? "§aenabled" : "§cdisabled";
            this.mc.player.displayClientMessage(Component.literal("§7[§bBepMine§7] §fAuto-mine " + status), false);
        }

        if (!this.instantToggleKey.get().isPressed() || this.mc.screen != null) {
            this.instantTogglePressed = false;
        } else if (!this.instantTogglePressed) {
            this.instantTogglePressed = true;
            this.instantConfig.set(!this.instantConfig.get());
            if (!this.instantConfig.get()) {
                this.clearRebreak();
            }

            String status = this.instantConfig.get() ? "§aenabled" : "§cdisabled";
            this.mc.player.displayClientMessage(Component.literal("§7[§bBepMine§7] §fInstant mining " + status), false);
        }

        if (!this.clearQueueKey.get().isPressed() || this.mc.screen != null) {
            this.clearQueuePressed = false;
        } else if (!this.clearQueuePressed) {
            this.clearQueuePressed = true;
            int cleared = this.pending.size() + (this.activeMine != null ? 1 : 0) + (this.secondaryMine != null ? 1 : 0);
            if (cleared > 0) {
                if (this.activeMine != null && this.activeMine.started) {
                    this.sendAbort(this.activeMine.pos);
                }

                this.activeMine = null;
                this.clearSecondary();
                this.pending.clear();
                this.mc.player.displayClientMessage(Component.literal("§7[§bBepMine§7] §fQueue cleared §7(" + cleared + ")"), false);
            }
        }
    }

    private void updateAutoMine() {
        if (this.autoMine.get() && this.modeConfig.get() == BepMine.SpeedmineMode.PACKET) {
            if (this.activeMine == null && this.pending.isEmpty()) {
                long currentTime = System.currentTimeMillis();
                if (this.antiCrawl.get() && this.mc.player.getPose() == Pose.SWIMMING && currentTime - this.lastAntiCrawlTime >= 100L) {
                    BlockPos crawlBlock = this.getAntiCrawlBlock();
                    if (crawlBlock != null && !this.isMiningBlock(crawlBlock)) {
                        this.pending.add(new BepMine.MiningData(crawlBlock, Direction.DOWN, true, false));
                        this.lastAntiCrawlTime = currentTime;
                        return;
                    }
                }

                this.currentTarget = this.getClosestEnemy();
                if (this.currentTarget != null && currentTime - this.lastAutoMineTime >= 250L) {
                    BlockPos targetBlock = this.findBestEnemyBlock(this.currentTarget);
                    if (targetBlock != null && !this.isMiningBlock(targetBlock)) {
                        Direction direction = this.getInteractDirection(targetBlock);
                        this.pending.add(new BepMine.MiningData(targetBlock, direction, true, false));
                        this.lastAutoMineTime = currentTime;
                    }
                }
            }
        }
    }

    private void tickRebreak() {
        if (this.instantConfig.get() && this.rebreakPos != null) {
            if (this.lastStartPos == null || !this.lastStartPos.equals(this.rebreakPos)) {
                this.clearRebreak();
            } else if (this.activeMine == null || !this.activeMine.pos.equals(this.rebreakPos)) {
                if (this.rebreakAirPending) {
                    this.rebreakAirPending = false;
                    this.noteRebreakAir();
                }

                if (this.rebreakReplacePending) {
                    this.rebreakReplacePending = false;
                    this.rebreakReplaceSeen = true;
                }

                BlockState state = this.mc.level.getBlockState(this.rebreakPos);
                if (state.isAir()) {
                    this.noteRebreakAir();
                    this.rebreakReplaceSeen = true;
                    this.rebreakStaleTicks = 0;
                    this.rebreakSeenTicks = 0;
                    if (this.rotateConfig.get() && this.canSpoofAim() && ++this.rebreakAimParkTicks <= 60) {
                        this.assertMiningAim(this.rebreakPos);
                    }
                } else if (state.getDestroySpeed(this.mc.level, this.rebreakPos) != -1.0F && this.inRange(this.rebreakPos)) {
                    if (state.getBlock() != this.rebreakBlock && state.getFluidState().isEmpty()) {
                        if (!this.rebreakAirSeen) {
                            this.noteRebreakAir();
                        }

                        this.rebreakReplaceSeen = true;
                        this.rebreakBlock = state.getBlock();
                    }

                    if (!this.rebreakAirSeen) {
                        for (ItemEntity drop : this.mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(this.rebreakPos), e -> true)) {
                            if (drop.tickCount <= 3) {
                                this.noteRebreakAir();
                                this.rebreakReplaceSeen = true;
                                break;
                            }
                        }
                    }

                    if (!this.rebreakAirSeen) {
                        if (this.lastAckedSequence - this.rebreakAckSequence >= 0 || this.timePacketCount - this.rebreakTimeMark >= 2) {
                            if (++this.rebreakSolidTicks > 20 && (this.secondaryMine == null || !this.secondaryMine.pos.equals(this.rebreakPos))) {
                                BlockPos pos = this.rebreakPos;
                                Direction dir = this.rebreakDir;
                                this.clearRebreak();
                                if (this.isValidTarget(pos) && !this.isMiningBlock(pos)) {
                                    this.pending.addFirst(new BepMine.MiningData(pos, dir, false, false));
                                }
                            }
                        }
                    } else {
                        if (!this.rebreakReplaceSeen) {
                            if (++this.rebreakStaleTicks < 2) {
                                return;
                            }

                            this.rebreakReplaceSeen = true;
                        }

                        this.rebreakSeenTicks++;
                        int delay = this.remineDelayConfig.get();
                        if (this.rebreakSeenTicks <= delay) {
                            if (this.rebreakSeenTicks == delay && !this.actionConflictTick() && this.ensureToolHeld(this.bestToolSlot(state))) {
                                this.sendSwing();
                            }
                        } else {
                            double predictedMs = this.grimPredictedFromDelta(this.rebreakGrimDelta);
                            if (this.finishBudgetOk(predictedMs)) {
                                if (!this.actionConflictTick() && !this.digSentThisTick && this.digBudgetAvailable()) {
                                    if (this.rotateConfig.get() && this.canSpoofAim()) {
                                        this.assertMiningAim(this.rebreakPos);
                                        if (!this.sentAimHitsBlock(this.rebreakPos) && this.rebreakSeenTicks <= delay + 10) {
                                            return;
                                        }
                                    }

                                    if (this.holdOrPulseDigSlot(state)) {
                                        this.sendSwing();
                                        this.sendDig(Action.STOP_DESTROY_BLOCK, this.rebreakPos, this.getInteractDirection(this.rebreakPos));
                                        this.noteFinishSent(predictedMs);
                                        this.destroyDelayTicks = this.rollBreakDelay();
                                        this.rebreakAirSeen = false;
                                        this.rebreakAirPending = false;
                                        this.rebreakReplaceSeen = false;
                                        this.rebreakReplacePending = false;
                                        this.rebreakStaleTicks = 0;
                                        this.rebreakSolidTicks = 0;
                                        this.rebreakSeenTicks = 0;
                                        this.rebreakAckSequence = this.lastDigSequence;
                                        this.rebreakTimeMark = this.timePacketCount;
                                        this.rebreakAimParkTicks = 0;
                                        this.rebreakBlock = state.getBlock();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void noteRebreakAir() {
        this.rebreakAirSeen = true;
        this.grimSampleIsAir = true;
        this.rebreakSolidTicks = 0;
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (event.packet instanceof ClientboundBlockChangedAckPacket ack) {
            if (ack.sequence() - this.lastAckedSequence > 0) {
                this.lastAckedSequence = ack.sequence();
            }
        } else if (event.packet instanceof ClientboundSetTimePacket) {
            this.timePacketCount++;
        } else {
            BlockPos pos = this.rebreakPos;
            if (pos != null && this.modeConfig.get() == BepMine.SpeedmineMode.PACKET) {
                if (this.activeMine == null || !this.activeMine.pos.equals(pos)) {
                    if (event.packet instanceof ClientboundBlockUpdatePacket packet) {
                        if (packet.getPos().equals(pos)) {
                            this.noteRebreakUpdate(packet.getBlockState());
                        }
                    } else if (event.packet instanceof ClientboundSectionBlocksUpdatePacket packet) {
                        packet.runUpdates((updatePos, state) -> {
                            if (updatePos.equals(pos)) {
                                this.noteRebreakUpdate(state);
                            }
                        });
                    }
                }
            }
        }
    }

    private void noteRebreakUpdate(BlockState state) {
        if (state.isAir()) {
            this.rebreakAirPending = true;
        } else if (this.rebreakAirPending || this.rebreakAirSeen) {
            this.rebreakReplacePending = true;
        }
    }

    private void clearRebreak() {
        this.rebreakPos = null;
        this.rebreakDir = null;
        this.rebreakBlock = null;
        this.rebreakSeenTicks = 0;
        this.rebreakAirSeen = false;
        this.rebreakAirPending = false;
        this.rebreakReplaceSeen = false;
        this.rebreakReplacePending = false;
        this.rebreakStaleTicks = 0;
        this.rebreakSolidTicks = 0;
        this.rebreakAimParkTicks = 0;
    }

    private void clearSecondary() {
        this.secondaryMine = null;
    }

    private void tickSecondary() {
        if (this.secondaryMine != null) {
            BepMine.MiningData data = this.secondaryMine;
            BlockState state = this.mc.level.getBlockState(data.pos);
            if (!state.isAir() && (data.minedBlock == null || state.getBlock() == data.minedBlock)) {
                data.ticksMined++;
                float serverDelta = this.swapConfig.get() == BepMine.Swap.OFF
                    ? this.serverHeldBreakingDelta(state, data.pos)
                    : this.calcBlockBreakingDelta(state, data.pos);
                float serverProgress = (data.ticksMined + 1) * serverDelta;
                data.lastDamage = data.damage;
                data.damage = Math.min(this.getEffectiveThreshold(), serverProgress * this.getEffectiveThreshold());
                if (!(serverProgress < 1.0F)) {
                    if (!this.actionConflictTick()) {
                        this.holdOrPulseDigSlot(state);
                    }

                    if (++data.graceTicks > 20) {
                        if (this.isValidTarget(data.pos)) {
                            this.pending.addFirst(new BepMine.MiningData(data.pos, data.direction, data.autoTarget, false));
                        }

                        this.clearSecondary();
                    }
                } else {
                    int toolSlot = this.bestToolSlot(state);
                    if (this.swapConfig.get() != BepMine.Swap.OFF && toolSlot != -1 && !this.actionConflictTick() && !this.toolStarved()) {
                        SwapManager swap = SwapManager.getInstance();
                        boolean primaryWantsOther = this.activeMine != null
                            && this.activeMine.started
                            && this.bestToolSlot(this.mc.level.getBlockState(this.activeMine.pos)) != toolSlot;
                        if (!primaryWantsOther) {
                            swap.hold(this, toolSlot, 10, this.swapConfig.get() == BepMine.Swap.SILENT, 2);
                        }
                    }
                }
            } else {
                this.clearSecondary();
            }
        }
    }

    private void tickActiveMine() {
        this.tryPromote();
        if (this.activeMine == null && !this.pending.isEmpty()) {
            BepMine.MiningData next;
            while ((next = this.pending.poll()) != null && !this.isValidTarget(next.pos)) {
            }

            this.activeMine = next;
        }

        if (this.activeMine != null) {
            BepMine.MiningData data = this.activeMine;
            BlockState state = this.mc.level.getBlockState(data.pos);
            if (state.isAir()) {
                if (data.started) {
                    this.sendAbort(data.pos);
                }

                this.activeMine = null;
            } else if (this.inRange(data.pos) && state.getDestroySpeed(this.mc.level, data.pos) != -1.0F) {
                if (data.started && state.getBlock() != data.minedBlock) {
                    this.sendAbort(data.pos);
                    data.started = false;
                    data.damage = 0.0F;
                    data.lastDamage = 0.0F;
                    data.heldTicks = 0;
                    data.ticksMined = 0;
                    data.startMs = 0L;
                    data.grimMaxDelta = 0.0F;
                    data.swapPoint = this.rollSwapPoint();
                }

                boolean rotate = data.autoTarget ? this.autoRotate.get() : this.rotateConfig.get();
                if (rotate && this.canSpoofAim()) {
                    this.assertMiningAim(data.pos);
                }

                if (!data.started) {
                    this.tryStartActive(state, rotate);
                } else {
                    float delta = this.calcBlockBreakingDelta(state, data.pos);
                    data.lastDamage = data.damage;
                    data.damage += delta;
                    data.ticksMined++;
                    data.grimMaxDelta = Math.max(data.grimMaxDelta, this.grimSampledBreakingDelta(state, data.pos));
                    float threshold = this.getEffectiveThreshold();
                    int toolSlot = this.bestToolSlot(state);
                    boolean wantsTool = this.swapConfig.get() != BepMine.Swap.OFF && toolSlot != -1;
                    if (wantsTool
                        && (data.damage >= data.swapPoint * threshold || this.promotionWaiting())
                        && !this.actionConflictTick()
                        && this.ensureToolHeld(toolSlot)) {
                        data.heldTicks++;
                    }

                    float finishAt = this.secondaryMine != null ? Math.max(threshold, 0.72F) : threshold;
                    double predictedMs = this.grimPredictedMs(data);
                    boolean needsBypass = this.shouldBypass(predictedMs);
                    if (this.grimBypassConfig.get() == BepMine.GrimBypass.ALWAYS
                        && data.damage + delta < finishAt
                        && this.sinceFinish() >= 275L
                        && this.delayBalanceMirror > 400.0
                        && this.bypassBudgetAvailable()
                        && this.canSendDigNow()) {
                        this.sendBypassStart(data.pos);
                        this.sendSwing();
                    } else if (needsBypass && data.damage + delta >= finishAt && this.canSendDigNow()) {
                        this.sendBypassStart(data.pos);
                        this.sendSwing();
                    } else if (data.damage < finishAt) {
                        this.sendSwing();
                    } else if (wantsTool && !this.toolStarved() && data.heldTicks < this.swapDelayConfig.get()) {
                        this.sendSwing();
                    } else if (rotate && this.canSpoofAim() && !this.sentAimHitsBlock(data.pos) && data.alignTicks++ < 10) {
                        this.sendSwing();
                    } else if (!this.canSendDigNow()) {
                        this.sendSwing();
                    } else if (needsBypass) {
                        this.sendBypassStart(data.pos);
                        this.sendSwing();
                    } else if (!this.finishBudgetOk(predictedMs)) {
                        this.sendSwing();
                    } else if (!this.holdOrPulseDigSlot(state)) {
                        this.sendSwing();
                    } else {
                        this.sendSwing();
                        data.direction = this.getInteractDirection(data.pos);
                        this.sendDig(Action.STOP_DESTROY_BLOCK, data.pos, data.direction);
                        this.noteFinishSent(predictedMs);
                        if (data.damage + delta < 0.7F && this.secondaryMine == null) {
                            data.graceTicks = 0;
                            this.secondaryMine = data;
                        }

                        this.destroyDelayTicks = this.rollBreakDelay();
                        this.activeMine = null;
                        if (this.instantConfig.get()) {
                            this.rebreakSeenTicks = 0;
                            this.rebreakAirSeen = false;
                            this.rebreakAirPending = false;
                            this.rebreakReplaceSeen = false;
                            this.rebreakReplacePending = false;
                            this.rebreakStaleTicks = 0;
                            this.rebreakSolidTicks = 0;
                            this.rebreakAimParkTicks = 0;
                            this.rebreakGrimDelta = data.grimMaxDelta;
                            this.rebreakBlock = data.minedBlock;
                            this.rebreakAckSequence = this.lastDigSequence;
                            this.rebreakTimeMark = this.timePacketCount;
                            this.rebreakDir = data.direction;
                            this.rebreakPos = data.pos;
                        }

                        SwapManager.getInstance().scheduleRelease(this, this.swapReleaseDelayConfig.get());
                    }
                }
            } else {
                if (data.started) {
                    this.sendAbort(data.pos);
                }

                this.activeMine = null;
            }
        }
    }

    private boolean promotionWaiting() {
        return this.doubleBreakConfig.get() && this.secondaryMine == null && !this.pending.isEmpty();
    }

    private void tryPromote() {
        if (this.doubleBreakConfig.get() && !this.pending.isEmpty()) {
            if (this.activeMine != null && this.activeMine.started) {
                if (this.secondaryMine == null) {
                    BepMine.MiningData primary = this.activeMine;
                    BlockState state = this.mc.level.getBlockState(primary.pos);
                    if (!state.isAir()) {
                        float delta = this.calcBlockBreakingDelta(state, primary.pos);
                        if (!(delta <= 0.0F)) {
                            if (!(delta >= 1.0F) && !(primary.damage >= this.getEffectiveThreshold())) {
                                BepMine.MiningData next;
                                while ((next = this.pending.peek()) != null && !this.isValidTarget(next.pos)) {
                                    this.pending.poll();
                                }

                                if (next != null) {
                                    if (this.canSendDigNow()) {
                                        int toolSlot = this.bestToolSlot(state);
                                        boolean wantsTool = this.swapConfig.get() != BepMine.Swap.OFF && toolSlot != -1;
                                        if (!wantsTool
                                            || this.toolStarved()
                                            || primary.heldTicks >= this.swapDelayConfig.get() && this.ensureToolHeld(toolSlot)) {
                                            boolean rotate = primary.autoTarget ? this.autoRotate.get() : this.rotateConfig.get();
                                            if (!rotate || !this.canSpoofAim() || this.sentAimHitsBlock(primary.pos)) {
                                                double predictedMs = this.grimPredictedMs(primary);
                                                if (this.shouldBypass(predictedMs)) {
                                                    this.sendBypassStart(primary.pos);
                                                    this.sendSwing();
                                                } else if (this.finishBudgetOk(predictedMs)) {
                                                    this.sendSwing();
                                                    this.sendDig(Action.STOP_DESTROY_BLOCK, primary.pos, this.getInteractDirection(primary.pos));
                                                    this.noteFinishSent(predictedMs);
                                                    this.destroyDelayTicks = this.rollBreakDelay();
                                                    int holdTicks = Math.min(
                                                        100, (int)Math.ceil((1.0F - Math.min(1.0F, primary.damage)) / Math.min(1.0F, delta)) + 2
                                                    );
                                                    SwapManager.getInstance().scheduleRelease(this, holdTicks);
                                                    primary.graceTicks = 0;
                                                    this.secondaryMine = primary;
                                                    this.pending.poll();
                                                    this.activeMine = next;
                                                    this.activeMine.fastStart = true;
                                                    boolean rotateNext = next.autoTarget ? this.autoRotate.get() : this.rotateConfig.get();
                                                    if (rotateNext && this.canSpoofAim()) {
                                                        this.assertMiningAim(next.pos);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private long sinceFinish() {
        return this.lastFinishMs == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - this.lastFinishMs;
    }

    private void noteStartSent(boolean airSample) {
        long since = this.sinceFinish();
        if (since >= 275L) {
            this.delayBalanceMirror *= 0.9;
        } else {
            this.delayBalanceMirror = Math.min(1000.0, this.delayBalanceMirror + (300L - since));
        }

        this.grimStartMs = System.currentTimeMillis();
        this.grimSampleIsAir = airSample;
    }

    private void noteFinishSent(double predictedMs) {
        long now = System.currentTimeMillis();
        double predicted = this.grimSampleIsAir ? 0.0 : predictedMs;
        double real = this.grimStartMs == 0L ? 0.0 : now - this.grimStartMs;
        this.breakBalanceMirror = Mth.clamp(this.breakBalanceMirror, -1000.0, 1000.0);
        double diff = predicted - real;
        if (diff < 25.0) {
            this.breakBalanceMirror *= 0.9;
        } else {
            this.breakBalanceMirror += diff;
        }

        this.lastFinishMs = now;
        this.grimStartMs = now;
    }

    private boolean startBudgetOk() {
        long since = this.sinceFinish();
        return since >= 275L ? true : this.delayBalanceMirror + (300L - since) <= 700.0;
    }

    private boolean finishBudgetOk(double predictedMs) {
        if (this.grimSampleIsAir) {
            return true;
        }

        double diff = predictedMs - (this.grimStartMs == 0L ? 0L : System.currentTimeMillis() - this.grimStartMs);
        return diff < 25.0 ? true : this.breakBalanceMirror + diff <= 800.0;
    }

    private double grimPredictedMs(BepMine.MiningData data) {
        return this.grimPredictedFromDelta(data.grimMaxDelta);
    }

    private double grimPredictedFromDelta(float delta) {
        return delta <= 0.0F ? 1000.0 : Math.ceil(1.0 / Math.min(1.0F, delta)) * 50.0;
    }

    private void sendBypassStart(BlockPos pos) {
        int y = Math.min(2000, pos.getY() + 955);
        this.bypassPacketTimes.addLast(System.currentTimeMillis());
        this.sendDig(Action.START_DESTROY_BLOCK, new BlockPos(pos.getX(), y, pos.getZ()), Direction.DOWN);
        this.noteStartSent(true);
    }

    private boolean bypassBudgetAvailable() {
        long now = System.currentTimeMillis();

        while (!this.bypassPacketTimes.isEmpty() && now - this.bypassPacketTimes.peekFirst() > 1000L) {
            this.bypassPacketTimes.pollFirst();
        }

        return this.bypassPacketTimes.size() < 40;
    }

    private boolean shouldBypass(double predictedMs) {
        if (this.grimBypassConfig.get() != BepMine.GrimBypass.OFF && !this.grimSampleIsAir) {
            if (this.sinceFinish() < 275L) {
                return false;
            } else {
                return !this.bypassBudgetAvailable() ? false : this.grimBypassConfig.get() == BepMine.GrimBypass.ALWAYS || !this.finishBudgetOk(predictedMs);
            }
        } else {
            return false;
        }
    }

    private boolean canSendDigNow() {
        return !this.actionConflictTick() && !this.digSentThisTick && this.digBudgetAvailable();
    }

    private void tryStartActive(BlockState state, boolean rotate) {
        BepMine.MiningData data = this.activeMine;
        if (this.canSendDigNow()) {
            float delta = this.calcBlockBreakingDelta(state, data.pos);
            boolean insta = delta >= 1.0F;
            if (this.startBudgetOk()) {
                if (this.destroyDelayTicks <= 0 || insta || data.fastStart) {
                    if (!rotate || !this.canSpoofAim() || this.sentAimHitsBlock(data.pos) || data.alignTicks++ >= 10) {
                        if (!data.manualFace) {
                            data.direction = this.getInteractDirection(data.pos);
                        }

                        if (!(delta >= 1.0F) || this.swapConfig.get() == BepMine.Swap.OFF || this.holdOrPulseDigSlot(state)) {
                            data.minedBlock = state.getBlock();
                            data.started = true;
                            data.damage = 0.0F;
                            data.lastDamage = 0.0F;
                            data.ticksMined = 0;
                            data.startMs = System.currentTimeMillis();
                            data.grimMaxDelta = this.grimSampledBreakingDelta(state, data.pos);
                            data.alignTicks = 0;
                            this.noteStart(data.pos);
                            if (delta >= 1.0F) {
                                this.sendInstaBreak(data.pos, data.direction);
                            } else {
                                this.sendDig(Action.START_DESTROY_BLOCK, data.pos, data.direction);
                            }

                            this.sendSwing();
                            if (delta >= 1.0F) {
                                this.instaGracePos = data.pos;
                                this.instaGraceTicks = 8;
                                this.activeMine = null;
                                if (rotate && this.canSpoofAim()) {
                                    for (BepMine.MiningData next : this.pending) {
                                        if (this.isValidTarget(next.pos)) {
                                            this.assertMiningAim(next.pos);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onAttackBlock(StartBreakingBlockEvent event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (!this.mc.player.isCreative() && !this.mc.player.isSpectator() && this.modeConfig.get() == BepMine.SpeedmineMode.PACKET) {
                event.cancel();
                BlockPos pos = event.blockPos;
                if (!this.isMiningBlock(pos)) {
                    BlockState blockState = this.mc.level.getBlockState(pos);
                    if (blockState.getDestroySpeed(this.mc.level, pos) != -1.0F && !blockState.isAir() && this.inRange(pos)) {
                        if (this.activeMine != null) {
                            if (this.clickQueueConfig.get() || this.doubleBreakConfig.get()) {
                                int limit = this.clickQueueConfig.get() ? this.queueLimitConfig.get() : 1;
                                if (this.pending.size() < limit) {
                                    this.pending.add(new BepMine.MiningData(pos.immutable(), event.direction, false, true));
                                    if (this.doubleBreakConfig.get()) {
                                        this.tryPromote();
                                    }
                                }

                                return;
                            }

                            if (this.activeMine.started) {
                                this.sendAbort(this.activeMine.pos);
                            }
                        }

                        this.activeMine = new BepMine.MiningData(pos, event.direction, false, true);
                        if (this.rotateConfig.get() && this.canSpoofAim()) {
                            this.assertMiningAim(pos);
                        }

                        this.tryStartActive(blockState, this.rotateConfig.get());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onRenderWorld(Render3DEvent event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (!this.mc.player.isCreative() && this.modeConfig.get() == BepMine.SpeedmineMode.PACKET && this.render.get()) {
                this.trackFade(this.activeMine);
                this.trackFade(this.secondaryMine);

                for (BepMine.MiningData data : this.pending) {
                    this.trackFade(data);
                }

                float total = this.getEffectiveThreshold();
                double fadeSeconds = this.fadeTimeConfig.get().intValue() / 1000.0;
                SettingColor mineColor = this.colorConfig.get();
                SettingColor doneColor = this.colorDoneConfig.get();
                Iterator<BepMine.FadeEntry> it = this.fadeList.values().iterator();

                while (it.hasNext()) {
                    BepMine.FadeEntry entry = it.next();
                    BepMine.MiningData data = entry.data;
                    BlockState state = data.getState();
                    boolean air = state.isAir();
                    boolean primary = data == this.activeMine || data == this.secondaryMine;
                    boolean queued = !primary && this.pending.contains(data);
                    boolean visible = (primary || queued) && !air;
                    entry.fade.update(visible, fadeSeconds, true);
                    if (!visible && !entry.fade.rendering()) {
                        it.remove();
                    } else {
                        float factor = entry.fade.alpha();
                        int boxAlpha = (int)((queued ? 20 : 40) * factor);
                        int lineAlpha = (int)((queued ? 60 : 100) * factor);
                        boolean done = !queued && (data.damage >= total || air);
                        SettingColor base = done ? doneColor : mineColor;
                        this.renderBoxColor.set(base.r, base.g, base.b, boxAlpha);
                        this.renderLineColor.set(base.r, base.g, base.b, lineAlpha);
                        BlockPos mining = data.pos;
                        VoxelShape outlineShape = state.getShape(this.mc.level, mining);
                        AABB bounds = (outlineShape.isEmpty() ? Shapes.block() : outlineShape).bounds();
                        float scale = !queued && !air
                            ? Mth.clamp((data.damage + (data.damage - data.lastDamage) * event.tickDelta) / total, 0.0F, 1.0F)
                            : 1.0F;
                        double cx = mining.getX() + (bounds.minX + bounds.maxX) / 2.0;
                        double cy = mining.getY() + (bounds.minY + bounds.maxY) / 2.0;
                        double cz = mining.getZ() + (bounds.minZ + bounds.maxZ) / 2.0;
                        double hx = (bounds.maxX - bounds.minX) / 2.0 * scale;
                        double hy = (bounds.maxY - bounds.minY) / 2.0 * scale;
                        double hz = (bounds.maxZ - bounds.minZ) / 2.0 * scale;
                        event.renderer
                            .box(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, this.renderBoxColor, this.renderLineColor, this.shapeMode.get(), 0);
                    }
                }
            }
        }
    }

    private void trackFade(BepMine.MiningData data) {
        if (data != null && !data.getState().isAir()) {
            BepMine.FadeEntry existing = this.fadeList.get(data.pos);
            if (existing == null) {
                this.fadeList.put(data.pos, new BepMine.FadeEntry(data));
            } else {
                existing.data = data;
            }
        }
    }

    public boolean canDelegateMining() {
        return this.isActive() && this.modeConfig.get() == BepMine.SpeedmineMode.PACKET;
    }

    public void mineBlock(BlockPos pos, Direction direction) {
        if (this.canDelegateMining() && this.mc.player != null && this.mc.level != null) {
            if (!this.isMiningBlock(pos)) {
                if (this.isValidTarget(pos)) {
                    if (this.pending.size() < this.queueLimitConfig.get()) {
                        this.pending.add(new BepMine.MiningData(pos.immutable(), direction, false, false));
                    }
                }
            }
        }
    }

    public boolean isMiningBlock(BlockPos pos) {
        if (this.activeMine != null && this.activeMine.pos.equals(pos)) {
            return true;
        }

        if (this.secondaryMine != null && this.secondaryMine.pos.equals(pos)) {
            return true;
        }

        if (this.rebreakPos != null && this.rebreakPos.equals(pos)) {
            return true;
        }

        if (this.instaGraceTicks > 0 && pos.equals(this.instaGracePos)) {
            return true;
        }

        for (BepMine.MiningData data : this.pending) {
            if (data.pos.equals(pos)) {
                return true;
            }
        }

        return false;
    }

    private boolean isValidTarget(BlockPos pos) {
        BlockState state = this.mc.level.getBlockState(pos);
        return !state.isAir() && state.getDestroySpeed(this.mc.level, pos) != -1.0F && this.inRange(pos);
    }

    private boolean inRange(BlockPos pos) {
        double r = this.rangeConfig.get();
        return GrimUtils.closestEyeDistanceSqTo(this.mc.player, new AABB(pos)) <= r * r;
    }

    private void noteStart(BlockPos pos) {
        this.noteStartSent(false);
        this.lastStartPos = pos;
        if (this.rebreakPos != null && !this.rebreakPos.equals(pos)) {
            this.clearRebreak();
        }
    }

    private void sendAbort(BlockPos pos) {
        if (this.rebreakPos != null && !this.rebreakPos.equals(pos)) {
            this.clearRebreak();
        }

        this.sendDig(Action.ABORT_DESTROY_BLOCK, pos, Direction.DOWN);
    }

    public boolean needsMiningTickPacket() {
        if (!this.isActive() || this.modeConfig.get() != BepMine.SpeedmineMode.PACKET) {
            return false;
        } else if (this.instantConfig.get() && this.rebreakPos != null) {
            return true;
        } else {
            return this.swingConfig.get() != BepMine.SwingMode.NONE ? false : this.activeMine != null && this.activeMine.started;
        }
    }

    private void sendSwing() {
        switch ((BepMine.SwingMode)this.swingConfig.get()) {
            case FULL:
                this.mc.player.swing(InteractionHand.MAIN_HAND);
                break;
            case PACKET:
                this.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            case NONE:
        }
    }

    private void sendDig(Action action, BlockPos pos, Direction direction) {
        this.digPacketTimes.addLast(System.currentTimeMillis());
        if (action == Action.ABORT_DESTROY_BLOCK) {
            this.mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, direction));
        } else {
            this.digSentThisTick = true;
            this.lastDigSequence = GrimUtils.nextSequence();
            this.mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, direction, this.lastDigSequence));
        }
    }

    private void sendInstaBreak(BlockPos pos, Direction direction) {
        this.digPacketTimes.addLast(System.currentTimeMillis());
        this.digSentThisTick = true;
        BlockStatePredictionHandler handler = this.mc.level.getBlockStatePredictionHandler();

        try {
            int seq = handler.startPredicting().currentSequence();
            this.mc.getConnection().send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, pos, direction, seq));
            this.mc.gameMode.destroyBlock(pos);
        } finally {
            handler.close();
        }
    }

    private boolean digBudgetAvailable() {
        long now = System.currentTimeMillis();

        while (!this.digPacketTimes.isEmpty() && now - this.digPacketTimes.peekFirst() > 1000L) {
            this.digPacketTimes.pollFirst();
        }

        return this.digPacketTimes.size() < 300;
    }

    private boolean ensureToolHeld(int slot) {
        return this.swapConfig.get() != BepMine.Swap.OFF && slot != -1
            ? SwapManager.getInstance().hold(this, slot, 10, this.swapConfig.get() == BepMine.Swap.SILENT, this.swapReleaseDelayConfig.get())
            : true;
    }

    private boolean toolStarved() {
        return SwapManager.getInstance().isForeignSession(this, 10);
    }

    private boolean holdOrPulseDigSlot(BlockState state) {
        if (this.swapConfig.get() == BepMine.Swap.OFF) {
            return true;
        }

        int slot = this.bestToolSlot(state);
        SwapManager swap = SwapManager.getInstance();
        if (this.toolStarved()) {
            int target = slot != -1 ? slot : swap.referenceSlot();
            return swap.pulse(this, target, 10);
        }

        if (slot == -1) {
            slot = swap.referenceSlot();
        }

        return swap.hold(this, slot, 10, this.swapConfig.get() == BepMine.Swap.SILENT, this.swapReleaseDelayConfig.get());
    }

    public float calcBlockBreakingDelta(BlockState state, BlockPos pos) {
        if (state.isAir()) {
            return 0.0F;
        }

        if (this.swapConfig.get() == BepMine.Swap.OFF) {
            return state.getDestroyProgress(this.mc.player, this.mc.level, pos);
        }

        float f = state.getDestroySpeed(this.mc.level, pos);
        if (f == -1.0F) {
            return 0.0F;
        }

        int i = this.canHarvest(state) ? 30 : 100;
        return this.getBlockBreakingSpeed(state) / f / i;
    }

    private float serverHeldBreakingDelta(BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(this.mc.level, pos);
        if (hardness == -1.0F) {
            return 0.0F;
        }

        ItemStack stack = this.mc.player.getInventory().getItem(SwapManager.getInstance().getServerSlot());
        int i = this.canStackHarvest(stack, state) ? 30 : 100;
        return this.stackBreakingSpeed(stack, state, this.effLevel(stack)) / hardness / i;
    }

    private float grimSampledBreakingDelta(BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(this.mc.level, pos);
        if (hardness == -1.0F) {
            return 0.0F;
        }

        ItemStack stack = this.mc.player.getInventory().getItem(SwapManager.getInstance().getServerSlot());
        int i = this.canStackHarvest(stack, state) ? 30 : 100;
        return this.stackBreakingSpeed(stack, state, this.grimAttrEffLevel) / hardness / i;
    }

    private void updateGrimEffMirror() {
        int level = this.effLevel(this.mc.player.getInventory().getItem(SwapManager.getInstance().getServerSlot()));
        long now = System.currentTimeMillis();
        if (!this.effMirrorInit) {
            this.effMirrorInit = true;
            this.grimAttrEffLevel = level;
            this.pendingEffLevel = -1;
        } else {
            if (this.pendingEffLevel == -1 ? level != this.grimAttrEffLevel : level != this.pendingEffLevel) {
                this.pendingEffLevel = level;
                this.effChangeMs = now;
            }

            if (this.pendingEffLevel != -1 && now - this.effChangeMs >= this.attrSyncMs()) {
                this.grimAttrEffLevel = this.pendingEffLevel;
                this.pendingEffLevel = -1;
            }
        }
    }

    private long attrSyncMs() {
        int latency = 200;
        if (this.mc.getConnection() != null) {
            PlayerInfo info = this.mc.getConnection().getPlayerInfo(this.mc.player.getUUID());
            if (info != null && info.getLatency() > 0) {
                latency = info.getLatency();
            }
        }

        return latency + 150L;
    }

    private int effLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        for (Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.EFFICIENCY)) {
                return entry.getIntValue();
            }
        }

        return 0;
    }

    private float getBlockBreakingSpeed(BlockState block) {
        int tool = this.getBestTool(block);
        return this.stackBreakingSpeed(this.mc.player.getInventory().getItem(tool), block);
    }

    private float stackBreakingSpeed(ItemStack stack, BlockState block) {
        return this.stackBreakingSpeed(stack, block, this.effLevel(stack));
    }

    private float stackBreakingSpeed(ItemStack stack, BlockState block, int efficiency) {
        float f = stack.getDestroySpeed(block);
        if (f > 1.0F && efficiency > 0 && !stack.isEmpty()) {
            f += efficiency * efficiency + 1;
        }

        if (MobEffectUtil.hasDigSpeed(this.mc.player)) {
            f *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(this.mc.player) + 1) * 0.2F;
        }

        if (this.mc.player.hasEffect(MobEffects.MINING_FATIGUE)) {
            float g = switch (this.mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
            f *= g;
        }

        f *= (float)this.mc.player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (this.mc.player.isEyeInFluid(FluidTags.WATER)) {
            f *= (float)this.mc.player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        }

        if (!this.mc.player.onGround()) {
            f /= 5.0F;
        }

        return f;
    }

    private boolean canHarvest(BlockState state) {
        if (state.requiresCorrectToolForDrops()) {
            int tool = this.getBestTool(state);
            return this.mc.player.getInventory().getItem(tool).isCorrectToolForDrops(state);
        } else {
            return true;
        }
    }

    private int referenceSlot() {
        return SwapManager.getInstance().referenceSlot();
    }

    private int getBestTool(BlockState state) {
        int bestSlot = this.bestToolSlot(state);
        return bestSlot == -1 ? this.referenceSlot() : bestSlot;
    }

    private int bestToolSlot(BlockState state) {
        ItemStack held = this.mc.player.getInventory().getItem(this.referenceSlot());
        float bestSpeed = held.getDestroySpeed(state);
        boolean bestHarvest = this.canStackHarvest(held, state);
        int bestSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            float speed = stack.getDestroySpeed(state);
            boolean harvest = this.canStackHarvest(stack, state);
            if (harvest && !bestHarvest || harvest == bestHarvest && speed > bestSpeed) {
                bestHarvest = harvest;
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private boolean canStackHarvest(ItemStack stack, BlockState state) {
        return !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
    }

    private static float[] getRotationsTo(Vec3 src, Vec3 dest) {
        float yaw = (float)(Math.toDegrees(Math.atan2(dest.subtract(src).z, dest.subtract(src).x)) - 90.0);
        float pitch = (float)Math.toDegrees(
            -Math.atan2(dest.subtract(src).y, Math.hypot(dest.subtract(src).x, dest.subtract(src).z))
        );
        return new float[]{Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch)};
    }

    private Player getClosestEnemy() {
        if (this.mc.level != null && this.mc.player != null) {
            Player closest = null;
            double closestDist = this.enemyRange.get() * this.enemyRange.get();

            for (Player player : this.mc.level.players()) {
                if (player != this.mc.player && !player.isSpectator() && !player.isDeadOrDying() && !Friends.get().isFriend(player)) {
                    double dist = this.mc.player.distanceToSqr(player);
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = player;
                    }
                }
            }

            return closest;
        } else {
            return null;
        }
    }

    private BlockPos findBestEnemyBlock(Player enemy) {
        if (enemy == null) {
            return null;
        }

        BlockPos enemyPos = enemy.blockPosition();
        BlockState feetState = this.mc.level.getBlockState(enemyPos);
        if (!feetState.isAir()
            && feetState.getDestroySpeed(this.mc.level, enemyPos) != -1.0F
            && this.inRange(enemyPos)
            && !this.isMiningBlock(enemyPos)
            && this.isResistantBlock(feetState)
            && !this.isOwnSurroundBlock(enemyPos)) {
            return enemyPos;
        }

        List<BlockPos> surroundBlocks = new ArrayList<>();
        surroundBlocks.add(enemyPos.north());
        surroundBlocks.add(enemyPos.south());
        surroundBlocks.add(enemyPos.east());
        surroundBlocks.add(enemyPos.west());
        BlockPos bestSurround = this.findBestBlock(surroundBlocks);
        if (bestSurround != null) {
            return bestSurround;
        }

        if (this.targetHead.get()) {
            BlockPos aboveHead = enemyPos.above(2);
            BlockState aboveState = this.mc.level.getBlockState(aboveHead);
            if (!aboveState.isAir()
                && aboveState.getDestroySpeed(this.mc.level, aboveHead) != -1.0F
                && this.inRange(aboveHead)
                && !this.isMiningBlock(aboveHead)
                && this.isResistantBlock(aboveState)
                && !this.isOwnSurroundBlock(aboveHead)) {
                return aboveHead;
            }
        }

        return null;
    }

    private BlockPos findBestBlock(List<BlockPos> positions) {
        BlockPos bestBlock = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : positions) {
            if (this.inRange(pos)) {
                BlockState state = this.mc.level.getBlockState(pos);
                if (!state.isAir()
                    && state.getDestroySpeed(this.mc.level, pos) != -1.0F
                    && !this.isMiningBlock(pos)
                    && !this.isOwnSurroundBlock(pos)
                    && this.isResistantBlock(state)) {
                    double dist = GrimUtils.closestEyeDistanceSqTo(this.mc.player, new AABB(pos));
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestBlock = pos;
                    }
                }
            }
        }

        return bestBlock;
    }

    private BlockPos getAntiCrawlBlock() {
        if (this.mc.player != null && this.mc.level != null) {
            BlockPos playerPos = this.mc.player.blockPosition();
            BlockPos blockAbove = playerPos.above();
            BlockState state = this.mc.level.getBlockState(blockAbove);
            return !state.isAir()
                    && state.getDestroySpeed(this.mc.level, blockAbove) != -1.0F
                    && !state.is(Blocks.BEDROCK)
                    && !state.is(Blocks.REINFORCED_DEEPSLATE)
                    && !state.is(Blocks.BARRIER)
                    && this.inRange(blockAbove)
                ? blockAbove
                : null;
        } else {
            return null;
        }
    }

    private boolean isOwnSurroundBlock(BlockPos pos) {
        BlockPos playerPos = this.mc.player.blockPosition();
        return !pos.equals(playerPos.north())
                && !pos.equals(playerPos.south())
                && !pos.equals(playerPos.east())
                && !pos.equals(playerPos.west())
            ? pos.equals(playerPos.above()) || pos.equals(playerPos.above(2))
            : true;
    }

    private boolean isResistantBlock(BlockState state) {
        return !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.REINFORCED_DEEPSLATE)
                && !state.is(Blocks.BARRIER)
                && !state.is(Blocks.COMMAND_BLOCK)
                && !state.is(Blocks.STRUCTURE_BLOCK)
            ? state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.ENDER_CHEST)
                || state.is(Blocks.ANCIENT_DEBRIS)
                || state.is(Blocks.RESPAWN_ANCHOR)
            : false;
    }

    private boolean sentAimHitsBlock(BlockPos pos) {
        RotationUtils rot = RotationUtils.getInstance();
        Vec3 dir = RotationUtils.getRotationVector(rot.getServerPitch(), rot.getServerYaw());
        AABB box = new AABB(pos);
        double reach = this.mc.player.blockInteractionRange();
        Vec3 base = this.mc.player.position();

        for (double h : GrimUtils.getPossibleEyeHeights(this.mc.player)) {
            Vec3 eye = base.add(0.0, h, 0.0);
            if (box.contains(eye) || box.clip(eye, eye.add(dir.scale(reach))).isPresent()) {
                return true;
            }
        }

        return false;
    }

    private Vec3 aimPointIn(BlockPos pos) {
        Vec3 eye = this.mc.player.getEyePosition();
        Vec3 camDir = RotationUtils.getRotationVector(this.mc.player.getXRot(), this.mc.player.getYRot());
        AABB box = new AABB(pos).deflate(0.15);
        double t = Mth.clamp(box.getCenter().subtract(eye).dot(camDir), 0.0, this.mc.player.blockInteractionRange());
        Vec3 p = eye.add(camDir.scale(t));
        return new Vec3(
            Mth.clamp(p.x, box.minX, box.maxX),
            Mth.clamp(p.y, box.minY, box.maxY),
            Mth.clamp(p.z, box.minZ, box.maxZ)
        );
    }

    private void assertMiningAim(BlockPos pos) {
        float[] rotations = getRotationsTo(this.mc.player.getEyePosition(), this.aimPointIn(pos));
        RotationUtils.getInstance().setRotationSilent(this, 10, rotations[0], rotations[1]);
    }

    private boolean canSpoofAim() {
        return !this.mc.player.isFallFlying() && !this.mc.player.isSwimming() && !this.mc.player.isPassenger();
    }

    private Direction getInteractDirection(BlockPos pos) {
        Vec3 eyePos = this.mc.player.getEyePosition();
        Vec3 posVec = Vec3.atCenterOf(pos);
        Direction bestDir = null;
        double bestDot = -1.0;

        for (Direction dir : Direction.values()) {
            Vec3 dirVec = Vec3.atLowerCornerOf(dir.getUnitVec3i());
            double dot = eyePos.subtract(posVec).normalize().dot(dirVec);
            if (dot > bestDot) {
                bestDot = dot;
                bestDir = dir;
            }
        }

        return bestDir;
    }

    private static final class FadeEntry {
        private BepMine.MiningData data;
        private final FadeAnimator fade = new FadeAnimator();

        private FadeEntry(BepMine.MiningData data) {
            this.data = data;
        }
    }

    public enum GrimBypass {
        AUTO,
        ALWAYS,
        OFF;
    }

    public class MiningData {
        private final BlockPos pos;
        private Direction direction;
        private final boolean autoTarget;
        private final boolean manualFace;
        private Block minedBlock;
        private float lastDamage;
        private float damage;
        private boolean started;
        private int alignTicks;
        private int heldTicks;
        private int ticksMined;
        private long startMs;
        private float grimMaxDelta;
        private boolean fastStart;
        private int graceTicks;
        private float swapPoint;

        public MiningData(BlockPos pos, Direction direction, boolean autoTarget, boolean manualFace) {
            this.pos = pos;
            this.direction = direction;
            this.autoTarget = autoTarget;
            this.manualFace = manualFace;
            this.swapPoint = BepMine.this.rollSwapPoint();
        }

        public BlockPos getPos() {
            return this.pos;
        }

        public Direction getDirection() {
            return this.direction;
        }

        public BlockState getState() {
            return BepMine.this.mc.level.getBlockState(this.pos);
        }

        public float getBlockDamage() {
            return this.damage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o != null && this.getClass() == o.getClass()) {
                BepMine.MiningData that = (BepMine.MiningData)o;
                return this.pos.equals(that.pos);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return this.pos.hashCode();
        }
    }

    public enum SpeedmineMode {
        PACKET,
        DAMAGE;
    }

    public enum Swap {
        NORMAL,
        SILENT,
        OFF;
    }

    public enum SwingMode {
        FULL,
        PACKET,
        NONE;
    }
}
