package bep.hax.serverlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ServerOrganizer extends System<ServerOrganizer> {
    private final List<ServerFolder> folders = new ArrayList<>();
    private final Map<String, ServerMetadata> serverMetadata = new HashMap<>();

    public ServerOrganizer() {
        super("server-organizer");
    }

    public static ServerOrganizer get() {
        return Systems.get(ServerOrganizer.class);
    }

    public ServerFolder createFolder(String name) {
        ServerFolder folder = new ServerFolder(name);
        int maxOrder = this.getMaxSortOrderForParent(null);
        folder.setSortOrder(maxOrder + 1);
        this.folders.add(folder);
        this.save();
        return folder;
    }

    public ServerFolder createFolder(String name, @Nullable String parentId) {
        ServerFolder folder = new ServerFolder(name);
        folder.setParentId(parentId);
        int maxOrder = this.getMaxSortOrderForParent(parentId);
        folder.setSortOrder(maxOrder + 1);
        this.folders.add(folder);
        this.save();
        return folder;
    }

    private int getMaxSortOrderForParent(@Nullable String parentId) {
        int max = -1;

        for (ServerFolder f : this.folders) {
            if (parentId == null && f.getParentId() == null || parentId != null && parentId.equals(f.getParentId())) {
                max = Math.max(max, f.getSortOrder());
            }
        }

        for (ServerMetadata meta : this.serverMetadata.values()) {
            if (parentId == null && meta.getFolderId() == null || parentId != null && parentId.equals(meta.getFolderId())) {
                max = Math.max(max, meta.getSortOrder());
            }
        }

        return max;
    }

    public boolean removeFolder(String folderId) {
        ServerFolder folder = this.getFolder(folderId);
        if (folder == null) {
            return false;
        }

        for (ServerMetadata meta : this.serverMetadata.values()) {
            if (folderId.equals(meta.getFolderId())) {
                meta.setFolderId(null);
            }
        }

        String parentId = folder.getParentId();

        for (ServerFolder child : this.folders) {
            if (folderId.equals(child.getParentId())) {
                child.setParentId(parentId);
            }
        }

        this.folders.remove(folder);
        this.save();
        return true;
    }

    @Nullable
    public ServerFolder getFolder(String folderId) {
        if (folderId == null) {
            return null;
        }

        for (ServerFolder folder : this.folders) {
            if (folder.getId().equals(folderId)) {
                return folder;
            }
        }

        return null;
    }

    public List<ServerFolder> getFolders() {
        return Collections.unmodifiableList(this.folders);
    }

    public List<ServerFolder> getRootFolders() {
        List<ServerFolder> roots = new ArrayList<>();

        for (ServerFolder folder : this.folders) {
            if (folder.getParentId() == null) {
                roots.add(folder);
            }
        }

        return roots;
    }

    public List<ServerFolder> getChildFolders(String parentId) {
        List<ServerFolder> children = new ArrayList<>();

        for (ServerFolder folder : this.folders) {
            if (Objects.equals(folder.getParentId(), parentId)) {
                children.add(folder);
            }
        }

        return children;
    }

    @NotNull
    public ServerMetadata getOrCreateMetadata(String serverAddress) {
        String key = serverAddress.toLowerCase();
        ServerMetadata meta = this.serverMetadata.get(key);
        if (meta == null) {
            meta = new ServerMetadata(serverAddress);
            this.serverMetadata.put(key, meta);
        }

        return meta;
    }

    @Nullable
    public ServerMetadata getMetadata(String serverAddress) {
        return this.serverMetadata.get(serverAddress.toLowerCase());
    }

    @Nullable
    public ServerMetadata getMetadata(ServerData serverInfo) {
        return serverInfo == null ? null : this.getMetadata(serverInfo.ip);
    }

    public void setDisplayName(String serverAddress, @Nullable String displayName) {
        ServerMetadata meta = this.getOrCreateMetadata(serverAddress);
        meta.setDisplayName(displayName);
        this.save();
    }

    @Nullable
    public String getDisplayName(String serverAddress) {
        ServerMetadata meta = this.getMetadata(serverAddress);
        return meta != null ? meta.getDisplayName() : null;
    }

    @Nullable
    public String getDisplayName(ServerData serverInfo) {
        return serverInfo == null ? null : this.getDisplayName(serverInfo.ip);
    }

    public void setServerFolder(String serverAddress, @Nullable String folderId) {
        ServerMetadata meta = this.getOrCreateMetadata(serverAddress);
        meta.setFolderId(folderId);
        this.save();
    }

    @Nullable
    public String getServerFolder(String serverAddress) {
        ServerMetadata meta = this.getMetadata(serverAddress);
        return meta != null ? meta.getFolderId() : null;
    }

    public List<String> getServersInFolder(@Nullable String folderId) {
        List<String> servers = new ArrayList<>();

        for (Entry<String, ServerMetadata> entry : this.serverMetadata.entrySet()) {
            if (Objects.equals(entry.getValue().getFolderId(), folderId)) {
                servers.add(entry.getKey());
            }
        }

        return servers;
    }

    public void removeServerMetadata(String serverAddress) {
        this.serverMetadata.remove(serverAddress.toLowerCase());
        this.save();
    }

    public String getFolderPath(String folderId) {
        StringBuilder path = new StringBuilder();
        List<String> pathParts = new ArrayList<>();

        while (folderId != null) {
            ServerFolder folder = this.getFolder(folderId);
            if (folder == null) {
                break;
            }

            pathParts.add(0, folder.getName());
            folderId = folder.getParentId();
        }

        for (String part : pathParts) {
            path.append(part).append("/");
        }

        return path.toString();
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("folders", NbtUtils.listToTag(this.folders));
        tag.put("serverMetadata", NbtUtils.listToTag(this.serverMetadata.values()));
        return tag;
    }

    public ServerOrganizer fromTag(CompoundTag tag) {
        this.folders.clear();
        this.serverMetadata.clear();

        for (Tag element : tag.getListOrEmpty("folders")) {
            if (element instanceof CompoundTag folderTag) {
                this.folders.add(new ServerFolder(folderTag));
            }
        }

        Collections.sort(this.folders);

        for (Tag element : tag.getListOrEmpty("serverMetadata")) {
            if (element instanceof CompoundTag metaTag) {
                ServerMetadata meta = new ServerMetadata(metaTag);
                this.serverMetadata.put(meta.getServerAddress(), meta);
            }
        }

        return this;
    }
}
