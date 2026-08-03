package com.rtsbuilding.rtsbuilding.client.input;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定容器叠层输入的硬门禁，防止事件注册、资格策略和路由职责再次合并成千行大类。
 */
class RtsClientInputOwnershipContractTest {
    private static final Path INPUT_DIR = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/client/input");

    @Test
    void gateAndOwnersStayBelowHardLineLimits() throws IOException {
        assertLineLimit("RtsClientInputGate.java", 700);
        assertLineLimit("RtsClientInputEvents1122.java", 120);
        assertLineLimit("RtsRawGuiInputAdapter.java", 140);
        assertLineLimit("RtsClientPointerRouter.java", 500);
        assertLineLimit("RtsClientInputRouter.java", 500);
        assertLineLimit("RtsClientInputPolicy.java", 500);
    }

    @Test
    void eventGateOnlyDelegatesInputInsteadOfKeepingDuplicateRoutes() throws IOException {
        String gate = source("RtsClientInputEvents1122.java");
        String rawAdapter = source("RtsRawGuiInputAdapter.java");

        assertTrue(gate.contains("RtsRawGuiInputAdapter.routeMouse(screen, source)"));
        assertTrue(gate.contains("RtsRawGuiInputAdapter.routeKeyboard(screen, source)"));
        assertTrue(rawAdapter.contains("RtsClientPointerRouter.onScreenMousePressed(pointer);"));
        assertTrue(rawAdapter.contains("RtsClientPointerRouter.onScreenMouseDragged(pointer);"));
        assertTrue(rawAdapter.contains("RtsClientPointerRouter.onScreenMouseReleased(pointer);"));
        assertTrue(rawAdapter.contains("RtsClientPointerRouter.onScreenMouseScrolled(pointer);"));
        assertTrue(rawAdapter.contains("RtsClientInputRouter.onScreenKeyPressed("));
        assertTrue(gate.contains("RtsClientInputRouter.onScreenClosing(previous);"));

        assertFalse(gate.contains("tryPickupFromOverlay("));
        assertFalse(gate.contains("tryContinueShiftImportDrag("));
        assertFalse(gate.contains("appendSearchText("));
        assertFalse(gate.contains("NetworkHandler.sendToServer("));
    }

    @Test
    void routersUseOneEligibilityPolicyAndDoNotRegisterEvents() throws IOException {
        String pointer = source("RtsClientPointerRouter.java");
        String keyboard = source("RtsClientInputRouter.java");
        String policy = source("RtsClientInputPolicy.java");

        assertTrue(pointer.contains("RtsClientInputPolicy.isOverlayContainer("));
        assertTrue(pointer.contains("RtsClientInputPolicy.canHandleOverlayInput("));
        assertTrue(keyboard.contains("RtsClientInputPolicy.canHandleOverlayInput("));
        assertFalse(pointer.contains("@SubscribeEvent"));
        assertFalse(keyboard.contains("@SubscribeEvent"));

        assertTrue(policy.contains("screen instanceof GuiContainer"));
        assertTrue(policy.contains("!(screen instanceof BuilderScreen)"));
        assertTrue(policy.contains("!(screen instanceof RtsCraftTerminalScreen)"));
        assertTrue(policy.contains("ClientRtsController.get().canUseStorageOverlay()"));
        assertTrue(policy.contains("RtsClientUiStateStore.isContainerOverlayEnabled()"));
    }

    private static String source(String fileName) throws IOException {
        return Files.readString(INPUT_DIR.resolve(fileName), StandardCharsets.UTF_8);
    }

    private static void assertLineLimit(String fileName, int maxLines) throws IOException {
        long lines;
        try (var stream = Files.lines(INPUT_DIR.resolve(fileName), StandardCharsets.UTF_8)) {
            lines = stream.count();
        }
        assertTrue(lines <= maxLines,
                fileName + " 超过硬门禁：" + lines + " > " + maxLines);
    }
}
