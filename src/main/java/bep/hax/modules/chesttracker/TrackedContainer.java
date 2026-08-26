package bep.hax.modules.chesttracker;

import bep.hax.util.ItemSignature;
import bep.hax.util.ShulkerDataParser;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public class TrackedContainer {
    private final BlockPos position;
    private final String dimension;
    private String customName;
    private final Map<String, Integer> items;
    private final Map<String, Integer> sigCounts;
    private final List<ItemStack> itemStacks;
    private long lastUpdated;
    private String containerType;

    public TrackedContainer(BlockPos position, String dimension, String containerType) {
        this.position = position;
        this.dimension = dimension;
        this.containerType = containerType;
        this.items = new HashMap<>();
        this.sigCounts = new HashMap<>();
        this.itemStacks = new ArrayList<>();
        this.customName = null;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void updateContents(List<ItemStack> stacks) {
        this.items.clear();
        this.sigCounts.clear();
        this.itemStacks.clear();

        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                this.items.put(itemId, this.items.getOrDefault(itemId, 0) + stack.getCount());
                this.sigCounts.merge(ItemSignature.of(stack), stack.getCount(), Integer::sum);
                this.itemStacks.add(stack.copy());
                this.indexNestedItems(stack);
            }
        }

        this.lastUpdated = System.currentTimeMillis();
    }

    private void indexNestedItems(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            if (blockItem.getBlock() instanceof ShulkerBoxBlock) {
                for (ItemStack nested : ShulkerDataParser.parseShulkerContentsAsList(stack)) {
                    if (nested != null && !nested.isEmpty()) {
                        String itemId = BuiltInRegistries.ITEM.getKey(nested.getItem()).toString();
                        this.items.put(itemId, this.items.getOrDefault(itemId, 0) + nested.getCount());
                        this.sigCounts.merge(ItemSignature.of(nested), nested.getCount(), Integer::sum);
                    }
                }
            }
        }
    }

    public boolean containsItem(Item item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        return this.items.containsKey(itemId);
    }

    public boolean containsSignature(String sig) {
        return this.sigCounts.containsKey(sig);
    }

    public int getSignatureCount(String sig) {
        return this.sigCounts.getOrDefault(sig, 0);
    }

    public int getItemCount(String itemId) {
        return this.items.getOrDefault(itemId, 0);
    }

    public Map<String, Integer> getItems() {
        return new HashMap<>(this.items);
    }

    public List<ItemStack> getItemStacks() {
        return new ArrayList<>(this.itemStacks);
    }

    public BlockPos getPosition() {
        return this.position;
    }

    public String getCustomName() {
        return this.customName;
    }

    public long getLastUpdated() {
        return this.lastUpdated;
    }

    public String getContainerType() {
        return this.containerType;
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("x", this.position.getX());
        json.addProperty("y", this.position.getY());
        json.addProperty("z", this.position.getZ());
        json.addProperty("dimension", this.dimension);
        json.addProperty("type", this.containerType);
        json.addProperty("lastUpdated", this.lastUpdated);
        if (this.customName != null) {
            json.addProperty("customName", this.customName);
        }

        JsonObject itemsJson = new JsonObject();

        for (Entry<String, Integer> entry : this.items.entrySet()) {
            itemsJson.addProperty(entry.getKey(), entry.getValue());
        }

        json.add("items", itemsJson);
        JsonObject sigsJson = new JsonObject();

        for (Entry<String, Integer> entry : this.sigCounts.entrySet()) {
            sigsJson.addProperty(entry.getKey(), entry.getValue());
        }

        json.add("sigs", sigsJson);
        return json;
    }

    public static TrackedContainer fromJson(JsonObject json) {
        BlockPos pos = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
        String dimension = json.get("dimension").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "chest";
        TrackedContainer container = new TrackedContainer(pos, dimension, type);
        if (json.has("customName")) {
            container.customName = json.get("customName").getAsString();
        }

        if (json.has("lastUpdated")) {
            container.lastUpdated = json.get("lastUpdated").getAsLong();
        }

        if (json.has("items")) {
            JsonObject itemsJson = json.getAsJsonObject("items");

            for (String key : itemsJson.keySet()) {
                container.items.put(key, itemsJson.get(key).getAsInt());
            }
        }

        if (json.has("sigs")) {
            JsonObject sigsJson = json.getAsJsonObject("sigs");

            for (String key : sigsJson.keySet()) {
                container.sigCounts.put(key, sigsJson.get(key).getAsInt());
            }
        }

        return container;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else {
            return !(obj instanceof TrackedContainer other) ? false : this.position.equals(other.position) && this.dimension.equals(other.dimension);
        }
    }

    @Override
    public int hashCode() {
        return this.position.hashCode() * 31 + this.dimension.hashCode();
    }
}
