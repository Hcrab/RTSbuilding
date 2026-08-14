package com.rtsbuilding.rtsbuilding.server.service.crafting;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCraftTerminalMenuParityContractTest {
    @Test
    void dedicatedMenuOwnsTheUnifiedTerminalSlotCoordinates() throws IOException {
        String menu = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/menu/RtsCraftTerminalMenu.java"));
        assertTrue(menu.contains("extends RecipeBookMenu<CraftingContainer>"));
        assertTrue(menu.contains("super(RtsMenuTypes.RTS_CRAFT_TERMINAL.get(), containerId)"));
        assertTrue(menu.contains("CraftTerminalLayout.CRAFT_GRID_X"));
        assertTrue(menu.contains("CraftTerminalLayout.INVENTORY_Y"));
        assertFalse(menu.contains("extends CraftingMenu"));
    }

    @Test
    void clearAndRefillStayServerAuthoritativeAndLossless() throws IOException {
        String screen = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/RtsCraftTerminalScreen.java"));
        String filler = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/crafting/RtsCraftingGridFiller.java"));
        assertTrue(screen.contains("new C2SRtsClearCraftingGridPayload"));
        assertTrue(filler.contains("refillCraftGridToSnapshotCounts"));
        assertTrue(filler.contains("evictUnexpectedRemainders"));
        assertTrue(filler.contains("if (!remain.isEmpty()) {\n                grid.set(remain);"));
    }
}
