package com.rtsbuilding.rtsbuilding.server.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 区分玩家配置的 RTS 产品范围与不应继承的旧 Home Radius 重复门槛。 */
class RtsRemoteOperationRangeContractTest {
    @Test
    void remoteOperationsKeepConfiguredRtsRangeWithoutLegacyHomeRadius() throws Exception {
        String resolver = read("src/main/java/com/rtsbuilding/rtsbuilding/server/storage/resolver/RtsLinkedStorageResolver.java");
        String funnel = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsFunnelServiceImpl.java");
        String fluid = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsFluidServiceImpl.java");
        String transfer = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/transfer/RtsTransferPlayerIntegration.java");

        assertTrue(resolver.contains("isWithinActionRange("));
        assertTrue(funnel.contains("isWithinActionRange("));
        assertTrue(fluid.contains("isWithinActionRange("));
        assertTrue(transfer.contains("isWithinActionRange("));

        assertFalse(resolver.contains("canAccessHomeRadius("));
        assertFalse(funnel.contains("canAccessHomeRadius("));
        assertFalse(fluid.contains("canAccessHomeRadius("));
        assertFalse(transfer.contains("canAccessHomeRadius("));

        assertTrue(resolver.contains("RtsCameraManager.isActive(player)"));
        assertTrue(resolver.contains("level.hasChunkAt(pos)"));
        assertTrue(resolver.contains("level.mayInteract(player, pos)"));
        assertTrue(transfer.contains("refundToLinked(insertHandlers, player, extracted)"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
