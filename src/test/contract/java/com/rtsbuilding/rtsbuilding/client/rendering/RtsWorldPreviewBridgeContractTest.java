package com.rtsbuilding.rtsbuilding.client.rendering;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固化 26.1 世界预览的“纯快照 → 平台提交”边界。
 */
class RtsWorldPreviewBridgeContractTest {
    private static final Path JAVA_ROOT =
            Path.of("src/main/java/com/rtsbuilding/rtsbuilding");

    @Test
    void immutablePreviewStateDoesNotKnowNeoForgeCollectorTypes() throws IOException {
        String snapshot = read("client/rendering/state/RtsWorldPreviewSnapshot.java");
        String extractor = read("client/rendering/state/RtsWorldPreviewExtractor.java");

        assertFalse(snapshot.contains("net.neoforged"));
        assertFalse(snapshot.contains("SubmitNodeCollector"));
        assertFalse(extractor.contains("net.neoforged"));
    }

    @Test
    void platformBridgeUsesExtractThenSubmitEvents() throws IOException {
        String bridge = read(
                "platform/neoforge/client/rendering/NeoForgeWorldPreviewBridge.java");

        assertTrue(bridge.contains("ExtractLevelRenderStateEvent"));
        assertTrue(bridge.contains("SubmitCustomGeometryEvent"));
        assertTrue(bridge.contains("setRenderData"));
        assertTrue(bridge.contains("getRenderData"));
        assertTrue(bridge.contains("submitCustomGeometry"));
    }

    @Test
    void oldGhostModelEntrypointsNeverFlushSharedBuffers() throws IOException {
        String build = read("client/rendering/builder/BuildGhostModelRenderer.java");
        String blueprint = read(
                "client/rendering/blueprint/BlueprintGhostBlockModelRenderer.java");

        assertFalse(build.contains("renderBuffers()"));
        assertFalse(build.contains("endBatch("));
        assertFalse(blueprint.contains("renderBuffers()"));
        assertFalse(blueprint.contains("endBatch("));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(JAVA_ROOT.resolve(relative));
    }
}
