package com.rtsbuilding.rtsbuilding.client.screen.culling;

import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;

/** 命中被剔除方块时，从剔除盒出口之后继续裁剪的纯射线循环。 */
public final class RtsCullingRayClipper {
    private static final int DEFAULT_SKIP_GUARD = 32;
    private static final double EPSILON = 1.0E-7D;

    private RtsCullingRayClipper() { }

    public static RayTraceResult clip(Vec3d origin, Vec3d direction, double maxDistance,
            BlockClip clip, CullingQuery culling) {
        if (origin == null || direction == null || direction.lengthSquared() < EPSILON
                || clip == null || culling == null || maxDistance <= 0.0D) {
            return null;
        }
        Vec3d normalized = direction.normalize();
        Vec3d start = origin;
        Vec3d end = origin.add(normalized.scale(maxDistance));
        double startDistance = 0.0D;
        for (int guard = 0; guard < DEFAULT_SKIP_GUARD; guard++) {
            RayTraceResult raw = clip.clip(start, end);
            if (raw == null || raw.typeOfHit != RayTraceResult.Type.BLOCK) return null;
            BlockPos hitPos = raw.getBlockPos();
            if (!culling.shouldCull(hitPos)) return raw;
            double nextDistance = culling.distanceAfterCulledBlock(
                    origin, normalized, hitPos, maxDistance);
            if (nextDistance <= startDistance + 0.01D || nextDistance >= maxDistance) return null;
            startDistance = nextDistance;
            start = origin.add(normalized.scale(startDistance));
        }
        return null;
    }

    @FunctionalInterface
    public interface BlockClip { RayTraceResult clip(Vec3d start, Vec3d end); }

    public interface CullingQuery {
        boolean shouldCull(BlockPos pos);
        double distanceAfterCulledBlock(Vec3d origin, Vec3d direction, BlockPos pos, double maxDistance);
    }
}
