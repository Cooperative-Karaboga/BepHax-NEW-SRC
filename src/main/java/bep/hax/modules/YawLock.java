package bep.hax.modules;

import bep.hax.Bep;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class YawLock extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> bypass2b2t = this.sgGeneral
        .add(new Builder().name("2b2t-bypass").description("Adds tiny yaw variations to bypass anticheat.").defaultValue(true).build());
    private final Setting<Double> jitterAmount = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("jitter-amount")
                .description("Maximum jitter in degrees.")
                .defaultValue(0.1)
                .min(0.01)
                .max(0.5)
                .visible(this.bypass2b2t::get)
                .build()
        );
    private final Setting<Integer> jitterInterval = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("jitter-interval")
                .description("Ticks between jitter changes.")
                .defaultValue(10)
                .min(1)
                .max(40)
                .visible(this.bypass2b2t::get)
                .build()
        );
    private float lockedYaw;
    private float jitter = 0.0F;
    private int tickCounter = 0;

    public YawLock() {
        super(Bep.HUNT_CATEGORY, "yaw-lock", "Locks your yaw to the closest 45-degree increment.");
    }

    @Override
    public void onActivate() {
        float currentYaw = this.mc.player.getYRot();
        float normalizedYaw = (currentYaw % 360.0F + 360.0F) % 360.0F;
        int closestMultiple = Math.round(normalizedYaw / 45.0F);
        this.lockedYaw = closestMultiple * 45 % 360;
        if (this.lockedYaw > 180.0F) {
            this.lockedYaw -= 360.0F;
        }

        this.tickCounter = 0;
        this.jitter = 0.0F;
    }

    @EventHandler
    private void onTick(Pre event) {
        float yawToSet = this.lockedYaw;
        if (this.bypass2b2t.get()) {
            this.tickCounter++;
            if (this.tickCounter >= this.jitterInterval.get()) {
                this.tickCounter = 0;
                this.jitter = (float)((Math.random() * 2.0 - 1.0) * this.jitterAmount.get());
            }

            yawToSet = this.lockedYaw + this.jitter;
        }

        this.mc.player.setYRot(yawToSet);
        if (this.mc.player.isPassenger()) {
            this.mc.player.getVehicle().setYRot(yawToSet);
        }
    }
}
