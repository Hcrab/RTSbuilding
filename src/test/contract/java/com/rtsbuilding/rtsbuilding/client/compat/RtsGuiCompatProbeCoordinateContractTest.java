package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsGuiCompatProbeCoordinateContractTest {
    @Test
    void 单例探针必须用绝对坐标连接服务端布置与客户端交互() throws IOException {
        String probe = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsGuiCompatProbe.java"));

        assertTrue(probe.contains("prepareAutoSetupCommand(minecraft)"));
        assertTrue(probe.contains("autoRun.targetPos = targetPos"));
        assertTrue(probe.contains("+ targetPos.getX() + \" \""));
        assertTrue(probe.contains("matchesTargetBlock(minecraft, autoRun.targetPos)"));
        assertTrue(probe.contains("AUTO_SETUP_SYNC_TIMEOUT"));
        assertTrue(probe.contains("\"auto-setup-sync\", \"FAIL\""));
    }
}
