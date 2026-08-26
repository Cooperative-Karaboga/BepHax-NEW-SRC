package bep.hax.modules.chesttracker;

import net.minecraft.client.Minecraft;

public class ChestTrackerDataManager {
    private static ChestTrackerDataV2 sharedData = null;
    private static int activeModuleCount = 0;
    private static String currentServerIdentifier = null;

    public static synchronized ChestTrackerDataV2 onModuleActivate() {
        activeModuleCount++;
        return sharedData;
    }

    public static synchronized void onModuleDeactivate() {
        activeModuleCount--;
        if (activeModuleCount <= 0) {
            if (sharedData != null) {
                sharedData.saveData();
                sharedData = null;
            }

            activeModuleCount = 0;
            currentServerIdentifier = null;
        }
    }

    public static synchronized ChestTrackerDataV2 getData() {
        String serverNow = getCurrentServerIdentifier();
        if (sharedData != null && currentServerIdentifier != null && !currentServerIdentifier.equals(serverNow)) {
            sharedData.saveData();
            sharedData.reinitializeForNewServer();
            sharedData.loadData();
            currentServerIdentifier = serverNow;
        }

        if (sharedData == null) {
            sharedData = new ChestTrackerDataV2();
            sharedData.loadData();
            currentServerIdentifier = serverNow;
        }

        return sharedData;
    }

    private static String getCurrentServerIdentifier() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return "unknown";
        } else if (mc.getCurrentServer() != null) {
            return mc.getCurrentServer().ip;
        } else {
            return mc.isLocalServer() && mc.getSingleplayerServer() != null ? "singleplayer_" + mc.getSingleplayerServer().getWorldData().getLevelName() : "unknown";
        }
    }

    public static synchronized void saveData() {
        if (sharedData != null) {
            sharedData.saveData();
        }
    }

    public static synchronized void onWorldJoin() {
        String serverNow = getCurrentServerIdentifier();
        if (sharedData != null) {
            sharedData.saveData();
            sharedData.reinitializeForNewServer();
            sharedData.loadData();
            currentServerIdentifier = serverNow;
        } else if (activeModuleCount > 0) {
            sharedData = new ChestTrackerDataV2();
            sharedData.loadData();
            currentServerIdentifier = serverNow;
        }
    }
}
