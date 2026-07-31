package com.rtsbuilding.rtsbuilding.server.service.destruction;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 便捷破坏必须保持声明式协议、服务端权威和既有任务链路。 */
class RtsConvenienceDestroyContractTest {
    @Test
    void payloadCarriesIntentInsteadOfClientCoordinateArray() throws IOException {
        String payload = read("network/builder/C2SRtsConvenienceDestroyPayload.java");
        assertTrue(payload.contains("RtsConvenienceDestroyMode mode"));
        assertTrue(payload.contains("BlockPos anchor"));
        assertTrue(payload.contains("RtsConvenienceDestroySettings settings"));
        assertFalse(payload.contains("List<BlockPos>"),
                "客户端不能把树或区块扫描出的任意坐标表作为权威请求");
    }

    @Test
    void serverReplansBeforeEnteringExistingAreaDestroyPipeline() throws IOException {
        String service = read("server/service/destruction/RtsConvenienceDestroyService.java");
        String planner = read("common/destruction/RtsConvenienceDestroyPlanner.java");
        assertTrue(service.contains("RtsConvenienceDestroyPlanner.plan("));
        assertTrue(service.contains("mining().areaDestroy("),
                "便捷工具必须复用现有 Task Engine、工具、掉落和 Ctrl+Z 链路");
        assertTrue(planner.contains("return rejected(ResultCode.OVER_LIMIT"));
        assertTrue(planner.contains("targets.size() > maxBlocks"));
        assertTrue(planner.contains("!level.hasChunk("),
                "预览与服务端规划都不得为便捷破坏主动加载区块");
    }

    @Test
    void quickBuildPanelDelegatesScanningToDedicatedPlanner() throws IOException {
        String panel = read("client/screen/quickbuild/QuickBuildPanel.java");
        String convenience = read("client/screen/quickbuild/QuickBuildConvenienceController.java");
        assertTrue(panel.contains("QuickBuildConvenienceController"));
        assertTrue(convenience.contains("RtsDestroyPreviewPlanner"));
        assertFalse(panel.contains("ArrayDeque<BlockPos>"));
        assertFalse(panel.contains("BlockTags.LOGS"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }
}
