package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.common.shape.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Central executor for area-based building and destruction operations.
 * <p>
 * Inspired by {@code BuildingUtils.build()} / {@code BuildingUtils.removeTickHandler()}
 * from Building Gadgets 2.  This class orchestrates the full pipeline:
 * <ol>
 *   <li>Shape-based position generation</li>
 *   <li>Per-position validation (world access, breakability, replaceability)</li>
 *   <li>Item extraction from linked storage</li>
 *   <li>Server-side execution via tick handlers or direct block manipulation</li>
 *   <li>History recording for undo</li>
 * </ol>
 * <p>
 * This is a stateless utility — all state lives in the caller's session.
 */
public final class AreaOperationExecutor {

    private AreaOperationExecutor() {
    }

    // ======================================================================
    //  Area Placement — batch place one block state at many positions
    // ======================================================================

    /**
     * Generates the target positions for an area placement operation.
     *
     * @param shape     the shape type
     * @param start     anchor position
     * @param end       second corner position
     * @param height    height offset for 3D shapes
     * @param face      placement face
     * @param fillMode  fill strategy
     * @return list of absolute world positions to place at
     */
    public static List<BlockPos> generatePlacementPositions(AreaShape shape, BlockPos start, BlockPos end,
                                                             int height, Direction face, ShapeFillMode fillMode) {
        AreaShapeGenerator generator = ShapeGeneratorRegistry.getGenerator(shape);
        AreaShapeInput input = AreaShapeInput.of(start, end, height, face, face);
        return generator.generatePositions(input, fillMode);
    }

    /**
     * Generates the target positions for an area destruction operation.
     * <p>
     * Semantically identical to {@link #generatePlacementPositions} — the
     * position list is the same; the caller decides whether to place or destroy.
     *
     * @param shape     the shape type
     * @param start     anchor position
     * @param end       second corner position
     * @param height    height offset for 3D shapes
     * @param face      clicked face
     * @param fillMode  fill strategy
     * @return list of absolute world positions to attempt destruction on
     */
    public static List<BlockPos> generateDestroyPositions(AreaShape shape, BlockPos start, BlockPos end,
                                                           int height, Direction face, ShapeFillMode fillMode) {
        return generatePlacementPositions(shape, start, end, height, face, fillMode);
    }

    /**
     * Filters a list of destruction targets to only those that are valid for
     * breaking: non-air, within world-access range, and with a valid destroy
     * speed.
     *
     * @param level   the server world
     * @param targets raw position list
     * @param player  the player performing the action
     * @return filtered list of breakable positions
     */
    public static List<BlockPos> filterBreakableTargets(ServerLevel level, List<BlockPos> targets, ServerPlayer player) {
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : targets) {
            if (pos == null) continue;
            if (!level.mayInteract(player, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) continue;
            valid.add(pos.immutable());
        }
        return valid;
    }

    /**
     * Filters a list of placement targets to only those that are valid for
     * placing: within build height, replaceable, world-interactable.
     *
     * @param level   the server world
     * @param targets raw position list
     * @param state   the block state to place
     * @param player  the player performing the action
     * @return filtered list of valid placement positions
     */
    public static List<BlockPos> filterPlaceableTargets(ServerLevel level, List<BlockPos> targets,
                                                         BlockState state, ServerPlayer player) {
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : targets) {
            if (pos == null) continue;
            if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) continue;
            if (!level.mayInteract(player, pos)) continue;
            if (!state.canSurvive(level, pos)) continue;
            if (!level.getBlockState(pos).canBeReplaced()) continue;
            valid.add(pos.immutable());
        }
        return valid;
    }

    /**
     * Validates that a single position is a valid destruction target.
     * Checks destroy speed and world access.
     *
     * @param level  the server world
     * @param pos    target block position
     * @param player the player
     * @return true if the block is destroyable
     */
    public static boolean isValidDestroyTarget(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return AreaShapeGenerator.validateDestroyPosition(level, pos, player);
    }

    /**
     * Validates that a single position is a valid placement target.
     *
     * @param level  the server world
     * @param pos    target position
     * @param state  the block state to place
     * @param player the player
     * @return true if the block can be placed here
     */
    public static boolean isValidPlacementTarget(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        return AreaShapeGenerator.validatePlacementPosition(level, pos, state, player);
    }

    /**
     * Scans a 3D bounding box and returns all breakable block positions
     * within it, applying shape filtering.
     * <p>
     * Equivalent to GadgetUtils.getDestructionArea().
     *
     * @param level    the server world
     * @param minX, maxX  inclusive X bounds
     * @param minY, maxY  inclusive Y bounds
     * @param minZ, maxZ  inclusive Z bounds
     * @param player      the player
     * @param shapeOrdinal shape type ordinal
     * @param fillOrdinal  fill mode ordinal
     * @return list of breakable block positions within the bounds
     */
    public static List<BlockPos> scanAreaMineTargets(ServerLevel level,
                                                      int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                                      ServerPlayer player,
                                                      byte shapeOrdinal, byte fillOrdinal) {
        AreaShapeGenerator generator = ShapeGeneratorRegistry.getGenerator(shapeOrdinal);
        ShapeFillMode fillMode = fillOrdinal <= 0 ? ShapeFillMode.FILL : ShapeFillMode.values()[Math.min(fillOrdinal, ShapeFillMode.values().length - 1)];

        AreaShapeInput input = new AreaShapeInput(
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ),
                maxY - minY,
                Direction.DOWN,
                Direction.DOWN);

        List<BlockPos> candidates = generator.generatePositions(input, fillMode);
        return filterBreakableTargets(level, candidates, player);
    }
}