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
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildControlRenderer.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("screen.width - tooltipWidth"));
        assertTrue(source.contains("screen.height - tooltipHeight"));
        assertFalse(source.contains("new ScaledResolution"));
    }
}
