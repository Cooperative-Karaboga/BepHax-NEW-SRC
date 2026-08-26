package bep.hax.serverlist.gui;

import bep.hax.mixin.accessor.MultiplayerScreenAccessor;
import bep.hax.serverlist.ServerFolder;
import bep.hax.serverlist.ServerOrganizer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.Entry;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class BepFolderEntry extends Entry {
    private static final Identifier JOIN_TEXTURE = Identifier.parse("server_list/join");
    private static final Identifier JOIN_HIGHLIGHTED_TEXTURE = Identifier.parse("server_list/join_highlighted");
    private static final Identifier MOVE_UP_TEXTURE = Identifier.parse("server_list/move_up");
    private static final Identifier MOVE_UP_HIGHLIGHTED_TEXTURE = Identifier.parse("server_list/move_up_highlighted");
    private static final Identifier MOVE_DOWN_TEXTURE = Identifier.parse("server_list/move_down");
    private static final Identifier MOVE_DOWN_HIGHLIGHTED_TEXTURE = Identifier.parse("server_list/move_down_highlighted");
    private final Minecraft client;
    private final JoinMultiplayerScreen screen;
    private final ServerFolder folder;
    @NotNull
    private final List<Entry> entries = new ArrayList<>();

    public BepFolderEntry(JoinMultiplayerScreen screen, ServerFolder folder) {
        this.client = Minecraft.getInstance();
        this.screen = screen;
        this.folder = folder;
    }

    public ServerFolder getFolder() {
        return this.folder;
    }

    public void addEntry(Entry entry) {
        this.entries.add(entry);
    }

    public void clearEntries() {
        this.entries.clear();
    }

    public int getTotalServerCount() {
        ServerOrganizer organizer = ServerOrganizer.get();
        return organizer == null ? this.entries.size() : this.countServersRecursive(organizer, this.folder.getId());
    }

    private int countServersRecursive(ServerOrganizer organizer, String folderId) {
        int count = organizer.getServersInFolder(folderId).size();

        for (ServerFolder child : organizer.getChildFolders(folderId)) {
            count += this.countServersRecursive(organizer, child.getId());
        }

        return count;
    }

    public int getSubfolderCount() {
        ServerOrganizer organizer = ServerOrganizer.get();
        return organizer == null ? 0 : organizer.getChildFolders(this.folder.getId()).size();
    }

    @Override
    protected boolean matches(Entry entry) {
        return entry instanceof BepFolderEntry;
    }

    @Override
    public void join() {
        this.navigateInto();
    }

    public void navigateInto() {
        if (this.screen instanceof MultiplayerScreenAccessor accessor && accessor.getServerListWidget() instanceof BepServerListWidgetAccessor widgetAccessor) {
            widgetAccessor.bep$setCurrentFolder(this.folder);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.hasShiftDown()) {
            if (this.screen instanceof MultiplayerScreenAccessor accessor
                && accessor.getServerListWidget() instanceof BepServerListWidgetAccessor widgetAccessor) {
                int index = widgetAccessor.bep$findFolderIndex(this.folder.getId());
                if (index == -1) {
                    return true;
                }

                int size = widgetAccessor.bep$getCurrentEntries().size();
                if (input.key() == 264 && index < size - 1) {
                    widgetAccessor.bep$swapEntries(index, index + 1);
                    return true;
                }

                if (input.key() == 265 && index > 0) {
                    widgetAccessor.bep$swapEntries(index, index - 1);
                    return true;
                }
            }

            return true;
        } else {
            return super.keyPressed(input);
        }
    }

    @Override
    public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        int x = this.getContentX();
        int y = this.getContentY();
        int index = -1;
        int size = 0;
        if (this.screen instanceof MultiplayerScreenAccessor accessor && accessor.getServerListWidget() instanceof BepServerListWidgetAccessor widgetAccessor) {
            index = widgetAccessor.bep$findFolderIndex(this.folder.getId());
            size = widgetAccessor.bep$getCurrentEntries().size();
        }

        if (!this.client.options.touchscreen().get() && !hovered) {
            context.fill(x, y, x + 32, y + 32, -12566464);
            context.fill(x + 1, y + 1, x + 31, y + 31, -10461088);
            context.fill(x + 4, y + 4, x + 14, y + 8, -2841228);
            context.fill(x + 4, y + 8, x + 28, y + 26, -2841228);
            context.fill(x + 4, y + 8, x + 28, y + 10, -1521506);
        } else {
            context.fill(x, y, x + 32, y + 32, -1601138544);
            int o = mouseX - x;
            int p = mouseY - y;
            if (o < 32 && o > 16) {
                context.blitSprite(RenderPipelines.GUI_TEXTURED, JOIN_HIGHLIGHTED_TEXTURE, x, y, 32, 32);
            } else {
                context.blitSprite(RenderPipelines.GUI_TEXTURED, JOIN_TEXTURE, x, y, 32, 32);
            }

            if (index > 0) {
                if (o < 16 && p < 16) {
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, MOVE_UP_HIGHLIGHTED_TEXTURE, x, y, 32, 32);
                } else {
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, MOVE_UP_TEXTURE, x, y, 32, 32);
                }
            }

            if (index < size - 1) {
                if (o < 16 && p > 16) {
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, MOVE_DOWN_HIGHLIGHTED_TEXTURE, x, y, 32, 32);
                } else {
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, MOVE_DOWN_TEXTURE, x, y, 32, 32);
                }
            }
        }

        String displayName = this.folder.getName();
        context.drawString(this.client.font, displayName, x + 35, y + 1, -1);
        int serverCount = this.getTotalServerCount();
        int subfolderCount = this.getSubfolderCount();
        StringBuilder infoBuilder = new StringBuilder();
        infoBuilder.append("Folder");
        if (serverCount > 0 || subfolderCount > 0) {
            infoBuilder.append(" (");
            if (subfolderCount > 0) {
                infoBuilder.append(subfolderCount).append(" folder").append(subfolderCount > 1 ? "s" : "");
                if (serverCount > 0) {
                    infoBuilder.append(", ");
                }
            }

            if (serverCount > 0) {
                infoBuilder.append(serverCount).append(" server").append(serverCount > 1 ? "s" : "");
            }

            infoBuilder.append(")");
        }

        context.drawString(this.client.font, infoBuilder.toString(), x + 35, y + 12, -8355712);
        context.drawString(this.client.font, "Double-click to open", x + 35, y + 23, -10461088);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (this.screen instanceof MultiplayerScreenAccessor accessor && accessor.getServerListWidget() instanceof BepServerListWidgetAccessor widgetAccessor) {
            int index = widgetAccessor.bep$findFolderIndex(this.folder.getId());
            int size = widgetAccessor.bep$getCurrentEntries().size();
            int x = this.getContentX();
            int y = this.getContentY();
            int entryWidth = this.getWidth();
            double mouseX = click.x();
            double mouseY = click.y();
            int iconX = x + entryWidth - 35;
            int iconY = y + 2;
            if (mouseX >= iconX && mouseX < iconX + 32 && mouseY >= iconY && mouseY < iconY + 32) {
                this.navigateInto();
                return true;
            }

            double d = mouseX - x;
            double e = mouseY - y;
            if (d <= 32.0 && index != -1) {
                if (d < 16.0 && e < 16.0 && index > 0) {
                    widgetAccessor.bep$swapEntries(index, index - 1);
                    return true;
                }

                if (d < 16.0 && e > 16.0 && index < size - 1) {
                    widgetAccessor.bep$swapEntries(index, index + 1);
                    return true;
                }
            }

            if (doubled) {
                this.navigateInto();
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public Component getNarration() {
        return Component.literal("Folder: " + this.folder.getName() + ", " + this.getTotalServerCount() + " servers");
    }
}
