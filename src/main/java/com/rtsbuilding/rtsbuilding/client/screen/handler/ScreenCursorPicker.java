package com.rtsbuilding.rtsbuilding.client.screen.handler;

import com.rtsbuilding.rtsbuilding.platform.block.BlockState;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingRayClipper;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingWorldInput;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsPlacementRayFreeze;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsCursorRay;
import com.rtsbuilding.rtsbuilding.common.blueprint.rule.BlueprintReplaceRules;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;

import java.util.List;

public final class ScreenCursorPicker implements RtsCullingWorldInput.Cursor {
    private static final double BLUEPRINT_AIR_FALLBACK_DISTANCE = 24.0D;
    private static final double ITEM_AIR_INTERACTION_DISTANCE = 2.0D;
    private static final double BLOCK_RAY_DISTANCE = 128.0D;

    private BuilderScreen screen;
    private ClientRtsController controller;
    private ScreenShapeController shapeController;

    public void init(BuilderScreen screen, ClientRtsController controller, ScreenShapeController shapeController) {
        this.screen = screen;
        this.controller = controller;
        this.shapeController = shapeController;
    }

    // ===== Public API =====

    public InteractionTypes.InteractionTarget pickInteractionTarget(boolean includeFluidSource) {
        if (this.screen == null) {
            return null;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.renderViewEntity == null) {
            return null;
        }
        Vec3d camPos = cameraPosition(mc);
        Vec3d dir = computeCursorRayDirection();
        Vec3d to = camPos.add(dir.scale(BLOCK_RAY_DISTANCE));
        RayTraceResult blockHit = clipBlockHit(mc, camPos, dir, includeFluidSource, true);
        RayTraceResult entityHit = pickEntityHit(camPos, to, dir);
        double blockDist = blockHit != null ? camPos.squareDistanceTo(blockHit.hitVec) : Double.MAX_VALUE;
        double entityDist = entityHit != null ? camPos.squareDistanceTo(entityHit.hitVec) : Double.MAX_VALUE;
        if (entityHit != null && entityDist <= blockDist) {
            Entity entity = entityHit.entityHit;
            return new InteractionTypes.InteractionTarget(
                    entity.getEntityId(),
                    entityHit.hitVec,
                    null,
                    camPos,
                    dir);
        }
        if (blockHit != null) {
            return new InteractionTypes.InteractionTarget(
                    C2SRtsInteractPayload.NO_ENTITY,
                    blockHit.hitVec,
                    blockHit,
                    camPos,
                    dir);
        }
        RayTraceResult airShapeHit = tryCreateAirShapeHit(camPos, dir);
        if (airShapeHit != null) {
            return new InteractionTypes.InteractionTarget(
                    C2SRtsInteractPayload.NO_ENTITY,
                    airShapeHit.hitVec,
                    airShapeHit,
                    camPos,
                    dir);
        }
        return null;
    }

    public InteractionTypes.InteractionTarget pickItemAirInteractionTarget() {
        if (this.screen == null) {
            return null;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.renderViewEntity == null) {
            return null;
        }
        Vec3d camPos = cameraPosition(mc);
        Vec3d dir = computeCursorRayDirection();
        RayTraceResult airHit = createItemAirInteractionHit(camPos, dir);
        if (airHit == null) {
            return null;
        }
        return new InteractionTypes.InteractionTarget(
                C2SRtsInteractPayload.NO_ENTITY,
                airHit.hitVec,
                airHit,
                camPos,
                dir);
    }

    public RayTraceResult pickBlockHit() {
        return pickBlockHit(false);
    }

    public RayTraceResult pickBlockHit(boolean includeFluidSource) {
        if (this.screen == null) {
            return null;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.renderViewEntity == null) {
            return null;
        }
        Vec3d camPos = cameraPosition(mc);
        Vec3d dir = computeCursorRayDirection();
        RayTraceResult hit = clipBlockHit(mc, camPos, dir, includeFluidSource, true);
        if (hit != null) {
            return hit;
        }
        return tryCreateAirShapeHit(camPos, dir);
    }

    public RayTraceResult pickBlockHitIgnoringRangeCulling(boolean includeFluidSource) {
        if (this.screen == null) {
            return null;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.renderViewEntity == null) {
            return null;
        }
        Vec3d camPos = cameraPosition(mc);
        Vec3d dir = computeCursorRayDirection();
        return clipBlockHit(mc, camPos, dir, includeFluidSource, false);
    }

    @Override
    public RayTraceResult pickCullingAwareBlockHit() {
        return pickBlockHit(false);
    }

    public RayTraceResult pickBlueprintPlacementHit() {
        InteractionTypes.InteractionTarget target = pickInteractionTarget(false);
        if (target != null && target.blockHit() != null) {
            return target.blockHit();
        }
        return tryCreateBlueprintAirHit();
    }

    public BlockPos resolveBlueprintAnchor(RayTraceResult hit) {
        if (this.screen == null) {
            return null;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (hit == null || mc == null || mc.theWorld == null) {
            return null;
        }
        BlockPos clicked = hit.getBlockPos();
        // Blueprint dragging should keep the building center vertically above the cursor target.
        return BlueprintReplaceRules.canBlueprintReplace(BlockState.fromWorld(mc.theWorld, clicked))
                ? clicked
                : clicked.up();
    }

    public Vec3d computeCursorRayDirection() {
        if (this.screen == null) {
            return new Vec3d(0, 0, -1);
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null) {
            return new Vec3d(0, 0, -1);
        }
        if (RtsPlacementRayFreeze.isFrozen()) {
            return RtsPlacementRayFreeze.directionOr(new Vec3d(0.0D, 0.0D, 1.0D));
        }
        return RtsCursorRay.capture(mc).direction();
    }

    public Vec3d currentRayOrigin() {
        if (this.screen == null) {
            return Vec3d.ZERO;
        }
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.entityRenderer == null) {
            return Vec3d.ZERO;
        }
        return RtsPlacementRayFreeze.originOr(
                cameraPosition(mc));
    }

    // ===== Private helpers =====

    private RayTraceResult clipBlockHit(Minecraft mc, Vec3d camPos, Vec3d dir, boolean includeFluidSource,
            boolean respectRangeCulling) {
        if (!respectRangeCulling) {
            Vec3d normalizedDir = dir.normalize();
            RayTraceResult raw = RayTraceResult.trace(mc.theWorld, camPos,
                    camPos.add(normalizedDir.scale(BLOCK_RAY_DISTANCE)), includeFluidSource, false, false);
            return raw != null && raw.typeOfHit == RayTraceResult.Type.BLOCK ? raw : null;
        }
        return RtsCullingRayClipper.clip(
                camPos,
                dir,
                BLOCK_RAY_DISTANCE,
                (start, end) -> RayTraceResult.trace(
                        mc.theWorld, start, end, includeFluidSource, false, false),
                new RtsCullingRayClipper.CullingQuery() {
                    @Override
                    public boolean shouldCull(BlockPos pos) {
                        return RtsCullingClientState.shouldCull(pos);
                    }

                    @Override
                    public double distanceAfterCulledBlock(Vec3d origin, Vec3d direction, BlockPos pos, double maxDistance) {
                        return RtsCullingClientState.distanceAfterCulledBlock(origin, direction, pos, maxDistance);
                    }
                });
    }

    private RayTraceResult pickEntityHit(Vec3d camPos, Vec3d to, Vec3d dir) {
        Minecraft mc = this.screen.getMinecraft();
        Entity cameraEntity = mc != null ? mc.renderViewEntity : null;
        if (cameraEntity == null || mc == null || mc.thePlayer == null) {
            return null;
        }
        AxisAlignedBB search = com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB.fromNative(cameraEntity.boundingBox).expand(
                dir.x * BLOCK_RAY_DISTANCE, dir.y * BLOCK_RAY_DISTANCE, dir.z * BLOCK_RAY_DISTANCE).grow(1.0D);
        List<Entity> candidates = mc.theWorld.getEntitiesWithinAABBExcludingEntity(cameraEntity, search);
        Entity closest = null;
        Vec3d closestHit = null;
        double closestDistance = BLOCK_RAY_DISTANCE * BLOCK_RAY_DISTANCE;
        for (Entity entity : candidates) {
            if (entity == null || entity == mc.thePlayer || !entity.isEntityAlive() || !entity.canBeCollidedWith()) continue;
            AxisAlignedBB bounds = com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB.fromNative(entity.boundingBox).grow(entity.getCollisionBorderSize());
            RayTraceResult intercept = bounds.calculateIntercept(camPos, to);
            if (bounds.contains(camPos)) {
                if (closestDistance >= 0.0D) { closest = entity; closestHit = camPos; closestDistance = 0.0D; }
            } else if (intercept != null) {
                double distance = camPos.squareDistanceTo(intercept.hitVec);
                if (distance < closestDistance) { closest = entity; closestHit = intercept.hitVec; closestDistance = distance; }
            }
        }
        return closest == null ? null : new RayTraceResult(closest, closestHit);
    }

    private RayTraceResult tryCreateAirShapeHit(Vec3d camPos, Vec3d dir) {
        if (camPos == null || dir == null) {
            return null;
        }
        if (this.controller.getBuildShape() == BuildShape.BLOCK
                && (this.shapeController.getShapeBuildSession() == null || this.shapeController.getShapeBuildSession().shape() == BuildShape.BLOCK)) {
            return null;
        }
        EnumFacing face = resolveAirShapeFace(dir);
        Vec3d planeAnchor = resolveAirShapePlaneAnchor(face);
        if (face == null || planeAnchor == null) {
            return null;
        }
        double dirComponent = axisComponent(face.getAxis(), dir);
        if (Math.abs(dirComponent) < 1.0E-5D) {
            return null;
        }
        double planeCoord = axisComponent(face.getAxis(), planeAnchor);
        double originCoord = axisComponent(face.getAxis(), camPos);
        double t = (planeCoord - originCoord) / dirComponent;
        if (t <= 0.0D || t > 128.0D) {
            return null;
        }
        Vec3d hitVec = camPos.add(dir.scale(t));
        BlockPos hitPos = new BlockPos(hitVec);
        if (RtsCullingClientState.shouldCull(hitPos)) {
            return null;
        }
        return new RayTraceResult(hitVec, face, hitPos);
    }

    private RayTraceResult tryCreateBlueprintAirHit() {
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null
                || mc.renderViewEntity == null) {
            return null;
        }
        Vec3d camPos = cameraPosition(mc);
        Vec3d dir = computeCursorRayDirection();
        double planeY = com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat
                .blockPosition(mc.thePlayer).getY();
        double t = Math.abs(dir.y) < 1.0E-5D
                ? BLUEPRINT_AIR_FALLBACK_DISTANCE
                : (planeY - camPos.y) / dir.y;
        if (t <= 0.0D || t > 128.0D) {
            t = BLUEPRINT_AIR_FALLBACK_DISTANCE;
        }
        Vec3d hitVec = camPos.add(dir.scale(t));
        BlockPos hitPos = new BlockPos(hitVec);
        if (RtsCullingClientState.shouldCull(hitPos)) {
            return null;
        }
        return new RayTraceResult(hitVec, EnumFacing.UP, hitPos);
    }

    private RayTraceResult createItemAirInteractionHit(Vec3d camPos, Vec3d dir) {
        if (camPos == null || dir == null || dir.lengthSquared() < 1.0E-6D) {
            return null;
        }
        Vec3d normalizedDir = dir.normalize();
        Vec3d hitVec = camPos.add(normalizedDir.scale(ITEM_AIR_INTERACTION_DISTANCE));
        BlockPos hitPos = new BlockPos(hitVec);
        if (RtsCullingClientState.shouldCull(hitPos)) {
            return null;
        }
        EnumFacing face = nearestFacing(-normalizedDir.x, -normalizedDir.y, -normalizedDir.z);
        return new RayTraceResult(hitVec, face, hitPos);
    }

    private EnumFacing resolveAirShapeFace(Vec3d dir) {
        if (this.shapeController.getShapeBuildSession() != null && this.shapeController.getShapeBuildSession().planeFace() != null) {
            return this.shapeController.getShapeBuildSession().planeFace();
        }
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.LINE
                || shape == BuildShape.SQUARE
                || shape == BuildShape.CYLINDER
                || shape == BuildShape.BOX) {
            return EnumFacing.UP;
        }
        if (shape == BuildShape.WALL) {
            return EnumFacing.UP;
        }
        return nearestFacing(-dir.x, -dir.y, -dir.z);
    }

    private Vec3d resolveAirShapePlaneAnchor(EnumFacing face) {
        Minecraft mc = this.screen.getMinecraft();
        if (face == null || mc == null || mc.thePlayer == null) {
            return null;
        }
        if (this.shapeController.getShapeBuildSession() != null) {
            if (this.shapeController.getShapeBuildSession().pointA() != null) {
                return centerOf(this.shapeController.getShapeBuildSession().pointA());
            }
            if (this.shapeController.getShapeBuildSession().pointB() != null) {
                return centerOf(this.shapeController.getShapeBuildSession().pointB());
            }
        }
        return centerOf(com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat
                .blockPosition(mc.thePlayer));
    }

    private static Vec3d cameraPosition(Minecraft mc) {
        return RtsCursorRay.capture(mc).origin();
    }

    private static Vec3d centerOf(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private static double axisComponent(EnumFacing.Axis axis, Vec3d vector) {
        switch (axis) {
            case X: return vector.x;
            case Y: return vector.y;
            case Z: return vector.z;
            default: throw new IllegalArgumentException("Unknown axis " + axis);
        }
    }

    private static EnumFacing nearestFacing(double x, double y, double z) {
        EnumFacing best = EnumFacing.NORTH;
        double score = -Double.MAX_VALUE;
        for (EnumFacing facing : EnumFacing.values()) {
            double dot = x * facing.getXOffset() + y * facing.getYOffset() + z * facing.getZOffset();
            if (dot > score) { score = dot; best = facing; }
        }
        return best;
    }
}
