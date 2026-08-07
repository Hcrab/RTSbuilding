package com.rtsbuilding.rtsbuilding.client.service.destruction;

import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyPlanner;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 便捷破坏的客户端短时预览缓存。
 *
 * <p>真实算法由共享规划器提供；本类只避免每个渲染帧重复扫描树冠或区块。它不发送
 * 网络请求、不决定服务端最终目标，也不会为了预览加载区块。</p>
 */
public final class RtsDestroyPreviewPlanner {
    private static final long CACHE_MILLIS = 120L;

    private Identifier lastDimension;
    private BlockHitResult lastHit;
    private RtsConvenienceDestroyMode lastMode;
    private RtsConvenienceDestroySettings lastSettings;
    private long lastComputedAt;
    private RtsConvenienceDestroyPlanner.Plan lastPlan = new RtsConvenienceDestroyPlanner.Plan(
            RtsConvenienceDestroyPlanner.ResultCode.INVALID_TARGET, java.util.List.of(), 0);

    public RtsConvenienceDestroyPlanner.Plan preview(Minecraft minecraft,
            RtsConvenienceDestroyMode mode, BlockHitResult hit,
            RtsConvenienceDestroySettings settings) {
        if (minecraft == null || minecraft.level == null || mode == null || hit == null) {
            return new RtsConvenienceDestroyPlanner.Plan(
                    RtsConvenienceDestroyPlanner.ResultCode.INVALID_TARGET, java.util.List.of(), 0);
        }
        RtsConvenienceDestroySettings clean = RtsConvenienceDestroyPlanner.sanitize(settings);
        Identifier dimension = minecraft.level.dimension().identifier();
        long now = System.currentTimeMillis();
        if (dimension.equals(this.lastDimension)
                && sameHit(hit, this.lastHit)
                && mode == this.lastMode
                && clean.equals(this.lastSettings)
                && now - this.lastComputedAt < CACHE_MILLIS) {
            return this.lastPlan;
        }
        this.lastDimension = dimension;
        this.lastHit = hit;
        this.lastMode = mode;
        this.lastSettings = clean;
        this.lastComputedAt = now;
        this.lastPlan = RtsConvenienceDestroyPlanner.plan(
                minecraft.level, mode, hit.getBlockPos(), hit.getDirection(), clean);
        return this.lastPlan;
    }

    public void invalidate() {
        this.lastComputedAt = 0L;
        this.lastHit = null;
    }

    private static boolean sameHit(BlockHitResult left, BlockHitResult right) {
        return left != null && right != null
                && left.getBlockPos().equals(right.getBlockPos())
                && left.getDirection() == right.getDirection();
    }
}
