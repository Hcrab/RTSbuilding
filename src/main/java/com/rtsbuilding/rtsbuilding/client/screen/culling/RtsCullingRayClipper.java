package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 范围剔除专用的射线裁剪循环。
 *
 * <p>这个类只负责“命中被剔除方块时，从剔除盒出口之后继续裁剪”的通用算法；
 * 它不直接依赖 Minecraft 客户端实例，也不读取屏幕状态。这样真实客户端路径和
 * 自动化测试都能复用同一段穿透逻辑，避免管理页、交互目标和后续工具各写一套。</p>
 */
public final class RtsCullingRayClipper {
    private static final int DEFAULT_SKIP_GUARD = 32;

    private RtsCullingRayClipper() {
    }

    public static BlockHitResult clip(Vec3 origin, Vec3 direction, double maxDistance,
            BlockClip clip, CullingQuery culling) {
        if (origin == null || direction == null || clip == null || culling == null || maxDistance <= 0.0D) {
            return null;
        }
        Vec3 normalizedDirection = direction.normalize();
        Vec3 start = origin;
        Vec3 end = origin.add(normalizedDirection.scale(maxDistance));
        double startDistance = 0.0D;
        for (int guard = 0; guard < DEFAULT_SKIP_GUARD; guard++) {
            HitResult raw = clip.clip(start, end);
            if (!(raw instanceof BlockHitResult hit) || raw.getType() != HitResult.Type.BLOCK) {
                return null;
            }
            BlockPos hitPos = hit.getBlockPos();
            if (!culling.shouldCull(hitPos)) {
                return hit;
            }
            double nextDistance = culling.distanceAfterCulledBlock(origin, normalizedDirection, hitPos, maxDistance);
            if (nextDistance <= startDistance + 0.01D || nextDistance >= maxDistance) {
                return null;
            }
            startDistance = nextDistance;
            start = origin.add(normalizedDirection.scale(startDistance));
        }
        return null;
    }

    @FunctionalInterface
    public interface BlockClip {
        HitResult clip(Vec3 start, Vec3 end);
    }

    public interface CullingQuery {
        boolean shouldCull(BlockPos pos);

        double distanceAfterCulledBlock(Vec3 origin, Vec3 direction, BlockPos pos, double maxDistance);
    }
}
