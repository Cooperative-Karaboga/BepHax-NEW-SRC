package bep.hax.mixin.accessor;

import java.util.List;
import net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookAccess.class)
public interface BookScreenContentsAccessor {
    @Accessor("pages")
    List<Component> getPages();

    @Mutable
    @Accessor("pages")
    void setPages(List<Component> var1);
}
