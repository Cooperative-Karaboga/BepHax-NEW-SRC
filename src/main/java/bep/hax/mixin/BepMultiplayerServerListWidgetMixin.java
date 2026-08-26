package bep.hax.mixin;

import bep.hax.serverlist.ServerFolder;
import bep.hax.serverlist.ServerMetadata;
import bep.hax.serverlist.ServerOrganizer;
import bep.hax.serverlist.SortableEntryData;
import bep.hax.serverlist.gui.BepFolderEntry;
import bep.hax.serverlist.gui.BepServerListWidgetAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.NetworkServerEntry;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.OnlineServerEntry;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerSelectionList.class)
public abstract class BepMultiplayerServerListWidgetMixin extends ObjectSelectionList<net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.Entry> implements BepServerListWidgetAccessor {
    @Shadow
    @Final
    private JoinMultiplayerScreen screen;
    @Shadow
    @Final
    private List<OnlineServerEntry> onlineServers;
    @Shadow
    @Final
    private net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.Entry lanHeader;
    @Shadow
    @Final
    private List<NetworkServerEntry> networkServers;
    @Unique
    @Nullable
    private ServerFolder bep$currentFolder = null;
    @Unique
    @NotNull
    private final List<net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.Entry> bep$currentEntries = new ArrayList<>();
    @Unique
    private final Map<String, BepFolderEntry> bep$folderEntries = new HashMap<>();

    @Shadow
    protected abstract void refreshEntries();

    public BepMultiplayerServerListWidgetMixin(Minecraft client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
    }

    @Nullable
    @Override
    public ServerFolder bep$getCurrentFolder() {
        return this.bep$currentFolder;
    }

    @NotNull
    @Override
    public List<net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.Entry> bep$getCurrentEntries() {
        return this.bep$currentEntries;
    }

    @Override
    public void bep$setCurrentFolder(@Nullable ServerFolder folder) {
        this.bep$currentFolder = folder;
        this.setSelected(null);
        this.refreshEntries();
    }

    @Override
    public void bep$updateEntries() {
        this.refreshEntries();
    }

    @Override
    public void bep$swapEntries(int i, int j) {
        if (i >= 0 && j >= 0 && i < this.bep$currentEntries.size() && j < this.bep$currentEntries.size()) {
            Collections.swap(this.bep$currentEntries, i, j);
            ServerOrganizer organizer = ServerOrganizer.get();
            if (organizer != null) {
                this.bep$persistCurrentOrder(organizer);
            }

            this.refreshEntries();
            if (j >= 0 && j < this.children().size()) {
                this.setSelected(this.children().get(j));
                this.scrollToEntry(this.getSelected());
            }
        }
    }

    @Inject(method = "refreshEntries", at = @At("HEAD"), cancellable = true)
    private void bep$onUpdateEntries(CallbackInfo ci) {
        ServerOrganizer organizer = ServerOrganizer.get();
        if (organizer != null) {
            this.clearEntries();
            this.bep$currentEntries.clear();
            this.bep$folderEntries.clear();
            Map<String, OnlineServerEntry> serverEntryMap = new HashMap<>();

            for (OnlineServerEntry entry : this.onlineServers) {
                ServerData info = entry.getServerData();
                if (info != null) {
                    serverEntryMap.put(info.ip.toLowerCase(), entry);
                }
            }

            if (this.bep$currentFolder == null) {
                this.bep$buildRootEntries(organizer, serverEntryMap);
            } else {
                this.bep$buildFolderEntries(organizer, serverEntryMap);
            }

            for (net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.Entry entry : this.bep$currentEntries) {
                this.addEntry(entry);
            }

            this.addEntry(this.lanHeader);

            for (NetworkServerEntry lanEntry : this.networkServers) {
                this.addEntry(lanEntry);
            }

            if (this.getSelected() == null) {
                this.setScrollAmount(0.0);
            }

            ci.cancel();
        }
    }

    @Unique
    private void bep$buildRootEntries(ServerOrganizer organizer, Map<String, OnlineServerEntry> serverEntryMap) {
        List<SortableEntryData> allEntries = new ArrayList<>();
        int vanillaIndex = 0;

        for (OnlineServerEntry serverEntry : this.onlineServers) {
            ServerData info = serverEntry.getServerData();
            if (info == null) {
                vanillaIndex++;
            } else {
                ServerMetadata meta = organizer.getMetadata(info.ip);
                if (meta == null || meta.getFolderId() == null) {
                    int sortOrder;
                    if (meta != null) {
                        sortOrder = meta.getSortOrder();
                    } else {
                        sortOrder = vanillaIndex;
                    }

                    allEntries.add(new SortableEntryData(serverEntry, sortOrder, info.ip.toLowerCase()));
                }

                vanillaIndex++;
            }
        }

        if (allEntries.isEmpty()) {
            int maxServerOrder = 0;
        } else {
            allEntries.stream().mapToInt(e -> e.sortOrder).max().orElse(0);
        }

        for (ServerFolder folder : organizer.getRootFolders()) {
            BepFolderEntry folderEntry = new BepFolderEntry(this.screen, folder);
            this.bep$populateFolderEntry(folderEntry, organizer, serverEntryMap);
            this.bep$folderEntries.put(folder.getId(), folderEntry);
            int folderOrder = folder.getSortOrder();
            allEntries.add(new SortableEntryData(folderEntry, folderOrder, folder.getId()));
        }

        allEntries.sort(Comparator.comparingInt(e -> e.sortOrder));
        boolean needsReindex = this.bep$needsReindex(allEntries);
        if (needsReindex) {
            int order = 0;

            for (SortableEntryData se : allEntries) {
                se.sortOrder = order++;
                if (se.entry instanceof BepFolderEntry fe) {
                    fe.getFolder().setSortOrder(se.sortOrder);
                } else if (se.entry instanceof OnlineServerEntry) {
                    ServerMetadata meta = organizer.getOrCreateMetadata(se.id);
                    meta.setSortOrder(se.sortOrder);
                }
            }

            organizer.save();
        }

        for (SortableEntryData se : allEntries) {
            this.bep$currentEntries.add(se.entry);
        }
    }

    @Unique
    private void bep$buildFolderEntries(ServerOrganizer organizer, Map<String, OnlineServerEntry> serverEntryMap) {
        String currentFolderId = this.bep$currentFolder.getId();
        List<SortableEntryData> allEntries = new ArrayList<>();
        Set<String> serversInFolderSet = new HashSet<>();

        for (String addr : organizer.getServersInFolder(currentFolderId)) {
            serversInFolderSet.add(addr.toLowerCase());
        }

        int vanillaIndex = 0;

        for (OnlineServerEntry serverEntry : this.onlineServers) {
            ServerData info = serverEntry.getServerData();
            if (info != null && serversInFolderSet.contains(info.ip.toLowerCase())) {
                ServerMetadata meta = organizer.getMetadata(info.ip);
                int sortOrder;
                if (meta != null) {
                    sortOrder = meta.getSortOrder();
                } else {
                    sortOrder = vanillaIndex;
                }

                allEntries.add(new SortableEntryData(serverEntry, sortOrder, info.ip.toLowerCase()));
            }

            vanillaIndex++;
        }

        for (ServerFolder childFolder : organizer.getChildFolders(currentFolderId)) {
            BepFolderEntry folderEntry = new BepFolderEntry(this.screen, childFolder);
            this.bep$populateFolderEntry(folderEntry, organizer, serverEntryMap);
            this.bep$folderEntries.put(childFolder.getId(), folderEntry);
            allEntries.add(new SortableEntryData(folderEntry, childFolder.getSortOrder(), childFolder.getId()));
        }

        allEntries.sort(Comparator.comparingInt(e -> e.sortOrder));
        boolean needsReindex = this.bep$needsReindex(allEntries);
        if (needsReindex) {
            int order = 0;

            for (SortableEntryData se : allEntries) {
                se.sortOrder = order++;
                if (se.entry instanceof BepFolderEntry fe) {
                    fe.getFolder().setSortOrder(se.sortOrder);
                } else if (se.entry instanceof OnlineServerEntry) {
                    ServerMetadata meta = organizer.getOrCreateMetadata(se.id);
                    meta.setFolderId(currentFolderId);
                    meta.setSortOrder(se.sortOrder);
                }
            }

            organizer.save();
        }

        for (SortableEntryData se : allEntries) {
            this.bep$currentEntries.add(se.entry);
        }
    }

    @Unique
    private boolean bep$needsReindex(List<SortableEntryData> entries) {
        Set<Integer> usedOrders = new HashSet<>();

        for (SortableEntryData se : entries) {
            if (se.sortOrder == Integer.MAX_VALUE || !usedOrders.add(se.sortOrder)) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private void bep$persistCurrentOrder(ServerOrganizer organizer) {
        int order = 0;

        for (net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.Entry entry : this.bep$currentEntries) {
            if (entry instanceof BepFolderEntry fe) {
                fe.getFolder().setSortOrder(order);
            } else if (entry instanceof OnlineServerEntry se) {
                ServerData info = se.getServerData();
                if (info != null) {
                    ServerMetadata meta = organizer.getOrCreateMetadata(info.ip);
                    meta.setSortOrder(order);
                }
            }

            order++;
        }

        organizer.save();
    }

    @Unique
    private void bep$populateFolderEntry(BepFolderEntry folderEntry, ServerOrganizer organizer, Map<String, OnlineServerEntry> serverEntryMap) {
        String folderId = folderEntry.getFolder().getId();

        for (String serverAddress : organizer.getServersInFolder(folderId)) {
            OnlineServerEntry serverEntry = serverEntryMap.get(serverAddress.toLowerCase());
            if (serverEntry != null) {
                folderEntry.addEntry(serverEntry);
            }
        }
    }

    @Override
    public void swap(int pos1, int pos2) {
        if (pos1 >= 0 && pos2 >= 0 && pos1 < this.bep$currentEntries.size() && pos2 < this.bep$currentEntries.size()) {
            this.bep$swapEntries(pos1, pos2);
        }
    }
}
