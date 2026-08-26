package bep.hax.modules.livemessage.gui;

import bep.hax.modules.livemessage.util.LivemessageUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileReader;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;

public class GuiUtil {
    public static int hslColor(float h, float s, float l) {
        float r;
        float g;
        float b;
        if (s == 0.0F) {
            b = l;
            g = l;
            r = l;
        } else {
            float q = l < 0.5 ? l * (1.0F + s) : l + s - l * s;
            float p = 2.0F * l - q;
            r = hue2rgb(p, q, h + 0.33333334F);
            g = hue2rgb(p, q, h);
            b = hue2rgb(p, q, h - 0.33333334F);
        }

        return getRGB(Math.round(r * 255.0F), Math.round(g * 255.0F), Math.round(b * 255.0F));
    }

    private static float hue2rgb(float p, float q, float h) {
        if (h < 0.0F) {
            h++;
        }

        if (h > 1.0F) {
            h--;
        }

        if (6.0F * h < 1.0F) {
            return p + (q - p) * 6.0F * h;
        } else if (2.0F * h < 1.0F) {
            return q;
        } else {
            return 3.0F * h < 2.0F ? p + (q - p) * 6.0F * (0.6666667F - h) : p;
        }
    }

    public static int getRGB(int r, int g, int b) {
        return getRGBA(r, g, b, 255);
    }

    public static int getSingleRGB(int color) {
        return getRGBA(color, color, color, 255);
    }

    public static int getRGBA(int r, int g, int b, int a) {
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int getWindowColor(UUID uuid) {
        try {
            File settingsFile = LivemessageUtil.LIVEMESSAGE_FOLDER.resolve("mainwindow.json").toFile();
            if (settingsFile.exists()) {
                Gson gson = new Gson();
                JsonObject json = gson.fromJson(new FileReader(settingsFile), JsonObject.class);
                if (json.has("customColor")) {
                    int mainWindowColor = json.get("customColor").getAsInt();
                    if (mainWindowColor > 0) {
                        return mainWindowColor;
                    }
                }
            }
        } catch (Exception var5) {
        }

        switch (uuid.toString()) {
            case "342fc44b-1fd1-4272-a4c3-a98a2df98abc":
                return -13273633;
            case "cda8edd9-430e-4f6e-a45a-be4566f39c38":
                return -1342888;
            case "c499a96f-8a69-47c3-8525-0595b6b50f00":
                return -16711868;
            case "a997ff99-4515-4055-9117-39be3469c9d7":
                return -12739;
            case "4d03444c-2e0b-4b8e-a445-a2965c907676":
                return -12011795;
            default:
                float uuidvalue1 = Integer.parseInt(uuid.toString().substring(0, 2), 16) / 255.0F;
                float uuidvalue2 = Integer.parseInt(uuid.toString().substring(2, 4), 16) / 255.0F;
                return hslColor(uuidvalue1, 0.6F + uuidvalue2 * 0.15F, 0.5F) | 0xFF000000;
        }
    }

    public static double easeOutQuint(double t) {
        return 1.0 - Math.pow(1.0 - t, 5.0);
    }

    public static int fade(int argb) {
        float f = LivemessageGui.currentFadeAlpha;
        if (f >= 0.999F) {
            return argb;
        }

        int a = argb >>> 24 & 0xFF;
        a = Math.max(0, Math.min(255, Math.round(a * f)));
        return a << 24 | argb & 16777215;
    }

    public static void drawRect(GuiGraphics context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + h, fade(color));
    }

    public static class QuintAnimation {
        public long startTime = 0L;
        public float startValue = 0.0F;
        public float currentValue = 0.0F;
        public int animationLength = 1000;

        public float animate(float val) {
            if (val != this.currentValue) {
                this.startValue = this.getState();
                this.currentValue = val;
                this.startTime = System.currentTimeMillis();
            }

            return this.getState();
        }

        public float getState() {
            if (System.currentTimeMillis() - this.startTime > this.animationLength) {
                return this.currentValue;
            }

            double progress = (double)(System.currentTimeMillis() - this.startTime) / this.animationLength;
            return (float)(this.startValue - GuiUtil.easeOutQuint(progress) * (this.startValue - this.currentValue));
        }

        public QuintAnimation(int len, float initialValue) {
            this.animationLength = len;
            this.startValue = initialValue;
            this.currentValue = initialValue;
        }
    }
}
