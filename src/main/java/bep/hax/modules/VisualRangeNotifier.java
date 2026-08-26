package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.LogUtil;
import bep.hax.util.MsgUtil;
import bep.hax.util.Utils;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.Vec3;

public class VisualRangeNotifier extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> chatNotify = this.sgGeneral
        .add(new Builder().name("chat-notify").description("Show chat messages for notifications.").defaultValue(true).build());
    private final Setting<Boolean> ignoreFriends = this.sgGeneral
        .add(new Builder().name("ignore-friends").description("Don't notify for Meteor friends.").defaultValue(true).build());
    private final SettingGroup sgPlayers = this.settings.createGroup("Players");
    private final Setting<Boolean> playerEnter = this.sgPlayers
        .add(new Builder().name("player-enter").description("Notify when a player enters visual range.").defaultValue(true).build());
    private final Setting<Boolean> playerLeave = this.sgPlayers
        .add(new Builder().name("player-leave").description("Notify when a player leaves visual range.").defaultValue(true).build());
    private final Setting<Boolean> playerCoords = this.sgPlayers
        .add(new Builder().name("player-coords").description("Include their coordinates in the notification.").defaultValue(false).build());
    private final Setting<Boolean> playerEquipment = this.sgPlayers
        .add(
            new Builder()
                .name("player-equipment")
                .description("Include visible equipment in the enter notification.")
                .defaultValue(true)
                .visible(this.playerEnter::get)
                .build()
        );
    private final SettingGroup sgItems = this.settings.createGroup("Ground Items");
    private final Setting<Boolean> itemNotify = this.sgItems
        .add(new Builder().name("item-notify").description("Notify when selected items appear on the ground nearby.").defaultValue(false).build());
    private final Setting<List<Item>> trackedItems = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                .name("tracked-items")
                .description("Items to notify about when found on the ground.")
                .defaultValue(
                    Items.ELYTRA,
                    Items.DIAMOND_CHESTPLATE,
                    Items.DIAMOND_HELMET,
                    Items.DIAMOND_LEGGINGS,
                    Items.DIAMOND_BOOTS,
                    Items.NETHERITE_CHESTPLATE,
                    Items.NETHERITE_HELMET,
                    Items.NETHERITE_LEGGINGS,
                    Items.NETHERITE_BOOTS,
                    Items.NETHERITE_SWORD,
                    Items.ENCHANTED_GOLDEN_APPLE,
                    Items.TOTEM_OF_UNDYING,
                    Items.END_CRYSTAL,
                    Items.SHULKER_BOX
                )
                .visible(this.itemNotify::get)
                .build()
        );
    private final Setting<Integer> itemCooldown = this.sgItems
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("item-cooldown")
                .description("Seconds before the same item entity triggers another notification.")
                .defaultValue(30)
                .min(5)
                .sliderRange(5, 120)
                .visible(this.itemNotify::get)
                .build()
        );
    private final SettingGroup sgSound = this.settings.createGroup("Sound");
    private final Setting<Boolean> soundEnabled = this.sgSound
        .add(new Builder().name("sound-enabled").description("Play a sound on player enter notification.").defaultValue(true).build());
    private final Setting<List<SoundEvent>> sound = this.sgSound
        .add(
            new meteordevelopment.meteorclient.settings.SoundEventListSetting.Builder()
                .name("sound")
                .description("Sound to play.")
                .defaultValue(List.of(SoundEvents.NOTE_BLOCK_PLING.value()))
                .visible(this.soundEnabled::get)
                .build()
        );
    private final Setting<Double> soundVolume = this.sgSound
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("sound-volume")
                .description("Volume of the notification sound.")
                .defaultValue(1.0)
                .min(0.0)
                .max(2.0)
                .sliderRange(0.0, 2.0)
                .visible(this.soundEnabled::get)
                .build()
        );
    private final Setting<Double> soundPitch = this.sgSound
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("sound-pitch")
                .description("Pitch of the notification sound.")
                .defaultValue(1.0)
                .min(0.5)
                .max(2.0)
                .sliderRange(0.5, 2.0)
                .visible(this.soundEnabled::get)
                .build()
        );
    private final SettingGroup sgDiscord = this.settings.createGroup("Discord");
    private final Setting<Boolean> discordEnabled = this.sgDiscord
        .add(new Builder().name("discord-webhook").description("Send notifications to a Discord webhook.").defaultValue(false).build());
    private final Setting<String> webhookUrl = this.sgDiscord
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("webhook-url")
                .description("Discord webhook URL.")
                .defaultValue("")
                .visible(this.discordEnabled::get)
                .build()
        );
    private final Setting<Boolean> discordPlayers = this.sgDiscord
        .add(
            new Builder()
                .name("discord-players")
                .description("Send player enter/leave events to Discord.")
                .defaultValue(true)
                .visible(this.discordEnabled::get)
                .build()
        );
    private final Setting<Boolean> discordItems = this.sgDiscord
        .add(
            new Builder()
                .name("discord-items")
                .description("Send ground item events to Discord.")
                .defaultValue(true)
                .visible(() -> this.discordEnabled.get() && this.itemNotify.get())
                .build()
        );
    private final Setting<String> pingId = this.sgDiscord
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("ping-id")
                .description("Discord user ID to ping. Leave blank to disable.")
                .defaultValue("")
                .visible(this.discordEnabled::get)
                .build()
        );
    private final SettingGroup sgPvp = this.settings.createGroup("PvP Activity");
    private final Setting<Boolean> crystalPlace = this.sgPvp
        .add(new Builder().name("crystal-place").description("Notify when a player places an end crystal nearby.").defaultValue(true).build());
    private final Setting<Boolean> crystalPop = this.sgPvp
        .add(new Builder().name("crystal-pop").description("Notify when a player pops (destroys) an end crystal nearby.").defaultValue(true).build());
    private final Setting<Boolean> anchorPlace = this.sgPvp
        .add(new Builder().name("anchor-place").description("Notify when a respawn anchor is placed nearby.").defaultValue(true).build());
    private final Setting<Boolean> anchorPop = this.sgPvp
        .add(new Builder().name("anchor-pop").description("Notify when a respawn anchor is destroyed (exploded) nearby.").defaultValue(true).build());
    private final Setting<Integer> pvpRange = this.sgPvp
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("pvp-range")
                .description("Maximum range to attribute crystal/anchor activity to a player.")
                .defaultValue(6)
                .min(1)
                .sliderRange(1, 20)
                .build()
        );
    private final Setting<Boolean> pvpIgnoreSelf = this.sgPvp
        .add(new Builder().name("pvp-ignore-self").description("Don't notify for your own crystal/anchor activity.").defaultValue(true).build());
    private final Setting<Boolean> discordPvp = this.sgDiscord
        .add(
            new Builder()
                .name("discord-pvp")
                .description("Send crystal/anchor activity to Discord.")
                .defaultValue(true)
                .visible(this.discordEnabled::get)
                .build()
        );
    private final Set<UUID> trackedPlayers = new HashSet<>();
    private final Map<UUID, String> uuidNameCache = new HashMap<>();
    private final Map<Integer, Long> notifiedItems = new HashMap<>();
    private final Set<BlockPos> trackedAnchors = new HashSet<>();

    public VisualRangeNotifier() {
        super(
            Bep.CATEGORY,
            "visual-range-notifier",
            "Notifies when players enter visual range or selected items appear on the ground with optional Discord webhook alerts."
        );
    }

    @Override
    public void onActivate() {
        this.trackedPlayers.clear();
        this.uuidNameCache.clear();
        this.notifiedItems.clear();
        this.trackedAnchors.clear();
    }

    @Override
    public void onDeactivate() {
        this.trackedPlayers.clear();
        this.uuidNameCache.clear();
        this.notifiedItems.clear();
        this.trackedAnchors.clear();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        this.trackedPlayers.clear();
        this.uuidNameCache.clear();
        this.notifiedItems.clear();
        this.trackedAnchors.clear();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.playerEnter.get() || this.playerLeave.get()) {
                this.tickPlayers();
            }

            if (this.itemNotify.get()) {
                this.tickItems();
            }

            if (this.anchorPop.get()) {
                this.tickAnchorTracking();
            }
        }
    }

    @EventHandler
    private void onReceivePacket(Receive event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (event.packet instanceof ClientboundBundlePacket bundle) {
                for (Packet<?> sub : bundle.subPackets()) {
                    this.handleInboundPacket(sub);
                }
            } else {
                this.handleInboundPacket(event.packet);
            }
        }
    }

    private void handleInboundPacket(Packet<?> packet) {
        if (packet instanceof ClientboundAddEntityPacket spawn && spawn.getType() == EntityType.END_CRYSTAL) {
            this.handleCrystalSpawn(spawn);
        } else if (packet instanceof ClientboundRemoveEntitiesPacket destroy) {
            this.handleEntitiesDestroy(destroy);
        } else if (packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
            this.handleBlockUpdate(blockUpdate);
        }
    }

    private void tickPlayers() {
        Set<UUID> currentPlayers = new HashSet<>();

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof Player player && player != this.mc.player) {
                UUID uuid = player.getUUID();
                currentPlayers.add(uuid);
                this.uuidNameCache.put(uuid, player.getGameProfile().name());
                if (this.playerEnter.get() && !this.trackedPlayers.contains(uuid) && (!this.ignoreFriends.get() || !Friends.get().isFriend(player))) {
                    String name = player.getGameProfile().name();
                    int x = (int)player.getX();
                    int y = (int)player.getY();
                    int z = (int)player.getZ();
                    String equipStr = this.playerEquipment.get() ? this.getEquipmentString(player) : null;
                    if (this.chatNotify.get()) {
                        StringBuilder msg = new StringBuilder();
                        msg.append("§a").append(name).append(" §7entered visual range");
                        if (this.playerCoords.get()) {
                            msg.append(" at §f").append(x).append(" ").append(y).append(" ").append(z);
                        }

                        if (equipStr != null && !equipStr.isEmpty()) {
                            msg.append("\n §8 └ §7Gear: §f").append(equipStr);
                        }

                        MsgUtil.sendMsg(msg.toString());
                    }

                    this.playNotifSound();
                    if (this.discordEnabled.get() && this.discordPlayers.get() && !this.webhookUrl.get().isEmpty()) {
                        this.sendDiscordAsync("Player Entered Visual Range", this.buildPlayerMessage(name, "entered", x, y, z, equipStr));
                    }

                    LogUtil.info("Player entered visual range: " + name + " at " + x + " " + y + " " + z);
                }
            }
        }

        if (this.playerLeave.get()) {
            for (UUID uuid : this.trackedPlayers) {
                if (!currentPlayers.contains(uuid)) {
                    if (this.ignoreFriends.get()) {
                        PlayerInfo entry = this.mc.getConnection() != null ? this.mc.getConnection().getPlayerInfo(uuid) : null;
                        if (entry != null) {
                            GameProfile profile = entry.getProfile();
                            if (Friends.get().get(profile.name()) != null) {
                                continue;
                            }
                        }
                    }

                    String name = this.getNameFromUuid(uuid);
                    if (this.chatNotify.get()) {
                        MsgUtil.sendMsg("§c" + name + " §7left visual range.");
                    }

                    if (this.discordEnabled.get() && this.discordPlayers.get() && !this.webhookUrl.get().isEmpty()) {
                        this.sendDiscordAsync("Player Left Visual Range", "**" + name + "** left visual range\\nDimension: " + this.getDimension());
                    }

                    LogUtil.info("Player left visual range: " + name);
                }
            }
        }

        this.trackedPlayers.clear();
        this.trackedPlayers.addAll(currentPlayers);
    }

    private void tickItems() {
        long now = System.currentTimeMillis();
        long cooldownMs = this.itemCooldown.get().intValue() * 1000L;
        this.notifiedItems.entrySet().removeIf(e -> now - e.getValue() > cooldownMs);
        List<Item> watchList = this.trackedItems.get();
        if (!watchList.isEmpty()) {
            for (Entity entity : this.mc.level.entitiesForRendering()) {
                if (entity instanceof ItemEntity itemEntity) {
                    Item item = itemEntity.getItem().getItem();
                    if (watchList.contains(item)) {
                        int entityId = itemEntity.getId();
                        if (!this.notifiedItems.containsKey(entityId)) {
                            this.notifiedItems.put(entityId, now);
                            int count = itemEntity.getItem().getCount();
                            String itemName = itemEntity.getItem().getHoverName().getString();
                            int x = (int)itemEntity.getX();
                            int y = (int)itemEntity.getY();
                            int z = (int)itemEntity.getZ();
                            if (this.chatNotify.get()) {
                                String countStr = count > 1 ? " x" + count : "";
                                MsgUtil.sendMsg("§d" + itemName + countStr + " §7found on ground at §f" + x + " " + y + " " + z);
                            }

                            this.playNotifSound();
                            if (this.discordEnabled.get() && this.discordItems.get() && !this.webhookUrl.get().isEmpty()) {
                                String countStr = count > 1 ? " x" + count : "";
                                String message = "**"
                                    + itemName
                                    + countStr
                                    + "** found on ground\\nCoords: "
                                    + x
                                    + " "
                                    + y
                                    + " "
                                    + z
                                    + "\\nDimension: "
                                    + this.getDimension();
                                this.sendDiscordAsync("Ground Item Alert", message);
                            }

                            LogUtil.info("Ground item detected: " + itemName + " at " + x + " " + y + " " + z);
                        }
                    }
                }
            }
        }
    }

    private void handleCrystalSpawn(ClientboundAddEntityPacket packet) {
        if (this.crystalPlace.get()) {
            Vec3 pos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
            Player nearest = this.findNearestPlayer(pos);
            String name = nearest != null ? nearest.getGameProfile().name() : "Unknown";
            int x = (int)pos.x;
            int y = (int)pos.y;
            int z = (int)pos.z;
            if (this.chatNotify.get()) {
                MsgUtil.sendMsg("§d" + name + " §7placed an §dEnd Crystal §7at §f" + x + " " + y + " " + z);
            }

            this.playNotifSound();
            if (this.discordEnabled.get() && this.discordPvp.get() && !this.webhookUrl.get().isEmpty()) {
                this.sendDiscordAsync(
                    "End Crystal Placed", "**" + name + "** placed an End Crystal\nCoords: " + x + " " + y + " " + z + "\nDimension: " + this.getDimension()
                );
            }

            LogUtil.info("Crystal placed by " + name + " at " + x + " " + y + " " + z);
        }
    }

    private void handleEntitiesDestroy(ClientboundRemoveEntitiesPacket packet) {
        if (this.crystalPop.get()) {
            for (int id : packet.getEntityIds()) {
                Entity entity = this.mc.level.getEntity(id);
                if (entity instanceof EndCrystal) {
                    Vec3 pos = entity.position();
                    Player nearest = this.findNearestPlayer(pos);
                    String name = nearest != null ? nearest.getGameProfile().name() : "Unknown";
                    int x = (int)pos.x;
                    int y = (int)pos.y;
                    int z = (int)pos.z;
                    if (this.chatNotify.get()) {
                        MsgUtil.sendMsg("§c" + name + " §7popped an §cEnd Crystal §7at §f" + x + " " + y + " " + z);
                    }

                    this.playNotifSound();
                    if (this.discordEnabled.get() && this.discordPvp.get() && !this.webhookUrl.get().isEmpty()) {
                        this.sendDiscordAsync(
                            "End Crystal Popped",
                            "**" + name + "** popped an End Crystal\nCoords: " + x + " " + y + " " + z + "\nDimension: " + this.getDimension()
                        );
                    }

                    LogUtil.info("Crystal popped by " + name + " at " + x + " " + y + " " + z);
                }
            }
        }
    }

    private void handleBlockUpdate(ClientboundBlockUpdatePacket packet) {
        BlockPos pos = packet.getPos();
        boolean isAnchor = packet.getBlockState().getBlock() instanceof RespawnAnchorBlock;
        if (isAnchor && !this.trackedAnchors.contains(pos)) {
            this.trackedAnchors.add(pos);
            if (this.anchorPlace.get()) {
                Vec3 center = Vec3.atCenterOf(pos);
                Player nearest = this.findNearestPlayer(center);
                String name = nearest != null ? nearest.getGameProfile().name() : "Unknown";
                if (this.chatNotify.get()) {
                    MsgUtil.sendMsg(
                        "§d" + name + " §7placed a §dRespawn Anchor §7at §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    );
                }

                this.playNotifSound();
                if (this.discordEnabled.get() && this.discordPvp.get() && !this.webhookUrl.get().isEmpty()) {
                    this.sendDiscordAsync(
                        "Respawn Anchor Placed",
                        "**"
                            + name
                            + "** placed a Respawn Anchor\nCoords: "
                            + pos.getX()
                            + " "
                            + pos.getY()
                            + " "
                            + pos.getZ()
                            + "\nDimension: "
                            + this.getDimension()
                    );
                }

                LogUtil.info("Anchor placed by " + name + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            }
        } else if (!isAnchor && this.trackedAnchors.remove(pos) && this.anchorPop.get()) {
            Vec3 center = Vec3.atCenterOf(pos);
            Player nearest = this.findNearestPlayer(center);
            String name = nearest != null ? nearest.getGameProfile().name() : "Unknown";
            if (this.chatNotify.get()) {
                MsgUtil.sendMsg("§c" + name + " §7popped a §cRespawn Anchor §7at §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            }

            this.playNotifSound();
            if (this.discordEnabled.get() && this.discordPvp.get() && !this.webhookUrl.get().isEmpty()) {
                this.sendDiscordAsync(
                    "Respawn Anchor Exploded",
                    "**"
                        + name
                        + "** popped a Respawn Anchor\nCoords: "
                        + pos.getX()
                        + " "
                        + pos.getY()
                        + " "
                        + pos.getZ()
                        + "\nDimension: "
                        + this.getDimension()
                );
            }

            LogUtil.info("Anchor popped by " + name + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        }
    }

    private void tickAnchorTracking() {
        this.trackedAnchors.removeIf(pos -> !(this.mc.level.getBlockState(pos).getBlock() instanceof RespawnAnchorBlock));
    }

    private Player findNearestPlayer(Vec3 pos) {
        double maxRange = this.pvpRange.get().intValue();
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof Player player
                && (!this.pvpIgnoreSelf.get() || player != this.mc.player)
                && (!this.ignoreFriends.get() || !Friends.get().isFriend(player))) {
                double dist = player.position().distanceTo(pos);
                if (dist <= maxRange && dist < nearestDist) {
                    nearest = player;
                    nearestDist = dist;
                }
            }
        }

        return nearest;
    }

    private String buildPlayerMessage(String name, String action, int x, int y, int z, String equipment) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(name).append("** ").append(action).append(" visual range");
        if (this.playerCoords.get()) {
            sb.append("\\nCoords: ").append(x).append(" ").append(y).append(" ").append(z);
        }

        sb.append("\\nDimension: ").append(this.getDimension());
        if (equipment != null && !equipment.isEmpty()) {
            sb.append("\\nGear: ").append(equipment);
        }

        return sb.toString();
    }

    private String getEquipmentString(Player player) {
        List<String> parts = new ArrayList<>();
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
        if (!head.isEmpty()) {
            parts.add(head.getHoverName().getString());
        }

        if (!chest.isEmpty()) {
            parts.add(chest.getHoverName().getString());
        }

        if (!legs.isEmpty()) {
            parts.add(legs.getHoverName().getString());
        }

        if (!feet.isEmpty()) {
            parts.add(feet.getHoverName().getString());
        }

        if (!mainHand.isEmpty()) {
            parts.add(mainHand.getHoverName().getString() + " (hand)");
        }

        if (!offHand.isEmpty()) {
            parts.add(offHand.getHoverName().getString() + " (off)");
        }

        return String.join(", ", parts);
    }

    private void playNotifSound() {
        if (this.soundEnabled.get() && !this.sound.get().isEmpty()) {
            this.mc
                .getSoundManager()
                .play(SimpleSoundInstance.forUI(this.sound.get().getFirst(), this.soundPitch.get().floatValue(), this.soundVolume.get().floatValue()));
        }
    }

    private void sendDiscordAsync(String title, String message) {
        String url = this.webhookUrl.get();
        String ping = this.pingId.get().isEmpty() ? null : this.pingId.get();
        String sender = this.mc.player != null ? this.mc.player.getGameProfile().name() : "Unknown";
        MeteorExecutor.execute(() -> Utils.sendWebhook(url, title, message, ping, sender));
    }

    private String getDimension() {
        if (this.mc.level == null) {
            return "Unknown";
        }

        String dim = this.mc.level.dimension().identifier().getPath();

        return switch (dim) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "The End";
            default -> dim;
        };
    }

    private String getNameFromUuid(UUID uuid) {
        if (this.mc.getConnection() != null) {
            PlayerInfo entry = this.mc.getConnection().getPlayerInfo(uuid);
            if (entry != null) {
                return entry.getProfile().name();
            }
        }

        String cached = this.uuidNameCache.get(uuid);
        return cached != null ? cached : uuid.toString();
    }
}
