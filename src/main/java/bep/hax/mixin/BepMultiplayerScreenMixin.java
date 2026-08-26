package bep.hax.mixin;

import bep.hax.serverlist.ServerFolder;
import bep.hax.serverlist.ServerOrganizer;
import bep.hax.serverlist.gui.BepFolderEntry;
import bep.hax.serverlist.gui.BepServerListWidgetAccessor;
import bep.hax.serverlist.gui.EditFolderScreen;
import bep.hax.serverlist.gui.ServerOrganizerScreen;
import java.util.List;
import meteordevelopment.meteorclient.gui.GuiThemes;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.Entry;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.OnlineServerEntry;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class BepMultiplayerScreenMixin extends Screen {
    @Shadow
    protected ServerSelectionList serverSelectionList;
    @Unique
    private Button bep$newFolderButton;
    @Unique
    private Button bep$moveIntoFolderButton;
    @Unique
    private Button bep$organizeButton;

    protected BepMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void bep$onInit(CallbackInfo ci) {
        this.bep$createButtons();
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void bep$onRefreshWidgetPositions(CallbackInfo ci) {
        this.bep$updateButtonPositions();
    }

    @Unique
    private void bep$createButtons() {
        this.bep$newFolderButton = this.addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            if (this.minecraft != null) {
                String parentId = null;
                if (this.serverSelectionList instanceof BepServerListWidgetAccessor accessor) {
                    ServerFolder currentFolder = accessor.bep$getCurrentFolder();
                    if (currentFolder != null) {
                        parentId = currentFolder.getId();
                    }
                }

                String finalParentId = parentId;
                this.minecraft.setScreen(new EditFolderScreen(GuiThemes.get(), null, finalParentId, () -> {
                    if (this.serverSelectionList instanceof BepServerListWidgetAccessor accessor) {
                        accessor.bep$updateEntries();
                    }

                    this.minecraft.setScreen((JoinMultiplayerScreen)(Object)this);
                }));
            }
        }).size(20, 20).tooltip(Tooltip.create(Component.literal("Create new folder"))).build());
        this.bep$moveIntoFolderButton = this.addRenderableWidget(
            Button.builder(Component.literal("▶"), button -> this.bep$moveSelectedIntoFolder())
                .size(20, 20)
                .tooltip(Tooltip.create(Component.literal("Move server to folder (cycles through folders)")))
                .build()
        );
        this.bep$organizeButton = this.addRenderableWidget(Button.builder(Component.literal("\ud83d\udcc1"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ServerOrganizerScreen(GuiThemes.get()));
            }
        }).size(20, 20).tooltip(Tooltip.create(Component.literal("Open Server Organizer"))).build());
        this.bep$updateButtonPositions();
    }

    @Unique
    private void bep$updateButtonPositions() {
        int buttonY = this.height - 52;
        int leftX = 5;
        int spacing = 22;
        if (this.bep$newFolderButton != null) {
            this.bep$newFolderButton.setPosition(leftX, buttonY);
        }

        if (this.bep$moveIntoFolderButton != null) {
            this.bep$moveIntoFolderButton.setPosition(leftX + spacing, buttonY);
        }

        if (this.bep$organizeButton != null) {
            this.bep$organizeButton.setPosition(leftX + spacing * 2, buttonY);
        }
    }

    @Unique
    private void bep$moveSelectedIntoFolder() {
        if (this.serverSelectionList != null) {
            Entry selected = this.serverSelectionList.getSelected();
            if (selected != null) {
                if (!(selected instanceof BepFolderEntry)) {
                    if (selected instanceof OnlineServerEntry serverEntry) {
                        ServerOrganizer organizer = ServerOrganizer.get();
                        if (organizer != null) {
                            String serverAddress = serverEntry.getServerData().ip;
                            List<ServerFolder> allFolders = organizer.getFolders();
                            if (!allFolders.isEmpty()) {
                                String currentFolderId = organizer.getServerFolder(serverAddress);
                                int currentIndex = -1;

                                for (int i = 0; i < allFolders.size(); i++) {
                                    if (allFolders.get(i).getId().equals(currentFolderId)) {
                                        currentIndex = i;
                                        break;
                                    }
                                }

                                String newFolderId;
                                if (currentFolderId == null) {
                                    newFolderId = allFolders.get(0).getId();
                                } else if (currentIndex == allFolders.size() - 1) {
                                    newFolderId = null;
                                } else {
                                    newFolderId = allFolders.get(currentIndex + 1).getId();
                                }

                                organizer.setServerFolder(serverAddress, newFolderId);
                                if (this.serverSelectionList instanceof BepServerListWidgetAccessor accessor) {
                                    accessor.bep$updateEntries();
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
