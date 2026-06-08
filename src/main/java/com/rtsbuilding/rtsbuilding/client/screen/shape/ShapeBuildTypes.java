package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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
 * {@link com.rtsbuilding.rtsbuilding.client.screen.ScreenShapeController}.
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
    public record Input(
            BuildShape shape,
            Direction planeFace,
            Direction placementFace,
            BlockPos pointA,
            BlockPos pointB,
            int boxHeightOffset,
            boolean connectedLine) {}

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
    public record Session(
            BuildShape shape,
            Direction planeFace,
            Direction placementFace,
            BlockPos pointA,
            BlockPos pointB,
            Phase phase,
            int boxHeightOffset,
            double boxHeightMouseBaseY) {}



    private ShapeBuildTypes() {}
}
