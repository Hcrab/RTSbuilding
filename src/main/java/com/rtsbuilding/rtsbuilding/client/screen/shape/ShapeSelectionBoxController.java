package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsBoxHandleInteraction;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionBoxAnimator;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.SHAPE_MAX_DIMENSION;

/**
 * 高级形状范围框的唯一交互 owner。
 *
 * <p>它负责 handle 命中、拖动、滚轮缩放、平移动画和尺寸硬限制；不推进多点击会话，也不
 * 生成方块或触发世界副作用。会话通过窄读写端口接入，避免把状态复制到两个模块。</p>
 */
public final class ShapeSelectionBoxController {
    private static final int DEFAULT_AREA_MINE_MAX_SIZE = 36;
    private static final int DEFAULT_AREA_MINE_MAX_VOLUME =
            DEFAULT_AREA_MINE_MAX_SIZE * DEFAULT_AREA_MINE_MAX_SIZE * DEFAULT_AREA_MINE_MAX_SIZE;
    private static final RangeDestroySelectionLimiter.Limits SHAPE_LIMITS =
            new RangeDestroySelectionLimiter.Limits(
                    SHAPE_MAX_DIMENSION, SHAPE_MAX_DIMENSION, SHAPE_MAX_DIMENSION,
                    SHAPE_MAX_DIMENSION * SHAPE_MAX_DIMENSION * SHAPE_MAX_DIMENSION);

    private final RtsBoxHandleInteraction handles = new RtsBoxHandleInteraction();
    private final RtsSelectionBoxAnimator animator = new RtsSelectionBoxAnimator();
    private BuilderScreen screen;
    private Supplier<ShapeBuildTypes.Session> sessionReader;
    private Consumer<ShapeBuildTypes.Session> sessionWriter;

    public void init(
            BuilderScreen screen,
            Supplier<ShapeBuildTypes.Session> sessionReader,
            Consumer<ShapeBuildTypes.Session> sessionWriter) {
        this.screen = screen;
        this.sessionReader = sessionReader;
        this.sessionWriter = sessionWriter;
    }

    public void clear() {
        this.handles.clear();
        this.animator.clear();
    }

    public boolean isAdvanced(BuildShape shape) {
        return this.screen != null && this.screen.isAdvancedShapeMode()
                && shape != null && shape != BuildShape.BLOCK && shape != BuildShape.LINE;
    }

    public boolean hasEditableSession() {
        ShapeBuildTypes.Session session = current();
        return isAdvanced(session == null ? null : session.shape())
                && session.phase() == ShapeBuildTypes.Phase.READY_CONFIRM
                && session.pointB() != null;
    }

    public RtsCullingBox box() {
        return hasEditableSession() ? AdvancedShapeSelectionGeometry.boxFromSession(current()) : null;
    }

    public AABB renderAabb(RtsCullingBox generatedBounds) {
        return generatedBounds == null ? null : this.animator.renderAabb(generatedBounds);
    }

    public Direction hoveredHandle() {
        return this.handles.hoveredDirection();
    }

    public Direction activeHandle() {
        return this.handles.activeDirection();
    }

    public Set<Direction> allowedDirections() {
        if (!hasEditableSession()) {
            return Set.of();
        }
        ShapeBuildTypes.Session session = current();
        if (session.shape() == BuildShape.SQUARE) {
            return EnumSet.of(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH);
        }
        if (session.shape() == BuildShape.CIRCLE) {
            Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(session.shape(), session.planeFace());
            EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
            for (Direction axis : axes) {
                directions.add(axis);
                directions.add(axis.getOpposite());
            }
            return directions;
        }
        if (session.shape() == BuildShape.WALL) {
            RtsCullingBox box = AdvancedShapeSelectionGeometry.boxFromSession(session);
            return box.width() >= box.depth()
                    ? EnumSet.of(Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN)
                    : EnumSet.of(Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN);
        }
        return EnumSet.allOf(Direction.class);
    }

    public void updateHover(Vec3 origin, Vec3 rayDirection, boolean enabled) {
        RtsCullingBox box = box();
        this.handles.updateHover(box, origin, rayDirection, enabled && box != null, allowedDirections());
    }

    public boolean click(Vec3 origin, Vec3 rayDirection) {
        RtsCullingBox box = box();
        return box != null && this.handles.clickHandle(box, origin, rayDirection, allowedDirections()).handled();
    }

    public boolean scroll(double scrollY, boolean fast) {
        return this.handles.handleScroll(scrollY, fast, this::resize);
    }

    public boolean drag(double dragX, double dragY, double axisX, double axisY) {
        return this.handles.handleDrag(dragX, dragY, axisX, axisY, this::resize);
    }

    public boolean releaseIfDragged() {
        return this.handles.releaseActiveHandleIfDragged();
    }

    private boolean resize(Direction direction, int delta) {
        if (direction == null || delta == 0 || !hasEditableSession()) {
            return false;
        }
        ShapeBuildTypes.Session session = current();
        RtsCullingBox before = AdvancedShapeSelectionGeometry.boxFromSession(session);
        RtsCullingBox after = before.resizeFromHandle(direction, delta);
        if (!withinCaps(after)) {
            return true;
        }
        this.animator.animate(before, after);
        update(AdvancedShapeSelectionGeometry.sessionFromBox(session, after));
        return true;
    }

    public boolean nudge(int dx, int dy, int dz) {
        ShapeBuildTypes.Session session = current();
        if (session == null || (dx == 0 && dy == 0 && dz == 0)
                || session.shape() == BuildShape.BLOCK || session.pointB() == null
                || session.phase() == ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            return false;
        }
        RtsCullingBox oldBox = hasEditableSession()
                ? AdvancedShapeSelectionGeometry.boxFromSession(session)
                : null;
        ShapeBuildTypes.Session moved = new ShapeBuildTypes.Session(
                session.shape(), session.planeFace(), session.placementFace(),
                session.pointA().offset(dx, dy, dz), session.pointB().offset(dx, dy, dz),
                session.phase(), session.boxHeightOffset(), session.boxHeightMouseBaseY());
        update(moved);
        if (oldBox != null) {
            this.animator.animate(oldBox, AdvancedShapeSelectionGeometry.boxFromSession(moved));
        }
        return true;
    }

    public RtsCullingBox clamp(RtsCullingBox box, BlockPos anchor) {
        return RangeDestroySelectionLimiter.clampBox(
                box, anchor, isRangeDestroy() ? currentRangeDestroyLimits() : SHAPE_LIMITS);
    }

    private boolean withinCaps(RtsCullingBox box) {
        if (box == null) {
            return false;
        }
        if (isRangeDestroy()) {
            return RangeDestroySelectionLimiter.contains(box, currentRangeDestroyLimits());
        }
        return box.width() <= SHAPE_MAX_DIMENSION
                && box.height() <= SHAPE_MAX_DIMENSION
                && box.depth() <= SHAPE_MAX_DIMENSION;
    }

    private boolean isRangeDestroy() {
        return this.screen != null && this.screen.isQuickBuildRangeDestroyMode();
    }

    public static RangeDestroySelectionLimiter.Limits currentRangeDestroyLimits() {
        return new RangeDestroySelectionLimiter.Limits(
                configInt(Config::areaMineMaxWidth, DEFAULT_AREA_MINE_MAX_SIZE),
                configInt(Config::areaMineMaxHeight, DEFAULT_AREA_MINE_MAX_SIZE),
                configInt(Config::areaMineMaxDepth, DEFAULT_AREA_MINE_MAX_SIZE),
                configInt(Config::areaMineMaxVolume, DEFAULT_AREA_MINE_MAX_VOLUME));
    }

    private static int configInt(java.util.function.IntSupplier supplier, int fallback) {
        try {
            return Math.max(1, supplier.getAsInt());
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    private ShapeBuildTypes.Session current() {
        return this.sessionReader == null ? null : this.sessionReader.get();
    }

    private void update(ShapeBuildTypes.Session session) {
        if (this.sessionWriter != null) {
            this.sessionWriter.accept(session);
        }
    }
}
