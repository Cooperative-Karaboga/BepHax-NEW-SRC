package bep.hax.mixin;

import bep.hax.config.BepConfig;
import bep.hax.util.Utils;
import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public class GameMenuScreenMixin extends Screen {
    protected GameMenuScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V"))
    private void addIllegalDisconnectButton(CallbackInfo ci, @Local RowHelper adder) {
        if (!MeteorClient.mc.isLocalServer() && BepConfig.illegalDisconnectButtonSetting.get()) {
            adder.addChild(Button.builder(Component.literal("Illegal Disconnect"), button -> {
                button.active = false;
                Utils.illegalDisconnect(false, BepConfig.illegalDisconnectMethodSetting.get());
            }).width(204).build(), 2);
        }
    }
}
