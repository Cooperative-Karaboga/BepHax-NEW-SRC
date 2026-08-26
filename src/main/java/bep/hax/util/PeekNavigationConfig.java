package bep.hax.util;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

public class PeekNavigationConfig {
    private static boolean enabled = true;
    private static boolean navigatingBack = false;
    private static final Deque<Screen> screenStack = new ArrayDeque<>();

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean val) {
        enabled = val;
    }

    public static boolean isNavigatingBack() {
        return navigatingBack;
    }

    public static void setNavigatingBack(boolean val) {
        navigatingBack = val;
    }

    public static void pushScreen(Screen screen) {
        screenStack.push(screen);
    }

    @Nullable
    public static Screen popScreen() {
        return screenStack.isEmpty() ? null : screenStack.pop();
    }

    public static void clearStack() {
        screenStack.clear();
    }
}
