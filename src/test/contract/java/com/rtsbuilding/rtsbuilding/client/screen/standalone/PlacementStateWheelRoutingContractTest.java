package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementStateWheelRoutingContractTest {

    @Test
    void placementWheelGetsRBeforeRotateModeAndUsesSeparateAction() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreenComponentState.java"))
                + owner("BuilderScreenPointerActionOwner.java")
                + owner("BuilderScreenPointerGestureOwner.java")
                + owner("BuilderScreenKeyboardActionOwner.java")
                + owner("BuilderScreenModeSessionOwner.java")
                + owner("BuilderScreenPointerClickRouter.java")
                + owner("BuilderScreenKeyPressRouter.java");
        String builder = owner("BuilderScreen.java");
        int placementRoute = source.indexOf("screen.openPlacementStateWheel(screen.currentMouseX(), screen.currentMouseY())");
        int modeRoute = source.indexOf("handleModeKeyPressed(keyCode, scanCode)", placementRoute);
        assertTrue(placementRoute >= 0);
        assertTrue(modeRoute > placementRoute);
        assertTrue(source.contains("PlacedBlockRotationHandles rotationHandles"));
        assertTrue(source.contains("PlacementStateWheel placementStateWheel"));
        assertTrue(source.contains("copyPlacementState(choice.state())"),
                "轮盘必须保存其实际渲染状态的安全属性快照，不能只提交单个属性");
        assertTrue(source.contains("handlePlacementPageClick(mouseX, mouseY)"));
        assertTrue(source.contains("cyclePlacementPage(-1)"));
        assertTrue(source.contains("cyclePlacementPage(1)"));
        assertTrue(source.contains("screen.controller.rotateBlockStep("));
        assertTrue(source.contains("RtsPlacementRayFreeze.freeze("));
        assertTrue(source.contains("GLFW.glfwSetCursorPos("));
        assertTrue(builder.contains("return this.keyboardActionOwner.handleWorldInteractionKeys("));
        assertTrue(builder.contains("return this.keyPressRouter.keyPressed("),
                "生产按键入口必须连接到新的 owner/router 链");

        String picker = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/input/CameraInputHandler.java"));
        String placementService = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/service/BuildPlacementService.java"));
        String preset = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/common/placement/PlacementStatePreset.java"));
        assertTrue(picker.contains("this.controller.copyPlacementState(state);"));
        assertTrue(placementService.contains("private String placementStateItemId = \"\";"));
        assertTrue(placementService.contains("!nextItemId.equals(this.placementStateItemId)"),
                "手持方块先选 R 状态、再从 RTS 列表选择同一物品时，不得把预选状态清空");
        assertTrue(preset.contains("public static String fromBlockState(BlockState state)"));
        assertTrue(preset.contains("state.getBlock() instanceof SlabBlock && !\"double\".equals(valueName)"));

        String wheel = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/mode/PlacementStateWheel.java"));
        assertTrue(wheel.contains("private static final int PLACEMENT_PAGE_SIZE = 8"));
        assertTrue(wheel.contains("PlacementStateCombinationPlan.combinations("),
                "放置轮盘应生成完整状态组合，而不是把每个属性强行画成同心层");
    }

    private static String owner(String file) throws IOException {
        return Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/" + file));
    }
}
