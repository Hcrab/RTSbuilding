package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.common.placement.PlacementStatePreset;
import com.rtsbuilding.rtsbuilding.common.placement.PlacedBlockRotationStep;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * RTS 放置系统的纯辅助工具方法集合。
 *
 * <p>此类提供一组被 {@link RtsPlacementExecutor}、{@link RtsPlacementQuickBuild}
 * 和批处理作业运行器共享的可重用无状态工具方法。所有方法均为 {@code static}，
 * 类本身设计为不可实例化的工具类。
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>{@link #sanitizeHitOffset(double, Direction, Direction.Axis)} — 清理点击偏移量，
 *       非有限值时回退到基于面的默认值（0.5 ± 0.5）</li>
 *   <li>{@link #rotateState(BlockState, byte)} — 将方块状态旋转指定次数的 90 度（仅用最低 2 位）</li>
 *   <li>{@link #rotatePlacedBlock(ServerLevel, BlockPos, byte)} — 对世界中已放置的方块施加增量旋转</li>
 *   <li>{@link #detectPlacedPos(ServerLevel, BlockPos, BlockState, BlockPos, BlockState)} —
 *       通过比较点击位置和相邻位置的前后状态，检测方块实际放置的位置</li>
 *   <li>{@link #requestSessionPage(ServerPlayer, RtsStorageSession, boolean)} —
 *       条件性请求刷新玩家的储存页面（仅在 {@code refreshStoragePage} 为 true 时）</li>
 * </ul>
 *
 * <p><b>设计原则：</b>此类故意不执行实际放置、物品提取、声音播放或批处理作业管理，
 * 这些职责分别位于 {@code RtsPlacementExecutor}、{@code RtsPlacementExtractor}、
 * {@code RtsPlacementSound} 和 {@code RtsPlacementBatch} 中。
 */
public final class RtsPlacementHelper {

    private RtsPlacementHelper() {
    }

    /**
     * 清理点击偏移坐标，当提供的值为 {@link Double#isFinite(double) 非有限} 时
     * 回退到基于面的默认值。
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
     * 将 {@link BlockState} 旋转指定数量的 90 度步数
     * （仅使用 {@code rotateSteps} 的最低两位）。
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
     * 对已放置的方块应用增量旋转。
     */
    public static void rotatePlacedBlock(ServerLevel level, BlockPos pos, byte rotateSteps) {
        int turns = rotateSteps & 3;
        if (turns == 0 || !RtsPlacedBlockRotation.canReadNeighborhood(level, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockState rotated = rotateState(state, rotateSteps);
        RtsPlacedBlockRotation.applyResolvedState(level, pos, state, rotated);
    }

    public static void rotatePlacedBlockStep(
            ServerLevel level,
            BlockPos pos,
            Direction axisDirection,
            int quarterTurns) {
        if (!RtsPlacedBlockRotation.canReadNeighborhood(level, pos)
                || axisDirection == null
                || Math.abs(quarterTurns) != 1) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        BlockState rotated = PlacedBlockRotationStep.rotate(
                current, axisDirection, quarterTurns);
        RtsPlacedBlockRotation.applyResolvedState(
                level, pos, current, rotated);
    }

    /**
     * 对刚刚成功放下的方块应用服务端白名单状态预设。
     */
    public static void applyPlacementStatePreset(ServerLevel level, BlockPos pos, String encodedPreset) {
        if (encodedPreset == null || encodedPreset.isBlank()
                || !RtsPlacedBlockRotation.canReadNeighborhood(level, pos)) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        BlockState resolved = PlacementStatePreset.apply(current, encodedPreset);
        RtsPlacedBlockRotation.applyFreshPlacementState(level, pos, current, resolved);
    }

    /**
     * 通过比较点击位置及其相邻邻居的前后状态来检测方块实际放置的位置。
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
     * 请求玩家的储存页面刷新，但仅在 {@code refreshStoragePage} 为 {@code true} 时。
     */
    public static void requestSessionPage(ServerPlayer player, RtsStorageSession session, boolean refreshStoragePage) {
        if (refreshStoragePage) {
            var reg = ServiceRegistry.getInstance();
            reg.serviceOp().markDirty(player, session);
            reg.serviceOp().refreshPage(player, session);
        }
    }
}
