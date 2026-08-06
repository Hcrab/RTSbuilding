package com.rtsbuilding.rtsbuilding.server.service.crafting;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCraftTerminalShiftDepositContractTest {
    @Test
    void playerInventoryQuickMoveDepositsWithoutUsingCraftGrid() throws IOException {
        String menu = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/menu/RtsCraftTerminalMenu.java"));
        int methodStart = menu.indexOf(
                "public void clicked(int slotId, int button, ClickType clickType, Player player)");
        int nextMethod = menu.indexOf("\n    private ", methodStart + 20);
        String method = menu.substring(methodStart, nextMethod);

        assertTrue(method.contains("button == 0"));
        assertTrue(method.contains("clickType == ClickType.QUICK_MOVE"));
        assertTrue(method.contains("depositCraftTerminalPlayerSlot(serverPlayer, slotId)"));
        assertTrue(method.indexOf("depositCraftTerminalPlayerSlot")
                < method.indexOf("super.clicked(slotId, button, clickType, player)"));
    }

    @Test
    void depositServicePreservesUninsertedRemainderInTheOriginalSlot() throws IOException {
        String integration = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/transfer/RtsTransferPlayerIntegration.java"));
        int methodStart = integration.indexOf("public static boolean depositCraftTerminalPlayerSlot(");
        int nextMethod = integration.indexOf("\n    public static ", methodStart + 20);
        String method = integration.substring(methodStart, nextMethod);

        assertTrue(method.contains("storeToLinkedOnlyPreferExisting(insertHandlers, source)"));
        assertTrue(method.contains("source.shrink(inserted)"));
        assertTrue(method.contains("slot.setByPlayer(source.isEmpty() ? ItemStack.EMPTY : source)"));
        assertFalse(method.contains("storeToLinkedWithFallback"));
        assertFalse(method.contains("player.drop("));
    }
}
