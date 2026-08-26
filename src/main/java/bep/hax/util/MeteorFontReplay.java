package bep.hax.util;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

public final class MeteorFontReplay {
    public static final double METEOR_HEIGHT_PER_SCALE = 18.666666666666668;
    private final List<MeteorFontReplay.Run> runs = new ArrayList<>();
    private final Color color = new Color();

    public static boolean replayAfterGui() {
        Screen screen = Minecraft.getInstance().screen;
        return screen == null || screen instanceof ChatScreen;
    }

    public double renderLine(TextRenderer renderer, FormattedCharSequence text, int defaultRgb, int alpha, double x, double y) {
        this.runs.clear();
        StringBuilder current = new StringBuilder();
        int[] currentRgb = new int[]{Integer.MIN_VALUE};
        boolean[] currentBold = new boolean[]{false};
        text.accept((index, style, codePoint) -> {
            TextColor textColor = style.getColor();
            int rgb = textColor != null ? textColor.getValue() : defaultRgb;
            boolean bold = style.isBold();
            if ((currentRgb[0] != rgb || currentBold[0] != bold) && current.length() > 0) {
                this.runs.add(new MeteorFontReplay.Run(current.toString(), currentRgb[0], currentBold[0]));
                current.setLength(0);
            }

            currentRgb[0] = rgb;
            currentBold[0] = bold;
            current.appendCodePoint(codePoint);
            return true;
        });
        if (current.length() > 0) {
            this.runs.add(new MeteorFontReplay.Run(current.toString(), currentRgb[0], currentBold[0]));
        }

        double boldOffset = renderer.getHeight(false) / 9.0;
        double penX = x;

        for (MeteorFontReplay.Run run : this.runs) {
            this.color.set(run.rgb >> 16 & 0xFF, run.rgb >> 8 & 0xFF, run.rgb & 0xFF, alpha);
            renderer.render(run.text, penX, y, this.color, true);
            if (run.bold) {
                renderer.render(run.text, penX + boldOffset, y, this.color, true);
            }

            penX += renderer.getWidth(run.text, false);
        }

        return penX - x;
    }

    private record Run(String text, int rgb, boolean bold) {
    }
}
