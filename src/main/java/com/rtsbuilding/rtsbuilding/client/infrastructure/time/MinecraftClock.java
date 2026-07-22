package com.rtsbuilding.rtsbuilding.client.infrastructure.time;

import com.rtsbuilding.rtsbuilding.client.domain.time.Clock;

public final class MinecraftClock implements Clock {
    private long epochMs;
    private int tickIndex;

    public void tick() {
        this.epochMs = System.currentTimeMillis();
        this.tickIndex++;
    }

    @Override
    public long epochMs() {
        return epochMs;
    }

    @Override
    public int tickIndex() {
        return tickIndex;
    }

    @Override
    public float partialTick() {
        return 0;
    }
}
