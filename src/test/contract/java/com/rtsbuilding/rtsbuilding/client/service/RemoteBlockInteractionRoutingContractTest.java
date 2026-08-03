package com.rtsbuilding.rtsbuilding.client.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteBlockInteractionRoutingContractTest {
    @Test
    void 空手点方块走专用交互包而不是放置队列() throws IOException {
        String service = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/service/BuildPlacementService.java"));
        int start = service.indexOf("public void interactEmpty(");
        int end = service.indexOf("public void interactEntityEmpty(", start);
        String method = service.substring(start, end);

        assertTrue(method.contains("sendInteractBlockEmptyHand("));
        assertFalse(method.contains("sendEmptyHandPlace("));

        String gateway = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/network/RtsClientPacketGateway.java"));
        int gatewayStart = gateway.indexOf("public static void sendInteractBlockEmptyHand(");
        int gatewayEnd = gateway.indexOf("public static void sendUseItemInAirWithToolSlot(", gatewayStart);
        String gatewayMethod = gateway.substring(gatewayStart, gatewayEnd);
        assertTrue(gatewayMethod.contains("new C2SRtsInteractPayload("));
        assertTrue(gatewayMethod.contains("C2SRtsInteractPayload.SOURCE_EMPTY_HAND"));
    }

    @Test
    void 服务端交互链登记远程菜单会话() throws IOException {
        String implementation = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsInteractionServiceImpl.java"));
        assertTrue(implementation.contains("RtsEmptyHandInteractor.interactWithEmptyHand("));
        assertTrue(implementation.contains("RtsRemoteMenuService.markRemoteMenuOpen("));
    }
}
