package bep.hax.modules.macro;

import java.util.ArrayList;
import java.util.List;

public class MacroFile {
    public int version = 1;
    public String name;
    public long recordedAt;
    public String dimension;
    public double startX;
    public double startY;
    public double startZ;
    public float startYaw;
    public float startPitch;
    public int totalTicks;
    public List<MacroFrame> frames = new ArrayList<>();
}
