package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

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
    private RayTraceResult templateHit;
    private int footprintNudgeA;
    private int footprintNudgeB;
    private double cursorY;

    public void init(BuilderScreen screen, ShapeSelectionBoxController boxes) {
        this.screen = screen;
        this.boxes = boxes;
    }

    public ShapeBuildTypes.Session current() {
        return this.session;
    }

    public void replace(ShapeBuildTypes.Session session) {
        this.session = session;
    }

    public RayTraceResult templateHit() {
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
        this.boxes.clear();
    }

    public boolean shouldSubmitAfterSelection(boolean keyboardConfirmEnabled) {
        return this.session != null
                && ShapeConfirmationPolicy.shouldSubmitAfterSelection(keyboardConfirmEnabled, this.session.phase());
    }

    public void advance(RayTraceResult hit, Vec3d rayDir, double mouseY, BuildShape shape) {
        if (this.session == null || this.session.shape() != shape) {
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

    private void start(RayTraceResult hit, Vec3d rayDir, double mouseY, BuildShape shape) {
        this.footprintNudgeA = 0;
        this.footprintNudgeB = 0;
        EnumFacing placementFace = ShapeGeometryUtil.resolveShapePlacementFace(shape, hit.sideHit, rayDir);
        this.templateHit = new RayTraceResult(hit.hitVec, placementFace, hit.getBlockPos());
        this.session = new ShapeBuildTypes.Session(
                shape,
                resolveBuildFace(shape, hit.sideHit, rayDir),
                placementFace,
                hit.getBlockPos(),
                null,
                ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                0,
                mouseY);
    }

    private EnumFacing resolveBuildFace(BuildShape shape, EnumFacing clickedFace, Vec3d rayDir) {
        if (shape != BuildShape.CIRCLE && shape != BuildShape.CYLINDER) {
            return ShapeGeometryUtil.resolveShapeBuildFace(shape, clickedFace, rayDir);
        }
        if (this.screen == null || !this.screen.isRoundShapeVertical(shape)) {
            return EnumFacing.UP;
        }
        if (rayDir != null && (Math.abs(rayDir.x) > 1.0E-5D || Math.abs(rayDir.z) > 1.0E-5D)) {
            EnumFacing nearest = EnumFacing.getFacingFromVector((float) rayDir.x, 0.0F, (float) rayDir.z);
            if (nearest.getAxis() != EnumFacing.Axis.Y) {
                return nearest;
            }
        }
        return clickedFace != null && clickedFace.getAxis() != EnumFacing.Axis.Y ? clickedFace : EnumFacing.NORTH;
    }

    private void advanceLine(RayTraceResult hit) {
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

    private void advanceTwoPoint(RayTraceResult hit, double mouseY) {
        if (this.session.phase() != ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            return;
        }
        BlockPos pointB = this.boxes.isAdvanced(this.session.shape())
                ? resolveAdvancedSecondPoint(this.session, hit)
                : resolvePlanePoint(this.session, hit);
        this.session = ready(this.session, pointB, mouseY);
    }

    private void advanceHeightShape(RayTraceResult hit, double mouseY) {
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

    private void advanceBox(RayTraceResult hit, double mouseY) {
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

    private BlockPos resolveAdvancedSecondPoint(ShapeBuildTypes.Session base, RayTraceResult hit) {
        if (hit == null) {
            return resolvePlanePoint(base, null);
        }
        Minecraft mc = this.screen.getMinecraft();
        BlockPos clicked = hit.getBlockPos();
        if (mc != null && mc.world != null) {
            net.minecraft.block.state.IBlockState state = mc.world.getBlockState(clicked);
            if (state.getBlock().isAir(state, mc.world, clicked)) {
                return resolvePlanePoint(base, hit);
            }
        }
        return clicked;
    }

    public ShapeBuildTypes.Input resolveInput(
            RayTraceResult cursorHit,
            boolean requireReady,
            BuildShape currentShape,
            boolean lineConnected) {
        if (this.session == null || this.session.shape() != currentShape) {
            return null;
        }
        Minecraft mc = this.screen.getMinecraft();
        Vec3d rayOrigin = cameraOrigin(mc);
        Vec3d rayDirection = mc != null ? this.screen.computeCursorRayDirection() : null;
        return ShapeSessionInputResolver.resolve(
                this.session, cursorHit, requireReady, isVerticalLine(this.session.shape()), lineConnected,
                this.footprintNudgeA, this.footprintNudgeB, rayOrigin, rayDirection);
    }

    private BlockPos resolvePlanePoint(ShapeBuildTypes.Session base, RayTraceResult cursorHit) {
        Minecraft mc = this.screen.getMinecraft();
        Vec3d origin = cameraOrigin(mc);
        Vec3d direction = mc != null ? this.screen.computeCursorRayDirection() : null;
        return ShapeSessionInputResolver.resolvePlanePoint(base, cursorHit, origin, direction);
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
            nextPointB = this.session.pointA().add(0, nextOffset, 0);
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

    private static Vec3d cameraOrigin(Minecraft minecraft) {
        if (minecraft == null || minecraft.getRenderViewEntity() == null) return null;
        return minecraft.getRenderViewEntity().getPositionEyes(minecraft.getRenderPartialTicks());
    }
}
