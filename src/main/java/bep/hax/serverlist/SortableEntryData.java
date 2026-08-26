package bep.hax.serverlist;

import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.Entry;

public class SortableEntryData {
    public Entry entry;
    public int sortOrder;
    public String id;

    public SortableEntryData(Entry entry, int sortOrder, String id) {
        this.entry = entry;
        this.sortOrder = sortOrder;
        this.id = id;
    }
}
