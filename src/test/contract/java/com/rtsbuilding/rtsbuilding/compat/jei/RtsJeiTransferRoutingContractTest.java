package com.rtsbuilding.rtsbuilding.compat.jei;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsJeiTransferRoutingContractTest {
    @Test
    void rtsCraftTerminalHandlerDirectlySendsRtsPacket() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/jei/RtsCraftTerminalJeiTransferHandler.java"));
        String body = methodBody(source,
                "public IRecipeTransferError transferRecipe(RtsCraftTerminalMenu container, RecipeHolder<CraftingRecipe> recipe,");

        int customPacket = body.indexOf("new C2SRtsJeiTransferPayload");

        assertTrue(customPacket >= 0,
                "RTS 合成终端 handler 应直接发送 RTS 专用合成包，不应再有委托原版 JEI 的逻辑。");
        assertTrue(body.indexOf("vanillaDelegate") < 0,
                "工作台劫持已移除，vanillaDelegate 不应存在。");
        assertTrue(body.indexOf("requireScreenCheck") < 0,
                "工作台劫持已移除，requireScreenCheck 逻辑不应存在。");
        assertTrue(body.indexOf("isRtsCraftTerminalScreen") < 0,
                "工作台劫持已移除，屏幕检查逻辑不应存在。");
    }

    @Test
    void vanillaDelegateUsesCraftingTableSlotLayout() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/jei/RtsCraftTerminalJeiTransferHandler.java"));

        assertTrue(source.contains("CRAFT_GRID_SLOT_START = 1"),
                "原版工作台合成输入槽应从菜单 slot 1 开始。");
        assertTrue(source.contains("CRAFT_GRID_SLOT_COUNT = 9"),
                "原版工作台应暴露 3x3 共 9 个输入槽给 JEI 原生转入。");
        assertTrue(source.contains("INVENTORY_SLOT_START = 10"),
                "玩家背包槽在 CraftingMenu 中应从 slot 10 开始。");
        assertTrue(source.contains("INVENTORY_SLOT_COUNT = 36"),
                "玩家背包和快捷栏共 36 个槽，供 JEI 原生材料检查使用。");
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "method body not found: " + signatureStart);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}
