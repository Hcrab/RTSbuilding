package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.compat.sable.RtsSableClientSpatialCompat;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.compat.sable.RtsSableSpatialCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * 持有一次形状选区会话，并独占多次点击推进与滚轮尺寸调整。
 *
 * <p>该类不生成方块集合、不发送网络请求，也不决定放置或破坏；这些副作用仍由顶层控制器
 * 编排。把会话状态与推进规则放在一起，可以避免顶层控制器再次长成每种形状各一套状态机。</p>
 */
public final class ShapeSelectionSession {
    private BuilderScreen screen;
    private ShapeSelectionBoxController boxes;
    private ShapeBuildTypes.Session session;
    private BlockHitResult templateHit;
    private int footprintNudgeA;
    private int footprintNudgeB;
    private double cursorY;
    private UUID frameId;

    public void init(BuilderScreen screen, ShapeSelectionBoxController boxes) {
        this.screen = screen;
        this.boxes = boxes;
    }

    public ShapeBuildTypes.Session current() {
        return this.session;
    }

    public void replace(ShapeBuildTypes.Session session) {
        this.session = session;
        Minecraft mc = this.screen == null ? null : this.screen.getMinecraft();
        this.frameId = session == null || session.pointA() == null || mc == null || mc.level == null
                ? null
                : RtsSableSpatialCompat.frameId(mc.level, session.pointA());
    }

    public BlockHitResult templateHit() {
        return this.templateHit;
    }

    public void setCursorY(double cursorY) {
        this.cursorY = cursorY;
    }

    public void clear() {
        this.session = null;
        this.templateHit = null;
        this.footprintNudgeA = 0;
        this.footprintNudgeB = 0;
        this.cursorY = 0.0D;
        this.frameId = null;
        this.boxes.clear();
    }

    public boolean shouldSubmitAfterSelection(boolean keyboardConfirmEnabled) {
        return this.session != null
                && ShapeConfirmationPolicy.shouldSubmitAfterSelection(keyboardConfirmEnabled, this.session.phase());
    }

    public void advance(BlockHitResult hit, Vec3 rayDir, double mouseY, BuildShape shape) {
        if (this.session == null || this.session.shape() != shape) {
            start(hit, rayDir, mouseY, shape);
            return;
        }
        Minecraft mc = this.screen == null ? null : this.screen.getMinecraft();
        UUID hitFrameId = mc == null || mc.level == null
                ? null
                : RtsSableSpatialCompat.frameId(mc.level, hit.getBlockPos());
        if (!Objects.equals(this.frameId, hitFrameId)) {
            // 跨主世界/飞船或跨两艘飞船时，本次点击成为新会话第一点，避免生成横跨 plot 的巨型形状。
            clear();
            start(hit, rayDir, mouseY, shape);
            return;
        }
        switch (this.session.shape()) {
            case LINE -> advanceLine(hit);
            case SQUARE, CIRCLE, BALL -> advanceTwoPoint(hit, mouseY);
            case WALL, CYLINDER -> advanceHeightShape(hit, mouseY);
            case BOX -> advanceBox(hit, mouseY);
            default -> { }
        }
    }

    private void start(BlockHitResult hit, Vec3 rayDir, double mouseY, BuildShape shape) {
        this.footprintNudgeA = 0;
        this.footprintNudgeB = 0;
        Minecraft mc = this.screen == null ? null : this.screen.getMinecraft();
        this.frameId = mc == null || mc.level == null
                ? null
                : RtsSableSpatialCompat.frameId(mc.level, hit.getBlockPos());
        Vec3 localRayDir = localRay(hit.getBlockPos(), rayDir).direction();
        Direction placementFace = ShapeGeometryUtil.resolveShapePlacementFace(
                shape, hit.getDirection(), localRayDir);
        this.templateHit = new BlockHitResult(hit.getLocation(), placementFace, hit.getBlockPos(), hit.isInside());
        this.session = new ShapeBuildTypes.Session(
                shape,
                resolveBuildFace(shape, hit.getDirection(), localRayDir),
                placementFace,
                hit.getBlockPos(),
                null,
                ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                0,
                mouseY);
    }

    private Direction resolveBuildFace(BuildShape shape, Direction clickedFace, Vec3 rayDir) {
        if (shape != BuildShape.CIRCLE && shape != BuildShape.CYLINDER) {
            return ShapeGeometryUtil.resolveShapeBuildFace(shape, clickedFace, rayDir);
        }
        if (this.screen == null || !this.screen.isRoundShapeVertical(shape)) {
            return Direction.UP;
        }
        if (rayDir != null && (Math.abs(rayDir.x) > 1.0E-5D || Math.abs(rayDir.z) > 1.0E-5D)) {
            Direction nearest = Direction.getNearest(rayDir.x, 0.0D, rayDir.z);
            if (nearest.getAxis() != Direction.Axis.Y) {
                return nearest;
            }
        }
        return clickedFace != null && clickedFace.getAxis() != Direction.Axis.Y ? clickedFace : Direction.NORTH;
    }

    private void advanceLine(BlockHitResult hit) {
        if (this.session.phase() != ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            return;
        }
        boolean vertical = isVerticalLine(this.session.shape());
        BlockPos pointB = vertical
                ? ShapeSessionInputResolver.resolveVerticalLinePoint(this.session, hit)
                : resolvePlanePoint(this.session, hit);
        this.session = new ShapeBuildTypes.Session(
                this.session.shape(), this.session.planeFace(), this.session.placementFace(),
                this.session.pointA(), pointB, ShapeBuildTypes.Phase.READY_CONFIRM,
                vertical && pointB != null && this.session.pointA() != null
                        ? pointB.getY() - this.session.pointA().getY()
                        : 0,
                this.session.boxHeightMouseBaseY());
    }

    private void advanceTwoPoint(BlockHitResult hit, double mouseY) {
        if (this.session.phase() != ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            return;
        }
        BlockPos pointB = this.boxes.isAdvanced(this.session.shape())
                ? resolveAdvancedSecondPoint(this.session, hit)
                : resolvePlanePoint(this.session, hit);
        this.session = ready(this.session, pointB, mouseY);
    }

    private void advanceHeightShape(BlockHitResult hit, double mouseY) {
        if (this.session.phase() == ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            BlockPos pointB = this.boxes.isAdvanced(this.session.shape())
                    ? resolveAdvancedSecondPoint(this.session, hit)
                    : resolvePlanePoint(this.session, hit);
            this.session = this.boxes.isAdvanced(this.session.shape())
                    ? ready(this.session, pointB, mouseY)
                    : new ShapeBuildTypes.Session(
                            this.session.shape(), this.session.planeFace(), this.session.placementFace(),
                            this.session.pointA(), pointB, ShapeBuildTypes.Phase.NEED_THIRD_POINT, 0, mouseY);
            return;
        }
        if (this.session.phase() == ShapeBuildTypes.Phase.NEED_THIRD_POINT) {
            this.session = new ShapeBuildTypes.Session(
                    this.session.shape(), this.session.planeFace(), this.session.placementFace(),
                    this.session.pointA(), this.session.pointB(), ShapeBuildTypes.Phase.READY_CONFIRM,
                    this.session.boxHeightOffset(), this.session.boxHeightMouseBaseY());
        }
    }

    private void advanceBox(BlockHitResult hit, double mouseY) {
        if (this.session.phase() == ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            boolean advanced = this.boxes.isAdvanced(this.session.shape());
            BlockPos pointB = advanced
                    ? resolveAdvancedSecondPoint(this.session, hit)
                    : resolvePlanePoint(this.session, hit);
            ShapeBuildTypes.Session next = new ShapeBuildTypes.Session(
                    this.session.shape(), this.session.planeFace(), this.session.placementFace(),
                    this.session.pointA(), pointB,
                    advanced ? ShapeBuildTypes.Phase.READY_CONFIRM : ShapeBuildTypes.Phase.NEED_THIRD_POINT,
                    advanced ? pointB.getY() - this.session.pointA().getY() : 0,
                    mouseY);
            this.session = advanced
                    ? AdvancedShapeSelectionGeometry.sessionFromBox(
                            next,
                            this.boxes.clamp(AdvancedShapeSelectionGeometry.boxFromSession(next), this.session.pointA()))
                    : next;
            return;
        }
        if (this.session.phase() == ShapeBuildTypes.Phase.NEED_THIRD_POINT) {
            this.session = new ShapeBuildTypes.Session(
                    this.session.shape(), this.session.planeFace(), this.session.placementFace(),
                    this.session.pointA(), this.session.pointB(), ShapeBuildTypes.Phase.READY_CONFIRM,
                    this.session.boxHeightOffset(), this.session.boxHeightMouseBaseY());
        }
    }

    private ShapeBuildTypes.Session ready(ShapeBuildTypes.Session base, BlockPos pointB, double mouseY) {
        ShapeBuildTypes.Session ready = new ShapeBuildTypes.Session(
                base.shape(), base.planeFace(), base.placementFace(), base.pointA(), pointB,
                ShapeBuildTypes.Phase.READY_CONFIRM,
                this.boxes.isAdvanced(base.shape()) && pointB != null
                        ? AdvancedShapeSelectionGeometry.initialHeightOffset(base.shape(), base.pointA(), pointB)
                        : 0,
                mouseY);
        return this.boxes.isAdvanced(base.shape())
                ? AdvancedShapeSelectionGeometry.sessionFromBox(
                        ready,
                        this.boxes.clamp(AdvancedShapeSelectionGeometry.initialBox(ready), base.pointA()))
                : ready;
    }

    private BlockPos resolveAdvancedSecondPoint(ShapeBuildTypes.Session base, BlockHitResult hit) {
        if (hit == null) {
            return resolvePlanePoint(base, null);
        }
        Minecraft mc = this.screen.getMinecraft();
        BlockPos clicked = hit.getBlockPos();
        if (mc != null && mc.level != null
                && mc.level.getBlockState(clicked).isAir()
                && mc.level.getFluidState(clicked).isEmpty()) {
            return resolvePlanePoint(base, hit);
        }
        return clicked;
    }

    public ShapeBuildTypes.Input resolveInput(
            BlockHitResult cursorHit,
            boolean requireReady,
            BuildShape currentShape,
            boolean lineConnected) {
        if (this.session == null || this.session.shape() != currentShape) {
            return null;
        }
        Minecraft mc = this.screen.getMinecraft();
        Vec3 rayOrigin = mc != null && mc.gameRenderer != null
                ? mc.gameRenderer.getMainCamera().getPosition()
                : null;
        Vec3 rayDirection = mc != null ? this.screen.computeCursorRayDirection() : null;
        RtsSableClientSpatialCompat.Ray localRay = localRay(this.session.pointA(), rayOrigin, rayDirection);
        return ShapeSessionInputResolver.resolve(
                this.session, cursorHit, requireReady, isVerticalLine(this.session.shape()), lineConnected,
                this.footprintNudgeA, this.footprintNudgeB, localRay.origin(), localRay.direction());
    }

    private BlockPos resolvePlanePoint(ShapeBuildTypes.Session base, BlockHitResult cursorHit) {
        Minecraft mc = this.screen.getMinecraft();
        Vec3 origin = mc != null && mc.gameRenderer != null
                ? mc.gameRenderer.getMainCamera().getPosition()
                : null;
        Vec3 direction = mc != null ? this.screen.computeCursorRayDirection() : null;
        RtsSableClientSpatialCompat.Ray localRay = localRay(base.pointA(), origin, direction);
        return ShapeSessionInputResolver.resolvePlanePoint(
                base, cursorHit, localRay.origin(), localRay.direction());
    }

    private RtsSableClientSpatialCompat.Ray localRay(BlockPos framePosition, Vec3 globalDirection) {
        Minecraft mc = this.screen == null ? null : this.screen.getMinecraft();
        Vec3 globalOrigin = mc != null && mc.gameRenderer != null
                ? mc.gameRenderer.getMainCamera().getPosition()
                : null;
        return localRay(framePosition, globalOrigin, globalDirection);
    }

    private RtsSableClientSpatialCompat.Ray localRay(
            BlockPos framePosition, Vec3 globalOrigin, Vec3 globalDirection) {
        Minecraft mc = this.screen == null ? null : this.screen.getMinecraft();
        return mc == null || mc.level == null
                ? new RtsSableClientSpatialCompat.Ray(globalOrigin, globalDirection)
                : RtsSableClientSpatialCompat.toRenderLocalRay(
                        mc.level, framePosition, globalOrigin, globalDirection);
    }

    public boolean isAwaiting(BuildShape currentShape) {
        return currentShape != BuildShape.BLOCK
                && this.session != null
                && this.session.shape() == currentShape
                && this.session.phase() == ShapeBuildTypes.Phase.READY_CONFIRM;
    }

    public boolean adjustDimension(int delta, boolean secondary, boolean height) {
        if (delta == 0 || this.session == null) {
            return false;
        }
        if (height && supportsHeight(this.session.shape())) {
            return adjustHeight(delta);
        }
        if (this.session.shape() == BuildShape.BLOCK
                || (this.session.phase() != ShapeBuildTypes.Phase.NEED_SECOND_POINT
                && this.session.phase() != ShapeBuildTypes.Phase.NEED_THIRD_POINT
                && this.session.phase() != ShapeBuildTypes.Phase.READY_CONFIRM)) {
            return false;
        }
        if (secondary) {
            this.footprintNudgeB = ShapeGeometryUtil.clampShapeOffset(this.footprintNudgeB + delta);
        } else {
            this.footprintNudgeA = ShapeGeometryUtil.clampShapeOffset(this.footprintNudgeA + delta);
        }
        return true;
    }

    public boolean canAdjustHeight(BuildShape currentShape) {
        return this.session != null
                && this.session.shape() == currentShape
                && supportsHeight(this.session.shape())
                && (this.session.shape() != BuildShape.LINE || isVerticalLine(BuildShape.LINE));
    }

    private static boolean supportsHeight(BuildShape shape) {
        return shape == BuildShape.LINE || shape == BuildShape.WALL
                || shape == BuildShape.CYLINDER || shape == BuildShape.BOX;
    }

    public boolean adjustHeight(int delta) {
        if (delta == 0 || this.session == null || !supportsHeight(this.session.shape())) {
            return false;
        }
        if (this.session.shape() == BuildShape.LINE && !isVerticalLine(BuildShape.LINE)) {
            return false;
        }
        if ((this.session.shape() == BuildShape.BOX
                || this.session.shape() == BuildShape.CYLINDER
                || this.session.shape() == BuildShape.WALL)
                && this.session.phase() != ShapeBuildTypes.Phase.NEED_THIRD_POINT) {
            return false;
        }
        int nextOffset = ShapeGeometryUtil.clampShapeOffset(this.session.boxHeightOffset() + delta);
        BlockPos nextPointB = this.session.pointB();
        if (this.session.shape() == BuildShape.LINE && this.session.pointA() != null) {
            nextPointB = this.session.pointA().offset(0, nextOffset, 0);
        }
        this.session = new ShapeBuildTypes.Session(
                this.session.shape(), this.session.planeFace(), this.session.placementFace(),
                this.session.pointA(), nextPointB, this.session.phase(), nextOffset,
                this.session.boxHeightMouseBaseY());
        return true;
    }

    private boolean isVerticalLine(BuildShape shape) {
        return shape == BuildShape.LINE && this.screen != null && this.screen.isRoundShapeVertical(BuildShape.LINE);
    }
}
