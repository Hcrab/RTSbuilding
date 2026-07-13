package com.rtsbuilding.rtsbuilding.server.service.mining;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsAreaDestroyInstantStartContractTest {
    @Test
    void areaDestroySkipsSeedMiningWhileOtherBatchMiningKeepsIt() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsUltimineProcessor.java"));

        String ultimine = methodBody(source, "public static PipelineBatchStartResult startUltimineFromPipeline");
        String areaMine = methodBody(source, "public static PipelineBatchStartResult areaMineFromPipeline");
        String areaDestroy = methodBody(source, "public static PipelineBatchStartResult areaDestroyFromPipeline");
        String batchStart = methodBody(source, "private static PipelineBatchStartResult beginPipelineBatch");

        assertTrue(ultimine.contains("BatchStartMode.WAIT_FOR_SEED"),
                "连锁挖掘仍应等待玩家挖完种子方块");
        assertTrue(areaMine.contains("BatchStartMode.WAIT_FOR_SEED"),
                "旧体积挖掘的首块进度语义不应被范围破坏修改");
        assertTrue(areaDestroy.contains("BatchStartMode.IMMEDIATE"),
                "范围破坏确认后必须立即进入批量处理");
        assertTrue(batchStart.contains("startMode == BatchStartMode.WAIT_FOR_SEED")
                        && batchStart.contains("RtsMiningStateMachine.beginRemoteMining"),
                "只有显式等待种子方块的批量模式才能启动首块挖掘进度");
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
