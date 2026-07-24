package com.rtsbuilding.rtsbuilding.client.infrastructure.module.overlay;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;


public final class OverlayModule implements FeatureModule {

    @Override
    public String moduleId() {
        return "overlay";
    }

    @Override
    public void onSessionEvent(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            
        }
    }
}
