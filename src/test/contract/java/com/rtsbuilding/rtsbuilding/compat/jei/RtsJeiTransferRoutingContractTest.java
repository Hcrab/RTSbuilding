package com.rtsbuilding.rtsbuilding.compat.jei;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsJeiTransferRoutingContractTest {
    @Test
    void handlerTargetsOnlyDedicatedRtsTerminalMenu() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/jei/RtsCraftTerminalJeiTransferHandler.java"));

        assertTrue(source.contains("IRecipeTransferHandler<RtsCraftTerminalMenu"));
        assertTrue(source.contains("RtsMenuTypes.RTS_CRAFT_TERMINAL.get()"));
        assertFalse(source.contains("MenuType.CRAFTING"),
                "RTS JEI handler 不能再注册到原版工作台菜单类型");
        assertFalse(source.contains("vanillaDelegate"),
                "独立菜单类型不应拦截或代管普通工作台的 JEI 路由");
    }

    @Test
    void menuKeepsVanillaCraftingSlotIndexContract() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/menu/RtsCraftTerminalMenu.java"));
        assertTrue(source.contains("RESULT_SLOT = 0"));
        assertTrue(source.contains("CRAFT_SLOT_START = 1"));
        assertTrue(source.contains("INVENTORY_SLOT_START = 10"));
        assertTrue(source.contains("HOTBAR_SLOT_END = 46"));
        assertTrue(source.contains("new ResultSlot"),
                "配方剩余物、事件和成就必须继续经过原版 ResultSlot");
    }

    @Test
    void alwaysLoadedSearchBridgeDoesNotReferenceJeiTypes() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/jei/RtsJeiSearchBridge.java"));

        assertFalse(source.contains("mezz.jei"),
                "无 JEI 的正式客户端仍会加载终端搜索桥，桥本身不能持有 JEI 类型");
        assertTrue(source.contains("Supplier<String>"));
        assertTrue(source.contains("Consumer<String>"));
    }
}
