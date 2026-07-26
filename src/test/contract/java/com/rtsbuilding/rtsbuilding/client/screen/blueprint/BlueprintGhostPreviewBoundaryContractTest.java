package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定蓝图虚影的唯一模型和纯生成边界，避免 Panel 内嵌记录与独立记录再次并存。
 */
class BlueprintGhostPreviewBoundaryContractTest {
    private static final Path BLUEPRINT_ROOT = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint");
    private static final Path RENDER_ROOT = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/blueprint");

    @Test
    void panelDelegatesGeometryAndDoesNotOwnDuplicatePreviewRecords()
            throws IOException {
        String panel = source(BLUEPRINT_ROOT.resolve("BlueprintPanel.java"));
        String placement = source(BLUEPRINT_ROOT.resolve("BlueprintPlacementSession.java"));
        String factory = source(BLUEPRINT_ROOT.resolve(
                "BlueprintPlacementPreviewFactory.java"));
        String screen = source(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/"
                        + "BuilderScreen.java"));

        assertTrue(placement.contains(
                "BlueprintPlacementPreviewFactory.anchorForCursorTarget("));
        assertTrue(placement.contains("BlueprintPlacementPreviewFactory.create("));
        assertTrue(panel.contains("return PLACEMENT.anchorForCursorTarget(cursorTarget);"));
        assertTrue(panel.contains("return PLACEMENT.createGhostPreview("));
        assertFalse(panel.contains("BlueprintPlacementPreviewFactory."));
        assertFalse(panel.contains("record BlueprintGhostBlock"));
        assertFalse(panel.contains("record BlueprintGhostPreview"));
        assertFalse(screen.contains("new BlueprintGhostPreview("),
                "主屏幕应直接返回唯一预览模型，不再每帧重新包装");

        assertFalse(factory.contains("Minecraft.getInstance"));
        assertFalse(factory.contains("ClientRtsController"));
        assertFalse(factory.contains("Config."));
        assertFalse(factory.contains("PacketDistributor"));
    }

    @Test
    void renderersDependOnStandaloneGhostBlockModel() throws IOException {
        List<Path> consumers = List.of(
                BLUEPRINT_ROOT.resolve("BlueprintGhostPreview.java"),
                RENDER_ROOT.resolve("BlueprintGhostRenderer.java"),
                RENDER_ROOT.resolve("BlueprintGhostBoundsFilter.java"),
                RENDER_ROOT.resolve("BlueprintGhostFallbackRenderer.java"),
                RENDER_ROOT.resolve("BlueprintGhostBlockModelRenderer.java"));

        for (Path consumer : consumers) {
            assertFalse(source(consumer).contains(
                            "BlueprintPanel.BlueprintGhostBlock"),
                    "旧 Panel 内嵌数据模型仍被引用: " + consumer);
        }
        assertTrue(source(BLUEPRINT_ROOT.resolve("BlueprintGhostPreview.java"))
                .contains("List<BlueprintGhostBlock> blocks"));
    }

    private static String source(Path path) throws IOException {
        return Files.readString(path);
    }
}
