package bep.hax.mixin.accessor;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
    @Accessor("attachedToEntity")
    LivingEntity getAttachedToEntity();

    @Accessor("life")
    int getLife();

    @Accessor("lifetime")
    int getLifetime();
}
