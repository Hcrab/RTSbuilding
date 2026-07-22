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
        String topBarAdapter = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/topbar/TopBarUiAdapter.java");

        String selection = methodBody(screen, "private void selectModeFromWheel(BuilderMode mode)");
        assertTrue(selection.contains("this.controller.setMode(mode)"),
                "轮盘必须提交到统一 BuilderMode，不能维护独立显示状态");
        assertTrue(topBar.contains("TopBarUiAdapter.snapshot(screen, controller)"));
        assertTrue(topBarAdapter.contains("mode(controller.getMode())"));
        assertTrue(topBarAdapter.contains("case INTERACT -> TopBarUiState.Mode.INTERACT"));
        assertTrue(topBarAdapter.contains("case LINK_STORAGE -> TopBarUiState.Mode.LINK_STORAGE"));
        assertTrue(topBarAdapter.contains("case FUNNEL -> TopBarUiState.Mode.FUNNEL"));
        assertTrue(topBarAdapter.contains("case ROTATE -> TopBarUiState.Mode.ROTATE"));
        assertTrue(topBarAdapter.contains("controller.getMode()"),
                "顶部栏选中样式必须直接读取统一 BuilderMode");
    }

    @Test
    void altWheelUsesOneStableTickEdgeInsteadOfThreeCompetingInputPaths() throws IOException {
        String screen = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String altState = methodBody(screen, "private void updateModeWheelAltState()");
        String tick = methodBody(screen, "public void tick()");
        String keyPressed = methodBody(
                screen, "public boolean keyPressed(int keyCode, int scanCode, int modifiers)");
        String render = methodBody(
                screen, "public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)");

        assertTrue(altState.contains("this.cameraInput.cancelPointerGestures()"),
                "轮盘打开时必须取消此前未完成的鼠标手势");
        assertTrue(tick.contains("updateModeWheelAltState()"),
                "Alt 轮盘只从客户端 tick 读取稳定的物理按键边沿");
        assertTrue(!keyPressed.contains("openModeWheelAtCursor")
                        && !keyPressed.contains("isAltKey(keyCode)"),
                "不能在 keyPressed 抢先打开，否则下一帧 GLFW 状态可能立即把轮盘关闭");
        assertTrue(!render.contains("updateModeWheelAltState()"),
                "渲染帧不得再次轮询 Alt，避免输入状态被一帧处理多次");
    }

    @Test
    void linkAndRotateActionsKeepTheRightDragCameraArbitration() throws IOException {
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
                "世界右键必须先进入点击/拖动仲裁");
        assertTrue(!mouseDown.contains("this.controller.rotateBlockStep("),
                "旋转箭头只响应左键，不能在鼠标按下时抢走右键拖动");
        assertTrue(!mouseDown.contains("this.controller.linkStorage("),
                "关联模式不能在鼠标按下时抢走右键拖动");
        assertTrue(rightDrag.contains("if (this.rightDragDistance <= 1.5D)"));
        assertTrue(rightDrag.contains("queueRotateDrag(this.pendingRightDragX, this.pendingRightDragY)"));
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

    @Test
    void funnelUsesRightMouseAndFAsIndependentHoldSources() throws IOException {
        String screen = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String mouseDown = methodBody(
                screen,
                "private boolean handleWorldClickActions(double mouseX, double mouseY, int button)");
        String mouseUp = methodBody(
                screen,
                "public boolean mouseReleased(double mouseX, double mouseY, int button)");
        String sync = methodBody(screen, "private void syncFunnelHoldState()");

        assertTrue(mouseDown.contains("beginFunnelMouseHold(button)"));
        assertTrue(mouseDown.indexOf("beginFunnelMouseHold(button)")
                        < mouseDown.indexOf("this.cameraInput.beginRightPress("));
        assertTrue(mouseUp.contains("endFunnelMouseHold(button)"));
        assertTrue(sync.contains("this.funnelHotkeyHeld || this.funnelMouseHoldButton >= 0"));
        assertTrue(!screen.contains("funnelClickPulseTicks"));
    }

    @Test
    void placedBlockRotationUsesWorldArcsAndSubmitsOneStepIntent() throws IOException {
        String screen = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String handles = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/mode/PlacedBlockRotationHandles.java");
        String leftClick = methodBody(
                screen,
                "private boolean handleLeftClickInteractions(double mouseX, double mouseY, int button)");

        assertTrue(screen.contains("private final PlacedBlockRotationHandles rotationHandles"));
        assertTrue(leftClick.contains("this.rotationHandles.hitGesture("));
        assertTrue(leftClick.contains("this.controller.rotateBlockStep("));
        assertTrue(leftClick.contains("this.rotationHandles.select("));
        assertTrue(handles.contains("availableArcs(state, pos, cameraForward)"));
        assertTrue(handles.contains("PlacedBlockRotationStep.supports("),
                "客户端只显示共享增量旋转器真正支持的小圆弧");
        assertTrue(!screen.contains("openPlacedBlockRotationWheel("),
                "世界中已有方块的旋转不再打开屏幕空间轮盘");
    }

    @Test
    void placedBlockRotationHandlesRenderInWorldWithoutBlockingCameraDrag() throws IOException {
        String renderer = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/selection/PlacedBlockRotationHandleRenderer.java");
        String cameraInput = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/input/CameraInputHandler.java");

        assertTrue(renderer.contains("RenderingUtil.quad("));
        assertTrue(renderer.contains("renderArc("));
        assertTrue(renderer.contains("screen.computeCursorRayDirection()"));
        assertTrue(cameraInput.contains("queueRotateDrag("),
                "旋转模式中的箭头不能抢走原有右键拖动镜头");
    }

    @Test
    void rotationKeyboardTrackRunsAfterCameraAndSelectionPriority() throws IOException {
        String screen = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String worldKeys = methodBody(
                screen,
                "private boolean handleWorldInteractionKeys(int keyCode, int scanCode, int modifiers)");
        String keyPressed = methodBody(
                screen, "public boolean keyPressed(int keyCode, int scanCode, int modifiers)");

        assertTrue(worldKeys.indexOf("this.cameraInput.updateCameraVerticalHeldState(")
                        < worldKeys.indexOf("handlePlacedBlockRotationKey(keyCode)"),
                "相机升降绑定必须先于旋转方向键");
        assertTrue(keyPressed.indexOf("handleSelectionBoxKeys(")
                        < keyPressed.indexOf("handleWorldInteractionKeys("),
                "已有选择框微调必须先于世界方块旋转");
        assertTrue(screen.contains("PlacedBlockRotationGesture.fromKey(keyCode)"));
    }

    @Test
    void hoveredPinBindingGetsPriorityOverWorldKeyConflicts() throws IOException {
        String screen = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String keyPressed = methodBody(
                screen, "public boolean keyPressed(int keyCode, int scanCode, int modifiers)");

        assertTrue(keyPressed.indexOf("handleToolSlotKeys(")
                        < keyPressed.indexOf("handleWorldInteractionKeys("),
                "悬浮 Pin 格时的自定义绑定键必须先于相机/世界按键，避免改绑成 A 后被移动输入吞掉");
    }

    @Test
    void altModeWheelUsesModernVectorNodesAndProgressiveOpening() throws IOException {
        String wheel = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/mode/BuilderModeWheel.java");
        String vectorRenderer = source(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/util/RtsGuiVectorRenderer.java");

        assertTrue(wheel.contains("OPEN_DURATION_MS = 175L"));
        assertTrue(wheel.contains("animationProgress("));
        assertTrue(wheel.contains("hoverProgress"));
        assertTrue(wheel.contains("RtsGuiVectorRenderer.drawRing("));
        assertTrue(wheel.contains("RtsGuiVectorRenderer.fillDisc("));
        assertTrue(!wheel.contains("private static void fillCircle("));
        assertTrue(vectorRenderer.contains("SUBPIXEL_SCALE = 2"));
        assertTrue(vectorRenderer.contains("multiplyAlpha(color, 0.24F)"));
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
