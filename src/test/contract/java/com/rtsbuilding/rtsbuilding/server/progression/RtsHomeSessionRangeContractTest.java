package com.rtsbuilding.rtsbuilding.server.progression;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsHomeSessionRangeContractTest {
    @Test
    void homeOnlyGatesCameraStartAndWorldActionsUseSessionAnchor() throws Exception {
        String progression = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/progression/RtsProgressionManager.java"));
        String camera = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/camera/RtsCameraManager.java"));
        String sources = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/storage/resolver/RtsLinkedStorageResolver.java"))
                + Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsFluidServiceImpl.java"))
                + Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/transfer/RtsTransferPlayerIntegration.java"));

        assertTrue(progression.contains("RtsHomeManager.canOpenRtsNearHome(player)"));
        assertTrue(camera.contains("\"message.rtsbuilding.home.too_far\""));
        assertTrue(camera.contains("session.anchor().x"));
        assertTrue(camera.contains("session.anchor().z"));
        assertTrue(sources.contains("RtsCameraManager.isWithinActionRange"));
        assertFalse(sources.contains("canAccessHomeRadius"));
    }
}
