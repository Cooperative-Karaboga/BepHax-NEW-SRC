package bep.hax.mixin.meteor;

import bep.hax.capes.CapeManager;
import bep.hax.config.BepConfig;
import meteordevelopment.meteorclient.utils.network.Capes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Capes.class, remap = false)
public class CapesMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private static void onGetCape(Player player, CallbackInfoReturnable<Identifier> cir) {
        if (!BepConfig.disableBepHaxCapes.get()) {
            try {
                Identifier bepCape = CapeManager.getInstance().getCapeTexture(player.getUUID());
                if (bepCape != null) {
                    cir.setReturnValue(bepCape);
                    return;
                }
            } catch (Exception var3) {
            }
        }

        if (BepConfig.disableMeteorCapes.get()) {
            cir.setReturnValue(null);
        }
    }
}
