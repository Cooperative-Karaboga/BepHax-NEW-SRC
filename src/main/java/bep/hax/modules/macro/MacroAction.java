package bep.hax.modules.macro;

import org.jetbrains.annotations.Nullable;

public class MacroAction {
    public MacroAction.Type type;
    public int slotId;
    public int button;
    @Nullable
    public String actionType;
    @Nullable
    public String screenHandlerClass;
    public int blockX;
    public int blockY;
    public int blockZ;
    public double hitX;
    public double hitY;
    public double hitZ;
    @Nullable
    public String direction;
    @Nullable
    public String hand;
    public int hotbarSlot;
    public boolean dropEntireStack;
    @Nullable
    public String message;
    @Nullable
    public String entityType;
    public double entityDx;
    public double entityDy;
    public double entityDz;
    public double entityAbsX;
    public double entityAbsY;
    public double entityAbsZ;

    public static MacroAction clickSlot(int slotId, int button, String actionType, String screenHandlerClass) {
        MacroAction a = new MacroAction();
        a.type = MacroAction.Type.CLICK_SLOT;
        a.slotId = slotId;
        a.button = button;
        a.actionType = actionType;
        a.screenHandlerClass = screenHandlerClass;
        return a;
    }

    public static MacroAction containerButton(int buttonId) {
        MacroAction a = new MacroAction();
        a.type = MacroAction.Type.CONTAINER_BUTTON;
        a.button = buttonId;
        return a;
    }

    public static MacroAction renameItem(String name) {
        MacroAction a = new MacroAction();
        a.type = MacroAction.Type.RENAME_ITEM;
        a.message = name;
        return a;
    }

    public static MacroAction selectTrade(int tradeIndex) {
        MacroAction a = new MacroAction();
        a.type = MacroAction.Type.SELECT_TRADE;
        a.slotId = tradeIndex;
        return a;
    }

    public static MacroAction closeScreen() {
        MacroAction a = new MacroAction();
        a.type = MacroAction.Type.CLOSE_SCREEN;
        return a;
    }

    public static MacroAction interactBlock(int bx, int by, int bz, double hx, double hy, double hz, String direction, String hand) {
        MacroAction a = new MacroAction();
        a.type = MacroAction.Type.INTERACT_BLOCK;
        a.blockX = bx;
        a.blockY = by;
        a.blockZ = bz;
        a.hitX = hx;
        a.hitY = hy;
        a.hitZ = hz;
        a.direction = direction;
        a.hand = hand;
        return a;
    }

    public static MacroAction attackBlock(int bx, int by, int bz, String direction) {
        MacroAction a = new MacroAction();
        a.type = MacroAction.Type.ATTACK_BLOCK;
        a.blockX = bx;
        a.blockY = by;
        a.blockZ = bz;
        a.direction = direction;
        return a;
    }

    public static MacroAction interactItem(String hand) {
        MacroAction a = new MacroAction();
        a.type = MacroAction.Type.INTERACT_ITEM;
        a.hand = hand;
        return a;
    }

    public static MacroAction swapHotbarSlot(int slot) {
        MacroAction a = new MacroAction();
        a.type = MacroAction.Type.SWAP_HOTBAR_SLOT;
        a.hotbarSlot = slot;
        return a;
    }

    public static MacroAction dropItem(boolean entireStack) {
        MacroAction a = new MacroAction();
        a.type = MacroAction.Type.DROP_ITEM;
        a.dropEntireStack = entireStack;
        return a;
    }

    public static MacroAction sendChat(String message) {
        MacroAction a = new MacroAction();
        a.type = MacroAction.Type.SEND_CHAT;
        a.message = message;
        return a;
    }

    public static MacroAction interactEntity(String entityType, double dx, double dy, double dz, double absX, double absY, double absZ, String hand) {
        MacroAction a = new MacroAction();
        a.type = MacroAction.Type.INTERACT_ENTITY;
        a.entityType = entityType;
        a.entityDx = dx;
        a.entityDy = dy;
        a.entityDz = dz;
        a.entityAbsX = absX;
        a.entityAbsY = absY;
        a.entityAbsZ = absZ;
        a.hand = hand;
        return a;
    }

    public enum Type {
        CLICK_SLOT,
        CONTAINER_BUTTON,
        RENAME_ITEM,
        SELECT_TRADE,
        CLOSE_SCREEN,
        INTERACT_BLOCK,
        INTERACT_ITEM,
        ATTACK_BLOCK,
        SWAP_HOTBAR_SLOT,
        DROP_ITEM,
        SEND_CHAT,
        INTERACT_ENTITY;
    }
}
