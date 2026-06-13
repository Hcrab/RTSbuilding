package com.rtsbuilding.rtsbuilding.common.shape;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Wall (vertical extruded line) shape generator.
 * <p>
 * Generates positions along a baseline (XZ plane) extruded vertically by the
 * height offset.  Supports FILL (solid wall) and HOLLOW (edge frame) modes.
 */
public class WallShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "wall";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        int dx = clampOffset(input.end().getX() - input.start().getX());
        int dz = clampOffset(input.end().getZ() - input.start().getZ());
        int dy = clampOffset(input.heightOffset());

        // Generate baseline (start → end projected onto XZ plane)
        BlockPos endPos = new BlockPos(input.start().getX() + dx, input.start().getY(), input.start().getZ() + dz);
        BlockPos baseStart = input.start();
        List<BlockPos> base = generateLinePositions(baseStart, endPos);

        int minY = Math.min(0, dy);
        int maxY = Math.max(0, dy);
        List<BlockPos> result = new ArrayList<>();

        for (int i = 0; i < base.size(); i++) {
            BlockPos basePos = base.get(i);
            boolean endColumn = (base.size() <= 1) || (i == 0 || i == base.size() - 1);
            for (int iy = minY; iy <= maxY; iy++) {
                if (fillMode != ShapeFillMode.FILL && !endColumn && iy != minY && iy != maxY) {
                    continue;
                }
                result.add(basePos.above(iy));
            }
        }

        return result;
    }
}
