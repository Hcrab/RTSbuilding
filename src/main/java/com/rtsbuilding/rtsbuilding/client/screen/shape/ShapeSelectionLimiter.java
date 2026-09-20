package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
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
     * 普通建造的几何限幅：直线/矩形只看 dimension，圆、圆柱底面和球只看 radius。
     * 圆柱的高度仍使用 dimension；不能把半径偷偷折算成旧的轴向上限。
     */
    public static ShapeBuildTypes.Input clampShapeDimensions(
            ShapeBuildTypes.Input input, int maxDimension, int maxRadius) {
        if (input == null || input.pointA() == null || input.pointB() == null || input.shape() == null) {
            return input;
        }
        int safeDimension = Math.max(1, maxDimension);
        int safeRadius = Math.max(0, maxRadius);
        return switch (input.shape()) {
            case CIRCLE, CYLINDER -> clampRoundRadius(input, safeRadius, safeDimension);
            case BALL -> clampBallRadius(input, safeRadius);
            default -> clampRectilinear(input, safeDimension, safeDimension, safeDimension);
        };
    }

    /**
     * 同时限制普通形状的三个轴向尺寸和范围挖掘的覆盖体积。
     *
     * <p>覆盖体积按形状包围盒计算，而不是按最终非空气方块数量计算。这样与服务端
     * {@code maxSelectionVolume} 的含义一致，也能保证客户端不会先分配一个超大预览列表。
     * 该重载保留普通建造的轴向限制；范围破坏使用下面的体积-only 重载。</p>
     */
    public static ShapeBuildTypes.Input clampDimensionsAndVolume(
            ShapeBuildTypes.Input input,
            int maxWidth,
            int maxHeight,
            int maxDepth,
            int maxVolume) {
        int safeMaxVolume = MiningLimits.clampVolume(maxVolume);
        ShapeBuildTypes.Input dimensionClamped = clampDimensions(input, maxWidth, maxHeight, maxDepth);
        if (dimensionClamped == null || envelopeVolume(dimensionClamped) <= safeMaxVolume) {
            return dimensionClamped;
        }

        ShapeBuildTypes.Input best = scaleSelection(dimensionClamped, 0.0D);
        double low = 0.0D;
        double high = 1.0D;
        for (int i = 0; i < 32; i++) {
            double middle = (low + high) * 0.5D;
            ShapeBuildTypes.Input candidate = scaleSelection(dimensionClamped, middle);
            if (envelopeVolume(candidate) <= safeMaxVolume) {
                best = candidate;
                low = middle;
            } else {
                high = middle;
            }
        }
        return best;
    }

    /**
     * 只按包围盒体积限制范围挖掘/范围破坏输入，不给任何单独轴设置隐含上限。
     * 长条形选区只要乘积合法就保持原状；超限时沿三个选区偏移按比例收缩，避免在生成
     * 坐标列表后才截断造成预览与实际请求不一致。
     */
    public static ShapeBuildTypes.Input clampDimensionsAndVolume(
            ShapeBuildTypes.Input input, int maxVolume) {
        if (input == null || input.pointA() == null || input.pointB() == null || input.shape() == null) {
            return input;
        }
        int safeMaxVolume = MiningLimits.clampVolume(maxVolume);
        if (envelopeVolume(input) <= safeMaxVolume) {
            return input;
        }

        ShapeBuildTypes.Input best = scaleSelection(input, 0.0D);
        double low = 0.0D;
        double high = 1.0D;
        for (int i = 0; i < 32; i++) {
            double middle = (low + high) * 0.5D;
            ShapeBuildTypes.Input candidate = scaleSelection(input, middle);
            if (envelopeVolume(candidate) <= safeMaxVolume) {
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
        BlockPos limitedB = ShapeGeometryUtil.offsetPos(a,
                clampSignedOffset((long) b.getX() - a.getX(), maxWidth - 1),
                clampSignedOffset((long) b.getY() - a.getY(), maxHeight - 1),
                clampSignedOffset((long) b.getZ() - a.getZ(), maxDepth - 1));
        int limitedHeight = clampSignedOffset(input.boxHeightOffset(), maxHeight - 1);
        return copy(input, limitedB, limitedHeight);
    }

    private static ShapeBuildTypes.Input clampRound(
            ShapeBuildTypes.Input input, int maxWidth, int maxHeight, int maxDepth) {
        Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(input.shape(), input.planeFace());
        BlockPos a = input.pointA();
        BlockPos b = input.pointB();
        long dx = (long) b.getX() - a.getX();
        long dy = (long) b.getY() - a.getY();
        long dz = (long) b.getZ() - a.getZ();
        long axisA = dotDelta(dx, dy, dz, axes[0]);
        long axisB = dotDelta(dx, dy, dz, axes[1]);
        long maxRadius = Math.max(0L, (Math.min(
                maxLengthForAxis(axes[0].getAxis(), maxWidth, maxHeight, maxDepth),
                maxLengthForAxis(axes[1].getAxis(), maxWidth, maxHeight, maxDepth)) - 1L) / 2L);
        long radius = Math.round(Math.sqrt(axisA * (double) axisA + axisB * (double) axisB));
        if (radius > maxRadius && radius > 0) {
            double scale = maxRadius / (double) radius;
            b = ShapeGeometryUtil.offsetPos(
                    a,
                    axes[0], toInt(Math.round(axisA * scale)),
                    axes[1], toInt(Math.round(axisB * scale)));
        }
        Direction normal = input.planeFace() == null ? Direction.UP : input.planeFace();
        int height = input.shape() == BuildShape.CYLINDER
                ? clampSignedOffset(
                        input.boxHeightOffset(),
                        maxLengthForAxis(normal.getAxis(), maxWidth, maxHeight, maxDepth) - 1)
                : input.boxHeightOffset();
        return copy(input, b, height);
    }

    private static ShapeBuildTypes.Input clampRoundRadius(
            ShapeBuildTypes.Input input, int maxRadius, int maxDimension) {
        Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(input.shape(), input.planeFace());
        BlockPos a = input.pointA();
        BlockPos b = input.pointB();
        long dx = (long) b.getX() - a.getX();
        long dy = (long) b.getY() - a.getY();
        long dz = (long) b.getZ() - a.getZ();
        long axisA = dotDelta(dx, dy, dz, axes[0]);
        long axisB = dotDelta(dx, dy, dz, axes[1]);
        long radius = Math.round(Math.sqrt(axisA * (double) axisA + axisB * (double) axisB));
        if (radius > maxRadius && radius > 0L) {
            double scale = maxRadius / (double) radius;
            b = ShapeGeometryUtil.offsetPos(
                    a,
                    axes[0], toInt(Math.round(axisA * scale)),
                    axes[1], toInt(Math.round(axisB * scale)));
        }
        int height = input.shape() == BuildShape.CYLINDER
                ? clampSignedOffset(input.boxHeightOffset(), maxDimension - 1)
                : input.boxHeightOffset();
        return copy(input, b, height);
    }

    private static ShapeBuildTypes.Input clampBall(
            ShapeBuildTypes.Input input, int maxWidth, int maxHeight, int maxDepth) {
        BlockPos a = input.pointA();
        BlockPos b = input.pointB();
        long dx = (long) b.getX() - a.getX();
        long dy = (long) b.getY() - a.getY();
        long dz = (long) b.getZ() - a.getZ();
        long maxRadius = Math.max(0L, (Math.min(maxWidth, Math.min(maxHeight, maxDepth)) - 1L) / 2L);
        long radius = Math.round(Math.sqrt(dx * (double) dx + dy * (double) dy + dz * (double) dz));
        if (radius > maxRadius && radius > 0) {
            double scale = maxRadius / (double) radius;
            b = ShapeGeometryUtil.offsetPos(a,
                    toInt(Math.round(dx * scale)),
                    toInt(Math.round(dy * scale)),
                    toInt(Math.round(dz * scale)));
        }
        return copy(input, b, input.boxHeightOffset());
    }

    private static ShapeBuildTypes.Input clampBallRadius(
            ShapeBuildTypes.Input input, int maxRadius) {
        BlockPos a = input.pointA();
        BlockPos b = input.pointB();
        long dx = (long) b.getX() - a.getX();
        long dy = (long) b.getY() - a.getY();
        long dz = (long) b.getZ() - a.getZ();
        long radius = Math.round(Math.sqrt(
                dx * (double) dx + dy * (double) dy + dz * (double) dz));
        if (radius > maxRadius && radius > 0L) {
            double scale = maxRadius / (double) radius;
            b = ShapeGeometryUtil.offsetPos(a,
                    toInt(Math.round(dx * scale)),
                    toInt(Math.round(dy * scale)),
                    toInt(Math.round(dz * scale)));
        }
        return copy(input, b, input.boxHeightOffset());
    }

    private static ShapeBuildTypes.Input scaleSelection(ShapeBuildTypes.Input input, double scale) {
        BlockPos a = input.pointA();
        BlockPos b = input.pointB();
        BlockPos scaledB = new BlockPos(
                scaledCoordinate(a.getX(), b.getX(), scale),
                scaledCoordinate(a.getY(), b.getY(), scale),
                scaledCoordinate(a.getZ(), b.getZ(), scale));
        return copy(input, scaledB, scaleSignedOffset(input.boxHeightOffset(), scale));
    }

    private static int scaleSignedOffset(int offset, double scale) {
        return (int) Math.round(offset * Mth.clamp(scale, 0.0D, 1.0D));
    }

    private static int scaledCoordinate(int anchor, int target, double scale) {
        return (int) (anchor + Math.round(((long) target - anchor) * Mth.clamp(scale, 0.0D, 1.0D)));
    }

    static long envelopeVolume(ShapeBuildTypes.Input input) {
        if (input == null || input.pointA() == null || input.pointB() == null || input.shape() == null) {
            return 0L;
        }
        BlockPos a = input.pointA();
        BlockPos b = input.pointB();
        long dx = (long) b.getX() - a.getX();
        long dy = (long) b.getY() - a.getY();
        long dz = (long) b.getZ() - a.getZ();
        return switch (input.shape()) {
            case CIRCLE -> roundEnvelopeVolume(input, dx, dy, dz, false);
            case CYLINDER -> roundEnvelopeVolume(input, dx, dy, dz, true);
            case BALL -> {
                long radius = roundedSpatialRadius(dx, dy, dz);
                long diameter = (radius * 2L) + 1L;
                yield saturatedProduct(diameter, diameter, diameter);
            }
            case SQUARE -> {
                Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(input.shape(), input.planeFace());
                long axisA = Math.abs(dotDelta(dx, dy, dz, axes[0])) + 1L;
                long axisB = Math.abs(dotDelta(dx, dy, dz, axes[1])) + 1L;
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
            ShapeBuildTypes.Input input, long dx, long dy, long dz, boolean includeHeight) {
        Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(input.shape(), input.planeFace());
        long axisA = dotDelta(dx, dy, dz, axes[0]);
        long axisB = dotDelta(dx, dy, dz, axes[1]);
        long radius = Math.round(Math.sqrt(axisA * (double) axisA + axisB * (double) axisB));
        long diameter = (radius * 2L) + 1L;
        long height = includeHeight ? Math.abs((long) input.boxHeightOffset()) + 1L : 1L;
        return saturatedProduct(diameter, diameter, height);
    }

    private static long roundedSpatialRadius(long dx, long dy, long dz) {
        return Math.round(Math.sqrt(dx * (double) dx + dy * (double) dy + dz * (double) dz));
    }

    private static long dotDelta(long dx, long dy, long dz, Direction axis) {
        return dx * axis.getStepX() + dy * axis.getStepY() + dz * axis.getStepZ();
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

    private static int clampSignedOffset(long offset, int maxMagnitude) {
        long bound = Math.max(0L, maxMagnitude);
        return toInt(Math.max(-bound, Math.min(bound, offset)));
    }

    private static int toInt(long value) {
        return value <= Integer.MIN_VALUE ? Integer.MIN_VALUE
                : value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
