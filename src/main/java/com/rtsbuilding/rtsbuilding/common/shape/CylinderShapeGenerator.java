package com.rtsbuilding.rtsbuilding.common.shape;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 圆柱体生成器：第一点是底面中心，第二点决定半径，高度由 heightOffset 决定。
 *
 * <p>这个服务端生成器和客户端 quick-build 预览保持同一语义：FILL 是实心圆柱，
 * HOLLOW/SKELETON 是侧壁加上下表面；单层高度时退化成圆环。</p>
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
                    result.add(input.start().offset(cell.x(), y, cell.z()));
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

    private record Cell(int x, int z) {
    }
}
