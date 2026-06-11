package com.rtsbuilding.rtsbuilding.common.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Wall (vertical extruded line) shape generator.
 * <p>
 * Generates positions along a base line (XZ plane) extruded vertically by the
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

        // Generate base line (start → end projected onto XZ plane)
        LinkedHashSet<BlockPos> baseLine = new LinkedHashSet<>();
        generateLine(baseLine, input.start(), new BlockPos(
                input.start().getX() + dx,
                input.start().getY(),
                input.start().getZ() + dz));

        int minY = Math.min(0, dy);
        int maxY = Math.max(0, dy);
        List<BlockPos> result = new ArrayList<>();
        List<BlockPos> base = new ArrayList<>(baseLine);

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

    /**
     * Generates a straight line of connected blocks between two positions.
     */
    private static void generateLine(LinkedHashSet<BlockPos> targets, BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps <= 0) {
            targets.add(start);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = start.getX() + (int) Math.round(dx * t);
            int y = start.getY() + (int) Math.round(dy * t);
            int z = start.getZ() + (int) Math.round(dz * t);
            targets.add(new BlockPos(x, y, z));
        }
    }
}
