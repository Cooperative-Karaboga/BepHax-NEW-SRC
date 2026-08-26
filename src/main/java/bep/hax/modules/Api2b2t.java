package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.commands.PlayerToast;
import java.util.Timer;
import java.util.TimerTask;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.network.chat.Component;

public class Api2b2t extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public final Setting<Api2b2t.DisplayMode> displayMode = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("display-mode")).description("How to display API responses."))
                    .defaultValue(Api2b2t.DisplayMode.Toast))
                .build()
        );
    public final Setting<Boolean> showPrefix = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-prefix")
                .description("Show the [2b2t] prefix in messages.")
                .defaultValue(true)
                .build()
        );
    public final Setting<Integer> toastDuration = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("toast-duration")
                .description("How long toasts are displayed (in seconds).")
                .defaultValue(6)
                .min(1)
                .max(30)
                .sliderMax(30)
                .visible(() -> this.displayMode.get() == Api2b2t.DisplayMode.Toast || this.displayMode.get() == Api2b2t.DisplayMode.Both)
                .build()
        );
    public final Setting<Integer> actionBarDuration = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("action-bar-duration")
                .description("How long action bar messages are displayed (in seconds).")
                .defaultValue(10)
                .min(1)
                .max(30)
                .sliderMax(30)
                .visible(() -> this.displayMode.get() == Api2b2t.DisplayMode.ActionBar || this.displayMode.get() == Api2b2t.DisplayMode.Both)
                .build()
        );
    private static Api2b2t instance;

    public Api2b2t() {
        super(Bep.CATEGORY, "2b2t-API", "Controls how 2b2t.vc API responses are displayed in-game.");
    }

    public void sendApiMessage(String message) {
        this.sendApiMessage(message, null);
    }

    public void sendApiMessage(String message, String playerName) {
        if (this.mc.player != null) {
            this.mc.execute(() -> {
                String prefix = this.showPrefix.get() ? "§8[§a2b2t§8] " : "";
                switch ((Api2b2t.DisplayMode)this.displayMode.get()) {
                    case Chat:
                        this.sendChatMessages(prefix, message);
                        break;
                    case ActionBar:
                        this.sendActionBarMessage(prefix, message);
                        break;
                    case Toast:
                        this.sendToastMessage(message, playerName);
                        break;
                    case Both:
                        this.sendChatMessages(prefix, message);
                        this.sendActionBarMessage(prefix, message);
                }
            });
        }
    }

    public void sendApiError(String message) {
        if (this.mc.player != null) {
            this.mc.execute(() -> {
                String prefix = this.showPrefix.get() ? "§8[§c2b2t§8] " : "";
                switch ((Api2b2t.DisplayMode)this.displayMode.get()) {
                    case Chat:
                        this.sendChatMessages(prefix, "§c" + message);
                        break;
                    case ActionBar:
                        this.sendActionBarMessage(prefix, "§c" + message);
                        break;
                    case Toast:
                        this.sendErrorToast(message);
                        break;
                    case Both:
                        this.sendChatMessages(prefix, "§c" + message);
                        this.sendActionBarMessage(prefix, "§c" + message);
                }
            });
        }
    }

    private void sendChatMessages(String prefix, String message) {
        if (this.mc.player != null) {
            String[] lines = message.split("\n");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    if (i == 0) {
                        this.mc.player.displayClientMessage(Component.literal(prefix + line), false);
                    } else {
                        this.mc.player.displayClientMessage(Component.literal(line), false);
                    }
                }
            }
        }
    }

    private void sendActionBarMessage(String prefix, String message) {
        if (this.mc.player != null) {
            String condensed = message.replace("\n", " §8| ").trim();

            while (condensed.contains("§8| §8|")) {
                condensed = condensed.replace("§8| §8|", "§8|");
            }

            final String finalMessage = prefix + condensed;
            final int durationSeconds = this.actionBarDuration.get();
            this.mc.player.displayClientMessage(Component.literal(finalMessage), true);
            final Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                int elapsed = 0;

                @Override
                public void run() {
                    this.elapsed++;
                    if (this.elapsed < durationSeconds && Api2b2t.this.mc.player != null) {
                        Api2b2t.this.mc.execute(() -> {
                            if (Api2b2t.this.mc.player != null) {
                                Api2b2t.this.mc.player.displayClientMessage(Component.literal(finalMessage), true);
                            }
                        });
                    } else {
                        timer.cancel();
                    }
                }
            }, 1000L, 1000L);
        }
    }

    private void sendToastMessage(String message, String playerName) {
        if (this.mc.getToastManager() != null) {
            String[] lines = message.split("\n");
            String title;
            String body;
            if (lines.length == 1) {
                String stripped = this.stripColors(lines[0].trim());
                int colonIndex = stripped.indexOf(58);
                if (colonIndex > 0 && colonIndex < stripped.length() - 1) {
                    title = stripped.substring(0, colonIndex).trim();
                    body = stripped.substring(colonIndex + 1).trim();
                } else {
                    title = stripped;
                    body = null;
                }
            } else {
                title = this.stripColors(lines[0].trim());
                StringBuilder bodyBuilder = new StringBuilder();

                for (int i = 1; i < lines.length; i++) {
                    String line = this.stripColors(lines[i].trim());
                    if (!line.isEmpty()) {
                        if (bodyBuilder.length() > 0) {
                            bodyBuilder.append("\n");
                        }

                        bodyBuilder.append(line);
                    }
                }

                body = bodyBuilder.length() > 0 ? bodyBuilder.toString() : null;
            }

            long duration = this.toastDuration.get().intValue() * 1000L;
            this.mc.getToastManager().addToast(PlayerToast.create(title, body, playerName, duration));
        }
    }

    private void sendErrorToast(String message) {
        if (this.mc.getToastManager() != null) {
            this.mc
                .getToastManager()
                .addToast(new SystemToast(SystemToastId.PERIODIC_NOTIFICATION, Component.literal("2b2t Error"), Component.literal(this.stripColors(message))));
        }
    }

    private String stripColors(String text) {
        return text.replaceAll("§[0-9a-fklmnor]", "");
    }

    public static Api2b2t get() {
        if (instance == null) {
            instance = Modules.get().get(Api2b2t.class);
        }

        return instance;
    }

    public static void sendMessage(String message) {
        sendMessage(message, null);
    }

    public static void sendMessage(String message, String playerName) {
        Api2b2t module = get();
        if (module != null && module.isActive()) {
            module.sendApiMessage(message, playerName);
        } else {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.execute(() -> {
                    String[] lines = message.split("\n");

                    for (int i = 0; i < lines.length; i++) {
                        String line = lines[i].trim();
                        if (!line.isEmpty()) {
                            if (i == 0) {
                                client.player.displayClientMessage(Component.literal("§8[§a2b2t§8] " + line), false);
                            } else {
                                client.player.displayClientMessage(Component.literal(line), false);
                            }
                        }
                    }
                });
            }
        }
    }

    public static void sendError(String message) {
        Api2b2t module = get();
        if (module != null && module.isActive()) {
            module.sendApiError(message);
        } else {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.execute(() -> client.player.displayClientMessage(Component.literal("§8[§c2b2t§8] §c" + message), false));
            }
        }
    }

    @Override
    public void onActivate() {
        instance = this;
    }

    public enum DisplayMode {
        Chat,
        ActionBar,
        Toast,
        Both;
    }
}
