package com.rtsbuilding.rtsbuilding.common.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 便捷破坏的共享、无副作用目标规划器。
 *
 * <p>客户端只把结果用于预览；服务端会用真实世界重新调用本类，再把通过验证的目标交给
 * 正式拆除任务。本类不加载区块、不借用工具、不破坏方块，也不创建 Ctrl+Z 历史。</p>
 *
 * <p>树木模式允许树叶或原木把多棵相接的树连成一个操作组。扫描一旦发现完整连通组
 * 超过玩家选择的总方块上限，就返回空目标并拒绝整组，绝不截断后只砍一部分。</p>
 */
public final class RtsConvenienceDestroyPlanner {
    public static final int MIN_BOX_SIZE = 1;
    public static final int MAX_BOX_SIZE = 64;
    public static final int MAX_BOX_HEIGHT = 128;
    public static final int MAX_VOLUME = 32_768;
    public static final int MIN_TREE_BLOCKS = 1;
    public static final int MAX_TREE_BLOCKS = 8_192;

    public enum ResultCode {
        READY,
        EMPTY,
        INVALID_TARGET,
        OVER_LIMIT,
        UNLOADED_CHUNK
    }

    public record Plan(ResultCode code, List<BlockPos> targets, int discoveredTargets) {
        public Plan {
            code = code == null ? ResultCode.INVALID_TARGET : code;
            targets = List.copyOf(targets == null ? List.of() : targets);
            discoveredTargets = Math.max(discoveredTargets, targets.size());
        }

        public boolean ready() {
            return code == ResultCode.READY && !targets.isEmpty();
        }
    }

    private static final int[][] NEIGHBORS_26 = createNeighbors26();

    private RtsConvenienceDestroyPlanner() {
    }

    public static Plan plan(LevelReader level, RtsConvenienceDestroyMode mode,
            BlockPos anchor, Direction hitFace, RtsConvenienceDestroySettings rawSettings) {
        if (level == null || mode == null || anchor == null) {
            return rejected(ResultCode.INVALID_TARGET, 0);
        }
        Direction safeFace = hitFace == null ? Direction.UP : hitFace;
        RtsConvenienceDestroySettings settings = sanitize(rawSettings);
        return switch (mode) {
            case REPEAT_BOX -> planRepeatBox(level, anchor, safeFace, settings);
            case CHUNK_QUARRY -> planChunk(level, anchor, settings);
            case TREE_FELL -> planTree(level, anchor, settings.treeMaxBlocks());
        };
    }

    public static RtsConvenienceDestroySettings sanitize(RtsConvenienceDestroySettings value) {
        RtsConvenienceDestroySettings source = value == null
                ? RtsConvenienceDestroySettings.DEFAULT : value;
        return new RtsConvenienceDestroySettings(
                clamp(source.sizeX(), MIN_BOX_SIZE, MAX_BOX_SIZE),
                clamp(source.sizeY(), MIN_BOX_SIZE, MAX_BOX_HEIGHT),
                clamp(source.sizeZ(), MIN_BOX_SIZE, MAX_BOX_SIZE),
                clamp(source.chunkUp(), 0, MAX_BOX_HEIGHT),
                clamp(source.chunkDown(), 0, MAX_BOX_HEIGHT),
                clamp(source.treeMaxBlocks(), MIN_TREE_BLOCKS, MAX_TREE_BLOCKS));
    }

    private static Plan planRepeatBox(LevelReader level, BlockPos anchor, Direction face,
            RtsConvenienceDestroySettings settings) {
        long volume = (long) settings.sizeX() * settings.sizeY() * settings.sizeZ();
        if (volume > MAX_VOLUME) {
            return rejected(ResultCode.OVER_LIMIT, safeInt(volume));
        }

        int[] xBounds = axisBounds(anchor.getX(), settings.sizeX(), face, Direction.Axis.X);
        int[] yBounds = axisBounds(anchor.getY(), settings.sizeY(), face, Direction.Axis.Y);
        int[] zBounds = axisBounds(anchor.getZ(), settings.sizeZ(), face, Direction.Axis.Z);
        int minY = Math.max(level.getMinBuildHeight(), yBounds[0]);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, yBounds[1]);
        if (minY > maxY) {
            return rejected(ResultCode.INVALID_TARGET, 0);
        }
        return collectBox(level, xBounds[0], xBounds[1], minY, maxY, zBounds[0], zBounds[1]);
    }

    private static Plan planChunk(LevelReader level, BlockPos anchor,
            RtsConvenienceDestroySettings settings) {
        ChunkPos chunk = new ChunkPos(anchor);
        int minY = Math.max(level.getMinBuildHeight(), anchor.getY() - settings.chunkDown());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, anchor.getY() + settings.chunkUp());
        if (minY > maxY) {
            return rejected(ResultCode.INVALID_TARGET, 0);
        }
        long volume = 16L * 16L * (maxY - minY + 1L);
        if (volume > MAX_VOLUME) {
            return rejected(ResultCode.OVER_LIMIT, safeInt(volume));
        }
        return collectBox(level,
                chunk.getMinBlockX(), chunk.getMaxBlockX(),
                minY, maxY,
                chunk.getMinBlockZ(), chunk.getMaxBlockZ());
    }

    private static Plan collectBox(LevelReader level, int minX, int maxX, int minY, int maxY,
            int minZ, int maxZ) {
        int minChunkX = SectionPosCompat.blockToSectionCoord(minX);
        int maxChunkX = SectionPosCompat.blockToSectionCoord(maxX);
        int minChunkZ = SectionPosCompat.blockToSectionCoord(minZ);
        int maxChunkZ = SectionPosCompat.blockToSectionCoord(maxZ);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return rejected(ResultCode.UNLOADED_CHUNK, 0);
                }
            }
        }

        List<BlockPos> targets = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isCandidate(level, pos)) {
                        targets.add(pos);
                    }
                }
            }
        }
        return targets.isEmpty()
                ? rejected(ResultCode.EMPTY, 0)
                : new Plan(ResultCode.READY, targets, targets.size());
    }

    private static Plan planTree(LevelReader level, BlockPos anchor, int maxBlocks) {
        if (!level.hasChunk(SectionPosCompat.blockToSectionCoord(anchor.getX()),
                SectionPosCompat.blockToSectionCoord(anchor.getZ()))) {
            return rejected(ResultCode.UNLOADED_CHUNK, 0);
        }
        BlockState seedState = level.getBlockState(anchor);
        if (!isTreePart(seedState)) {
            return rejected(ResultCode.INVALID_TARGET, 0);
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
        BlockPos seed = anchor.immutable();
        queue.add(seed);
        visited.add(seed);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            BlockState currentState = level.getBlockState(current);
            if (!isTreePart(currentState)) {
                continue;
            }
            targets.add(current);
            if (targets.size() > maxBlocks) {
                return rejected(ResultCode.OVER_LIMIT, targets.size());
            }

            for (int[] delta : NEIGHBORS_26) {
                BlockPos next = current.offset(delta[0], delta[1], delta[2]);
                if (next.getY() < level.getMinBuildHeight()
                        || next.getY() >= level.getMaxBuildHeight()
                        || !visited.add(next)) {
                    continue;
                }
                int chunkX = SectionPosCompat.blockToSectionCoord(next.getX());
                int chunkZ = SectionPosCompat.blockToSectionCoord(next.getZ());
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return rejected(ResultCode.UNLOADED_CHUNK, targets.size());
                }
                if (isTreePart(level.getBlockState(next))) {
                    queue.addLast(next.immutable());
                }
            }
        }

        List<BlockPos> sorted = new ArrayList<>(targets);
        sorted.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed()
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return sorted.isEmpty()
                ? rejected(ResultCode.EMPTY, 0)
                : new Plan(ResultCode.READY, sorted, sorted.size());
    }

    private static boolean isCandidate(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private static boolean isTreePart(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (state.is(BlockTags.LOGS)) {
            return true;
        }
        if (state.is(BlockTags.LEAVES)) {
            return !state.hasProperty(LeavesBlock.PERSISTENT)
                    || !state.getValue(LeavesBlock.PERSISTENT);
        }
        return state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.RED_MUSHROOM_BLOCK);
    }

    private static int[] axisBounds(int anchor, int size, Direction face, Direction.Axis axis) {
        if (face.getAxis() == axis) {
            int step = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? -1 : 1;
            int end = anchor + step * (size - 1);
            return new int[] { Math.min(anchor, end), Math.max(anchor, end) };
        }
        int min = anchor - (size - 1) / 2;
        return new int[] { min, min + size - 1 };
    }

    private static Plan rejected(ResultCode code, int discovered) {
        return new Plan(code, List.of(), Math.max(0, discovered));
    }

    private static int safeInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int[][] createNeighbors26() {
        List<int[]> result = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        result.add(new int[] { dx, dy, dz });
                    }
                }
            }
        }
        return result.toArray(int[][]::new);
    }

    /** 避免依赖版本间名称易变的 SectionPos 静态方法。 */
    private static final class SectionPosCompat {
        private SectionPosCompat() {
        }

        private static int blockToSectionCoord(int blockCoordinate) {
            return blockCoordinate >> 4;
        }
    }
}

