package bep.hax.mixin;

import bep.hax.modules.BepMine;
import bep.hax.util.RotationUtils;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Shadow
    private float destroyProgress;

    @ModifyExpressionValue(method = "method_41929", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float bephax$useItemYaw(float original) {
        RotationUtils rotations = RotationUtils.getInstance();
        return !rotations.isRotating() && !rotations.isWireFresh() ? original : rotations.getSentYaw();
    }

    @ModifyExpressionValue(method = "method_41929", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getXRot()F"))
    private float bephax$useItemPitch(float original) {
        RotationUtils rotations = RotationUtils.getInstance();
        return !rotations.isRotating() && !rotations.isWireFresh() ? original : rotations.getSentPitch();
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void onUpdateBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        if (bepMine != null
            && bepMine.isActive()
            && bepMine.getModeConfig().get() == BepMine.SpeedmineMode.DAMAGE
            && this.destroyProgress >= bepMine.getEffectiveThreshold()) {
            this.destroyProgress = 1.0F;
            cir.setReturnValue(true);
        }
    }
}
