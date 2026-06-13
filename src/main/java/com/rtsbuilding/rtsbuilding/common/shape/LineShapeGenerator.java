package com.rtsbuilding.rtsbuilding.common.shape;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Line (1D) shape generator.
 * <p>
 * Generates a single straight line in 3D space, typically following the axis
 * with the greatest distance between start and end.  Only supports FILL mode.
 */
public class LineShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "line";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        return generateLinePositions(input.start(), input.end());
    }
}
