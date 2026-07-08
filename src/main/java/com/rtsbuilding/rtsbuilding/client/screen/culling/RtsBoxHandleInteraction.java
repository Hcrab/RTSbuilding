package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Shared six-direction box handle interaction state.
 *
 * <p>This class owns only handle hover, lock/release, scroll, and drag-to-resize math.
 * It intentionally does not own blueprint persistence, culling persistence, block exclusion lists,
 * or world refresh. Blueprint capture and range culling both use this layer so the same arrow
 * interaction keeps the same feel in both tools.
 */
public final class RtsBoxHandleInteraction {
    private static final double HANDLE_RAY_DISTANCE = 128.0D;
    private static final int FAST_SCROLL_STEP = 4;
    private static final double DRAG_PIXELS_PER_BLOCK = 18.0D;

    private Direction hoveredDirection;
    private Direction activeDirection;
    private double dragPixels;
    private boolean draggedActiveHandle;

    public Direction hoveredDirection() {
        return hoveredDirection;
    }

    public Direction activeDirection() {
        return activeDirection;
    }

    public void clear() {
        hoveredDirection = null;
        activeDirection = null;
        dragPixels = 0.0D;
        draggedActiveHandle = false;
    }

    public boolean releaseActiveHandle() {
        if (activeDirection == null) {
            return false;
        }
        activeDirection = null;
        dragPixels = 0.0D;
        draggedActiveHandle = false;
        return true;
    }

    public boolean releaseActiveHandleIfDragged() {
        return draggedActiveHandle && releaseActiveHandle();
    }

    public void updateHover(RtsCullingBox box, Vec3 origin, Vec3 rayDirection, boolean enabled) {
        hoveredDirection = null;
        if (!enabled || activeDirection != null) {
            return;
        }
        hoveredDirection = nearestHandle(box, origin, rayDirection).orElse(null);
    }

    public ClickResult clickHandle(RtsCullingBox box, Vec3 origin, Vec3 rayDirection) {
        Optional<Direction> hit = nearestHandle(box, origin, rayDirection);
        if (hit.isEmpty()) {
            return ClickResult.none();
        }
        Direction clicked = hit.get();
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
        if (activeDirection == null || sink == null) {
            return false;
        }
        int delta = scrollY > 0.0D ? 1 : -1;
        if (fast) {
            delta *= FAST_SCROLL_STEP;
        }
        return sink.resize(activeDirection, delta);
    }

    public boolean handleDrag(double dragX, double dragY, double axisX, double axisY, ResizeSink sink) {
        if (activeDirection == null || sink == null) {
            return false;
        }
        if (Math.abs(dragX) + Math.abs(dragY) > 1.0E-4D) {
            draggedActiveHandle = true;
        }
        double axisLength = Math.sqrt(axisX * axisX + axisY * axisY);
        if (axisLength < 1.0E-5D) {
            axisX = 0.0D;
            axisY = -1.0D;
            axisLength = 1.0D;
        }
        double projectedPixels = dragX * (axisX / axisLength) + dragY * (axisY / axisLength);
        dragPixels += projectedPixels;
        int steps = (int) (dragPixels / DRAG_PIXELS_PER_BLOCK);
        if (steps == 0) {
            return true;
        }
        dragPixels -= steps * DRAG_PIXELS_PER_BLOCK;
        return sink.resize(activeDirection, steps);
    }

    private static Optional<Direction> nearestHandle(RtsCullingBox box, Vec3 origin, Vec3 rayDirection) {
        return RtsCullingAxisHandle.nearestHit(box, origin, rayDirection, HANDLE_RAY_DISTANCE)
                .map(RtsCullingAxisHandle.HandleHit::direction);
    }

    @FunctionalInterface
    public interface ResizeSink {
        boolean resize(Direction direction, int delta);
    }

    public enum ClickKind {
        NONE,
        SELECTED,
        RELEASED
    }

    public record ClickResult(ClickKind kind, Direction direction) {
        static ClickResult none() {
            return new ClickResult(ClickKind.NONE, null);
        }

        public boolean handled() {
            return kind != ClickKind.NONE;
        }
    }
}
