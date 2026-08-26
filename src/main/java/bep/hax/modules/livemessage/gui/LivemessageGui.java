package bep.hax.modules.livemessage.gui;

import bep.hax.modules.livemessage.LiveMessage;
import bep.hax.modules.livemessage.irc.IrcWindow;
import bep.hax.modules.livemessage.util.LiveProfileCache;
import bep.hax.modules.livemessage.util.LivemessageUtil;
import bep.hax.util.FadeAnimator;
import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class LivemessageGui extends Screen {
    public static List<UUID> chats = new CopyOnWriteArrayList<>();
    public static List<UUID> recentChats = new CopyOnWriteArrayList<>();
    public static Map<UUID, Integer> unreadMessages = new ConcurrentHashMap<>();
    public static List<LiveWindow> liveWindows = new CopyOnWriteArrayList<>();
    private static LiveWindow lastActiveChatWindow = null;
    public static double scl = 1.0;
    public static int screenHeight = 600;
    public static int screenWidth = 800;
    private LiveWindow activeWindow = null;
    public static float currentFadeAlpha = 1.0F;
    private final FadeAnimator fade = new FadeAnimator();
    private boolean closing = false;

    public LivemessageGui() {
        super(Component.literal("Livemessage"));
        if (this.minecraft != null) {
            this.setScl();
        }
    }

    public static void loadBuddies() {
        chats.clear();
        File folder = LivemessageUtil.MESSAGES_FOLDER.toFile();
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    UUID uuid = UUID.fromString(file.getName().substring(0, 36));
                    chats.add(uuid);
                }
            }
        }

        Collections.sort(chats);
    }

    public void setScl() {
        double guiScale = LiveMessage.INSTANCE.guiScale.get();
        scl = 1.0 / guiScale;
        screenHeight = (int)(this.minecraft.getWindow().getGuiScaledHeight() / guiScale);
        screenWidth = (int)(this.minecraft.getWindow().getGuiScaledWidth() / guiScale);
    }

    @Override
    protected void init() {
        this.setScl();
        loadBuddies();
        if (liveWindows.isEmpty()) {
            liveWindows.add(new ManeWindow());
        }

        this.restoreLastActiveChatWindow();
    }

    @Override
    public void removed() {
        this.activeWindow = null;
        this.saveLastActiveChatWindow();

        for (LiveWindow window : liveWindows) {
            if (window instanceof ChatWindow chatWindow) {
                if (chatWindow.inputField != null) {
                    chatWindow.inputField.setFocused(false);
                }
            } else if (window instanceof ManeWindow) {
                if (ManeWindow.searchField != null) {
                    ManeWindow.searchField.setFocused(false);
                }
            } else if (window instanceof IrcWindow ircWindow && ircWindow.inputField != null) {
                ircWindow.inputField.setFocused(false);
            }
        }

        super.removed();
    }

    private void saveLastActiveChatWindow() {
        if (!liveWindows.isEmpty()) {
            LiveWindow topWindow = liveWindows.get(liveWindows.size() - 1);
            if (topWindow instanceof ChatWindow || topWindow instanceof IrcWindow) {
                lastActiveChatWindow = topWindow;
            }
        }
    }

    private void restoreLastActiveChatWindow() {
        if (lastActiveChatWindow != null && liveWindows.contains(lastActiveChatWindow)) {
            liveWindows.get(liveWindows.size() - 1).deactivateWindow();
            liveWindows.remove(lastActiveChatWindow);
            liveWindows.add(lastActiveChatWindow);
            lastActiveChatWindow.activateWindow();
            if (lastActiveChatWindow instanceof ChatWindow chatWindow) {
                if (chatWindow.inputField != null) {
                    chatWindow.inputField.setFocused(true);
                }
            } else if (lastActiveChatWindow instanceof IrcWindow ircWindow && ircWindow.inputField != null) {
                ircWindow.inputField.setFocused(true);
            }
        }
    }

    public static void openChatWindow(UUID uuid) {
        if (uuid != null) {
            liveWindows.get(liveWindows.size() - 1).deactivateWindow();

            for (LiveWindow liveWindow : liveWindows) {
                if (liveWindow instanceof ChatWindow chatWindow && chatWindow.liveProfile.uuid.equals(uuid)) {
                    chatWindow.activateWindow();
                    liveWindows.removeIf(it -> it == chatWindow);
                    liveWindows.add(chatWindow);
                    return;
                }
            }

            addChatWindow(new ChatWindow(uuid));
        }
    }

    private static void addChatWindow(ChatWindow chatWindow) {
        if (chatWindow.valid) {
            liveWindows.add(chatWindow);
        } else {
            liveWindows.get(liveWindows.size() - 1).activateWindow();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        liveWindows.get(liveWindows.size() - 1).mouseWheel((int)verticalAmount);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.activeWindow != null) {
            int virtualX = (int)(click.x() / LiveMessage.INSTANCE.guiScale.get());
            int virtualY = (int)(click.y() / LiveMessage.INSTANCE.guiScale.get());
            this.activeWindow.handleMouseDrag(virtualX, virtualY);
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!liveWindows.isEmpty()) {
            int virtualX = (int)(mouseX / LiveMessage.INSTANCE.guiScale.get());
            int virtualY = (int)(mouseY / LiveMessage.INSTANCE.guiScale.get());
            liveWindows.get(liveWindows.size() - 1).mouseMove(virtualX, virtualY);
        }

        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (!liveWindows.isEmpty()) {
            double guiScale = LiveMessage.INSTANCE.guiScale.get();
            int virtualX = (int)(click.x() / guiScale);
            int virtualY = (int)(click.y() / guiScale);
            int button = click.button();
            LiveWindow clickedWindow = null;

            for (int i = liveWindows.size() - 1; i >= 0; i--) {
                LiveWindow liveWindow = liveWindows.get(i);
                if (liveWindow.mouseInWindow(virtualX, virtualY)) {
                    clickedWindow = liveWindow;
                    if (i != liveWindows.size() - 1) {
                        liveWindows.get(liveWindows.size() - 1).deactivateWindow();
                        liveWindow.activateWindow();
                        liveWindows.remove(i);
                        liveWindows.add(liveWindow);
                    }
                    break;
                }
            }

            if (clickedWindow != null) {
                this.activeWindow = clickedWindow;
                this.activeWindow.mouseClicked(virtualX, virtualY, button);
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (this.activeWindow != null) {
            int virtualX = (int)(click.x() / LiveMessage.INSTANCE.guiScale.get());
            int virtualY = (int)(click.y() / LiveMessage.INSTANCE.guiScale.get());
            this.activeWindow.mouseReleased(virtualX, virtualY, click.button());
            this.activeWindow = null;
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (!liveWindows.isEmpty()) {
            LiveWindow activeWindow = liveWindows.get(liveWindows.size() - 1);
            activeWindow.handleKeyInput(input);
            activeWindow.keyTyped('\u0000', input.key());
        }

        if (this.isAnyTextFieldFocused()) {
            return input.key() == 256 ? super.keyPressed(input) : true;
        } else {
            return super.keyPressed(input);
        }
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (!liveWindows.isEmpty()) {
            LiveWindow activeWindow = liveWindows.get(liveWindows.size() - 1);
            activeWindow.handleCharInput(input);
            activeWindow.keyTyped((char)input.codepoint(), 0);
        }

        return this.isAnyTextFieldFocused() ? true : super.charTyped(input);
    }

    public static boolean newMessage(String username, String message, boolean sentByMe) {
        LiveProfileCache.LiveProfile profile = LiveProfileCache.getLiveprofileFromName(username);
        if (profile == null) {
            return false;
        }

        UUID uuid = profile.uuid;
        boolean doHide = false;
        if (uuid != null) {
            try {
                Gson gson = new Gson();
                FileWriter fw = new FileWriter(LivemessageUtil.MESSAGES_FOLDER.resolve(uuid.toString() + ".jsonl").toFile(), true);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(
                    gson.toJson(new ChatWindow.ChatMessage(message, sentByMe, System.currentTimeMillis(), Minecraft.getInstance().player.getUUID()))
                );
                bw.newLine();
                bw.close();
            } catch (Exception e) {
                LiveMessage.LOG.error("Failed to write message to history file for UUID: {}", uuid, e);
            }

            if (!chats.contains(uuid)) {
                chats.add(uuid);
                Collections.sort(chats);
            }

            recentChats.remove(uuid);
            recentChats.add(0, uuid);
            if (recentChats.size() > 10) {
                recentChats.remove(recentChats.size() - 1);
            }

            if (!sentByMe) {
                unreadMessages.put(uuid, unreadMessages.getOrDefault(uuid, 0) + 1);
                if (LiveMessage.INSTANCE.toastsEnabled.get()) {
                    Minecraft mc = Minecraft.getInstance();
                    ToastManager toastManager = mc.getToastManager();
                    toastManager.addToast(
                        new SystemToast(SystemToastId.NARRATOR_TOGGLE, Component.literal("DM from " + username), Component.literal(message))
                    );
                }

                if (LiveMessage.INSTANCE.soundsEnabled.get()) {
                    Minecraft.getInstance().player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                }
            } else if (LiveMessage.INSTANCE.readOnReply.get()) {
                unreadMessages.put(uuid, 0);
            }

            if (LiveMessage.INSTANCE.hideMessages.get()) {
                doHide = true;
            }
        }

        for (LiveWindow liveWindow : liveWindows) {
            if (liveWindow instanceof ChatWindow chatWindow && username.equals(chatWindow.liveProfile.username)) {
                chatWindow.chatHistory.add(new ChatWindow.ChatMessage(message, sentByMe, System.currentTimeMillis()));
                break;
            }
        }

        return doHide;
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (LiveMessage.INSTANCE != null && LiveMessage.INSTANCE.enableBlur.get()) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void onClose() {
        boolean fadeEnabled = LiveMessage.INSTANCE != null && LiveMessage.INSTANCE.fadeAnimation.get();
        if (fadeEnabled && !this.closing && currentFadeAlpha > 0.001F) {
            this.closing = true;
        } else {
            super.onClose();
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        boolean fadeEnabled = LiveMessage.INSTANCE != null && LiveMessage.INSTANCE.fadeAnimation.get();
        double fadeDur = LiveMessage.INSTANCE != null ? LiveMessage.INSTANCE.fadeDuration.get() : 0.2;
        this.fade.update(!this.closing, fadeDur, fadeEnabled);
        currentFadeAlpha = fadeEnabled ? this.fade.alpha() : 1.0F;
        if (!this.closing || fadeEnabled && !(this.fade.alpha() <= 0.001F)) {
            float reverseGuiScale = (float)(1.0 / scl);
            if (LiveMessage.INSTANCE != null && LiveMessage.INSTANCE.enableBlur.get()) {
                boolean shouldDrawBlur = false;
                int blurAlpha = 0;

                for (LiveWindow liveWindow : liveWindows) {
                    if (liveWindow instanceof ChatWindow chatWindow) {
                        if (chatWindow.shouldDrawBlur()) {
                            shouldDrawBlur = true;
                            blurAlpha = chatWindow.getBlurAlpha();
                            break;
                        }
                    } else if (liveWindow instanceof ManeWindow maneWindow && maneWindow.shouldDrawBlur()) {
                        shouldDrawBlur = true;
                        blurAlpha = maneWindow.getBlurAlpha();
                        break;
                    }
                }

                if (shouldDrawBlur) {
                    context.fill(0, 0, screenWidth, screenHeight, GuiUtil.fade(GuiUtil.getRGBA(0, 0, 0, blurAlpha)));
                }
            }

            context.pose().scale(reverseGuiScale, reverseGuiScale);

            for (LiveWindow liveWindow : liveWindows) {
                liveWindow.preDrawWindow(context);
                liveWindow.drawTextFields(context);
            }

            context.pose().scale((float)scl, (float)scl);
        } else {
            this.closing = false;
            currentFadeAlpha = 1.0F;
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public GuiEventListener getFocused() {
        EditBox focused = this.getFocusedTextField();
        return focused != null ? focused : super.getFocused();
    }

    private EditBox getFocusedTextField() {
        for (LiveWindow window : liveWindows) {
            if (window instanceof ChatWindow chatWindow) {
                if (chatWindow.inputField != null && chatWindow.inputField.isFocused()) {
                    return chatWindow.inputField;
                }
            } else if (window instanceof ManeWindow) {
                if (ManeWindow.searchField != null && ManeWindow.searchField.isFocused()) {
                    return ManeWindow.searchField;
                }
            } else if (window instanceof IrcWindow ircWindow && ircWindow.inputField != null && ircWindow.inputField.isFocused()) {
                return ircWindow.inputField;
            }
        }

        return null;
    }

    public boolean isAnyTextFieldFocused() {
        return this.getFocusedTextField() != null;
    }
}
