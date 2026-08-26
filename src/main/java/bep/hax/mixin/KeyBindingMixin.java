package bep.hax.mixin;

import bep.hax.modules.ElytraBounce;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class KeyBindingMixin {
    @Final
    @Shadow
    private String name;
    @Unique
    ElytraBounce efly = null;

    @Inject(at = @At("RETURN"), method = "isDown", cancellable = true)
    public void isPressed(CallbackInfoReturnable<Boolean> cir) {
        if (Modules.get() != null) {
            this.efly = this.efly == null ? Modules.get().get(ElytraBounce.class) : this.efly;
            if (this.efly != null && this.efly.isActive() && this.efly.enabled()) {
                if (this.name.equals("key.forward")) {
                    cir.setReturnValue(true);
                } else if (this.name.equals("key.jump") && this.efly.shouldAutoJump()) {
                    cir.setReturnValue(this.efly.isJumpKeyForcedDown());
                }
            }
        }
    }
}
