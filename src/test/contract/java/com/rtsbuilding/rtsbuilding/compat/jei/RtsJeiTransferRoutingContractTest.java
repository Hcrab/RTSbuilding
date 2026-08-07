package com.rtsbuilding.rtsbuilding.compat.jei;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsJeiTransferRoutingContractTest {
    @Test
    void dedicatedTerminalHandlerLeavesVanillaCraftingToJei() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/compat/jei/RtsCraftTerminalJeiTransferHandler.java"));

        assertTrue(source.contains("IRecipeTransferHandler<RtsCraftTerminalMenu"));
        assertTrue(source.contains("RtsMenuTypes.RTS_CRAFT_TERMINAL.get()"));
        assertTrue(source.contains("new C2SRtsJeiTransferPayload"));
        assertFalse(source.contains("MenuType.CRAFTING"),
                "RTS handler 不得注册到原版工作台；原版工作台继续走 JEI 自带 handler。");
    }

    @Test
    void dedicatedMenuKeepsVanillaCraftingSlotIndexContract() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/menu/RtsCraftTerminalMenu.java"));

        assertTrue(source.contains("RESULT_SLOT = 0"));
        assertTrue(source.contains("CRAFT_SLOT_START = 1"));
        assertTrue(source.contains("INVENTORY_SLOT_START = 10"),
                "RTS 终端的 3x3 合成槽必须保持原版工作台索引，JEI 原料映射才能一致。");
        assertTrue(source.contains("HOTBAR_SLOT_END = 46"));
        assertTrue(source.contains("new ResultSlot"),
                "配方剩余物、事件和成就必须继续经过原版 ResultSlot。");
    }
}
