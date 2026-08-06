package com.rtsbuilding.rtsbuilding.client.service.destruction;

import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyPlanner;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

/**
 * 便捷破坏的客户端短时预览缓存。
 *
 * <p>它只避免每帧重复扫描树冠或区块；不会加载区块、发包或取代服务端 AREA_DESTROY 校验。</p>
 */
public final class RtsDestroyPreviewPlanner {
    private static final long CACHE_MILLIS = 120L;

    private int lastDimension = Integer.MIN_VALUE;
    private BlockPos lastPos;
    private EnumFacing lastFace;
    private RtsConvenienceDestroyMode lastMode;
    private RtsConvenienceDestroySettings lastSettings;
    private long lastComputedAt;
    private RtsConvenienceDestroyPlanner.Plan lastPlan = invalid();

    public RtsConvenienceDestroyPlanner.Plan preview(Minecraft minecraft,
            RtsConvenienceDestroyMode mode, RayTraceResult hit,
            RtsConvenienceDestroySettings settings) {
        if (minecraft == null || minecraft.world == null || mode == null || hit == null
                || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.sideHit == null) {
            return invalid();
        }
        RtsConvenienceDestroySettings clean = RtsConvenienceDestroyPlanner.sanitize(settings);
        BlockPos pos = hit.getBlockPos();
        long now = System.currentTimeMillis();
        if (minecraft.world.provider.getDimension() == this.lastDimension
                && pos.equals(this.lastPos) && hit.sideHit == this.lastFace
                && mode == this.lastMode && clean.equals(this.lastSettings)
                && now - this.lastComputedAt < CACHE_MILLIS) {
            return this.lastPlan;
        }
        this.lastDimension = minecraft.world.provider.getDimension();
        this.lastPos = pos.toImmutable();
        this.lastFace = hit.sideHit;
        this.lastMode = mode;
        this.lastSettings = clean;
        this.lastComputedAt = now;
        this.lastPlan = RtsConvenienceDestroyPlanner.plan(
                minecraft.world, mode, pos, hit.sideHit, clean);
        return this.lastPlan;
    }

    public void invalidate() {
        this.lastComputedAt = 0L;
        this.lastPos = null;
    }

    private static RtsConvenienceDestroyPlanner.Plan invalid() {
        return new RtsConvenienceDestroyPlanner.Plan(
                RtsConvenienceDestroyPlanner.ResultCode.INVALID_TARGET,
                java.util.Collections.<BlockPos>emptyList(), 0);
    }
}
