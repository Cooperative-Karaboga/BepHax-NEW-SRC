package bep.hax.mixin.meteor;

import bep.hax.util.BetterChatConfigHolder;
import bep.hax.util.EnemyColorManager;
import bep.hax.util.EnemyManager;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.misc.BetterChat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BetterChat.class, remap = false)
public class BetterChatMixin {
    @Shadow
    private SettingGroup sgGeneral;
    @Unique
    private static final Pattern bephax$PLAYER_NAME_PATTERN = Pattern.compile("<([a-zA-Z0-9_]{1,16})>");
    @Unique
    private Setting<Boolean> bephax$colorFriendNames;
    @Unique
    private Setting<Boolean> bephax$colorEnemyNames;
    @Unique
    private Setting<Boolean> bephax$customFont;
    @Unique
    private Setting<BetterChatConfigHolder.ChatAnimation> bephax$chatAnimation;
    @Unique
    private Setting<Double> bephax$animationDuration;
    @Unique
    private Setting<Boolean> bephax$noChatBackground;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void addSettings(CallbackInfo ci) {
        this.bephax$colorFriendNames = this.sgGeneral
            .add(new Builder().name("color-friend-names").description("Colors friend player names in chat messages.").defaultValue(true).build());
        this.bephax$colorEnemyNames = this.sgGeneral
            .add(new Builder().name("color-enemy-names").description("Colors enemy player names in chat messages.").defaultValue(true).build());
        this.bephax$customFont = this.sgGeneral
            .add(
                new Builder()
                    .name("custom-font")
                    .description("Renders the chat message history with Meteor's custom font instead of the vanilla font.")
                    .defaultValue(false)
                    .build()
            );
        BetterChatConfigHolder.setCustomFontSetting(this.bephax$customFont);
        this.bephax$chatAnimation = this.sgGeneral
            .add(
                ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                                    .name("receive-animation"))
                                .description("Animates incoming messages when using the custom font."))
                            .defaultValue(BetterChatConfigHolder.ChatAnimation.None))
                        .visible(() -> this.bephax$customFont.get()))
                    .build()
            );
        BetterChatConfigHolder.setAnimationSetting(this.bephax$chatAnimation);
        this.bephax$animationDuration = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("animation-duration")
                    .description("How long the receive animation takes, in seconds.")
                    .defaultValue(0.25)
                    .min(0.0)
                    .sliderRange(0.0, 1.0)
                    .visible(() -> this.bephax$customFont.get() && this.bephax$chatAnimation.get() != BetterChatConfigHolder.ChatAnimation.None)
                    .build()
            );
        BetterChatConfigHolder.setAnimationDurationSetting(this.bephax$animationDuration);
        this.bephax$noChatBackground = this.sgGeneral
            .add(
                new Builder()
                    .name("no-chat-background")
                    .description("Removes the translucent background box behind chat lines (stops it covering custom-font text).")
                    .defaultValue(false)
                    .build()
            );
        BetterChatConfigHolder.setNoChatBackgroundSetting(this.bephax$noChatBackground);
    }

    @Inject(method = "onMessageReceive", at = @At("TAIL"))
    private void colorPlayerNames(ReceiveMessageEvent event, CallbackInfo ci) {
        if (!event.isCancelled()) {
            if (this.bephax$colorFriendNames.get() || this.bephax$colorEnemyNames.get()) {
                Component message = event.getMessage();
                String fullText = message.getString();
                Matcher matcher = bephax$PLAYER_NAME_PATTERN.matcher(fullText);
                if (matcher.find()) {
                    String playerName = matcher.group(1);
                    boolean isFriend = this.bephax$colorFriendNames.get() && Friends.get().get(playerName) != null;
                    boolean isEnemy = !isFriend
                        && this.bephax$colorEnemyNames.get()
                        && EnemyColorManager.getEnemyColorSetting() != null
                        && EnemyManager.get().isEnemy(playerName);
                    if (isFriend || isEnemy) {
                        int color = isFriend ? Config.get().friendColor.get().getPacked() : EnemyColorManager.getEnemyColorSetting().get().getPacked();
                        MutableComponent newMessage = Component.empty();
                        message.visit((style, text) -> {
                            if (text.equals(playerName)) {
                                newMessage.append(Component.literal(text).setStyle(style.withColor(TextColor.fromRgb(color))));
                            } else if (text.contains(playerName)) {
                                int idx = text.indexOf(playerName);
                                if (idx > 0) {
                                    newMessage.append(Component.literal(text.substring(0, idx)).setStyle(style));
                                }

                                newMessage.append(Component.literal(playerName).setStyle(style.withColor(TextColor.fromRgb(color))));
                                int afterIdx = idx + playerName.length();
                                if (afterIdx < text.length()) {
                                    newMessage.append(Component.literal(text.substring(afterIdx)).setStyle(style));
                                }
                            } else {
                                newMessage.append(Component.literal(text).setStyle(style));
                            }

                            return Optional.empty();
                        }, Style.EMPTY);
                        event.setMessage(newMessage);
                    }
                }
            }
        }
    }
}
