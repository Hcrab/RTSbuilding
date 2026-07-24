package com.rtsbuilding.rtsbuilding.client.util.state;


public final class HoverSuppression {

    private boolean suppressed;

    
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
