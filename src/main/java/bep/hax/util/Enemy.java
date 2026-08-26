package bep.hax.util;

import com.mojang.util.UndashedUuid;
import java.util.Objects;
import java.util.UUID;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Enemy implements ISerializable<Enemy>, Comparable<Enemy> {
    public volatile String name;
    @Nullable
    private volatile UUID id;

    public Enemy(String name, @Nullable UUID id) {
        this.name = name;
        this.id = id;
    }

    public Enemy(Player player) {
        this(player.getName().getString(), player.getUUID());
    }

    public Enemy(String name) {
        this(name, null);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", this.name);
        if (this.id != null) {
            tag.putString("id", UndashedUuid.toString(this.id));
        }

        return tag;
    }

    public Enemy fromTag(CompoundTag tag) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            Enemy enemy = (Enemy)o;
            return Objects.equals(this.name, enemy.name);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name);
    }

    public int compareTo(@NotNull Enemy enemy) {
        return this.name.compareToIgnoreCase(enemy.name);
    }
}
