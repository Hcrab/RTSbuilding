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

        String ultimine = methodBody(source, "public static boolean startUltimine");
        String areaMine = methodBody(source, "public static boolean areaMine");
        String areaDestroy = methodBody(source, "public static void areaDestroy");
        String queuedAreaDestroy = methodBody(source, "public static int queueAreaDestroy");

        assertTrue(usesProgressiveMode(ultimine, true),
                "连锁挖掘仍应等待玩家挖完种子方块");
        assertTrue(usesProgressiveMode(areaMine, true),
                "旧体积挖掘的首块进度语义不应被范围破坏修改");
        assertTrue(usesProgressiveMode(areaDestroy, false),
                "范围破坏确认后必须立即进入批量处理");
        assertTrue(usesProgressiveMode(queuedAreaDestroy, false),
                "排队的范围破坏也不能重新落回首块蓄力模式");
    }

    private static boolean usesProgressiveMode(String methodBody, boolean progressive) {
        return methodBody.matches("(?s).*toolProtectionEnabled\\s*,\\s*" + progressive + ".*");
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
