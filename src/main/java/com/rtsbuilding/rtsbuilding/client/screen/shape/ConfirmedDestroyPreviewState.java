package com.rtsbuilding.rtsbuilding.client.screen.shape;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * 已确认范围破坏与连锁破坏工作区的客户端生命周期 owner。
 * <p>
 * 本类负责保存不可变预览、根据服务端进度续期、按超时或世界目标消失清理，以及裁掉
 * 服务端明确拒绝的坐标。它不读取 Minecraft 世界、控制器、配置或网络；调用方只需传入
 * 当前进度快照和“该坐标是否仍是活目标”的判断。时钟同样可注入，保证超时规则能被
 * 确定性单测覆盖。
 * <p>
 * 普通范围破坏和连锁破坏故意各只保留一个工作区，因为服务端执行模型也是单队列；
 * 同时显示多个已确认区域会错误暗示它们正在并行执行。
 */
public final class ConfirmedDestroyPreviewState {
    private static final long INITIAL_HOLD_MS = 2500L;
    private static final long ACTIVE_PROGRESS_HOLD_MS = 850L;

    private final LongSupplier clock;
    private ShapeDataRecords.GhostPreview rangePreview = ShapeDataRecords.GhostPreview.EMPTY;
    private long rangePreviewUntilMs;
    private ShapeDataRecords.GhostPreview chainPreview = ShapeDataRecords.GhostPreview.EMPTY;
    private long chainPreviewUntilMs;

    public ConfirmedDestroyPreviewState() {
        this(System::currentTimeMillis);
    }

    ConfirmedDestroyPreviewState(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 当前破坏进度的最小只读快照。
     *
     * @param position              服务端当前报告的方块
     * @param mineStage             单块/批量挖掘阶段，负数表示无活动进度
     * @param activeDestroyWorkflow 是否存在带目标数量的活动破坏工作流
     */
    public static final class Progress {
        private final BlockPos position;
        private final int mineStage;
        private final boolean activeDestroyWorkflow;

        public Progress(BlockPos position, int mineStage, boolean activeDestroyWorkflow) {
            this.position = position;
            this.mineStage = mineStage;
            this.activeDestroyWorkflow = activeDestroyWorkflow;
        }

        public BlockPos position() { return this.position; }
        public int mineStage() { return this.mineStage; }
        public boolean activeDestroyWorkflow() { return this.activeDestroyWorkflow; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Progress)) return false;
            Progress that = (Progress) other;
            return this.mineStage == that.mineStage && this.activeDestroyWorkflow == that.activeDestroyWorkflow
                    && java.util.Objects.equals(this.position, that.position);
        }
        @Override public int hashCode() { return java.util.Objects.hash(this.position, this.mineStage, this.activeDestroyWorkflow); }
    }

    public void rememberRange(List<BlockPos> breakableBlocks, List<BlockPos> envelopeBlocks) {
        List<BlockPos> blocks = copyImmutableBlocks(breakableBlocks);
        if (blocks.isEmpty()) {
            return;
        }
        this.rangePreview = new ShapeDataRecords.GhostPreview(
                blocks,
                true,
                true,
                copyImmutableBlocks(envelopeBlocks),
                false,
                true);
        this.rangePreviewUntilMs = this.clock.getAsLong() + INITIAL_HOLD_MS;
    }

    public void rememberChain(List<BlockPos> blocks) {
        List<BlockPos> copied = copyImmutableBlocks(blocks);
        if (copied.isEmpty()) {
            clearChain();
            return;
        }
        this.chainPreview = new ShapeDataRecords.GhostPreview(
                copied,
                true,
                true,
                java.util.Collections.emptyList(),
                true,
                true);
        this.chainPreviewUntilMs = this.clock.getAsLong() + INITIAL_HOLD_MS;
    }

    public void clearChain() {
        this.chainPreview = ShapeDataRecords.GhostPreview.EMPTY;
        this.chainPreviewUntilMs = 0L;
    }

    public ShapeDataRecords.GhostPreview activeRange(
            Progress progress,
            Predicate<BlockPos> liveTarget) {
        ShapeDataRecords.GhostPreview preview = this.rangePreview;
        if (!isPopulated(preview) || !hasAnyLiveTarget(preview, liveTarget)) {
            clearRange();
            return ShapeDataRecords.GhostPreview.EMPTY;
        }

        long now = this.clock.getAsLong();
        boolean containsProgress = contains(preview, progress == null ? null : progress.position());
        boolean miningProgressBelongsHere =
                progress != null && containsProgress && progress.mineStage() >= 0;
        boolean workflowBelongsHere =
                progress != null && containsProgress && progress.activeDestroyWorkflow();
        if (miningProgressBelongsHere || workflowBelongsHere) {
            this.rangePreviewUntilMs = now + ACTIVE_PROGRESS_HOLD_MS;
            return preview;
        }
        if (now <= this.rangePreviewUntilMs) {
            return preview;
        }

        clearRange();
        return ShapeDataRecords.GhostPreview.EMPTY;
    }

    public ShapeDataRecords.GhostPreview activeChain(
            Progress progress,
            Predicate<BlockPos> liveTarget) {
        ShapeDataRecords.GhostPreview preview = this.chainPreview;
        if (!isPopulated(preview) || !hasAnyLiveTarget(preview, liveTarget)) {
            clearChain();
            return ShapeDataRecords.GhostPreview.EMPTY;
        }

        long now = this.clock.getAsLong();
        BlockPos progressPos = progress == null ? null : progress.position();
        boolean containsProgress = contains(preview, progressPos);
        boolean hasForeignProgress =
                progress != null
                        && progressPos != null
                        && progress.mineStage() >= 0
                        && !containsProgress;
        if (hasForeignProgress) {
            clearChain();
            return ShapeDataRecords.GhostPreview.EMPTY;
        }

        boolean miningProgressBelongsHere =
                progress != null && containsProgress && progress.mineStage() >= 0;
        boolean workflowBelongsHere =
                progress != null && containsProgress && progress.activeDestroyWorkflow();
        if (miningProgressBelongsHere || workflowBelongsHere) {
            this.chainPreviewUntilMs = now + ACTIVE_PROGRESS_HOLD_MS;
            return preview;
        }
        if (now <= this.chainPreviewUntilMs) {
            return preview;
        }

        clearChain();
        return ShapeDataRecords.GhostPreview.EMPTY;
    }

    public List<ShapeDataRecords.GhostPreview> activeRanges(
            Progress progress,
            Predicate<BlockPos> liveTarget) {
        ShapeDataRecords.GhostPreview preview = activeRange(progress, liveTarget);
        return preview == ShapeDataRecords.GhostPreview.EMPTY ? java.util.Collections.emptyList() : java.util.Collections.singletonList(preview);
    }

    public boolean hasAnyActive(
            Progress progress,
            Predicate<BlockPos> liveTarget) {
        return activeRange(progress, liveTarget) != ShapeDataRecords.GhostPreview.EMPTY
                || activeChain(progress, liveTarget) != ShapeDataRecords.GhostPreview.EMPTY;
    }

    public void removeRangeBlocks(List<BlockPos> skippedPositions) {
        this.rangePreview = prune(this.rangePreview, skippedPositions);
        if (this.rangePreview == ShapeDataRecords.GhostPreview.EMPTY) {
            this.rangePreviewUntilMs = 0L;
        }
    }

    /**
     * 从已确认预览中删除服务端拒绝的坐标，并保留所有渲染语义标志。
     */
    public static ShapeDataRecords.GhostPreview prune(
            ShapeDataRecords.GhostPreview preview,
            List<BlockPos> skippedPositions) {
        if (preview == null
                || preview == ShapeDataRecords.GhostPreview.EMPTY
                || preview.blocks() == null
                || preview.blocks().isEmpty()
                || skippedPositions == null
                || skippedPositions.isEmpty()) {
            return preview == null ? ShapeDataRecords.GhostPreview.EMPTY : preview;
        }

        Set<Long> skippedKeys = new HashSet<>();
        for (BlockPos pos : skippedPositions) {
            if (pos != null) {
                skippedKeys.add(pos.toLong());
            }
        }
        if (skippedKeys.isEmpty()) {
            return preview;
        }

        List<BlockPos> remainingBlocks = preview.blocks().stream()
                .filter(Objects::nonNull)
                .filter(pos -> !skippedKeys.contains(pos.toLong()))
                .collect(java.util.stream.Collectors.toList());
        if (remainingBlocks.size() == preview.blocks().size()) {
            return preview;
        }
        if (remainingBlocks.isEmpty()) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        List<BlockPos> remainingEmptyBlocks = preview.emptyBlocks() == null
                ? java.util.Collections.emptyList()
                : preview.emptyBlocks().stream()
                        .filter(Objects::nonNull)
                        .filter(pos -> !skippedKeys.contains(pos.toLong()))
                        .collect(java.util.stream.Collectors.toList());
        return new ShapeDataRecords.GhostPreview(
                remainingBlocks,
                preview.readyConfirm(),
                preview.destructive(),
                remainingEmptyBlocks,
                preview.chainDestroyPreview(),
                preview.confirmedWorkArea());
    }

    private static boolean isPopulated(ShapeDataRecords.GhostPreview preview) {
        return preview != null
                && preview != ShapeDataRecords.GhostPreview.EMPTY
                && (!preview.blocks().isEmpty() || !preview.emptyBlocks().isEmpty());
    }

    private static boolean hasAnyLiveTarget(
            ShapeDataRecords.GhostPreview preview,
            Predicate<BlockPos> liveTarget) {
        if (preview.blocks().isEmpty()) {
            return false;
        }
        Predicate<BlockPos> safePredicate = liveTarget == null ? ignored -> true : liveTarget;
        for (BlockPos pos : preview.blocks()) {
            if (pos != null && safePredicate.test(pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(ShapeDataRecords.GhostPreview preview, BlockPos pos) {
        if (preview == null || pos == null) {
            return false;
        }
        return contains(preview.blocks(), pos) || contains(preview.emptyBlocks(), pos);
    }

    private static boolean contains(List<BlockPos> blocks, BlockPos pos) {
        if (blocks == null || pos == null) {
            return false;
        }
        for (BlockPos block : blocks) {
            if (pos.equals(block)) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> copyImmutableBlocks(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return blocks.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
    }

    private void clearRange() {
        this.rangePreview = ShapeDataRecords.GhostPreview.EMPTY;
        this.rangePreviewUntilMs = 0L;
    }

}
