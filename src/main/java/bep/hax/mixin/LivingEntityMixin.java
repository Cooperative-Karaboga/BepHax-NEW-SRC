package bep.hax.mixin;

import bep.hax.modules.ElytraBounce;
import bep.hax.modules.NoJumpDelay;
import bep.hax.modules.RocketBoost;
import bep.hax.util.RotationUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    private int noJumpDelay;
    private Module noJumpDelayMod;
    private ElytraBounce efly;
    private RocketBoost boostModule;

    @Shadow
    public abstract Brain<?> getBrain();

    private RocketBoost getBoost() {
        if (this.boostModule == null) {
            this.boostModule = Modules.get().get(RocketBoost.class);
        }

        return this.boostModule;
    }

    private Module getNoJumpDelay() {
        if (this.noJumpDelayMod == null) {
            this.noJumpDelayMod = Modules.get().get(NoJumpDelay.class);
        }

        return this.noJumpDelayMod;
    }

    private ElytraBounce getEfly() {
        if (this.efly == null) {
            this.efly = Modules.get().get(ElytraBounce.class);
        }

        return this.efly;
    }

    @Inject(at = @At("HEAD"), method = "Lnet/minecraft/world/entity/LivingEntity;aiStep")
    private void tickMovement(CallbackInfo ci) {
        ElytraBounce eflyModule = this.getEfly();
        Module noJumpDelayModule = this.getNoJumpDelay();
        if (MeteorClient.mc.player != null
                && MeteorClient.mc.player.getBrain().equals(this.getBrain())
                && eflyModule != null
                && eflyModule.enabled()
            || noJumpDelayModule != null && noJumpDelayModule.isActive()) {
            this.noJumpDelay = 0;
        }
    }

    @WrapOperation(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float bephax$wrapGlidingPitch(LivingEntity entity, Operation<Float> original) {
        Float pitch = RotationUtils.getInstance().getMovementPitch();
        return entity == MeteorClient.mc.player && pitch != null ? pitch : original.call(entity);
    }

    @WrapOperation(
        method = "travelFallFlying",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;updateFallFlyingMovement(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 bephax$rocketBoost(LivingEntity entity, Vec3 oldVelocity, Operation<Vec3> original) {
        RocketBoost boost = this.getBoost();
        if (boost != null && boost.isActive() && entity == MeteorClient.mc.player) {
            Vec3 vanilla = original.call(entity, oldVelocity);
            Vec3 boosted = boost.glideVelocity(oldVelocity, vanilla);
            return boosted != null ? boosted : vanilla;
        } else {
            return original.call(entity, oldVelocity);
        }
    }

    @WrapOperation(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float bephax$wrapSprintJumpYaw(LivingEntity entity, Operation<Float> original) {
        RotationUtils rm = RotationUtils.getInstance();
        return entity == MeteorClient.mc.player && rm.isRotating() ? rm.getRotationYaw() : original.call(entity);
    }
}
