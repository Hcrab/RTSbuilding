package com.rtsbuilding.rtsbuilding.client.screen.handler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定形状 UI 文案与世界交互控制器的生产边界。
 */
class ShapeSelectionTextPresenterContractTest {
    @Test
    void controllerDelegatesEveryPlayerFacingShapeTextFamily() throws IOException {
        String controller = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));

        assertTrue(controller.contains("ShapeSelectionTextPresenter.fillModeLabel"));
        assertTrue(controller.contains("ShapeSelectionTextPresenter.dimensionLabel"));
        assertTrue(controller.contains("ShapeSelectionTextPresenter.sizeText"));
        assertTrue(controller.contains("ShapeSelectionTextPresenter.countText"));
        assertTrue(controller.contains("ShapeSelectionTextPresenter.pendingStatusText"));
        assertTrue(controller.contains("ShapeSelectionTextPresenter.shapeLabel"));
        assertFalse(controller.contains("private static String confirmStatusKey"),
                "确认状态翻译键不应回流到世界交互控制器");
    }

    @Test
    void presenterCannotReadScreenConfigWorldOrKeyMappings() throws IOException {
        String presenter = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeSelectionTextPresenter.java"));

        assertFalse(presenter.contains("BuilderScreen"));
        assertFalse(presenter.contains("ClientRtsController"));
        assertFalse(presenter.contains("ClientKeyMappings"));
        assertFalse(presenter.contains("Config."));
        assertFalse(presenter.contains("Minecraft."));
        assertFalse(presenter.contains("ShapeGeometryUtil."));
        assertFalse(presenter.contains("generateShapePositions"));
    }
}
