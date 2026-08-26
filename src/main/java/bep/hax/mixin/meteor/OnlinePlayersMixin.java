package bep.hax.mixin.meteor;

import bep.hax.config.BepConfig;
import meteordevelopment.meteorclient.utils.network.OnlinePlayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OnlinePlayers.class, remap = false)
public class OnlinePlayersMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private static void removeMeteorTelemetry(CallbackInfo ci) {
        if (BepConfig.disableMeteorClientTelemetry.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "leave", at = @At("HEAD"), cancellable = true)
    private static void removeMeteorTelemetryOnLeave(CallbackInfo ci) {
        if (BepConfig.disableMeteorClientTelemetry.get()) {
            ci.cancel();
        }
    }
}
