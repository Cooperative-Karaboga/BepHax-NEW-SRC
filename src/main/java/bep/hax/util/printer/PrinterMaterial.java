package bep.hax.util.printer;

import net.minecraft.world.item.ItemStack;

public record PrinterMaterial(ItemStack stack, int missing) {
}
