package com.rtsbuilding.rtsbuilding.network.culling;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 26.1 范围剔除的注册、服务器维度复核与客户端隔离接线。 */
class RtsCullingNetworkContractTest {
    @Test
    void registersDedicatedRequestSaveAndClientRestoreRoutes() throws Exception {
        String registrar = source("network/RtsPayloadRegistrar.java");
        String packets = source("network/culling/RtsCullingPackets.java");
        String dispatcher = source("network/ClientPayloadDispatcher.java");

        assertTrue(registrar.contains("RtsCullingPackets.register(registrar)"));
        assertTrue(packets.contains("C2SRtsRequestCullingStatePayload.TYPE"));
        assertTrue(packets.contains("C2SRtsSaveCullingStatePayload.TYPE"));
        assertTrue(packets.contains("S2CRtsCullingStatePayload.TYPE"));
        assertTrue(dispatcher.contains("dispatchCulling"));
    }

    @Test
    void serverUsesPlayerCurrentDimensionForLateSaveProtection() throws Exception {
        String handlers = source("network/culling/RtsCullingNetworkHandlers.java");

        assertTrue(handlers.contains("player.level().dimension().identifier().toString()"));
        assertTrue(handlers.contains("currentDimension.equals(payload.dimension())"));
        assertTrue(handlers.contains("RtsCullingPersistence.save(player, payload.boxes(), payload.revealed())"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding/" + relative));
    }
}
