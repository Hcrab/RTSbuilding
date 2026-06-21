package com.rtsbuilding.rtsbuilding.common.shape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Area shape generator ??abstract base for shape-based coordinate generation.
 * <p>
 * Inspired by {@code BaseMode} from Building Gadgets 2.  Each concrete
 * subclass knows how to generate a set of {@link BlockPos} coordinates for
 * one geometric shape (box, wall, line, circle, etc.) given an
 * {@link AreaShapeInput}.
 * <p>
 * Generators produce <b>relative</b> offsets from the anchor position and
 * items, tools, energy, or network state.  The executor layer
 * ({@link com.rtsbuilding.rtsbuilding.common.AreaOperationExecutor}) owns
 * the actual block-state manipulation and item extraction.
 */
public abstract class AreaShapeGenerator {

    /**
     * Generates block positions for this shape.
     *
     * @param input    shape input parameters (anchor, bounds, face, etc.)
     * @param fillMode fill strategy (FILL / HOLLOW / SKELETON)
     * @return ordered list of absolute world positions
     */
    public abstract List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode);

    /**
     * Returns the human-readable / translation key suffix for this shape.
     */
    public abstract String getName();

    // ======================================================================
    // Shared validation helpers
    // ======================================================================

    /**
     * Validates that a block position is valid for placement.
     * <p>
     * Equivalent to {@code BaseMode.isPosValid()} ??checks build height,
     * world interaction permission, and whether the existing block can be
     * replaced.
     *
     * @param level   the world
     * @param pos     target position
     * @param state   the block state to place
     * @param player  the player performing the action
     * @return true if this position may be used for placement
     */
    public static boolean validatePlacementPosition(Level level, BlockPos pos, BlockState state, Player player) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        if (!state.canSurvive(level, pos)) {
            return false;
        }
        return level.getBlockState(pos).canBeReplaced();
    }

    /**
     * Validates that a block position is valid for destruction.
     * <p>
     * Checks build-height range, world-interaction permission, and whether
     * the existing block is air or unbreakable.
     *
     * @param level  the world
     * @param pos    target position
     * @param player the player performing the action
     * @return true if the block at this position may be destroyed
     */
    public static boolean validateDestroyPosition(ServerLevel level, BlockPos pos, Player player) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        return true;
    }

    /**
     * Clamps a shape offset to the maximum allowed range.
     */
    protected static int clampOffset(int value) {
        int max = 64;
        return Math.max(-max, Math.min(max, value));
    }

    /**
     * Computes the dot product of a delta vector against one axis.
     */
    protected static int dotDelta(int dx, int dy, int dz, Direction axis) {
        return (dx * axis.getStepX()) + (dy * axis.getStepY()) + (dz * axis.getStepZ());
    }

    /**
     * Resolves the two plane axes for a 2D shape based on the clicked face.
     */
    protected static Direction[] resolvePlaneAxes(Direction face) {
        return switch (face.getAxis()) {
            case Y -> new Direction[]{Direction.EAST, Direction.SOUTH};
            case X -> new Direction[]{Direction.UP, Direction.SOUTH};
            case Z -> new Direction[]{Direction.EAST, Direction.UP};
        };
    }

    /**
     * Builds positions from start to end along two plane axes.
     */
    protected static List<BlockPos> buildPlanePositions(BlockPos start, Direction axisA, Direction axisB,
                                                         int aMin, int aMax, int bMin, int bMax) {
        List<BlockPos> result = new ArrayList<>();
        for (int a = aMin; a <= aMax; a++) {
            for (int b = bMin; b <= bMax; b++) {
                int dx = (axisA.getStepX() * a) + (axisB.getStepX() * b);
                int dy = (axisA.getStepY() * a) + (axisB.getStepY() * b);
                int dz = (axisA.getStepZ() * a) + (axisB.getStepZ() * b);
                result.add(start.offset(dx, dy, dz));
            }
        }
        return result;
    }

    /**
     * Filters a position list to keep only boundary positions (for HOLLOW / SKELETON modes).
     */
    protected static List<BlockPos> filterBoundary(List<BlockPos> full, int minY, int maxY) {
        java.util.Set<BlockPos> set = new java.util.HashSet<>(full);
        List<BlockPos> boundary = new ArrayList<>();
        for (BlockPos pos : full) {
            boolean xEdge = !set.contains(pos.east()) || !set.contains(pos.west());
            boolean yEdge = !set.contains(pos.above()) || !set.contains(pos.below());
            boolean zEdge = !set.contains(pos.north()) || !set.contains(pos.south());
            int edges = (xEdge ? 1 : 0) + (yEdge ? 1 : 0) + (zEdge ? 1 : 0);
            if (edges >= 1) {
                boundary.add(pos);
            }
        }
        return boundary;
    }
}
