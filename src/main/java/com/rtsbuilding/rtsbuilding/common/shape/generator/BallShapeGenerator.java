package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 球体形状生成器。
 *
 * <p>起点是球心，终点决定半径。FILL 生成实心球；HOLLOW/SKELETON 生成球壳。</p>
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
