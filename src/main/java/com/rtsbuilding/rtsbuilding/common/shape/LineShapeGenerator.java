package com.rtsbuilding.rtsbuilding.common.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
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
        int dx = clampOffset(input.end().getX() - input.start().getX());
        int dy = clampOffset(input.end().getY() - input.start().getY());
        int dz = clampOffset(input.end().getZ() - input.start().getZ());

        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        List<BlockPos> result = new ArrayList<>(steps + 1);

        if (steps <= 0) {
            result.add(input.start());
            return result;
        }

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = input.start().getX() + (int) Math.round(dx * t);
            int y = input.start().getY() + (int) Math.round(dy * t);
            int z = input.start().getZ() + (int) Math.round(dz * t);
            result.add(new BlockPos(x, y, z));
        }

        return result;
    }
}
