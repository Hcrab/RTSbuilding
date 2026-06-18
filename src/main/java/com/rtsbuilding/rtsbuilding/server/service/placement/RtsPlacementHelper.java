package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pure helper utilities for RTS placement operations.
 *
 * <p>This helper owns only reusable stateless utilities that are shared by
 * the placement executor, quick-build handler, and batch job runner. It
 * deliberately does not execute placement, extract items, play sounds, or
 * manage batch jobs.
 */
public final class RtsPlacementHelper {

    private RtsPlacementHelper() {
    }

    /**
     * Sanitises a hit-offset coordinate, falling back to a face-based default
     * when the supplied value is {@link Double#isFinite(double) non-finite}.
     */
    public static double sanitizeHitOffset(double offset, Direction face, Direction.Axis axis) {
        if (Double.isFinite(offset)) {
            return offset;
        }
        double fallback = 0.5D;
        if (face != null && face.getAxis() == axis) {
            fallback += face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.5D : -0.5D;
        }
        return fallback;
    }

    /**
     * Rotates a {@link BlockState} by the given number of 90-degree steps
     * (only the lowest two bits of {@code rotateSteps} are used).
     */
    public static BlockState rotateState(BlockState state, byte rotateSteps) {
        int turns = rotateSteps & 3;
        BlockState rotated = state;
        for (int i = 0; i < turns; i++) {
            rotated = rotated.rotate(Rotation.CLOCKWISE_90);
        }
        return rotated;
    }

    /**
     * Applies incremental rotation to a block that has already been placed.
     */
    public static void rotatePlacedBlock(ServerLevel level, BlockPos pos, byte rotateSteps) {
        int turns = rotateSteps & 3;
        if (turns == 0 || !level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockState rotated = rotateState(state, rotateSteps);
        if (rotated != state) {
            level.setBlock(pos, rotated, 3);
        }
    }

    /**
     * Detects where a block was actually placed by comparing before/after
     * states of the clicked position and its adjacent neighbour.
     */
    public static BlockPos detectPlacedPos(ServerLevel level, BlockPos clickedPos, BlockState beforeClicked,
                                            BlockPos adjacentPos, BlockState beforeAdjacent) {
        if (!level.hasChunkAt(clickedPos)) {
            return null;
        }
        BlockState afterClicked = level.getBlockState(clickedPos);
        if (!afterClicked.equals(beforeClicked) && !afterClicked.isAir()) {
            return clickedPos;
        }

        if (beforeAdjacent == null || !level.hasChunkAt(adjacentPos)) {
            return null;
        }
        BlockState afterAdjacent = level.getBlockState(adjacentPos);
        if (!afterAdjacent.equals(beforeAdjacent) && !afterAdjacent.isAir()) {
            return adjacentPos;
        }
        return null;
    }

    /**
     * Requests a storage page refresh for the player, but only when {@code
     * refreshStoragePage} is {@code true}.
     */
    public static void requestSessionPage(ServerPlayer player, RtsStorageSession session, boolean refreshStoragePage) {
        if (refreshStoragePage) {
            RtsStorageTickService.INSTANCE.forceRefresh(player);
            session.transfer.pageDataVersion.incrementAndGet();
            ServiceRegistry.getInstance().page().requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }
}
