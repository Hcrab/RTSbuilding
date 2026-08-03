package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsGuiCompatMatrixCoordinateContractTest {
    @Test
    void 矩阵必须把同一组绝对坐标同时交给服务端放置和客户端校验() throws IOException {
        String probe = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsGuiCompatMatrixProbe.java"));
        assertTrue(probe.contains("+ \" \" + targetPos.getX() + \" \" + targetPos.getY() + \" \" + targetPos.getZ()"));
        assertTrue(probe.contains("minecraft.world.getBlockState(targetPos)"));
        assertTrue(probe.contains("minecraft.world.getTileEntity(targetPos)"));
        assertTrue(probe.contains("!candidate.tileEntity() || clientTile != null"));

        String command = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsGuiCompatSetupCommand.java"));
        assertTrue(command.contains("args.length == 7"));
        assertTrue(command.contains("BlockPos explicitTarget"));
        assertTrue(command.contains("explicitTarget == null"));
        assertTrue(command.contains("RtsGuiCompatMatrixSync.markSetupComplete"));
        assertTrue(command.contains("RtsGuiCompatMatrixSync.markSetupFailed"));
        assertTrue(command.contains("clearProbeBlock(level, targetPos.up())"));
        assertTrue(command.indexOf("TileEntity previousTile")
                < command.indexOf("level.setBlockToAir(pos)"));
        assertTrue(command.indexOf("level.setBlockToAir(pos)")
                < command.indexOf("previousTile.invalidate()"));
        assertTrue(command.contains("level.removeTileEntity(pos)"));
        assertTrue(command.contains("boolean placed = level.setBlockState(targetPos, desiredState, 3)"));
    }

    @Test
    void 大矩阵可以缩短纯空等但半开窗口仍必须作为失败显式暴露() throws IOException {
        String probe = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsGuiCompatMatrixProbe.java"));
        assertTrue(probe.contains("rtsbuilding.guiCompatMatrixSetupWaitTicks"));
        assertTrue(probe.contains("rtsbuilding.guiCompatMatrixOpenTimeoutTicks"));
        assertTrue(probe.contains("rtsbuilding.guiCompatMatrixCloseWaitTicks"));
        assertTrue(probe.contains("sawMenu ? \"SCREEN_MISSING\" : \"NO_GUI_OR_PREREQUISITE\""));
        assertTrue(probe.contains("RtsGuiCompatMatrixSync.isSetupAcknowledgedAfter"));
        assertTrue(probe.contains("RtsGuiCompatMatrixSync.setupFailureAfter"));
        assertTrue(probe.contains("\"SETUP_REJECTED\""));
        assertTrue(probe.contains("RtsGuiCompatMatrixSync.isInteractionAcknowledgedAfter"));
        assertTrue(probe.contains("RtsGuiCompatMatrixSync.interactionFailureAfter"));
        assertTrue(probe.contains("\"INTERACTION_REJECTED\""));
        assertTrue(probe.contains("!sawMenu && !sawExternalScreen"));
        assertTrue(probe.contains("sawMenu && !sawExternalScreen"));
        assertTrue(probe.contains("rtsbuilding.guiCompatMatrixMenuScreenTimeoutTicks"));
        assertTrue(probe.contains("rtsbuilding.guiCompatMatrixGuiStabilityTimeoutTicks"));

        String handler = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/builder/handler/RtsPlacementActionHandlers1122.java"));
        assertTrue(handler.contains("RtsGuiCompatMatrixSync.markInteractionProcessed"));
        assertTrue(handler.contains("RtsGuiCompatMatrixSync.markInteractionFailed"));

        String gradle = Files.readString(Path.of("gradle/client-smoke.gradle"));
        assertTrue(gradle.contains("guiCompatMatrixSetupWaitTicks"));
        assertTrue(gradle.contains("guiCompatMatrixOpenTimeoutTicks"));
        assertTrue(gradle.contains("guiCompatMatrixCloseWaitTicks"));
        assertTrue(gradle.contains("boolean focusedRun"));
        assertTrue(gradle.contains("expectedTotal != 14606"));
        assertTrue(gradle.contains("c[3] == expectedTotalText"));
    }
}
