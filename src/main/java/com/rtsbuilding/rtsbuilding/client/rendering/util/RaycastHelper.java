package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.google.common.base.Predicate;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingRayClipper;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Mouse;

import java.util.List;

/** Forge 1.12 鼠标射线、方块命中与实体命中的统一工具。 */
public final class RaycastHelper {
    private RaycastHelper() {
    }

    public static RayTraceResult raycastBlockFromCursor(Minecraft minecraft,
            Vec3d cameraPosition, Vec3d end, boolean includeFluidSource) {
        if (minecraft == null || minecraft.world == null || minecraft.getRenderViewEntity() == null
                || cameraPosition == null || end == null) return null;
        RayTraceResult hit = minecraft.world.rayTraceBlocks(
                cameraPosition, end, includeFluidSource, false, false);
        return hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK ? hit : null;
    }

    public static RayTraceResult raycastBlockFromCursorThroughCulling(final Minecraft minecraft,
            Vec3d cameraPosition, Vec3d direction, double maxReach, final boolean includeFluidSource) {
        if (minecraft == null || minecraft.world == null || minecraft.getRenderViewEntity() == null
                || cameraPosition == null || direction == null) return null;
        return RtsCullingRayClipper.clip(cameraPosition, direction, maxReach,
                new RtsCullingRayClipper.BlockClip() {
                    @Override
                    public RayTraceResult clip(Vec3d start, Vec3d end) {
                        return minecraft.world.rayTraceBlocks(
                                start, end, includeFluidSource, false, false);
                    }
                }, new RtsCullingRayClipper.CullingQuery() {
                    @Override
                    public boolean shouldCull(BlockPos pos) {
                        return RtsCullingClientState.shouldCull(pos);
                    }

                    @Override
                    public double distanceAfterCulledBlock(Vec3d origin, Vec3d rayDirection,
                            BlockPos pos, double maxDistance) {
                        return RtsCullingClientState.distanceAfterCulledBlock(
                                origin, rayDirection, pos, maxDistance);
                    }
                });
    }

    public static RayTraceResult raycastEntityFromCursor(final Minecraft minecraft,
            Vec3d cameraPosition, Vec3d end, Vec3d viewDirection, double reach) {
        if (minecraft == null || minecraft.world == null || cameraPosition == null
                || end == null || viewDirection == null) return null;
        final Entity camera = minecraft.getRenderViewEntity();
        if (camera == null) return null;

        AxisAlignedBB search = camera.getEntityBoundingBox().expand(
                viewDirection.x * reach, viewDirection.y * reach, viewDirection.z * reach).grow(1.0D);
        List<Entity> entities = minecraft.world.getEntitiesInAABBexcluding(camera, search,
                new Predicate<Entity>() {
                    @Override
                    public boolean apply(Entity entity) {
                        return entity != null && !entity.isDead && entity.canBeCollidedWith()
                                && entity != camera && entity != minecraft.player;
                    }
                });

        Entity bestEntity = null;
        Vec3d bestHit = null;
        double bestDistanceSq = reach * reach;
        for (Entity entity : entities) {
            AxisAlignedBB bounds = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
            RayTraceResult intercept = bounds.calculateIntercept(cameraPosition, end);
            if (bounds.contains(cameraPosition)) {
                if (bestDistanceSq >= 0.0D) {
                    bestEntity = entity;
                    bestHit = cameraPosition;
                    bestDistanceSq = 0.0D;
                }
            } else if (intercept != null && intercept.hitVec != null) {
                double distanceSq = cameraPosition.squareDistanceTo(intercept.hitVec);
                if (distanceSq < bestDistanceSq) {
                    bestEntity = entity;
                    bestHit = intercept.hitVec;
                    bestDistanceSq = distanceSq;
                }
            }
        }
        return bestEntity == null ? null : new RayTraceResult(bestEntity, bestHit);
    }

    /**
     * 根据 LWJGL2 鼠标像素坐标和 1.12 相机朝向计算归一化世界射线。
     * 冻结放置射线时直接返回面板打开瞬间的快照。
     */
    public static Vec3d computeCursorRayDirection(Minecraft minecraft) {
        if (RtsPlacementRayFreeze.isFrozen()) {
            return RtsPlacementRayFreeze.directionOr(new Vec3d(0.0D, 0.0D, 1.0D));
        }
        if (minecraft == null || minecraft.getRenderViewEntity() == null) {
            return new Vec3d(0.0D, 0.0D, 1.0D);
        }

        double width = Math.max(1.0D, minecraft.displayWidth);
        double height = Math.max(1.0D, minecraft.displayHeight);
        double mouseX = Mouse.isCreated() ? Mouse.getX() : width * 0.5D;
        double mouseY = Mouse.isCreated() ? Mouse.getY() : height * 0.5D;
        double normalizedX = mouseX / width * 2.0D - 1.0D;
        double normalizedY = mouseY / height * 2.0D - 1.0D;

        Entity camera = minecraft.getRenderViewEntity();
        Vec3d forward = camera.getLook(minecraft.getRenderPartialTicks()).normalize();
        return computeDirection(forward, normalizedX, normalizedY,
                minecraft.gameSettings.fovSetting, width / height);
    }

    static Vec3d computeDirection(Vec3d forward, double normalizedX, double normalizedY,
            double verticalFovDegrees, double aspectRatio) {
        if (forward == null || forward.lengthSquared() < 1.0E-8D) {
            return new Vec3d(0.0D, 0.0D, 1.0D);
        }
        forward = forward.normalize();
        Vec3d worldUp = Math.abs(forward.y) > 0.999D
                ? new Vec3d(0.0D, 0.0D, 1.0D) : new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d right = worldUp.crossProduct(forward).normalize();
        Vec3d up = forward.crossProduct(right).normalize();
        double tanY = Math.tan(Math.toRadians(verticalFovDegrees) * 0.5D);
        double tanX = tanY * Math.max(1.0E-8D, aspectRatio);
        return forward.add(right.scale(normalizedX * tanX))
                .add(up.scale(normalizedY * tanY)).normalize();
    }
}
