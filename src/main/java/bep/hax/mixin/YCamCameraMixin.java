package bep.hax.mixin;

import bep.hax.modules.ControlFly;
import bep.hax.modules.ElytraBounce;
import bep.hax.modules.YCam;
import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = Camera.class, priority = 1001)
public class YCamCameraMixin {
    @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void modifyCameraPosition(Args args, @Local(argsOnly = true) Entity focusedEntity, @Local(argsOnly = true) float tickDelta) {
        YCam yCam = Modules.get().get(YCam.class);
        if (yCam != null && yCam.isActive() && focusedEntity != null) {
            double x = Mth.lerp(tickDelta, focusedEntity.xOld, focusedEntity.getX());
            double z = Mth.lerp(tickDelta, focusedEntity.zOld, focusedEntity.getZ());
            double y = yCam.yLevel.get();
            args.set(0, x);
            args.set(1, y);
            args.set(2, z);
        } else {
            ControlFly controlFly = ControlFly.INSTANCE;
            if (controlFly != null && controlFly.isIdleHovering() && focusedEntity != null) {
                args.set(0, controlFly.getSmoothCamX(tickDelta));
                args.set(2, controlFly.getSmoothCamZ(tickDelta));
            }
        }
    }

    @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void modifyCameraRotation(Args args, @Local(argsOnly = true) Entity focusedEntity) {
        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook == null || !freeLook.isActive()) {
            YCam yCam = Modules.get().get(YCam.class);
            if (yCam != null && yCam.isActive()) {
                args.set(0, yCam.cameraYaw);
                args.set(1, yCam.cameraPitch);
            } else {
                ElytraBounce elytraBounce = Modules.get().get(ElytraBounce.class);
                if (elytraBounce != null && elytraBounce.isFreePitchEnabled() && focusedEntity != null) {
                    args.set(1, elytraBounce.cameraPitch);
                }
            }
        }
    }
}
