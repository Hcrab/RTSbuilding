package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsModeWheelRoutingContractTest {
    @Test
    void wheelSelectionUpdatesTheStateReadByTopBarActiveStyle() throws IOException {
        String screen = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String topBar = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/topbar/TopBarPanel.java");

        String selection = methodBody(screen, "private void selectModeFromWheel(BuilderMode mode)");
        String opening = methodBody(screen, "private void updateModeWheelAltState()");
        assertTrue(selection.contains("this.controller.setMode(mode)"),
                "轮盘必须提交到统一 BuilderMode，不能维护独立显示状态");
        assertTrue(opening.contains("this.cameraInput.cancelPointerGestures()"),
                "轮盘打开时必须取消此前未完成的鼠标手势");
        assertTrue(topBar.contains("topActionForMode() == TopAction.INTERACT"));
        assertTrue(topBar.contains("topActionForMode() == TopAction.LINK"));
        assertTrue(topBar.contains("topActionForMode() == TopAction.FUNNEL"));
        assertTrue(topBar.contains("topActionForMode() == TopAction.ROTATE"));
        assertTrue(topBar.contains("switch (this.controller.getMode())"),
                "顶部栏已选中样式必须直接读取统一 BuilderMode");
    }

    @Test
    void linkAndRotateActionsWaitForNoDragMouseRelease() throws IOException {
        String screen = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String cameraInput = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/input/CameraInputHandler.java");
        String mouseDown = methodBody(
                screen,
                "private boolean handleWorldClickActions(double mouseX, double mouseY, int button)");
        String rightDrag = methodBody(
                cameraInput,
                "public boolean handleRightDrag(double mouseX, double mouseY, int button, double dragX, double dragY)");

        assertTrue(mouseDown.contains("this.cameraInput.beginRightPress("),
                "世界右键必须先进入点击/拖动分流");
        assertTrue(!mouseDown.contains("this.controller.rotateBlock("),
                "旋转模式不能在鼠标按下时抢走相机拖动");
        assertTrue(!mouseDown.contains("this.controller.linkStorage("),
                "关联模式不能在鼠标按下时抢走相机拖动");
        assertTrue(rightDrag.contains("if (this.rightDragDistance <= 1.5D)"),
                "跨过阈值前必须缓存右键微拖，不能同时转镜头并执行模式点击");
        assertTrue(rightDrag.contains("queueRotateDrag(this.pendingRightDragX, this.pendingRightDragY)"),
                "跨过阈值后必须补交累计位移，避免镜头跳过第一段拖动");
    }

    @Test
    void closingDuringTemporaryFunnelRestoresPreviousMode() throws IOException {
        String screen = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String onClose = methodBody(screen, "public void onClose()");

        int restore = onClose.indexOf("this.controller.setMode(this.modeBeforeFunnelHotkey)");
        int clearTemporary = onClose.indexOf("this.funnelHotkeyTemporaryMode = false");
        assertTrue(restore >= 0 && clearTemporary > restore,
                "按住 F 关闭界面时，必须先恢复旧模式再清除临时漏斗标记");
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
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
