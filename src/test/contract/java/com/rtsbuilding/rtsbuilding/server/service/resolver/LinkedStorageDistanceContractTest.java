package com.rtsbuilding.rtsbuilding.server.service.resolver;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 已经建立的存储链接不应随 RTS 相机离开基地而失效。 */
class LinkedStorageDistanceContractTest {
    @Test
    void handlerResolutionUsesLinkedEndpointVisibilityNotCurrentCameraRange() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/resolver/"
                        + "RtsLinkedHandlerResolutionService.java"));

        assertTrue(source.contains("RtsLinkedStorageResolver.isLinkedRefWorldVisible(player, session, ref)"));
        assertFalse(source.contains("RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)"));
    }
}
