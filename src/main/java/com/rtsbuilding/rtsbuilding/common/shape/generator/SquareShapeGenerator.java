package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * 正方形/矩形（2D 平面）形状生成器。
 * <p>
 * 在点击面所确定的平面上生成一个扁平矩形区域，
 * 仅有一层厚度，无高度扩展。支持 FILL（实心）和 HOLLOW（空心）模式。
 */
public class SquareShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "square";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        // 根据点击面确定平面上的两个轴向
        Direction face = input.clickedFace();
        Direction[] axes = resolvePlaneAxes(face);

        // 计算偏移并限制范围
        int dx = clampOffset(input.end().getX() - input.start().getX());
        int dy = clampOffset(input.end().getY() - input.start().getY());
        int dz = clampOffset(input.end().getZ() - input.start().getZ());

        // 将偏移投影到两个平面轴上
        int aOffset = clampOffset(dotDelta(dx, dy, dz, axes[0]));
        int bOffset = clampOffset(dotDelta(dx, dy, dz, axes[1]));

        int minA = Math.min(0, aOffset);
        int maxA = Math.max(0, aOffset);
        int minB = Math.min(0, bOffset);
        int maxB = Math.max(0, bOffset);

        // 生成平面上的所有方块位置
        List<BlockPos> all = buildPlanePositions(input.start(), axes[0], axes[1], minA, maxA, minB, maxB);

        if (fillMode == ShapeFillMode.FILL || all.isEmpty()) {
            return all;
        }

        // HOLLOW / SKELETON：调用通用边界过滤器
        int minY = Math.min(0, clampOffset(input.end().getY() - input.start().getY()));
        int maxY = Math.max(0, clampOffset(input.end().getY() - input.start().getY()));
        return filterBoundary(all, minY, maxY);
    }
}