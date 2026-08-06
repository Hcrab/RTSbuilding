package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 Quick Build tooltip 与 RTS 虚拟视口使用同一坐标系。 */
class QuickBuildTooltipScaleContractTest {
    @Test
    void edgeClampUsesBuilderVirtualViewport() throws Exception {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildControlRenderer.java"),
                StandardCharsets.UTF_8);
        String tooltip = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildHoverTooltipRenderer.java"),
                StandardCharsets.UTF_8);
        String layout = Files.readString(Path.of(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/layout/QuickBuildWindowLayout.java"),
                StandardCharsets.UTF_8);
        assertTrue(renderer.contains("QuickBuildHoverTooltipRenderer.render"));
        assertTrue(tooltip.contains("QuickBuildWindowLayout.tooltipBounds(screen.width, screen.height"));
        assertTrue(layout.contains("screenWidth - TOOLTIP_SCREEN_MARGIN - width"));
        assertTrue(layout.contains("screenHeight - TOOLTIP_SCREEN_MARGIN - height"));
        assertFalse(renderer.contains("new ScaledResolution"));
        assertFalse(tooltip.contains("new ScaledResolution"));
    }
}
