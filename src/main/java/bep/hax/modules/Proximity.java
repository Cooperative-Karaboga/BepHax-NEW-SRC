package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.MsgUtil;
import bep.hax.util.prox.EmoteManager;
import bep.hax.util.prox.ProxTransport;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;
import org.joml.Vector3fc;

public class Proximity extends Module {
    private static final String CHAT_PREFIX = "§8[§aProxChat§8] §f";
    private static final int VENDOR_ID = 0;
    private static final int PACKET_ID_CHAT = 1;
    private static final int PACKET_ID_LEGACY_PAT = 2;
    private static final int PACKET_ID_EMOTE = 3;
    private static final int PATPAT_VENDOR_ID = 2;
    private static final int PATPAT_PACKET_ID = 0;
    private static final int MAX_SEND_LENGTH = 256;
    private static final int MAX_PATS_SENT_PER_SECOND = 5;
    private static final int MAX_ACTIVE_ANIMATIONS = 64;
    private static final double MAX_PAT_DISTANCE = 8.0;
    private static final int HAND_FRAMES = 5;
    private static final double HAND_SIZE = 0.85;
    private static final double HAND_Y_OFFSET = 0.11;
    private static final Identifier HAND_TEXTURE = Identifier.fromNamespaceAndPath("bephax", "textures/patpat/hand.png");
    private static final SoundEvent PAT_SOUND = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("bephax", "patpat"));
    public static final int EMOTE_START = 0;
    public static final int EMOTE_REPEAT = 1;
    public static final int EMOTE_STOP = 2;
    private static final int EMOTE_HEARTBEAT_TICKS = 100;
    private static final long EMOTE_TIMEOUT_MS = 15000L;
    private static final Map<Integer, Long> ANIMATIONS = new HashMap<>();
    private static Proximity instance;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgChat = this.settings.createGroup("Chat");
    private final SettingGroup sgPats = this.settings.createGroup("Pats");
    private final SettingGroup sgEmotes = this.settings.createGroup("Emotes");
    private final Setting<String> prefix = this.sgGeneral
        .add(
            new Builder().name("prefix").description("Messages typed in chat starting with this are sent as proximity chat instead.").defaultValue("%").build()
        );
    private final Setting<Boolean> showOwn = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-own-messages")
                .description("Echo your sent proximity messages in your chat.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> advanced = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("advanced")
                .description("Show advanced tuning settings (spam limits, sizes, timings, compatibility).")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> showReceived = this.sgChat
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-messages")
                .description("Display proximity messages received from nearby players.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> allowColorCodes = this.sgChat
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("allow-color-codes")
                .description("Keep § formatting codes in received messages. Off strips them to prevent chat spoofing.")
                .defaultValue(false)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Integer> maxDisplayLength = this.sgChat
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-length")
                .description("Received messages longer than this are truncated.")
                .defaultValue(256)
                .min(32)
                .sliderRange(32, 1024)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Integer> chatSpamLimit = this.sgChat
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("spam-limit")
                .description("Max messages shown per sender per 5 seconds; the rest are dropped.")
                .defaultValue(4)
                .min(1)
                .sliderRange(1, 20)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Proximity.PatTrigger> patTrigger = this.sgPats
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("pat-trigger"))
                        .description("Hold while looking at an entity to pat it. Sneak + Use is PatPat's default (shift + right click)."))
                    .defaultValue(Proximity.PatTrigger.SneakUse))
                .build()
        );
    private final Setting<Keybind> patKey = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                .name("pat-key")
                .description("Custom key to hold while looking at an entity to pat it.")
                .defaultValue(Keybind.none())
                .visible(() -> this.patTrigger.get() == Proximity.PatTrigger.Custom)
                .build()
        );
    private final Setting<Integer> patCooldown = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("pat-cooldown")
                .description("Ticks between pats while holding the key.")
                .defaultValue(4)
                .min(1)
                .sliderRange(1, 20)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Boolean> swingHand = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("swing-hand")
                .description("Swing your hand when patting.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showHand = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-hand")
                .description("Render the patting hand above the patted entity.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> handScale = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("hand-scale")
                .description("Size of the patting hand, 1 being PatPat's original size.")
                .defaultValue(1.0)
                .min(0.25)
                .sliderRange(0.25, 3.0)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Boolean> squish = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("squish")
                .description("Squish the patted entity's model.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> squishStrength = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("squish-strength")
                .description("How far the pat presses the entity down, in blocks.")
                .defaultValue(0.7)
                .min(0.1)
                .sliderRange(0.1, 1.5)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Integer> patDurationMs = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("pat-duration")
                .description("Pat animation length in milliseconds.")
                .defaultValue(240)
                .min(100)
                .sliderRange(100, 1000)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Boolean> patSound = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("pat-sound")
                .description("Play the PatPat pat sound.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Double> patVolume = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("pat-volume")
                .description("Pat sound volume.")
                .defaultValue(1.0)
                .min(0.0)
                .sliderRange(0.0, 2.0)
                .visible(() -> this.patSound.get() && this.advanced.get())
                .build()
        );
    private final Setting<Boolean> acceptPats = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-received-pats")
                .description("Display pats from nearby PatPat/ProxChat users.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> legacyPats = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("legacy-pats")
                .description("Also accept old ProxChat-style pat packets (deduplicated against native ones).")
                .defaultValue(true)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Integer> patSpamLimit = this.sgPats
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("pat-spam-limit")
                .description("Max pats shown per sender per second; the rest are dropped.")
                .defaultValue(6)
                .min(1)
                .sliderRange(1, 20)
                .visible(this.advanced::get)
                .build()
        );
    private final Setting<Boolean> sendEmotes = this.sgEmotes
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("send-emotes")
                .description("Broadcast your emotes to nearby ProxChat users.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> showEmotes = this.sgEmotes
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("show-emotes")
                .description("Play emotes received from nearby players.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Integer> emoteSpamLimit = this.sgEmotes
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("emote-spam-limit")
                .description("Max emote actions per sender per second; the rest are dropped.")
                .defaultValue(4)
                .min(1)
                .sliderRange(1, 20)
                .visible(this.advanced::get)
                .build()
        );
    private final Cache<UUID, AtomicInteger> chatRate = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(5L)).maximumSize(256L).build();
    private final Cache<UUID, AtomicInteger> patRate = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(1L)).maximumSize(256L).build();
    private final Cache<UUID, AtomicInteger> emoteRate = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(1L)).maximumSize(256L).build();
    private final Set<UUID> nativePatSenders = new HashSet<>();
    private final Set<UUID> emoteViewers = new HashSet<>();
    private UUID announcedEmote;
    private int emoteHeartbeat;
    private final ProxTransport.Handler chatHandler = this::onProxMessage;
    private final ProxTransport.Handler nativePatHandler = this::onNativePat;
    private final ProxTransport.Handler legacyPatHandler = this::onLegacyPat;
    private final ProxTransport.Handler emoteHandler = this::onEmotePacket;
    private final Vector3d pos = new Vector3d();
    private final Vector3d probe = new Vector3d();
    private int cooldownTicks = 0;
    private int patSendWindowSecond = 0;
    private int patSendCount = 0;

    public Proximity() {
        super(Bep.CATEGORY, "proximity", "Proximity chat, head pats and emotes with nearby ProxChat/PatPat users.");
        instance = this;
    }

    @Override
    public void onActivate() {
        ProxTransport transport = ProxTransport.getInstance();
        transport.register(0, 1, this.chatHandler);
        transport.register(2, 0, this.nativePatHandler);
        transport.register(0, 2, this.legacyPatHandler);
        transport.register(0, 3, this.emoteHandler);
        this.info("Chat: type (highlight)%s<message>(default) to talk to nearby players.", this.prefix.get());
        String cmd = Config.get().prefix.get() + "emote";
        int emoteCount = EmoteManager.getInstance().count();
        if (emoteCount == 0) {
            this.info("No emotes loaded — emotes won't be sent or shown.");
        } else {
            this.info("Emotes: (highlight)%d(default) loaded — play with (highlight)%s <name>(default) or the Emote Wheel module.", emoteCount, cmd);
        }
    }

    @Override
    public void onDeactivate() {
        UUID localEmote = EmoteManager.getInstance().stopLocal();
        if (localEmote != null && this.sendEmotes.get()) {
            byte[] data = encodeEmote(2, localEmote, 0);
            if (data != null) {
                ProxTransport.getInstance().send(0, 3, data);
            }
        }

        ProxTransport transport = ProxTransport.getInstance();
        transport.unregister(0, 1, this.chatHandler);
        transport.unregister(2, 0, this.nativePatHandler);
        transport.unregister(0, 2, this.legacyPatHandler);
        transport.unregister(0, 3, this.emoteHandler);
        this.clearSessionState();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.clearSessionState();
    }

    private void clearSessionState() {
        ANIMATIONS.clear();
        EmoteManager.getInstance().clearSession();
        this.nativePatSenders.clear();
        this.emoteViewers.clear();
        this.announcedEmote = null;
        this.chatRate.invalidateAll();
        this.patRate.invalidateAll();
        this.emoteRate.invalidateAll();
        this.cooldownTicks = 0;
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        String trigger = this.prefix.get();
        if (!trigger.isEmpty() && event.message != null && event.message.startsWith(trigger)) {
            event.cancel();
            String message = event.message.substring(trigger.length()).trim();
            if (message.isEmpty()) {
                MsgUtil.sendModuleMsg("Nothing to send.", this.name);
            } else {
                if (message.length() > 256) {
                    message = message.substring(0, 256);
                }

                byte[] data = encodeChat(message);
                if (data != null) {
                    int sent = ProxTransport.getInstance().send(0, 1, data);
                    if (sent < 0) {
                        MsgUtil.sendModuleMsg("Could not send proximity message.", this.name);
                    } else {
                        if (this.showOwn.get() && this.mc.player != null) {
                            this.displayMessage(this.mc.player.getGameProfile().name(), message);
                        }
                    }
                }
            }
        }
    }

    private void onProxMessage(Player sender, byte[] data) {
        if (this.isActive() && this.showReceived.get()) {
            String message = decodeChat(data);
            if (message != null) {
                message = this.sanitize(message);
                if (!message.isEmpty()) {
                    AtomicInteger count = this.chatRate.get(sender.getUUID(), k -> new AtomicInteger());
                    if (count.incrementAndGet() <= this.chatSpamLimit.get()) {
                        this.displayMessage(sender.getGameProfile().name(), message);
                    }
                }
            }
        }
    }

    private void displayMessage(String name, String message) {
        if (this.mc.player != null) {
            this.mc.player.displayClientMessage(Component.literal("§8[§aProxChat§8] §f§2" + name + ": §a" + message), false);
        }
    }

    private String sanitize(String message) {
        int max = this.maxDisplayLength.get();
        StringBuilder sb = new StringBuilder(Math.min(message.length(), max));

        for (int i = 0; i < message.length() && sb.length() < max; i++) {
            char c = message.charAt(i);
            if (c >= ' ' && c != 127) {
                if (c == 167 && !this.allowColorCodes.get()) {
                    i++;
                } else {
                    sb.append(c);
                }
            }
        }

        return sb.toString().trim();
    }

    private static byte[] encodeChat(String message) {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bout);
            dout.writeUTF(message);
            dout.close();
            return bout.toByteArray();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String decodeChat(byte[] data) {
        try {
            return new DataInputStream(new ByteArrayInputStream(data)).readUTF();
        } catch (Exception ex) {
            return null;
        }
    }

    public static float getSquish(int entityId, float bbHeight) {
        Proximity m = instance;
        if (m != null && !ANIMATIONS.isEmpty() && m.isActive() && m.squish.get()) {
            Long start = ANIMATIONS.get(entityId);
            if (start == null) {
                return 1.0F;
            } else {
                long elapsed = System.currentTimeMillis() - start;
                int duration = m.patDurationMs.get();
                if (elapsed >= 0L && elapsed < duration) {
                    float eased = eased((float)elapsed / duration);
                    float range = Math.min(m.squishStrength.get().floatValue() / Math.max(bbHeight, 0.1F), 0.9F);
                    return 1.0F - range * (float)Math.sin(eased * Math.PI);
                } else {
                    return 1.0F;
                }
            }
        } else {
            return 1.0F;
        }
    }

    private static float eased(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.player != null && this.mc.level != null) {
            long now = System.currentTimeMillis();
            int duration = this.patDurationMs.get();
            Iterator<Long> it = ANIMATIONS.values().iterator();

            while (it.hasNext()) {
                if (now - it.next() >= duration) {
                    it.remove();
                }
            }

            this.tickEmoteReannounce();
            EmoteManager.getInstance().stopStale(15000L);
            if (this.cooldownTicks > 0) {
                this.cooldownTicks--;
            }

            if (!this.patKeyPressed()) {
                this.cooldownTicks = 0;
            } else if (this.mc.screen == null && this.cooldownTicks <= 0) {
                if (this.mc.crosshairPickEntity instanceof LivingEntity living && living.isAlive() && !living.isInvisible()) {
                    if (living != this.mc.player) {
                        if (this.allowPatSend()) {
                            int sent = ProxTransport.getInstance().send(2, 0, writeVarInt(living.getId()));
                            if (sent >= 0) {
                                this.cooldownTicks = this.patCooldown.get();
                                this.startPatAnimation(living);
                                if (this.swingHand.get()) {
                                    this.mc.player.swing(InteractionHand.MAIN_HAND);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean patKeyPressed() {
        return switch ((Proximity.PatTrigger)this.patTrigger.get()) {
            case SneakUse -> this.mc.options.keyShift.isDown() && this.mc.options.keyUse.isDown();
            case Use -> this.mc.options.keyUse.isDown();
            case Custom -> this.patKey.get().isPressed();
        };
    }

    private boolean allowPatSend() {
        int second = (int)(System.currentTimeMillis() / 1000L);
        if (second != this.patSendWindowSecond) {
            this.patSendWindowSecond = second;
            this.patSendCount = 0;
        }

        return ++this.patSendCount <= 5;
    }

    private void onNativePat(Player sender, byte[] data) {
        this.nativePatSenders.add(sender.getUUID());
        if (data.length <= 5) {
            this.handleIncomingPat(sender, readVarInt(data));
        }
    }

    private void onLegacyPat(Player sender, byte[] data) {
        if (this.legacyPats.get() && !this.nativePatSenders.contains(sender.getUUID())) {
            if (data.length == 4) {
                int id = (data[0] & 255) << 24 | (data[1] & 255) << 16 | (data[2] & 255) << 8 | data[3] & 255;
                this.handleIncomingPat(sender, id);
            }
        }
    }

    private void handleIncomingPat(Player sender, int pattedId) {
        if (this.acceptPats.get() && pattedId >= 0 && this.mc.level != null) {
            if (this.mc.level.getEntity(pattedId) instanceof LivingEntity patted && patted.isAlive()) {
                if (patted.getId() != sender.getId()) {
                    if (!(sender.distanceTo(patted) > 8.0)) {
                        AtomicInteger count = this.patRate.get(sender.getUUID(), k -> new AtomicInteger());
                        if (count.incrementAndGet() <= this.patSpamLimit.get()) {
                            this.startPatAnimation(patted);
                        }
                    }
                }
            }
        }
    }

    private void startPatAnimation(LivingEntity patted) {
        if (ANIMATIONS.size() < 64 || ANIMATIONS.containsKey(patted.getId())) {
            ANIMATIONS.put(patted.getId(), System.currentTimeMillis());
            if (this.patSound.get() && this.mc.level != null) {
                this.mc
                    .level
                    .playLocalSound(
                        patted.getX(),
                        patted.getY(),
                        patted.getZ(),
                        PAT_SOUND,
                        SoundSource.PLAYERS,
                        this.patVolume.get().floatValue(),
                        1.0F,
                        false
                    );
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (this.showHand.get() && !ANIMATIONS.isEmpty() && this.mc.level != null) {
            AbstractTexture texture = this.mc.getTextureManager().getTexture(HAND_TEXTURE);
            long now = System.currentTimeMillis();
            int duration = this.patDurationMs.get();

            for (Entry<Integer, Long> entry : ANIMATIONS.entrySet()) {
                long elapsed = now - entry.getValue();
                if (elapsed >= 0L
                    && elapsed < duration
                    && this.mc.level.getEntity(entry.getKey()) instanceof LivingEntity living
                    && !living.isInvisible()
                    && (living != this.mc.getCameraEntity() || !this.mc.options.getCameraType().isFirstPerson())) {
                    float eased = eased((float)elapsed / duration);
                    int frame = Mth.clamp((int)(5.0F * eased), 0, 4);
                    float squishNow = getSquish(living.getId(), living.getBbHeight());
                    Utils.set(this.pos, living, event.tickDelta);
                    this.pos.add(0.0, living.getBbHeight() * squishNow + 0.11, 0.0);
                    Vector3fc left = this.mc.gameRenderer.getMainCamera().leftVector();
                    this.probe.set(this.pos.x + left.x() * 0.85, this.pos.y + left.y() * 0.85, this.pos.z + left.z() * 0.85);
                    if (NametagUtils.to2D(this.pos, 1.0, false) && NametagUtils.to2D(this.probe, 1.0, false)) {
                        double u1 = frame / 5.0;
                        double u2 = u1 + 0.2;
                        double size = Math.hypot(this.probe.x - this.pos.x, this.probe.y - this.pos.y) * this.handScale.get();
                        if (!(size < 1.0)) {
                            NametagUtils.scale = 1.0;
                            NametagUtils.begin(this.pos);
                            Renderer2D.TEXTURE.begin();
                            Renderer2D.TEXTURE.texQuad(-size / 2.0, -size / 2.0, size, size, 0.0, u1, 0.0, u2, 1.0, Color.WHITE);
                            Renderer2D.TEXTURE.render(texture.getTextureView(), texture.getSampler());
                            NametagUtils.end();
                        }
                    }
                }
            }
        }
    }

    private static byte[] writeVarInt(int value) {
        byte[] buf = new byte[5];
        int i = 0;

        while ((value & -128) != 0) {
            buf[i++] = (byte)(value & 127 | 128);
            value >>>= 7;
        }

        buf[i++] = (byte)value;
        return Arrays.copyOf(buf, i);
    }

    private static int readVarInt(byte[] data) {
        int value = 0;
        int shift = 0;

        for (int i = 0; i < data.length && i < 5; i++) {
            value |= (data[i] & 127) << shift;
            if ((data[i] & 128) == 0) {
                return value;
            }

            shift += 7;
        }

        return -1;
    }

    public static void onLocalEmote(int action, UUID emoteUuid, int tick) {
        Proximity m = instance;
        if (m != null && m.isActive() && m.sendEmotes.get()) {
            byte[] data = encodeEmote(action, emoteUuid, tick);
            if (data != null) {
                ProxTransport.getInstance().send(0, 3, data);
            }
        }
    }

    private void onEmotePacket(Player sender, byte[] data) {
        if (this.isActive() && this.showEmotes.get()) {
            if (data.length <= 32) {
                Proximity.EmoteData emote = decodeEmote(data);
                if (emote != null) {
                    AtomicInteger count = this.emoteRate.get(sender.getUUID(), k -> new AtomicInteger());
                    if (count.incrementAndGet() <= this.emoteSpamLimit.get()) {
                        EmoteManager emotes = EmoteManager.getInstance();
                        switch (emote.action) {
                            case 0:
                                emotes.touch(sender.getUUID());
                                emotes.play(sender, emote.emoteUuid, emote.tick);
                                break;
                            case 1:
                                emotes.touch(sender.getUUID());
                                if (!emotes.isPlaying(sender, emote.emoteUuid)) {
                                    emotes.play(sender, emote.emoteUuid, emote.tick);
                                }
                                break;
                            case 2:
                                emotes.stop(sender, emote.emoteUuid);
                        }
                    }
                }
            }
        }
    }

    private void tickEmoteReannounce() {
        EmoteManager emotes = EmoteManager.getInstance();
        UUID current = this.sendEmotes.get() ? emotes.localPlaying() : null;
        if (current == null) {
            this.announcedEmote = null;
            this.emoteViewers.clear();
            this.emoteHeartbeat = 0;
        } else if (current.equals(this.announcedEmote)) {
            boolean resend = false;
            Set<UUID> present = new HashSet<>();

            for (Player player : this.mc.level.players()) {
                present.add(player.getUUID());
                if (this.emoteViewers.add(player.getUUID())) {
                    resend = true;
                }
            }

            this.emoteViewers.retainAll(present);
            if (emotes.loops(current) && ++this.emoteHeartbeat >= 100) {
                resend = true;
            }

            if (resend) {
                this.emoteHeartbeat = 0;
                onLocalEmote(1, current, (int)emotes.localTicks());
            }
        } else {
            this.announcedEmote = current;
            this.emoteViewers.clear();

            for (Player player : this.mc.level.players()) {
                this.emoteViewers.add(player.getUUID());
            }

            this.emoteHeartbeat = 0;
        }
    }

    private static byte[] encodeEmote(int action, UUID emoteUuid, int tick) {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bout);
            dout.writeByte(action);
            dout.writeBoolean(emoteUuid != null);
            if (emoteUuid != null) {
                dout.writeLong(emoteUuid.getMostSignificantBits());
                dout.writeLong(emoteUuid.getLeastSignificantBits());
            }

            dout.writeInt(tick);
            dout.close();
            return bout.toByteArray();
        } catch (Exception ex) {
            return null;
        }
    }

    private static Proximity.EmoteData decodeEmote(byte[] data) {
        try {
            DataInputStream din = new DataInputStream(new ByteArrayInputStream(data));
            int action = din.readByte();
            if (action >= 0 && action <= 2) {
                UUID emoteUuid = null;
                if (din.readBoolean()) {
                    emoteUuid = new UUID(din.readLong(), din.readLong());
                }

                int tick = din.readInt();
                return new Proximity.EmoteData(action, emoteUuid, tick);
            } else {
                return null;
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private record EmoteData(int action, UUID emoteUuid, int tick) {
    }

    public enum PatTrigger {
        SneakUse("Sneak + Use"),
        Use("Use"),
        Custom("Custom Key");

        private final String title;

        PatTrigger(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
        }
    }
}
