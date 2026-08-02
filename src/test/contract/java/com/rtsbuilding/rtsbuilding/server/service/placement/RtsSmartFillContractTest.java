package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智能填坑跨客户端、网络和服务端边界的源代码契约。
 *
 * <p>纯算法与真实世界行为由单测和 GameTest 覆盖；这里防止后续维护把客户端坐标表重新塞进
 * C2S、绕开服务端重规划，或让锚定取消再次落到挖掘路径之后。</p>
 */
final class RtsSmartFillContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");

    @Test
    void payloadCarriesIntentInsteadOfClientTargetCoordinates() throws IOException {
        String payload = read("network/builder/C2SRtsConfirmSmartFillPayload.java");

        assertTrue(payload.contains("BlockPos clickedPos")
                        && payload.contains("int maxBlocks")
                        && payload.contains("int detectionDiameter"),
                "智能填坑 C2S 必须携带锚点与有界参数");
        assertFalse(payload.contains("List<BlockPos>")
                        || payload.contains("List<SmartFill"),
                "客户端预览目标列表不得进入智能填坑 C2S");
    }

    @Test
    void serverReplansAndChecksAuthorityBeforeSubmittingDurablePlacement() throws IOException {
        String service = read("server/service/placement/RtsSmartFillService.java");
        int plan = service.indexOf("SmartFillPlanner.plan(");
        int claim = service.indexOf("RtsClaimProtectionService.canPlaceBlock");
        int enqueue = service.indexOf("enqueuePlaceBatch(");

        assertTrue(plan >= 0 && claim > plan && enqueue > claim,
                "服务端必须先权威重规划并逐格检查领地，再进入正式放置任务");
        assertTrue(service.contains("RtsLinkedStorageResolver.canAccessWorldTarget")
                        && service.contains("SmartFillCandidateClassifier.classify"),
                "服务端必须复核动作范围、区块状态与当前可替换性");
    }

    @Test
    void leftClickCancellationRunsBeforeWorldMining() throws IOException {
        String owner = read("client/screen/standalone/BuilderScreenPointerActionOwner.java");
        String router = read("client/screen/standalone/BuilderScreenPointerClickRouter.java");
        int left = router.indexOf("handleLeftClickInteractions");
        int world = router.indexOf("handleWorldClickActions");
        int method = owner.indexOf(
                "boolean handleLeftClickInteractions(double mouseX, double mouseY, int button)");
        int methodEnd = owner.indexOf("boolean handleWorldClickActions", method);
        String body = owner.substring(method, methodEnd);

        assertTrue(left >= 0 && world > left,
                "左键专用交互必须早于包含挖掘的世界操作路由");
        assertTrue(body.contains("screen.cancelQuickBuildSmartFillAnchor()"),
                "锚定后的左键必须取消智能填坑而不是开始挖掘");
    }

    @Test
    void topModeButtonsRemainReachableFromConvenienceSubpage() throws IOException {
        String controls = read("client/screen/quickbuild/QuickBuildControlSurface.java");
        int modeHit = controls.indexOf("QuickBuildUiMode mode = layout.modeAt");
        int convenienceEarlyReturn = controls.indexOf("if (state.convenienceMode())", modeHit);

        assertTrue(modeHit >= 0 && convenienceEarlyReturn > modeHit,
                "顶部模式命中必须先于便捷工具子页的提前返回");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
