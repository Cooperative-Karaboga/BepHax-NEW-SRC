package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.MsgUtil;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BlockPosSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

public class AngleCalculator extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<BlockPos> to = this.sgGeneral
        .add(new Builder().name("to").description("Target coordinate to travel straight towards.").defaultValue(new BlockPos(0, 64, 1000)).build());
    private final Setting<Boolean> snapPitch = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("level-pitch")
                .description("Also set your pitch to 0 (level) when applying the angle.")
                .defaultValue(true)
                .build()
        );
    private float targetYaw;

    public AngleCalculator() {
        super(Bep.CATEGORY, "angle-calculator", "Continuously locks your view towards a target coordinate to travel straight along it (highway trails).");
    }

    @Override
    public void onActivate() {
        if (this.mc.player == null || this.mc.level == null) {
            this.toggle();
        } else if (!this.computeYaw()) {
            MsgUtil.sendModuleMsg("You are already at the target point - nothing to aim at.", this.name);
            this.toggle();
        } else {
            double dx = this.to.get().getX() + 0.5 - this.mc.player.getX();
            double dz = this.to.get().getZ() + 0.5 - this.mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            MsgUtil.sendModuleMsg(String.format("Locking yaw to target: §a%.2f°§r  (distance §a%.1f§r blocks).", this.targetYaw, dist), this.name);
            this.applyView();
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.computeYaw()) {
                this.applyView();
            }
        }
    }

    private boolean computeYaw() {
        if (this.mc.player == null) {
            return false;
        }

        double dx = this.to.get().getX() + 0.5 - this.mc.player.getX();
        double dz = this.to.get().getZ() + 0.5 - this.mc.player.getZ();
        if (dx == 0.0 && dz == 0.0) {
            return false;
        }

        this.targetYaw = Mth.wrapDegrees((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        return true;
    }

    private void applyView() {
        if (this.mc.player != null) {
            this.mc.player.setYRot(this.targetYaw);
            if (this.snapPitch.get()) {
                this.mc.player.setXRot(0.0F);
            }

            if (this.mc.player.isPassenger()) {
                this.mc.player.getVehicle().setYRot(this.targetYaw);
                if (this.snapPitch.get()) {
                    this.mc.player.getVehicle().setXRot(0.0F);
                }
            }
        }
    }
}
