package bep.hax.mixin.meteor;

import bep.hax.util.PeekNavigationConfig;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.screens.ContainerInventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContainerInventoryScreen.class, remap = false)
public abstract class ContainerInventoryScreenMixin extends Screen {
    @Unique
    @Nullable
    private Screen bephax$parentScreen = null;

    protected ContainerInventoryScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(ItemStack containerItem, CallbackInfo ci) {
        this.bephax$parentScreen = MeteorClient.mc.screen;
    }

    @Override
    public void onClose() {
        if (PeekNavigationConfig.isEnabled()) {
            Screen parent = PeekNavigationConfig.popScreen();
            if (parent != null) {
                PeekNavigationConfig.setNavigatingBack(true);
                MeteorClient.mc.setScreen(parent);
                PeekNavigationConfig.setNavigatingBack(false);
                return;
            }
        }

        PeekNavigationConfig.clearStack();
        super.onClose();
    }
}
