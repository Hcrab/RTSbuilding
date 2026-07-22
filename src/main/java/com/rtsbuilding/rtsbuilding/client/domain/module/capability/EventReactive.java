package com.rtsbuilding.rtsbuilding.client.domain.module.capability;

import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;

public interface EventReactive {
    void onEvent(StateEvent event);
}
