package com.rtsbuilding.rtsbuilding.client.kernel;

public final class EpochClock {
    private long epochMs;
    private int tickIndex;

    
    public long tick() {
        this.epochMs = System.currentTimeMillis();
        this.tickIndex++;
        return this.epochMs;
    }

    
    public long epochMs() {
        return this.epochMs;
    }

    
    public int tickIndex() {
        return this.tickIndex;
    }
}
