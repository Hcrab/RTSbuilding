package com.rtsbuilding.rtsbuilding.client.screen.culling;

import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 共享的六向手柄悬停、锁定、滚轮与拖拽状态；不拥有面板或世界状态。 */
public final class RtsBoxHandleInteraction {
    private static final double HANDLE_RAY_DISTANCE = 128.0D;
    private static final int FAST_SCROLL_STEP = 4;
    private static final double DRAG_PIXELS_PER_BLOCK = 18.0D;

    private EnumFacing hoveredDirection;
    private EnumFacing activeDirection;
    private double dragPixels;
    private boolean draggedActiveHandle;

    public EnumFacing hoveredDirection() { return hoveredDirection; }
    public EnumFacing activeDirection() { return activeDirection; }

    public void clear() {
        hoveredDirection = null;
        activeDirection = null;
        dragPixels = 0.0D;
        draggedActiveHandle = false;
    }

    public boolean releaseActiveHandle() {
        if (activeDirection == null) return false;
        activeDirection = null;
        dragPixels = 0.0D;
        draggedActiveHandle = false;
        return true;
    }

    public boolean releaseActiveHandleIfDragged() {
        return draggedActiveHandle && releaseActiveHandle();
    }

    public void updateHover(RtsCullingBox box, Vec3d origin, Vec3d rayDirection, boolean enabled) {
        updateHover(box, origin, rayDirection, enabled, null);
    }

    public void updateHover(RtsCullingBox box, Vec3d origin, Vec3d rayDirection, boolean enabled,
            Set<EnumFacing> allowedDirections) {
        hoveredDirection = null;
        if (!enabled || activeDirection != null) return;
        hoveredDirection = nearestHandle(box, origin, rayDirection, allowedDirections).orElse(null);
    }

    public ClickResult clickHandle(RtsCullingBox box, Vec3d origin, Vec3d rayDirection) {
        return clickHandle(box, origin, rayDirection, null);
    }

    public ClickResult clickHandle(RtsCullingBox box, Vec3d origin, Vec3d rayDirection,
            Set<EnumFacing> allowedDirections) {
        Optional<EnumFacing> hit = nearestHandle(box, origin, rayDirection, allowedDirections);
        if (!hit.isPresent()) return ClickResult.none();
        EnumFacing clicked = hit.get();
        hoveredDirection = clicked;
        dragPixels = 0.0D;
        draggedActiveHandle = false;
        if (activeDirection == clicked) {
            activeDirection = null;
            return new ClickResult(ClickKind.RELEASED, clicked);
        }
        activeDirection = clicked;
        return new ClickResult(ClickKind.SELECTED, clicked);
    }

    public boolean handleScroll(double scrollY, boolean fast, ResizeSink sink) {
        if (activeDirection == null || sink == null) return false;
        int delta = scrollY > 0.0D ? 1 : -1;
        if (fast) delta *= FAST_SCROLL_STEP;
        return sink.resize(activeDirection, delta);
    }

    public boolean handleDrag(double dragX, double dragY, double axisX, double axisY, ResizeSink sink) {
        if (activeDirection == null || sink == null) return false;
        if (Math.abs(dragX) + Math.abs(dragY) > 1.0E-4D) draggedActiveHandle = true;
        double axisLength = Math.sqrt(axisX * axisX + axisY * axisY);
        if (axisLength < 1.0E-5D) {
            axisX = 0.0D;
            axisY = -1.0D;
            axisLength = 1.0D;
        }
        dragPixels += dragX * (axisX / axisLength) + dragY * (axisY / axisLength);
        int steps = (int) (dragPixels / DRAG_PIXELS_PER_BLOCK);
        if (steps == 0) return true;
        dragPixels -= steps * DRAG_PIXELS_PER_BLOCK;
        return sink.resize(activeDirection, steps);
    }

    private static Optional<EnumFacing> nearestHandle(RtsCullingBox box, Vec3d origin,
            Vec3d rayDirection, Set<EnumFacing> allowedDirections) {
        Optional<RtsCullingAxisHandle.HandleHit> hit = RtsCullingAxisHandle.nearestHit(
                box, origin, rayDirection, HANDLE_RAY_DISTANCE, allowedDirections);
        return hit.isPresent() ? Optional.of(hit.get().direction()) : Optional.<EnumFacing>empty();
    }

    @FunctionalInterface
    public interface ResizeSink { boolean resize(EnumFacing direction, int delta); }

    public enum ClickKind { NONE, SELECTED, RELEASED }

    public static final class ClickResult {
        private final ClickKind kind;
        private final EnumFacing direction;
        public ClickResult(ClickKind kind, EnumFacing direction) { this.kind = kind; this.direction = direction; }
        static ClickResult none() { return new ClickResult(ClickKind.NONE, null); }
        public ClickKind kind() { return kind; }
        public EnumFacing direction() { return direction; }
        public boolean handled() { return kind != ClickKind.NONE; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ClickResult)) return false;
            ClickResult that = (ClickResult) other;
            return kind == that.kind && direction == that.direction;
        }
        @Override public int hashCode() { return Objects.hash(kind, direction); }
        @Override public String toString() { return "ClickResult[kind=" + kind + ", direction=" + direction + "]"; }
    }
}
