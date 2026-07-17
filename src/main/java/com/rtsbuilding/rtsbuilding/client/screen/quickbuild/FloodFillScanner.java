package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 BFS 的 FloodFill 空洞扫描器。
 *
 * <p>核心逻辑：
 * <ol>
 *   <li>起始位置必须是空气或可替换方块</li>
 *   <li>起始位置必须通过空洞验证（至少 4 方向有边界）</li>
 *   <li>BFS 扩张时，<b>每个邻居都必须再次通过空洞验证</b>，
 *       已加入结果集的方块被视为有效边界，确保只在封闭空洞内扩张</li>
 * </ol>
 *
 * <p>这防止了 BFS 通过洞穴开口逃逸到外部世界。
 *
 * <p>使用示例：
 * <pre>{@code
 * FloodFillScanner.ScanResult result = FloodFillScanner.scan(
 *     level, startPos, 512, 16, false);
 * }</pre>
 */
public final class FloodFillScanner {

    private FloodFillScanner() {}

    /** 扫描结果 */
    public record ScanResult(
            List<BlockPos> positions,
            AABB boundingBox,
            int scannedCount,
            boolean reachedVolumeLimit
    ) {
        public boolean isValid() {
            return !positions.isEmpty();
        }
    }

    /** 6 方向偏移 */
    private static final int[][] DIRECTIONS = {
            { 1, 0, 0 }, { -1, 0, 0 },
            { 0, 1, 0 }, { 0, -1, 0 },
            { 0, 0, 1 }, { 0, 0, -1 }
    };

    /** 流体模式移除 Y+ 后的 5 方向 */
    private static final int[][] FLUID_DIRECTIONS = {
            { 1, 0, 0 }, { -1, 0, 0 },
            { 0, -1, 0 },
            { 0, 0, 1 }, { 0, 0, -1 }
    };

    /**
     * 执行 FloodFill 扫描。
     *
     * @param level        当前世界
     * @param start        扫描起始点
     * @param maxVolume    BFS 最大访问方块数
     * @param maxDiameter  空洞检测最大直径
     * @param fluidMode    是否为流体模式（移除向上的方向）
     * @return 扫描结果
     */
    public static ScanResult scan(Level level, BlockPos start, int maxVolume, int maxDiameter, boolean fluidMode) {
        if (level == null || start == null || maxVolume <= 0) {
            return new ScanResult(List.of(), null, 0, false);
        }

        BlockState startState = level.getBlockState(start);
        if (!isReplaceable(level, startState)) {
            return new ScanResult(List.of(), null, 0, false);
        }

        // 起始点必须自身位于空洞内
        if (!isWithinHole(level, start, maxDiameter, Set.of())) {
            return new ScanResult(List.of(), null, 0, false);
        }

        int[][] dirs = fluidMode ? FLUID_DIRECTIONS : DIRECTIONS;
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> holeSet = new HashSet<>(); // 已确认属于空洞的方块，作为伪边界
        List<BlockPos> result = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.addLast(start);
        visited.add(start);
        holeSet.add(start);

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean reachedLimit = false;

        while (!queue.isEmpty() && !reachedLimit) {
            BlockPos current = queue.removeFirst();

            BlockState currentState = level.getBlockState(current);
            if (!isReplaceable(level, currentState)) {
                continue;
            }

            result.add(current.immutable());
            minX = Math.min(minX, current.getX());
            minY = Math.min(minY, current.getY());
            minZ = Math.min(minZ, current.getZ());
            maxX = Math.max(maxX, current.getX());
            maxY = Math.max(maxY, current.getY());
            maxZ = Math.max(maxZ, current.getZ());

            for (int[] dir : dirs) {
                if (result.size() >= maxVolume) {
                    reachedLimit = true;
                    break;
                }
                BlockPos neighbor = current.offset(dir[0], dir[1], dir[2]);
                if (visited.contains(neighbor)) {
                    continue;
                }
                visited.add(neighbor);

                BlockState neighborState = level.getBlockState(neighbor);
                if (!isReplaceable(level, neighborState)) {
                    continue;
                }

                // 关键修复：每个邻居必须验证其自身是否位于空洞内，
                // 使用已确认的 holeSet 作为伪边界
                if (!isWithinHole(level, neighbor, maxDiameter, holeSet)) {
                    continue;
                }

                holeSet.add(neighbor);
                queue.addLast(neighbor);
            }
        }

        if (result.isEmpty()) {
            return new ScanResult(List.of(), null, result.size(), reachedLimit);
        }

        AABB box = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        return new ScanResult(List.copyOf(result), box, result.size(), reachedLimit);
    }

    /**
     * 检查指定位置是否位于封闭空洞内。
     * <p>从该位置向 6 方向探测，至少 4 个方向在 maxDiameter/2 距离内
     * 遇到有效边界（实体方块 或 已在 holeSet 中的方块）。</p>
     *
     * @param level        当前世界
     * @param pos          待检查位置
     * @param maxDiameter  检测直径
     * @param holeSet      已确认属于空洞的方块集（作为伪边界，允许 BFS 内向扩张）
     */
    private static boolean isWithinHole(Level level, BlockPos pos, int maxDiameter, Set<BlockPos> holeSet) {
        int halfDiam = maxDiameter / 2;
        int target = 4; // 至少 4 方向有边界
        int count = 0;

        for (int[] dir : DIRECTIONS) {
            for (int d = 1; d <= halfDiam; d++) {
                BlockPos probe = new BlockPos(
                        pos.getX() + dir[0] * d,
                        pos.getY() + dir[1] * d,
                        pos.getZ() + dir[2] * d);
                // 实体边界 或 已在空洞集内（伪边界）
                if (isValidBoundary(level, probe) || holeSet.contains(probe)) {
                    count++;
                    break;
                }
            }
            if (count >= target) {
                return true;
            }
        }
        return false;
    }

    /** 是否可替换方块（空气、水、熔岩等） */
    public static boolean isReplaceable(Level level, BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    /**
     * 是否为有效边界方块。
     * <p>非空、完整碰撞体积、可遮挡、非树叶、非伪原木。</p>
     */
    private static boolean isValidBoundary(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (level.isEmptyBlock(pos)) return false;

        Block block = state.getBlock();
        // 完整碰撞体积 + 可遮挡
        VoxelShape shape = state.getShape(level, pos);
        if (!Block.isShapeFullBlock(shape)) return false;
        if (!state.canOcclude()) return false;

        // 排除树叶
        if (block instanceof LeavesBlock) return false;
        // 排除伪原木
        if (block instanceof RotatedPillarBlock
                && state.instrument() == NoteBlockInstrument.BASS
                && state.ignitedByLava()
                && block.defaultDestroyTime() == 2.0F) {
            return false;
        }

        return true;
    }
}
