package com.rtsbuilding.rtsbuilding.compat.jei;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsJeiTransferRoutingContractTest {
    @Test
    void vanillaCraftingMenusDelegateBackToJeiTransferChecks() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/jei/RtsCraftTerminalJeiTransferHandler.java"));
        String body = methodBody(source,
                "public IRecipeTransferError transferRecipe(CraftingMenu container, RecipeHolder<CraftingRecipe> recipe,");

        int rtsScreenGuard = body.indexOf("if (!isRtsCraftTerminalScreen(container))");
        int delegateCall = body.indexOf("this.vanillaDelegate.transferRecipe", rtsScreenGuard);
        int customPacket = body.indexOf("new C2SRtsJeiTransferPayload", delegateCall);

        assertTrue(rtsScreenGuard >= 0,
                "普通工作台不能被 RTS handler 直接宣称可转入，必须先区分 RTS 终端屏幕。");
        assertTrue(delegateCall > rtsScreenGuard,
                "非 RTS 终端应委托 JEI 原生 transfer handler，这样缺材料时加号会正确禁用。");
        assertTrue(customPacket > delegateCall,
                "RTS 自己的转入包只能在确认当前屏幕是 RTS 合成终端后发送。");
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
