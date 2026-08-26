package bep.hax.mixin;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.Notifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatMentionMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component modifyMessage(Component message) {
        if (this.minecraft.player == null) {
            return message;
        }

        Notifier notifier = Modules.get().get(Notifier.class);
        if (notifier != null && notifier.isActive()) {
            Setting<?> highlightSetting = notifier.settings.get("highlight-mentions");
            if (highlightSetting != null && (Boolean)highlightSetting.get()) {
                String chatMessage = message.getString();
                String playerName = this.minecraft.player.getName().getString();
                if (chatMessage.toLowerCase().contains(playerName.toLowerCase())) {
                    MutableComponent highlightedMessage = message.copy();
                    highlightedMessage.setStyle(message.getStyle().withBold(true));
                    Setting<?> soundSetting = notifier.settings.get("mention-sound");
                    if (soundSetting != null && (Boolean)soundSetting.get()) {
                        Setting<?> volumeSetting = notifier.settings.get("mention-volume");
                        float volume = volumeSetting != null ? ((Double)volumeSetting.get()).floatValue() : 1.0F;
                        this.minecraft.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), volume, 1.0F);
                    }

                    return highlightedMessage;
                } else {
                    return message;
                }
            } else {
                return message;
            }
        } else {
            return message;
        }
    }
}
