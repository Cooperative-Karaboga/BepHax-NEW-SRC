package bep.hax.serverlist.gui;

import bep.hax.serverlist.ServerFolder;
import bep.hax.serverlist.ServerOrganizer;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

public class EditFolderScreen extends WindowScreen {
    private final ServerFolder folder;
    private final boolean isNew;
    private final Runnable onClose;
    private WTextBox nameInput;
    private String selectedParentId = null;

    public EditFolderScreen(GuiTheme theme, ServerFolder folder, Runnable onClose) {
        this(theme, folder, null, onClose);
    }

    public EditFolderScreen(GuiTheme theme, ServerFolder folder, String initialParentId, Runnable onClose) {
        super(theme, folder == null ? "New Folder" : "Edit Folder");
        this.folder = folder;
        this.isNew = folder == null;
        this.onClose = onClose;
        if (folder != null) {
            this.selectedParentId = folder.getParentId();
        } else if (initialParentId != null) {
            this.selectedParentId = initialParentId;
        }
    }

    @Override
    public void initWidgets() {
        WTable table = this.add(this.theme.table()).expandX().minWidth(300.0).widget();
        table.add(this.theme.label("Name:"));
        this.nameInput = table.add(this.theme.textBox(this.isNew ? "" : this.folder.getName(), "Folder name")).minWidth(200.0).expandX().widget();
        table.row();
        table.add(this.theme.label("Parent Folder:"));
        List<String> parentOptions = new ArrayList<>();
        parentOptions.add("None (Root)");
        ServerOrganizer organizer = ServerOrganizer.get();
        List<ServerFolder> allFolders = organizer.getFolders();

        for (ServerFolder f : allFolders) {
            if (this.isNew || !f.getId().equals(this.folder.getId()) && !this.isChildOf(f.getId(), this.folder.getId())) {
                parentOptions.add(f.getName());
            }
        }

        String[] options = parentOptions.toArray(new String[0]);
        int currentIndex = 0;
        if (this.selectedParentId != null) {
            ServerFolder parentFolder = organizer.getFolder(this.selectedParentId);
            if (parentFolder != null) {
                for (int i = 1; i < parentOptions.size(); i++) {
                    if (parentOptions.get(i).equals(parentFolder.getName())) {
                        currentIndex = i;
                        break;
                    }
                }
            }
        }

        int[] selectedIndex = new int[]{currentIndex};
        WButton parentButton = table.add(this.theme.button(options[selectedIndex[0]])).minWidth(200.0).expandX().widget();
        parentButton.action = () -> {
            selectedIndex[0] = (selectedIndex[0] + 1) % options.length;
            parentButton.set(options[selectedIndex[0]]);
            if (selectedIndex[0] == 0) {
                this.selectedParentId = null;
            } else {
                String selectedName = options[selectedIndex[0]];

                for (ServerFolder f : allFolders) {
                    if (f.getName().equals(selectedName)) {
                        this.selectedParentId = f.getId();
                        break;
                    }
                }
            }
        };
        table.row();
        this.add(this.theme.horizontalSeparator()).expandX();
        WButton save = this.add(this.theme.button(this.isNew ? "Create" : "Save")).expandX().widget();
        save.action = () -> {
            String name = this.nameInput.get().trim();
            if (!name.isEmpty()) {
                if (this.isNew) {
                    ServerOrganizer.get().createFolder(name, this.selectedParentId);
                } else {
                    this.folder.setName(name);
                    this.folder.setParentId(this.selectedParentId);
                    ServerOrganizer.get().save();
                }

                this.onClose();
            }
        };
        this.enterAction = save.action;
    }

    private boolean isChildOf(String folderId, String potentialParentId) {
        ServerOrganizer organizer = ServerOrganizer.get();

        for (ServerFolder folder = organizer.getFolder(folderId);
            folder != null && folder.getParentId() != null;
            folder = organizer.getFolder(folder.getParentId())
        ) {
            if (folder.getParentId().equals(potentialParentId)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onClosed() {
        if (this.onClose != null) {
            this.onClose.run();
        }
    }
}
