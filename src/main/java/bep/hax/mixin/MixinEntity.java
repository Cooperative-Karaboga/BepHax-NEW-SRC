package bep.hax.mixin;

import bep.hax.modules.ElytraBounce;
import bep.hax.modules.YCam;
import bep.hax.util.RotationUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Shadow
    public abstract boolean hasPose(Pose var1);

    @Shadow
    public abstract Component getName();

    @Shadow
    public abstract Level level();

    @Shadow
    public abstract InteractionResult interact(Player var1, InteractionHand var2);

    @Shadow
    protected abstract void checkFallDamage(double var1, boolean var3, BlockState var4, BlockPos var5);

    @Shadow
    protected abstract boolean vibrationAndSoundEffectsFromBlock(BlockPos var1, BlockState var2, boolean var3, boolean var4, Vec3 var5);

    @Shadow
    public abstract float maxUpStep();

    @Shadow
    public abstract boolean onGround();

    @Shadow
    public abstract AABB getBoundingBox();

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object)this == MeteorClient.mc.player) {
            FreeLook freeLook = Modules.get().get(FreeLook.class);
            if (freeLook == null || !freeLook.isActive()) {
                YCam yCam = Modules.get().get(YCam.class);
                if (yCam != null && yCam.isActive()) {
                    yCam.cameraYaw = yCam.cameraYaw + (float)(cursorDeltaX / yCam.sensitivity.get());
                    yCam.cameraPitch = yCam.cameraPitch + (float)(cursorDeltaY / yCam.sensitivity.get());
                    yCam.cameraPitch = Mth.clamp(yCam.cameraPitch, -90.0F, 90.0F);
                    ci.cancel();
                } else {
                    ElytraBounce elytraBounce = Modules.get().get(ElytraBounce.class);
                    if (elytraBounce != null && elytraBounce.isFreePitchEnabled()) {
                        elytraBounce.cameraPitch += (float)(cursorDeltaY * 0.15);
                        elytraBounce.cameraPitch = Mth.clamp(elytraBounce.cameraPitch, -90.0F, 90.0F);
                        MeteorClient.mc.player.setYRot(MeteorClient.mc.player.getYRot() + (float)(cursorDeltaX * 0.15));
                        ci.cancel();
                    }
                }
            }
        }
    }

    @WrapOperation(method = "moveRelative", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
    private float bephax$velocityYaw(Entity entity, Operation<Float> original) {
        if ((Object)this != MeteorClient.mc.player) {
            return original.call(entity);
        }

        Float yaw = RotationUtils.getInstance().getMoveYaw();
        return yaw == null ? original.call(entity) : yaw;
    }

    @ModifyVariable(method = "moveRelative", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float bephax$matchDeclaredInputMagnitude(float speed) {
        return (Object)this != MeteorClient.mc.player ? speed : speed * RotationUtils.getInstance().getMoveSpeedScale();
    }

    @WrapOperation(method = "getLookAngle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
    private float bephax$rotationVectorYaw(Entity entity, Operation<Float> original) {
        if ((Object)this != MeteorClient.mc.player) {
            return original.call(entity);
        }

        Float yaw = RotationUtils.getInstance().getMovementYaw();
        return yaw == null ? original.call(entity) : yaw;
    }

    @WrapOperation(method = "getLookAngle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getXRot()F"))
    private float bephax$rotationVectorPitch(Entity entity, Operation<Float> original) {
        if ((Object)this != MeteorClient.mc.player) {
            return original.call(entity);
        }

        Float pitch = RotationUtils.getInstance().getMovementPitch();
        return pitch == null ? original.call(entity) : pitch;
    }

    @WrapOperation(method = "getViewVector", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float bephax$rotationVecYaw(Entity entity, float tickDelta, Operation<Float> original) {
        if ((Object)this != MeteorClient.mc.player) {
            return original.call(entity, tickDelta);
        }

        Float yaw = RotationUtils.getInstance().getMovementYaw();
        return yaw == null ? original.call(entity, tickDelta) : yaw;
    }

    @WrapOperation(method = "getViewVector", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float bephax$rotationVecPitch(Entity entity, float tickDelta, Operation<Float> original) {
        if ((Object)this != MeteorClient.mc.player) {
            return original.call(entity, tickDelta);
        }

        Float pitch = RotationUtils.getInstance().getMovementPitch();
        return pitch == null ? original.call(entity, tickDelta) : pitch;
    }
}
