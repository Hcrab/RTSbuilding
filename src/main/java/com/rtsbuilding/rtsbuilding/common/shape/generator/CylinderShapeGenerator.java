package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 鍦嗘煴浣撳舰鐘剁敓鎴愬櫒銆? *
 * <p>鍗婂緞鐢辫捣鐐瑰埌缁堢偣鍦?XZ 骞抽潰鐨勮窛绂诲喅瀹氾紝楂樺害鐢?heightOffset 鍐冲畾銆? * FILL 鐢熸垚瀹炲績鍦嗘煴锛汬OLLOW/SKELETON 鐢熸垚渚у浠ュ強涓婁笅琛ㄩ潰锛屽崟灞傛椂閫€鍖栦负鍦嗙幆銆?/p>
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
