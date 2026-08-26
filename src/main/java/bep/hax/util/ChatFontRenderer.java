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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;

public class ChatFontRenderer {
    private static ChatFontRenderer INSTANCE;
    private static final double VANILLA_TEXT_HEIGHT = 9.0;
    private final List<ChatFontRenderer.Entry> queue = new ArrayList<>();
    private final MeteorFontReplay replay = new MeteorFontReplay();
    private volatile long messageTimeNanos = 0L;
    private boolean renderErrorLogged;

    public static ChatFontRenderer get() {
        if (INSTANCE == null) {
            INSTANCE = new ChatFontRenderer();
        }

        return INSTANCE;
    }

    private ChatFontRenderer() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public void submit(FormattedCharSequence text, double x, double y, float scaleF, float opacity) {
        this.queue.add(new ChatFontRenderer.Entry(text, x, y, scaleF, opacity));
    }

    public static boolean tryCapture(GuiGraphics context, int y, float opacity, FormattedCharSequence text) {
        if (!BetterChatConfigHolder.useCustomFont()) {
            return false;
        } else {
            CustomTextRenderer renderer = Fonts.RENDERER;
            if (renderer != null && !renderer.isBuilding()) {
                Matrix3x2fStack pose = context.pose();
                Vector2f origin = pose.transformPosition(0.0F, y, new Vector2f());
                get().submit(text, origin.x, origin.y, pose.m00(), opacity);
                return true;
            } else {
                return false;
            }
        }
    }

    public double chatWrapFactor() {
        CustomTextRenderer renderer = Fonts.RENDERER;
        if (renderer != null && !renderer.isBuilding()) {
            String sample = "abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789";
            double customWidth = renderer.getWidth(sample, false);
            double customHeight = renderer.getHeight(false);
            double vanillaWidth = MeteorClient.mc.font.width(sample);
            if (!(customWidth <= 0.0) && !(customHeight <= 0.0)) {
                double factor = vanillaWidth / customWidth * (customHeight / 9.0);
                return Mth.clamp(factor * 0.98, 1.0, 4.0);
            } else {
                return 1.0;
            }
        } else {
            return 1.0;
        }
    }

    public void onMessageReceived() {
        if (BetterChatConfigHolder.getAnimation() != BetterChatConfigHolder.ChatAnimation.None) {
            this.messageTimeNanos = System.nanoTime();
        }
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
                TextRenderer renderer = new FallbackTextRenderer(base);
                double guiScale = MeteorClient.mc.getWindow().getGuiScale();
                BetterChatConfigHolder.ChatAnimation effect = BetterChatConfigHolder.getAnimation();
                double duration = Math.max(0.001, BetterChatConfigHolder.getAnimationDuration());
                double progress = effect == BetterChatConfigHolder.ChatAnimation.None
                    ? 1.0
                    : Mth.clamp((System.nanoTime() - this.messageTimeNanos) / (duration * 1.0E9), 0.0, 1.0);
                double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
                boolean animating = effect != BetterChatConfigHolder.ChatAnimation.None && progress < 1.0;
                int newest = animating ? this.newestIndex() : -1;
                double lineHeight = 9.0 * this.queue.get(0).scaleF * guiScale;

                try {
                    renderer.setAlpha(1.0);
                    double openScale = Double.NaN;
                    boolean open = false;

                    for (int idx = 0; idx < this.queue.size(); idx++) {
                        ChatFontRenderer.Entry entry = this.queue.get(idx);
                        double scaleMul = 1.0;
                        double alphaMul = 1.0;
                        double xOff = 0.0;
                        double yOff = 0.0;
                        if (animating) {
                            boolean isNew = idx == newest;
                            switch (effect) {
                                case Fade:
                                    if (isNew) {
                                        alphaMul = eased;
                                    }
                                    break;
                                case SlideUp:
                                    yOff = (1.0 - eased) * lineHeight;
                                    if (isNew) {
                                        alphaMul = eased;
                                    }
                                    break;
                                case SlideLeft:
                                    if (isNew) {
                                        xOff = (1.0 - eased) * lineHeight * 2.0;
                                        alphaMul = eased;
                                    }
                                    break;
                                case Scale:
                                    if (isNew) {
                                        scaleMul = 0.6 + 0.4 * eased;
                                        alphaMul = eased;
                                    }
                            }
                        }

                        double scale = 9.0 * entry.scaleF * guiScale / 18.666666666666668 * scaleMul;
                        if (!(scale <= 0.0)) {
                            if (!open || scale != openScale) {
                                if (open) {
                                    renderer.end();
                                }

                                renderer.begin(scale);
                                openScale = scale;
                                open = true;
                            }

                            this.renderEntry(renderer, entry, entry.x * guiScale + xOff, entry.y * guiScale + yOff, alphaMul);
                        }
                    }
                } catch (Throwable t) {
                    if (!this.renderErrorLogged) {
                        this.renderErrorLogged = true;
                        Bep.LOG.error("[BetterChat] Custom font chat render failed", t);
                    }
                } finally {
                    try {
                        if (renderer.isBuilding()) {
                            renderer.end();
                        }
                    } catch (Throwable var38) {
                    }

                    this.queue.clear();
                }
            } else {
                this.queue.clear();
            }
        }
    }

    private int newestIndex() {
        int best = 0;
        double bestY = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < this.queue.size(); i++) {
            if (this.queue.get(i).y > bestY) {
                bestY = this.queue.get(i).y;
                best = i;
            }
        }

        return best;
    }

    private void renderEntry(TextRenderer renderer, ChatFontRenderer.Entry entry, double x, double y, double alphaMul) {
        int alpha = Mth.clamp(Math.round(entry.opacity * (float)alphaMul * 255.0F), 0, 255);
        if (alpha != 0) {
            this.replay.renderLine(renderer, entry.text, 16777215, alpha, x, y);
        }
    }

    private record Entry(FormattedCharSequence text, double x, double y, float scaleF, float opacity) {
    }
}
