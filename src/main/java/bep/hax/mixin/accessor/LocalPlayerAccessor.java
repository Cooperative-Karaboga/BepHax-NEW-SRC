package bep.hax.mixin.accessor;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    @Accessor("xLast")
    double getXLast();

    @Accessor("yLast")
    double getYLast();

    @Accessor("zLast")
    double getZLast();

    @Accessor("positionReminder")
    int getPositionReminder();
}
