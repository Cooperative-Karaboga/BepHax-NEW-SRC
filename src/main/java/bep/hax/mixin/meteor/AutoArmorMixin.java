package bep.hax.mixin.meteor;

import bep.hax.mixin.meteor.accessor.SettingAccessor;
import bep.hax.util.AutoArmorBepMode;
import java.util.Set;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.AutoArmor;
import meteordevelopment.meteorclient.systems.modules.combat.AutoArmor.Protection;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutoArmor.class, remap = false)
public abstract class AutoArmorMixin extends Module {
    @Shadow
    @Final
    private SettingGroup sgGeneral;
    @Shadow
    @Final
    private Setting<Protection> preferredProtection;
    @Shadow
    @Final
    private Setting<Integer> delay;
    @Shadow
    @Final
    private Setting<Set<ResourceKey<Enchantment>>> avoidedEnchantments;
    @Shadow
    @Final
    private Setting<Boolean> blastLeggings;
    @Shadow
    @Final
    private Setting<Boolean> antiBreak;
    @Shadow
    @Final
    private Setting<Boolean> ignoreElytra;
    @Unique
    private Setting<AutoArmorBepMode> bephax$mode;
    @Unique
    private Setting<Double> bephax$durabilityThreshold;
    @Unique
    private Setting<Integer> bephax$swapCooldown;
    @Unique
    private int bephax$cooldownTicks = 0;
    @Unique
    private int bephax$lastSwappedSlot = -1;

    public AutoArmorMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bephax$addModeSettings(CallbackInfo ci) {
        this.bephax$mode = this.sgGeneral
            .add(
                ((Builder)((Builder)((Builder)new Builder().name("mode"))
                            .description("Normal: default AutoArmor. Mending: swap armor/elytra when durability threshold is reached to repair with mending."))
                        .defaultValue(AutoArmorBepMode.Normal))
                    .build()
            );
        this.bephax$durabilityThreshold = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("durability-threshold")
                    .description("Durability percentage at which to swap to a lower durability piece for mending.")
                    .defaultValue(100.0)
                    .min(50.0)
                    .max(100.0)
                    .sliderRange(50.0, 100.0)
                    .visible(() -> this.bephax$mode.get() == AutoArmorBepMode.Mending)
                    .build()
            );
        this.bephax$swapCooldown = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("swap-cooldown")
                    .description("Ticks to wait between armor swaps.")
                    .defaultValue(10)
                    .min(1)
                    .max(40)
                    .sliderRange(1, 20)
                    .visible(() -> this.bephax$mode.get() == AutoArmorBepMode.Mending)
                    .build()
            );
        ((SettingAccessor)this.preferredProtection).setVisible(() -> this.bephax$mode.get() == AutoArmorBepMode.Normal);
        ((SettingAccessor)this.delay).setVisible(() -> this.bephax$mode.get() == AutoArmorBepMode.Normal);
        ((SettingAccessor)this.avoidedEnchantments).setVisible(() -> this.bephax$mode.get() == AutoArmorBepMode.Normal);
        ((SettingAccessor)this.blastLeggings).setVisible(() -> this.bephax$mode.get() == AutoArmorBepMode.Normal);
        ((SettingAccessor)this.antiBreak).setVisible(() -> this.bephax$mode.get() == AutoArmorBepMode.Normal);
        ((SettingAccessor)this.ignoreElytra).setVisible(() -> this.bephax$mode.get() == AutoArmorBepMode.Normal);
    }

    @Inject(method = "onActivate", at = @At("TAIL"))
    private void bephax$onActivate(CallbackInfo ci) {
        this.bephax$cooldownTicks = 0;
        this.bephax$lastSwappedSlot = -1;
    }

    @Inject(method = "onPreTick", at = @At("HEAD"), cancellable = true)
    private void bephax$onTick(CallbackInfo ci) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.bephax$mode != null && this.bephax$mode.get() == AutoArmorBepMode.Mending) {
                ci.cancel();
                if (this.bephax$cooldownTicks > 0) {
                    this.bephax$cooldownTicks--;
                } else {
                    EquipmentSlot[] armorSlots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

                    for (EquipmentSlot slot : armorSlots) {
                        ItemStack equipped = this.mc.player.getItemBySlot(slot);
                        if (equipped.isEmpty()) {
                            int bestSlot = this.bephax$findLowestDurabilitySlot(slot);
                            if (bestSlot != -1) {
                                this.bephax$swapArmorPiece(bestSlot, slot);
                                return;
                            }
                        } else if (Utils.hasEnchantment(equipped, Enchantments.MENDING)) {
                            double durabilityPercent = this.bephax$getDurabilityPercent(equipped);
                            if (durabilityPercent >= this.bephax$durabilityThreshold.get()) {
                                int bestSlot = this.bephax$findLowestDurabilitySlot(slot);
                                if (bestSlot != -1) {
                                    ItemStack candidate = this.mc.player.getInventory().getItem(bestSlot);
                                    double candidateDurability = this.bephax$getDurabilityPercent(candidate);
                                    if (candidateDurability < durabilityPercent) {
                                        this.bephax$swapArmorPiece(bestSlot, slot);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Unique
    private double bephax$getDurabilityPercent(ItemStack stack) {
        if (!stack.isEmpty() && stack.isDamageableItem()) {
            int maxDamage = stack.getMaxDamage();
            if (maxDamage == 0) {
                return 100.0;
            }

            int currentDamage = stack.getDamageValue();
            return (double)(maxDamage - currentDamage) / maxDamage * 100.0;
        } else {
            return 100.0;
        }
    }

    @Unique
    private int bephax$findLowestDurabilitySlot(EquipmentSlot targetSlot) {
        if (this.mc.player == null) {
            return -1;
        }

        int bestSlot = -1;
        double lowestDurability = 100.0;
        int targetSlotId = targetSlot.getIndex();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                int itemSlotId = this.bephax$getItemSlotId(stack);
                if (itemSlotId == targetSlotId && Utils.hasEnchantment(stack, Enchantments.MENDING) && stack.getDamageValue() != 0) {
                    double durability = this.bephax$getDurabilityPercent(stack);
                    if (durability < lowestDurability) {
                        lowestDurability = durability;
                        bestSlot = i;
                    }
                }
            }
        }

        return bestSlot;
    }

    @Unique
    private int bephax$getItemSlotId(ItemStack stack) {
        if (stack.has(DataComponents.GLIDER)) {
            return 2;
        } else {
            return stack.has(DataComponents.EQUIPPABLE) ? stack.get(DataComponents.EQUIPPABLE).slot().getIndex() : -1;
        }
    }

    @Unique
    private void bephax$swapArmorPiece(int inventorySlot, EquipmentSlot armorSlot) {
        int armorIndex = switch (armorSlot) {
            case FEET -> 0;
            case LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
            default -> -1;
        };
        if (armorIndex != -1) {
            if (inventorySlot == this.bephax$lastSwappedSlot) {
                this.bephax$cooldownTicks = this.bephax$swapCooldown.get() * 2;
            } else {
                InvUtils.move().from(inventorySlot).toArmor(armorIndex);
                this.bephax$lastSwappedSlot = inventorySlot;
                this.bephax$cooldownTicks = this.bephax$swapCooldown.get();
            }
        }
    }
}
