package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;

/**
 * Container for shape-building data types used in the multi-click shape
 * build pipeline (line, square, wall, circle, box).
 * <p>
 * Groups three closely coupled types that are always used together in the
 * shape build flow:
 * <ul>
 *   <li>{@link Phase} — enum for the current interaction stage</li>
 *   <li>{@link Input} — immutable input parameters for geometry computation</li>
 *   <li>{@link Session} — full session state including phase and height drag ref</li>
 * </ul>
 * <p>
 * All types here are tightly coupled to {@link ShapeGeometryUtil} and
 */
public final class ShapeBuildTypes {

    /**
     * Shape build phase.
     * <p>
     * Represents the current interaction stage when the player is defining
     * a shape through successive clicks:
     * <ul>
     *   <li>{@link #NEED_SECOND_POINT} — first anchor placed, waiting for
     *       the second anchor click</li>
     *   <li>{@link #NEED_THIRD_POINT} — second anchor placed, waiting for
     *       height drag input (cube only)</li>
     *   <li>{@link #READY_CONFIRM} — all anchors determined, waiting for
     *       placement confirmation</li>
     * </ul>
     */
    public enum Phase {
        NEED_SECOND_POINT,
        NEED_THIRD_POINT,
        READY_CONFIRM
    }

    /**
     * Shape build input (immutable).
     * <p>
     * Contains all parameters needed to compute the block positions for a
     * shape, including the shape kind, reference plane orientation,
     * placement face, two anchor positions, and an optional height offset
     * used only for the BOX shape.
     *
     * @param shape          the shape kind (LINE, SQUARE, WALL, CIRCLE, BOX)
     * @param planeFace      the reference-plane direction the shape lives on
     * @param placementFace  the face toward which blocks are placed
     * @param pointA         first anchor point (origin corner)
     * @param pointB         second anchor point (opposite corner / end)
     * @param boxHeightOffset height offset in blocks (BOX only, 0 otherwise)
     */
    public static final class Input {
        private final BuildShape shape;
        private final EnumFacing planeFace;
        private final EnumFacing placementFace;
        private final BlockPos pointA;
        private final BlockPos pointB;
        private final int boxHeightOffset;
        private final boolean connectedLine;

        public Input(BuildShape shape, EnumFacing planeFace, EnumFacing placementFace,
                BlockPos pointA, BlockPos pointB, int boxHeightOffset, boolean connectedLine) {
            this.shape = shape;
            this.planeFace = planeFace;
            this.placementFace = placementFace;
            this.pointA = pointA;
            this.pointB = pointB;
            this.boxHeightOffset = boxHeightOffset;
            this.connectedLine = connectedLine;
        }

        public BuildShape shape() { return this.shape; }
        public EnumFacing planeFace() { return this.planeFace; }
        public EnumFacing placementFace() { return this.placementFace; }
        public BlockPos pointA() { return this.pointA; }
        public BlockPos pointB() { return this.pointB; }
        public int boxHeightOffset() { return this.boxHeightOffset; }
        public boolean connectedLine() { return this.connectedLine; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Input)) return false;
            Input that = (Input) other;
            return this.boxHeightOffset == that.boxHeightOffset && this.connectedLine == that.connectedLine
                    && java.util.Objects.equals(this.shape, that.shape)
                    && java.util.Objects.equals(this.planeFace, that.planeFace)
                    && java.util.Objects.equals(this.placementFace, that.placementFace)
                    && java.util.Objects.equals(this.pointA, that.pointA)
                    && java.util.Objects.equals(this.pointB, that.pointB);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(this.shape, this.planeFace, this.placementFace,
                    this.pointA, this.pointB, this.boxHeightOffset, this.connectedLine);
        }
    }

    /**
     * Shape build session (immutable, extends the Input concept).
     * <p>
     * Tracks the complete interactive state of an ongoing shape build.
     * Adds the current {@link Phase}, a height-offset value, and a
     * Y-coordinate reference for mouse-based height dragging.
     *
     * @param shape               the shape kind
     * @param planeFace           the reference-plane direction
     * @param placementFace       the placement face
     * @param pointA              first anchor point
     * @param pointB              second anchor point (null until placed)
     * @param phase               current interaction stage
     * @param boxHeightOffset     height offset in blocks (BOX only)
     * @param boxHeightMouseBaseY screen Y at which height-drag started
     */
    public static final class Session {
        private final BuildShape shape;
        private final EnumFacing planeFace;
        private final EnumFacing placementFace;
        private final BlockPos pointA;
        private final BlockPos pointB;
        private final Phase phase;
        private final int boxHeightOffset;
        private final double boxHeightMouseBaseY;

        public Session(BuildShape shape, EnumFacing planeFace, EnumFacing placementFace,
                BlockPos pointA, BlockPos pointB, Phase phase, int boxHeightOffset,
                double boxHeightMouseBaseY) {
            this.shape = shape;
            this.planeFace = planeFace;
            this.placementFace = placementFace;
            this.pointA = pointA;
            this.pointB = pointB;
            this.phase = phase;
            this.boxHeightOffset = boxHeightOffset;
            this.boxHeightMouseBaseY = boxHeightMouseBaseY;
        }

        public BuildShape shape() { return this.shape; }
        public EnumFacing planeFace() { return this.planeFace; }
        public EnumFacing placementFace() { return this.placementFace; }
        public BlockPos pointA() { return this.pointA; }
        public BlockPos pointB() { return this.pointB; }
        public Phase phase() { return this.phase; }
        public int boxHeightOffset() { return this.boxHeightOffset; }
        public double boxHeightMouseBaseY() { return this.boxHeightMouseBaseY; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Session)) return false;
            Session that = (Session) other;
            return this.boxHeightOffset == that.boxHeightOffset
                    && Double.compare(this.boxHeightMouseBaseY, that.boxHeightMouseBaseY) == 0
                    && this.shape == that.shape && this.planeFace == that.planeFace
                    && this.placementFace == that.placementFace && this.phase == that.phase
                    && java.util.Objects.equals(this.pointA, that.pointA)
                    && java.util.Objects.equals(this.pointB, that.pointB);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(this.shape, this.planeFace, this.placementFace, this.pointA,
                    this.pointB, this.phase, this.boxHeightOffset, this.boxHeightMouseBaseY);
        }
    }



    private ShapeBuildTypes() {}
}
