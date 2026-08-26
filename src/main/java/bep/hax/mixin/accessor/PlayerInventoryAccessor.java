package bep.hax.mixin.accessor;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Inventory.class)
public interface PlayerInventoryAccessor {
    @Accessor("selected")
    int getSelectedSlot();

    @Accessor("selected")
    void setSelectedSlot(int var1);

    @Accessor("items")
    NonNullList<ItemStack> getMain();
}
