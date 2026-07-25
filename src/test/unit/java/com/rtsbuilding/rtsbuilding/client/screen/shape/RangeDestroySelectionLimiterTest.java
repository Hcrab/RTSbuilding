package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 范围破坏的输入、包围盒和位置列表必须服从同一组轴长与体积上限。
 */
class RangeDestroySelectionLimiterTest {
    @Test
    void boxClampKeepsAnchorAndHonorsAxisAndVolumeCaps() {
        RangeDestroySelectionLimiter.Limits limits =
                new RangeDestroySelectionLimiter.Limits(6, 5, 4, 60);
        BlockPos anchor = new BlockPos(3, 3, 3);
        RtsCullingBox source = new RtsCullingBox(
                7,
                BlockPos.ZERO,
                new BlockPos(9, 9, 9));

        RtsCullingBox limited =
                RangeDestroySelectionLimiter.clampBox(source, anchor, limits);

        assertEquals(7, limited.id());
        assertTrue(limited.contains(anchor));
        assertTrue(limited.width() <= 6);
        assertTrue(limited.height() <= 5);
        assertTrue(limited.depth() <= 4);
        assertTrue((long) limited.width() * limited.height() * limited.depth()
                <= 60);
        assertTrue(RangeDestroySelectionLimiter.contains(limited, limits));
        assertFalse(RangeDestroySelectionLimiter.contains(source, limits));
    }

    @Test
    void positionClampUsesInputAnchorAndDropsOutsideCells() {
        RangeDestroySelectionLimiter.Limits limits =
                new RangeDestroySelectionLimiter.Limits(3, 2, 2, 12);
        ShapeBuildTypes.Input input = new ShapeBuildTypes.Input(
                BuildShape.BOX,
                Direction.UP,
                Direction.UP,
                new BlockPos(2, 1, 1),
                new BlockPos(5, 3, 3),
                2,
                false);
        List<BlockPos> positions = new ArrayList<>();
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 6; x++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }

        List<BlockPos> limited =
                RangeDestroySelectionLimiter.clampPositions(
                        input,
                        positions,
                        limits);

        assertEquals(12, limited.size());
        assertTrue(limited.contains(input.pointA()));
        assertFalse(limited.contains(new BlockPos(5, 3, 3)));
    }

    @Test
    void roundPositionsStayUnchangedWhenEnvelopeAlreadyFits() {
        List<BlockPos> positions = List.of(
                new BlockPos(-1, 0, 0),
                BlockPos.ZERO,
                new BlockPos(1, 0, 0));
        RangeDestroySelectionLimiter.Limits limits =
                new RangeDestroySelectionLimiter.Limits(3, 1, 1, 3);

        List<BlockPos> limited =
                RangeDestroySelectionLimiter.clampRoundPositions(
                        null,
                        positions,
                        limits);

        assertEquals(positions, limited);
        assertNotSame(positions, limited);
    }

    @Test
    void limitsAndNullInputsFailClosedToOneCell() {
        RangeDestroySelectionLimiter.Limits limits =
                new RangeDestroySelectionLimiter.Limits(0, -2, 0, -1);

        assertEquals(1, limits.maxWidth());
        assertEquals(1, limits.maxHeight());
        assertEquals(1, limits.maxDepth());
        assertEquals(1, limits.maxVolume());
        assertFalse(RangeDestroySelectionLimiter.contains(null, limits));
        assertNull(RangeDestroySelectionLimiter.clampBox(null, BlockPos.ZERO, limits));
        assertEquals(List.of(), RangeDestroySelectionLimiter.clampPositions(
                null,
                null,
                limits));
    }
}
