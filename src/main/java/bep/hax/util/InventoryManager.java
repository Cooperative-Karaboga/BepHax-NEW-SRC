package bep.hax.util;

import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.mixin.accessor.UpdateSelectedSlotS2CPacketAccessor;
import java.util.Arrays;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;

public class InventoryManager {
    private static InventoryManager INSTANCE;
    private int serverSlot = -1;
    private int lastSentSlot = -1;
    private boolean sendingPacket = false;
    private boolean isEating = false;
    private long lastSetbackTime = -1L;
    private final int[] transactions = new int[4];
    private int transactionIndex = 0;
    private boolean isGrim = false;
    private int currentPriority = 0;

    private InventoryManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
        Arrays.fill(this.transactions, -1);
    }

    public static InventoryManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new InventoryManager();
        }

        return INSTANCE;
    }

    @EventHandler
    public void onPacketSend(Send event) {
        if (!this.sendingPacket) {
            if (!event.isCancelled()) {
                if (event.packet instanceof ServerboundSetCarriedItemPacket packet) {
                    int packetSlot = packet.getSlot();
                    if (!Inventory.isHotbarSlot(packetSlot) || this.serverSlot == packetSlot) {
                        event.cancel();
                        return;
                    }

                    if (this.lastSentSlot == packetSlot) {
                        event.cancel();
                        this.setSlotForced(packetSlot);
                        return;
                    }

                    this.serverSlot = packetSlot;
                    this.lastSentSlot = packetSlot;
                }
            }
        }
    }

    @EventHandler(priority = 200)
    public void onPacketReceive(Receive event) {
        if (event.packet instanceof ClientboundSetHeldSlotPacket packet) {
            this.serverSlot = ((UpdateSelectedSlotS2CPacketAccessor)(Object)packet).getSlot();
        } else if (event.packet instanceof ClientboundPingPacket packet) {
            if (this.transactionIndex > 3) {
                return;
            }

            int uid = packet.getId();
            this.transactions[this.transactionIndex] = uid;
            this.transactionIndex++;
            if (this.transactionIndex == 4) {
                this.grimCheck();
            }
        } else if (event.packet instanceof ClientboundPlayerPositionPacket) {
            this.lastSetbackTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onTick(Post event) {
        if (MeteorClient.mc.player != null && this.serverSlot == -1) {
            this.serverSlot = ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot();
        }

        if (!this.isEating && this.currentPriority > 0) {
            this.currentPriority = 0;
        }
    }

    @EventHandler
    public void onDisconnect(GameLeftEvent event) {
        Arrays.fill(this.transactions, -1);
        this.transactionIndex = 0;
        this.isGrim = false;
        this.lastSetbackTime = -1L;
        this.serverSlot = -1;
        this.lastSentSlot = -1;
        this.currentPriority = 0;
        this.isEating = false;
    }

    private void grimCheck() {
        for (int i = 0; i < 4; i++) {
            if (this.transactions[i] != -i) {
                return;
            }
        }

        this.isGrim = true;
        LogUtil.info("Server is running GrimAC.");
    }

    public boolean isGrim() {
        return this.isGrim;
    }

    public boolean hasPassed(long timeMS) {
        return this.lastSetbackTime != -1L && System.currentTimeMillis() - this.lastSetbackTime >= timeMS;
    }

    public void setSlot(int barSlot) {
        this.setSlot(barSlot, 0);
    }

    public void setSlot(int barSlot, boolean highPriority) {
        this.setSlot(barSlot, highPriority ? 20 : 0);
    }

    public void setSlot(int barSlot, int priority) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.getConnection() != null) {
            if (priority >= this.currentPriority) {
                if (this.serverSlot == -1) {
                    this.serverSlot = ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot();
                }

                if (this.serverSlot != barSlot && Inventory.isHotbarSlot(barSlot)) {
                    this.setSlotForced(barSlot);
                    this.currentPriority = priority;
                }
            }
        }
    }

    public void setSlotForced(int barSlot) {
        if (MeteorClient.mc.getConnection() != null && Inventory.isHotbarSlot(barSlot)) {
            if (this.serverSlot != barSlot) {
                this.sendingPacket = true;

                try {
                    if (this.lastSentSlot == barSlot) {
                        int bounce = (barSlot + 1) % 9;
                        MeteorClient.mc.getConnection().send(new ServerboundSetCarriedItemPacket(bounce));
                        this.lastSentSlot = bounce;
                    }

                    MeteorClient.mc.getConnection().send(new ServerboundSetCarriedItemPacket(barSlot));
                    this.lastSentSlot = barSlot;
                    this.serverSlot = barSlot;
                } finally {
                    this.sendingPacket = false;
                }
            }
        }
    }

    public void syncToClient() {
        if (MeteorClient.mc.player != null) {
            if (this.isDesynced()) {
                this.setSlotForced(((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot());
            }
        }
    }

    public boolean isDesynced() {
        return MeteorClient.mc.player == null
            ? false
            : ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot() != this.serverSlot;
    }

    public int getServerSlot() {
        if (MeteorClient.mc.player == null) {
            return -1;
        } else {
            return this.serverSlot == -1 ? ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot() : this.serverSlot;
        }
    }

    public int getLastSentSlot() {
        return this.lastSentSlot;
    }

    public void setEating(boolean eating) {
        this.isEating = eating;
        if (eating) {
            this.currentPriority = 10;
        } else {
            this.currentPriority = 0;
        }
    }

    public boolean isEating() {
        return this.isEating;
    }

    public interface IPlayerInteractEntityC2SPacket {
        boolean isAttackPacket();

        int getTargetEntityId();
    }

    public static class Priority {
        public static final int NORMAL = 0;
        public static final int TOTEM = 5;
        public static final int EATING = 10;
        public static final int SURROUND = 20;
        public static final int PEARL_PHASE = 30;
    }

    public enum VelocityMode {
        NORMAL,
        WALLS,
        GRIM,
        GRIM_V3,
        GRIM_SKIP;
    }
}
