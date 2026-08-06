package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillCandidateClassifier;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillLimits;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlan;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 智能填坑的客户端瞬时会话。
 *
 * <p>本类只为玩家生成实时预览并保存第一次确认时的点击锚点；它不把预览坐标发送给服务器，
 * 也不认为本地扫描结果具有执行权限。第二次确认只提交锚点与参数，服务端仍会独立重规划。</p>
 */
final class SmartFillClientSession {
    private int maxBlocks = SmartFillLimits.DEFAULT_BLOCKS;
    private int diameter = SmartFillLimits.DEFAULT_DIAMETER;
    private boolean anchored;
    private BlockHitResult anchoredHit;
    private Vec3 anchoredRayOrigin;
    private Vec3 anchoredRayDirection;
    private SmartFillPlan plan = emptyPlan();
    private BlockPos lastClicked;
    private net.minecraft.core.Direction lastFace;
    private long lastGameTime = Long.MIN_VALUE;

    int maxBlocks() {
        return maxBlocks;
    }

    void maxBlocks(int value) {
        int next = clamp(value, SmartFillLimits.MIN_BLOCKS, SmartFillLimits.MAX_BLOCKS);
        if (next != this.maxBlocks) {
            this.maxBlocks = next;
            invalidateSelection();
        }
    }

    int diameter() {
        return diameter;
    }

    void diameter(int value) {
        int next = clamp(value, SmartFillLimits.MIN_DIAMETER, SmartFillLimits.MAX_DIAMETER);
        if (next != this.diameter) {
            this.diameter = next;
            invalidateSelection();
        }
    }

    ShapeDataRecords.GhostPreview preview(Minecraft minecraft, BlockHitResult cursorHit) {
        refresh(minecraft, cursorHit, false);
        return plan.targets().isEmpty()
                ? ShapeDataRecords.GhostPreview.EMPTY
                : new ShapeDataRecords.GhostPreview(plan.targets(), anchored);
    }

    SmartFillPlan plan(Minecraft minecraft, BlockHitResult cursorHit) {
        refresh(minecraft, cursorHit, false);
        return plan;
    }

    boolean submitOrAnchor(
            Minecraft minecraft,
            BlockHitResult cursorHit,
            Vec3 rayOrigin,
            Vec3 rayDirection,
            Submitter submitter) {
        if (anchored) {
            if (anchoredHit != null && plan.canSubmit()) {
                submitter.submit(
                        anchoredHit,
                        maxBlocks,
                        diameter,
                        anchoredRayOrigin == null ? rayOrigin : anchoredRayOrigin,
                        anchoredRayDirection == null ? rayDirection : anchoredRayDirection);
            }
            clearAnchor();
            return true;
        }
        refresh(minecraft, cursorHit, true);
        if (cursorHit != null && plan.canSubmit()) {
            this.anchored = true;
            this.anchoredHit = cursorHit;
            this.anchoredRayOrigin = rayOrigin;
            this.anchoredRayDirection = rayDirection;
        }
        return true;
    }

    boolean cancelAnchor() {
        if (!anchored) {
            return false;
        }
        clearAnchor();
        return true;
    }

    boolean anchored() {
        return anchored;
    }

    void clear() {
        this.anchored = false;
        this.anchoredHit = null;
        this.anchoredRayOrigin = null;
        this.anchoredRayDirection = null;
        this.plan = emptyPlan();
        this.lastClicked = null;
        this.lastFace = null;
        this.lastGameTime = Long.MIN_VALUE;
    }

    private void clearAnchor() {
        this.anchored = false;
        this.anchoredHit = null;
        this.anchoredRayOrigin = null;
        this.anchoredRayDirection = null;
        this.lastGameTime = Long.MIN_VALUE;
    }

    private void invalidateSelection() {
        clear();
    }

    private void refresh(Minecraft minecraft, BlockHitResult cursorHit, boolean force) {
        if (anchored) {
            return;
        }
        if (minecraft == null || minecraft.level == null || cursorHit == null) {
            this.plan = emptyPlan();
            this.lastClicked = null;
            this.lastFace = null;
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        BlockPos clicked = cursorHit.getBlockPos();
        // 同一锚点最多每两 tick 重算一次，避免大型候选区在渲染帧中反复扫描。
        if (!force && gameTime >= lastGameTime && gameTime - lastGameTime < 2
                && clicked.equals(lastClicked) && cursorHit.getDirection() == lastFace) {
            return;
        }
        this.plan = SmartFillPlanner.plan(
                clicked,
                cursorHit.getDirection(),
                new SmartFillPlanner.Limits(
                        maxBlocks,
                        diameter,
                        SmartFillLimits.HARD_MAX_BLOCKS,
                        SmartFillLimits.QUERY_BUDGET),
                pos -> SmartFillCandidateClassifier.classify(minecraft.level, pos));
        this.lastClicked = clicked.immutable();
        this.lastFace = cursorHit.getDirection();
        this.lastGameTime = gameTime;
    }

    private static SmartFillPlan emptyPlan() {
        return new SmartFillPlan(
                SmartFillPlan.Status.INVALID_START,
                BlockPos.ZERO,
                java.util.List.of(),
                null,
                0,
                0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @FunctionalInterface
    interface Submitter {
        void submit(
                BlockHitResult hit,
                int maxBlocks,
                int diameter,
                Vec3 rayOrigin,
                Vec3 rayDirection);
    }
}
