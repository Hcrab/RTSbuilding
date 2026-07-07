package com.rtsbuilding.rtsbuilding.client.screen.handler;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingRayClipper;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingWorldInput;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.blueprint.rule.BlueprintReplaceRules;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

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
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null || mc.getCameraEntity() == null) {
            return null;
        }
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 dir = computeCursorRayDirection();
        Vec3 to = camPos.add(dir.scale(BLOCK_RAY_DISTANCE));
        BlockHitResult blockHit = clipBlockHit(mc, camPos, dir, includeFluidSource, true);
        EntityHitResult entityHit = pickEntityHit(camPos, to, dir);
        double blockDist = blockHit != null ? camPos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        double entityDist = entityHit != null ? camPos.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;
        if (entityHit != null && entityDist <= blockDist) {
            Entity entity = entityHit.getEntity();
            return new InteractionTypes.InteractionTarget(
                    entity.getId(),
                    entityHit.getLocation(),
                    null,
                    camPos,
                    dir);
        }
        if (blockHit != null) {
            return new InteractionTypes.InteractionTarget(
                    C2SRtsInteractPayload.NO_ENTITY,
                    blockHit.getLocation(),
                    blockHit,
                    camPos,
                    dir);
        }
        BlockHitResult airShapeHit = tryCreateAirShapeHit(camPos, dir);
        if (airShapeHit != null) {
            return new InteractionTypes.InteractionTarget(
                    C2SRtsInteractPayload.NO_ENTITY,
                    airShapeHit.getLocation(),
                    airShapeHit,
                    camPos,
                    dir);
        }
        return null;
    }

    public InteractionTypes.InteractionTarget pickItemAirInteractionTarget() {
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null || mc.player == null || mc.getCameraEntity() == null) {
            return null;
        }
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 dir = computeCursorRayDirection();
        BlockHitResult airHit = createItemAirInteractionHit(camPos, dir);
        if (airHit == null) {
            return null;
        }
        return new InteractionTypes.InteractionTarget(
                C2SRtsInteractPayload.NO_ENTITY,
                airHit.getLocation(),
                airHit,
                camPos,
                dir);
    }

    public BlockHitResult pickBlockHit() {
        return pickBlockHit(false);
    }

    public BlockHitResult pickBlockHit(boolean includeFluidSource) {
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null || mc.getCameraEntity() == null) {
            return null;
        }
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 dir = computeCursorRayDirection();
        BlockHitResult hit = clipBlockHit(mc, camPos, dir, includeFluidSource, true);
        if (hit != null) {
            return hit;
        }
        return tryCreateAirShapeHit(camPos, dir);
    }

    public BlockHitResult pickBlockHitIgnoringRangeCulling(boolean includeFluidSource) {
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null || mc.getCameraEntity() == null) {
            return null;
        }
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 dir = computeCursorRayDirection();
        return clipBlockHit(mc, camPos, dir, includeFluidSource, false);
    }

    @Override
    public BlockHitResult pickCullingAwareBlockHit() {
        return pickBlockHit(false);
    }

    public BlockHitResult pickBlueprintPlacementHit() {
        InteractionTypes.InteractionTarget target = pickInteractionTarget(false);
        if (target != null && target.blockHit() != null) {
            return target.blockHit();
        }
        return tryCreateBlueprintAirHit();
    }

    public BlockPos resolveBlueprintAnchor(BlockHitResult hit) {
        Minecraft mc = this.screen.getMinecraft();
        if (hit == null || mc == null || mc.level == null) {
            return null;
        }
        BlockPos clicked = hit.getBlockPos();
        // Blueprint dragging should keep the building center vertically above the cursor target.
        return BlueprintReplaceRules.canBlueprintReplace(mc.level.getBlockState(clicked))
                ? clicked
                : clicked.above();
    }

    public Vec3 computeCursorRayDirection() {
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null) {
            return new Vec3(0, 0, -1);
        }
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        double width = Math.max(1.0D, mc.getWindow().getScreenWidth());
        double height = Math.max(1.0D, mc.getWindow().getScreenHeight());
        double nx = (mouseX / width) * 2.0D - 1.0D;
        double ny = 1.0D - (mouseY / height) * 2.0D;
        float yawDeg = mc.gameRenderer.getMainCamera().getYRot();
        float pitchDeg = mc.gameRenderer.getMainCamera().getXRot();
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        Vec3 forward = new Vec3(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)).normalize();
        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();
        Vec3 up = forward.cross(right).normalize();
        double fovY = Math.toRadians(mc.options.fov().get());
        double tanY = Math.tan(fovY * 0.5D);
        double tanX = tanY * (width / height);
        return forward.add(right.scale(-nx * tanX)).add(up.scale(ny * tanY)).normalize();
    }

    public Vec3 currentRayOrigin() {
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.gameRenderer == null) {
            return Vec3.ZERO;
        }
        return mc.gameRenderer.getMainCamera().getPosition();
    }

    // ===== Private helpers =====

    private BlockHitResult clipBlockHit(Minecraft mc, Vec3 camPos, Vec3 dir, boolean includeFluidSource,
            boolean respectRangeCulling) {
        ClipContext.Fluid fluidMode = includeFluidSource ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE;
        if (!respectRangeCulling) {
            Vec3 normalizedDir = dir.normalize();
            HitResult raw = mc.level.clip(new ClipContext(
                    camPos,
                    camPos.add(normalizedDir.scale(BLOCK_RAY_DISTANCE)),
                    ClipContext.Block.OUTLINE,
                    fluidMode,
                    mc.getCameraEntity()));
            return raw instanceof BlockHitResult bhr && raw.getType() == HitResult.Type.BLOCK ? bhr : null;
        }
        return RtsCullingRayClipper.clip(
                camPos,
                dir,
                BLOCK_RAY_DISTANCE,
                (start, end) -> mc.level.clip(new ClipContext(
                        start,
                        end,
                        ClipContext.Block.OUTLINE,
                        fluidMode,
                        mc.getCameraEntity())),
                new RtsCullingRayClipper.CullingQuery() {
                    @Override
                    public boolean shouldCull(BlockPos pos) {
                        return RtsCullingClientState.shouldCull(pos);
                    }

                    @Override
                    public double distanceAfterCulledBlock(Vec3 origin, Vec3 direction, BlockPos pos, double maxDistance) {
                        return RtsCullingClientState.distanceAfterCulledBlock(origin, direction, pos, maxDistance);
                    }
                });
    }

    private EntityHitResult pickEntityHit(Vec3 camPos, Vec3 to, Vec3 dir) {
        Minecraft mc = this.screen.getMinecraft();
        Entity cameraEntity = mc != null ? mc.getCameraEntity() : null;
        if (cameraEntity == null || mc == null || mc.player == null) {
            return null;
        }
        AABB search = cameraEntity.getBoundingBox().expandTowards(dir.scale(128.0D)).inflate(1.0D);
        return ProjectileUtil.getEntityHitResult(
                cameraEntity,
                camPos,
                to,
                search,
                entity -> entity != null
                        && entity.isAlive()
                        && entity.isPickable()
                        && entity != cameraEntity
                        && entity != mc.player,
                128.0D * 128.0D);
    }

    private BlockHitResult tryCreateAirShapeHit(Vec3 camPos, Vec3 dir) {
        if (camPos == null || dir == null) {
            return null;
        }
        if (this.controller.getBuildShape() == BuildShape.BLOCK
                && (this.shapeController.getShapeBuildSession() == null || this.shapeController.getShapeBuildSession().shape() == BuildShape.BLOCK)) {
            return null;
        }
        Direction face = resolveAirShapeFace(dir);
        Vec3 planeAnchor = resolveAirShapePlaneAnchor(face);
        if (face == null || planeAnchor == null) {
            return null;
        }
        double dirComponent = switch (face.getAxis()) {
            case X -> dir.x;
            case Y -> dir.y;
            case Z -> dir.z;
        };
        if (Math.abs(dirComponent) < 1.0E-5D) {
            return null;
        }
        double planeCoord = switch (face.getAxis()) {
            case X -> planeAnchor.x;
            case Y -> planeAnchor.y;
            case Z -> planeAnchor.z;
        };
        double originCoord = switch (face.getAxis()) {
            case X -> camPos.x;
            case Y -> camPos.y;
            case Z -> camPos.z;
        };
        double t = (planeCoord - originCoord) / dirComponent;
        if (t <= 0.0D || t > 128.0D) {
            return null;
        }
        Vec3 hitVec = camPos.add(dir.scale(t));
        BlockPos hitPos = BlockPos.containing(hitVec);
        if (RtsCullingClientState.shouldCull(hitPos)) {
            return null;
        }
        return new BlockHitResult(hitVec, face, hitPos, false);
    }

    private BlockHitResult tryCreateBlueprintAirHit() {
        Minecraft mc = this.screen.getMinecraft();
        if (mc == null || mc.level == null || mc.player == null
                || mc.getCameraEntity() == null) {
            return null;
        }
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 dir = computeCursorRayDirection();
        double planeY = mc.player.blockPosition().getY();
        double t = Math.abs(dir.y) < 1.0E-5D
                ? BLUEPRINT_AIR_FALLBACK_DISTANCE
                : (planeY - camPos.y) / dir.y;
        if (t <= 0.0D || t > 128.0D) {
            t = BLUEPRINT_AIR_FALLBACK_DISTANCE;
        }
        Vec3 hitVec = camPos.add(dir.scale(t));
        BlockPos hitPos = BlockPos.containing(hitVec);
        if (RtsCullingClientState.shouldCull(hitPos)) {
            return null;
        }
        return new BlockHitResult(hitVec, Direction.UP, hitPos, false);
    }

    private BlockHitResult createItemAirInteractionHit(Vec3 camPos, Vec3 dir) {
        if (camPos == null || dir == null || dir.lengthSqr() < 1.0E-6D) {
            return null;
        }
        Vec3 normalizedDir = dir.normalize();
        Vec3 hitVec = camPos.add(normalizedDir.scale(ITEM_AIR_INTERACTION_DISTANCE));
        BlockPos hitPos = BlockPos.containing(hitVec);
        if (RtsCullingClientState.shouldCull(hitPos)) {
            return null;
        }
        Direction face = Direction.getNearest(-normalizedDir.x, -normalizedDir.y, -normalizedDir.z);
        return new BlockHitResult(hitVec, face, hitPos, false);
    }

    private Direction resolveAirShapeFace(Vec3 dir) {
        if (this.shapeController.getShapeBuildSession() != null && this.shapeController.getShapeBuildSession().planeFace() != null) {
            return this.shapeController.getShapeBuildSession().planeFace();
        }
        BuildShape shape = this.controller.getBuildShape();
        if (shape == BuildShape.LINE
                || shape == BuildShape.SQUARE
                || shape == BuildShape.BOX) {
            return Direction.UP;
        }
        if (shape == BuildShape.WALL) {
            return Direction.UP;
        }
        return Direction.getNearest(-dir.x, -dir.y, -dir.z);
    }

    private Vec3 resolveAirShapePlaneAnchor(Direction face) {
        Minecraft mc = this.screen.getMinecraft();
        if (face == null || mc == null || mc.player == null) {
            return null;
        }
        if (this.shapeController.getShapeBuildSession() != null) {
            if (this.shapeController.getShapeBuildSession().pointA() != null) {
                return Vec3.atCenterOf(this.shapeController.getShapeBuildSession().pointA());
            }
            if (this.shapeController.getShapeBuildSession().pointB() != null) {
                return Vec3.atCenterOf(this.shapeController.getShapeBuildSession().pointB());
            }
        }
        return Vec3.atCenterOf(mc.player.blockPosition());
    }
}
