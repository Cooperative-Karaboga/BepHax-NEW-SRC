package bep.hax.modules.livemessage.irc;

import bep.hax.modules.livemessage.LiveMessage;
import bep.hax.modules.livemessage.gui.GuiUtil;
import bep.hax.modules.livemessage.gui.LiveWindow;
import bep.hax.modules.livemessage.gui.LivemessageGui;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class IrcWindow extends LiveWindow {
    private static IrcWindow instance;
    private final IrcClient ircClient;
    private final List<IrcMessage> messages = new CopyOnWriteArrayList<>();
    private final List<IrcUser> sortedUsers = new CopyOnWriteArrayList<>();
    private boolean initialJoinComplete = false;
    private int historyMessageCount = 0;
    private static final int MAX_HISTORY_MESSAGES = 20;
    public EditBox inputField;
    private static final int USER_LIST_WIDTH = 120;
    private final int chatBoxY = titlebarHeight + 5;
    private final int chatBoxX = 5;
    private final int footer = 20;
    private int chatScrollPosition = 0;
    private int userScrollPosition = 0;
    private boolean chatScrollDragging = false;
    private boolean userScrollDragging = false;
    private int scrollBarWidth = 8;
    private boolean autoScrollToBottom = true;
    private IrcUser selectedUser = null;
    private boolean showModMenu = false;
    private int modMenuX;
    private int modMenuY;
    private String statusMessage = "Disconnected";
    private int statusColor = -43691;

    public IrcWindow() {
        instance = this;
        this.title = "IRC";
        this.w = 550;
        this.h = 350;
        this.minw = 400;
        this.minh = 250;
        this.closeButton = true;
        this.ircClient = new IrcClient();
        this.setupIrcCallbacks();
        this.inputField = new EditBox(this.mc.font, 9, this.h - 16, this.w - 18 - 120, 12, Component.literal(""));
        this.inputField.setMaxLength(450);
        this.inputField.setBordered(false);
        this.inputField.setFocused(true);
        this.inputField.setValue("");
        this.inputField.setTextColor(-1);
        this.inputField.setTextColorUneditable(-8355712);
        this.initButtons();
    }

    public static IrcWindow getInstance() {
        return instance;
    }

    public static IrcWindow getOrCreate() {
        boolean isNew = instance == null || !LivemessageGui.liveWindows.contains(instance);
        if (isNew) {
            instance = new IrcWindow();
        }

        return instance;
    }

    private void setupIrcCallbacks() {
        this.ircClient.setOnMessage(msg -> {
            LiveMessage.LOG.debug("IrcWindow received message: {} (total: {})", msg.getContent(), this.messages.size() + 1);
            if (!msg.isJoinLeaveMessage() || IrcConfig.showJoinLeaveMessages()) {
                if (!this.initialJoinComplete) {
                    this.historyMessageCount++;
                    if (this.historyMessageCount > 20 && !this.messages.isEmpty()) {
                        this.messages.remove(0);
                    }
                }

                this.messages.add(msg);

                while (this.messages.size() > 500) {
                    this.messages.remove(0);
                    if (this.chatScrollPosition > 0) {
                        this.chatScrollPosition--;
                    }
                }

                this.scrollChatToBottom();
                this.autoScrollToBottom = true;
                if ((msg.getType() == IrcMessage.Type.CHAT || msg.getType() == IrcMessage.Type.ACTION) && !msg.isSentByMe() && this.initialJoinComplete) {
                    String myNick = this.ircClient.getNickname();
                    if (myNick != null && msg.getContent().toLowerCase().contains(myNick.toLowerCase())) {
                        this.handleMentionNotification(msg);
                    }
                }

                LiveMessage.LOG.debug("IrcWindow scroll: pos={}, size={}, auto={}", this.chatScrollPosition, this.messages.size(), this.autoScrollToBottom);
            }
        });
        this.ircClient.setOnUserListUpdate(userList -> {
            this.sortedUsers.clear();
            this.sortedUsers.addAll(userList.getSortedUsers());
            if (!this.initialJoinComplete) {
                this.initialJoinComplete = true;
            }

            this.updateTitle();
        });
        this.ircClient.setOnConnectionChange(connected -> {
            if (connected) {
                this.statusMessage = "Connecting...";
                this.statusColor = -171;
            } else {
                this.statusMessage = "Disconnected";
                this.statusColor = -43691;
            }

            if (this.ircClient.isAuthenticated()) {
                this.statusMessage = "Connected";
                this.statusColor = -11141291;
            }

            this.updateTitle();
        });
        this.ircClient.setOnError(error -> {
            this.messages.add(IrcMessage.errorMessage(error));
            this.statusMessage = "Error";
            this.statusColor = -43691;
        });
        this.ircClient.setOnClearChat(() -> {
            this.messages.clear();
            this.chatScrollPosition = 0;
            this.messages.add(IrcMessage.systemMessage("Chat was cleared by a moderator"));
        });
    }

    private void handleMentionNotification(IrcMessage msg) {
        if (IrcConfig.hasToastNotifications()) {
            Minecraft mc = Minecraft.getInstance();
            ToastManager toastManager = mc.getToastManager();
            String senderName = msg.getSender() != null ? msg.getSender().getNickname() : "IRC";
            toastManager.addToast(
                new SystemToast(SystemToastId.NARRATOR_TOGGLE, Component.literal("IRC Mention from " + senderName), Component.literal(msg.getContent()))
            );
        }

        if (IrcConfig.hasSoundNotifications()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
            }
        }
    }

    private void updateTitle() {
        String channel = this.ircClient.getChannel();
        int userCount = this.sortedUsers.size();
        if (this.ircClient.isConnected() && this.ircClient.isAuthenticated()) {
            this.title = "IRC - " + (channel != null ? channel : "?") + " (" + userCount + ")";
        } else {
            this.title = "IRC - " + this.statusMessage;
        }
    }

    private void initButtons() {
        this.liveButtons.add(new LiveWindow.LiveButton(0, 200, titlebarHeight + 2, 65, 11, true, "Connect", "Connect to IRC", () -> {
            if (this.ircClient.isConnected()) {
                this.disconnect();
            } else {
                this.connectToIrc();
            }
        }));
    }

    public void connectToIrc() {
        String serverUrl = IrcConfig.getServerUrl();
        String nickname = IrcConfig.getNickname();
        String channel = IrcConfig.getChannel();
        if (IrcConfig.isOfficialServer()) {
            String email = IrcConfig.getOfficialEmail();
            String password = IrcConfig.getOfficialPassword();
            if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
                this.messages.add(IrcMessage.errorMessage("No credentials found. Please log in to BepHax first."));
                return;
            }

            this.ircClient.connectOfficial(serverUrl, email, password, nickname, channel);
        } else {
            String serverPassword = IrcConfig.getServerPassword();
            String channelPassword = IrcConfig.getChannelPassword();
            this.ircClient.connectCustom(serverUrl, nickname, channel, serverPassword);
        }

        this.initialJoinComplete = false;
        this.historyMessageCount = 0;
        this.messages.clear();
        this.sortedUsers.clear();
        this.chatScrollPosition = 0;
        this.autoScrollToBottom = true;
        this.statusMessage = "Connecting...";
        this.statusColor = -171;
        this.updateButtonText();
    }

    public void disconnect() {
        this.ircClient.disconnect();
        this.messages.clear();
        this.sortedUsers.clear();
        this.chatScrollPosition = 0;
        this.userScrollPosition = 0;
        this.autoScrollToBottom = true;
        this.initialJoinComplete = false;
        this.historyMessageCount = 0;
        this.statusMessage = "Disconnected";
        this.statusColor = -43691;
        this.updateButtonText();
        this.updateTitle();
    }

    private void updateButtonText() {
        for (LiveWindow.LiveButton btn : this.liveButtons) {
            if (btn.id == 0) {
                btn.btnText = this.ircClient.isConnected() ? "Disconnect" : "Connect";
            }
        }
    }

    private void updateButtonPositions() {
        for (LiveWindow.LiveButton btn : this.liveButtons) {
            if (btn.id == 0) {
                btn.bx = 200;
                btn.by = titlebarHeight + 2;
            }
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (this.showModMenu) {
            this.showModMenu = false;
        } else {
            if (keyCode == 257 || keyCode == 335) {
                String text = this.inputField.getValue().trim();
                if (!text.isEmpty()) {
                    if (text.startsWith("/")) {
                        this.handleCommand(text.substring(1));
                    } else {
                        this.ircClient.sendMessage(text);
                    }

                    this.inputField.setValue("");
                }
            } else if (keyCode == 266) {
                this.chatScrollPosition = Math.max(0, this.chatScrollPosition - 10);
            } else if (keyCode == 267) {
                this.chatScrollPosition = Math.min(Math.max(0, this.messages.size() - 1), this.chatScrollPosition + 10);
            } else {
                if (keyCode != 0 && this.lastKeyInput != null) {
                    this.inputField.keyPressed(this.lastKeyInput);
                }

                if (typedChar != 0 && this.lastCharInput != null) {
                    this.inputField.charTyped(this.lastCharInput);
                }
            }

            super.keyTyped(typedChar, keyCode);
        }
    }

    private void handleCommand(String cmd) {
        String[] parts = cmd.split(" ", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";
        switch (command) {
            case "me":
                if (!args.isEmpty()) {
                    this.ircClient.sendCommand("PRIVMSG " + this.ircClient.getChannel() + args);
                }
                break;
            case "nick":
                if (!args.isEmpty()) {
                    this.ircClient.sendCommand("NICK " + args);
                }
                break;
            case "kick":
                if (!args.isEmpty()) {
                    String[] kickParts = args.split(" ", 2);
                    String reason = kickParts.length > 1 ? kickParts[1] : "Kicked";
                    this.ircClient.sendCommand("KICK " + this.ircClient.getChannel() + " " + kickParts[0] + " :" + reason);
                }
                break;
            case "ban":
                if (!args.isEmpty()) {
                    this.ircClient.sendCommand("MODE " + this.ircClient.getChannel() + " +b " + args.trim());
                }
                break;
            case "unban":
                if (!args.isEmpty()) {
                    this.ircClient.sendCommand("MODE " + this.ircClient.getChannel() + " -b " + args.trim());
                }
                break;
            case "mod":
                if (!args.isEmpty()) {
                    this.ircClient.sendCommand("MODE " + this.ircClient.getChannel() + " +m " + args.trim());
                }
                break;
            case "demod":
                if (!args.isEmpty()) {
                    this.ircClient.sendCommand("MODE " + this.ircClient.getChannel() + " -m " + args.trim());
                }
                break;
            case "clear":
                this.ircClient.sendCommand("PRIVMSG " + this.ircClient.getChannel() + " :!clear");
                break;
            case "help":
                this.ircClient.sendCommand("HELP");
                break;
            case "quit":
                this.ircClient.disconnect();
                break;
            case "raw":
                if (!args.isEmpty()) {
                    this.ircClient.sendCommand(args);
                }
                break;
            default:
                this.messages.add(IrcMessage.errorMessage("Unknown command: /" + command + " - try /help"));
        }
    }

    @Override
    public void mouseWheel(int mWheelState) {
        int userListX = this.w - 120 - 5;
        boolean inUserList = this.lastMouseX > this.x + userListX && this.lastMouseX < this.x + this.w - 5;
        boolean shift = GLFW.glfwGetKey(this.mc.getWindow().handle(), 340) == 1;
        int scrollAmount = shift ? 10 : 1;
        if (inUserList) {
            int maxUserScroll = this.getMaxUserScroll();
            if (mWheelState < 0) {
                this.userScrollPosition = Math.min(this.userScrollPosition + scrollAmount, maxUserScroll);
            } else {
                this.userScrollPosition = Math.max(0, this.userScrollPosition - scrollAmount);
            }
        } else {
            int maxChatScroll = this.getMaxChatScroll();
            if (mWheelState < 0) {
                this.chatScrollPosition = Math.min(this.chatScrollPosition + scrollAmount, maxChatScroll);
            } else {
                this.chatScrollPosition = Math.max(0, this.chatScrollPosition - scrollAmount);
            }

            this.autoScrollToBottom = this.chatScrollPosition >= maxChatScroll;
        }

        super.mouseWheel(mWheelState);
    }

    private int getChatMaxLines() {
        int chatHeight = this.h - this.chatBoxY - 20 - 5;
        return Math.max(1, (chatHeight - 6) / 12);
    }

    private int getMaxChatScroll() {
        int maxLines = this.getChatMaxLines();
        return Math.max(0, this.messages.size() - maxLines);
    }

    private int getUserMaxLines() {
        int chatHeight = this.h - this.chatBoxY - 20 - 5;
        return Math.max(1, (chatHeight - 16) / 12);
    }

    private int getMaxUserScroll() {
        return Math.max(0, this.sortedUsers.size() - this.getUserMaxLines());
    }

    private void scrollChatToBottom() {
        int maxLines = this.getChatMaxLines();
        this.chatScrollPosition = Math.max(0, this.messages.size() - maxLines);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        if (!this.showModMenu) {
            int chatHeight = this.h - this.chatBoxY - 20 - 5;
            int chatWidth = this.w - 120 - 15 - this.scrollBarWidth;
            int chatScrollbarX = 5 + chatWidth;
            int userListX = this.w - 120 - 5;
            int userScrollbarX = userListX + 120 - this.scrollBarWidth;
            if (mouseButton == 0 && this.mouseInRect(chatScrollbarX, this.chatBoxY, this.scrollBarWidth, chatHeight, mouseX, mouseY)) {
                this.chatScrollDragging = true;
                int maxScroll = this.getMaxChatScroll();
                if (maxScroll > 0) {
                    int relY = mouseY - this.y - this.chatBoxY;
                    float scrollRatio = (float)relY / chatHeight;
                    this.chatScrollPosition = (int)(scrollRatio * maxScroll);
                    this.chatScrollPosition = Mth.clamp(this.chatScrollPosition, 0, maxScroll);
                    this.autoScrollToBottom = this.chatScrollPosition >= maxScroll;
                }
            } else if (mouseButton == 0 && this.mouseInRect(userScrollbarX, this.chatBoxY, this.scrollBarWidth, chatHeight, mouseX, mouseY)) {
                this.userScrollDragging = true;
                int maxScroll = this.getMaxUserScroll();
                if (maxScroll > 0) {
                    int relY = mouseY - this.y - this.chatBoxY;
                    float scrollRatio = (float)relY / chatHeight;
                    this.userScrollPosition = (int)(scrollRatio * maxScroll);
                    this.userScrollPosition = Mth.clamp(this.userScrollPosition, 0, maxScroll);
                }
            } else {
                for (LiveWindow.LiveButton btn : this.liveButtons) {
                    if (btn.isMouseOver()) {
                        btn.action.run();
                        return;
                    }
                }

                int inputFieldY = this.h - 20 + 2;
                int inputWidth = this.w - 120 - 15;
                if (this.mouseInRect(5, inputFieldY, inputWidth, 14, mouseX, mouseY)) {
                    this.inputField.setFocused(true);
                } else {
                    this.inputField.setFocused(false);
                }

                int userListY = this.chatBoxY;
                int userListHeight = this.h - this.chatBoxY - 20 - 5;
                if (mouseButton == 1 && this.mouseInRect(userListX, userListY, 120, userListHeight, mouseX, mouseY)) {
                    int clickedIndex = this.userScrollPosition + (mouseY - this.y - userListY) / 12;
                    if (clickedIndex >= 0 && clickedIndex < this.sortedUsers.size()) {
                        this.selectedUser = this.sortedUsers.get(clickedIndex);
                        this.showModMenu = true;
                        this.modMenuX = mouseX;
                        this.modMenuY = mouseY;
                        return;
                    }
                }

                super.mouseClicked(mouseX, mouseY, mouseButton);
            }
        } else {
            if (this.selectedUser != null) {
                int itemHeight = 14;
                String[] options = this.getModOptions();

                for (int i = 0; i < options.length; i++) {
                    if (this.mouseInRect(this.modMenuX - this.x, this.modMenuY - this.y + i * itemHeight, 80, itemHeight, mouseX, mouseY)) {
                        this.executeModOption(options[i], this.selectedUser);
                        break;
                    }
                }
            }

            this.showModMenu = false;
            this.selectedUser = null;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        this.chatScrollDragging = false;
        this.userScrollDragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void mouseMove(int mouseX, int mouseY) {
        super.mouseMove(mouseX, mouseY);
        if (this.chatScrollDragging || this.userScrollDragging) {
            int chatHeight = this.h - this.chatBoxY - 20 - 5;
            int relY = mouseY - this.y - this.chatBoxY;
            float scrollRatio = Mth.clamp((float)relY / chatHeight, 0.0F, 1.0F);
            if (this.chatScrollDragging) {
                int maxScroll = this.getMaxChatScroll();
                this.chatScrollPosition = (int)(scrollRatio * maxScroll);
                this.chatScrollPosition = Mth.clamp(this.chatScrollPosition, 0, maxScroll);
                this.autoScrollToBottom = this.chatScrollPosition >= maxScroll;
            } else if (this.userScrollDragging) {
                int maxScroll = this.getMaxUserScroll();
                this.userScrollPosition = (int)(scrollRatio * maxScroll);
                this.userScrollPosition = Mth.clamp(this.userScrollPosition, 0, maxScroll);
            }
        }
    }

    private String[] getModOptions() {
        List<String> options = new ArrayList<>();
        options.add("Whisper");
        if (IrcConfig.isOfficialServer()) {
            IrcUser self = this.ircClient.getUserList().getUser(this.ircClient.getNickname());
            boolean canMod = self != null && self.getRole().canModerate();
            boolean isOwner = self != null && self.getRole() == IrcUser.Role.OWNER;
            if (canMod) {
                options.add("Kick");
                options.add("Ban");
            }

            if (isOwner) {
                options.add("Mod");
                options.add("Demod");
            }
        }

        return options.toArray(new String[0]);
    }

    private void executeModOption(String option, IrcUser user) {
        switch (option.toLowerCase()) {
            case "whisper":
                this.inputField.setValue("/msg " + user.getNickname() + " ");
                this.inputField.setFocused(true);
                break;
            case "kick":
                this.ircClient.sendCommand("KICK " + this.ircClient.getChannel() + " " + user.getNickname() + " :Kicked");
                break;
            case "ban":
                this.ircClient.sendCommand("MODE " + this.ircClient.getChannel() + " +b " + user.getNickname());
                break;
            case "mod":
                this.ircClient.sendCommand("MODE " + this.ircClient.getChannel() + " +m " + user.getNickname());
                break;
            case "demod":
                this.ircClient.sendCommand("MODE " + this.ircClient.getChannel() + " -m " + user.getNickname());
        }
    }

    @Override
    public void preDrawWindow(GuiGraphics context) {
        super.preDrawWindow(context);
        context.pose().translate(this.x, this.y);
        int statusX = 5;
        int statusY = titlebarHeight + 5;
        context.fill(statusX, statusY, statusX + 6, statusY + 6, GuiUtil.fade(this.statusColor));
        this.updateButtonText();
        this.updateButtonPositions();
        int chatWidth = this.w - 120 - 15 - this.scrollBarWidth;
        int chatHeight = this.h - this.chatBoxY - 20 - 5;
        GuiUtil.drawRect(context, 5, this.chatBoxY, chatWidth + this.scrollBarWidth, chatHeight, GuiUtil.getSingleRGB(24));
        this.drawChatMessages(context, 8, this.chatBoxY + 3, chatWidth - 6, chatHeight - 6);
        this.drawScrollbar(
            context,
            5 + chatWidth,
            this.chatBoxY + 2,
            this.scrollBarWidth - 2,
            chatHeight - 4,
            this.chatScrollPosition,
            this.getMaxChatScroll(),
            this.messages.size(),
            this.getChatMaxLines()
        );
        int userListX = this.w - 120 - 5;
        GuiUtil.drawRect(context, userListX, this.chatBoxY, 120, chatHeight, GuiUtil.getSingleRGB(28));
        this.drawUserList(context, userListX + 3, this.chatBoxY + 3, 120 - this.scrollBarWidth - 4, chatHeight - 6);
        this.drawScrollbar(
            context,
            userListX + 120 - this.scrollBarWidth,
            this.chatBoxY + 2,
            this.scrollBarWidth - 2,
            chatHeight - 4,
            this.userScrollPosition,
            this.getMaxUserScroll(),
            this.sortedUsers.size(),
            this.getUserMaxLines()
        );
        int inputFieldY = this.h - 20 + 2;
        GuiUtil.drawRect(context, 5, inputFieldY, chatWidth + this.scrollBarWidth, 14, GuiUtil.getSingleRGB(48));
        this.drawRectOutline(context, 5, inputFieldY, chatWidth + this.scrollBarWidth, 14, GuiUtil.getSingleRGB(64));

        for (LiveWindow.LiveButton btn : this.liveButtons) {
            btn.draw(context);
        }

        if (this.showModMenu && this.selectedUser != null) {
            this.drawModMenu(context);
        }

        for (LiveWindow.LiveButton btn : this.liveButtons) {
            btn.drawTooltips(context);
        }

        context.pose().translate(-this.x, -this.y);
    }

    private void drawScrollbar(GuiGraphics context, int x, int y, int width, int height, int scrollPos, int maxScroll, int totalItems, int visibleItems) {
        context.fill(x, y, x + width, y + height, GuiUtil.fade(GuiUtil.getSingleRGB(16)));
        if (totalItems > visibleItems && maxScroll > 0) {
            float viewRatio = (float)visibleItems / totalItems;
            int thumbHeight = Math.max(20, (int)(height * viewRatio));
            float scrollRatio = (float)scrollPos / maxScroll;
            int thumbY = y + (int)((height - thumbHeight) * scrollRatio);
            thumbY = Mth.clamp(thumbY, y, y + height - thumbHeight);
            int thumbColor = GuiUtil.getSingleRGB(80);
            int mouseRelX = this.lastMouseX - this.x;
            int mouseRelY = this.lastMouseY - this.y;
            if (this.chatScrollDragging && x < this.w / 2
                || this.userScrollDragging && x > this.w / 2
                || mouseRelX >= x && mouseRelX <= x + width && mouseRelY >= thumbY && mouseRelY <= thumbY + thumbHeight) {
                thumbColor = GuiUtil.getSingleRGB(120);
            }

            context.fill(x + 1, thumbY, x + width - 1, thumbY + thumbHeight, GuiUtil.fade(thumbColor));
        } else {
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, GuiUtil.fade(GuiUtil.getSingleRGB(64)));
        }
    }

    private void drawChatMessages(GuiGraphics context, int x, int y, int width, int height) {
        if (this.messages.isEmpty()) {
            this.drawText(context, "No messages yet. Type to chat!", x, y, GuiUtil.getSingleRGB(96), false);
            this.autoScrollToBottom = false;
        } else {
            int lineHeight = 12;
            int drawHeight = 0;
            this.autoScrollToBottom = true;
            int bottomLimit = height - lineHeight;

            for (int i = this.chatScrollPosition; i < this.messages.size(); i++) {
                IrcMessage msg = this.messages.get(i);
                boolean isTrimmed = false;
                String time = "[" + msg.getFormattedTime() + "] ";
                int timeWidth = this.getTextWidth(time);
                String sender;
                int senderColor;
                switch (msg.getType()) {
                    case SYSTEM:
                        sender = "*** ";
                        senderColor = -7829368;
                        break;
                    case NOTICE:
                        sender = "[Notice] ";
                        senderColor = -22016;
                        break;
                    case ERROR:
                        sender = "[Error] ";
                        senderColor = -43691;
                        break;
                    case ACTION:
                        sender = "* " + (msg.getSender() != null ? msg.getSender().getNickname() : "???") + " ";
                        senderColor = -3372852;
                        break;
                    default:
                        if (msg.getSender() != null) {
                            sender = "<" + msg.getSender().getDisplayName() + "> ";
                            senderColor = msg.isSentByMe() ? -11141121 : msg.getSender().getNameColor();
                        } else {
                            sender = "";
                            senderColor = -1;
                        }
                }

                int senderWidth = this.getTextWidth(sender);
                String content = msg.getContent();
                int contentColor = msg.getMessageColor();

                while (true) {
                    if (drawHeight * lineHeight > bottomLimit) {
                        this.autoScrollToBottom = false;
                        break;
                    }

                    String prefix = isTrimmed ? "" : time;
                    int prefixWidth = isTrimmed ? timeWidth : 0;
                    if (!isTrimmed) {
                        this.drawText(context, time, x, y + drawHeight * lineHeight, GuiUtil.getSingleRGB(96), false);
                        if (!sender.isEmpty()) {
                            this.drawText(context, sender, x + timeWidth, y + drawHeight * lineHeight, senderColor, false);
                        }
                    }

                    int contentX = x + timeWidth + (isTrimmed ? 0 : senderWidth);
                    int maxContentWidth = width - timeWidth - (isTrimmed ? 0 : senderWidth) - 5;
                    if (maxContentWidth <= 10) {
                        this.drawText(context, this.fontRenderer.plainSubstrByWidth(content, width - 5), contentX, y + drawHeight * lineHeight, contentColor, false);
                        drawHeight++;
                        break;
                    }

                    String trimmed = this.fontRenderer.plainSubstrByWidth(content, maxContentWidth);
                    this.drawText(context, trimmed, contentX, y + drawHeight * lineHeight, contentColor, false);
                    drawHeight++;
                    if (content.equals(trimmed)) {
                        break;
                    }

                    content = content.substring(trimmed.length());
                    isTrimmed = true;
                }

                if (!this.autoScrollToBottom) {
                    break;
                }
            }

            if (this.autoScrollToBottom && (drawHeight + 2) * lineHeight <= bottomLimit) {
                this.autoScrollToBottom = false;
            }
        }
    }

    private void drawUserList(GuiGraphics context, int x, int y, int width, int height) {
        this.drawText(context, "Users (" + this.sortedUsers.size() + ")", x, y, GuiUtil.getSingleRGB(180), false);
        int lineHeight = 12;
        int startY = y + lineHeight + 2;
        int maxUsers = (height - lineHeight - 4) / lineHeight;
        int hoveredIndex = -1;
        if (this.lastMouseX > this.x + x - 3 && this.lastMouseX < this.x + x + width + 3) {
            int relY = this.lastMouseY - this.y - startY;
            if (relY >= 0) {
                hoveredIndex = this.userScrollPosition + relY / lineHeight;
            }
        }

        int drawY = startY;

        for (int i = this.userScrollPosition; i < this.sortedUsers.size() && i < this.userScrollPosition + maxUsers; i++) {
            IrcUser user = this.sortedUsers.get(i);
            if (i == hoveredIndex) {
                context.fill(x - 2, drawY - 1, x + width + 2, drawY + lineHeight - 1, GuiUtil.fade(GuiUtil.getSingleRGB(48)));
            }

            String prefix = user.getRole().prefix;
            int prefixWidth = 0;
            if (!prefix.isEmpty()) {
                this.drawText(context, prefix, x, drawY, user.getRole().color | 0xFF000000, false);
                prefixWidth = this.getTextWidth(prefix);
            }

            int nameX = x + prefixWidth;
            int nameColor = user.getNickname().equalsIgnoreCase(this.ircClient.getNickname())
                ? -11141121
                : (user.getRole() == IrcUser.Role.NORMAL ? -5592406 : user.getNameColor());
            String displayName = this.fontRenderer.plainSubstrByWidth(user.getNickname(), width - prefixWidth - 2);
            this.drawText(context, displayName, nameX, drawY, nameColor, false);
            if (user.isAway()) {
                this.drawText(context, " (away)", nameX + this.getTextWidth(displayName), drawY, GuiUtil.getSingleRGB(96), false);
            }

            drawY += lineHeight;
        }
    }

    private void drawModMenu(GuiGraphics context) {
        if (this.selectedUser != null) {
            String[] options = this.getModOptions();
            int itemHeight = 14;
            int menuWidth = 80;
            int menuHeight = options.length * itemHeight + 4;
            int menuX = this.modMenuX - this.x;
            int menuY = this.modMenuY - this.y;
            if (menuX + menuWidth > this.w) {
                menuX = this.w - menuWidth;
            }

            if (menuY + menuHeight > this.h) {
                menuY = this.h - menuHeight;
            }

            GuiUtil.drawRect(context, menuX - 1, menuY - 1, menuWidth + 2, menuHeight + 2, GuiUtil.getSingleRGB(200));
            GuiUtil.drawRect(context, menuX, menuY, menuWidth, menuHeight, GuiUtil.getSingleRGB(32));
            String header = this.selectedUser.getNickname();
            if (header.length() > 10) {
                header = header.substring(0, 10) + "...";
            }

            this.drawText(context, header, menuX + 4, menuY + 2, this.selectedUser.getNameColor(), false);
            int optY = menuY + itemHeight;

            for (String opt : options) {
                boolean hovered = this.lastMouseX >= this.x + menuX
                    && this.lastMouseX <= this.x + menuX + menuWidth
                    && this.lastMouseY >= this.y + optY
                    && this.lastMouseY <= this.y + optY + itemHeight;
                if (hovered) {
                    context.fill(menuX + 1, optY, menuX + menuWidth - 1, optY + itemHeight, GuiUtil.fade(GuiUtil.getSingleRGB(64)));
                }

                int color = opt.equalsIgnoreCase("ban") ? -43691 : (opt.equalsIgnoreCase("kick") ? -22016 : -1);
                this.drawText(context, opt, menuX + 6, optY + 2, color, false);
                optY += itemHeight;
            }
        }
    }

    @Override
    public void drawTextFields(GuiGraphics context) {
        context.pose().translate(this.x, this.y);
        int inputFieldY = this.h - 20 + 4;
        this.inputField.setX(8);
        this.inputField.setY(inputFieldY);
        this.inputField.setWidth(this.w - 120 - 20);
        this.inputField.render(context, this.lastMouseX - this.x, this.lastMouseY - this.y, 0.0F);
        context.pose().translate(-this.x, -this.y);
    }

    @Override
    public void deactivateWindow() {
        super.deactivateWindow();
        if (this.inputField != null) {
            this.inputField.setFocused(false);
        }
    }

    @Override
    public void activateWindow() {
        super.activateWindow();
        if (this.inputField != null) {
            this.inputField.setFocused(true);
        }
    }

    public IrcClient getIrcClient() {
        return this.ircClient;
    }

    public void cleanup() {
        if (this.ircClient != null) {
            this.ircClient.shutdown();
        }

        if (instance == this) {
            instance = null;
        }
    }
}
