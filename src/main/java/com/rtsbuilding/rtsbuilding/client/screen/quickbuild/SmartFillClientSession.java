package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillCandidateClassifier;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillLimits;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlan;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/**
 * 智能填坑的客户端两次确认会话。
 *
 * <p>第一次点击只锁定锚点，第二次点击只发送锚点与参数；本地规划仅用于预览，绝不授予
 * 客户端放置坐标的执行权。锚定后不会因镜头移动改变预览。</p>
 */
final class SmartFillClientSession {
    interface Submitter {
        void submit(RayTraceResult hit, int maxBlocks, int diameter,
                Vec3d rayOrigin, Vec3d rayDirection);
    }

    private int maxBlocks = SmartFillLimits.DEFAULT_BLOCKS;
    private int diameter = SmartFillLimits.DEFAULT_DIAMETER;
    private boolean anchored;
    private RayTraceResult anchoredHit;
    private Vec3d anchoredRayOrigin;
    private Vec3d anchoredRayDirection;
    private SmartFillPlan plan = emptyPlan();
    private BlockPos lastClicked;
    private EnumFacing lastFace;
    private long lastWorldTime = Long.MIN_VALUE;

    int maxBlocks() { return this.maxBlocks; }
    int diameter() { return this.diameter; }
    boolean anchored() { return this.anchored; }

    void maxBlocks(int value) {
        int next = clamp(value, SmartFillLimits.MIN_BLOCKS, SmartFillLimits.MAX_BLOCKS);
        if (next != this.maxBlocks) {
            this.maxBlocks = next;
            clear();
        }
    }

    void diameter(int value) {
        int next = clamp(value, SmartFillLimits.MIN_DIAMETER, SmartFillLimits.MAX_DIAMETER);
        if (next != this.diameter) {
            this.diameter = next;
            clear();
        }
    }

    ShapeDataRecords.GhostPreview preview(Minecraft minecraft, RayTraceResult cursorHit) {
        refresh(minecraft, cursorHit, false);
        return this.plan.targets().isEmpty() ? ShapeDataRecords.GhostPreview.EMPTY
                : new ShapeDataRecords.GhostPreview(this.plan.targets(), this.anchored);
    }

    SmartFillPlan plan(Minecraft minecraft, RayTraceResult cursorHit) {
        refresh(minecraft, cursorHit, false);
        return this.plan;
    }

    boolean submitOrAnchor(Minecraft minecraft, RayTraceResult hit, Vec3d rayOrigin,
            Vec3d rayDirection, Submitter submitter) {
        if (this.anchored) {
            if (this.anchoredHit != null && this.plan.canSubmit() && submitter != null) {
                submitter.submit(this.anchoredHit, this.maxBlocks, this.diameter,
                        this.anchoredRayOrigin == null ? rayOrigin : this.anchoredRayOrigin,
                        this.anchoredRayDirection == null ? rayDirection : this.anchoredRayDirection);
            }
            clear();
            return true;
        }
        refresh(minecraft, hit, true);
        if (hit != null && this.plan.canSubmit()) {
            this.anchored = true;
            this.anchoredHit = copyHit(hit);
            this.anchoredRayOrigin = rayOrigin;
            this.anchoredRayDirection = rayDirection;
        }
        return true;
    }

    boolean cancelAnchor() {
        if (!this.anchored) return false;
        clear();
        return true;
    }

    void clear() {
        this.anchored = false;
        this.anchoredHit = null;
        this.anchoredRayOrigin = null;
        this.anchoredRayDirection = null;
        this.plan = emptyPlan();
        this.lastClicked = null;
        this.lastFace = null;
        this.lastWorldTime = Long.MIN_VALUE;
    }

    private void refresh(Minecraft minecraft, RayTraceResult hit, boolean force) {
        if (this.anchored) return;
        if (minecraft == null || minecraft.world == null || hit == null
                || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.sideHit == null) {
            this.plan = emptyPlan();
            this.lastClicked = null;
            this.lastFace = null;
            return;
        }
        long worldTime = minecraft.world.getTotalWorldTime();
        BlockPos clicked = hit.getBlockPos();
        if (!force && clicked.equals(this.lastClicked) && hit.sideHit == this.lastFace
                && worldTime >= this.lastWorldTime && worldTime - this.lastWorldTime < 2L) {
            return;
        }
        this.plan = SmartFillPlanner.plan(clicked, hit.sideHit,
                new SmartFillPlanner.Limits(this.maxBlocks, this.diameter,
                        SmartFillLimits.HARD_MAX_BLOCKS, SmartFillLimits.QUERY_BUDGET),
                pos -> SmartFillCandidateClassifier.classify(minecraft.world, pos));
        this.lastClicked = clicked.toImmutable();
        this.lastFace = hit.sideHit;
        this.lastWorldTime = worldTime;
    }

    private static RayTraceResult copyHit(RayTraceResult value) {
        return new RayTraceResult(value.hitVec, value.sideHit, value.getBlockPos());
    }

    private static SmartFillPlan emptyPlan() {
        return new SmartFillPlan(SmartFillPlan.Status.INVALID_START, BlockPos.ORIGIN,
                java.util.Collections.<BlockPos>emptyList(), null, 0, 0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
