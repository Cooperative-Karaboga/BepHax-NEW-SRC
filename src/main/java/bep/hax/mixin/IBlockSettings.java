package bep.hax.mixin;

import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Properties.class)
public interface IBlockSettings {
    @Accessor("replaceable")
    boolean replaceable();
}
