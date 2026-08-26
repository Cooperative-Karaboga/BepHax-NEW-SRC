package bep.hax.mixin;

import bep.hax.modules.PearlLoader;
import bep.hax.modules.livemessage.LiveMessage;
import bep.hax.modules.livemessage.gui.LivemessageGui;
import bep.hax.modules.livemessage.util.LivemessageUtil;
import bep.hax.util.BetterChatConfigHolder;
import bep.hax.util.ChatFontRenderer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatHudMixin {
    @ModifyExpressionValue(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(D)I"))
    private int bephax$widenChatWrap(int width) {
        return !BetterChatConfigHolder.useCustomFont() ? width : (int)Math.round(width * ChatFontRenderer.get().chatWrapFactor());
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void onLivemessageAddMessage(Component message, MessageSignature signature, GuiMessageTag indicator, CallbackInfo ci) {
        try {
            PearlLoader bephaxPearlLoader = Modules.get().get(PearlLoader.class);
            if (bephaxPearlLoader != null) {
                bephaxPearlLoader.tryTriggerFromChat(message.getString());
            }
        } catch (Exception var13) {
        }

        if (LiveMessage.INSTANCE.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String messageText = message.getString();

                for (Pattern pattern : LivemessageUtil.FROM_PATTERNS) {
                    Matcher matcher = pattern.matcher(messageText);
                    if (matcher.find()) {
                        String username = matcher.group(1);
                        String msg = matcher.group(2);
                        boolean shouldHide = LivemessageGui.newMessage(username, msg, false);
                        if (shouldHide) {
                            ci.cancel();
                        }

                        return;
                    }
                }

                for (Pattern pattern : LivemessageUtil.TO_PATTERNS) {
                    Matcher matcher = pattern.matcher(messageText);
                    if (matcher.find()) {
                        String username = matcher.group(1);
                        String msg = matcher.group(2);
                        boolean shouldHide = LivemessageGui.newMessage(username, msg, true);
                        if (shouldHide) {
                            ci.cancel();
                        }

                        return;
                    }
                }
            }
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("TAIL"))
    private void bephax$animateVisibleMessage(Component message, MessageSignature signature, GuiMessageTag indicator, CallbackInfo ci) {
        ChatFontRenderer.get().onMessageReceived();
    }
}
