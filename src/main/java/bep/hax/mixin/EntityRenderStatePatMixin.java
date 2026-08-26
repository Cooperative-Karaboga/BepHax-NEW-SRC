package bep.hax.mixin;

import bep.hax.accessor.PatEntityIdAccess;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStatePatMixin implements PatEntityIdAccess {
    @Unique
    private int bephax$patEntityId = -1;

    @Override
    public int bephax$getPatEntityId() {
        return this.bephax$patEntityId;
    }

    @Override
    public void bephax$setPatEntityId(int id) {
        this.bephax$patEntityId = id;
    }
}
