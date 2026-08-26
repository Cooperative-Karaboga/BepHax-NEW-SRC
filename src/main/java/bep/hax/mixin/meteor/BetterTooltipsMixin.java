package bep.hax.mixin.meteor;

import bep.hax.util.PeekNavigationConfig;
import meteordevelopment.meteorclient.events.game.ItemStackTooltipEvent;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.render.BetterTooltips;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BetterTooltips.class, remap = false)
public class BetterTooltipsMixin extends Module {
    @Shadow
    @Final
    private SettingGroup sgOther;
    @Unique
    @Nullable
    private Setting<Boolean> rawDamageTag = null;
    @Unique
    @Nullable
    private Setting<Boolean> trueDurability = null;

    public BetterTooltipsMixin(Category category, String name, String description, String... aliases) {
        super(category, name, description, aliases);
    }

    @Inject(
        method = "<init>",
        at = @At(
            value = "FIELD",
            target = "Lmeteordevelopment/meteorclient/systems/modules/render/BetterTooltips;statusEffects:Lmeteordevelopment/meteorclient/settings/Setting;"
        )
    )
    private void addTrueDurabilitySetting(CallbackInfo ci) {
        this.trueDurability = this.sgOther
            .add(new Builder().name("true-durability").description("Show the raw damage value of an item.").defaultValue(false).build());
        this.rawDamageTag = this.sgOther
            .add(new Builder().name("raw-damage-tag").description("Show the raw Damage tag of an item.").defaultValue(false).build());
        this.sgOther
            .add(
                new Builder()
                    .name("navigate-back")
                    .description("Pressing Escape in a nested container preview goes back to the previous container instead of closing everything.")
                    .defaultValue(true)
                    .onChanged(PeekNavigationConfig::setEnabled)
                    .build()
            );
    }

    @Inject(method = "appendTooltip", at = @At("TAIL"))
    private void appendDurabilityTooltip(ItemStackTooltipEvent event, CallbackInfo ci) {
        if (event.itemStack().isDamageableItem()) {
            int maxDamage = event.itemStack().getMaxDamage();
            int damage = event.itemStack().getOrDefault(DataComponents.DAMAGE, event.itemStack().getDamageValue());
            if (this.rawDamageTag != null && this.rawDamageTag.get()) {
                event.appendEnd(Component.literal("§7Damage§3: §a§o" + damage + " §8[§7Max§3: §a§o" + maxDamage + "§8]"));
            }

            if (this.trueDurability != null && this.trueDurability.get()) {
                int durability = maxDamage - damage;
                event.appendEnd(Component.literal("§7Durability§3: §a§o" + durability + " §8[§7Max§3: §a§o" + maxDamage + "§8]"));
            }
        }
    }
}
