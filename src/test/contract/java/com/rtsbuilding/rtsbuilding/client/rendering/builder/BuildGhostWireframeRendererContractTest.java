package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildGhostWireframeRendererContractTest {
    @Test
    void buildPreviewAppendsPerBlockWireframesToCallerBuffer() throws IOException {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/BuildGhostWireframeRenderer.java"));
        String merged = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/MergedSkeletonRenderer.java"));

        assertTrue(renderer.contains("for (BlockPos pos : blocks)"));
        assertTrue(renderer.contains("callerBuffer,"));
        assertTrue(renderer.contains("lineR") && renderer.contains("lineG")
                && renderer.contains("lineB") && renderer.contains("0.70F"));
        assertFalse(renderer.contains("new BufferBuilder"));
        assertFalse(renderer.contains(".begin("));
        assertFalse(renderer.contains("WorldVertexBufferUploader")
                || renderer.contains("RtsOwnedBufferUploader"));
        assertFalse(renderer.contains("RenderGlobal.drawBoundingBox("));
        assertTrue(renderer.contains("appendLineBox("));
        assertTrue(renderer.contains("appendEdge("));
        assertFalse(renderer.contains("PreviewLod") || renderer.contains("LARGE_SURFACE_EDGE_LIMIT"));
        assertFalse(merged.contains("buildPreviewOutlineEdges"));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/WireframeEdgeSimplifier.java")));
    }
}
