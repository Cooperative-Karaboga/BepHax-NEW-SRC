package bep.hax.mixin.accessor;

import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(JoinMultiplayerScreen.class)
public interface MultiplayerScreenAccessor {
    @Accessor("serverSelectionList")
    ServerSelectionList getServerListWidget();

    @Accessor("servers")
    ServerList getServerList();
}
