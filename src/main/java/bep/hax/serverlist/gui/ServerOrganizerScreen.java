package bep.hax.serverlist.gui;

import bep.hax.serverlist.ServerFolder;
import bep.hax.serverlist.ServerMetadata;
import bep.hax.serverlist.ServerOrganizer;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

public class ServerOrganizerScreen extends WindowScreen {
    private ServerList serverList;

    public ServerOrganizerScreen(GuiTheme theme) {
        super(theme, "Server Organizer");
    }

    @Override
    public void initWidgets() {
        ServerOrganizer organizer = ServerOrganizer.get();
        this.serverList = new ServerList(MeteorClient.mc);
        this.serverList.load();
        WHorizontalList buttons = this.add(this.theme.horizontalList()).expandX().widget();
        WButton newFolder = buttons.add(this.theme.button("New Folder")).expandX().widget();
        newFolder.action = () -> MeteorClient.mc.setScreen(new EditFolderScreen(this.theme, null, this::reload));
        this.add(this.theme.horizontalSeparator()).expandX();
        WVerticalList content = this.add(this.theme.verticalList()).expandX().widget();

        for (ServerFolder folder : organizer.getRootFolders()) {
            this.addFolderSection(content, folder, 0);
        }

        List<String> unassignedServers = organizer.getServersInFolder(null);
        if (!unassignedServers.isEmpty()) {
            this.add(this.theme.horizontalSeparator("Unassigned Servers")).expandX();
            WTable unassignedTable = this.add(this.theme.table()).expandX().widget();

            for (String serverAddress : unassignedServers) {
                this.addServerRow(unassignedTable, serverAddress);
            }
        }

        this.add(this.theme.horizontalSeparator("All Servers")).expandX();
        this.add(this.theme.label("Click edit to set display name or assign to folder:")).expandX();
        if (this.serverList != null) {
            WTable allServersTable = this.add(this.theme.table()).expandX().widget();

            for (int i = 0; i < this.serverList.size(); i++) {
                ServerData server = this.serverList.get(i);
                this.addServerInfoRow(allServersTable, server);
            }
        }
    }

    private void addFolderSection(WVerticalList parent, ServerFolder folder, int depth) {
        ServerOrganizer organizer = ServerOrganizer.get();
        String indent = "  ".repeat(depth);
        WSection section = parent.add(this.theme.section(indent + folder.getName(), !folder.isCollapsed())).expandX().widget();
        WHorizontalList folderControls = section.add(this.theme.horizontalList()).expandX().widget();
        WButton editFolder = folderControls.add(this.theme.button(GuiRenderer.EDIT)).widget();
        editFolder.action = () -> MeteorClient.mc.setScreen(new EditFolderScreen(this.theme, folder, this::reload));
        editFolder.tooltip = "Edit Folder";
        WMinus deleteFolder = folderControls.add(this.theme.minus()).widget();
        deleteFolder.action = () -> {
            organizer.removeFolder(folder.getId());
            this.reload();
        };
        List<String> serversInFolder = organizer.getServersInFolder(folder.getId());
        if (!serversInFolder.isEmpty()) {
            WTable serverTable = section.add(this.theme.table()).expandX().widget();

            for (String serverAddress : serversInFolder) {
                this.addServerRow(serverTable, serverAddress);
            }
        }

        List<ServerFolder> children = organizer.getChildFolders(folder.getId());
        if (!children.isEmpty()) {
            WVerticalList childList = section.add(this.theme.verticalList()).expandX().widget();

            for (ServerFolder child : children) {
                this.addFolderSection(childList, child, depth + 1);
            }
        }
    }

    private void addServerRow(WTable table, String serverAddress) {
        ServerOrganizer organizer = ServerOrganizer.get();
        ServerMetadata meta = organizer.getMetadata(serverAddress);
        String displayName = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
        table.add(this.theme.label(serverAddress)).expandCellX();
        if (displayName != null) {
            table.add(this.theme.label("->")).widget().color = this.theme.textSecondaryColor();
            table.add(this.theme.label(displayName)).widget().color = this.theme.textColor();
        }

        WButton edit = table.add(this.theme.button(GuiRenderer.EDIT)).widget();
        edit.action = () -> MeteorClient.mc.setScreen(new EditServerMetadataScreen(this.theme, serverAddress, this::reload));
        edit.tooltip = "Edit";
        if (meta != null) {
            WMinus remove = table.add(this.theme.minus()).widget();
            remove.action = () -> {
                organizer.removeServerMetadata(serverAddress);
                this.reload();
            };
        }

        table.row();
    }

    private void addServerInfoRow(WTable table, ServerData server) {
        ServerOrganizer organizer = ServerOrganizer.get();
        ServerMetadata meta = organizer.getMetadata(server.ip);
        table.add(this.theme.label(server.name)).expandCellX();
        table.add(this.theme.label("(" + server.ip + ")")).widget().color = this.theme.textSecondaryColor();
        if (meta != null && meta.hasDisplayName()) {
            table.add(this.theme.label("->")).widget().color = this.theme.textSecondaryColor();
            table.add(this.theme.label(meta.getDisplayName())).widget().color = this.theme.textColor();
        }

        if (meta != null && meta.getFolderId() != null) {
            ServerFolder folder = organizer.getFolder(meta.getFolderId());
            if (folder != null) {
                table.add(this.theme.label("[" + folder.getName() + "]")).widget().color = this.theme.textSecondaryColor();
            }
        }

        WButton edit = table.add(this.theme.button(GuiRenderer.EDIT)).widget();
        edit.action = () -> MeteorClient.mc.setScreen(new EditServerMetadataScreen(this.theme, server.ip, this::reload));
        edit.tooltip = "Edit";
        table.row();
    }
}
