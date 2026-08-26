package bep.hax.util.commands;

import java.util.Arrays;
import java.util.UUID;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerToast implements Toast {
    private static final int TITLE_COLOR = Color.fromRGBA(145, 209, 62, 255);
    private static final int TEXT_COLOR = Color.fromRGBA(220, 220, 220, 255);
    private static final Identifier TEXTURE = Identifier.parse("toast/advancement");
    private static final SoundInstance DEFAULT_SOUND = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 1.0F);
    @NotNull
    private final Component title;
    @Nullable
    private final String[] textLines;
    @Nullable
    private final UUID playerUuid;
    @Nullable
    private final PlayerInfo playerEntry;
    private final long duration;
    private boolean playedSound;
    private long start = -1L;
    private net.minecraft.client.gui.components.toasts.Toast.Visibility visibility = net.minecraft.client.gui.components.toasts.Toast.Visibility.HIDE;

    private PlayerToast(String title, String text, UUID playerUuid, long duration) {
        String finalTitle = title != null && !title.trim().isEmpty() ? title : "2b2t API";
        this.title = Component.literal(finalTitle);
        if (text != null && !text.trim().isEmpty()) {
            String[] lines = text.split("\n");
            this.textLines = Arrays.stream(lines).map(String::trim).filter(line -> !line.isEmpty()).toArray(String[]::new);
        } else {
            this.textLines = null;
        }

        this.playerUuid = playerUuid;
        this.playerEntry = playerUuid != null && MeteorClient.mc.getConnection() != null ? MeteorClient.mc.getConnection().getPlayerInfo(playerUuid) : null;
        this.duration = duration;
    }

    public static PlayerToast create(String title, String text, String playerName, long duration) {
        UUID uuid = getPlayerUuidByName(playerName);
        return new PlayerToast(title, text, uuid, duration);
    }

    private static UUID getPlayerUuidByName(String name) {
        if (MeteorClient.mc.getConnection() != null && name != null) {
            PlayerInfo entry = MeteorClient.mc.getConnection().getPlayerInfo(name);
            return entry != null ? entry.getProfile().id() : null;
        } else {
            return null;
        }
    }

    @Override
    public int width() {
        if (MeteorClient.mc.font == null) {
            return 160;
        }

        int iconWidth = this.playerEntry != null ? 28 : 12;
        int rightPadding = 12;
        int titleWidth = MeteorClient.mc.font.width(this.title);
        int maxTextWidth = 0;
        if (this.textLines != null) {
            for (String line : this.textLines) {
                int lineWidth = MeteorClient.mc.font.width(line);
                maxTextWidth = Math.max(maxTextWidth, lineWidth);
            }
        }

        int requiredWidth = iconWidth + Math.max(titleWidth, maxTextWidth) + rightPadding;
        return Math.min(320, Math.max(160, requiredWidth));
    }

    @Override
    public int height() {
        int height = 20;
        if (this.textLines != null) {
            height += this.textLines.length * 11;
        }

        return Math.max(32, height);
    }

    @Override
    public net.minecraft.client.gui.components.toasts.Toast.Visibility getWantedVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (this.start == -1L) {
            this.start = time;
        }

        this.visibility = time - this.start >= this.duration ? net.minecraft.client.gui.components.toasts.Toast.Visibility.HIDE : net.minecraft.client.gui.components.toasts.Toast.Visibility.SHOW;
        if (!this.playedSound) {
            MeteorClient.mc.getSoundManager().play(DEFAULT_SOUND);
            this.playedSound = true;
        }
    }

    @Override
    public void render(GuiGraphics context, Font textRenderer, long startTime) {
        context.blitSprite(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, this.width(), this.height());
        boolean hasIcon = this.playerEntry != null;
        if (hasIcon) {
            PlayerFaceRenderer.draw(context, this.playerEntry.getSkin(), 8, 8, 16);
        }

        int textX = hasIcon ? 28 : 12;
        int titleY;
        if (this.textLines != null && this.textLines.length > 0) {
            titleY = 7;
        } else {
            titleY = 12;
        }

        if (this.title != null) {
            context.drawString(textRenderer, this.title, textX, titleY, TITLE_COLOR, false);
        }

        if (this.textLines != null) {
            int lineY = 18;

            for (String line : this.textLines) {
                context.drawString(textRenderer, Component.literal(line), textX, lineY, TEXT_COLOR, false);
                lineY += 11;
            }
        }
    }
}
