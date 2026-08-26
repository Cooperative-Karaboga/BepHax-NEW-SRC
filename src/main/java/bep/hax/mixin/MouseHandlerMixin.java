package bep.hax.mixin;

import bep.hax.modules.WheelPicker;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void bephax$freezeCameraForWheel(CallbackInfo ci) {
        if (WheelPicker.isWheelOpen()) {
            ci.cancel();
        }
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void bephax$swallowClicksForWheel(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (action == 1 && WheelPicker.isWheelOpen()) {
            ci.cancel();
        }
    }
}
