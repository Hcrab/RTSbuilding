package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsBatchPlacementQuickBuildContractTest {
    @Test
    void publicBatchPlacementKeepsQuickBuildFastPath() throws IOException {
        assertBatchEntryUsesQuickBuild(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsPlacementService.java"));
    }

    @Test
    void implBatchPlacementKeepsQuickBuildFastPath() throws IOException {
        assertBatchEntryUsesQuickBuild(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsPlacementServiceImpl.java"));
    }

    private static void assertBatchEntryUsesQuickBuild(Path sourcePath) throws IOException {
        String source = Files.readString(sourcePath);
        String method = methodBody(source, "enqueuePlaceBatch(ServerPlayer player, List<BlockPos> clickedPositions");
        int pipeline = method.indexOf("PipelineRegistry.execute(RtsWorkflowType.PLACE_BATCH");
        int quickBuild = method.indexOf(".quickBuild(true)", pipeline);
        int build = method.indexOf(".build())", pipeline);

        assertTrue(pipeline >= 0, "batch placement should enter PLACE_BATCH workflow");
        assertTrue(quickBuild > pipeline && quickBuild < build,
                "batch shape placement must keep quickBuild=true so creative builds use the fast setBlock path");
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "method body not found: " + signatureStart);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}
