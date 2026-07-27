package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 圆柱体形状生成器。
 *
 * <p>半径由起点到终点在 XZ 平面的距离决定，高度由 heightOffset 决定。
 * FILL 生成实心圆柱；HOLLOW/SKELETON 生成侧壁以及上下表面，单层时退化为圆环。</p>
 */
public class CylinderShapeGenerator extends AreaShapeGenerator {
    @Override
    public String getName() {
        return "cylinder";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        int dx = input.end().getX() - input.start().getX();
        int dz = input.end().getZ() - input.start().getZ();
        int radius = Math.min(64, Math.max(0, (int) Math.round(Math.sqrt(dx * (double) dx + dz * (double) dz))));
        int height = clampOffset(input.heightOffset());
        int minY = Math.min(0, height);
        int maxY = Math.max(0, height);
        Set<Cell> filledBase = circleCells(radius, true);
        Set<Cell> shellBase = circleCells(radius, false);
        boolean fill = fillMode == ShapeFillMode.FILL;
        boolean singleLayer = minY == maxY;

        List<BlockPos> result = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            boolean capLayer = y == minY || y == maxY;
            for (Cell cell : filledBase) {
                if (fill || (!singleLayer && capLayer) || shellBase.contains(cell)) {
                    result.add(input.start().add(cell.x(), y, cell.z()));
                }
            }
        }
        return result;
    }

    private static Set<Cell> circleCells(int radius, boolean fill) {
        int outer2 = radius * radius;
        int inner = Math.max(0, radius - 1);
        int inner2 = inner * inner;
        Set<Cell> cells = new HashSet<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int dist2 = x * x + z * z;
                if (dist2 <= outer2 && (fill || dist2 >= inner2)) {
                    cells.add(new Cell(x, z));
                }
            }
        }
        return cells;
    }

    private static final class Cell {
        private final int x;
        private final int z;

        private Cell(int x, int z) {
            this.x = x;
            this.z = z;
        }

        private int x() {
            return x;
        }

        private int z() {
            return z;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Cell)) return false;
            Cell that = (Cell) other;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return (31 * x) + z;
        }
    }
}
