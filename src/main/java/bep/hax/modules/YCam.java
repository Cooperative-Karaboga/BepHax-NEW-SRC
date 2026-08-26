package bep.hax.modules;

import bep.hax.Bep;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

public class YCam extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public final Setting<Double> yLevel = this.sgGeneral
        .add(
            new Builder()
                .name("y-level")
                .description("The Y level to lock the camera to.")
                .defaultValue(83.0)
                .range(-64.0, 320.0)
                .sliderRange(-64.0, 320.0)
                .build()
        );
    public final Setting<Boolean> togglePerspective = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("toggle-perspective")
                .description("Changes your perspective on toggle.")
                .defaultValue(true)
                .build()
        );
    public final Setting<Double> sensitivity = this.sgGeneral
        .add(new Builder().name("sensitivity").description("Camera rotation sensitivity.").defaultValue(8.0).min(0.0).sliderMax(10.0).build());
    public final Setting<Boolean> syncYaw = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("sync-yaw")
                .description("Sync camera yaw with player yaw, allowing you to steer while keeping pitch independent.")
                .defaultValue(true)
                .build()
        );
    public float cameraYaw;
    public float cameraPitch;
    private CameraType prePers;

    public YCam() {
        super(Bep.HUNT_CATEGORY, "y-cam", "Lock camera Y to a configurable level while following player X/Z. Look around freely with mouse.");
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null) {
            this.cameraYaw = this.mc.player.getYRot();
            this.cameraPitch = this.mc.player.getXRot();
            this.prePers = this.mc.options.getCameraType();
            if (this.prePers != CameraType.THIRD_PERSON_BACK && this.togglePerspective.get()) {
                this.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (this.mc.options.getCameraType() != this.prePers && this.togglePerspective.get()) {
            this.mc.options.setCameraType(this.prePers);
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.player != null) {
            if (this.syncYaw.get()) {
                this.cameraYaw = this.mc.player.getYRot();
            }

            this.cameraPitch = Mth.clamp(this.cameraPitch, -90.0F, 90.0F);
        }
    }
}
