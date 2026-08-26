package bep.hax.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Nametags;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererNametagMixin {
    @Shadow
    protected abstract boolean shouldShowName(Entity var1, double var2);

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z"))
    private boolean bephax$hideVanillaPlayerNametag(EntityRenderer self, Entity entity, double distanceSq) {
        boolean hasLabel = this.shouldShowName(entity, distanceSq);
        if (!hasLabel) {
            return false;
        }

        if (entity instanceof Player player) {
            Nametags nametags = Modules.get().get(Nametags.class);
            if (nametags != null && nametags.playerNametags() && (!nametags.excludeBots() || EntityUtils.getGameMode(player) != null)) {
                return false;
            }
        }

        return hasLabel;
    }
}
