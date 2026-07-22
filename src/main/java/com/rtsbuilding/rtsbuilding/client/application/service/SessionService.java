package com.rtsbuilding.rtsbuilding.client.application.service;

import com.rtsbuilding.rtsbuilding.client.domain.event.EventBus;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;

public final class SessionService {
    private boolean active;
    private final EventBus eventBus;

    public SessionService(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void toggle(boolean enabled) {
        this.active = enabled;
        eventBus.publish(new StateEvent.RtsToggled(enabled));
    }

    public boolean isActive() {
        return active;
    }
}
