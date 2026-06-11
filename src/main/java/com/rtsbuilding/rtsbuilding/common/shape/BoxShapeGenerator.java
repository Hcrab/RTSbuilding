package com.rtsbuilding.rtsbuilding.common.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Box (3D cuboid) shape generator.
 * <p>
 * Generates all positions within a rectangular prism defined by two opposite
 * corners and an optional height offset.  Supports FILL, HOLLOW, and SKELETON
 * fill modes.
 */
public class BoxShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "box";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        int dx = clampOffset(input.end().getX() - input.start().getX());
        int dz = clampOffset(input.end().getZ() - input.start().getZ());
        int dy = clampOffset(input.heightOffset());

        int minX = Math.min(0, dx);
        int maxX = Math.max(0, dx);
        int minZ = Math.min(0, dz);
        int maxZ = Math.max(0, dz);
        int minY = Math.min(0, dy);
        int maxY = Math.max(0, dy);

        List<BlockPos> full = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    full.add(input.start().offset(x, y, z));
                }
            }
        }

        if (fillMode == ShapeFillMode.FILL || full.isEmpty()) {
            return full;
        }

        return filterBoundary(full, minY, maxY);
    }
}
