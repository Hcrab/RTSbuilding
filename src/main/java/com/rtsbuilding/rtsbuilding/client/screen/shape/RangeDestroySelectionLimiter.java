package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
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
        return ShapeSelectionLimiter.clampDimensions(
                input,
                safe.maxWidth(),
                safe.maxHeight(),
                safe.maxDepth());
    }

    public static boolean contains(RtsCullingBox box, Limits limits) {
        if (box == null) {
            return false;
        }
        Limits safe = Limits.safe(limits);
        return box.width() <= safe.maxWidth()
                && box.height() <= safe.maxHeight()
                && box.depth() <= safe.maxDepth()
                && (long) box.width() * box.height() * box.depth()
                <= safe.maxVolume();
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
        AxisBounds x = clampAxisAroundAnchor(
                box.min().getX(),
                box.max().getX(),
                anchor.getX(),
                safe.maxWidth());
        AxisBounds y = clampAxisAroundAnchor(
                box.min().getY(),
                box.max().getY(),
                anchor.getY(),
                safe.maxHeight());
        AxisBounds z = clampAxisAroundAnchor(
                box.min().getZ(),
                box.max().getZ(),
                anchor.getZ(),
                safe.maxDepth());
        while ((long) x.length() * y.length() * z.length()
                > safe.maxVolume()) {
            if (y.length() >= x.length()
                    && y.length() >= z.length()
                    && y.length() > 1) {
                y = y.shrinkToward(anchor.getY());
            } else if (x.length() >= z.length() && x.length() > 1) {
                x = x.shrinkToward(anchor.getX());
            } else if (z.length() > 1) {
                z = z.shrinkToward(anchor.getZ());
            } else {
                break;
            }
        }
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
                && maxX - minX + 1 <= limits.maxWidth()
                && maxY - minY + 1 <= limits.maxHeight()
                && maxZ - minZ + 1 <= limits.maxDepth();
    }

    private static AxisBounds clampAxisAroundAnchor(
            int min,
            int max,
            int anchor,
            int maxLength) {
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
        int safeMaxLength = Math.max(1, maxLength);
        int length = max - min + 1;
        if (length <= safeMaxLength) {
            return new AxisBounds(min, max);
        }
        if (anchor <= min) {
            return new AxisBounds(min, min + safeMaxLength - 1);
        }
        if (anchor >= max) {
            return new AxisBounds(max - safeMaxLength + 1, max);
        }
        int leftAvailable = anchor - min;
        int rightAvailable = max - anchor;
        int left = Math.min(leftAvailable, safeMaxLength / 2);
        int right = Math.min(
                rightAvailable,
                safeMaxLength - 1 - left);
        int spare = safeMaxLength - 1 - left - right;
        if (spare > 0) {
            int moreLeft = Math.min(spare, leftAvailable - left);
            left += moreLeft;
            spare -= moreLeft;
        }
        if (spare > 0) {
            right += Math.min(spare, rightAvailable - right);
        }
        return new AxisBounds(anchor - left, anchor + right);
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
            maxVolume = Math.max(1, maxVolume);
        }

        private static Limits safe(Limits limits) {
            return limits == null
                    ? new Limits(1, 1, 1, 1)
                    : limits;
        }
    }

    private record AxisBounds(int min, int max) {
        int length() {
            return max - min + 1;
        }

        AxisBounds shrinkToward(int anchor) {
            if (length() <= 1) {
                return this;
            }
            if (anchor <= min) {
                return new AxisBounds(min, max - 1);
            }
            if (anchor >= max) {
                return new AxisBounds(min + 1, max);
            }
            return max - anchor >= anchor - min
                    ? new AxisBounds(min, max - 1)
                    : new AxisBounds(min + 1, max);
        }
    }
}
