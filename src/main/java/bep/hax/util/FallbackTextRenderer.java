package bep.hax.util;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import org.joml.Matrix4f;

public final class FallbackTextRenderer implements TextRenderer {
    private static final FallbackTextRenderer.IsolatedVanillaRenderer ISOLATED = new FallbackTextRenderer.IsolatedVanillaRenderer();
    private final TextRenderer primary;
    private final FallbackTextRenderer.IsolatedVanillaRenderer fallback;

    public FallbackTextRenderer(TextRenderer primary) {
        this.primary = primary;
        this.fallback = ISOLATED;
    }

    public static TextRenderer vanilla() {
        return ISOLATED;
    }

    public static boolean supports(int cp) {
        return cp >= 32 && cp <= 126 || cp >= 160 && cp <= 255 || cp >= 256 && cp <= 383 || cp >= 880 && cp <= 1023 || cp >= 1024 && cp <= 1279 || cp == 8734;
    }

    public static boolean needsFallback(String text) {
        int i = 0;
        int n = text.length();

        while (i < n) {
            int cp = text.codePointAt(i);
            if (!supports(cp)) {
                return true;
            }

            i += Character.charCount(cp);
        }

        return false;
    }

    private TextRenderer rendererFor(int cp) {
        return supports(cp) ? this.primary : this.fallback;
    }

    @Override
    public void setAlpha(double a) {
        this.primary.setAlpha(a);
        this.fallback.setAlpha(a);
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big) {
        if (this.primary.isBuilding()) {
            forceEnd(this.primary);
        }

        if (this.fallback.isBuilding()) {
            forceEnd(this.fallback);
        }

        this.primary.begin(scale, scaleOnly, big);
        this.fallback.begin(scale, scaleOnly, big);
    }

    private static void forceEnd(TextRenderer renderer) {
        try {
            renderer.end();
        } catch (Throwable var2) {
        }
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        if (text.isEmpty()) {
            return 0.0;
        }

        String s = length != text.length() ? text.substring(0, length) : text;
        double width = 0.0;
        int i = 0;
        int n = s.length();

        while (i < n) {
            int cp = s.codePointAt(i);
            TextRenderer r = this.rendererFor(cp);
            int runStart = i;
            i += Character.charCount(cp);

            while (i < n) {
                int next = s.codePointAt(i);
                if (this.rendererFor(next) != r) {
                    break;
                }

                i += Character.charCount(next);
            }

            String run = s.substring(runStart, i);
            width += r.getWidth(run, run.length(), false);
        }

        return width + (shadow ? 1 : 0);
    }

    @Override
    public double getHeight(boolean shadow) {
        return Math.max(this.primary.getHeight(shadow), this.fallback.getHeight(shadow));
    }

    @Override
    public double render(String text, double x, double y, Color color, boolean shadow) {
        if (text.isEmpty()) {
            return 0.0;
        }

        double startX = x;
        int i = 0;
        int n = text.length();

        while (i < n) {
            int cp = text.codePointAt(i);
            TextRenderer r = this.rendererFor(cp);
            int runStart = i;
            i += Character.charCount(cp);

            while (i < n) {
                int next = text.codePointAt(i);
                if (this.rendererFor(next) != r) {
                    break;
                }

                i += Character.charCount(next);
            }

            String run = text.substring(runStart, i);
            r.render(run, x, y, color, shadow);
            x += r.getWidth(run, run.length(), false);
        }

        return x - startX;
    }

    @Override
    public boolean isBuilding() {
        return this.primary.isBuilding() || this.fallback.isBuilding();
    }

    @Override
    public void end() {
        if (this.primary.isBuilding()) {
            forceEnd(this.primary);
        }

        if (this.fallback.isBuilding()) {
            forceEnd(this.fallback);
        }
    }

    private static final class IsolatedVanillaRenderer implements TextRenderer {
        private final ByteBufferBuilder buffer = new ByteBufferBuilder(2048);
        private final BufferSource immediate = MultiBufferSource.immediate(this.buffer);
        private final Matrix4f glyphMatrix = new Matrix4f();
        private double scale = 2.0;
        private boolean building;
        private double alpha = 1.0;

        @Override
        public void setAlpha(double a) {
            this.alpha = a;
        }

        @Override
        public void begin(double scale, boolean scaleOnly, boolean big) {
            if (this.building) {
                this.end();
            }

            this.scale = scale * 2.0;
            this.building = true;
        }

        @Override
        public double getWidth(String text, int length, boolean shadow) {
            if (text.isEmpty()) {
                return 0.0;
            }

            if (length != text.length()) {
                text = text.substring(0, length);
            }

            return (MeteorClient.mc.font.width(text) + (shadow ? 1 : 0)) * this.scale;
        }

        @Override
        public double getHeight(boolean shadow) {
            return (9 + (shadow ? 1 : 0)) * this.scale;
        }

        @Override
        public double render(String text, double x, double y, Color color, boolean shadow) {
            boolean wasBuilding = this.building;
            if (!wasBuilding) {
                this.begin();
            }

            x = Math.round(x);
            y = Math.round(y);
            x += 0.5 * this.scale;
            y += 0.5 * this.scale;
            int preA = color.a;
            color.a = (int)(color.a / 255.0 * this.alpha * 255.0);
            this.glyphMatrix.scaling((float)this.scale, (float)this.scale, 1.0F);
            MeteorClient.mc
                .font
                .drawInBatch(
                    text,
                    (float)(x / this.scale),
                    (float)(y / this.scale),
                    color.getPacked(),
                    shadow,
                    this.glyphMatrix,
                    this.immediate,
                    DisplayMode.NORMAL,
                    0,
                    15728880
                );
            double x2 = x / this.scale + MeteorClient.mc.font.width(text);
            color.a = preA;
            if (!wasBuilding) {
                this.end();
            }

            return (x2 - 1.0) * this.scale;
        }

        @Override
        public boolean isBuilding() {
            return this.building;
        }

        @Override
        public void end() {
            if (this.building) {
                try {
                    this.immediate.endBatch();
                } finally {
                    this.scale = 2.0;
                    this.building = false;
                }
            }
        }
    }
}
