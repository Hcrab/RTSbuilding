package com.rtsbuilding.rtsbuilding.compat.jei;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsJeiTransferRoutingContractTest {
    @Test
    void terminalUsesItsOwnMenuTypeInsteadOfInterceptingVanillaCrafting() throws IOException {
        String handler = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/jei/RtsCraftTerminalJeiTransferHandler.java"));
        String menus = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/common/RtsMenuTypes.java"));

        assertTrue(handler.contains("IRecipeTransferHandler<RtsCraftTerminalMenu, CraftingRecipe>"));
        assertTrue(handler.contains("Optional.of(RtsMenuTypes.RTS_CRAFT_TERMINAL.get())"));
        assertTrue(menus.contains("IForgeMenuType.create"));
        assertFalse(handler.contains("MenuType.CRAFTING"));
        assertFalse(handler.contains("vanillaDelegate"));
    }

    @Test
    void clientNoLongerRewrapsVanillaCraftingScreen() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsController.java"));
        assertFalse(controller.contains("new RtsCraftTerminalScreen"));
        assertFalse(controller.contains("shouldUseRtsCraftTerminalScreen"));
    }
}
