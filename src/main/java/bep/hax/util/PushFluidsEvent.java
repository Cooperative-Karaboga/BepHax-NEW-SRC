package bep.hax.util;

public class PushFluidsEvent {
    private boolean canceled = false;

    public boolean isCanceled() {
        return this.canceled;
    }

    public void cancel() {
        this.canceled = true;
    }
}
