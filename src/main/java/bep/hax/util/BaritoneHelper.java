package bep.hax.util;

public class BaritoneHelper {
    private static Boolean available;
    private static Boolean elytraProcess;

    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName("baritone.api.BaritoneAPI");
                available = true;
            } catch (ClassNotFoundException e) {
                available = false;
            }
        }

        return available;
    }

    public static boolean hasElytraProcess() {
        if (elytraProcess == null) {
            if (!isAvailable()) {
                elytraProcess = false;
            } else {
                try {
                    Class.forName("baritone.api.IBaritone").getMethod("getElytraProcess");
                    elytraProcess = true;
                } catch (Exception e) {
                    elytraProcess = false;
                }
            }
        }

        return elytraProcess;
    }
}
