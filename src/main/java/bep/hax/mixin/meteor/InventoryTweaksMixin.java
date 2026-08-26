package bep.hax.mixin.meteor;

import bep.hax.util.InventoryTweaksConfigHolder;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.misc.InventoryTweaks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InventoryTweaks.class, remap = false)
public class InventoryTweaksMixin {
    @Unique
    private SettingGroup bephax$sgBephax;
    @Unique
    private Setting<Boolean> bephax$hoverEffect;
    @Unique
    private Setting<Double> bephax$hoverEffectScale;
    @Unique
    private Setting<Boolean> bephax$fade;
    @Unique
    private Setting<Double> bephax$fadeDuration;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bephax$addSettings(CallbackInfo ci) {
        Settings settings = ((Module)(Object)this).settings;
        this.bephax$sgBephax = settings.createGroup("BepHax");
        this.bephax$hoverEffect = this.bephax$sgBephax
            .add(
                new Builder()
                    .name("hover-effect")
                    .description("Pops the item under your cursor up in size with a soft glow when hovering slots in containers and inventories.")
                    .defaultValue(false)
                    .build()
            );
        InventoryTweaksConfigHolder.setHoverEffectSetting(this.bephax$hoverEffect);
        this.bephax$hoverEffectScale = this.bephax$sgBephax
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("hover-effect-scale")
                    .description("How much the hovered item grows.")
                    .defaultValue(1.4)
                    .min(1.0)
                    .sliderRange(1.0, 2.5)
                    .visible(() -> this.bephax$hoverEffect.get())
                    .build()
            );
        InventoryTweaksConfigHolder.setHoverEffectScaleSetting(this.bephax$hoverEffectScale);
        this.bephax$fade = this.bephax$sgBephax
            .add(
                new Builder()
                    .name("container-fade")
                    .description(
                        "Smoothly fades container screens in (from black) when they open. Drawn as an overlay only, so it never blocks input or delays closing."
                    )
                    .defaultValue(false)
                    .build()
            );
        InventoryTweaksConfigHolder.setFadeSetting(this.bephax$fade);
        this.bephax$fadeDuration = this.bephax$sgBephax
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("container-fade-duration")
                    .description("How long the fade-in takes, in seconds.")
                    .defaultValue(0.15)
                    .min(0.0)
                    .sliderRange(0.0, 1.0)
                    .visible(() -> this.bephax$fade.get())
                    .build()
            );
        InventoryTweaksConfigHolder.setFadeDurationSetting(this.bephax$fadeDuration);
    }
}
