package bep.hax.util;

import bep.hax.Bep;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.renderer.text.CustomTextRenderer;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

public class TabFontRenderer {
    private static TabFontRenderer INSTANCE;
    private static final double VANILLA_TEXT_HEIGHT = 8.0;
    private static final double FALLBACK_WIDTH_FACTOR = 0.8571428571428571;
    private final List<TabFontRenderer.Entry> queue = new ArrayList<>();
    private final MeteorFontReplay replay = new MeteorFontReplay();
    private final StringBuilder plain = new StringBuilder();
    private boolean renderErrorLogged;

    public static TabFontRenderer get() {
        if (INSTANCE == null) {
            INSTANCE = new TabFontRenderer();
        }

        return INSTANCE;
    }

    private TabFontRenderer() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public boolean isAvailable() {
        CustomTextRenderer renderer = Fonts.RENDERER;
        return renderer != null && !renderer.isBuilding();
    }

    public void submit(FormattedCharSequence text, int x, int y, int argb) {
        this.queue.add(new TabFontRenderer.Entry(text, x, y, argb));
    }

    public int scaledWidth(String text) {
        if (text != null && !text.isEmpty()) {
            CustomTextRenderer renderer = Fonts.RENDERER;
            if (renderer != null && !renderer.isBuilding()) {
                double customHeight = renderer.getHeight(false);
                if (customHeight <= 0.0) {
                    return MeteorClient.mc.font.width(text);
                }

                double customFactor = 8.0 / customHeight;
                double width = 0.0;
                int i = 0;
                int n = text.length();

                while (i < n) {
                    int cp = text.codePointAt(i);
                    boolean custom = FallbackTextRenderer.supports(cp);
                    int runStart = i;
                    i += Character.charCount(cp);

                    while (i < n) {
                        int next = text.codePointAt(i);
                        if (FallbackTextRenderer.supports(next) != custom) {
                            break;
                        }

                        i += Character.charCount(next);
                    }

                    String run = text.substring(runStart, i);
                    if (custom) {
                        width += renderer.getWidth(run, false) * customFactor;
                    } else {
                        width += MeteorClient.mc.font.width(run) * 0.8571428571428571;
                    }
                }

                return (int)Math.ceil(width);
            } else {
                return MeteorClient.mc.font.width(text);
            }
        } else {
            return 0;
        }
    }

    public int scaledWidth(FormattedText text) {
        return this.scaledWidth(text.getString());
    }

    public int scaledWidth(FormattedCharSequence text) {
        this.plain.setLength(0);
        text.accept((index, style, codePoint) -> {
            this.plain.appendCodePoint(codePoint);
            return true;
        });
        return this.scaledWidth(this.plain.toString());
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (!MeteorFontReplay.replayAfterGui()) {
            this.flush();
        }
    }

    public boolean hasQueued() {
        return !this.queue.isEmpty();
    }

    public void flush() {
        if (!this.queue.isEmpty()) {
            CustomTextRenderer base = Fonts.RENDERER;
            if (base != null && !base.isBuilding()) {
                double guiScale = MeteorClient.mc.getWindow().getGuiScale();
                double scale = 8.0 * guiScale / 18.666666666666668;
                TextRenderer renderer = new FallbackTextRenderer(base);

                try {
                    renderer.setAlpha(1.0);
                    renderer.begin(scale);

                    for (TabFontRenderer.Entry entry : this.queue) {
                        this.renderEntry(renderer, entry, entry.x * guiScale, entry.y * guiScale);
                    }
                } catch (Throwable t) {
                    if (!this.renderErrorLogged) {
                        this.renderErrorLogged = true;
                        Bep.LOG.error("[BetterTab] Custom font tab render failed", t);
                    }
                } finally {
                    try {
                        if (renderer.isBuilding()) {
                            renderer.end();
                        }
                    } catch (Throwable var16) {
                    }

                    this.queue.clear();
                }
            } else {
                this.queue.clear();
            }
        }
    }

    private void renderEntry(TextRenderer renderer, TabFontRenderer.Entry entry, double x, double y) {
        int alpha = entry.argb >>> 24 & 0xFF;
        if (alpha == 0) {
            if (BetterTabConfigHolder.isFadeActive()) {
                return;
            }

            alpha = 255;
        }

        int fallbackRgb = entry.argb & 16777215;
        this.replay.renderLine(renderer, entry.text, fallbackRgb, alpha, x, y);
    }

    private record Entry(FormattedCharSequence text, int x, int y, int argb) {
    }
}
