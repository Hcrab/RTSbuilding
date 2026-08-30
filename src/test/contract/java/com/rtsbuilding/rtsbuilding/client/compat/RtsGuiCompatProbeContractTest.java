package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsGuiCompatProbeContractTest {
    @Test
    void vanillaDriverUsesRealContainerPacketsInsteadOfMutatingSlots() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsGuiCompatVanillaInteractionDriver.java"));

        assertTrue(source.contains("handleInventoryMouseClick"));
        assertTrue(source.contains("handleInventoryButtonClick"));
        assertTrue(source.contains("Enchanting inputs synchronized; this pack exposed no selectable vanilla enchant option"));
        assertTrue(source.contains("Smithing inputs synchronized; this pack exposed no vanilla netherite output"));
        assertTrue(source.contains("Grindstone input synchronized; this pack exposed no vanilla disenchant output"));
        assertTrue(!source.contains(".setItem("),
                "客户端深交互驱动不得直接改槽位伪造服务端成功。");
    }

    @Test
    void batchProbePersistsEveryCompletedCaseAndHasCrashSafeFallback() throws Exception {
        String probe = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsGuiCompatProbe.java"));
        String report = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsGuiCompatProbeReport.java"));

        assertTrue(probe.contains("REPORT.markCompleted"));
        assertTrue(probe.contains("INTERACTION_FAIL"));
        assertTrue(probe.contains("trustedServerSetup"));
        assertTrue(probe.contains("clientChunkLoaded"));
        assertTrue(probe.contains("minecraft.player.respawn()"));
        assertTrue(probe.contains("RtsGuiCompatWorldStabilityGate"));
        assertTrue(probe.contains("auto-world-stable"));
        assertTrue(probe.contains("autoRun.stage = AutoStage.WAIT_WORLD"));
        assertTrue(probe.contains("minecraft.screen instanceof AbstractContainerScreen<?>"));
        assertTrue(probe.contains("sendInteractBlockWithToolSlot"));
        assertTrue(probe.contains("currentCase.setupWaitTicks()"));
        assertTrue(probe.contains("applyCaseHitGeometry"));
        assertTrue(probe.contains("guiCase.hitOffsetZ()"));
        assertTrue(report.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(report.contains("baselineSha"));
        assertTrue(report.contains("manifestHash"));
    }
}
