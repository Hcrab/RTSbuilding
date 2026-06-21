package com.rtsbuilding.rtsbuilding.common.shape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Square (2D rectangle) shape generator.
 * <p>
 * Generates a flat rectangular area projected onto a plane determined by the
 * clicked face.  Has no height ??just one layer.  Supports FILL and HOLLOW.
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

        // HOLLOW: keep only edge positions
        List<BlockPos> boundary = new ArrayList<>();
        for (BlockPos pos : all) {
            boolean onEdge = pos.getX() == input.start().getX() + (minA == 0 ? 0 : minA * axes[0].getStepX())
                    || pos.getX() == input.start().getX() + (maxA == 0 ? 0 : maxA * axes[0].getStepX())
                    || pos.getZ() == input.start().getZ() + (minB == 0 ? 0 : minB * axes[1].getStepZ())
                    || pos.getZ() == input.start().getZ() + (maxB == 0 ? 0 : maxB * axes[1].getStepZ());
            if (onEdge) {
                boundary.add(pos);
            }
        }
        return boundary;
    }
}
