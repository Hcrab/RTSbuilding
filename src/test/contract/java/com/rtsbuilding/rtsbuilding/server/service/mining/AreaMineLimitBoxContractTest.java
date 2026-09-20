package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.common.mining.MiningSelectionBounds;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 验证确认后的区域整体接纳，不再维护旧的服务器静默裁剪契约。 */
class AreaMineLimitBoxContractTest {
    @Test
    void defaultVolumeAllowsCubeAndLongThinSelectionWithoutAxisCaps() {
        assertTrue(MiningSelectionBounds.between(0, 35, 0, 35, 0, 35).fits(MiningLimits.DEFAULT_VOLUME));
        assertTrue(MiningSelectionBounds.between(0, 511, 64, 64, 0, 63).fits(MiningLimits.DEFAULT_VOLUME));
        assertFalse(MiningSelectionBounds.between(0, 36, 0, 36, 0, 36).fits(MiningLimits.DEFAULT_VOLUME));
    }

    @Test
    void explicitSupportedCapPreservesWhole48And64Cubes() {
        assertTrue(MiningSelectionBounds.between(0, 47, 0, 47, 0, 47).fits(MiningLimits.MAX_VOLUME));
        assertTrue(MiningSelectionBounds.between(0, 63, 0, 63, 0, 63).fits(MiningLimits.MAX_VOLUME));
        assertFalse(MiningSelectionBounds.between(0, 64, 0, 63, 0, 63).fits(MiningLimits.MAX_VOLUME));
    }

    @Test
    void reversedCornersAreNormalizedButNeverShrunk() {
        var box = MiningSelectionBounds.between(49, 10, 59, 20, 69, 30);
        assertEquals(new MiningSelectionBounds(10, 49, 20, 59, 30, 69), box);
        assertFalse(box.fits(MiningLimits.DEFAULT_VOLUME));
        assertTrue(box.fits(64_000));
    }

    @Test
    void extremeCoordinatesCannotOverflowIntoSmallVolume() {
        assertFalse(MiningSelectionBounds.between(Integer.MIN_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE).fits(MiningLimits.MAX_VOLUME));
        assertFalse(MiningLimits.fitsVolume(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, MiningLimits.MAX_VOLUME));
        assertFalse(MiningLimits.fitsVolume(0, 1, 1, MiningLimits.MAX_VOLUME));
    }

    @Test
    void sparseOrHollowTargetsStillUseTheirSelectionEnvelope() {
        List<BlockPos> endpoints = List.of(new BlockPos(-6, 64, 0), new BlockPos(6, 64, 0));
        assertTrue(MiningSelectionBounds.accepts(endpoints, 13));
        assertFalse(MiningSelectionBounds.accepts(endpoints, 12));
        assertFalse(MiningSelectionBounds.accepts(List.of(new BlockPos(0, 0, 0), new BlockPos(1000, 1000, 1000)), 262144));
        assertFalse(MiningSelectionBounds.accepts(List.of(), 100));
    }

    @Test
    void newAndQueuedAreaMineValidateWholeRequestBeforeScanning() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsUltimineProcessor.java"));
        for (String method : List.of("public static boolean areaMine", "public static int queueAreaMine")) {
            int start = source.indexOf(method);
            int validation = source.indexOf("RtsMiningRequestLimits.accepts(player,", start);
            int scan = source.indexOf("AreaOperationExecutor.scanAreaMineTargets", start);
            assertTrue(start >= 0 && validation > start && scan > validation);
        }
        assertFalse(source.contains("limitAreaMineBox("));
        assertFalse(source.contains("explicitAreaDestroyFitsSoftEnvelopeForCaps"));
    }
}
