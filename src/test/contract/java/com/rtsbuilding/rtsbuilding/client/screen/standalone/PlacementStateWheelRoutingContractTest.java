package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementStateWheelRoutingContractTest {

    @Test
    void placementWheelGetsRBeforeRotateModeAndUsesSeparateAction() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        int placementRoute = source.indexOf("openPlacementStateWheel(currentMouseX(), currentMouseY())");
        int modeRoute = source.indexOf("handleModeKeyPressed(keyCode, scanCode)", placementRoute);
        assertTrue(placementRoute >= 0);
        assertTrue(modeRoute > placementRoute);
        assertTrue(source.contains("private final PlacedBlockRotationHandles rotationHandles"));
        assertTrue(source.contains("private final PlacementStateWheel placementStateWheel"));
        assertTrue(source.contains("copyPlacementState(choice.state())"),
                "轮盘必须保存其实际渲染状态的安全属性快照，不能只提交单个属性");
        assertTrue(source.contains("handlePlacementPageClick(mouseX, mouseY)"));
        assertTrue(source.contains("cyclePlacementPage(-1)"));
        assertTrue(source.contains("cyclePlacementPage(1)"));
        assertTrue(source.contains("this.controller.rotateBlockStep("));
        assertTrue(source.contains("RtsPlacementRayFreeze.freeze("));
        assertTrue(source.contains("GLFW.glfwSetCursorPos("));

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
}
