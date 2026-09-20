package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 渲染生命周期不适合在无窗口 JVM 创建 GL 上下文，用契约锁定 Forge 线程和缓存边界。 */
class BlueprintPreviewCachingContractTest {
    private static final Path ROOT = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");
    private static final Path RENDER = ROOT.resolve("client/rendering/blueprint");
    private static final Path SCREEN = ROOT.resolve("client/screen/blueprint");

    @Test
    void blueprintModelsDoNotFlushSharedBuffersOrBakeWholeListFromFacade() throws Exception {
        String models = read(RENDER.resolve("BlueprintGhostBlockModelRenderer.java"));
        String facade = read(RENDER.resolve("BlueprintGhostRenderer.java"));
        String mesh = read(RENDER.resolve("BlueprintGhostMesh.java"));
        assertFalse(models.contains("renderBuffers()"));
        assertFalse(models.contains("endBatch("));
        assertFalse(facade.contains("BlueprintGhostBoundsFilter.filter("));
        assertFalse(facade.contains("for (BlueprintGhostBlock"));
        assertTrue(mesh.contains("BLOCKS_PER_FRAME"));
        assertTrue(mesh.contains("BUILD_NANOS"));
        assertTrue(mesh.contains("VertexBuffer.Usage.STATIC"));
        assertTrue(mesh.contains("frustum.isVisible"));
        assertTrue(mesh.contains("includeModelBounds"));
        assertTrue(mesh.contains("batches.forEach(Batch::close)"));
        assertFalse(mesh.contains("CompletableFuture"), "模型和 GL 不得搬到后台线程");
    }

    @Test
    void resourceReloadWorldExitAndScreenExitReleaseMeshResources() throws Exception {
        String facade = read(RENDER.resolve("BlueprintGhostRenderer.java"));
        String lifecycle = read(SCREEN.resolve("BlueprintClientLifecycle.java"));
        String reload = read(SCREEN.resolve("BlueprintClientReloadListener.java"));
        assertTrue(reload.contains("RegisterClientReloadListenersEvent"));
        assertTrue(reload.contains("BlueprintGhostRenderer.invalidate()"));
        assertTrue(facade.contains("ClientPlayerNetworkEvent.LoggingOut"));
        assertTrue(facade.contains("ClientTickEvent"));
        assertTrue(facade.contains("!(minecraft.screen instanceof BuilderScreen)"));
        assertTrue(facade.contains("CACHE.close()"));
        assertTrue(lifecycle.contains("BlueprintPanel.closeLibrary()"));
        assertTrue(lifecycle.contains("BlueprintPanel.tickLibrary()"));
    }

    @Test
    void libraryWorkerAndSessionKeepGenerationAndFilenameIdentityBoundaries() throws Exception {
        String repository = read(SCREEN.resolve("BlueprintLibraryRepository.java"));
        String worker = read(SCREEN.resolve("BlueprintLibraryLoadWorker.java"));
        String session = read(SCREEN.resolve("BlueprintLibrarySession.java"));
        String panel = read(SCREEN.resolve("BlueprintPanel.java"));
        assertTrue(repository.contains("RESULT_QUEUE_CAPACITY"));
        assertTrue(repository.contains("MAX_RESULTS_PER_PUMP"));
        assertTrue(repository.contains("result.generation() != generation"));
        assertTrue(repository.contains("isCurrentFileRevision(result)"));
        assertTrue(worker.contains("setDaemon(true)"));
        assertTrue(worker.contains("setPriority(Thread.MIN_PRIORITY)"));
        assertTrue(session.contains("selectedFileName"));
        assertTrue(session.contains("pendingSelectionFileName"));
        assertFalse(session.contains("private int selectedIndex"));
        assertTrue(panel.contains("selectedFileName"));
        assertFalse(panel.contains("selectedIndex"));
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }
}
