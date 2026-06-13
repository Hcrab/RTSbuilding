package com.rtsbuilding.rtsbuilding.common.shape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Square (2D rectangle) shape generator.
 * <p>
 * Generates a flat rectangular area projected onto a plane determined by the
 * clicked face.  Has no height — just one layer.  Supports FILL and HOLLOW.
 */
public class SquareShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "square";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        Direction face = input.clickedFace();
        Direction[] axes = resolvePlaneAxes(face);

        int dx = clampOffset(input.end().getX() - input.start().getX());
        int dy = clampOffset(input.end().getY() - input.start().getY());
        int dz = clampOffset(input.end().getZ() - input.start().getZ());

        int aOffset = clampOffset(dotDelta(dx, dy, dz, axes[0]));
        int bOffset = clampOffset(dotDelta(dx, dy, dz, axes[1]));

        int minA = Math.min(0, aOffset);
        int maxA = Math.max(0, aOffset);
        int minB = Math.min(0, bOffset);
        int maxB = Math.max(0, bOffset);

        List<BlockPos> all = buildPlanePositions(input.start(), axes[0], axes[1], minA, maxA, minB, maxB);

        if (fillMode == ShapeFillMode.FILL || all.isEmpty()) {
            return all;
        }

        // HOLLOW / SKELETON: delegate to generic boundary filter
        int minY = Math.min(0, clampOffset(input.end().getY() - input.start().getY()));
        int maxY = Math.max(0, clampOffset(input.end().getY() - input.start().getY()));
        return filterBoundary(all, minY, maxY);
    }
}