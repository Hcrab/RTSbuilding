package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 详情窗口的关闭、滚轮隔离和共享边界接线证据。 */
final class RtsWorkflowDetailPanelContractTest {
    @Test
    void productionPanelUsesOneViewportAndExplicitClosePath() throws IOException {
        String panel = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/workflow/RtsWorkflowDetailPanel.java"));
        String base = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/RtsWindowPanel.java"));
        assertTrue(panel.contains("RtsWorkflowDetailLayout"));
        assertTrue(panel.contains("handleContentScroll"));
        assertTrue(panel.contains("setOpen(false)"));
        assertTrue(base.contains("GLFW.GLFW_KEY_ESCAPE"));
        assertTrue(base.contains("handleContentScroll(mouseX, mouseY, scrollX, scrollY)"));
    }
}
