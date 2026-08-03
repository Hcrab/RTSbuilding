package com.rtsbuilding.rtsbuilding.server.service.mining;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FarMiningStorageSmokeContractTest {
    @Test
    void 整合包探针必须经过真实挖掘事件缓冲和链接存储路径() throws IOException {
        String command = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsFarMiningStorageSmokeCommand.java"));
        assertTrue(command.contains("TARGET_DISTANCE = 120"));
        assertTrue(command.contains("session.sessionFlags.autoStoreMinedDrops = false"));
        assertTrue(command.contains("RtsMiningStateMachine.destroyMinedBlock"));
        assertTrue(command.contains("RtsMiningDropCapture.capture(player, session, targetPos"));
        assertTrue(command.contains("new EntityItem"));
        assertTrue(command.contains("direct EntityItem drop bypassed the exact capture hook"));
        assertTrue(command.contains("RtsDropAbsorber.drainDropBuffer"));
        assertTrue(command.contains("RtsLinkedCapabilities.findLinkedItemHandler"));
        assertTrue(command.contains("linked chest did not receive the remote drop"));
        assertTrue(command.contains("far-drop safety mutated the player's auto-store setting"));
        assertTrue(command.contains("forcedAutoStore=true"));

        String matrix = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsGuiCompatMatrixProbe.java"));
        assertTrue(matrix.contains("/rtsbuilding_far_mining_storage_smoke"));
        assertTrue(matrix.contains("FAR_MINING_STORAGE_REPORT"));

        String matrixBuild = Files.readString(Path.of("gradle/client-smoke.gradle"));
        assertTrue(matrixBuild.contains("mmToolProgressionCore(rfg.deobf"));
        assertTrue(matrixBuild.contains("normalizeMmToolProgression"));
        assertTrue(matrixBuild.contains("Tool Progression 内嵌核心"));
    }
}
