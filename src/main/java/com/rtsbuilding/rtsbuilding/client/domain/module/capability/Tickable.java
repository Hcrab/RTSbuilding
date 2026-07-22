package com.rtsbuilding.rtsbuilding.client.domain.module.capability;

import com.rtsbuilding.rtsbuilding.client.domain.time.Clock;

public interface Tickable {
    void tick(Clock clock);
}
