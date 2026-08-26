package bep.hax.accessor;

import meteordevelopment.meteorclient.utils.misc.MBlockPos;

public interface IHighwayBuilder {
    boolean bephax$isPavingPlace();

    void bephax$stallPlacement(int var1);

    boolean bephax$shouldLockLine();

    void bephax$snapToLine(MBlockPos var1);
}
