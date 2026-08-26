package bep.hax.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class ItemSignature {
    private ItemSignature() {
    }

    public static String of(ItemStack s) {
        if (s != null && !s.isEmpty()) {
            String itemId = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            ItemEnchantments ench = s.getItem() == Items.ENCHANTED_BOOK ? s.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY) : s.getEnchantments();
            List<String> parts = new ArrayList<>();

            for (Entry<Holder<Enchantment>> e : ench.entrySet()) {
                String id = e.getKey().getRegisteredName();
                if (id != null && !id.isEmpty()) {
                    parts.add(id + "=" + e.getIntValue());
                }
            }

            return format(itemId, parts);
        } else {
            return "";
        }
    }

    public static String format(String itemId, List<String> enchParts) {
        List<String> sorted = new ArrayList<>(enchParts);
        Collections.sort(sorted);
        return itemId + "|" + String.join(",", sorted);
    }
}
