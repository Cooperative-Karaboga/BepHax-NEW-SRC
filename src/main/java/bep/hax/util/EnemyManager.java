package bep.hax.util;

import com.mojang.util.UndashedUuid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class EnemyManager extends System<EnemyManager> implements Iterable<Enemy> {
    private final List<Enemy> enemies = new ArrayList<>();

    public EnemyManager() {
        super("enemies");
    }

    public static EnemyManager get() {
        return Systems.get(EnemyManager.class);
    }

    public boolean add(Enemy enemy) {
        if (enemy.name.isEmpty() || enemy.name.contains(" ")) {
            return false;
        } else if (!this.enemies.contains(enemy)) {
            this.enemies.add(enemy);
            this.save();
            return true;
        } else {
            return false;
        }
    }

    public boolean add(String name) {
        return name != null && !name.isEmpty() ? this.add(new Enemy(name)) : false;
    }

    public boolean remove(Enemy enemy) {
        if (this.enemies.remove(enemy)) {
            this.save();
            return true;
        } else {
            return false;
        }
    }

    public boolean remove(String name) {
        if (name != null && !name.isEmpty()) {
            Enemy enemy = this.get(name);
            return enemy != null ? this.remove(enemy) : false;
        } else {
            return false;
        }
    }

    public Enemy get(String name) {
        for (Enemy enemy : this.enemies) {
            if (enemy.name.equalsIgnoreCase(name)) {
                return enemy;
            }
        }

        return null;
    }

    public Enemy get(Player player) {
        return this.get(player.getName().getString());
    }

    public boolean isEnemy(Player player) {
        return player != null && this.get(player) != null;
    }

    public boolean isEnemy(String name) {
        return this.get(name) != null;
    }

    public List<String> getEnemyNames() {
        List<String> names = new ArrayList<>();

        for (Enemy enemy : this.enemies) {
            names.add(enemy.name);
        }

        return names;
    }

    public int count() {
        return this.enemies.size();
    }

    public void clear() {
        this.enemies.clear();
        this.save();
    }

    @NotNull
    @Override
    public Iterator<Enemy> iterator() {
        return this.enemies.iterator();
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("enemies", NbtUtils.listToTag(this.enemies));
        return tag;
    }

    public EnemyManager fromTag(CompoundTag tag) {
        this.enemies.clear();

        for (Tag itemTag : tag.getListOrEmpty("enemies")) {
            CompoundTag enemyTag = (CompoundTag)itemTag;
            if (enemyTag.contains("name")) {
                String name = enemyTag.getStringOr("name", "");
                if (this.get(name) == null) {
                    String uuid = enemyTag.getStringOr("id", "");
                    Enemy enemy = !uuid.isBlank() ? new Enemy(name, UndashedUuid.fromStringLenient(uuid)) : new Enemy(name);
                    this.enemies.add(enemy);
                }
            }
        }

        Collections.sort(this.enemies);
        return this;
    }
}
