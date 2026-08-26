package bep.hax.mixin;

import bep.hax.capes.CapeManager;
import bep.hax.config.BepConfig;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset.Texture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void onGetSkinTextures(CallbackInfoReturnable<PlayerSkin> cir) {
        if (!BepConfig.disableBepHaxCapes.get()) {
            try {
                AbstractClientPlayer self = (AbstractClientPlayer)(Object)this;
                Identifier bepCape = CapeManager.getInstance().getCapeTexture(self.getUUID());
                if (bepCape != null) {
                    PlayerSkin original = cir.getReturnValue();
                    Texture capeAsset = new AbstractClientPlayerEntityMixin.BepCapeTextureAsset(bepCape);
                    PlayerSkin modified = new PlayerSkin(original.body(), capeAsset, capeAsset, original.model(), original.secure());
                    cir.setReturnValue(modified);
                }
            } catch (Exception var7) {
            }
        }
    }

    private record BepCapeTextureAsset(Identifier texturePath) implements Texture {
        @Override
        public Identifier id() {
            return this.texturePath;
        }

        @Override
        public Identifier texturePath() {
            return this.texturePath;
        }
    }
}
