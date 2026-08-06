package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickBuildButtonVisualStateContractTest {
    @Test
    void 模式形状和填充按钮都把业务状态送入实际绘制路径() throws IOException {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildControlRenderer.java"));
        String surface = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildControlSurface.java"));
        String button = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/widget/WindowButton.java"));

        assertTrue(renderer.contains("state.mode != QuickBuildUiMode.DESTROY")
                && renderer.contains("state.mode == QuickBuildUiMode.DESTROY")
                && renderer.contains("&& enabled"));
        assertTrue(renderer.contains("QuickBuildStyle.mode(enabled, active, hovered)"));
        assertTrue(surface.contains("setSelectedVisual(option.selected)"));
        assertTrue(surface.contains("setSelectedVisual(control.selected)"));
        assertTrue(button.contains(
                "useActiveTexture(\n                selectedVisual, effectiveHovered, pressedVisual)"));
        assertTrue(button.contains("visual.getOverlay().alpha() > 0"));
    }
}
