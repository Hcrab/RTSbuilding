package com.rtsbuilding.rtsbuilding.client.screen;

import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenShapeControllerFillModeSyncTest {
    @Test
    void buildFillModeButtonUpdatesActiveGeneratorMode() {
        ScreenShapeController controller = new ScreenShapeController();

        controller.setBuildShapeFillMode(ShapeFillMode.HOLLOW);

        assertEquals(ShapeFillMode.HOLLOW, controller.getBuildShapeFillMode());
        assertEquals(ShapeFillMode.HOLLOW, controller.getShapeFillMode());
    }

    @Test
    void destroyAndBuildModesKeepIndependentActiveValues() {
        ScreenShapeController controller = new ScreenShapeController();
        controller.setDestroyShapeFillMode(ShapeFillMode.HOLLOW);

        assertEquals(ShapeFillMode.FILL, controller.getShapeFillMode());
        controller.switchToDestroy();
        assertEquals(ShapeFillMode.HOLLOW, controller.getShapeFillMode());

        controller.setDestroyShapeFillMode(ShapeFillMode.FILL);
        assertEquals(ShapeFillMode.FILL, controller.getShapeFillMode());

        controller.setBuildShapeFillMode(ShapeFillMode.HOLLOW);
        assertEquals(ShapeFillMode.FILL, controller.getShapeFillMode());
        controller.switchToBuild();
        assertEquals(ShapeFillMode.HOLLOW, controller.getShapeFillMode());
    }
}
