package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningTaskSubmissionIdentityContractTest {

    @Test
    void newMiningOperationDoesNotReuseHistoricalWorkflowIdAsTaskIdentity() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/task/RtsTaskEngine.java"));
        String method = methodBody(source, "private boolean submitMiningState(");

        assertTrue(method.contains("SubmissionId.create()"),
                "玩家新发起的挖掘必须使用新 submission，避免撞上旧终态回执");
        assertFalse(method.contains("SubmissionId.fromLegacy("),
                "活动 workflowEntryId 不能冒充跨存档稳定的挖掘 submission");
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
