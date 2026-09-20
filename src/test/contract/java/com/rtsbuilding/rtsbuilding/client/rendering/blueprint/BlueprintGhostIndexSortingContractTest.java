package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 Forge 1.20.1 蓝图排序必须走私有 index-only 与绑定边界，不启动客户端或 GL。 */
class BlueprintGhostIndexSortingContractTest {
    private static final Path ROOT = Path.of("src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/blueprint");

    @Test
    void productionMeshCapturesStateAndCallsBoundedSortSchedule() throws Exception {
        String mesh = read(ROOT.resolve("BlueprintGhostMesh.java"));
        String sorter = read(ROOT.resolve("BlueprintGhostIndexSorter.java"));
        String scheduler = read(ROOT.resolve("BlueprintGhostSortScheduler.java"));
        assertTrue(mesh.contains("getSortState()"));
        assertTrue(mesh.contains("sortScheduler.schedule"));
        assertTrue(mesh.contains("batch.resort(indexSorter, localCamera)"));
        assertTrue(mesh.contains("VertexBuffer.unbind()"));
        assertTrue(sorter.contains("restoreSortState"));
        assertTrue(sorter.contains("target.bind()"));
        assertTrue(sorter.contains("target.upload(data)"));
        assertTrue(sorter.contains("target.isInvalid()"));
        assertTrue(sorter.contains("VertexBuffer.unbind()"));
        assertFalse(sorter.contains("Tesselator.getInstance"));
        assertTrue(scheduler.contains("SORT_DISTANCE_SQR = 1.0D"));
        assertTrue(scheduler.contains("SORT_BUDGET_NANOS = 1_000_000L"));
        assertFalse(mesh.contains("CompletableFuture"));
    }

    @Test
    void lineAndModelLayersSkipInvalidOwnedBuffersAndCloseClearsSortState() throws Exception {
        String mesh = read(ROOT.resolve("BlueprintGhostMesh.java"));
        assertTrue(mesh.contains("buffer == null || buffer.isInvalid()"));
        assertTrue(mesh.contains("sortState = null"));
        assertTrue(mesh.contains("sortedFrom = null"));
        assertTrue(mesh.contains("indexSorter.close()"));
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }
}
