package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * 快速建造与范围破坏共用的预览输入限幅器。
 *
 * <p>这里仅在生成方块列表之前收紧第二点和高度，不读取世界、不判断库存，也不执行操作。
 * 因此普通模式、高级模式和服务端最终校验仍可保持各自职责，同时避免客户端先生成一个
 * 远超上限的巨大列表，再在渲染或发包阶段截断。</p>
 */
public final class ShapeSelectionLimiter {
    private ShapeSelectionLimiter() {
    }

    public static ShapeBuildTypes.Input clampDimensions(
            ShapeBuildTypes.Input input, int maxWidth, int maxHeight, int maxDepth) {
        if (input == null || input.pointA() == null || input.pointB() == null || input.shape() == null) {
            return input;
        }
        int safeMaxWidth = Math.max(1, maxWidth);
        int safeMaxHeight = Math.max(1, maxHeight);
        int safeMaxDepth = Math.max(1, maxDepth);
        return switch (input.shape()) {
            case CIRCLE, CYLINDER -> clampRound(input, safeMaxWidth, safeMaxHeight, safeMaxDepth);
            case BALL -> clampBall(input, safeMaxWidth, safeMaxHeight, safeMaxDepth);
            default -> clampRectilinear(input, safeMaxWidth, safeMaxHeight, safeMaxDepth);
        };
    }

    /**
     * 同时限制范围挖掘的三个轴向尺寸和覆盖体积。
     *
     * <p>覆盖体积按形状包围盒计算，而不是按最终非空气方块数量计算。这样与服务端
     * {@code areaMineMaxVolume} 的含义一致，也能保证客户端不会先分配一个超大预览列表。</p>
     */
    public static ShapeBuildTypes.Input clampDimensionsAndVolume(
            ShapeBuildTypes.Input input,
            int maxWidth,
            int maxHeight,
            int maxDepth,
            int maxVolume) {
        ShapeBuildTypes.Input dimensionClamped = clampDimensions(input, maxWidth, maxHeight, maxDepth);
        if (dimensionClamped == null || envelopeVolume(dimensionClamped) <= Math.max(1, maxVolume)) {
            return dimensionClamped;
        }

        ShapeBuildTypes.Input best = scaleSelection(dimensionClamped, 0.0D);
        double low = 0.0D;
        double high = 1.0D;
        for (int i = 0; i < 32; i++) {
            double middle = (low + high) * 0.5D;
            ShapeBuildTypes.Input candidate = scaleSelection(dimensionClamped, middle);
            if (envelopeVolume(candidate) <= Math.max(1, maxVolume)) {
                best = candidate;
                low = middle;
            } else {
                high = middle;
            }
        }
        return best;
    }

    private static ShapeBuildTypes.Input clampRectilinear(
            ShapeBuildTypes.Input input, int maxWidth, int maxHeight, int maxDepth) {
        BlockPos a = input.pointA();
        BlockPos b = input.pointB();
        BlockPos limitedB = a.offset(
                clampSignedOffset(b.getX() - a.getX(), maxWidth - 1),
                clampSignedOffset(b.getY() - a.getY(), maxHeight - 1),
                clampSignedOffset(b.getZ() - a.getZ(), maxDepth - 1));
        int limitedHeight = clampSignedOffset(input.boxHeightOffset(), maxHeight - 1);
        return copy(input, limitedB, limitedHeight);
    }

    private static ShapeBuildTypes.Input clampRound(
            ShapeBuildTypes.Input input, int maxWidth, int maxHeight, int maxDepth) {
        Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(input.shape(), input.planeFace());
        BlockPos a = input.pointA();
        BlockPos b = input.pointB();
        int dx = b.getX() - a.getX();
        int dy = b.getY() - a.getY();
        int dz = b.getZ() - a.getZ();
        int axisA = ShapeGeometryUtil.dotDelta(dx, dy, dz, axes[0]);
        int axisB = ShapeGeometryUtil.dotDelta(dx, dy, dz, axes[1]);
        int maxRadius = Math.max(0, (Math.min(
                maxLengthForAxis(axes[0].getAxis(), maxWidth, maxHeight, maxDepth),
                maxLengthForAxis(axes[1].getAxis(), maxWidth, maxHeight, maxDepth)) - 1) / 2);
        int radius = (int) Math.round(Math.sqrt(axisA * (double) axisA + axisB * (double) axisB));
        if (radius > maxRadius && radius > 0) {
            double scale = maxRadius / (double) radius;
            b = ShapeGeometryUtil.offsetPos(
                    a,
                    axes[0], (int) Math.round(axisA * scale),
                    axes[1], (int) Math.round(axisB * scale));
        }
        Direction normal = input.planeFace() == null ? Direction.UP : input.planeFace();
        int height = input.shape() == BuildShape.CYLINDER
                ? clampSignedOffset(
                        input.boxHeightOffset(),
                        maxLengthForAxis(normal.getAxis(), maxWidth, maxHeight, maxDepth) - 1)
                : input.boxHeightOffset();
        return copy(input, b, height);
    }

    private static ShapeBuildTypes.Input clampBall(
            ShapeBuildTypes.Input input, int maxWidth, int maxHeight, int maxDepth) {
        BlockPos a = input.pointA();
        BlockPos b = input.pointB();
        int dx = b.getX() - a.getX();
        int dy = b.getY() - a.getY();
        int dz = b.getZ() - a.getZ();
        int maxRadius = Math.max(0, (Math.min(maxWidth, Math.min(maxHeight, maxDepth)) - 1) / 2);
        int radius = (int) Math.round(Math.sqrt(dx * (double) dx + dy * (double) dy + dz * (double) dz));
        if (radius > maxRadius && radius > 0) {
            double scale = maxRadius / (double) radius;
            b = a.offset(
                    (int) Math.round(dx * scale),
                    (int) Math.round(dy * scale),
                    (int) Math.round(dz * scale));
        }
        return copy(input, b, input.boxHeightOffset());
    }

    private static ShapeBuildTypes.Input scaleSelection(ShapeBuildTypes.Input input, double scale) {
        BlockPos a = input.pointA();
        BlockPos b = input.pointB();
        BlockPos scaledB = a.offset(
                scaleSignedOffset(b.getX() - a.getX(), scale),
                scaleSignedOffset(b.getY() - a.getY(), scale),
                scaleSignedOffset(b.getZ() - a.getZ(), scale));
        return copy(input, scaledB, scaleSignedOffset(input.boxHeightOffset(), scale));
    }

    private static int scaleSignedOffset(int offset, double scale) {
        return (int) Math.round(offset * Mth.clamp(scale, 0.0D, 1.0D));
    }

    static long envelopeVolume(ShapeBuildTypes.Input input) {
        if (input == null || input.pointA() == null || input.pointB() == null || input.shape() == null) {
            return 0L;
        }
        BlockPos a = input.pointA();
        BlockPos b = input.pointB();
        int dx = b.getX() - a.getX();
        int dy = b.getY() - a.getY();
        int dz = b.getZ() - a.getZ();
        return switch (input.shape()) {
            case CIRCLE -> roundEnvelopeVolume(input, dx, dy, dz, false);
            case CYLINDER -> roundEnvelopeVolume(input, dx, dy, dz, true);
            case BALL -> {
                int radius = roundedSpatialRadius(dx, dy, dz);
                long diameter = (radius * 2L) + 1L;
                yield saturatedProduct(diameter, diameter, diameter);
            }
            case SQUARE -> {
                Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(input.shape(), input.planeFace());
                long axisA = Math.abs((long) ShapeGeometryUtil.dotDelta(dx, dy, dz, axes[0])) + 1L;
                long axisB = Math.abs((long) ShapeGeometryUtil.dotDelta(dx, dy, dz, axes[1])) + 1L;
                yield saturatedProduct(axisA, axisB, 1L);
            }
            case WALL -> saturatedProduct(
                    Math.abs((long) dx) + 1L,
                    Math.abs((long) input.boxHeightOffset()) + 1L,
                    Math.abs((long) dz) + 1L);
            case BOX -> saturatedProduct(
                    Math.abs((long) dx) + 1L,
                    Math.abs((long) input.boxHeightOffset()) + 1L,
                    Math.abs((long) dz) + 1L);
            default -> saturatedProduct(
                    Math.abs((long) dx) + 1L,
                    Math.abs((long) dy) + 1L,
                    Math.abs((long) dz) + 1L);
        };
    }

    private static long roundEnvelopeVolume(
            ShapeBuildTypes.Input input, int dx, int dy, int dz, boolean includeHeight) {
        Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(input.shape(), input.planeFace());
        int axisA = ShapeGeometryUtil.dotDelta(dx, dy, dz, axes[0]);
        int axisB = ShapeGeometryUtil.dotDelta(dx, dy, dz, axes[1]);
        int radius = (int) Math.round(Math.sqrt(axisA * (double) axisA + axisB * (double) axisB));
        long diameter = (radius * 2L) + 1L;
        long height = includeHeight ? Math.abs((long) input.boxHeightOffset()) + 1L : 1L;
        return saturatedProduct(diameter, diameter, height);
    }

    private static int roundedSpatialRadius(int dx, int dy, int dz) {
        return (int) Math.round(Math.sqrt(dx * (double) dx + dy * (double) dy + dz * (double) dz));
    }

    private static long saturatedProduct(long a, long b, long c) {
        if (a <= 0L || b <= 0L || c <= 0L) {
            return 0L;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        long ab = a * b;
        return ab > Long.MAX_VALUE / c ? Long.MAX_VALUE : ab * c;
    }

    private static ShapeBuildTypes.Input copy(ShapeBuildTypes.Input input, BlockPos pointB, int heightOffset) {
        return new ShapeBuildTypes.Input(
                input.shape(),
                input.planeFace(),
                input.placementFace(),
                input.pointA(),
                pointB,
                heightOffset,
                input.connectedLine());
    }

    private static int maxLengthForAxis(
            Direction.Axis axis, int maxWidth, int maxHeight, int maxDepth) {
        return switch (axis) {
            case X -> maxWidth;
            case Y -> maxHeight;
            case Z -> maxDepth;
        };
    }

    private static int clampSignedOffset(int offset, int maxMagnitude) {
        return Mth.clamp(offset, -Math.max(0, maxMagnitude), Math.max(0, maxMagnitude));
    }
}
