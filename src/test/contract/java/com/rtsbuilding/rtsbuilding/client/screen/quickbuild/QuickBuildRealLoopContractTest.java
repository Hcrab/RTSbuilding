package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证三项快捷操作没有退化成仅客户端预览或旁路工作流。 */
class QuickBuildRealLoopContractTest {
    @Test
    void convenienceDestroyReusesAreaDestroyAndExistingSkeletonVisual() throws IOException {
        String controller = source("client/screen/quickbuild/QuickBuildConvenienceController.java");
        String renderer = source("client/rendering/builder/ShapeGhostRenderer.java");

        assertTrue(controller.contains("confirmShapeAreaDestroy(plan.targets()"));
        assertTrue(controller.contains("new ShapeDataRecords.GhostPreview(plan.targets(), true, true"));
        assertTrue(renderer.contains("MergedSkeletonRenderer.renderConfirmedDestroyWorkArea"));
        assertFalse(controller.contains("setBlockState("),
                "快捷破坏不能绕过既有 AREA_DESTROY 直接改客户端世界");
    }

    @Test
    void smartFillUsesTwoClickClientIntentAndServerReplan() throws IOException {
        String input = source("client/screen/input/CameraInputHandler.java");
        String clientSession = source("client/screen/quickbuild/SmartFillClientSession.java");
        String server = source("server/service/placement/RtsSmartFillService.java");
        String payload = source("network/builder/C2SRtsConfirmSmartFillPayload.java");

        assertTrue(input.contains("isQuickBuildSmartFillMode()"));
        assertTrue(input.contains("handleQuickBuildSmartFillClick()"));
        assertTrue(clientSession.contains("submitOrAnchor"));
        assertTrue(clientSession.contains("this.anchored"));
        assertTrue(server.contains("SmartFillPlanner.plan("));
        assertTrue(server.contains("RtsClaimProtectionService.canPlaceBlock"));
        assertTrue(server.contains("RtsPlacementService.enqueuePlaceBatch"));
        assertFalse(payload.contains("List<BlockPos>"),
                "智能填坑网络意图不能携带客户端规划出的坐标列表");
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding/" + relative));
    }
}
