package bep.hax.mixin;

import bep.hax.serverlist.ServerOrganizer;
import bep.hax.serverlist.gui.BepServerListWidgetAccessor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.OnlineServerEntry;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OnlineServerEntry.class)
public abstract class BepServerEntryMixin {
    @Shadow
    @Final
    private ServerData serverData;
    @Shadow
    @Final
    ServerSelectionList field_19117;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void bep$onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (input.hasShiftDown()) {
            int key = input.key();
            if (key == 265 || key == 264) {
                if (this.field_19117 instanceof BepServerListWidgetAccessor accessor) {
                    OnlineServerEntry var8 = (OnlineServerEntry)(Object)this;
                    int index = accessor.bep$getCurrentEntries().indexOf(var8);
                    if (index != -1) {
                        int size = accessor.bep$getCurrentEntries().size();
                        if (key == 265 && index > 0) {
                            accessor.bep$swapEntries(index, index - 1);
                            cir.setReturnValue(true);
                        } else if (key == 264 && index < size - 1) {
                            accessor.bep$swapEntries(index, index + 1);
                            cir.setReturnValue(true);
                        }
                    }
                }
            }
        }
    }

    @ModifyArg(
        method = "renderContent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
        index = 1,
        require = 0
    )
    private String bep$modifyServerNameString(String originalText) {
        return this.bep$replaceServerName(originalText);
    }

    @ModifyArg(
        method = "renderContent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"),
        index = 1,
        require = 0
    )
    private Component bep$modifyServerNameText(Component originalText) {
        String originalString = originalText.getString();
        String replaced = this.bep$replaceServerName(originalString);
        return !replaced.equals(originalString) ? Component.literal(replaced) : originalText;
    }

    @Unique
    private String bep$replaceServerName(String originalText) {
        if (originalText == null) {
            return originalText;
        } else {
            ServerOrganizer organizer = ServerOrganizer.get();
            if (organizer == null) {
                return originalText;
            } else {
                String alias = organizer.getDisplayName(this.serverData);
                if (alias == null || alias.isEmpty()) {
                    return originalText;
                } else if (originalText.equals(this.serverData.name)) {
                    return alias;
                } else {
                    return originalText.contains(this.serverData.name) ? originalText.replace(this.serverData.name, alias) : originalText;
                }
            }
        }
    }
}
