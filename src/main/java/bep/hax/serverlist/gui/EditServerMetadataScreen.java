package bep.hax.serverlist.gui;

import bep.hax.serverlist.ServerFolder;
import bep.hax.serverlist.ServerMetadata;
import bep.hax.serverlist.ServerOrganizer;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

public class EditServerMetadataScreen extends WindowScreen {
    private final String serverAddress;
    private final Runnable onClose;
    private WTextBox displayNameInput;
    private String selectedFolderId = null;

    public EditServerMetadataScreen(GuiTheme theme, String serverAddress, Runnable onClose) {
        super(theme, "Edit Server: " + serverAddress);
        this.serverAddress = serverAddress;
        this.onClose = onClose;
        ServerMetadata meta = ServerOrganizer.get().getMetadata(serverAddress);
        if (meta != null) {
            this.selectedFolderId = meta.getFolderId();
        }
    }

    @Override
    public void initWidgets() {
        ServerOrganizer organizer = ServerOrganizer.get();
        ServerMetadata meta = organizer.getMetadata(this.serverAddress);
        WTable table = this.add(this.theme.table()).expandX().minWidth(350.0).widget();
        table.add(this.theme.label("Server Address:"));
        table.add(this.theme.label(this.serverAddress)).widget().color = this.theme.textSecondaryColor();
        table.row();
        this.add(this.theme.horizontalSeparator()).expandX();
        WTable editTable = this.add(this.theme.table()).expandX().widget();
        editTable.add(this.theme.label("Display Name:"));
        String currentDisplayName = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        this.displayNameInput = editTable.add(this.theme.textBox(currentDisplayName, "Custom display name")).minWidth(200.0).expandX().widget();
        editTable.row();
        editTable.add(this.theme.label(""));
        WButton clearName = editTable.add(this.theme.button("Clear Display Name")).expandX().widget();
        clearName.action = () -> this.displayNameInput.set("");
        editTable.row();
        editTable.add(this.theme.label("Folder:"));
        List<String> folderOptions = new ArrayList<>();
        folderOptions.add("None");
        List<ServerFolder> allFolders = organizer.getFolders();

        for (ServerFolder f : allFolders) {
            String path = organizer.getFolderPath(f.getId());
            folderOptions.add(path.isEmpty() ? f.getName() : path);
        }

        String[] options = folderOptions.toArray(new String[0]);
        int currentIndex = 0;
        if (this.selectedFolderId != null) {
            ServerFolder currentFolder = organizer.getFolder(this.selectedFolderId);
            if (currentFolder != null) {
                String currentPath = organizer.getFolderPath(currentFolder.getId());
                String searchName = currentPath.isEmpty() ? currentFolder.getName() : currentPath;

                for (int i = 1; i < folderOptions.size(); i++) {
                    if (folderOptions.get(i).equals(searchName)) {
                        currentIndex = i;
                        break;
                    }
                }
            }
        }

        int[] selectedIndex = new int[]{currentIndex};
        WButton folderButton = editTable.add(this.theme.button(options[selectedIndex[0]])).minWidth(200.0).expandX().widget();
        folderButton.action = () -> {
            selectedIndex[0] = (selectedIndex[0] + 1) % options.length;
            folderButton.set(options[selectedIndex[0]]);
            if (selectedIndex[0] == 0) {
                this.selectedFolderId = null;
            } else {
                String selectedOption = options[selectedIndex[0]];

                for (ServerFolder f : allFolders) {
                    String path = organizer.getFolderPath(f.getId());
                    String folderDisplay = path.isEmpty() ? f.getName() : path;
                    if (folderDisplay.equals(selectedOption)) {
                        this.selectedFolderId = f.getId();
                        break;
                    }
                }
            }
        };
        editTable.row();
        this.add(this.theme.horizontalSeparator()).expandX();
        WButton save = this.add(this.theme.button("Save")).expandX().widget();
        save.action = this::saveAndClose;
        this.enterAction = save.action;
    }

    private void saveAndClose() {
        String displayName = this.displayNameInput.get().trim();
        ServerOrganizer organizer = ServerOrganizer.get();
        organizer.setDisplayName(this.serverAddress, displayName.isEmpty() ? null : displayName);
        organizer.setServerFolder(this.serverAddress, this.selectedFolderId);
        this.onClose();
    }

    @Override
    protected void onClosed() {
        if (this.onClose != null) {
            this.onClose.run();
        }
    }
}
