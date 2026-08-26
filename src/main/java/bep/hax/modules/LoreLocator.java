package bep.hax.modules;

import bep.hax.Bep;
import java.util.Optional;
import java.util.WeakHashMap;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.fish.TropicalFish.Variant;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class LoreLocator extends Module {
    private final SettingGroup sgRares = this.settings.createGroup("Rares Settings");
    private final SettingGroup sgUniques = this.settings.createGroup("Uniques Settings");
    private final Setting<Boolean> illegalEnchants = this.sgRares
        .add(
            new Builder()
                .name("illegal-enchants")
                .description("Highlight items with illegal enchantments like Mending/Infinity, or stacked Protection.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> onlySilkyShears = this.sgRares
        .add(
            new Builder()
                .name("exclusive-silky-shears")
                .description("Highlight silk touch shears only if they have no other enchants.")
                .defaultValue(true)
                .visible(this.illegalEnchants::get)
                .build()
        );
    private final Setting<Boolean> onlyInfinityMending = this.sgRares
        .add(
            new Builder()
                .name("exclusive-mending/Infinity")
                .description("Highlight bows & books that have ONLY mending & infinity applied.")
                .defaultValue(true)
                .visible(this.illegalEnchants::get)
                .build()
        );
    private final Setting<Boolean> negativeDurability = this.sgRares
        .add(
            new Builder()
                .name("negative-durability")
                .description("Highlight items with negative true durability (all negative durability items show in-game as 0 durability items in 1.21.)")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> petrifiedSlabs = this.sgRares
        .add(new Builder().name("alpha-slabs").description("Highlight alpha slabs (now petrified oak slabs.)").defaultValue(true).build());
    private final Setting<Boolean> lagRockets = this.sgRares
        .add(new Builder().name("lag-rockets").description("Highlight lag rockets.").defaultValue(true).build());
    private final Setting<Boolean> illegalFish = this.sgRares
        .add(
            new Builder()
                .name("illegal-fish")
                .description("Highlight illegal tropical fish with black as one of their colors. These are no longer obtainable as of 1.21.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> renamedItems = this.sgUniques
        .add(new Builder().name("renamed-items").description("Highlight renamed items in GUIs.").defaultValue(false).build());
    private final Setting<Boolean> writtenBooks = this.sgUniques
        .add(new Builder().name("written-books").description("Highlight written books.").defaultValue(true).build());
    private final Setting<String> metadataSearch = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("metadata-search")
                .description("Fuzzy search for item NBT data. Notable usage examples: specific book authors, item names, or enchants.")
                .defaultValue("")
                .build()
        );
    private final Setting<Boolean> splitQueries = this.settings
        .getDefaultGroup()
        .add(
            new Builder()
                .name("split-queries")
                .description("Split search queries into multiple items separated by commas. Disable to treat commas literally in the search instead.")
                .defaultValue(true)
                .build()
        );
    public final Setting<SettingColor> color = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("highlight-color").defaultValue(new SettingColor(254, 0, 255, 119)).build()
        );
    private final Setting<Boolean> ownInventory = this.settings
        .getDefaultGroup()
        .add(
            new Builder()
                .name("inventory-highlight")
                .description("Highlight items meeting the above criteria on the player inventory screen.")
                .defaultValue(true)
                .build()
        );
    private static final String[] NO_QUERIES = new String[0];
    private final WeakHashMap<ItemStack, Boolean> highlightCache = new WeakHashMap<>();
    private int cachedFlags = -1;
    private String cachedQuery = null;
    private String[] cachedQueries = NO_QUERIES;
    private String[] cachedNbtQueries = NO_QUERIES;

    public LoreLocator() {
        super(Bep.HUNT_CATEGORY, "LoreLocator", "Slot highlighter for rare, unique, and anomalous items.");
    }

    private int enchantmentsCount(ItemStack stack) {
        int count = 0;
        if (!stack.isEmpty()) {
            count = stack.getItem() == Items.ENCHANTED_BOOK
                ? stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).keySet().size()
                : stack.getEnchantments().size();
        }

        return count;
    }

    private boolean shouldIgnoreCurrentScreenHandler(LocalPlayer player) {
        if (this.mc.screen == null) {
            return true;
        }

        if (player.containerMenu == null) {
            return true;
        }

        AbstractContainerMenu handler = player.containerMenu;
        return handler instanceof InventoryMenu
            ? !this.ownInventory.get()
            : !(handler instanceof AbstractFurnaceMenu)
                && !(handler instanceof ChestMenu)
                && !(handler instanceof DispenserMenu)
                && !(handler instanceof ShulkerBoxMenu)
                && !(handler instanceof HopperMenu)
                && !(handler instanceof HorseInventoryMenu);
    }

    private int settingsFlags() {
        return (this.illegalEnchants.get() ? 1 : 0)
            | (this.onlySilkyShears.get() ? 2 : 0)
            | (this.onlyInfinityMending.get() ? 4 : 0)
            | (this.negativeDurability.get() ? 8 : 0)
            | (this.petrifiedSlabs.get() ? 16 : 0)
            | (this.lagRockets.get() ? 32 : 0)
            | (this.illegalFish.get() ? 64 : 0)
            | (this.renamedItems.get() ? 128 : 0)
            | (this.writtenBooks.get() ? 256 : 0)
            | (this.splitQueries.get() ? 512 : 0);
    }

    private void refreshCache() {
        int flags = this.settingsFlags();
        String query = this.metadataSearch.get();
        if (flags != this.cachedFlags || !query.equals(this.cachedQuery)) {
            this.cachedFlags = flags;
            this.cachedQuery = query;
            String lowerCase = query.toLowerCase();
            if (lowerCase.trim().isEmpty()) {
                this.cachedQueries = NO_QUERIES;
                this.cachedNbtQueries = NO_QUERIES;
            } else {
                String[] queries = this.splitQueries.get() && lowerCase.contains(",") ? lowerCase.split(",") : new String[]{lowerCase};
                this.cachedQueries = new String[queries.length];
                this.cachedNbtQueries = new String[queries.length];

                for (int i = 0; i < queries.length; i++) {
                    this.cachedQueries[i] = queries[i].trim();
                    this.cachedNbtQueries[i] = this.cachedQueries[i].replace(" ", "_");
                }
            }

            this.highlightCache.clear();
        }
    }

    public boolean shouldHighlightSlot(ItemStack stack) {
        if (this.mc.player == null) {
            return false;
        }

        if (!stack.isEmpty() && !this.shouldIgnoreCurrentScreenHandler(this.mc.player)) {
            this.refreshCache();
            Boolean cached = this.highlightCache.get(stack);
            if (cached != null) {
                return cached;
            }

            boolean result = this.computeHighlight(stack);
            this.highlightCache.put(stack, result);
            return result;
        } else {
            return false;
        }
    }

    private boolean computeHighlight(ItemStack stack) {
        if (this.matchesItemDirect(stack)) {
            return true;
        }

        if (Utils.hasItems(stack)) {
            ItemStack[] stacks = new ItemStack[27];
            Utils.getItemsInContainerItem(stack, stacks);

            for (ItemStack s : stacks) {
                if (s != null && !s.isEmpty() && this.computeHighlight(s)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean matchesMetadataSearch(ItemStack stack) {
        DataComponentMap metadata = stack.getComponents();
        if (metadata != null) {
            String components = metadata.toString().toLowerCase();

            for (int i = 0; i < this.cachedQueries.length; i++) {
                if (components.contains(this.cachedQueries[i]) || components.contains(this.cachedNbtQueries[i])) {
                    return true;
                }
            }
        }

        String name = stack.getHoverName().getString().toLowerCase();
        String defaultName = stack.getItem().getDefaultInstance().getHoverName().getString().toLowerCase();

        for (String query : this.cachedQueries) {
            if (name.contains(query) || defaultName.contains(query)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesItemDirect(ItemStack stack) {
        if (this.cachedQueries.length > 0 && this.matchesMetadataSearch(stack)) {
            return true;
        }

        if (this.lagRockets.get() && stack.has(DataComponents.FIREWORKS)) {
            Fireworks firework = stack.get(DataComponents.FIREWORKS);
            if (firework.explosions().size() == 7) {
                return true;
            }
        }

        if (this.illegalFish.get() && stack.is(Items.TROPICAL_FISH_BUCKET)) {
            CustomData nbtComponent = stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY);
            if (!nbtComponent.isEmpty()) {
                Optional<Variant> optional = Variant.CODEC
                    .parse(NbtOps.INSTANCE, nbtComponent.copyTag().getCompound("BucketVariantTag").orElse(new CompoundTag()))
                    .result();
                if (optional.isPresent()) {
                    Variant variant = optional.get();
                    String string = "color.minecraft." + variant.baseColor();
                    String string2 = "color.minecraft." + variant.patternColor();
                    int i = TropicalFish.COMMON_VARIANTS.indexOf(variant);
                    if (i == -1 && (string.contains("black") || string2.contains("black"))) {
                        return true;
                    }
                }
            }
        }

        if (this.writtenBooks.get() && stack.getItem() == Items.WRITTEN_BOOK) {
            return true;
        }

        if (this.petrifiedSlabs.get() && stack.getItem() == Items.PETRIFIED_OAK_SLAB) {
            return true;
        }

        if (this.renamedItems.get() && stack.has(DataComponents.CUSTOM_NAME)) {
            return true;
        }

        if (this.negativeDurability.get() && stack.isDamageableItem() && stack.getOrDefault(DataComponents.DAMAGE, stack.getDamageValue()) >= stack.getMaxDamage()) {
            return true;
        }

        if (this.illegalEnchants.get() && (stack.getItem() == Items.ENCHANTED_BOOK || stack.isEnchanted())) {
            int enchantmentsCount = this.enchantmentsCount(stack);
            if (stack.getItem() == Items.SHEARS && Utils.hasEnchantment(stack, Enchantments.SILK_TOUCH)) {
                return enchantmentsCount == 1 || !this.onlySilkyShears.get();
            }

            if (stack.getItem() == Items.ENCHANTED_BOOK && (enchantmentsCount == 0 || enchantmentsCount > 7)) {
                return true;
            }

            boolean hasProtection = Utils.hasEnchantment(stack, Enchantments.PROTECTION);
            if (Utils.hasEnchantment(stack, Enchantments.FIRE_PROTECTION)) {
                if (hasProtection) {
                    return true;
                }

                hasProtection = true;
            }

            if (Utils.hasEnchantment(stack, Enchantments.BLAST_PROTECTION)) {
                if (hasProtection) {
                    return true;
                }

                hasProtection = true;
            }

            if (Utils.hasEnchantment(stack, Enchantments.PROJECTILE_PROTECTION) && hasProtection) {
                return true;
            }

            if (Utils.hasEnchantment(stack, Enchantments.INFINITY) && Utils.hasEnchantment(stack, Enchantments.MENDING)) {
                return enchantmentsCount == 2 || !this.onlyInfinityMending.get();
            }
        }

        return false;
    }
}
