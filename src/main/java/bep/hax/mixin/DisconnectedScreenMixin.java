package bep.hax.mixin;

import bep.hax.events.DisconnectedScreenEvent;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoLog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin extends Screen {
    @Shadow
    @Final
    private LinearLayout layout;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("HEAD"))
    public void init(CallbackInfo ci) {
        MeteorClient.EVENT_BUS.post(new DisconnectedScreenEvent());
    }

    @Inject(method = "init()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/LinearLayout;arrangeElements()V"))
    private void bephax$addDisableAutoLogButton(CallbackInfo ci) {
        AutoLog autoLog = Modules.get().get(AutoLog.class);
        if (autoLog != null && autoLog.isActive()) {
            this.layout.addChild(Button.builder(Component.literal("Disable AutoLog"), button -> {
                if (autoLog.isActive()) {
                    autoLog.toggle();
                }

                button.active = false;
                button.setMessage(Component.literal("AutoLog Disabled").withStyle(ChatFormatting.GRAY));
            }).width(200).build());
        }
    }
}
