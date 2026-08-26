package bep.hax.mixin;

import bep.hax.modules.InvFix2b2t;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class Fix2b2tGhostItemsMixin<T extends AbstractContainerMenu> {
    @Shadow
    @Final
    protected T menu;

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    public void onMouseDragged(CallbackInfoReturnable<Boolean> cir) {
        InvFix2b2t module = Modules.get().get(InvFix2b2t.class);
        if (module != null && module.isActive() && module.fixGhostItems.get()) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || !client.player.isCreative()) {
                ItemStack cursorStack = this.menu.getCarried();
                if (cursorStack != null && !cursorStack.isEmpty()) {
                    if (!cursorStack.isStackable() || cursorStack.getItem() instanceof MapItem || cursorStack.getItem() instanceof BannerItem) {
                        cir.setReturnValue(true);
                    }
                }
            }
        }
    }
}
