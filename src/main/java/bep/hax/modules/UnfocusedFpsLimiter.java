package bep.hax.modules;

import bep.hax.Bep;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class UnfocusedFpsLimiter extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Integer> unfocusedFps = this.sgGeneral
        .add(
            new Builder()
                .name("unfocused-fps")
                .description("The FPS limit when the game window is not focused.")
                .defaultValue(30)
                .min(10)
                .max(260)
                .sliderRange(10, 60)
                .build()
        );
    private int originalFps = -1;
    private boolean wasFocused = true;
    private boolean hasStoredOriginal = false;

    public UnfocusedFpsLimiter() {
        super(Bep.CATEGORY, "unfocused-fps", "Limits the FPS when the game is unfocused.");
    }

    @Override
    public void onActivate() {
        if (this.mc.options != null) {
            this.originalFps = this.mc.options.framerateLimit().get();
            this.hasStoredOriginal = true;
            this.wasFocused = this.mc.isWindowActive();
            if (!this.wasFocused) {
                this.setFpsLimit(this.unfocusedFps.get());
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (this.hasStoredOriginal && this.mc.options != null) {
            this.setFpsLimit(this.originalFps);
        }

        this.hasStoredOriginal = false;
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.options != null && this.hasStoredOriginal) {
            boolean focused = this.mc.isWindowActive();
            if (focused != this.wasFocused) {
                if (focused) {
                    this.setFpsLimit(this.originalFps);
                } else {
                    if (this.wasFocused) {
                        this.originalFps = this.mc.options.framerateLimit().get();
                    }

                    this.setFpsLimit(this.unfocusedFps.get());
                }

                this.wasFocused = focused;
            }
        }
    }

    private void setFpsLimit(int fps) {
        int validFps = fps;
        if (validFps < 10) {
            validFps = 10;
        }

        if (validFps > 260) {
            validFps = 260;
        }

        if (validFps <= 120) {
            validFps = Math.round(validFps / 10.0F) * 10;
        }

        try {
            this.mc.options.framerateLimit().set(validFps);
        } catch (Exception var4) {
        }
    }
}
