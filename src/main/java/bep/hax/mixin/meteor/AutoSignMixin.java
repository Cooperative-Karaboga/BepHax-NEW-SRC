package bep.hax.mixin.meteor;

import bep.hax.util.PendingSignData;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.mixin.AbstractSignEditScreenAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.world.AutoSign;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutoSign.class, remap = false)
public abstract class AutoSignMixin extends Module {
    @Shadow
    @Final
    private SettingGroup sgGeneral;
    @Shadow
    private String[] text;
    @Unique
    @Nullable
    private SettingGroup bephaxGroup = null;
    @Unique
    @Nullable
    private Setting<Boolean> useCustomText = null;
    @Unique
    @Nullable
    private Setting<String> line1 = null;
    @Unique
    @Nullable
    private Setting<String> line2 = null;
    @Unique
    @Nullable
    private Setting<String> line3 = null;
    @Unique
    @Nullable
    private Setting<String> line4 = null;
    @Unique
    @Nullable
    private Setting<Boolean> realisticMode = null;
    @Unique
    @Nullable
    private Setting<Integer> baseDelay = null;
    @Unique
    @Nullable
    private Setting<Integer> maxRandomDelay = null;
    @Unique
    @Nullable
    private Setting<Boolean> randomTextEnabled = null;
    @Unique
    @Nullable
    private Setting<Boolean> includeLetters = null;
    @Unique
    @Nullable
    private Setting<Boolean> includeNumbers = null;
    @Unique
    private final Queue<PendingSignData> pendingQueue = new ArrayDeque<>();
    @Unique
    private final Random random = new Random();
    @Unique
    private SignBlockEntity pendingSignEntity = null;
    @Unique
    private int tickCounter = 0;
    @Unique
    private boolean waitingToSend = false;
    @Unique
    private static final Pattern RANDOM_PATTERN = Pattern.compile("%(r+)%");
    @Unique
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    @Unique
    private static final String NUMBERS = "0123456789";

    public AutoSignMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void addBepHaxSettings(CallbackInfo ci) {
        this.bephaxGroup = this.settings.createGroup("BepHax Sign Settings");
        this.useCustomText = this.bephaxGroup
            .add(
                new Builder()
                    .name("use-custom-text")
                    .description("Use custom text instead of copying from first sign (text won't be lost on failure).")
                    .defaultValue(false)
                    .build()
            );
        this.line1 = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                    .name("line-1")
                    .description("First line of the sign. Use %r% for random chars (e.g., %rrr% = 3 random).")
                    .defaultValue("")
                    .visible(() -> this.useCustomText != null && this.useCustomText.get())
                    .build()
            );
        this.line2 = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                    .name("line-2")
                    .description("Second line of the sign.")
                    .defaultValue("")
                    .visible(() -> this.useCustomText != null && this.useCustomText.get())
                    .build()
            );
        this.line3 = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                    .name("line-3")
                    .description("Third line of the sign.")
                    .defaultValue("")
                    .visible(() -> this.useCustomText != null && this.useCustomText.get())
                    .build()
            );
        this.line4 = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                    .name("line-4")
                    .description("Fourth line of the sign.")
                    .defaultValue("")
                    .visible(() -> this.useCustomText != null && this.useCustomText.get())
                    .build()
            );
        this.realisticMode = this.bephaxGroup
            .add(new Builder().name("realistic-mode").description("Add realistic delays to bypass 2B2T anti-cheat detection.").defaultValue(true).build());
        this.baseDelay = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("base-delay")
                    .description("Base delay in ticks before sending sign (20 ticks = 1 second).")
                    .defaultValue(15)
                    .range(5, 60)
                    .sliderRange(5, 60)
                    .visible(() -> this.realisticMode != null && this.realisticMode.get())
                    .build()
            );
        this.maxRandomDelay = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("max-random-delay")
                    .description("Maximum additional random delay in ticks.")
                    .defaultValue(10)
                    .range(0, 30)
                    .sliderRange(0, 30)
                    .visible(() -> this.realisticMode != null && this.realisticMode.get())
                    .build()
            );
        this.randomTextEnabled = this.bephaxGroup
            .add(
                new Builder()
                    .name("random-text")
                    .description("Enable %r% placeholders for anti-spam. %r%=1 char, %rr%=2, %rrr%=3, etc.")
                    .defaultValue(true)
                    .build()
            );
        this.includeLetters = this.bephaxGroup
            .add(
                new Builder()
                    .name("include-letters")
                    .description("Include letters in random text generation.")
                    .defaultValue(true)
                    .visible(() -> this.randomTextEnabled != null && this.randomTextEnabled.get())
                    .build()
            );
        this.includeNumbers = this.bephaxGroup
            .add(
                new Builder()
                    .name("include-numbers")
                    .description("Include numbers in random text generation.")
                    .defaultValue(true)
                    .visible(() -> this.randomTextEnabled != null && this.randomTextEnabled.get())
                    .build()
            );
    }

    @Inject(method = "onDeactivate", at = @At("TAIL"))
    private void onDeactivateBepHax(CallbackInfo ci) {
        this.pendingQueue.clear();
        this.pendingSignEntity = null;
        this.tickCounter = 0;
        this.waitingToSend = false;
    }

    @Unique
    private String buildCharacterSet() {
        StringBuilder chars = new StringBuilder();
        if (this.includeLetters != null && this.includeLetters.get()) {
            chars.append("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
        }

        if (this.includeNumbers != null && this.includeNumbers.get()) {
            chars.append("0123456789");
        }

        if (chars.length() == 0) {
            chars.append("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
        }

        return chars.toString();
    }

    @Unique
    private String generateRandomString(int length) {
        String charSet = this.buildCharacterSet();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(charSet.charAt(this.random.nextInt(charSet.length())));
        }

        return sb.toString();
    }

    @Unique
    private String processRandomPlaceholders(String input) {
        if (this.randomTextEnabled == null || !this.randomTextEnabled.get()) {
            return input;
        }

        if (input == null) {
            return "";
        }

        Matcher matcher = RANDOM_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            int count = matcher.group(1).length();
            String replacement = this.generateRandomString(count);
            matcher.appendReplacement(result, replacement);
        }

        matcher.appendTail(result);
        return result.toString();
    }

    @Unique
    private String[] getSignText() {
        return this.useCustomText != null && this.useCustomText.get()
            ? new String[]{
                this.line1 != null ? this.line1.get() : "",
                this.line2 != null ? this.line2.get() : "",
                this.line3 != null ? this.line3.get() : "",
                this.line4 != null ? this.line4.get() : ""
            }
            : this.text;
    }

    @Unique
    private String[] processText(String[] originalText) {
        if (originalText == null) {
            return new String[]{"", "", "", ""};
        }

        String[] processed = new String[4];

        for (int i = 0; i < 4; i++) {
            String line = i < originalText.length ? originalText[i] : "";
            processed[i] = this.processRandomPlaceholders(line != null ? line : "");
        }

        return processed;
    }

    @Unique
    private int calculateDelay() {
        if (this.realisticMode != null && this.realisticMode.get()) {
            int base = this.baseDelay != null ? this.baseDelay.get() : 15;
            int maxRandom = this.maxRandomDelay != null ? this.maxRandomDelay.get() : 10;
            int randomExtra = maxRandom > 0 ? this.random.nextInt(maxRandom + 1) : 0;
            return base + randomExtra;
        } else {
            return 10;
        }
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void onTickRealistic(Post event, CallbackInfo ci) {
        if (this.mc.player != null) {
            if (this.waitingToSend && this.pendingSignEntity != null) {
                this.tickCounter++;
                PendingSignData pending = this.pendingQueue.peek();
                if (pending != null && this.tickCounter >= pending.ticksRemaining) {
                    this.pendingQueue.poll();
                    this.mc
                        .player
                        .connection
                        .send(new ServerboundSignUpdatePacket(pending.pos, pending.front, pending.text[0], pending.text[1], pending.text[2], pending.text[3]));
                    this.mc.setScreen(null);
                    this.pendingSignEntity = null;
                    this.tickCounter = 0;
                    this.waitingToSend = false;
                }

                ci.cancel();
            } else {
                if (this.realisticMode != null && this.realisticMode.get()) {
                    PendingSignData pending = this.pendingQueue.peek();
                    if (pending != null && !this.waitingToSend) {
                        this.tickCounter++;
                        if (this.tickCounter >= pending.ticksRemaining) {
                            this.pendingQueue.poll();
                            this.mc
                                .player
                                .connection
                                .send(new ServerboundSignUpdatePacket(pending.pos, pending.front, pending.text[0], pending.text[1], pending.text[2], pending.text[3]));
                            this.tickCounter = 0;
                        }

                        ci.cancel();
                    }
                }
            }
        }
    }

    @Inject(method = "onOpenScreen", at = @At("HEAD"), cancellable = true)
    private void onOpenScreenRealistic(OpenScreenEvent event, CallbackInfo ci) {
        if (event.screen instanceof AbstractSignEditScreen) {
            String[] signText = this.getSignText();
            if (this.useCustomText != null && this.useCustomText.get() || signText != null) {
                AbstractSignEditScreen signScreen = (AbstractSignEditScreen)event.screen;
                SignBlockEntity sign = ((AbstractSignEditScreenAccessor)signScreen).meteor$getSign();
                String[] processedText = this.processText(signText);
                int delay = this.calculateDelay();
                this.pendingQueue.add(new PendingSignData(sign.getBlockPos(), true, processedText, delay));
                this.pendingSignEntity = sign;
                this.tickCounter = 0;
                this.waitingToSend = true;
                ci.cancel();
            }
        }
    }
}
