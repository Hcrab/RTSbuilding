package com.rtsbuilding.rtsbuilding.client.module.overlay;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;

/**
 * 覆盖层模块——管理容器覆盖层（Overlay）的渲染和交互。
 */
public final class OverlayModule implements FeatureModule {

    @Override
    public String moduleId() {
        return "overlay";
    }

    @Override
    public void onSessionEvent(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            // Overlay can work outside RTS mode for storage browsing
        }
    }
}
