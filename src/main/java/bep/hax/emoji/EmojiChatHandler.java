package bep.hax.emoji;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;

public class EmojiChatHandler {
    public static final EmojiChatHandler INSTANCE = new EmojiChatHandler();

    private EmojiChatHandler() {
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (EmojiData.enabled()) {
            Component message = event.getMessage();
            Component expanded = EmojiData.expandText(message);
            if (expanded != message) {
                event.setMessage(expanded);
            }
        }
    }
}
