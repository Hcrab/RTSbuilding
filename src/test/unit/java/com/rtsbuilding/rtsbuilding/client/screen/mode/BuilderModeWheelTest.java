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

    @Test
    void placementPreviewUsesMinecraftCameraYawSign() {
        assertEquals(180.0F, PlacementStateWheel.placementPreviewYaw(0.0F));
        assertEquals(90.0F, PlacementStateWheel.placementPreviewYaw(90.0F));
        assertEquals(270.0F, PlacementStateWheel.placementPreviewYaw(-90.0F));
    }

    @Test
    void narrowVirtualScreenKeepsWheelCenterInsideTheScreen() {
        assertEquals(45, BuilderModeWheel.clampCenter(200.0D, 90));
        assertEquals(106, BuilderModeWheel.clampCenter(-50.0D, 400));
        assertEquals(294, BuilderModeWheel.clampCenter(500.0D, 400));
    }
}
