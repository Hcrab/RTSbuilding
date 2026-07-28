package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCullingRayClipperTest {
    @Test
    void clipSkipsCulledBlockAndReturnsVisibleBlockBehindIt() {
        RtsCullingManager manager = managerWithConfirmedBox(
                new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0),
                1);
        Vec3d origin = new Vec3d(0.5D, 64.5D, 0.5D);
        Vec3d direction = new Vec3d(1.0D, 0.0D, 0.0D);
        RayTraceResult hiddenHit = hit(new BlockPos(1, 64, 0));
        RayTraceResult visibleBehindHit = hit(new BlockPos(4, 64, 0));
        List<Vec3d> clipStarts = new ArrayList<>();

        RayTraceResult result = RtsCullingRayClipper.clip(
                origin,
                direction,
                16.0D,
                (start, end) -> {
                    clipStarts.add(start);
                    return clipStarts.size() == 1 ? hiddenHit : visibleBehindHit;
                },
                cullingQuery(manager));

        assertEquals(new BlockPos(4, 64, 0), result.getBlockPos());
        assertEquals(2, clipStarts.size());
        assertTrue(clipStarts.get(1).x > 3.0D,
                "第二次裁剪应该从剔除盒出口之后开始，不能继续卡在隐藏体积里。");
    }

    @Test
    void clipReturnsFirstHitWhenItIsNotCulled() {
        RtsCullingManager manager = managerWithConfirmedBox(
                new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0),
                1);
        RayTraceResult visibleHit = hit(new BlockPos(4, 64, 0));
        List<Vec3d> clipStarts = new ArrayList<>();

        RayTraceResult result = RtsCullingRayClipper.clip(
                new Vec3d(0.5D, 64.5D, 0.5D),
                new Vec3d(1.0D, 0.0D, 0.0D),
                16.0D,
                (start, end) -> {
                    clipStarts.add(start);
                    return visibleHit;
                },
                cullingQuery(manager));

        assertEquals(new BlockPos(4, 64, 0), result.getBlockPos());
        assertEquals(1, clipStarts.size());
    }

    private static RtsCullingRayClipper.CullingQuery cullingQuery(RtsCullingManager manager) {
        return new RtsCullingRayClipper.CullingQuery() {
            @Override
            public boolean shouldCull(BlockPos pos) {
                return manager.shouldCullWorldBlock(pos);
            }

            @Override
            public double distanceAfterCulledBlock(Vec3d origin, Vec3d direction, BlockPos pos, double maxDistance) {
                return manager.distanceAfterCulledBlock(origin, direction, pos, maxDistance);
            }
        };
    }

    private static RtsCullingManager managerWithConfirmedBox(BlockPos first, BlockPos second, int heightOffset) {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);
        clickBlock(manager, first);
        clickBlock(manager, second);
        for (int i = 0; i < Math.abs(heightOffset); i++) {
            manager.handleScroll(heightOffset > 0 ? 1.0D : -1.0D, false);
        }
        manager.confirmDraft();
        return manager;
    }

    private static void clickBlock(RtsCullingManager manager, BlockPos pos) {
        manager.handleWorldAction(hit(pos), Vec3d.ZERO, new Vec3d(1.0D, 0.0D, 0.0D));
    }

    private static RayTraceResult hit(BlockPos pos) {
        return new RayTraceResult(new Vec3d(pos).add(0.5D, 0.5D, 0.5D), EnumFacing.UP, pos);
    }
}
