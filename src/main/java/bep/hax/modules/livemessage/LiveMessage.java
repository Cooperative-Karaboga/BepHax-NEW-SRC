package bep.hax.modules.livemessage;

import bep.hax.Bep;
import bep.hax.modules.livemessage.gui.ChatWindow;
import bep.hax.modules.livemessage.gui.LiveWindow;
import bep.hax.modules.livemessage.gui.LivemessageGui;
import bep.hax.modules.livemessage.gui.ManeWindow;
import bep.hax.modules.livemessage.irc.IrcConfig;
import bep.hax.modules.livemessage.irc.IrcWindow;
import bep.hax.modules.livemessage.util.LiveProfileCache;
import bep.hax.modules.livemessage.util.LiveSkinUtil;
import bep.hax.modules.livemessage.util.LivemessageUtil;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.settings.KeybindSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiveMessage extends Module {
    public static final Logger LOG = LoggerFactory.getLogger("Livemessage");
    public static LiveMessage INSTANCE;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgNotifications = this.settings.createGroup("Notifications");
    private final SettingGroup sgPatterns = this.settings.createGroup("Patterns");
    public final Setting<Keybind> openGuiKey = this.sgGeneral
        .add(new Builder().name("open-gui-key").description("Keybind to open Livemessage GUI.").defaultValue(Keybind.fromKey(89)).action(this::openGui).build());
    public final Setting<Boolean> hideMessages = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("hide-messages")
                .description("Hide DMs from main chat.")
                .defaultValue(true)
                .build()
        );
    public final Setting<Boolean> readOnReply = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("read-on-reply")
                .description("Mark messages as read when you reply.")
                .defaultValue(true)
                .build()
        );
    public final Setting<Double> guiScale = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("gui-scale")
                .description("GUI scale for Livemessage windows.")
                .defaultValue(1.0)
                .min(0.25)
                .max(8.0)
                .sliderRange(0.25, 4.0)
                .build()
        );
    public final Setting<Boolean> enableBlur = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("enable-blur")
                .description("Enable blur overlay when profile picture is enlarged.")
                .defaultValue(false)
                .build()
        );
    public final Setting<Boolean> fadeAnimation = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("fade-animation")
                .description("Smoothly fade the whole GUI in when you open it and out when you close it.")
                .defaultValue(true)
                .build()
        );
    public final Setting<Double> fadeDuration = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("fade-duration")
                .description("How long the GUI fade in/out takes, in seconds.")
                .defaultValue(0.2)
                .min(0.0)
                .sliderRange(0.0, 1.0)
                .visible(this.fadeAnimation::get)
                .build()
        );
    public final Setting<Integer> defaultChatWidth = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("default-chat-width")
                .description("Default width of chat windows.")
                .defaultValue(400)
                .min(200)
                .max(1000)
                .sliderMin(200)
                .sliderMax(800)
                .build()
        );
    public final Setting<Integer> defaultChatHeight = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("default-chat-height")
                .description("Default height of chat windows.")
                .defaultValue(300)
                .min(150)
                .max(800)
                .sliderMin(150)
                .sliderMax(600)
                .build()
        );
    public final Setting<Boolean> openOnChatKey = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("open-on-chat-key")
                .description("Open Livemessage GUI when pressing the chat key.")
                .defaultValue(false)
                .build()
        );
    public final Setting<Boolean> useMeteorFont = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("use-meteor-font")
                .description("[NOT IMPLEMENTED] Meteor font is incompatible with LiveMessage's GUI system.")
                .defaultValue(false)
                .visible(() -> false)
                .build()
        );
    public final Setting<Boolean> toastsEnabled = this.sgNotifications
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("toasts")
                .description("Show toast notifications for new DMs.")
                .defaultValue(true)
                .build()
        );
    public final Setting<Boolean> soundsEnabled = this.sgNotifications
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("sounds")
                .description("Play notification sounds for new DMs.")
                .defaultValue(true)
                .build()
        );
    public final Setting<String> fromPattern1 = this.sgPatterns
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("from-pattern-1")
                .description("Regex pattern for incoming DMs (group 1: username, group 2: message).")
                .defaultValue("^(?:<[^>]+> )?From (\\\\w{3,16}): (.*)")
                .build()
        );
    public final Setting<String> fromPattern2 = this.sgPatterns
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("from-pattern-2")
                .description("Regex pattern for incoming DMs (group 1: username, group 2: message).")
                .defaultValue("^(?:<[^>]+> )?from (\\\\w{3,16}): (.*)")
                .build()
        );
    public final Setting<String> fromPattern3 = this.sgPatterns
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("from-pattern-3")
                .description("Regex pattern for incoming DMs (group 1: username, group 2: message).")
                .defaultValue("^(?:<[^>]+> )?(\\\\w{3,16}) whispers: (.*)")
                .build()
        );
    public final Setting<String> fromPattern4 = this.sgPatterns
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("from-pattern-4")
                .description("Regex pattern for incoming DMs (group 1: username, group 2: message).")
                .defaultValue("^(?:<[^>]+> )?\\\\[(\\\\w{3,16}) -> me\\\\] (.*)")
                .build()
        );
    public final Setting<String> toPattern1 = this.sgPatterns
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("to-pattern-1")
                .description("Regex pattern for outgoing DMs (group 1: username, group 2: message).")
                .defaultValue("^(?:<[^>]+> )?To (\\\\w{3,16}): (.*)")
                .build()
        );
    public final Setting<String> toPattern2 = this.sgPatterns
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("to-pattern-2")
                .description("Regex pattern for outgoing DMs (group 1: username, group 2: message).")
                .defaultValue("^(?:<[^>]+> )?to (\\\\w{3,16}): (.*)")
                .build()
        );
    public final Setting<String> toPattern3 = this.sgPatterns
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("to-pattern-3")
                .description("Regex pattern for outgoing DMs (group 1: username, group 2: message).")
                .defaultValue("^(?:<[^>]+> )?\\\\[me -> (\\\\w{3,16})\\\\] (.*)")
                .build()
        );
    private final SettingGroup sgIrc = this.settings.createGroup("IRC");
    public final Setting<Boolean> ircEnabled = this.sgIrc
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("irc")
                .description("Enable IRC chat functionality.")
                .defaultValue(true)
                .build()
        );
    public final Setting<Boolean> ircUseCustomServer = this.sgIrc
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("use-custom-irc-server")
                .description("Connect to a custom IRC server instead of the official BepHax server.")
                .defaultValue(false)
                .onChanged(val -> IrcConfig.setUseCustomServer(val))
                .build()
        );
    public final Setting<String> ircServerUrl = this.sgIrc
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("irc-server-url")
                .description("WebSocket URL for custom IRC server.")
                .defaultValue("wss://irc.example.com")
                .visible(() -> this.ircUseCustomServer.get())
                .onChanged(val -> IrcConfig.setServerUrl(val))
                .build()
        );
    public final Setting<String> ircChannel = this.sgIrc
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("irc-channel")
                .description("IRC channel to join.")
                .defaultValue("#chat")
                .visible(() -> this.ircUseCustomServer.get())
                .onChanged(val -> IrcConfig.setChannel(val))
                .build()
        );
    public final Setting<String> ircChannelPassword = this.sgIrc
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("irc-channel-password")
                .description("Password for the IRC channel (leave empty if none).")
                .defaultValue("")
                .visible(() -> this.ircUseCustomServer.get())
                .onChanged(val -> IrcConfig.setChannelPassword(val))
                .build()
        );
    public final Setting<String> ircNickname = this.sgIrc
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("irc-nickname")
                .description("Nickname for custom IRC server (leave empty to use MC username).")
                .defaultValue("")
                .visible(() -> this.ircUseCustomServer.get())
                .onChanged(val -> IrcConfig.setNickname(val))
                .build()
        );
    public final Setting<String> ircServerPassword = this.sgIrc
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("irc-server-password")
                .description("Password for the IRC server (leave empty if none).")
                .defaultValue("")
                .visible(() -> this.ircUseCustomServer.get())
                .onChanged(val -> IrcConfig.setServerPassword(val))
                .build()
        );
    public final Setting<Boolean> ircShowJoinLeave = this.sgIrc
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("irc-show-join-leave")
                .description("Show join/leave messages in IRC.")
                .defaultValue(false)
                .onChanged(val -> IrcConfig.setShowJoinLeave(val))
                .build()
        );
    public final Setting<Boolean> ircSoundNotifications = this.sgIrc
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("irc-sound-notifications")
                .description("Play sound for IRC mentions.")
                .defaultValue(false)
                .onChanged(val -> IrcConfig.setSoundNotifications(val))
                .build()
        );
    public final Setting<Boolean> ircToastNotifications = this.sgIrc
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("irc-toast-notifications")
                .description("Show toast notifications for IRC mentions.")
                .defaultValue(false)
                .onChanged(val -> IrcConfig.setToastNotifications(val))
                .build()
        );

    public LiveMessage() {
        super(Bep.CATEGORY, "livemessage", "Advanced DM management system with GUI and IRC.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        LivemessageUtil.initFolders();
        IrcConfig.setShowJoinLeave(this.ircShowJoinLeave.get());
        IrcConfig.setSoundNotifications(this.ircSoundNotifications.get());
        IrcConfig.setToastNotifications(this.ircToastNotifications.get());
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        LiveProfileCache.cachedProfiles.clear();
        LiveProfileCache.cachedNames.clear();
        LiveProfileCache.badUUIDs.clear();
        LiveProfileCache.badNames.clear();
        LiveSkinUtil.clearCache();
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (this.openOnChatKey.get()) {
            if (event.screen instanceof ChatScreen) {
                event.cancel();
                this.mc.setScreen(new LivemessageGui());
            }
        }
    }

    public void openGui() {
        if (this.mc.screen == null) {
            this.mc.setScreen(new LivemessageGui());
        } else if (this.mc.screen instanceof LivemessageGui gui) {
            boolean anyFieldFocused = false;

            for (LiveWindow window : LivemessageGui.liveWindows) {
                if (window instanceof ChatWindow chatWindow) {
                    if (chatWindow.inputField != null && chatWindow.inputField.isFocused()) {
                        anyFieldFocused = true;
                        break;
                    }
                } else if (window instanceof ManeWindow maneWindow) {
                    if (ManeWindow.searchField != null && ManeWindow.searchField.isFocused()) {
                        anyFieldFocused = true;
                        break;
                    }
                } else if (window instanceof IrcWindow ircWindow && ircWindow.inputField != null && ircWindow.inputField.isFocused()) {
                    anyFieldFocused = true;
                    break;
                }
            }

            if (!anyFieldFocused) {
                gui.onClose();
            }
        }
    }
}
