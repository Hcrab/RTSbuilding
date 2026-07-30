package com.rtsbuilding.rtsbuilding.server.service.mining;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsNativeItemInteractionContractTest {
    @Test
    void clientGuiPredictionIsExplicitlyRegisteredAndNeverUsesVanillaGameModePackets() throws Exception {
        String registry = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsClientItemUseRegistry.java"));

        assertTrue(registry.contains("withDefaultNamespace(\"written_book\"), Activation.ALWAYS")
                        && registry.contains("withDefaultNamespace(\"writable_book\"), Activation.ALWAYS"),
                "Vanilla books must keep their client-opened screens in RTS mode.");
        assertTrue(registry.contains("\"create\", \"handheld_worldshaper\"), Activation.SHIFT_ONLY"),
                "Worldshaper client use must be restricted to its Shift-opened GUI.");
        assertTrue(registry.contains("stack.useOn(") && registry.contains("stack.use("),
                "Registered client GUI items must retain the normal useOn-then-use order.");
        assertFalse(registry.contains("gameMode.useItem"),
                "Client GUI prediction must not emit a second vanilla interaction packet.");
    }

    @Test
    void leftClickEventRunsOnceBeforeRtsMiningAndCanConsumeTheStart() throws Exception {
        String bridge = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsNativeLeftClickBridge.java"));
        String handler = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/handler/RtsMiningHandlers.java"));

        assertTrue(bridge.contains("!payload.start()")
                        && bridge.contains("usesRemoteSelectedTool(payload)"),
                "Only the initial click with a real hotbar item may enter the native event bridge.");
        assertTrue(bridge.contains("CommonHooks.onLeftClickBlock(")
                        && bridge.contains("START_DESTROY_BLOCK"),
                "RTS left click must expose NeoForge's canonical block-left-click event.");
        assertTrue(bridge.contains("event.isCanceled() || event.getUseItem().isFalse()")
                        && bridge.contains("canAttackBlock("),
                "Third-party cancellation and vanilla item mining vetoes must stop RTS mining.");

        int bridgeCall = handler.indexOf("RtsNativeLeftClickBridge.interceptMiningStart");
        int miningCall = handler.indexOf("ServiceRegistry.getInstance().mining().mine");
        assertTrue(bridgeCall >= 0 && miningCall > bridgeCall,
                "Native item arbitration must finish before the RTS mining state machine starts.");
    }

    @Test
    void miningPayloadCarriesModifierHitAndRayContextForThirdPartyItems() throws Exception {
        String payload = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/C2SRtsMinePayload.java"));
        String input = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/input/CameraInputHandler.java"));

        assertTrue(payload.contains("boolean shiftDown")
                        && payload.contains("double hitX")
                        && payload.contains("double rayOriginX")
                        && payload.contains("double rayDirX"),
                "The server needs the real RTS modifier, hit point, and ray direction.");
        assertTrue(input.contains("Screen.hasShiftDown()")
                        && input.contains("screen.currentRayOrigin()")
                        && input.contains("screen.computeCursorRayDirection()"),
                "The click edge must snapshot all native-item context instead of reconstructing it later.");
    }
}
