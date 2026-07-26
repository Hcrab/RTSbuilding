package com.rtsbuilding.rtsbuilding.client.util.state;

public final class HoverSuppression {

    private static final HoverSuppression FLOATING_WINDOW = new HoverSuppression();

    public static HoverSuppression floatingWindow() {
        return FLOATING_WINDOW;
    }

    private boolean suppressed;

    public HoverSuppression() {}

    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    public void clear() {
        this.suppressed = false;
    }
}
