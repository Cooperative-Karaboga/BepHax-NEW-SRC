package bep.hax.util;

import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownServiceException;
import java.time.Instant;
import java.util.Random;
import javax.net.ssl.HttpsURLConnection;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.util.Crypt.SaltSupplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class Utils {
    private static final Random RANDOM = new Random();
    public static final boolean XAERO_AVAILABLE = FabricLoader.getInstance().isModLoaded("xaeroworldmap")
        && FabricLoader.getInstance().isModLoaded("xaerominimap");

    public static String rCC() {
        String color = "§7";
        Utils.TextColor[] colors = Utils.TextColor.values();

        while (color.equals("§0") || color.equals("§8") || color.equals("§7")) {
            int luckyIndex = RANDOM.nextInt(colors.length);
            color = colors[luckyIndex].label;
        }

        return color;
    }

    public static void illegalDisconnect(boolean disableAutoReconnect, Utils.IllegalDisconnectMethod illegalDisconnectMethod) {
        if (meteordevelopment.meteorclient.utils.Utils.canUpdate()) {
            if (disableAutoReconnect) {
                disableAutoReconnect();
            }

            try {
                Packet<?> illegalPacket = switch (illegalDisconnectMethod) {
                    case Slot -> new ServerboundSetCarriedItemPacket(-69);
                    case Chat -> new ServerboundChatPacket("§", Instant.now(), SaltSupplier.getLong(), null, null);
                    case Interact -> ServerboundInteractPacket.createInteractionPacket(MeteorClient.mc.player, false, InteractionHand.MAIN_HAND);
                    case Movement -> new Pos(Double.NaN, Double.NaN, Double.NaN, false, false);
                    case SequenceBreak -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, -420, 13.37F, 69.69F);
                    case InvalidSettings -> new ServerboundClientInformationPacket(
                        new ClientInformation(
                            MeteorClient.mc.options.languageCode,
                            -69,
                            MeteorClient.mc.options.chatVisibility().get(),
                            MeteorClient.mc.options.chatColors().get(),
                            MeteorClient.mc.options.buildPlayerInformation().modelCustomisation(),
                            MeteorClient.mc.options.mainHand().get(),
                            MeteorClient.mc.options.buildPlayerInformation().textFilteringEnabled(),
                            MeteorClient.mc.options.allowServerListing().get(),
                            MeteorClient.mc.options.buildPlayerInformation().particleStatus()
                        )
                    );
                };
                if (illegalPacket != null && MeteorClient.mc.getConnection() != null && MeteorClient.mc.getConnection().getConnection() != null) {
                    MeteorClient.mc.getConnection().getConnection().send(illegalPacket, null);
                }

                if (MeteorClient.mc.getConnection() != null) {
                    MeteorClient.mc.getConnection().getConnection().disconnect(Component.literal("[BepHax] Illegal Disconnect"));
                }
            } catch (Exception e) {
                if (MeteorClient.mc.getConnection() != null && MeteorClient.mc.getConnection().getConnection() != null) {
                    MeteorClient.mc.getConnection().getConnection().disconnect(Component.literal("[BepHax] Disconnect"));
                }
            }
        }
    }

    public static void disableAutoReconnect() {
        Modules mods = Modules.get();
        if (mods != null) {
            AutoReconnect atrc = mods.get(AutoReconnect.class);
            if (atrc.isActive()) {
                atrc.toggle();
            }
        }
    }

    public static int firework(Minecraft mc, boolean elytraRequired) {
        if (mc.player != null && mc.gameMode != null) {
            int elytraSwapSlot = -1;
            if (elytraRequired && !mc.player.getInventory().getItem(38).is(Items.ELYTRA)) {
                FindItemResult itemResult = InvUtils.findInHotbar(Items.ELYTRA);
                if (!itemResult.found()) {
                    return -1;
                }

                elytraSwapSlot = itemResult.slot();
                InvUtils.swap(itemResult.slot(), true);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                InvUtils.swapBack();
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.START_FALL_FLYING));
            }

            FindItemResult itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
            if (itemResult.found()) {
                if (itemResult.isOffhand()) {
                    mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
                    mc.player.swing(InteractionHand.OFF_HAND);
                } else {
                    InvUtils.swap(itemResult.slot(), true);
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    InvUtils.swapBack();
                }

                return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
            } else {
                int movedSlot = -1;

                for (int n = 9; n < ((PlayerInventoryAccessor)mc.player.getInventory()).getMain().size(); n++) {
                    Item item = mc.player.getInventory().getItem(n).getItem();
                    if (item == Items.FIREWORK_ROCKET) {
                        InvUtils.move().from(n).to(((PlayerInventoryAccessor)mc.player.getInventory()).getSelectedSlot());
                        movedSlot = n;
                        break;
                    }
                }

                if (movedSlot != -1) {
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    InvUtils.move().from(((PlayerInventoryAccessor)mc.player.getInventory()).getSelectedSlot()).to(movedSlot);
                    return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
                } else {
                    return -1;
                }
            }
        } else {
            return -1;
        }
    }

    public static void setPressed(KeyMapping key, boolean pressed) {
        key.setDown(pressed);
        Input.setKeyState(key, pressed);
    }

    public static Vec3 positionInDirection(Vec3 pos, double yaw, double distance) {
        Vec3 offset = yawToDirection(yaw).scale(distance);
        return pos.add(offset);
    }

    public static Vec3 yawToDirection(double yaw) {
        yaw = yaw * Math.PI / 180.0;
        double x = -Math.sin(yaw);
        double z = Math.cos(yaw);
        return new Vec3(x, 0.0, z);
    }

    public static double distancePointToDirection(Vec3 point, Vec3 direction, @Nullable Vec3 start) {
        if (start == null) {
            start = Vec3.ZERO;
        }

        point = point.multiply(new Vec3(1.0, 0.0, 1.0));
        start = start.multiply(new Vec3(1.0, 0.0, 1.0));
        direction = direction.multiply(new Vec3(1.0, 0.0, 1.0));
        Vec3 directionVec = point.subtract(start);
        double projectionLength = directionVec.dot(direction) / direction.lengthSqr();
        Vec3 projection = direction.scale(projectionLength);
        Vec3 perp = directionVec.subtract(projection);
        return perp.length();
    }

    public static double angleOnAxis(double yaw) {
        if (yaw < 0.0) {
            yaw += 360.0;
        }

        return Math.round(yaw / 45.0) * 45L;
    }

    public static float smoothRotation(double current, double target, double rotationScaling) {
        double difference = angleDifference(target, current);
        return (float)(current + difference * rotationScaling);
    }

    public static double angleDifference(double target, double current) {
        double diff = (target - current + 180.0) % 360.0 - 180.0;
        return diff < -180.0 ? diff + 360.0 : diff;
    }

    public static void sendWebhook(String webhookURL, String title, String message, String pingID, String playerName) {
        String json = "";
        json = json
            + "{\"embeds\": [{\"title\": \""
            + title
            + "\",\"description\": \""
            + message
            + "\",\"color\": 15258703,\"footer\": {\"text\": \"From: "
            + playerName
            + "\"}}]}";
        sendRequest(webhookURL, json);
        if (pingID != null) {
            json = "{\"content\": \"<@" + pingID + ">\"}";
            sendRequest(webhookURL, json);
        }
    }

    private static void sendRequest(String webhookURL, String json) {
        try {
            URL url = URI.create(webhookURL).toURL();
            HttpsURLConnection con = (HttpsURLConnection)url.openConnection();
            con.addRequestProperty("Content-Type", "application/json");
            con.addRequestProperty("User-Agent", "Mozilla");
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            OutputStream stream = con.getOutputStream();
            stream.write(json.getBytes());
            stream.flush();
            stream.close();
            con.getInputStream().close();
            con.disconnect();
        } catch (MalformedURLException | UnknownServiceException var5) {
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public enum IllegalDisconnectMethod {
        Slot,
        Chat,
        Interact,
        Movement,
        SequenceBreak,
        InvalidSettings;
    }

    public enum RainbowColor {
        Reds(new String[]{"§c", "§4"}),
        Yellows(new String[]{"§e", "§6"}),
        Greens(new String[]{"§a", "§2"}),
        Cyans(new String[]{"§b", "§3"}),
        Blues(new String[]{"§9", "§1"}),
        Purples(new String[]{"§d", "§5"});

        public final String[] labels;

        RainbowColor(String[] labels) {
            this.labels = labels;
        }

        public static Utils.RainbowColor getFirst() {
            return values()[Utils.RANDOM.nextInt(values().length)];
        }

        public static Utils.RainbowColor getNext(Utils.RainbowColor previous) {
            return switch (previous) {
                case Reds -> Yellows;
                case Yellows -> Greens;
                case Greens -> Cyans;
                case Cyans -> Blues;
                case Blues -> Purples;
                case Purples -> Reds;
            };
        }
    }

    public enum TextColor {
        Black("§0"),
        White("§f"),
        Gray("§8"),
        Light_Gray("§7"),
        Dark_Green("§2"),
        Green("§a"),
        Dark_Aqua("§3"),
        Aqua("§b"),
        Dark_Blue("§1"),
        Blue("§9"),
        Dark_Red("§4"),
        Red("§c"),
        Dark_Purple("§5"),
        Purple("§d"),
        Gold("§6"),
        Yellow("§e"),
        Random("");

        public final String label;

        TextColor(String label) {
            this.label = label;
        }
    }

    public enum TextFormat {
        Plain(""),
        Italic("§o"),
        Bold("§l"),
        Underline("§n"),
        Strikethrough("§m"),
        Obfuscated("§k");

        public final String label;

        TextFormat(String label) {
            this.label = label;
        }
    }
}
