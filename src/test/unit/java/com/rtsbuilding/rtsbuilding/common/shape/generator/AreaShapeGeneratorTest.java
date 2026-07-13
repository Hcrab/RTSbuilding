package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.AreaShape;
import com.rtsbuilding.rtsbuilding.common.shape.AreaShapeGenerator;
import com.rtsbuilding.rtsbuilding.common.shape.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.common.shape.ShapeGeneratorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaShapeGeneratorTest {
    @Test
    void registryProvidesGeneratorForEveryNetworkShapeOrdinal() {
        for (AreaShape shape : AreaShape.values()) {
            AreaShapeGenerator generator = ShapeGeneratorRegistry.getGenerator((byte) shape.ordinal());
            assertEquals(ShapeGeneratorRegistry.getGenerator(shape).getName(), generator.getName());
        }

        assertEquals("block", ShapeGeneratorRegistry.getGenerator((byte) -1).getName());
        assertEquals("block", ShapeGeneratorRegistry.getGenerator((byte) 99).getName());
    }

    @Test
    void lineGeneratorKeepsEndpointsAndOrder() {
        AreaShapeGenerator generator = ShapeGeneratorRegistry.getGenerator(AreaShape.LINE);
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos end = new BlockPos(3, 64, 0);

        List<BlockPos> positions = generator.generatePositions(AreaShapeInput.of(start, end), ShapeFillMode.FILL);

        assertEquals(List.of(
                new BlockPos(0, 64, 0),
                new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0),
                new BlockPos(3, 64, 0)), positions);
    }

    @Test
    void squareFillAndHollowModesUseExpectedPlaneCells() {
        AreaShapeGenerator generator = ShapeGeneratorRegistry.getGenerator(AreaShape.SQUARE);
        BlockPos start = new BlockPos(0, 64, 0);
        AreaShapeInput input = AreaShapeInput.of(start, new BlockPos(2, 64, 2), 0, Direction.UP, Direction.UP);

        List<BlockPos> fill = generator.generatePositions(input, ShapeFillMode.FILL);
        List<BlockPos> hollow = generator.generatePositions(input, ShapeFillMode.HOLLOW);

        assertEquals(9, new HashSet<>(fill).size());
        assertTrue(fill.contains(new BlockPos(1, 64, 1)));
        assertEquals(8, new HashSet<>(hollow).size());
        assertFalse(hollow.contains(new BlockPos(1, 64, 1)));
    }

    @Test
    void boxFillAndHollowModesKeepInteriorBoundaryContract() {
        AreaShapeGenerator generator = ShapeGeneratorRegistry.getGenerator(AreaShape.BOX);
        BlockPos start = new BlockPos(0, 64, 0);
        AreaShapeInput input = AreaShapeInput.of(start, new BlockPos(2, 64, 2), 2, Direction.UP, Direction.UP);

        List<BlockPos> fill = generator.generatePositions(input, ShapeFillMode.FILL);
        List<BlockPos> hollow = generator.generatePositions(input, ShapeFillMode.HOLLOW);

        assertEquals(27, new HashSet<>(fill).size());
        assertTrue(fill.contains(new BlockPos(1, 65, 1)));
        assertEquals(26, new HashSet<>(hollow).size());
        assertFalse(hollow.contains(new BlockPos(1, 65, 1)));
    }

    @Test
    void cylinderUsesCircleFootprintAndHeightOffset() {
        AreaShapeGenerator generator = ShapeGeneratorRegistry.getGenerator(AreaShape.CYLINDER);
        BlockPos start = new BlockPos(0, 64, 0);
        AreaShapeInput input = AreaShapeInput.of(start, new BlockPos(2, 64, 0), 2, Direction.UP, Direction.UP);

        List<BlockPos> fill = generator.generatePositions(input, ShapeFillMode.FILL);
        List<BlockPos> hollow = generator.generatePositions(input, ShapeFillMode.HOLLOW);

        assertEquals(39, new HashSet<>(fill).size());
        assertTrue(fill.contains(new BlockPos(0, 65, 0)));
        assertEquals(38, new HashSet<>(hollow).size());
        assertFalse(hollow.contains(new BlockPos(0, 65, 0)));
        assertTrue(hollow.contains(new BlockPos(0, 64, 0)));
        assertTrue(hollow.contains(new BlockPos(0, 66, 0)));
    }

    @Test
    void ballRadiusCreatesThreeDimensionalVolume() {
        AreaShapeGenerator generator = ShapeGeneratorRegistry.getGenerator(AreaShape.BALL);
        BlockPos start = new BlockPos(0, 64, 0);
        AreaShapeInput input = AreaShapeInput.of(start, new BlockPos(1, 64, 0), 0, Direction.UP, Direction.UP);

        List<BlockPos> fill = generator.generatePositions(input, ShapeFillMode.FILL);

        assertEquals(7, new HashSet<>(fill).size());
        assertTrue(fill.contains(start.above()));
        assertTrue(fill.contains(start.below()));
        assertTrue(fill.contains(start.east()));
        assertTrue(fill.contains(start.west()));
        assertTrue(fill.contains(start.north()));
        assertTrue(fill.contains(start.south()));
    }
}
