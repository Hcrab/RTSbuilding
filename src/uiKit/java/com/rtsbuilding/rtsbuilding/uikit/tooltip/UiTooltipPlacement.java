package com.rtsbuilding.rtsbuilding.uikit.tooltip;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/** Tooltip 的方向选择和屏幕钳位算法。 */
public final class UiTooltipPlacement {
    public enum Direction {
        RIGHT,
        LEFT,
        BELOW,
        ABOVE
    }

    private UiTooltipPlacement() {
    }

    public static Result place(UiRect screen, UiRect anchor, double width, double height,
                               double gap, Direction preferred) {
        if (screen == null || anchor == null || preferred == null) {
            throw new IllegalArgumentException("screen, anchor and preferred must not be null");
        }
        if (width <= 0.0D || height <= 0.0D || gap < 0.0D) {
            throw new IllegalArgumentException("tooltip size must be positive and gap non-negative");
        }
        Direction[] order = order(preferred);
        for (Direction direction : order) {
            UiRect candidate = candidate(anchor, width, height, gap, direction);
            if (screen.contains(candidate)) {
                return new Result(candidate, direction, false);
            }
        }
        UiRect preferredRect = candidate(anchor, width, height, gap, preferred);
        return new Result(preferredRect.clampWithin(screen), preferred, true);
    }

    private static UiRect candidate(UiRect anchor, double width, double height,
                                    double gap, Direction direction) {
        switch (direction) {
            case RIGHT:
                return new UiRect(anchor.right() + gap, anchor.getY(), width, height);
            case LEFT:
                return new UiRect(anchor.getX() - gap - width, anchor.getY(), width, height);
            case BELOW:
                return new UiRect(anchor.getX(), anchor.bottom() + gap, width, height);
            case ABOVE:
                return new UiRect(anchor.getX(), anchor.getY() - gap - height, width, height);
            default:
                throw new IllegalStateException("unknown tooltip direction");
        }
    }

    private static Direction[] order(Direction preferred) {
        switch (preferred) {
            case RIGHT:
                return new Direction[] {Direction.RIGHT, Direction.LEFT, Direction.BELOW, Direction.ABOVE};
            case LEFT:
                return new Direction[] {Direction.LEFT, Direction.RIGHT, Direction.BELOW, Direction.ABOVE};
            case BELOW:
                return new Direction[] {Direction.BELOW, Direction.ABOVE, Direction.RIGHT, Direction.LEFT};
            case ABOVE:
                return new Direction[] {Direction.ABOVE, Direction.BELOW, Direction.RIGHT, Direction.LEFT};
            default:
                throw new IllegalStateException("unknown tooltip direction");
        }
    }

    public static final class Result {
        private final UiRect bounds;
        private final Direction direction;
        private final boolean clamped;

        private Result(UiRect bounds, Direction direction, boolean clamped) {
            this.bounds = bounds;
            this.direction = direction;
            this.clamped = clamped;
        }

        public UiRect getBounds() {
            return bounds;
        }

        public Direction getDirection() {
            return direction;
        }

        public boolean isClamped() {
            return clamped;
        }
    }
}
