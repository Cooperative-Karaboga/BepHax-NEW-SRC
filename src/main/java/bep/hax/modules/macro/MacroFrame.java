package bep.hax.modules.macro;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class MacroFrame {
    public float yaw;
    public float pitch;
    public double posX;
    public double posY;
    public double posZ;
    public float movementForward;
    public float movementSideways;
    public boolean jumping;
    public boolean sneaking;
    public boolean sprinting;
    public boolean using;
    public boolean attacking;
    public int selectedSlot;
    @Nullable
    public String currentScreenClass;
    public List<MacroAction> actions = new ArrayList<>();
}
