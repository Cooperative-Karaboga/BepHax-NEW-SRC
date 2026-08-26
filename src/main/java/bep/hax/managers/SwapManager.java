package bep.hax.managers;

import bep.hax.mixin.accessor.PlayerInventoryAccessor;
import bep.hax.util.InventoryManager;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;

public class SwapManager {
    private static SwapManager INSTANCE;
    private final InventoryManager inv = InventoryManager.getInstance();
    private Object owner;
    private int priority;
    private boolean silent;
    private int originalSlot = -1;
    private int heldSlot = -1;
    private int releaseTicks;
    private boolean actionConflict;
    private boolean movedSinceTickEnd;
    private int pendingTeleportConfirms;
    private boolean pendingRelease;
    private boolean bouncing;
    private Object resumeOwner;
    private int resumePriority;
    private boolean resumeSilent;
    private int resumeSlot = -1;
    private int resumeReleaseTicks;

    private SwapManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static SwapManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SwapManager();
        }

        return INSTANCE;
    }

    public boolean hold(Object owner, int slot, int priority, int releaseDelay) {
        return this.hold(owner, slot, priority, true, releaseDelay);
    }

    public boolean hold(Object owner, int slot, int priority, boolean silent, int releaseDelay) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.getConnection() != null && Inventory.isHotbarSlot(slot)) {
            this.checkDrift();
            if (this.owner != null) {
                if (this.owner != owner) {
                    if (priority <= this.priority) {
                        return false;
                    }

                    this.owner = owner;
                    this.clearResume();
                }

                this.priority = priority;
                this.silent = silent;
                if (this.inv.getServerSlot() == slot) {
                    this.heldSlot = slot;
                    if (this.resumeOwner == null) {
                        this.releaseTicks = Math.max(this.releaseTicks, releaseDelay);
                        this.pendingRelease = false;
                    }

                    return true;
                } else if (!this.actionConflict && !this.usingMainHandItem()) {
                    this.swapTo(slot);
                    this.heldSlot = slot;
                    if (this.resumeOwner == null) {
                        this.releaseTicks = Math.max(this.releaseTicks, releaseDelay);
                        this.pendingRelease = false;
                    }

                    return true;
                } else {
                    return false;
                }
            } else if (this.inv.getServerSlot() == slot) {
                return true;
            } else if (!this.actionConflict && !this.usingMainHandItem()) {
                this.owner = owner;
                this.priority = priority;
                this.silent = silent;
                this.originalSlot = this.clientSlot();
                this.swapTo(slot);
                this.heldSlot = slot;
                this.releaseTicks = releaseDelay;
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean pulse(Object owner, int slot, int priority) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.getConnection() != null && Inventory.isHotbarSlot(slot)) {
            this.checkDrift();
            if (this.owner == null || this.owner == owner) {
                return this.hold(owner, slot, priority, true, 1);
            } else if (this.resumeOwner != null) {
                return false;
            } else if (this.inv.getServerSlot() == slot) {
                return true;
            } else if (!this.actionConflict && !this.usingMainHandItem()) {
                this.resumeOwner = this.owner;
                this.resumePriority = this.priority;
                this.resumeSilent = this.silent;
                this.resumeSlot = this.heldSlot;
                this.resumeReleaseTicks = Math.max(1, this.releaseTicks);
                this.owner = owner;
                this.priority = Math.max(priority, this.resumePriority);
                this.silent = true;
                this.swapTo(slot);
                this.heldSlot = slot;
                this.releaseTicks = 1;
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean isForeignSession(Object owner, int priority) {
        this.checkDrift();
        return this.owner != null && this.owner != owner && this.priority > priority;
    }

    public void scheduleRelease(Object owner, int ticks) {
        if (this.owner == owner && this.resumeOwner == null) {
            this.releaseTicks = Math.max(this.releaseTicks, ticks);
        }
    }

    public void releaseNow(Object owner) {
        if (this.owner == owner) {
            if (!this.actionConflict && !this.usingMainHandItem()) {
                this.doRelease();
            } else {
                this.releaseTicks = 0;
                this.pendingRelease = true;
            }
        }
    }

    public void onUserAction() {
        if (MeteorClient.mc.player != null && MeteorClient.mc.getConnection() != null) {
            this.checkDrift();
            if (this.owner != null) {
                if (this.silent) {
                    this.clearResume();
                    this.doRelease();
                }
            } else {
                this.inv.syncToClient();
            }
        }
    }

    public boolean bounce(int via) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.getConnection() != null && Inventory.isHotbarSlot(via)) {
            this.checkDrift();
            if (!this.actionConflict && !this.movedSinceTickEnd && !this.usingMainHandItem()) {
                int slot = this.inv.getServerSlot();
                if (slot != via && Inventory.isHotbarSlot(slot)) {
                    this.bouncing = true;

                    try {
                        this.inv.setSlotForced(via);
                        this.inv.setSlotForced(slot);
                    } finally {
                        this.bouncing = false;
                    }

                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean isActionConflictTick() {
        return this.actionConflict;
    }

    public boolean isHolding(Object owner) {
        this.checkDrift();
        return this.owner == owner;
    }

    public int referenceSlot() {
        this.checkDrift();
        return this.owner != null ? this.originalSlot : this.clientSlot();
    }

    public int getServerSlot() {
        return this.inv.getServerSlot();
    }

    @EventHandler
    private void onPacketSend(Send event) {
        if (!event.isCancelled()) {
            if (!(event.packet instanceof ServerboundInteractPacket)
                && !(event.packet instanceof ServerboundUseItemPacket)
                && !(event.packet instanceof ServerboundUseItemOnPacket)
                && !(event.packet instanceof ServerboundPlayerCommandPacket)) {
                if (event.packet instanceof ServerboundPlayerActionPacket packet && packet.getAction() == Action.RELEASE_USE_ITEM) {
                    this.actionConflict = true;
                } else if (event.packet instanceof ServerboundMovePlayerPacket) {
                    if (this.pendingTeleportConfirms > 0) {
                        this.pendingTeleportConfirms--;
                    } else {
                        this.actionConflict = false;
                        this.movedSinceTickEnd = true;
                    }
                } else if (event.packet instanceof ServerboundClientTickEndPacket) {
                    if (!this.movedSinceTickEnd) {
                        this.actionConflict = false;
                    }

                    this.movedSinceTickEnd = false;
                }
            } else {
                this.actionConflict = true;
            }
        }
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            this.pendingTeleportConfirms++;
        }
    }

    private boolean usingMainHandItem() {
        return MeteorClient.mc.player != null
            && MeteorClient.mc.player.isUsingItem()
            && MeteorClient.mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND;
    }

    @EventHandler(priority = 200)
    private void onTickPre(Pre event) {
        this.checkDrift();
        if (this.pendingRelease && !this.actionConflict && !this.usingMainHandItem()) {
            this.pendingRelease = false;
            if (this.owner != null) {
                this.doRelease();
            }
        }
    }

    @EventHandler(priority = -200)
    private void onTickPost(Post event) {
        this.checkDrift();
        if (this.owner != null && this.releaseTicks > 0 && --this.releaseTicks == 0) {
            if (!this.actionConflict && !this.usingMainHandItem()) {
                this.pendingRelease = true;
            } else {
                this.releaseTicks = 1;
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.clearSession();
        this.actionConflict = false;
        this.movedSinceTickEnd = false;
        this.pendingTeleportConfirms = 0;
    }

    private void checkDrift() {
        if (!this.bouncing) {
            if (this.owner != null && this.inv.getServerSlot() != this.heldSlot) {
                this.clearSession();
            }
        }
    }

    private void doRelease() {
        if (this.resumeOwner != null) {
            this.owner = this.resumeOwner;
            this.priority = this.resumePriority;
            this.silent = this.resumeSilent;
            int slot = this.resumeSlot;
            this.releaseTicks = Math.max(1, this.resumeReleaseTicks);
            this.clearResume();
            if (MeteorClient.mc.player != null && MeteorClient.mc.getConnection() != null && Inventory.isHotbarSlot(slot)) {
                this.swapTo(slot);
                this.heldSlot = slot;
            } else {
                this.clearSession();
            }
        } else {
            boolean visible = !this.silent;
            int restore = this.originalSlot;
            this.clearSession();
            if (MeteorClient.mc.player != null && MeteorClient.mc.getConnection() != null) {
                if (visible && Inventory.isHotbarSlot(restore)) {
                    ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).setSelectedSlot(restore);
                    this.inv.setSlotForced(restore);
                } else {
                    this.inv.syncToClient();
                }
            }
        }
    }

    private void swapTo(int slot) {
        if (!this.silent) {
            ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).setSelectedSlot(slot);
        }

        this.inv.setSlotForced(slot);
    }

    private void clearSession() {
        this.owner = null;
        this.priority = 0;
        this.silent = true;
        this.originalSlot = -1;
        this.heldSlot = -1;
        this.releaseTicks = 0;
        this.pendingRelease = false;
        this.clearResume();
    }

    private void clearResume() {
        this.resumeOwner = null;
        this.resumePriority = 0;
        this.resumeSilent = true;
        this.resumeSlot = -1;
        this.resumeReleaseTicks = 0;
    }

    private int clientSlot() {
        return ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot();
    }

    public static final class Priority {
        public static final int FEED = 2;
        public static final int PRECHARGE = 3;
        public static final int MINING = 10;
        public static final int WEAPON = 20;
        public static final int SURROUND = 25;
        public static final int CRYSTAL = 30;
        public static final int PLACE = 35;
        public static final int PEARL = 40;
    }
}
