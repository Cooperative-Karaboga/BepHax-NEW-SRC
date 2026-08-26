package bep.hax.mixin.meteor;

import bep.hax.util.Utils;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.misc.Notifier;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.util.ArrayListDeque;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Notifier.class, remap = false)
public abstract class NotifierMixin extends Module {
    @Shadow
    @Final
    private SettingGroup sgJoinsLeaves;
    @Shadow
    @Final
    private ArrayListDeque<Component> messageQueue;
    @Unique
    @Nullable
    private Setting<Boolean> greeterNotifications = null;
    @Unique
    @Nullable
    private Setting<Utils.TextFormat> notificationFormatting = null;
    @Unique
    @Nullable
    private Setting<Boolean> highlightMentions = null;
    @Unique
    @Nullable
    private Setting<Boolean> mentionSound = null;
    @Unique
    @Nullable
    private Setting<Double> mentionVolume = null;
    @Unique
    private final String[] prefixGreetings = new String[]{"Hello", "Hi", "Welcome", "Howdy", "Yo", "Sup", "Greetings", "Hey", "Hola", "Hiya"};
    @Unique
    private final String[] suffixGreetings = new String[]{
        "joined", "arrived", "showed up", "connected", "appeared", "is here", "came through", "made it", "logged in", "logged on", "pulled up"
    };
    @Unique
    private final String[] prefixFarewells = new String[]{
        "Goodbye", "Bye", "Sayonara", "Later", "Cya", "Farewell", "Adios", "Catch you later", "So long", "Take care", "Toodles", "See ya", "See you later"
    };
    @Unique
    private final String[] suffixFarewells = new String[]{"left", "disconnected", "peaced out", "logged out", "departed", "signed off", "split", "logged off"};

    public NotifierMixin(Category category, String name, String desc) {
        super(category, name, desc);
    }

    @Inject(
        method = "<init>",
        at = @At(
            value = "FIELD",
            target = "Lmeteordevelopment/meteorclient/systems/modules/misc/Notifier;simpleNotifications:Lmeteordevelopment/meteorclient/settings/Setting;",
            shift = Shift.AFTER
        )
    )
    private void addGreeterSettings(CallbackInfo ci) {
        this.greeterNotifications = this.sgJoinsLeaves
            .add(
                new Builder()
                    .name("greeter-style-notifications")
                    .description("Use greeter-style messages for join/leave notifications.")
                    .defaultValue(true)
                    .build()
            );
        this.notificationFormatting = this.sgJoinsLeaves
            .add(
                ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                                    .name("greeter-notification-formatting"))
                                .description("Which text formatting to apply to greeter-style notifications."))
                            .defaultValue(Utils.TextFormat.Italic))
                        .visible(() -> this.greeterNotifications != null && this.greeterNotifications.get()))
                    .build()
            );
        this.highlightMentions = this.sgJoinsLeaves
            .add(new Builder().name("highlight-mentions").description("Highlights messages containing your name in bold.").defaultValue(true).build());
        this.mentionSound = this.sgJoinsLeaves
            .add(
                new Builder()
                    .name("mention-sound")
                    .description("Play a sound when your name is mentioned.")
                    .defaultValue(false)
                    .visible(() -> this.highlightMentions != null && this.highlightMentions.get())
                    .build()
            );
        this.mentionVolume = this.sgJoinsLeaves
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("mention-volume")
                    .description("Volume of the mention sound.")
                    .defaultValue(1.0)
                    .min(0.0)
                    .max(2.0)
                    .sliderMin(0.0)
                    .sliderMax(2.0)
                    .visible(() -> this.highlightMentions != null && this.highlightMentions.get() && this.mentionSound != null && this.mentionSound.get())
                    .build()
            );
    }

    @Inject(method = "createJoinNotifications", at = @At("HEAD"), cancellable = true)
    private void greetPlayers(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        if (this.greeterNotifications != null && this.greeterNotifications.get()) {
            ci.cancel();

            for (Entry entry : packet.newEntries()) {
                if (entry.profile() != null) {
                    String name = entry.profile().name();
                    String format = this.notificationFormatting == null ? "§o" : this.notificationFormatting.get().label;
                    int luckyInt = ThreadLocalRandom.current().nextInt(3);
                    if (luckyInt == 0) {
                        String greeting = this.suffixGreetings[ThreadLocalRandom.current().nextInt(this.suffixGreetings.length)];
                        this.messageQueue
                            .addLast(
                                Component.nullToEmpty("§8<" + Utils.rCC() + "§o✨§r§8> §a" + format + name + " §7" + format + greeting + "§a" + format + ".")
                            );
                    } else {
                        String greeting = this.prefixGreetings[ThreadLocalRandom.current().nextInt(this.prefixGreetings.length)];
                        this.messageQueue
                            .addLast(
                                Component.nullToEmpty("§8<" + Utils.rCC() + "§o✨§r§8> §7" + format + greeting + ", §a" + format + name + "§7" + format + ".")
                            );
                    }
                }
            }
        }
    }

    @Inject(method = "createLeaveNotification", at = @At("HEAD"), cancellable = true)
    private void bidFarewell(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
        if (this.mc.getConnection() != null && this.greeterNotifications != null && this.greeterNotifications.get()) {
            ci.cancel();

            for (UUID id : packet.profileIds()) {
                PlayerInfo player = this.mc.getConnection().getPlayerInfo(id);
                if (player != null) {
                    String name = player.getProfile().name();
                    String format = this.notificationFormatting == null ? "§o" : this.notificationFormatting.get().label;
                    int luckyInt = ThreadLocalRandom.current().nextInt(3);
                    if (luckyInt == 0) {
                        String farewell = this.suffixFarewells[ThreadLocalRandom.current().nextInt(this.suffixFarewells.length)];
                        this.messageQueue
                            .addLast(
                                Component.nullToEmpty("§8<" + Utils.rCC() + "§o✨§r§8> §c" + format + name + " §7" + format + farewell + "§c" + format + ".")
                            );
                    } else {
                        String farewell = this.prefixFarewells[ThreadLocalRandom.current().nextInt(this.prefixFarewells.length)];
                        this.messageQueue
                            .addLast(
                                Component.nullToEmpty("§8<" + Utils.rCC() + "§o✨§r§8> §7" + format + farewell + ", §c" + format + name + "§7" + format + ".")
                            );
                    }
                }
            }
        }
    }
}
