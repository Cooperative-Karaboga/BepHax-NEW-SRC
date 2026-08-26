package bep.hax.mixin;

import bep.hax.serverlist.ServerMetadata;
import bep.hax.serverlist.ServerOrganizer;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ManageServerScreen.class)
public abstract class BepAddServerScreenMixin extends Screen {
    @Shadow
    @Final
    private ServerData serverData;
    @Shadow
    private EditBox ipEdit;
    @Unique
    private EditBox bep$aliasField;
    @Unique
    private String bep$originalAddress;

    protected BepAddServerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void bep$onInit(CallbackInfo ci) {
        this.bep$originalAddress = this.serverData.ip;
        int aliasY = 156;
        this.bep$aliasField = new EditBox(this.font, this.width / 2 - 100, aliasY, 200, 20, Component.literal("Alias"));
        this.bep$aliasField.setMaxLength(128);
        this.bep$aliasField.setHint(Component.literal("Display alias (optional)").withStyle(s -> s.withColor(7368816)));
        if (this.bep$originalAddress != null && !this.bep$originalAddress.isEmpty()) {
            ServerOrganizer organizer = ServerOrganizer.get();
            if (organizer != null) {
                ServerMetadata meta = organizer.getMetadata(this.bep$originalAddress);
                if (meta != null && meta.hasDisplayName()) {
                    this.bep$aliasField.setValue(meta.getDisplayName());
                }
            }
        }

        this.addRenderableWidget(this.bep$aliasField);
    }

    @Inject(method = "onAdd", at = @At("HEAD"))
    private void bep$onAddAndClose(CallbackInfo ci) {
        String alias = this.bep$aliasField != null ? this.bep$aliasField.getValue().trim() : "";
        String serverAddress = this.ipEdit.getValue().trim();
        if (!serverAddress.isEmpty()) {
            ServerOrganizer organizer = ServerOrganizer.get();
            if (organizer != null) {
                if (this.bep$originalAddress != null && !this.bep$originalAddress.isEmpty() && !this.bep$originalAddress.equalsIgnoreCase(serverAddress)) {
                    String oldFolderId = organizer.getServerFolder(this.bep$originalAddress);
                    if (oldFolderId != null) {
                        organizer.setServerFolder(serverAddress, oldFolderId);
                        organizer.setServerFolder(this.bep$originalAddress, null);
                    }
                }

                if (!alias.isEmpty()) {
                    organizer.setDisplayName(serverAddress, alias);
                } else {
                    organizer.setDisplayName(serverAddress, null);
                }
            }
        }
    }
}
