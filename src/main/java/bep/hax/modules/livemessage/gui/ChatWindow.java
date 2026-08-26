package bep.hax.modules.livemessage.gui;

import bep.hax.emoji.EmojiData;
import bep.hax.modules.livemessage.LiveMessage;
import bep.hax.modules.livemessage.util.LiveProfileCache;
import bep.hax.modules.livemessage.util.LivemessageUtil;
import bep.hax.util.EnemyManager;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

public class ChatWindow extends LiveWindow {
    boolean valid;
    LiveProfileCache.LiveProfile liveProfile;
    String msgString;
    final int scrollBarWidth = 10;
    int scrollBarHeight = 50;
    int chatScrollPosition = 0;
    boolean scrolling = false;
    public boolean chatScrolledToBottom = true;
    public EditBox inputField;
    public LivemessageUtil.ChatSettings chatSettings;
    final int chatBoxY = titlebarHeight + 44;
    final int chatBoxX = 5;
    final int chatBoxSize = 13;
    List<ChatWindow.ChatMessage> chatHistory = new ArrayList<>();
    List<ChatWindow.ClickableLink> clickableLinks = new ArrayList<>();
    GuiUtil.QuintAnimation fullSkinAnim = new GuiUtil.QuintAnimation(600, 0.0F);
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s]+|www\\.[^\\s]+)", 2);
    private static final ChatFormatting[] MINECRAFT_COLORS = new ChatFormatting[]{
        ChatFormatting.BLACK,
        ChatFormatting.DARK_BLUE,
        ChatFormatting.DARK_GREEN,
        ChatFormatting.DARK_AQUA,
        ChatFormatting.DARK_RED,
        ChatFormatting.DARK_PURPLE,
        ChatFormatting.GOLD,
        ChatFormatting.GRAY,
        ChatFormatting.DARK_GRAY,
        ChatFormatting.BLUE,
        ChatFormatting.GREEN,
        ChatFormatting.AQUA,
        ChatFormatting.RED,
        ChatFormatting.LIGHT_PURPLE,
        ChatFormatting.YELLOW,
        ChatFormatting.WHITE
    };

    ChatWindow(UUID uuid) {
        this(LiveProfileCache.getLiveprofileFromUUID(uuid, false));
    }

    public ChatWindow(LiveProfileCache.LiveProfile liveProfile) {
        if (liveProfile == null) {
            LiveMessage.LOG.warn("Tried to open an invalid chat window - offline mode?");
            this.valid = false;
        } else {
            this.valid = true;
            this.minw = 280;
            this.w = LiveMessage.INSTANCE.defaultChatWidth.get();
            this.h = LiveMessage.INSTANCE.defaultChatHeight.get();
            this.x = Math.min(this.x, Math.max(0, LivemessageGui.screenWidth - this.w));
            this.y = Math.min(this.y, Math.max(0, LivemessageGui.screenHeight - this.h));
            this.liveProfile = liveProfile;
            this.chatSettings = LivemessageUtil.getChatSettings(liveProfile.uuid);
            this.loadWindowColor();
            this.loadChatHistory();
            this.initButtons();
            this.msgString = "/msg " + liveProfile.username + " ";
            this.inputField = new EditBox(this.mc.font, 9, this.h - 16, this.w - 18, 12, Component.literal(""));
            this.inputField.setMaxLength(256 - this.msgString.length());
            this.inputField.setBordered(false);
            this.inputField.setFocused(true);
            this.inputField.setValue("");
            this.inputField.setTextColor(-1);
            this.inputField.setTextColorUneditable(-8355712);
            this.chatScrollPosition = Math.max(0, this.chatHistory.size() - 1);
            this.chatScrolledToBottom = true;
        }
    }

    public void initButtons() {
        this.liveButtons.add(new LiveWindow.LiveButton(0, 14, titlebarHeight + 3, 11, 11, true, 0, "Toggle friend/enemy", () -> {
            this.toggleFriend();
            this.updateButtonStates();
        }));
        this.liveButtons.add(new LiveWindow.LiveButton(2, 14, titlebarHeight + 3 + 13, 11, 11, true, 2, "Custom color", () -> {
            this.toggleColor();
            this.updateButtonStates();
        }));
        this.liveButtons.add(new LiveWindow.LiveButton(3, 14, titlebarHeight + 3 + 26, 11, 11, true, 1, "Ignore player", () -> this.ignorePlayer()));
        this.updateButtonStates();
    }

    private void updateButtonStates() {
        Friends friends = Friends.get();
        EnemyManager enemyManager = EnemyManager.get();
        boolean isFriend = friends.get(this.liveProfile.username) != null;
        boolean isEnemy = enemyManager.isEnemy(this.liveProfile.username);

        for (LiveWindow.LiveButton btn : this.liveButtons) {
            if (btn.id == 0) {
                btn.iconActive = isFriend || isEnemy;
                if (isFriend) {
                    btn.iconColor = GuiUtil.getRGB(85, 255, 85);
                } else if (isEnemy) {
                    btn.iconColor = GuiUtil.getRGB(255, 85, 85);
                } else {
                    btn.iconColor = -1;
                }
            } else if (btn.id == 2) {
                btn.iconActive = this.chatSettings.customColor > 0;
            }
        }
    }

    private void ignorePlayer() {
        if (this.mc.player != null) {
            this.mc.player.connection.sendCommand("ignorehard " + this.liveProfile.username);
        }

        LivemessageGui.liveWindows.remove(this);
        if (!LivemessageGui.liveWindows.isEmpty()) {
            LivemessageGui.liveWindows.get(LivemessageGui.liveWindows.size() - 1).activateWindow();
        }
    }

    public void toggleFriend() {
        Friends friends = Friends.get();
        if (friends.get(this.liveProfile.username) != null) {
            friends.remove(friends.get(this.liveProfile.username));
        } else {
            friends.add(new Friend(this.liveProfile.username));
        }
    }

    public void toggleColor() {
        int currentIndex = -1;
        if (this.chatSettings.customColor == 0) {
            currentIndex = -1;
        } else {
            for (int i = 0; i < MINECRAFT_COLORS.length; i++) {
                if (MINECRAFT_COLORS[i].isColor() && MINECRAFT_COLORS[i].getColor() != null) {
                    int colorValue = MINECRAFT_COLORS[i].getColor() | 0xFF000000;
                    if (this.chatSettings.customColor == colorValue) {
                        currentIndex = i;
                        break;
                    }
                }
            }
        }

        currentIndex = (currentIndex + 1) % (MINECRAFT_COLORS.length + 1);
        if (currentIndex == MINECRAFT_COLORS.length) {
            this.chatSettings.customColor = 0;
            this.primaryColor = GuiUtil.getWindowColor(this.liveProfile.uuid);
            LiveMessage.LOG
                .info("Reset window color for {} to default (0x{})", this.liveProfile.username, Integer.toHexString(this.primaryColor).toUpperCase());
        } else if (MINECRAFT_COLORS[currentIndex].isColor() && MINECRAFT_COLORS[currentIndex].getColor() != null) {
            this.chatSettings.customColor = MINECRAFT_COLORS[currentIndex].getColor() | 0xFF000000;
            this.primaryColor = this.chatSettings.customColor;
            LiveMessage.LOG
                .info(
                    "Changed window color for {} to custom 0x{} (index {})",
                    this.liveProfile.username,
                    Integer.toHexString(this.chatSettings.customColor).toUpperCase(),
                    currentIndex
                );
        }

        LivemessageUtil.saveChatSettings(this.liveProfile.uuid, this.chatSettings);
    }

    public void loadWindowColor() {
        if (this.chatSettings.customColor > 0) {
            this.primaryColor = this.chatSettings.customColor;
            LiveMessage.LOG.info("Loaded custom window color for {} = 0x{}", this.liveProfile.username, Integer.toHexString(this.primaryColor).toUpperCase());
        } else {
            this.primaryColor = GuiUtil.getWindowColor(this.liveProfile.uuid);
            LiveMessage.LOG.info("Loaded default window color for {} = 0x{}", this.liveProfile.username, Integer.toHexString(this.primaryColor).toUpperCase());
        }
    }

    public void reloadWindowColor() {
        if (this.chatSettings.customColor == 0) {
            this.primaryColor = GuiUtil.getWindowColor(this.liveProfile.uuid);
            LiveMessage.LOG
                .info("Reloaded default window color for {} = 0x{}", this.liveProfile.username, Integer.toHexString(this.primaryColor).toUpperCase());
        }
    }

    public void loadChatHistory() {
        Gson gson = new Gson();
        List<String> allLines = new ArrayList<>();

        String line;
        try (BufferedReader reader = new BufferedReader(
                new FileReader(LivemessageUtil.MESSAGES_FOLDER.resolve(this.liveProfile.uuid.toString() + ".jsonl").toFile())
            )) {
            while ((line = reader.readLine()) != null) {
                allLines.add(line);
            }
        } catch (IOException var9) {
        }

        int startIndex = Math.max(0, allLines.size() - 100);

        for (int i = startIndex; i < allLines.size(); i++) {
            try {
                this.chatHistory.add(gson.fromJson(allLines.get(i), ChatWindow.ChatMessage.class));
            } catch (Exception e) {
                LiveMessage.LOG.error("Failed to parse chat message from history file for UUID: {}", this.liveProfile.uuid, e);
            }
        }
    }

    public void saveChatMessage(ChatWindow.ChatMessage message) {
        Gson gson = new Gson();

        try (FileWriter writer = new FileWriter(LivemessageUtil.MESSAGES_FOLDER.resolve(this.liveProfile.uuid.toString() + ".jsonl").toFile(), true)) {
            writer.write(gson.toJson(message) + "\n");
        } catch (IOException e) {
            LiveMessage.LOG.error("Failed to save chat message to history file for UUID: {}", this.liveProfile.uuid, e);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        this.markAsRead();
        if (keyCode == 257 || keyCode == 335) {
            String s = this.inputField.getValue().trim();
            if (!s.isEmpty()) {
                if (EmojiData.enabled()) {
                    s = EmojiData.expand(s);
                }

                this.mc.player.connection.sendCommand("msg " + this.liveProfile.username + " " + s);
                this.inputField.setValue("");
            }
        } else if (keyCode == 266) {
            this.chatScrollPosition = Mth.clamp(this.chatScrollPosition - 10, 0, Math.max(this.chatHistory.size() - 1, 0));
        } else if (keyCode == 267) {
            this.chatScrollPosition = Mth.clamp(this.chatScrollPosition + 10, 0, Math.max(this.chatHistory.size() - 1, 0));
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

    @Override
    public void mouseWheel(int mWheelState) {
        this.markAsRead();
        boolean shift = GLFW.glfwGetKey(this.mc.getWindow().handle(), 340) == 1;
        int scrollAmount = shift ? 10 : 1;
        if (mWheelState < 0) {
            this.chatScrollPosition = Math.min(this.chatScrollPosition + scrollAmount, Math.max(this.chatHistory.size() - 1, 0));
        } else {
            this.chatScrollPosition = Math.max(this.chatScrollPosition - scrollAmount, 0);
        }

        super.mouseWheel(mWheelState);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        this.scrolling = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseDrag(double mouseX, double mouseY) {
        if (this.scrolling && this.chatHistory.size() > 1) {
            int totalPixels = this.h - (this.chatBoxY + 10 + 13 + this.scrollBarHeight);
            int maxScroll = this.chatHistory.size() - 1;
            int relativeMouseY = (int)mouseY - (this.dragY + this.chatBoxY + this.y);
            this.chatScrollPosition = (int)Mth.clamp((float)(relativeMouseY * maxScroll) / totalPixels, 0.0F, maxScroll);
        } else {
            super.handleMouseDrag(mouseX, mouseY);
        }
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.scrolling && this.chatHistory.size() > 1) {
            int totalPixels = this.h - (this.chatBoxY + 10 + 13 + this.scrollBarHeight);
            int maxScroll = this.chatHistory.size() - 1;
            int relativeMouseY = mouseY - (this.dragY + this.chatBoxY + this.y);
            this.chatScrollPosition = (int)Mth.clamp((float)(relativeMouseY * maxScroll) / totalPixels, 0.0F, maxScroll);
        }

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        if (this.mouseInRect(0, 0, this.w, this.h, mouseX, mouseY)) {
            this.markAsRead();
        }

        if (mouseButton == 0) {
            for (ChatWindow.ClickableLink link : this.clickableLinks) {
                if (link.contains(mouseX - this.x, mouseY - this.y)) {
                    this.openUrl(link.url);
                    return;
                }
            }
        }

        boolean buttonClicked = false;

        for (LiveWindow.LiveButton btn : this.liveButtons) {
            if (btn.isMouseOver()) {
                LiveMessage.LOG
                    .info("Button {} clicked at ({}, {}) - btn pos: ({}, {}) window pos: ({}, {})", btn.id, mouseX, mouseY, btn.gx(), btn.by, this.x, this.y);

                try {
                    btn.action.run();
                    buttonClicked = true;
                } catch (Exception e) {
                    LiveMessage.LOG.error("Error executing button action for button {}", btn.id, e);
                }
                break;
            }
        }

        if (!buttonClicked) {
            int inputFieldY = this.h - 13 - 2;
            if (this.mouseInRect(5, inputFieldY, this.w - 10, 13, mouseX, mouseY)) {
                this.inputField.setFocused(true);
            } else {
                this.inputField.setFocused(false);
            }

            if (this.chatHistory.size() > 1 && this.mouseInRect(5 + this.w - 10 - 10, this.chatBoxY, 10, this.h - (this.chatBoxY + 10 + 13), mouseX, mouseY)) {
                this.scrolling = true;
                int maxScroll = Math.max(0, this.chatHistory.size() - 1);
                if (maxScroll > 0) {
                    int availableScrollArea = this.h - (this.chatBoxY + 10 + 13) - this.scrollBarHeight;
                    int scrollY = this.chatBoxY + availableScrollArea * this.chatScrollPosition / maxScroll;
                    this.dragY = mouseY - (this.y + scrollY);
                }
            }

            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    private void openUrl(String url) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            Util.getPlatform().openUri(url);
            LiveMessage.LOG.info("Opening URL: {}", url);
        } catch (Exception e) {
            LiveMessage.LOG.error("Failed to open URL: {}", url, e);
        }
    }

    @Override
    public void activateWindow() {
        this.markAsRead();
        super.activateWindow();
    }

    public void markAsRead() {
        LivemessageGui.unreadMessages.put(this.liveProfile.uuid, 0);
    }

    private void drawChatHistory(GuiGraphics context, int chatBoxX, int chatBoxY, int chatColorMe, int chatColorOther) {
        this.clickableLinks.clear();
        if (this.chatHistory.size() == 0) {
            this.drawText(context, "You're chatting with " + this.liveProfile.username, chatBoxX + 4, chatBoxY + 5, GuiUtil.getSingleRGB(96), false);
            this.chatScrolledToBottom = false;
        } else {
            int drawHeight = 0;
            this.chatScrolledToBottom = true;
            DateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy");
            DateFormat timeFormat = new SimpleDateFormat("<HH:mm> ");
            String lastDay = dateFormat.format(new Date(System.currentTimeMillis()));

            for (int i = this.chatScrollPosition; i < this.chatHistory.size(); i++) {
                ChatWindow.ChatMessage chatMessage = this.chatHistory.get(i);
                boolean isTrimmed = false;
                String message = chatMessage.message;
                Date timestamp = new Date(chatMessage.timestamp);

                while (true) {
                    if (chatBoxY + 5 + 12 * drawHeight > this.h - 34) {
                        this.chatScrolledToBottom = false;
                        break;
                    }

                    String thisDay = dateFormat.format(timestamp);
                    if (!thisDay.equals(lastDay)) {
                        lastDay = thisDay;
                        this.drawText(context, lastDay, chatBoxX + 4, chatBoxY + 5 + 12 * drawHeight, GuiUtil.getSingleRGB(64), false);
                        drawHeight++;
                    } else {
                        if (!isTrimmed) {
                            message = timeFormat.format(timestamp) + message;
                        }

                        int maxWidth = this.w - (chatBoxX * 2 + 8 + (isTrimmed ? this.getTextWidth("<00:00> ") : 0) + 10 - 5);
                        String trimmed = this.fontRenderer.plainSubstrByWidth(message, maxWidth);
                        int baseX = chatBoxX + 4 + (isTrimmed ? this.getTextWidth("<00:00> ") : 0);
                        int baseY = chatBoxY + 5 + 12 * drawHeight;
                        int baseColor = chatMessage.sentByMe ? chatColorMe : chatColorOther;
                        this.drawTextWithUrls(context, trimmed, baseX, baseY, baseColor);
                        drawHeight++;
                        if (message.equals(trimmed)) {
                            break;
                        }

                        message = message.substring(trimmed.length());
                        isTrimmed = true;
                    }
                }

                if (!this.chatScrolledToBottom) {
                    break;
                }
            }

            if (this.chatScrolledToBottom && chatBoxY + 5 + 12 * (drawHeight + 2) <= this.h - 34) {
                this.chatScrolledToBottom = false;
            }
        }
    }

    private void drawTextWithUrls(GuiGraphics context, String text, int x, int y, int baseColor) {
        Matcher matcher = URL_PATTERN.matcher(text);
        int lastEnd = 0;
        int currentX = x;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String beforeUrl = text.substring(lastEnd, matcher.start());
                this.drawText(context, beforeUrl, currentX, y, baseColor, false);
                currentX += this.getTextWidth(beforeUrl);
            }

            String url = matcher.group();
            int urlWidth = this.getTextWidth(url);
            boolean hovering = this.lastMouseX >= this.x + currentX
                && this.lastMouseX <= this.x + currentX + urlWidth
                && this.lastMouseY >= this.y + y
                && this.lastMouseY <= this.y + y + this.getTextHeight();
            int urlColor = hovering ? GuiUtil.getRGB(100, 200, 255) : GuiUtil.getRGB(85, 170, 255);
            this.drawText(context, url, currentX, y, urlColor, true);
            GuiUtil.drawRect(context, currentX, y + this.getTextHeight() - 1, urlWidth, 1, urlColor);
            this.clickableLinks.add(new ChatWindow.ClickableLink(url, this.x + currentX, this.y + y, urlWidth, this.getTextHeight()));
            currentX += urlWidth;
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String afterUrl = text.substring(lastEnd);
            this.drawText(context, afterUrl, currentX, y, baseColor, false);
        }

        if (lastEnd == 0) {
            this.drawText(context, text, x, y, baseColor, false);
        }
    }

    public boolean shouldDrawBlur() {
        boolean removeHat = this.lastMouseX > this.x + 5
            && this.lastMouseX < this.x + 37
            && this.lastMouseY > this.y + titlebarHeight + 5
            && this.lastMouseY < this.y + titlebarHeight + 37;
        float progress = this.fullSkinAnim.animate(removeHat && this.clicked && !this.dragging && !this.resizing && !this.scrolling ? 1.0F : 0.0F);
        return progress > 0.0F;
    }

    public int getBlurAlpha() {
        boolean removeHat = this.lastMouseX > this.x + 5
            && this.lastMouseX < this.x + 37
            && this.lastMouseY > this.y + titlebarHeight + 5
            && this.lastMouseY < this.y + titlebarHeight + 37;
        float progress = this.fullSkinAnim.animate(removeHat && this.clicked && !this.dragging && !this.resizing && !this.scrolling ? 1.0F : 0.0F);
        return (int)(progress * 128.0F);
    }

    private void drawProfilePic(GuiGraphics context, int x, int y) {
        boolean removeHat = this.lastMouseX > this.x + x
            && this.lastMouseX < this.x + x + 32
            && this.lastMouseY > this.y + y
            && this.lastMouseY < this.y + y + 32;
        float progress = this.fullSkinAnim.animate(removeHat && this.clicked && !this.dragging && !this.resizing && !this.scrolling ? 1.0F : 0.0F);
        int displaySize = Math.round(32.0F + progress * 224.0F);
        int displayX = Math.round(x - progress * 32.0F);
        int displayY = Math.round(y - progress * 32.0F);
        PlayerInfo entry = this.mc.getConnection().getPlayerInfo(this.liveProfile.uuid);
        if (entry != null) {
            PlayerFaceRenderer.draw(context, entry.getSkin(), displayX, displayY, displaySize, GuiUtil.fade(-1));
        }
    }

    @Override
    public void drawWindow(GuiGraphics context, int bgColor, int fgColor) {
        boolean online = LivemessageUtil.checkOnlineStatus(this.liveProfile.uuid);
        this.title = "[DM] " + this.liveProfile.username;
        int unreads = LivemessageGui.unreadMessages.getOrDefault(this.liveProfile.uuid, 0);
        if (unreads > 0) {
            this.title = this.title + " §l(" + unreads + ")";
        }

        this.scrollBarHeight = this.chatHistory.size() < 2
            ? 0
            : (int)Mth.clamp(
                Math.floor((this.h - (this.chatBoxY + 10 + 13)) / Math.max((this.chatHistory.size() - 1) / 10, 1)),
                10.0,
                (this.h - (this.chatBoxY + 10 + 13)) / 2
            );
        super.drawWindow(context, bgColor, fgColor);
        GuiUtil.drawRect(context, 3, titlebarHeight + 3, 36, 36, online ? GuiUtil.getRGB(60, 148, 100) : GuiUtil.getSingleRGB(128));
        if (this.lastMouseX > this.x + 40
            && this.lastMouseX < this.x + 40 + this.getTextWidth(this.liveProfile.username) + 4
            && this.lastMouseY > this.y + titlebarHeight + 3
            && this.lastMouseY < this.y + titlebarHeight + 4 + 12) {
            GuiUtil.drawRect(context, 40, titlebarHeight + 3, this.getTextWidth(this.liveProfile.username) + 4, 12, GuiUtil.getSingleRGB(64));
        }

        String displayUsername = this.liveProfile.username;
        int usernameColor = GuiUtil.getSingleRGB(255);
        boolean isFriend = Friends.get().get(this.liveProfile.username) != null;
        EnemyManager enemyManager = EnemyManager.get();
        boolean isEnemy = enemyManager.isEnemy(this.liveProfile.username);
        if (isFriend) {
            displayUsername = displayUsername + " (friend)";
            usernameColor = GuiUtil.getRGB(85, 255, 85);
        } else if (isEnemy) {
            displayUsername = displayUsername + " (enemy)";
            usernameColor = GuiUtil.getRGB(255, 85, 85);
        }

        this.drawText(context, displayUsername, 42, titlebarHeight + 5, usernameColor, false);
        this.drawText(context, this.liveProfile.uuid.toString(), 42, titlebarHeight + 5 + 11, GuiUtil.getSingleRGB(128), false);
        this.drawText(context, online ? "online" : "offline", 42, titlebarHeight + 5 + 21, GuiUtil.getSingleRGB(128), false);
        this.liveButtons.forEach(btn -> btn.draw(context));
        int chatbg = 36;
        int textbg = 24;
        GuiUtil.drawRect(context, 4, this.chatBoxY - 1, this.w - 10 + 2, this.h - (this.chatBoxY + 10 + 13) + 2, GuiUtil.getSingleRGB(64));
        GuiUtil.drawRect(context, 5, this.chatBoxY, this.w - 10, this.h - (this.chatBoxY + 10 + 13), GuiUtil.getSingleRGB(chatbg));
        int inputBorderColor = online ? GuiUtil.getSingleRGB(64) : GuiUtil.getRGB(200, 50, 50);
        int inputBgColor = online ? GuiUtil.getSingleRGB(textbg) : GuiUtil.getRGB(40, 20, 20);
        GuiUtil.drawRect(context, 4, this.chatBoxY - 1 + this.h - (this.chatBoxY + 5 + 13), this.w - 10 + 2, 15, inputBorderColor);
        GuiUtil.drawRect(context, 5, this.chatBoxY + this.h - (this.chatBoxY + 5 + 13), this.w - 10, 13, inputBgColor);
        if (!online) {
            String warningIcon = "§l!";
            int iconX = 5 + this.w - 10 - this.getTextWidth(warningIcon) - 3;
            int iconY = this.chatBoxY + this.h - (this.chatBoxY + 5 + 13) + 2;
            this.drawText(context, warningIcon, iconX + 1, iconY, GuiUtil.getRGB(100, 20, 20), false);
            this.drawText(context, warningIcon, iconX, iconY, GuiUtil.getRGB(255, 85, 85), false);
        }

        if (this.chatHistory.size() > 1) {
            int maxScroll = Math.max(0, this.chatHistory.size() - 1);
            int availableScrollArea = this.h - (this.chatBoxY + 10 + 13) - this.scrollBarHeight;
            int scrollY = this.chatBoxY + (maxScroll > 0 ? availableScrollArea * this.chatScrollPosition / maxScroll : 0);
            GuiUtil.drawRect(
                context,
                5 + this.w - 10 - 10,
                scrollY,
                10,
                this.scrollBarHeight,
                this.scrolling
                    ? GuiUtil.getSingleRGB(128)
                    : (
                        this.mouseInRect(5 + this.w - 10 - 10, this.chatBoxY, 10, this.h - (this.chatBoxY + 10 + 13), this.lastMouseX, this.lastMouseY)
                            ? GuiUtil.getSingleRGB(96)
                            : GuiUtil.getSingleRGB(64)
                    )
            );
        }

        int otherPlayerColor;
        if (isFriend) {
            otherPlayerColor = GuiUtil.getRGB(85, 255, 85);
        } else if (isEnemy) {
            otherPlayerColor = GuiUtil.getRGB(255, 85, 85);
        } else {
            otherPlayerColor = fgColor;
        }

        this.drawChatHistory(context, 5, this.chatBoxY, GuiUtil.getSingleRGB(255), otherPlayerColor);
        this.drawProfilePic(context, 5, titlebarHeight + 5);
        this.liveButtons.forEach(btn -> btn.drawTooltips(context));
    }

    @Override
    public void drawTextFields(GuiGraphics context) {
        context.pose().translate(this.x, this.y);
        this.inputField.setTextColor(this.active ? -1 : -8355712);
        this.inputField.setX(8);
        this.inputField.setY(this.h - 13 - 2);
        this.inputField.setWidth(this.w - 18);
        this.inputField.render(context, this.lastMouseX - this.x, this.lastMouseY - this.y, 0.0F);
        context.pose().translate(-this.x, -this.y);
    }

    public static class ChatMessage {
        public String message;
        public boolean sentByMe;
        public long timestamp;
        public UUID myUUID;

        ChatMessage(String message, boolean sentByMe, long timestamp) {
            this.message = message;
            this.sentByMe = sentByMe;
            this.timestamp = timestamp;
        }

        ChatMessage(String message, boolean sentByMe, long timestamp, UUID myUUID) {
            this(message, sentByMe, timestamp);
            this.myUUID = myUUID;
        }
    }

    public static class ClickableLink {
        public String url;
        public int x;
        public int y;
        public int width;
        public int height;

        ClickableLink(String url, int x, int y, int width, int height) {
            this.url = url;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean contains(int mouseX, int mouseY) {
            return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
        }
    }
}
