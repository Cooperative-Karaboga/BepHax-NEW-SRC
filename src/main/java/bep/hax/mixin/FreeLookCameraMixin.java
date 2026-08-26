package bep.hax.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 1001)
public class FreeLookCameraMixin {
    @Unique
    private static final float BEPHAX_ZOOM_SECONDS = 0.28F;
    @Unique
    private float bephax$zoom;
    @Unique
    private long bephax$lastNanos;
    @Unique
    private boolean bephax$wasActive;

    @Inject(method = "setup", at = @At("HEAD"))
    private void bephax$advanceFreeLookZoom(
        Level area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci
    ) {
        boolean active = this.bephax$freeLookThirdPerson();
        long now = System.nanoTime();
        float dt = this.bephax$lastNanos == 0L ? 0.0F : (float)(now - this.bephax$lastNanos) / 1.0E9F;
        this.bephax$lastNanos = now;
        dt = Mth.clamp(dt, 0.0F, 0.1F);
        if (active && !this.bephax$wasActive) {
            this.bephax$zoom = 0.0F;
        }

        this.bephax$wasActive = active;
        float target = active ? 1.0F : 0.0F;
        float step = dt / 0.28F;
        if (this.bephax$zoom < target) {
            this.bephax$zoom = Math.min(target, this.bephax$zoom + step);
        } else if (this.bephax$zoom > target) {
            this.bephax$zoom = Math.max(target, this.bephax$zoom - step);
        }
    }

    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;move(FFF)V", ordinal = 0), index = 0)
    private float bephax$animateBackDistance(float backDistance) {
        return this.bephax$freeLookThirdPerson() ? backDistance * this.bephax$easeOutCubic(this.bephax$zoom) : backDistance;
    }

    @Unique
    private boolean bephax$freeLookThirdPerson() {
        FreeLook freeLook = Modules.get().get(FreeLook.class);
        return freeLook != null && freeLook.isActive() && Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_BACK;
    }

    @Unique
    private float bephax$easeOutCubic(float t) {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }
}
