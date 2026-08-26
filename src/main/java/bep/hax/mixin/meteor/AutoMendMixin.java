package bep.hax.mixin.meteor;

import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.MsgUtil;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.systems.modules.combat.Offhand;
import meteordevelopment.meteorclient.systems.modules.player.AutoMend;
import meteordevelopment.meteorclient.systems.modules.player.EXPThrower;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutoMend.class, remap = false)
public abstract class AutoMendMixin extends Module {
    @Shadow
    private boolean didMove;
    @Shadow
    @Final
    private SettingGroup sgGeneral;
    @Shadow
    @Final
    private Setting<Boolean> autoDisable;
    @Unique
    private boolean notified = false;
    @Unique
    private boolean didWearMending = false;
    @Unique
    @Nullable
    private Setting<Boolean> auto = null;
    @Unique
    @Nullable
    private Setting<Boolean> wearMendElytras = null;
    @Unique
    @Nullable
    private Setting<Boolean> mendElytrasOnly = null;
    @Unique
    @Nullable
    private Setting<Boolean> ignoreOffhand = null;
    @Unique
    @Nullable
    private Setting<Boolean> disableOffhandModules = null;
    @Unique
    @Nullable
    private Setting<Integer> repairThreshold = null;
    @Unique
    private boolean wasAutoTotemActive = false;
    @Unique
    private boolean wasOffhandActive = false;
    @Unique
    private int timer = 0;

    public AutoMendMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Shadow
    protected abstract int getEmptySlot();

    @Unique
    private boolean needsRepair(ItemStack stack) {
        if (stack.getDamageValue() <= 0) {
            return false;
        } else if (this.repairThreshold != null && this.repairThreshold.get() > 0) {
            double damagePercent = (double)stack.getDamageValue() / stack.getMaxDamage() * 100.0;
            return damagePercent > this.repairThreshold.get().intValue();
        } else {
            return true;
        }
    }

    @Unique
    private void replaceElytra() {
        if (this.mc.player != null) {
            for (int n = 0; n < ((PlayerInventoryAccessor)this.mc.player.getInventory()).getMain().size(); n++) {
                ItemStack stack = this.mc.player.getInventory().getItem(n);
                if (stack.getItem() == Items.ELYTRA && Utils.hasEnchantment(stack, Enchantments.MENDING) && this.needsRepair(stack)) {
                    InvUtils.move().from(n).toArmor(2);
                    this.didWearMending = true;
                    return;
                }
            }

            if (!this.notified) {
                if (this.mendElytrasOnly != null
                    && this.mendElytrasOnly.get()
                    && this.ignoreOffhand != null
                    && this.ignoreOffhand.get()
                    && this.autoDisable.get()
                    && this.getDamagedElytraSlot() == -1) {
                    this.toggle();
                    this.sendToggledMsg();
                    if (this.didWearMending) {
                        MsgUtil.sendModuleMsg("Repaired all elytras, disabling§a..§e!", this.name);
                    } else {
                        MsgUtil.sendModuleMsg("No repairable elytras left in inventory, disabling§3..§5!", this.name);
                    }
                }

                this.notified = true;
            }
        }
    }

    @Unique
    private int getDamagedElytraSlot() {
        if (this.mc.player == null) {
            return -1;
        }

        for (int n = 0; n < ((PlayerInventoryAccessor)this.mc.player.getInventory()).getMain().size(); n++) {
            ItemStack stack = this.mc.player.getInventory().getItem(n);
            if (stack.getItem() == Items.ELYTRA && Utils.hasEnchantment(stack, Enchantments.MENDING) && this.needsRepair(stack)) {
                return n;
            }
        }

        return -1;
    }

    @Inject(
        method = "<init>",
        at = @At(
            value = "FIELD",
            target = "Lmeteordevelopment/meteorclient/systems/modules/player/AutoMend;autoDisable:Lmeteordevelopment/meteorclient/settings/Setting;"
        )
    )
    private void addElytraMendSettings(CallbackInfo ci) {
        this.wearMendElytras = this.sgGeneral
            .add(new Builder().name("wear-mend-elytras").description("Wear damaged mending elytras to mend them more efficiently.").defaultValue(false).build());
        this.mendElytrasOnly = this.sgGeneral
            .add(new Builder().name("mend-elytras-only").description("Only mend elytras, ignore other items.").defaultValue(false).build());
        this.auto = this.sgGeneral
            .add(new Builder().name("auto-use-xp").description("Uses ExpThrower to automatically throw bottles for you.").defaultValue(false).build());
        this.ignoreOffhand = this.sgGeneral
            .add(
                new Builder()
                    .name("ignore-offhand")
                    .description("Do not swap items into offhand for mending.")
                    .defaultValue(false)
                    .visible(() -> this.mendElytrasOnly != null && this.mendElytrasOnly.get())
                    .build()
            );
        this.disableOffhandModules = this.sgGeneral
            .add(
                new Builder()
                    .name("disable-offhand-modules")
                    .description("Temporarily disables AutoTotem and Offhand while mending, re-enables them when done.")
                    .defaultValue(false)
                    .build()
            );
        this.repairThreshold = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("repair-threshold")
                    .description("Stop mending when remaining damage is at or below this percentage. Increase on laggy servers to waste less XP.")
                    .defaultValue(0)
                    .range(0, 50)
                    .sliderRange(0, 50)
                    .build()
            );
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void hijackOnTick(CallbackInfo ci) {
        if (this.mc.player != null) {
            if (this.wearMendElytras != null && this.wearMendElytras.get()) {
                ItemStack chest = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
                if (chest.isEmpty()
                    || chest.getItem() != Items.ELYTRA
                    || !Utils.hasEnchantment(chest, Enchantments.MENDING)
                    || !this.needsRepair(chest)) {
                    if (this.auto != null && this.auto.get() && Modules.get().isActive(EXPThrower.class)) {
                        Modules.get().get(EXPThrower.class).toggle();
                    }

                    this.replaceElytra();
                }

                if (this.auto != null && this.auto.get() && !Modules.get().isActive(EXPThrower.class)) {
                    this.timer++;
                    if (this.timer >= 20) {
                        this.timer = 0;
                        Modules.get().get(EXPThrower.class).toggle();
                    }
                }

                if (this.ignoreOffhand != null && this.ignoreOffhand.get()) {
                    ci.cancel();
                } else if (this.mendElytrasOnly != null && this.mendElytrasOnly.get()) {
                    ci.cancel();
                    ItemStack offhand = this.mc.player.getOffhandItem();
                    if (offhand.isEmpty() || !Utils.hasEnchantment(offhand, Enchantments.MENDING) || !this.needsRepair(offhand)) {
                        int slot = this.getDamagedElytraSlot();
                        if (slot == -1) {
                            if (this.autoDisable.get()) {
                                if (this.didMove) {
                                    MsgUtil.sendModuleMsg("Repaired all elytras, disabling§a..§e!", this.name);
                                    int emptySlot = this.getEmptySlot();
                                    if (emptySlot != -1) {
                                        InvUtils.move().fromOffhand().to(emptySlot);
                                    }
                                } else {
                                    MsgUtil.sendModuleMsg("No repairable elytras left in inventory, disabling§3..§5!", this.name);
                                }

                                this.toggle();
                                this.sendToggledMsg();
                            }
                        } else {
                            InvUtils.move().from(slot).toOffhand();
                            this.didMove = true;
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "onActivate", at = @At("TAIL"))
    private void resetNotified(CallbackInfo ci) {
        this.notified = false;
        this.didWearMending = false;
        if (this.disableOffhandModules != null && this.disableOffhandModules.get()) {
            AutoTotem autoTotem = Modules.get().get(AutoTotem.class);
            Offhand offhand = Modules.get().get(Offhand.class);
            this.wasAutoTotemActive = autoTotem.isActive();
            this.wasOffhandActive = offhand.isActive();
            if (this.wasAutoTotemActive) {
                autoTotem.toggle();
            }

            if (this.wasOffhandActive) {
                offhand.toggle();
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (this.disableOffhandModules != null && this.disableOffhandModules.get()) {
            if (this.wasAutoTotemActive) {
                Modules.get().get(AutoTotem.class).toggle();
                this.wasAutoTotemActive = false;
            }

            if (this.wasOffhandActive) {
                Modules.get().get(Offhand.class).toggle();
                this.wasOffhandActive = false;
            }
        }
    }
}
