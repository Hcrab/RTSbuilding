package com.rtsbuilding.rtsbuilding.common.shape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Input parameters for area shape generation.
 * <p>
 * Carries the anchor point, the two bounding corners that define the shape
 * footprint, a height offset (for 3D shapes like BOX / WALL), the clicked
 * face direction, and the placement face.
 *
 * @param start        anchor / first corner position
 * @param end          second corner position (defines extent)
 * @param heightOffset vertical offset from the base plane (may be 0 for 2D shapes)
 * @param clickedFace  the face the player clicked on
 * @param placementFace the face to place against
 */
public record AreaShapeInput(
        BlockPos start,
        BlockPos end,
        int heightOffset,
        Direction clickedFace,
        Direction placementFace) {

    /**
     * Creates a minimal input with just two corners and default faces.
     */
    public static AreaShapeInput of(BlockPos start, BlockPos end) {
        return new AreaShapeInput(start, end, 0, Direction.UP, Direction.UP);
    }

    /**
     * Creates an input for destruction operations (no placement face needed).
     */
    public static AreaShapeInput destroy(BlockPos start, BlockPos end) {
        return new AreaShapeInput(start, end, 0, Direction.DOWN, Direction.DOWN);
    }

    /**
     * Creates a full input with all parameters.
     */
    public static AreaShapeInput of(BlockPos start, BlockPos end, int heightOffset,
                                     Direction clickedFace, Direction placementFace) {
        return new AreaShapeInput(start, end, heightOffset, clickedFace, placementFace);
    }
}
