package bep.hax.serverlist.gui;

import bep.hax.serverlist.ServerFolder;
import java.util.List;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.Entry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BepServerListWidgetAccessor {
    @Nullable
    default ServerFolder bep$getCurrentFolder() {
        return null;
    }

    @NotNull
    default List<Entry> bep$getCurrentEntries() {
        return List.of();
    }

    default void bep$setCurrentFolder(@Nullable ServerFolder folder) {
    }

    default void bep$updateEntries() {
    }

    default void bep$swapEntries(int i, int j) {
    }

    default int bep$findFolderIndex(String folderId) {
        List<Entry> entries = this.bep$getCurrentEntries();

        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof BepFolderEntry folderEntry && folderEntry.getFolder().getId().equals(folderId)) {
                return i;
            }
        }

        return -1;
    }
}
