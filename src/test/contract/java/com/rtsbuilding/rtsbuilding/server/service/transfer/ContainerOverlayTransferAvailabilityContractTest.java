package com.rtsbuilding.rtsbuilding.server.service.transfer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止移植层再次把可见的容器 overlay 错绑到 RTS 相机 active 状态或拒绝玩家背包 window 0。 */
class ContainerOverlayTransferAvailabilityContractTest {
    private static final Path HANDLER = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/network/storage/handler/RtsTransferHandlers.java");
    private static final Path SERVICE = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/server/service/transfer/RtsTransferPlayerIntegration.java");

    @Test
    void visibleOverlayTransfersDoNotRequireAnActiveCamera() throws IOException {
        String handler = read(HANDLER);

        assertFalse(handler.contains("RtsCameraManager"));
        assertFalse(handler.contains("if (active(player))"));
        assertFalse(handler.contains("menu.windowId == 0"));
    }

    @Test
    void transferServiceStillOwnsAuthorizationAndLinkedStorageValidation() throws IOException {
        String service = read(SERVICE);

        assertTrue(service.contains("RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)"));
        assertTrue(service.contains("RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)"));
        assertTrue(service.contains("RtsLinkedStorageResolver.resolveLinkedHandlers(player, session)"));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
