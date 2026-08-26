package bep.hax.serverlist;

import java.util.Objects;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public class ServerMetadata implements ISerializable<ServerMetadata> {
    private final String serverAddress;
    private String displayName;
    private String folderId;
    private int sortOrder;

    public ServerMetadata(String serverAddress) {
        this.serverAddress = serverAddress.toLowerCase();
        this.displayName = null;
        this.folderId = null;
        this.sortOrder = 0;
    }

    public ServerMetadata(CompoundTag tag) {
        this.serverAddress = tag.getStringOr("serverAddress", "").toLowerCase();
        this.displayName = tag.contains("displayName") ? tag.getStringOr("displayName", null) : null;
        this.folderId = tag.contains("folderId") ? tag.getStringOr("folderId", null) : null;
        this.sortOrder = tag.getIntOr("sortOrder", 0);
    }

    public String getServerAddress() {
        return this.serverAddress;
    }

    @Nullable
    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
    }

    public boolean hasDisplayName() {
        return this.displayName != null && !this.displayName.isEmpty();
    }

    @Nullable
    public String getFolderId() {
        return this.folderId;
    }

    public void setFolderId(@Nullable String folderId) {
        this.folderId = folderId;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("serverAddress", this.serverAddress);
        if (this.displayName != null) {
            tag.putString("displayName", this.displayName);
        }

        if (this.folderId != null) {
            tag.putString("folderId", this.folderId);
        }

        tag.putInt("sortOrder", this.sortOrder);
        return tag;
    }

    public ServerMetadata fromTag(CompoundTag tag) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            ServerMetadata that = (ServerMetadata)o;
            return Objects.equals(this.serverAddress, that.serverAddress);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.serverAddress);
    }
}
