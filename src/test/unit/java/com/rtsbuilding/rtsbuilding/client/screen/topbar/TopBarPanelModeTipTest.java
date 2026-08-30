package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopBarPanelModeTipTest {
    @Test
    void funnelModeExposesItsContextualTip() {
        assertEquals("screen.rtsbuilding.mode_tip.funnel",
                TopBarPanel.modeTipKey(BuilderMode.FUNNEL));
    }

    @Test
    void unrelatedModesDoNotShowTheFunnelTip() {
        assertEquals("", TopBarPanel.modeTipKey(BuilderMode.INTERACT));
        assertEquals("", TopBarPanel.modeTipKey(BuilderMode.LINK_STORAGE));
        assertEquals("", TopBarPanel.modeTipKey(BuilderMode.ROTATE));
    }
}
