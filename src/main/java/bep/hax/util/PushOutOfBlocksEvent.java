package bep.hax.util;

public class PushOutOfBlocksEvent {
    private boolean canceled = false;

    public boolean isCanceled() {
        return this.canceled;
    }

    public void cancel() {
        this.canceled = true;
    }
}
