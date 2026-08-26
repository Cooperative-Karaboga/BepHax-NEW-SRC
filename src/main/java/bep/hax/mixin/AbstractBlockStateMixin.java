package bep.hax.mixin;

import bep.hax.modules.BepMine;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockStateBase.class)
public abstract class AbstractBlockStateMixin {
    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true)
    private void onCalcBlockBreakingDelta(Player player, BlockGetter world, BlockPos pos, CallbackInfoReturnable<Float> info) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        if (bepMine != null && bepMine.isActive() && bepMine.getModeConfig().get() == BepMine.SpeedmineMode.DAMAGE) {
            float originalDelta = info.getReturnValueF();
            float speedMultiplier = 1.0F / bepMine.getEffectiveThreshold();
            info.setReturnValue(originalDelta * speedMultiplier);
        }
    }
}
