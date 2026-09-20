package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 范围破坏选区的纯尺寸、体积与方块列表限制器。
 *
 * <p>本类只消费已经解析好的上限，不读取配置、不访问世界，也不拥有预览缓存或确认状态。
 * ScreenShapeController 负责选择当前业务上限，本类负责让输入、包围盒和最终位置列表遵守
 * 同一套规则。</p>
 */
public final class RangeDestroySelectionLimiter {
    private RangeDestroySelectionLimiter() {
    }

    public static ShapeBuildTypes.Input clampInput(
            ShapeBuildTypes.Input input,
            Limits limits) {
        Limits safe = Limits.safe(limits);
        return ShapeSelectionLimiter.clampDimensionsAndVolume(
                input,
                safe.maxWidth(),
                safe.maxHeight(),
                safe.maxDepth(),
                safe.maxVolume());
    }

    public static ShapeBuildTypes.Input clampDimensions(
            ShapeBuildTypes.Input input,
            Limits limits) {
        Limits safe = Limits.safe(limits);
        return ShapeSelectionLimiter.clampDimensionsAndVolume(
                input, safe.maxWidth(), safe.maxHeight(), safe.maxDepth(), safe.maxVolume());
    }

    public static boolean contains(RtsCullingBox box, Limits limits) {
        if (box == null) {
            return false;
        }
        Limits safe = Limits.safe(limits);
        return safe.accepts(
                span(box.min().getX(), box.max().getX()),
                span(box.min().getY(), box.max().getY()),
                span(box.min().getZ(), box.max().getZ()));
    }

    public static List<BlockPos> clampRoundPositions(
            ShapeBuildTypes.Input input,
            List<BlockPos> positions,
            Limits limits) {
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        Limits safe = Limits.safe(limits);
        if (envelopeFits(positions, safe)) {
            List<BlockPos> copy = new ArrayList<>(positions.size());
            for (BlockPos pos : positions) {
                if (pos != null) {
                    copy.add(pos.immutable());
                }
            }
            return copy;
        }
        return clampPositions(input, positions, safe);
    }

    public static List<BlockPos> clampPositions(
            ShapeBuildTypes.Input input,
            List<BlockPos> positions,
            Limits limits) {
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (minX == Integer.MAX_VALUE) {
            return List.of();
        }

        BlockPos anchor = input != null && input.pointA() != null
                ? input.pointA()
                : new BlockPos(minX, minY, minZ);
        RtsCullingBox limited = clampBox(
                new RtsCullingBox(
                        0,
                        new BlockPos(minX, minY, minZ),
                        new BlockPos(maxX, maxY, maxZ)),
                anchor,
                limits);
        List<BlockPos> clamped = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (pos != null && limited.contains(pos)) {
                clamped.add(pos.immutable());
            }
        }
        return clamped;
    }

    public static RtsCullingBox clampBox(
            RtsCullingBox box,
            BlockPos anchor,
            Limits limits) {
        if (box == null || anchor == null) {
            return box;
        }
        Limits safe = Limits.safe(limits);
        AxisBounds x = AxisBounds.normalized(box.min().getX(), box.max().getX());
        AxisBounds y = AxisBounds.normalized(box.min().getY(), box.max().getY());
        AxisBounds z = AxisBounds.normalized(box.min().getZ(), box.max().getZ());
        MiningLimits.Dimensions dimensions = MiningLimits.clampDimensions(
                (int) Math.min(x.length(), safe.maxWidth()),
                (int) Math.min(y.length(), safe.maxHeight()),
                (int) Math.min(z.length(), safe.maxDepth()), safe.maxVolume());
        dimensions = new MiningLimits.Dimensions(
                Math.min(dimensions.width(), safe.maxWidth()),
                Math.min(dimensions.height(), safe.maxHeight()),
                Math.min(dimensions.depth(), safe.maxDepth()));
        x = x.shrinkToLength(dimensions.width(), anchor.getX());
        y = y.shrinkToLength(dimensions.height(), anchor.getY());
        z = z.shrinkToLength(dimensions.depth(), anchor.getZ());
        return new RtsCullingBox(
                box.id(),
                new BlockPos(x.min(), y.min(), z.min()),
                new BlockPos(x.max(), y.max(), z.max()));
    }

    private static boolean envelopeFits(
            List<BlockPos> positions,
            Limits limits) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int count = 0;
        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            count++;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (count == 0) {
            return true;
        }
        return count <= limits.maxVolume()
                && limits.accepts(span(minX, maxX), span(minY, maxY), span(minZ, maxZ));
    }

    public record Limits(
            int maxWidth,
            int maxHeight,
            int maxDepth,
            int maxVolume) {
        public Limits {
            maxWidth = Math.max(1, maxWidth);
            maxHeight = Math.max(1, maxHeight);
            maxDepth = Math.max(1, maxDepth);
            maxVolume = com.rtsbuilding.rtsbuilding.common.mining.MiningLimits.clampVolume(maxVolume);
        }

        /** 新配置只有共同体积；保留旧单参数构造器供已有预览缓存调用。 */
        public Limits(int maxVolume) {
            this(maxVolume, maxVolume, maxVolume, maxVolume);
        }

        boolean accepts(long width, long height, long depth) {
            return width > 0 && height > 0 && depth > 0
                    && width <= maxWidth && height <= maxHeight && depth <= maxDepth
                    && com.rtsbuilding.rtsbuilding.common.mining.MiningLimits.fitsVolume(width, height, depth, maxVolume);
        }

        private static Limits safe(Limits limits) {
            return limits == null
                    ? new Limits(1, 1, 1, 1)
                    : limits;
        }
    }

    private record AxisBounds(int min, int max) {
        static AxisBounds normalized(int first, int second) {
            return first <= second
                    ? new AxisBounds(first, second)
                    : new AxisBounds(second, first);
        }

        long length() {
            return (long) max - min + 1L;
        }

        AxisBounds shrinkToLength(long requestedLength, int anchor) {
            long target = Math.max(1L, Math.min(requestedLength, length()));
            if (target >= length()) {
                return this;
            }
            long pivot = Math.max((long) min, Math.min((long) max, anchor));
            long leftAvailable = pivot - min;
            long rightAvailable = max - pivot;
            long left = Math.min(leftAvailable, target / 2L);
            long right = Math.min(rightAvailable, target - 1L - left);
            long spare = target - 1L - left - right;
            if (spare > 0L) {
                long extraLeft = Math.min(spare, leftAvailable - left);
                left += extraLeft;
                spare -= extraLeft;
            }
            if (spare > 0L) {
                right += Math.min(spare, rightAvailable - right);
            }
            return new AxisBounds(toInt(pivot - left), toInt(pivot + right));
        }
    }

    private static long span(int min, int max) {
        return (long) Math.max(min, max) - Math.min(min, max) + 1L;
    }

    /** 普通建造保留原有轴长规则；它不是范围挖掘配置，不应随本轮迁移一起放开。 */
    public static RtsCullingBox clampBoxDimensions(RtsCullingBox box, BlockPos anchor, int maxDimension) {
        if (box == null || anchor == null) return box;
        AxisBounds x = AxisBounds.normalized(box.min().getX(), box.max().getX())
                .shrinkToLength(maxDimension, anchor.getX());
        AxisBounds y = AxisBounds.normalized(box.min().getY(), box.max().getY())
                .shrinkToLength(maxDimension, anchor.getY());
        AxisBounds z = AxisBounds.normalized(box.min().getZ(), box.max().getZ())
                .shrinkToLength(maxDimension, anchor.getZ());
        return new RtsCullingBox(box.id(), new BlockPos(x.min(), y.min(), z.min()),
                new BlockPos(x.max(), y.max(), z.max()));
    }

    private static int toInt(long value) {
        return value <= Integer.MIN_VALUE ? Integer.MIN_VALUE
                : value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
