package com.rtsbuilding.rtsbuilding.common.mining;

import com.rtsbuilding.rtsbuilding.common.shape.generator.BoxShapeGenerator;
import com.rtsbuilding.rtsbuilding.common.shape.generator.CircleShapeGenerator;
import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MiningShapeVolumeTest {
    @Test
    void admittedMiningBoxDoesNotInheritLegacyBuildingOffsetCap() {
        var input = miningInput(new BlockPos(511, 64, 0), 0);
        assertTrue(MiningShapeVolume.fits(AreaShape.BOX, input, 512));
        var positions = new BoxShapeGenerator().generatePositions(input, ShapeFillMode.FILL);
        assertEquals(512, positions.size());
        assertTrue(positions.contains(new BlockPos(511, 64, 0)));
        var legacyBuilding = new AreaShapeInput(input.start(), input.end(), 0, Direction.UP, Direction.UP);
        assertEquals(65, new BoxShapeGenerator().generatePositions(legacyBuilding, ShapeFillMode.FILL).size());
    }

    @Test
    void completeMaximumCubeIsGenerated() {
        var input = miningInput(new BlockPos(63, 127, 63), 63);
        assertTrue(MiningShapeVolume.fits(AreaShape.BOX, input, MiningLimits.MAX_VOLUME));
        assertEquals(MiningLimits.MAX_VOLUME, new BoxShapeGenerator().generatePositions(input, ShapeFillMode.FILL).size());
    }

    @Test
    void roundShapesCountDiameterRatherThanJustRadiusSelection() {
        var input = miningInput(new BlockPos(80, 64, 0), 0);
        assertTrue(MiningShapeVolume.fits(AreaShape.CIRCLE, input, 161 * 161));
        assertFalse(MiningShapeVolume.fits(AreaShape.CIRCLE, input, 161 * 161 - 1));
        var positions = new CircleShapeGenerator().generatePositions(input, ShapeFillMode.FILL);
        assertTrue(positions.contains(new BlockPos(80, 64, 0)));
        assertTrue(positions.contains(new BlockPos(-80, 64, 0)));
        assertFalse(MiningShapeVolume.fits(AreaShape.BALL, input, MiningLimits.MAX_VOLUME));
    }

    @Test
    void cylinderHeightAndHugeRadiusAreBoundedBeforeGeneration() {
        var input = miningInput(new BlockPos(10, 64, 0), 99);
        assertTrue(MiningShapeVolume.fits(AreaShape.CYLINDER, input, 21 * 21 * 100));
        assertFalse(MiningShapeVolume.fits(AreaShape.CYLINDER, input, 21 * 21 * 99));
        var huge = miningInput(new BlockPos(Integer.MAX_VALUE, 64, Integer.MAX_VALUE), 0);
        assertFalse(MiningShapeVolume.fits(AreaShape.CIRCLE, huge, MiningLimits.MAX_VOLUME));
    }

    private static AreaShapeInput miningInput(BlockPos end, int height) {
        return new AreaShapeInput(new BlockPos(0, 64, 0), end, height, Direction.DOWN, Direction.DOWN,
                MiningLimits.MAX_VOLUME - 1);
    }
}
