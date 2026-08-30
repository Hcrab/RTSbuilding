package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeModeStateTest {
    @Test
    void switchesBetweenIndependentBuildAndDestroySnapshots() {
        ShapeModeState state = new ShapeModeState();
        state.setBuildFillMode(ShapeFillMode.HOLLOW);
        state.setBuildLineConnected(true);
        state.setBuildRotateDegrees(450);
        state.setDestroyFillMode(ShapeFillMode.FILL);
        state.setDestroyRotateDegrees(-90);
        state.applyBuildState();

        state.switchToDestroy();
        assertTrue(state.destroyActive());
        assertEquals(ShapeFillMode.FILL, state.activeFillMode());
        assertFalse(state.activeLineConnected());
        assertEquals(270, state.activeRotateDegrees());

        state.setActiveLineConnected(true);
        state.switchToBuild();
        assertFalse(state.destroyActive());
        assertEquals(ShapeFillMode.HOLLOW, state.activeFillMode());
        assertTrue(state.activeLineConnected());
        assertEquals(90, state.activeRotateDegrees());

        state.switchToDestroy();
        assertTrue(state.activeLineConnected());
    }

    @Test
    void modeSpecificSetterOnlyChangesActiveStateForCurrentMode() {
        ShapeModeState state = new ShapeModeState();
        state.setBuildFillMode(ShapeFillMode.HOLLOW);
        assertEquals(ShapeFillMode.HOLLOW, state.activeFillMode());

        state.setDestroyFillMode(ShapeFillMode.SKELETON);
        assertEquals(ShapeFillMode.HOLLOW, state.activeFillMode());

        state.switchToDestroy();
        state.setDestroyFillMode(ShapeFillMode.FILL);
        assertEquals(ShapeFillMode.FILL, state.activeFillMode());

        state.setBuildFillMode(ShapeFillMode.SKELETON);
        assertEquals(ShapeFillMode.FILL, state.activeFillMode());
    }
}
