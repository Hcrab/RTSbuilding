package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.common.shape.generator.AreaShapeGenerator;
import com.rtsbuilding.rtsbuilding.common.shape.generator.ShapeGeneratorRegistry;
import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Area operation executor — shape-based area build and destroy operation hub.
 * <p>
 * This stateless utility class orchestrates the full pipeline:
 * <ol>
 *   <li>Shape-based position generation</li>
 *   <li>Per-position validation (world permissions, breakability, replaceability)</li>
 *   <li>Item extraction</li>
 *   <li>Server-side execution via tick handlers or direct block operations</li>
 *   <li>Operation recording for undo</li>
 * </ol>
 * All state is managed by the caller's Session.
 */
public final class AreaOperationExecutor {

    private AreaOperationExecutor() {
    }

    // ======================================================================
    //  Area Destroy — batch destroy blocks at many positions
    // ======================================================================

    /**
     * Filter the destroy target list, keeping only positions that can be effectively destroyed.
     * <p>
     * Conditions: non-air, within world interaction range, and has a valid destroy speed.
     *
     * @param level   server-side world
     * @param targets original position list
     * @param player  the player performing the operation
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
     * Scan a 3D bounding box and return all destroyable block positions within it.
     * <p>
     * Applies shape filtering, equivalent to GadgetUtils.getDestructionArea().
     *
     * @param level         server-side world
     * @param minX, maxX    inclusive X boundaries
     * @param minY, maxY    inclusive Y boundaries
     * @param minZ, maxZ    inclusive Z boundaries
     * @param player        the player
     * @param shapeOrdinal  shape type ordinal
     * @param fillOrdinal   fill mode ordinal
     * @return list of destroyable block positions within the boundaries
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