package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildGhostWireframeRendererContractTest {
    @Test
    void buildPreviewUsesOnlyPerBlockWireframesAtSeventyPercentAlpha() throws IOException {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/BuildGhostWireframeRenderer.java"));
        String merged = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/MergedSkeletonRenderer.java"));

        assertTrue(renderer.contains("for (BlockPos pos : blocks)"));
        assertTrue(renderer.contains("lineR, lineG, lineB, 0.70F"));
        assertFalse(renderer.contains("PreviewLod") || renderer.contains("LARGE_SURFACE_EDGE_LIMIT"));
        assertFalse(merged.contains("buildPreviewOutlineEdges"));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/WireframeEdgeSimplifier.java")));
    }
}
