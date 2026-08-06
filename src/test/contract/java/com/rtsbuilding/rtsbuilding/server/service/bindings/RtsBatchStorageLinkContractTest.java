package com.rtsbuilding.rtsbuilding.server.service.bindings;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 批量储存链接的服务端权威边界和客户端选择入口契约。 */
class RtsBatchStorageLinkContractTest {
    @Test
    void serverOnlyScansLoadedEndpointsAndKeepsIdentityScopedDeduplication() throws IOException {
        String service = source("server/service/bindings/RtsBatchStorageBindingService.java");

        assertTrue(service.contains("getLoadedChunk(chunkX, chunkZ)"));
        assertTrue(service.contains("RtsLinkedStorageResolver.canAccessWorldTarget"));
        assertTrue(service.contains("RtsLinkedStorageBindingService.canLinkStorageTarget"));
        assertTrue(service.contains("RtsStorageBindings.MAX_LINKED_STORAGES"));
        assertTrue(service.contains("this.identity == that.identity"));
        assertTrue(service.contains("System.identityHashCode(this.identity)"));
    }

    @Test
    void clientSelectionUsesSharedAnimationAndOnlySendsCorners() throws IOException {
        String session = source("client/screen/storage/StorageBatchSelectionSession.java");
        String renderer = source("client/rendering/storage/StorageBatchSelectionRenderer.java");
        String packet = source("network/storage/C2SRtsBatchLinkStoragePayload.java");
        String registration = source("network/storage/RtsStorageBindingPackets.java");

        assertTrue(session.contains("sendBatchLinkStorage(this.first, this.second"));
        assertTrue(session.contains("cancelOrExit"));
        assertTrue(renderer.contains("RtsSelectionBoxAnimator"));
        assertTrue(packet.contains("BlockPos first") && packet.contains("BlockPos second"));
        assertTrue(registration.contains("registerMessage(138"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding/" + relative));
    }
}
