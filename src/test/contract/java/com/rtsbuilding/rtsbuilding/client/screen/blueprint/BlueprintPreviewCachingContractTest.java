package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 渲染生命周期不适合在无窗口 JVM 创建 GL 上下文，用契约锁定共享缓冲与线程边界。 */
class BlueprintPreviewCachingContractTest {
    private static final Path RENDER = Path.of("src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/blueprint");

    @Test
    void blueprintModelsDoNotFlushSharedBuffersOrBakeWholeListFromFacade() throws Exception {
        String models = Files.readString(RENDER.resolve("BlueprintGhostBlockModelRenderer.java"));
        String facade = Files.readString(RENDER.resolve("BlueprintGhostRenderer.java"));
        String mesh = Files.readString(RENDER.resolve("BlueprintGhostMesh.java"));
        assertFalse(models.contains("renderBuffers()"));
        assertFalse(models.contains("endBatch("));
        assertFalse(facade.contains("BlueprintGhostBoundsFilter.filter("));
        assertFalse(facade.contains("for (BlueprintGhostBlock"));
        assertTrue(mesh.contains("BLOCKS_PER_FRAME"));
        assertTrue(mesh.contains("BUILD_NANOS"));
        assertTrue(mesh.contains("VertexBuffer.Usage.STATIC"));
        assertTrue(mesh.contains("frustum.isVisible"));
        assertTrue(mesh.contains("buildSortedIndexBuffer"));
        assertTrue(mesh.contains("batches.forEach(Batch::close)"));
        assertFalse(mesh.contains("CompletableFuture"), "模型和 GL 不得搬到后台线程");
    }

    @Test
    void resourceReloadWorldExitAndScreenExitReleaseMeshResources() throws Exception {
        String facade = Files.readString(RENDER.resolve("BlueprintGhostRenderer.java"));
        String events = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/bootstrap/RtsClientModEvents.java"));
        assertTrue(events.contains("BlueprintGhostRenderer.invalidate()"));
        assertTrue(facade.contains("ClientPlayerNetworkEvent.LoggingOut"));
        assertTrue(facade.contains("ClientTickEvent.Post"));
        assertTrue(facade.contains("!(minecraft.screen instanceof BuilderScreen)"));
        assertTrue(facade.contains("CACHE.close()"));
    }
}
