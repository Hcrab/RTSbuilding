package com.rtsbuilding.rtsbuilding.common.mining;

import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyPlanner;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MiningVolumePolicyTest {
    @Test
    void dimensionClampingKeepsAdmittedLongShapesAndHandlesHugeDrag() {
        assertEquals(new MiningLimits.Dimensions(512, 1, 64), MiningLimits.clampDimensions(512, 1, 64, 46656));
        assertEquals(new MiningLimits.Dimensions(64, 64, 64),
                MiningLimits.clampDimensions(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 262144));
        assertEquals(new MiningLimits.Dimensions(3, 2, 3), MiningLimits.clampDimensions(3, 3, 3, 26));
        for (int volume : new int[] {1, 2, 26, 512, 32768, 98304, 262144}) {
            var size = MiningLimits.clampDimensions(50000, 10000, 50, volume);
            assertTrue(MiningLimits.fitsVolume(size.width(), size.height(), size.depth(), volume));
        }
    }

    @Test
    void convenienceBoxUsesConfiguredVolumeBeforeReadingWorld() {
        LevelReader level = mock(LevelReader.class);
        var settings = new RtsConvenienceDestroySettings(512, 1, 1, 0, 0, 256);
        assertEquals(RtsConvenienceDestroyPlanner.ResultCode.OVER_LIMIT,
                RtsConvenienceDestroyPlanner.plan(level, RtsConvenienceDestroyMode.REPEAT_BOX,
                        new BlockPos(0, 64, 0), Direction.UP, settings, 511).code());
        verifyNoInteractions(level);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        assertEquals(RtsConvenienceDestroyPlanner.ResultCode.UNLOADED_CHUNK,
                RtsConvenienceDestroyPlanner.plan(level, RtsConvenienceDestroyMode.REPEAT_BOX,
                        new BlockPos(0, 64, 0), Direction.UP, settings, 512).code());
    }

    @Test
    void malformedHugeBoxCannotOverflowAndTriggerWorldScan() {
        LevelReader level = mock(LevelReader.class);
        var huge = new RtsConvenienceDestroySettings(Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, 0, 0, 256);
        assertEquals(RtsConvenienceDestroyPlanner.ResultCode.OVER_LIMIT,
                RtsConvenienceDestroyPlanner.plan(level, RtsConvenienceDestroyMode.REPEAT_BOX,
                        BlockPos.ZERO, Direction.UP, huge, 262144).code());
        verifyNoInteractions(level);
    }

    @Test
    void chunkPlanCanExceedOld32768AndClipsToWorldHeightWithoutOverflow() {
        LevelReader level = mock(LevelReader.class);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        var settings = new RtsConvenienceDestroySettings(1, 1, 1, Integer.MAX_VALUE, Integer.MAX_VALUE, 256);
        assertEquals(RtsConvenienceDestroyPlanner.ResultCode.UNLOADED_CHUNK,
                RtsConvenienceDestroyPlanner.plan(level, RtsConvenienceDestroyMode.CHUNK_QUARRY,
                        new BlockPos(0, 64, 0), Direction.UP, settings, 98304).code());
        assertEquals(RtsConvenienceDestroyPlanner.ResultCode.OVER_LIMIT,
                RtsConvenienceDestroyPlanner.plan(level, RtsConvenienceDestroyMode.CHUNK_QUARRY,
                        new BlockPos(0, 64, 0), Direction.UP, settings, 98303).code());
    }
}
