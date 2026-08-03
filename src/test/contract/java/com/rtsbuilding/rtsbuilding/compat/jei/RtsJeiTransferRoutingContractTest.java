package com.rtsbuilding.rtsbuilding.compat.jei;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsJeiTransferRoutingContractTest {
    @Test
    void terminalSurvivesOpeningTheJeiRecipeScreen() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/jei/RtsCraftTerminalJeiTransferHandler.java"));
        String context = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/jei/RtsJeiScreenContext.java"));
        String body = methodBody(source,
                "public IRecipeTransferError transferRecipe(");

        int rtsScreenGuard = body.indexOf("if (!RtsJeiScreenContext.isRtsCraftTerminal(container))");
        int overlayDelegate = body.indexOf("transferWithOverlay", rtsScreenGuard);
        int customPacket = body.indexOf("new C2SRtsJeiTransferPayload", overlayDelegate);

        assertTrue(rtsScreenGuard >= 0,
                "普通工作台不能被 RTS handler 直接宣称可转入，必须先区分 RTS 终端屏幕。");
        assertTrue(overlayDelegate > rtsScreenGuard,
                "非 RTS 终端仍需经过 overlay 感知委托，才能把链接存储纳入材料来源。");
        assertTrue(customPacket > overlayDelegate,
                "RTS 自己的转入包只能在确认当前屏幕是 RTS 合成终端后发送。");
        assertTrue(context.contains("getParentScreen")
                        && context.contains("parent.inventorySlots == container"),
                "HEI 配方页替换 currentScreen 后，必须沿 parentScreen 找回真实 RTS 终端容器。");
    }

    @Test
    void vanillaDelegateUsesCraftingTableSlotLayout() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/jei/RtsCraftTerminalJeiTransferHandler.java"));

        assertTrue(source.contains("JEI_FIRST_INPUT_SLOT = 1"),
                "原版工作台合成输入槽应从菜单 slot 1 开始。");
        assertTrue(source.contains("GRID_SIZE = 9"),
                "原版工作台应暴露 3x3 共 9 个输入槽给 JEI 原生转入。");
        assertTrue(source.contains("ingredients.get(JEI_FIRST_INPUT_SLOT + i)"),
                "JEI 4 必须从 slot 1 起逐一读取九宫格原料原型。");
        assertTrue(source.contains("WORKBENCH_TRANSFER_INFO"),
                "普通工作台必须保留原版 1-9 输入槽与 10+ 背包槽布局供 overlay 转移复用。");
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
