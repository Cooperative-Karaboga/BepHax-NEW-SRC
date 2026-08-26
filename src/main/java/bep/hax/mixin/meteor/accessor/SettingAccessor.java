package bep.hax.mixin.meteor.accessor;

import java.util.function.Consumer;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Setting.class, remap = false)
public interface SettingAccessor {
    @Mutable
    @Accessor("visible")
    void setVisible(IVisible var1);

    @Mutable
    @Accessor("onChanged")
    <T> void setOnChanged(Consumer<T> var1);

    @Mutable
    @Accessor("description")
    void setDescription(String var1);
}
