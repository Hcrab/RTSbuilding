package com.rtsbuilding.rtsbuilding.common.shape;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 球体生成器：第一点是球心，第二点决定半径。
 *
 * <p>FILL 生成实心球；HOLLOW/SKELETON 生成一格厚的球壳。</p>
 */
public class BallShapeGenerator extends AreaShapeGenerator {
    @Override
    public String getName() {
        return "ball";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        int dx = input.end().getX() - input.start().getX();
        int dy = input.end().getY() - input.start().getY();
        int dz = input.end().getZ() - input.start().getZ();
        int radius = Math.min(64, Math.max(0, (int) Math.round(Math.sqrt(
                dx * (double) dx + dy * (double) dy + dz * (double) dz))));
        int outer2 = radius * radius;
        int inner = Math.max(0, radius - 1);
        int inner2 = inner * inner;
        boolean fill = fillMode == ShapeFillMode.FILL;

        List<BlockPos> result = new ArrayList<>();
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int dist2 = x * x + y * y + z * z;
                    if (dist2 <= outer2 && (fill || dist2 >= inner2)) {
                        result.add(input.start().offset(x, y, z));
                    }
                }
            }
        }
        return result;
    }
}
