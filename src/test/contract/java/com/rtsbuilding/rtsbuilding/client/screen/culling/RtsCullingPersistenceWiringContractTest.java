package com.rtsbuilding.rtsbuilding.client.screen.culling;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCullingPersistenceWiringContractTest {
    @Test
    void clientClearsOnConnectionChangeAndRequestsServerStateOnScreenOpen() throws Exception {
        String inputGate = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/input/RtsClientInputGate.java"));
        String builderScreen = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreen.java"));
        String clientState = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/culling/RtsCullingClientState.java"));

        assertTrue(inputGate.contains("onClientLoggingIn"));
        assertTrue(inputGate.contains("onClientLoggingOut"));
        assertTrue(inputGate.contains("RtsCullingClientState.resetForWorldChange()"));
        assertTrue(builderScreen.contains("RtsCullingClientState.requestCurrentWorldState()"));
        assertTrue(clientState.contains("C2SRtsSaveCullingStatePayload"));
        assertTrue(clientState.contains("currentDimension.equals(payload.dimension())"));
    }

    @Test
    void serverRejectsLateCrossDimensionSavePackets() throws Exception {
        String handlers = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/culling/RtsCullingNetworkHandlers.java"));
        assertTrue(handlers.contains("currentDimension.equals(payload.dimension())"));
        assertFalse(handlers.contains("payload.dimension(), payload.boxes()"));
    }
}
