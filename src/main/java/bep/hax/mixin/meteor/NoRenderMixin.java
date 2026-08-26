package bep.hax.mixin.meteor;

import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NoRender.class, remap = false)
public abstract class NoRenderMixin extends Module {
    @Unique
    private final SettingGroup sgCody = this.settings.createGroup("codysmile11");
    @Unique
    @Nullable
    private Setting<Boolean> codySigns = null;
    @Unique
    @Nullable
    private Setting<Boolean> codyPlayer = null;
    @Unique
    @Nullable
    private Setting<Boolean> codyBanners = null;

    public NoRenderMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Inject(
        method = "<init>",
        at = @At(
            value = "FIELD",
            target = "Lmeteordevelopment/meteorclient/systems/modules/render/NoRender;noSignText:Lmeteordevelopment/meteorclient/settings/Setting;"
        )
    )
    private void addNoRenderSettings(CallbackInfo ci) {
        this.codyPlayer = this.sgCody.add(new Builder().name("cody").description("Ignore his very existence.").defaultValue(true).build());
        this.codySigns = this.sgCody
            .add(new Builder().name("cody-signs").description("Don't render signs which contain the text \"codysmile11\".").defaultValue(true).build());
        this.codyBanners = this.sgCody
            .add(new Builder().name("cody-banners").description("Don't render banners which contain \"codysmile11\" in the name.").defaultValue(true).build());
    }
}
