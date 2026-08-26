package bep.hax.util;

import net.minecraft.core.BlockPos;

public class PendingSignData {
    public final BlockPos pos;
    public final boolean front;
    public final String[] text;
    public int ticksRemaining;

    public PendingSignData(BlockPos pos, boolean front, String[] text, int ticksRemaining) {
        this.pos = pos;
        this.front = front;
        this.text = text;
        this.ticksRemaining = ticksRemaining;
    }
}
