package bep.hax.serverlist;

import java.util.Objects;
import java.util.UUID;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

public class ServerFolder implements ISerializable<ServerFolder>, Comparable<ServerFolder> {
    private final String id;
    private String name;
    private String parentId;
    private boolean collapsed;
    private int sortOrder;

    public ServerFolder(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.parentId = null;
        this.collapsed = false;
        this.sortOrder = 0;
    }

    public ServerFolder(CompoundTag tag) {
        this.id = tag.getStringOr("id", UUID.randomUUID().toString());
        this.name = tag.getStringOr("name", "Unnamed Folder");
        this.parentId = tag.contains("parentId") ? tag.getStringOr("parentId", null) : null;
        this.collapsed = tag.getBooleanOr("collapsed", false);
        this.sortOrder = tag.getIntOr("sortOrder", 0);
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentId() {
        return this.parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public boolean isCollapsed() {
        return this.collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
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
        tag.putString("id", this.id);
        tag.putString("name", this.name);
        if (this.parentId != null) {
            tag.putString("parentId", this.parentId);
        }

        tag.putBoolean("collapsed", this.collapsed);
        tag.putInt("sortOrder", this.sortOrder);
        return tag;
    }

    public ServerFolder fromTag(CompoundTag tag) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            ServerFolder that = (ServerFolder)o;
            return Objects.equals(this.id, that.id);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    public int compareTo(@NotNull ServerFolder other) {
        int orderCompare = Integer.compare(this.sortOrder, other.sortOrder);
        return orderCompare != 0 ? orderCompare : this.name.compareToIgnoreCase(other.name);
    }
}
