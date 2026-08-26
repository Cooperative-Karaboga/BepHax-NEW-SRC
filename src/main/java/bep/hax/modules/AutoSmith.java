package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.mixin.accessor.ClientConnectionAccessor;
import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.LogUtil;
import bep.hax.util.MsgUtil;
import bep.hax.util.Utils;
import com.google.common.primitives.Shorts;
import com.google.common.primitives.SignedBytes;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedPatchMap.HashGenerator;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.jetbrains.annotations.Nullable;

public class AutoSmith extends Module {
    private final SettingGroup trimSettings = this.settings.createGroup("Armor Trims");
    private final SettingGroup modeSettings = this.settings.createGroup("Smithing Mode");
    private final Setting<AutoSmith.ModuleMode> moduleMode = this.modeSettings
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("module-mode"))
                        .description("Packet is significantly faster (stacked silent), Interact is safer but slower."))
                    .defaultValue(AutoSmith.ModuleMode.Packet))
                .build()
        );
    private final Setting<Integer> tickRate = this.modeSettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("tick-delay")
                .description("Delay between actions in Interact mode. Increase if getting kicked.")
                .visible(() -> this.moduleMode.get().equals(AutoSmith.ModuleMode.Interact))
                .range(2, 100)
                .sliderRange(2, 20)
                .defaultValue(4)
                .build()
        );
    private final Setting<Integer> packetLimit = this.modeSettings
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("packet-limit")
                .description("Max packets to send at once in Packet mode. Decrease if getting kicked.")
                .visible(() -> this.moduleMode.get().equals(AutoSmith.ModuleMode.Packet))
                .min(20)
                .sliderMax(100)
                .defaultValue(42)
                .build()
        );
    private final Setting<AutoSmith.SmithingMode> operatingMode = this.modeSettings
        .add(((Builder)((Builder)new Builder().name("smithing-mode")).defaultValue(AutoSmith.SmithingMode.Upgrade)).build());
    private final Setting<Boolean> overwriteTrims = this.modeSettings
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("overwrite-trims")
                .description("Trim armor pieces which already contain a different trim pattern or material.")
                .defaultValue(false)
                .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim)
                .build()
        );
    private final Setting<AutoSmith.ArmorMaterials> helmetType = this.trimSettings
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("helmet-armor-type")).description("Which type of helmet to apply trims to."))
                        .defaultValue(AutoSmith.ArmorMaterials.Netherite))
                    .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim))
                .build()
        );
    private final Setting<AutoSmith.ArmorTrims> helmetTrim = this.trimSettings
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("helmet-armor-trim")).description("Which armor trim to apply onto helmets."))
                        .defaultValue(AutoSmith.ArmorTrims.Eye))
                    .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim))
                .build()
        );
    private final Setting<AutoSmith.TrimMaterial> helmetTrimMaterial = this.trimSettings
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("helmet-trim-material")).description("What material to use for helmet armor trims."))
                        .defaultValue(AutoSmith.TrimMaterial.Amethyst))
                    .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim))
                .build()
        );
    private final Setting<AutoSmith.ArmorMaterials> chestplateType = this.trimSettings
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("chestplate-armor-type")).description("Which type of chestplates to apply trims to."))
                        .defaultValue(AutoSmith.ArmorMaterials.Netherite))
                    .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim))
                .build()
        );
    private final Setting<AutoSmith.ArmorTrims> chestplateTrim = this.trimSettings
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("chestplate-armor-trim")).description("Which armor trim to apply onto chestplates."))
                        .defaultValue(AutoSmith.ArmorTrims.Eye))
                    .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim))
                .build()
        );
    private final Setting<AutoSmith.TrimMaterial> chestplateTrimMaterial = this.trimSettings
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("chestplate-trim-material"))
                            .description("What material to use for chestplate armor trims."))
                        .defaultValue(AutoSmith.TrimMaterial.Amethyst))
                    .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim))
                .build()
        );
    private final Setting<AutoSmith.ArmorMaterials> leggingsType = this.trimSettings
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("leggings-armor-type")).description("Which type of leggings to apply trims to."))
                        .defaultValue(AutoSmith.ArmorMaterials.Netherite))
                    .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim))
                .build()
        );
    private final Setting<AutoSmith.ArmorTrims> leggingsTrim = this.trimSettings
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("leggings-armor-trim")).description("Which armor trim to apply onto leggings."))
                        .defaultValue(AutoSmith.ArmorTrims.Eye))
                    .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim))
                .build()
        );
    private final Setting<AutoSmith.TrimMaterial> leggingsTrimMaterial = this.trimSettings
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("leggings-trim-material")).description("What material to use for leggings armor trims."))
                        .defaultValue(AutoSmith.TrimMaterial.Amethyst))
                    .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim))
                .build()
        );
    private final Setting<AutoSmith.ArmorMaterials> bootsType = this.trimSettings
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("boots-armor-type")).description("Which type of boots to apply trims to."))
                        .defaultValue(AutoSmith.ArmorMaterials.Netherite))
                    .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim))
                .build()
        );
    private final Setting<AutoSmith.ArmorTrims> bootsTrim = this.trimSettings
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("boots-armor-trim")).description("Which armor trim to apply onto boots."))
                        .defaultValue(AutoSmith.ArmorTrims.Eye))
                    .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim))
                .build()
        );
    private final Setting<AutoSmith.TrimMaterial> bootsTrimMaterial = this.trimSettings
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("boots-trim-material")).description("What material to use for boots armor trims."))
                        .defaultValue(AutoSmith.TrimMaterial.Amethyst))
                    .visible(() -> this.operatingMode.get() == AutoSmith.SmithingMode.Trim))
                .build()
        );
    private final Setting<Boolean> closeOnDone = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("close-screen")
                .description("Automatically close the crafting screen when no more gear can be upgraded.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> disableOnDone = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("disable-on-done")
                .description("Automatically disable the module when no more gear can be upgraded.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> pingOnDone = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("sound-ping")
                .description("Play a sound cue when no more gear can be trimmed or upgraded.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> pingVolume = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("ping-volume")
                .visible(this.pingOnDone::get)
                .sliderMin(0.0)
                .sliderMax(5.0)
                .defaultValue(0.5)
                .build()
        );
    private final Setting<Boolean> debug = this.settings
        .getDefaultGroup()
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug")
                .description("Displays debug messages in your chat.")
                .defaultValue(false)
                .visible(() -> false)
                .build()
        );
    private int timer = 0;
    private boolean notified = false;
    private boolean foundEquip = false;
    private boolean foundIngots = false;
    private boolean foundTemplates = false;
    private boolean resettingTemplates = false;
    private boolean resettingMaterials = false;
    @Nullable
    private ItemStack trimStack = null;
    @Nullable
    private ItemStack materialStack = null;
    @Nullable
    private ItemStack equipmentStack = null;
    @Nullable
    private AutoSmith.ArmorType currentlyLookingFor = null;
    private final IntArrayList projectedEmpty = new IntArrayList();
    private final IntArrayList processedSlots = new IntArrayList();
    private final List<AutoSmith.ArmorType> exhaustedArmorTypes = new ArrayList<>();

    public AutoSmith() {
        super(Bep.CATEGORY, "AutoSmith", "Automatically upgrade gear or trim armor in smithing tables.");
    }

    private int getInvSize() {
        return ((PlayerInventoryAccessor)this.mc.player.getInventory()).getMain().size();
    }

    private HashGenerator getHasher() {
        return this.mc.getConnection().decoratedHashOpsGenenerator();
    }

    private HashedStack hashStack(ItemStack stack) {
        return HashedStack.create(stack, this.getHasher());
    }

    private int getArmorSlotId(ItemStack itemStack) {
        if (!itemStack.has(DataComponents.EQUIPPABLE)) {
            return -1;
        }

        Equippable equip = itemStack.get(DataComponents.EQUIPPABLE);
        if (equip == null) {
            return -1;
        }

        EquipmentSlot slot = equip.slot();

        return switch (slot) {
            case FEET -> 0;
            case LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
            default -> -1;
        };
    }

    @Nullable
    private AutoSmith.ArmorType getArmorType(ItemStack stack) {
        int slotId = this.getArmorSlotId(stack);
        return AutoSmith.ArmorType.fromSlotId(slotId);
    }

    private boolean matchesArmorMaterial(ItemStack stack, AutoSmith.ArmorMaterials material) {
        Item item = stack.getItem();

        return switch (material) {
            case Iron -> item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE || item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS;
            case Gold -> item == Items.GOLDEN_HELMET || item == Items.GOLDEN_CHESTPLATE || item == Items.GOLDEN_LEGGINGS || item == Items.GOLDEN_BOOTS;
            case Chain -> item == Items.CHAINMAIL_HELMET || item == Items.CHAINMAIL_CHESTPLATE || item == Items.CHAINMAIL_LEGGINGS || item == Items.CHAINMAIL_BOOTS;
            case Turtle -> item == Items.TURTLE_HELMET;
            case Leather -> item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE || item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS;
            case Diamond -> item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS;
            case Netherite -> item == Items.NETHERITE_HELMET
                || item == Items.NETHERITE_CHESTPLATE
                || item == Items.NETHERITE_LEGGINGS
                || item == Items.NETHERITE_BOOTS;
        };
    }

    private boolean isValidEquipmentForUpgrading(ItemStack stack) {
        return stack.is(Items.DIAMOND_HOE)
            || stack.is(Items.DIAMOND_PICKAXE)
            || stack.is(Items.DIAMOND_AXE)
            || stack.is(Items.DIAMOND_SHOVEL)
            || stack.is(Items.DIAMOND_SWORD)
            || stack.is(Items.DIAMOND_HELMET)
            || stack.is(Items.DIAMOND_CHESTPLATE)
            || stack.is(Items.DIAMOND_LEGGINGS)
            || stack.is(Items.DIAMOND_BOOTS);
    }

    private boolean isValidEquipmentForTrimming(ItemStack stack) {
        AutoSmith.ArmorType armorType = this.getArmorType(stack);
        if (armorType == null) {
            return false;
        }

        if (this.exhaustedArmorTypes.contains(armorType)) {
            return false;
        }

        if (this.currentlyLookingFor != null && armorType != this.currentlyLookingFor) {
            return false;
        }

        boolean correctMaterial = switch (armorType) {
            case HELMET -> this.matchesArmorMaterial(stack, this.helmetType.get());
            case CHESTPLATE -> this.matchesArmorMaterial(stack, this.chestplateType.get());
            case LEGGINGS -> this.matchesArmorMaterial(stack, this.leggingsType.get());
            case BOOTS -> this.matchesArmorMaterial(stack, this.bootsType.get());
        };
        if (!correctMaterial) {
            return false;
        }

        if (!stack.has(DataComponents.TRIM)) {
            return true;
        }

        if (!this.overwriteTrims.get()) {
            return false;
        }

        ArmorTrim trim = stack.get(DataComponents.TRIM);
        if (trim == null) {
            return true;
        }

        String pattern = trim.pattern().getRegisteredName();
        String material = trim.material().getRegisteredName();

        return switch (armorType) {
            case HELMET -> (!this.helmetTrim.get().label.equals(pattern) || !this.helmetTrimMaterial.get().label.equals(material))
                && this.hasRequiredMaterialsForTrimming(armorType);
            case CHESTPLATE -> (!this.chestplateTrim.get().label.equals(pattern) || !this.chestplateTrimMaterial.get().label.equals(material))
                && this.hasRequiredMaterialsForTrimming(armorType);
            case LEGGINGS -> (!this.leggingsTrim.get().label.equals(pattern) || !this.leggingsTrimMaterial.get().label.equals(material))
                && this.hasRequiredMaterialsForTrimming(armorType);
            case BOOTS -> (!this.bootsTrim.get().label.equals(pattern) || !this.bootsTrimMaterial.get().label.equals(material))
                && this.hasRequiredMaterialsForTrimming(armorType);
        };
    }

    private boolean hasRequiredMaterialsForTrimming(AutoSmith.ArmorType type) {
        AutoSmith.ArmorTrims trimSetting = switch (type) {
            case HELMET -> (AutoSmith.ArmorTrims)this.helmetTrim.get();
            case CHESTPLATE -> (AutoSmith.ArmorTrims)this.chestplateTrim.get();
            case LEGGINGS -> (AutoSmith.ArmorTrims)this.leggingsTrim.get();
            case BOOTS -> (AutoSmith.ArmorTrims)this.bootsTrim.get();
        };

        AutoSmith.TrimMaterial materialSetting = switch (type) {
            case HELMET -> (AutoSmith.TrimMaterial)this.helmetTrimMaterial.get();
            case CHESTPLATE -> (AutoSmith.TrimMaterial)this.chestplateTrimMaterial.get();
            case LEGGINGS -> (AutoSmith.TrimMaterial)this.leggingsTrimMaterial.get();
            case BOOTS -> (AutoSmith.TrimMaterial)this.bootsTrimMaterial.get();
        };
        return this.hasItem(this.getTemplateItem(trimSetting)) && this.hasItem(this.getMaterialItem(materialSetting));
    }

    private Item getTemplateItem(AutoSmith.ArmorTrims trim) {
        return switch (trim) {
            case Eye -> Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Vex -> Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Rib -> Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Bolt -> Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Wild -> Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Dune -> Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Host -> Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Ward -> Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Tide -> Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Flow -> Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Coast -> Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Snout -> Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Spire -> Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Raiser -> Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Shaper -> Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Sentry -> Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Silence -> Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE;
            case Wayfinder -> Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE;
        };
    }

    private Item getMaterialItem(AutoSmith.TrimMaterial material) {
        return switch (material) {
            case Iron -> Items.IRON_INGOT;
            case Gold -> Items.GOLD_INGOT;
            case Lapis -> Items.LAPIS_LAZULI;
            case Resin -> Items.RESIN_BRICK;
            case Copper -> Items.COPPER_INGOT;
            case Quartz -> Items.QUARTZ;
            case Emerald -> Items.EMERALD;
            case Diamond -> Items.DIAMOND;
            case Redstone -> Items.REDSTONE;
            case Amethyst -> Items.AMETHYST_SHARD;
            case Netherite -> Items.NETHERITE_INGOT;
        };
    }

    private boolean hasItem(Item needed) {
        if (this.mc.player == null) {
            return false;
        }

        if (this.mc.player.containerMenu instanceof SmithingMenu ss) {
            for (int var5 = 0; var5 < this.getInvSize() + 4; var5++) {
                ItemStack stack = ss.getSlot(var5).getItem();
                if (stack.getItem() == needed) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    @Override
    public void onDeactivate() {
        this.timer = 0;
        this.trimStack = null;
        this.notified = false;
        this.foundEquip = false;
        this.foundIngots = false;
        this.materialStack = null;
        this.equipmentStack = null;
        this.foundTemplates = false;
        this.processedSlots.clear();
        this.projectedEmpty.clear();
        this.currentlyLookingFor = null;
        this.resettingTemplates = false;
        this.resettingMaterials = false;
        this.exhaustedArmorTypes.clear();
    }

    @EventHandler
    private void onScreenOpened(OpenScreenEvent event) {
        if (event.screen instanceof SmithingScreen) {
            this.notified = false;
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null) {
            if (this.mc.getConnection() != null) {
                if (this.mc.screen == null) {
                    this.onDeactivate();
                } else if (this.mc.player.containerMenu instanceof SmithingMenu ss) {
                    switch ((AutoSmith.ModuleMode)this.moduleMode.get()) {
                        case Packet:
                            if (this.notified) {
                                return;
                            }

                            ArrayDeque<ServerboundContainerClickPacket> packetQueue = new ArrayDeque<>();
                            boolean exhausted = false;

                            while (!exhausted) {
                                ServerboundContainerClickPacket packet = this.generateSmithingPacket(ss);
                                if (packet == null) {
                                    exhausted = true;
                                } else if (packetQueue.size() >= this.packetLimit.get()) {
                                    exhausted = true;
                                    packetQueue.addLast(packet);
                                    MsgUtil.sendModuleMsg("Packet limit was hit§c..! §7You may need to run the module again§c...", this.name);
                                } else {
                                    packetQueue.addLast(packet);
                                }
                            }

                            if (this.debug.get()) {
                                MsgUtil.sendModuleMsg("Sending §e" + packetQueue.size() + " §7packets" + Utils.rCC() + "..!", this.name);
                            }

                            while (!packetQueue.isEmpty()) {
                                ((ClientConnectionAccessor)this.mc.getConnection().getConnection()).invokeSendImmediately(packetQueue.removeFirst(), null, true);
                            }

                            this.finished();
                            break;
                        case Interact:
                            if (this.timer < this.tickRate.get()) {
                                this.timer++;
                                return;
                            }

                            this.timer = 0;
                            if (this.resettingTemplates) {
                                InvUtils.shiftClick().slotId(0);
                                this.timer = this.tickRate.get() - 1;
                                this.resettingTemplates = false;
                                return;
                            }

                            if (this.resettingMaterials) {
                                InvUtils.shiftClick().slotId(2);
                                this.timer = this.tickRate.get() - 1;
                                this.resettingMaterials = false;
                                return;
                            }

                            switch ((AutoSmith.SmithingMode)this.operatingMode.get()) {
                                case Trim:
                                    this.handleTrimInteract(ss);
                                    break;
                                case Upgrade:
                                    this.handleUpgradeInteract(ss);
                            }
                    }
                }
            }
        }
    }

    private void handleTrimInteract(SmithingMenu ss) {
        ItemStack output = ss.getSlot(3).getItem();
        if (!output.isEmpty()) {
            AutoSmith.ArmorType armorType = this.getArmorType(output);
            if (armorType == null) {
                return;
            }

            if (output.has(DataComponents.TRIM)) {
                ArmorTrim trimData = output.get(DataComponents.TRIM);
                if (trimData == null) {
                    this.foundEquip = false;
                    InvUtils.shiftClick().slotId(1);
                    return;
                }

                String pattern = trimData.pattern().getRegisteredName();
                String material = trimData.material().getRegisteredName();

                AutoSmith.ArmorTrims expectedTrim = switch (armorType) {
                    case HELMET -> (AutoSmith.ArmorTrims)this.helmetTrim.get();
                    case CHESTPLATE -> (AutoSmith.ArmorTrims)this.chestplateTrim.get();
                    case LEGGINGS -> (AutoSmith.ArmorTrims)this.leggingsTrim.get();
                    case BOOTS -> (AutoSmith.ArmorTrims)this.bootsTrim.get();
                };

                AutoSmith.TrimMaterial expectedMaterial = switch (armorType) {
                    case HELMET -> (AutoSmith.TrimMaterial)this.helmetTrimMaterial.get();
                    case CHESTPLATE -> (AutoSmith.TrimMaterial)this.chestplateTrimMaterial.get();
                    case LEGGINGS -> (AutoSmith.TrimMaterial)this.leggingsTrimMaterial.get();
                    case BOOTS -> (AutoSmith.TrimMaterial)this.bootsTrimMaterial.get();
                };
                if (!expectedTrim.label.equals(pattern)) {
                    this.foundTemplates = false;
                    InvUtils.shiftClick().slotId(0);
                } else if (!expectedMaterial.label.equals(material)) {
                    this.foundIngots = false;
                    InvUtils.shiftClick().slotId(2);
                } else {
                    InvUtils.shiftClick().slotId(3);
                    this.foundEquip = false;
                    this.foundIngots = false;
                    this.foundTemplates = false;
                    if (ss.getSlot(0).getItem().getCount() >= 1) {
                        this.resettingTemplates = true;
                    }

                    if (ss.getSlot(2).getItem().getCount() >= 1) {
                        this.resettingMaterials = true;
                    }
                }
            } else {
                this.foundEquip = false;
                InvUtils.shiftClick().slotId(1);
                this.foundIngots = false;
                this.foundTemplates = false;
                this.resettingTemplates = true;
            }
        } else if (!this.foundEquip) {
            for (int n = 4; n < this.getInvSize() + 4; n++) {
                ItemStack stack = ss.getSlot(n).getItem();
                if (this.isValidEquipmentForTrimming(stack)) {
                    this.foundEquip = true;
                    InvUtils.shiftClick().slotId(n);
                    break;
                }
            }

            if (!this.foundEquip && !this.notified) {
                MsgUtil.sendModuleMsg("§2§oNo armor left to trim§8§o.", this.name);
                this.finished();
            }
        } else if (!this.foundIngots) {
            ItemStack armorToTrim = ss.getSlot(1).getItem();
            AutoSmith.ArmorType armorType = this.getArmorType(armorToTrim);
            if (armorType == null) {
                this.foundEquip = false;
                this.resettingTemplates = true;
                this.resettingMaterials = true;
                InvUtils.shiftClick().slotId(1);
                return;
            }

            Item neededMaterial = this.getNeededMaterialItem(armorToTrim);
            if (neededMaterial == null) {
                return;
            }

            for (int n = 4; n < this.getInvSize() + 4; n++) {
                ItemStack stack = ss.getSlot(n).getItem();
                if (stack.is(neededMaterial)) {
                    this.foundIngots = true;
                    InvUtils.shiftClick().slotId(n);
                    break;
                }
            }

            if (!this.foundIngots && !this.notified) {
                if (!this.exhaustedArmorTypes.contains(armorType)) {
                    this.exhaustedArmorTypes.add(armorType);
                    return;
                }

                MsgUtil.sendModuleMsg("§c§oNo valid trim materials left§8§o..!", this.name);
                this.finished();
            }
        } else if (!this.foundTemplates) {
            ItemStack armorToTrim = ss.getSlot(1).getItem();
            AutoSmith.ArmorType armorType = this.getArmorType(armorToTrim);
            if (armorType == null) {
                this.foundEquip = false;
                this.resettingTemplates = true;
                this.resettingMaterials = true;
                InvUtils.shiftClick().slotId(1);
                return;
            }

            Item neededPattern = this.getNeededPatternItem(armorToTrim);
            if (neededPattern == null) {
                return;
            }

            for (int n = 4; n < this.getInvSize() + 4; n++) {
                ItemStack stack = ss.getSlot(n).getItem();
                if (stack.getItem() == neededPattern) {
                    this.foundTemplates = true;
                    InvUtils.shiftClick().slotId(n);
                    break;
                }
            }

            if (!this.foundTemplates && !this.notified) {
                if (!this.exhaustedArmorTypes.contains(armorType)) {
                    this.exhaustedArmorTypes.add(armorType);
                    return;
                }

                MsgUtil.sendModuleMsg("No valid trim templates left§c§o..!", this.name);
                this.finished();
            }
        } else {
            this.timer = this.tickRate.get() - 1;
        }
    }

    private void handleUpgradeInteract(SmithingMenu ss) {
        ItemStack output = ss.getSlot(3).getItem();
        if (!output.isEmpty()) {
            InvUtils.shiftClick().slotId(3);
            this.foundEquip = false;
            int ingotsRemaining = ss.getSlot(2).getItem().getCount();
            int templatesRemaining = ss.getSlot(0).getItem().getCount();
            if (ingotsRemaining == 0) {
                this.foundIngots = false;
            }

            if (templatesRemaining == 0) {
                this.foundTemplates = false;
            }
        } else if (!this.foundEquip) {
            for (int n = 4; n < this.getInvSize() + 4; n++) {
                ItemStack stack = ss.getSlot(n).getItem();
                if (this.isValidEquipmentForUpgrading(stack)) {
                    this.foundEquip = true;
                    InvUtils.shiftClick().slotId(n);
                    break;
                }
            }

            if (!this.foundEquip && !this.notified) {
                MsgUtil.sendModuleMsg("No gear left to upgrade§c..!", this.name);
                this.finished();
            }
        } else if (!this.foundIngots) {
            for (int n = 4; n < this.getInvSize() + 4; n++) {
                ItemStack stack = ss.getSlot(n).getItem();
                if (stack.getItem() == Items.NETHERITE_INGOT) {
                    this.foundIngots = true;
                    InvUtils.shiftClick().slotId(n);
                    break;
                }
            }

            if (!this.foundIngots && !this.notified) {
                MsgUtil.sendModuleMsg("No netherite ingots left§c..!", this.name);
                this.finished();
            }
        } else if (!this.foundTemplates) {
            for (int n = 4; n < this.getInvSize() + 4; n++) {
                ItemStack stack = ss.getSlot(n).getItem();
                if (stack.getItem() == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) {
                    this.foundTemplates = true;
                    InvUtils.shiftClick().slotId(n);
                    break;
                }
            }

            if (!this.foundTemplates && !this.notified) {
                MsgUtil.sendModuleMsg("No netherite templates left§c..!", this.name);
                this.finished();
            }
        } else {
            this.timer = this.tickRate.get() - 1;
        }
    }

    private void finished() {
        if (this.mc.player == null) {
            this.notified = true;
        } else {
            if (!this.notified) {
                if (this.chatFeedback) {
                    MsgUtil.sendModuleMsg("Finished processing items" + Utils.rCC() + "..!", this.name);
                }

                if (this.pingOnDone.get()) {
                    this.mc
                        .player
                        .playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, this.pingVolume.get().floatValue(), ThreadLocalRandom.current().nextFloat(0.69F, 1.337F));
                }
            }

            this.notified = true;
            if (this.closeOnDone.get()) {
                this.mc.player.closeContainer();
            }

            if (this.disableOnDone.get()) {
                this.toggle();
            }
        }
    }

    @Nullable
    private ServerboundContainerClickPacket generateSmithingPacket(SmithingMenu handler) {
        if (this.mc.player == null) {
            return null;
        }

        Int2ObjectMap<HashedStack> changedSlots = new Int2ObjectOpenHashMap<>();
        if (this.trimStack != null && this.materialStack != null && this.equipmentStack != null) {
            ItemStack armorToTrim = this.equipmentStack;
            if (this.operatingMode.get().equals(AutoSmith.SmithingMode.Trim) && this.getArmorType(armorToTrim) == null) {
                LogUtil.error("Item in equipment slot was not armor§c..!", this.name);
                return null;
            }

            Item neededPattern;
            Item neededMaterial;
            if (this.operatingMode.get().equals(AutoSmith.SmithingMode.Trim)) {
                neededPattern = this.getNeededPatternItem(armorToTrim);
                neededMaterial = this.getNeededMaterialItem(armorToTrim);
            } else {
                neededMaterial = Items.NETHERITE_INGOT;
                neededPattern = Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
            }

            if (this.operatingMode.get().equals(AutoSmith.SmithingMode.Trim) && !this.trimStack.is(neededPattern)) {
                changedSlots.put(0, this.hashStack(ItemStack.EMPTY));
                int shiftClickTargetSlot = this.predictEmptySlot(handler);
                if (shiftClickTargetSlot == -1) {
                    return null;
                }

                changedSlots.put(shiftClickTargetSlot, this.hashStack(this.trimStack.copy()));
                this.trimStack = null;
                return new ServerboundContainerClickPacket(
                    handler.containerId,
                    handler.getStateId(),
                    Shorts.checkedCast(0L),
                    SignedBytes.checkedCast(0L),
                    ClickType.QUICK_MOVE,
                    changedSlots,
                    this.hashStack(ItemStack.EMPTY)
                );
            } else if (this.operatingMode.get().equals(AutoSmith.SmithingMode.Trim) && !this.materialStack.is(neededMaterial)) {
                changedSlots.put(2, this.hashStack(ItemStack.EMPTY));
                int shiftClickTargetSlot = this.predictEmptySlot(handler);
                if (shiftClickTargetSlot == -1) {
                    return null;
                }

                changedSlots.put(shiftClickTargetSlot, this.hashStack(this.materialStack.copy()));
                this.materialStack = null;
                return new ServerboundContainerClickPacket(
                    handler.containerId,
                    handler.getStateId(),
                    Shorts.checkedCast(2L),
                    SignedBytes.checkedCast(0L),
                    ClickType.QUICK_MOVE,
                    changedSlots,
                    this.hashStack(ItemStack.EMPTY)
                );
            } else {
                int trimCount = this.trimStack.getCount();
                int materialCount = this.materialStack.getCount();
                changedSlots.put(3, this.hashStack(ItemStack.EMPTY));
                changedSlots.put(1, this.hashStack(ItemStack.EMPTY));
                if (trimCount - 1 > 0) {
                    ItemStack newTrimStack = this.trimStack.copyWithCount(trimCount - 1);
                    changedSlots.put(0, this.hashStack(newTrimStack));
                    this.trimStack = newTrimStack;
                } else {
                    changedSlots.put(0, this.hashStack(ItemStack.EMPTY));
                    this.trimStack = null;
                }

                if (materialCount - 1 > 0) {
                    ItemStack newMaterialStack = this.materialStack.copyWithCount(materialCount - 1);
                    changedSlots.put(2, this.hashStack(newMaterialStack));
                    this.materialStack = newMaterialStack;
                } else {
                    changedSlots.put(2, this.hashStack(ItemStack.EMPTY));
                    this.materialStack = null;
                }

                int shiftClickTargetSlot = this.predictEmptySlot(handler);
                if (shiftClickTargetSlot == -1) {
                    return null;
                }

                if (this.operatingMode.get().equals(AutoSmith.SmithingMode.Trim)) {
                    ItemStack output = new ItemStack(
                        BuiltInRegistries.ITEM.wrapAsHolder(armorToTrim.getItem()), armorToTrim.getCount(), armorToTrim.getComponentsPatch()
                    );
                    changedSlots.put(shiftClickTargetSlot, this.hashStack(output));
                } else {
                    ItemStack output = this.getUpgradedItem(armorToTrim);
                    changedSlots.put(shiftClickTargetSlot, this.hashStack(output));
                }

                this.equipmentStack = null;
                return new ServerboundContainerClickPacket(
                    handler.containerId,
                    handler.getStateId(),
                    Shorts.checkedCast(3L),
                    SignedBytes.checkedCast(0L),
                    ClickType.QUICK_MOVE,
                    changedSlots,
                    this.hashStack(ItemStack.EMPTY)
                );
            }
        } else {
            if (this.equipmentStack != null) {
                if (this.materialStack == null) {
                    ItemStack armorToTrim = this.equipmentStack;
                    if (this.operatingMode.get().equals(AutoSmith.SmithingMode.Trim) && this.getArmorType(armorToTrim) == null) {
                        return null;
                    }

                    Item needed;
                    if (this.operatingMode.get().equals(AutoSmith.SmithingMode.Trim)) {
                        needed = this.getNeededMaterialItem(armorToTrim);
                    } else {
                        needed = Items.NETHERITE_INGOT;
                    }

                    for (int n = 4; n < this.getInvSize() + 4; n++) {
                        if (!this.processedSlots.contains(n)) {
                            ItemStack stack = handler.getSlot(n).getItem();
                            if (stack.is(needed)) {
                                this.materialStack = stack;
                                this.processedSlots.add(n);
                                this.projectedEmpty.add(n);
                                this.processedSlots.add(2);
                                changedSlots.put(2, this.hashStack(stack));
                                changedSlots.put(n, this.hashStack(ItemStack.EMPTY));
                                return new ServerboundContainerClickPacket(
                                    handler.containerId,
                                    handler.getStateId(),
                                    Shorts.checkedCast(n),
                                    SignedBytes.checkedCast(0L),
                                    ClickType.QUICK_MOVE,
                                    changedSlots,
                                    this.hashStack(ItemStack.EMPTY)
                                );
                            }
                        }
                    }
                } else {
                    ItemStack armorToTrim = this.equipmentStack;
                    if (this.operatingMode.get().equals(AutoSmith.SmithingMode.Trim) && this.getArmorType(armorToTrim) == null) {
                        return null;
                    }

                    Item needed;
                    if (this.operatingMode.get().equals(AutoSmith.SmithingMode.Trim)) {
                        needed = this.getNeededPatternItem(armorToTrim);
                    } else {
                        needed = Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
                    }

                    for (int n = 4; n < this.getInvSize() + 4; n++) {
                        if (!this.processedSlots.contains(n)) {
                            ItemStack stack = handler.getSlot(n).getItem();
                            if (stack.is(needed)) {
                                this.trimStack = stack;
                                this.processedSlots.add(n);
                                this.projectedEmpty.add(n);
                                this.processedSlots.add(0);
                                changedSlots.put(0, this.hashStack(stack));
                                changedSlots.put(n, this.hashStack(ItemStack.EMPTY));
                                return new ServerboundContainerClickPacket(
                                    handler.containerId,
                                    handler.getStateId(),
                                    Shorts.checkedCast(n),
                                    SignedBytes.checkedCast(0L),
                                    ClickType.QUICK_MOVE,
                                    changedSlots,
                                    this.hashStack(ItemStack.EMPTY)
                                );
                            }
                        }
                    }
                }
            } else {
                if (this.currentlyLookingFor == null && this.operatingMode.get().equals(AutoSmith.SmithingMode.Trim)) {
                    this.currentlyLookingFor = this.computeLookingFor();
                }

                for (int n = 4; n < this.getInvSize() + 4; n++) {
                    if (!this.processedSlots.contains(n)) {
                        ItemStack stack = handler.getSlot(n).getItem();
                        if (this.operatingMode.get().equals(AutoSmith.SmithingMode.Trim) && this.isValidEquipmentForTrimming(stack)
                            || this.operatingMode.get().equals(AutoSmith.SmithingMode.Upgrade) && this.isValidEquipmentForUpgrading(stack)) {
                            this.equipmentStack = stack;
                            this.processedSlots.add(n);
                            this.projectedEmpty.add(n);
                            this.processedSlots.add(1);
                            changedSlots.put(1, this.hashStack(stack));
                            changedSlots.put(n, this.hashStack(ItemStack.EMPTY));
                            return new ServerboundContainerClickPacket(
                                handler.containerId,
                                handler.getStateId(),
                                Shorts.checkedCast(n),
                                SignedBytes.checkedCast(0L),
                                ClickType.QUICK_MOVE,
                                changedSlots,
                                this.hashStack(ItemStack.EMPTY)
                            );
                        }
                    }
                }

                if (this.operatingMode.get().equals(AutoSmith.SmithingMode.Trim)) {
                    this.exhaustedArmorTypes.add(this.currentlyLookingFor);
                    this.currentlyLookingFor = null;
                    if (this.exhaustedArmorTypes.size() < 4) {
                        return this.generateSmithingPacket(handler);
                    }
                }
            }

            return null;
        }
    }

    private ItemStack getUpgradedItem(ItemStack original) {
        if (original.is(Items.DIAMOND_HELMET)) {
            return new ItemStack(Items.NETHERITE_HELMET.builtInRegistryHolder(), original.getCount(), original.getComponentsPatch());
        } else if (original.is(Items.DIAMOND_CHESTPLATE)) {
            return new ItemStack(Items.NETHERITE_CHESTPLATE.builtInRegistryHolder(), original.getCount(), original.getComponentsPatch());
        } else if (original.is(Items.DIAMOND_LEGGINGS)) {
            return new ItemStack(Items.NETHERITE_LEGGINGS.builtInRegistryHolder(), original.getCount(), original.getComponentsPatch());
        } else if (original.is(Items.DIAMOND_BOOTS)) {
            return new ItemStack(Items.NETHERITE_BOOTS.builtInRegistryHolder(), original.getCount(), original.getComponentsPatch());
        } else if (original.is(Items.DIAMOND_SWORD)) {
            return new ItemStack(Items.NETHERITE_SWORD.builtInRegistryHolder(), original.getCount(), original.getComponentsPatch());
        } else if (original.is(Items.DIAMOND_PICKAXE)) {
            return new ItemStack(Items.NETHERITE_PICKAXE.builtInRegistryHolder(), original.getCount(), original.getComponentsPatch());
        } else if (original.is(Items.DIAMOND_AXE)) {
            return new ItemStack(Items.NETHERITE_AXE.builtInRegistryHolder(), original.getCount(), original.getComponentsPatch());
        } else if (original.is(Items.DIAMOND_SHOVEL)) {
            return new ItemStack(Items.NETHERITE_SHOVEL.builtInRegistryHolder(), original.getCount(), original.getComponentsPatch());
        } else {
            return original.is(Items.DIAMOND_HOE)
                ? new ItemStack(Items.NETHERITE_HOE.builtInRegistryHolder(), original.getCount(), original.getComponentsPatch())
                : original;
        }
    }

    private AutoSmith.ArmorType computeLookingFor() {
        if (!this.exhaustedArmorTypes.contains(AutoSmith.ArmorType.HELMET)) {
            return AutoSmith.ArmorType.HELMET;
        } else if (!this.exhaustedArmorTypes.contains(AutoSmith.ArmorType.CHESTPLATE)) {
            return AutoSmith.ArmorType.CHESTPLATE;
        } else {
            return !this.exhaustedArmorTypes.contains(AutoSmith.ArmorType.LEGGINGS) ? AutoSmith.ArmorType.LEGGINGS : AutoSmith.ArmorType.BOOTS;
        }
    }

    private int predictEmptySlot(SmithingMenu handler) {
        if (this.mc.player == null) {
            return -1;
        }

        for (int n = this.getInvSize() + 3; n >= 4; n--) {
            if (!this.processedSlots.contains(n) || this.projectedEmpty.contains(n)) {
                if (this.projectedEmpty.contains(n)) {
                    this.projectedEmpty.rem(n);
                    return n;
                }

                if (handler.getSlot(n).getItem().isEmpty()) {
                    this.processedSlots.add(n);
                    return n;
                }
            }
        }

        return -1;
    }

    @Nullable
    private Item getNeededPatternItem(ItemStack armorToTrim) {
        AutoSmith.ArmorType armorType = this.getArmorType(armorToTrim);
        if (armorType == null) {
            return null;
        }

        AutoSmith.ArmorTrims trim = switch (armorType) {
            case HELMET -> (AutoSmith.ArmorTrims)this.helmetTrim.get();
            case CHESTPLATE -> (AutoSmith.ArmorTrims)this.chestplateTrim.get();
            case LEGGINGS -> (AutoSmith.ArmorTrims)this.leggingsTrim.get();
            case BOOTS -> (AutoSmith.ArmorTrims)this.bootsTrim.get();
        };
        return this.getTemplateItem(trim);
    }

    @Nullable
    private Item getNeededMaterialItem(ItemStack armorToTrim) {
        AutoSmith.ArmorType armorType = this.getArmorType(armorToTrim);
        if (armorType == null) {
            return null;
        }

        AutoSmith.TrimMaterial material = switch (armorType) {
            case HELMET -> (AutoSmith.TrimMaterial)this.helmetTrimMaterial.get();
            case CHESTPLATE -> (AutoSmith.TrimMaterial)this.chestplateTrimMaterial.get();
            case LEGGINGS -> (AutoSmith.TrimMaterial)this.leggingsTrimMaterial.get();
            case BOOTS -> (AutoSmith.TrimMaterial)this.bootsTrimMaterial.get();
        };
        return this.getMaterialItem(material);
    }

    public enum ArmorMaterials {
        Iron,
        Gold,
        Chain,
        Turtle,
        Leather,
        Diamond,
        Netherite;
    }

    public enum ArmorTrims {
        Eye("minecraft:eye"),
        Vex("minecraft:vex"),
        Rib("minecraft:rib"),
        Bolt("minecraft:bolt"),
        Wild("minecraft:wild"),
        Dune("minecraft:dune"),
        Host("minecraft:host"),
        Ward("minecraft:ward"),
        Tide("minecraft:tide"),
        Flow("minecraft:flow"),
        Coast("minecraft:coast"),
        Snout("minecraft:snout"),
        Spire("minecraft:spire"),
        Raiser("minecraft:raiser"),
        Shaper("minecraft:shaper"),
        Sentry("minecraft:sentry"),
        Silence("minecraft:silence"),
        Wayfinder("minecraft:wayfinder");

        public final String label;

        ArmorTrims(String label) {
            this.label = label;
        }
    }

    public enum ArmorType {
        HELMET(3),
        CHESTPLATE(2),
        LEGGINGS(1),
        BOOTS(0);

        public final int slotId;

        ArmorType(int slotId) {
            this.slotId = slotId;
        }

        public static AutoSmith.ArmorType fromSlotId(int id) {
            return switch (id) {
                case 0 -> BOOTS;
                case 1 -> LEGGINGS;
                case 2 -> CHESTPLATE;
                case 3 -> HELMET;
                default -> null;
            };
        }
    }

    public enum ModuleMode {
        Packet,
        Interact;
    }

    public enum SmithingMode {
        Trim,
        Upgrade;
    }

    public enum TrimMaterial {
        Iron("minecraft:iron"),
        Gold("minecraft:gold"),
        Lapis("minecraft:lapis"),
        Resin("minecraft:resin"),
        Copper("minecraft:copper"),
        Quartz("minecraft:quartz"),
        Emerald("minecraft:emerald"),
        Diamond("minecraft:diamond"),
        Redstone("minecraft:redstone"),
        Amethyst("minecraft:amethyst"),
        Netherite("minecraft:netherite");

        public final String label;

        TrimMaterial(String label) {
            this.label = label;
        }
    }
}
