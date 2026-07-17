package com.rtsbuilding.rtsbuilding.client.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固化 26.1 UI 滩头的输入与控件边界，避免后续修按钮时恢复广播式路由。
 */
class RtsUiArchitectureContractTest {
    private static final Path CLIENT_ROOT =
            Path.of("src/main/java/com/rtsbuilding/rtsbuilding/client");

    @Test
    void floatingLayerOwnsCaptureAndNeverEndsMinecraftSharedBuffer() throws IOException {
        String source = read("screen/panel/RtsFloatingWindowLayer.java");

        assertTrue(source.contains("pointerCapture.release(button)"),
                "release 必须只返回给 press 时的捕获窗口");
        assertTrue(source.contains("RtsInputResult.BLOCK_WORLD"),
                "窗口内滚轮必须阻断世界/镜头");
        assertFalse(source.contains("endBatch("),
                "浮窗层不得结束 Minecraft 的共享渲染批次");
    }

    @Test
    void screenLifecycleCancelsOutstandingPointerCapture() throws IOException {
        String source = read("screen/standalone/BuilderScreen.java");

        assertTrue(count(source, "floatingWindowLayer.cancelPointerCapture()") >= 2,
                "关闭和移除屏幕都必须清空指针捕获");
    }

    @Test
    void topBarAndBlueprintFooterUseExplicitControlSemantics() throws IOException {
        String topBar = read("screen/topbar/TopBarTypes.java");
        String blueprint = read("screen/blueprint/BlueprintWindowPanel.java");

        assertTrue(topBar.contains("RtsControlState state"),
                "顶栏布局必须携带统一控件状态，而不是复用 active 布尔值");
        assertTrue(topBar.contains("RtsControlRole.MODE"));
        assertTrue(topBar.contains("RtsControlRole.TOGGLE"));
        assertTrue(blueprint.contains("BlueprintFooterView.capture"));
        assertTrue(blueprint.contains("BlueprintFooterView.placement"));
        assertTrue(blueprint.contains("screen.rtsbuilding.blueprints.cancel_preview"),
                "取消预览不得继续显示成含糊的“清除”");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(CLIENT_ROOT.resolve(relative));
    }

    private static int count(String source, String marker) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(marker, offset)) >= 0) {
            count++;
            offset += marker.length();
        }
        return count;
    }
}
