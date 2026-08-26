package bep.hax.mixin;

import bep.hax.modules.ItemSearchBar;
import bep.hax.modules.LoreLocator;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class DrawContextMixin {
    @Unique
    private static LoreLocator bephax$loreLocator;
    @Unique
    private static ItemSearchBar bephax$itemSearchBar;

    @Shadow
    public abstract void fill(int var1, int var2, int var3, int var4, int var5);

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
    private void bephax$highlightItem(LivingEntity entity, Level world, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (stack != null && !stack.isEmpty()) {
            int color = this.bephax$highlightColor(stack);
            if (color != 0) {
                this.fill(x - 1, y - 1, x + 17, y + 17, color);
            }
        }
    }

    @Unique
    private int bephax$highlightColor(ItemStack stack) {
        Modules modules = Modules.get();
        if (modules == null) {
            return 0;
        }

        LoreLocator ll = bephax$loreLocator;
        if (ll == null) {
            ll = bephax$loreLocator = modules.get(LoreLocator.class);
        }

        if (ll != null && ll.isActive() && ll.shouldHighlightSlot(stack)) {
            return ll.color.get().getPacked();
        }

        ItemSearchBar isb = bephax$itemSearchBar;
        if (isb == null) {
            isb = bephax$itemSearchBar = modules.get(ItemSearchBar.class);
        }

        return isb != null && isb.isActive() && isb.shouldHighlightSlot(stack) ? isb.highlightColor.get().getPacked() : 0;
    }
}
