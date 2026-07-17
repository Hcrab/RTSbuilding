package com.rtsbuilding.rtsbuilding.client.screen.mode;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuilderModeWheelTest {
    @Test
    void fourDirectionsResolveToTheFourWorldInteractionModes() {
        BuilderModeWheel wheel = new BuilderModeWheel();
        wheel.open(200, 150, 400, 300);

        assertEquals(BuilderMode.INTERACT, wheel.hoveredMode(200, 110));
        assertEquals(BuilderMode.LINK_STORAGE, wheel.hoveredMode(240, 150));
        assertEquals(BuilderMode.FUNNEL, wheel.hoveredMode(200, 190));
        assertEquals(BuilderMode.ROTATE, wheel.hoveredMode(160, 150));
    }

    @Test
    void centerAndOutsideRingDoNotSelectAMode() {
        BuilderModeWheel wheel = new BuilderModeWheel();
        wheel.open(200, 150, 400, 300);

        assertNull(wheel.hoveredMode(200, 150));
        assertNull(wheel.hoveredMode(300, 150));
    }
}
