package com.rtsbuilding.rtsbuilding.client.domain.time;

public interface Clock {
    long epochMs();
    int tickIndex();
    float partialTick();
}
