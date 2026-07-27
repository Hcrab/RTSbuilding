package com.rtsbuilding.rtsbuilding.common.shape.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Area shape generation input parameters — encapsulates all geometric parameters needed by the shape generator.
 * <p>
 * Carries the anchor position, two corner points (defining the shape's coverage),
 * height offset (for 3D shapes such as BOX / WALL), clicked face direction, and placement face direction.
 *
 * @param start         anchor / first corner position
 * @param end           second corner position (defines the shape's extent)
 * @param heightOffset  vertical offset relative to the base plane (0 for 2D shapes)
 * @param clickedFace   the direction of the face the player clicked
 * @param placementFace the direction of the face for block placement
 */
public record AreaShapeInput(
        BlockPos start,
        BlockPos end,
        int heightOffset,
        Direction clickedFace,
        Direction placementFace) {

    /**
     * Create a minimal input with only two corner points (defaults to UP direction).
     *
     * @param start first corner position
     * @param end   second corner position
     * @return AreaShapeInput instance
     */
    public static AreaShapeInput of(BlockPos start, BlockPos end) {
        return new AreaShapeInput(start, end, 0, Direction.UP, Direction.UP);
    }

    /**
     * Create an input dedicated to destroy operations (no placement face direction needed).
     *
     * @param start first corner position
     * @param end   second corner position
     * @return AreaShapeInput instance
     */
    public static AreaShapeInput destroy(BlockPos start, BlockPos end) {
        return new AreaShapeInput(start, end, 0, Direction.DOWN, Direction.DOWN);
    }

    /**
     * Create a complete input with all parameters.
     *
     * @param start         first corner position
     * @param end           second corner position
     * @param heightOffset  height offset
     * @param clickedFace   clicked face direction
     * @param placementFace placement face direction
     * @return AreaShapeInput instance
     */
    public static AreaShapeInput of(BlockPos start, BlockPos end, int heightOffset,
                                     Direction clickedFace, Direction placementFace) {
        return new AreaShapeInput(start, end, heightOffset, clickedFace, placementFace);
    }
}
