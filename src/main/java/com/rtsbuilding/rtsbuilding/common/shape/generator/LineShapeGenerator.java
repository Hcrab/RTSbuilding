package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 直线（1D）形状生成器。
 * <p>
 * 在三维空间中生成一条连接起点和终点的直线，
 * 通常沿着起点和终点之间距离最大的轴延伸。仅支持 FILL 模式。
 */
public class LineShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "line";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        return generateLinePositions(input.start(), input.end());
    }
}
