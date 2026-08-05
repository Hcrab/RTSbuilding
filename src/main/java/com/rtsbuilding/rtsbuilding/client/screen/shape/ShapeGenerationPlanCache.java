package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

import java.util.List;

/**
 * 形状几何计划的纯生成与单项缓存 owner。
 * <p>
 * 本类统一执行普通建造、高级选区和范围破坏的输入限幅、几何分派、范围破坏末级钳制，
 * 并缓存最终不可变坐标列表及其包围盒。它不读取屏幕、配置、鼠标、世界方块、物品或网络；
 * 调用方必须把当前模式、限制和高级选区作为 {@link Request} 明确传入。
 * <p>
 * 缓存只保存最近一个完整计划，目的是让尺寸文案、成本统计、预览渲染和确认动作在同一
 * 状态下复用坐标，而不是成为跨会话的全局缓存。会话清理时必须调用 {@link #clear()}。
 */
public final class ShapeGenerationPlanCache {
    private Key cachedKey;
    private List<BlockPos> cachedPositions = java.util.Collections.emptyList();
    private RtsCullingBox cachedBounds;

    /**
     * 生成计划所需的完整只读输入。
     *
     * @param input             原始形状输入
     * @param fillMode          当前填充模式
     * @param advancedBox       高级选区；普通两/三点模式传 {@code null}
     * @param rangeDestroy      是否按范围破坏限制生成
     * @param rangeLimits       范围破坏限制
     * @param buildMaxDimension 普通范围建造的单轴上限
     */
    public static final class Request {
        private final ShapeBuildTypes.Input input;
        private final ShapeFillMode fillMode;
        private final RtsCullingBox advancedBox;
        private final boolean rangeDestroy;
        private final RangeDestroySelectionLimiter.Limits rangeLimits;
        private final int buildMaxDimension;

        public Request(ShapeBuildTypes.Input input, ShapeFillMode fillMode, RtsCullingBox advancedBox,
                boolean rangeDestroy, RangeDestroySelectionLimiter.Limits rangeLimits, int buildMaxDimension) {
            this.input = input;
            this.fillMode = fillMode;
            this.advancedBox = advancedBox;
            this.rangeDestroy = rangeDestroy;
            this.rangeLimits = rangeLimits;
            this.buildMaxDimension = buildMaxDimension;
        }

        public ShapeBuildTypes.Input input() { return this.input; }
        public ShapeFillMode fillMode() { return this.fillMode; }
        public RtsCullingBox advancedBox() { return this.advancedBox; }
        public boolean rangeDestroy() { return this.rangeDestroy; }
        public RangeDestroySelectionLimiter.Limits rangeLimits() { return this.rangeLimits; }
        public int buildMaxDimension() { return this.buildMaxDimension; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Request)) return false;
            Request that = (Request) other;
            return this.rangeDestroy == that.rangeDestroy && this.buildMaxDimension == that.buildMaxDimension
                    && java.util.Objects.equals(this.input, that.input) && this.fillMode == that.fillMode
                    && java.util.Objects.equals(this.advancedBox, that.advancedBox)
                    && java.util.Objects.equals(this.rangeLimits, that.rangeLimits);
        }
        @Override public int hashCode() { return java.util.Objects.hash(this.input, this.fillMode,
                this.advancedBox, this.rangeDestroy, this.rangeLimits, this.buildMaxDimension); }
    }

    public List<BlockPos> positions(Request request) {
        if (request == null || request.input() == null) {
            return java.util.Collections.emptyList();
        }

        ShapeFillMode fillMode =
                request.fillMode() == null ? ShapeFillMode.FILL : request.fillMode();
        int buildMaxDimension = Math.max(1, request.buildMaxDimension());
        RangeDestroySelectionLimiter.Limits rangeLimits = request.rangeLimits() == null
                ? new RangeDestroySelectionLimiter.Limits(1, 1, 1, 1)
                : request.rangeLimits();
        int maxWidth = request.rangeDestroy() ? rangeLimits.maxWidth() : buildMaxDimension;
        int maxHeight = request.rangeDestroy() ? rangeLimits.maxHeight() : buildMaxDimension;
        int maxDepth = request.rangeDestroy() ? rangeLimits.maxDepth() : buildMaxDimension;
        int maxVolume = request.rangeDestroy()
                ? rangeLimits.maxVolume()
                : saturatedCube(buildMaxDimension);
        ShapeBuildTypes.Input effectiveInput = request.rangeDestroy()
                ? RangeDestroySelectionLimiter.clampInput(request.input(), rangeLimits)
                : ShapeSelectionLimiter.clampDimensions(
                        request.input(),
                        maxWidth,
                        maxHeight,
                        maxDepth);

        Key key = new Key(
                effectiveInput,
                fillMode,
                request.advancedBox(),
                request.rangeDestroy(),
                maxWidth,
                maxHeight,
                maxDepth,
                maxVolume);
        if (key.equals(this.cachedKey)) {
            return this.cachedPositions;
        }

        List<BlockPos> positions;
        if (request.advancedBox() != null) {
            positions = ShapeGeometryUtil.buildAdvancedShapePositions(
                    effectiveInput.shape(),
                    request.advancedBox(),
                    fillMode,
                    effectiveInput.planeFace());
        } else if (request.rangeDestroy()) {
            positions = ShapeGeometryUtil.buildRangeDestroyShapePositions(
                    effectiveInput,
                    fillMode);
        } else {
            positions = ShapeGeometryUtil.buildShapePositions(
                    effectiveInput,
                    fillMode);
        }
        if (request.rangeDestroy()) {
            positions = isRoundShape(effectiveInput.shape())
                    ? RangeDestroySelectionLimiter.clampRoundPositions(
                            effectiveInput,
                            positions,
                            rangeLimits)
                    : RangeDestroySelectionLimiter.clampPositions(
                            effectiveInput,
                            positions,
                            rangeLimits);
        }

        this.cachedKey = key;
        this.cachedPositions = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(positions));
        this.cachedBounds = boundsOf(this.cachedPositions);
        return this.cachedPositions;
    }

    public RtsCullingBox bounds() {
        return this.cachedBounds;
    }

    public void clear() {
        this.cachedKey = null;
        this.cachedPositions = java.util.Collections.emptyList();
        this.cachedBounds = null;
    }

    private static boolean isRoundShape(BuildShape shape) {
        return shape == BuildShape.CIRCLE
                || shape == BuildShape.CYLINDER
                || shape == BuildShape.BALL;
    }

    private static RtsCullingBox boundsOf(List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean found = false;
        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            found = true;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return found
                ? new RtsCullingBox(
                        0,
                        new BlockPos(minX, minY, minZ),
                        new BlockPos(maxX, maxY, maxZ))
                : null;
    }

    private static int saturatedCube(int value) {
        long cube = (long) value * value * value;
        return cube > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cube;
    }

    private static final class Key {
        private final ShapeBuildTypes.Input input;
        private final ShapeFillMode fillMode;
        private final RtsCullingBox advancedBox;
        private final boolean rangeDestroy;
        private final int maxWidth;
        private final int maxHeight;
        private final int maxDepth;
        private final int maxVolume;

        private Key(ShapeBuildTypes.Input input, ShapeFillMode fillMode, RtsCullingBox advancedBox,
                boolean rangeDestroy, int maxWidth, int maxHeight, int maxDepth, int maxVolume) {
            this.input = input;
            this.fillMode = fillMode;
            this.advancedBox = advancedBox;
            this.rangeDestroy = rangeDestroy;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
            this.maxDepth = maxDepth;
            this.maxVolume = maxVolume;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key)) return false;
            Key that = (Key) other;
            return this.rangeDestroy == that.rangeDestroy && this.maxWidth == that.maxWidth
                    && this.maxHeight == that.maxHeight && this.maxDepth == that.maxDepth
                    && this.maxVolume == that.maxVolume && java.util.Objects.equals(this.input, that.input)
                    && this.fillMode == that.fillMode && java.util.Objects.equals(this.advancedBox, that.advancedBox);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(this.input, this.fillMode, this.advancedBox,
                    this.rangeDestroy, this.maxWidth, this.maxHeight, this.maxDepth, this.maxVolume);
        }
    }
}
