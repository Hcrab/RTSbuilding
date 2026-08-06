package com.rtsbuilding.rtsbuilding.common.destruction;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 便利破坏的共享无副作用目标规划器。
 *
 * <p>预览只能显示本类的结果，服务端必须用自己的世界再算一次，并把确认结果继续交给既有
 * AREA_DESTROY 管道。这既保持第一方块线框到骨架侵蚀的已有表现，也不会复制另一套破坏执行器。</p>
 */
public final class RtsConvenienceDestroyPlanner {
    public static final int MIN_BOX_SIZE = 1;
    public static final int MAX_BOX_SIZE = 64;
    public static final int MAX_BOX_HEIGHT = 128;
    public static final int MAX_VOLUME = 32768;
    public static final int MIN_TREE_BLOCKS = 1;
    public static final int MAX_TREE_BLOCKS = 8192;

    public enum ResultCode { READY, EMPTY, INVALID_TARGET, OVER_LIMIT, UNLOADED_CHUNK }

    private static final int[][] NEIGHBORS_26 = createNeighbors26();

    private RtsConvenienceDestroyPlanner() {
    }

    public static Plan plan(World world, RtsConvenienceDestroyMode mode, BlockPos anchor,
                            EnumFacing hitFace, RtsConvenienceDestroySettings rawSettings) {
        if (world == null || mode == null || anchor == null) return rejected(ResultCode.INVALID_TARGET, 0);
        EnumFacing face = hitFace == null ? EnumFacing.UP : hitFace;
        RtsConvenienceDestroySettings settings = sanitize(rawSettings);
        switch (mode) {
            case REPEAT_BOX:
                return planRepeatBox(world, anchor, face, settings);
            case CHUNK_QUARRY:
                return planChunk(world, anchor, settings);
            case TREE_FELL:
                return planTree(world, anchor, settings.treeMaxBlocks());
            default:
                return rejected(ResultCode.INVALID_TARGET, 0);
        }
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

    private static Plan planRepeatBox(World world, BlockPos anchor, EnumFacing face,
                                      RtsConvenienceDestroySettings settings) {
        long volume = (long) settings.sizeX() * settings.sizeY() * settings.sizeZ();
        if (volume > MAX_VOLUME) return rejected(ResultCode.OVER_LIMIT, safeInt(volume));
        int[] x = axisBounds(anchor.getX(), settings.sizeX(), face, EnumFacing.Axis.X);
        int[] y = axisBounds(anchor.getY(), settings.sizeY(), face, EnumFacing.Axis.Y);
        int[] z = axisBounds(anchor.getZ(), settings.sizeZ(), face, EnumFacing.Axis.Z);
        int minY = Math.max(0, y[0]);
        int maxY = Math.min(world.getHeight() - 1, y[1]);
        return minY > maxY ? rejected(ResultCode.INVALID_TARGET, 0)
                : collectBox(world, x[0], x[1], minY, maxY, z[0], z[1]);
    }

    private static Plan planChunk(World world, BlockPos anchor, RtsConvenienceDestroySettings settings) {
        int minY = Math.max(0, anchor.getY() - settings.chunkDown());
        int maxY = Math.min(world.getHeight() - 1, anchor.getY() + settings.chunkUp());
        if (minY > maxY) return rejected(ResultCode.INVALID_TARGET, 0);
        long volume = 16L * 16L * (maxY - minY + 1L);
        if (volume > MAX_VOLUME) return rejected(ResultCode.OVER_LIMIT, safeInt(volume));
        int minX = (anchor.getX() >> 4) << 4;
        int minZ = (anchor.getZ() >> 4) << 4;
        return collectBox(world, minX, minX + 15, minY, maxY, minZ, minZ + 15);
    }

    private static Plan collectBox(World world, int minX, int maxX, int minY, int maxY,
                                   int minZ, int maxZ) {
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (!world.isBlockLoaded(new BlockPos(chunkX << 4, 0, chunkZ << 4))) {
                    return rejected(ResultCode.UNLOADED_CHUNK, 0);
                }
            }
        }
        List<BlockPos> targets = new ArrayList<BlockPos>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (isCandidate(world, position)) targets.add(position);
                }
            }
        }
        return targets.isEmpty() ? rejected(ResultCode.EMPTY, 0)
                : new Plan(ResultCode.READY, targets, targets.size());
    }

    private static Plan planTree(World world, BlockPos anchor, int maxBlocks) {
        if (!world.isBlockLoaded(anchor)) return rejected(ResultCode.UNLOADED_CHUNK, 0);
        if (!isTreePart(world, anchor)) return rejected(ResultCode.INVALID_TARGET, 0);

        ArrayDeque<BlockPos> queue = new ArrayDeque<BlockPos>();
        Set<BlockPos> visited = new HashSet<BlockPos>();
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<BlockPos>();
        BlockPos seed = anchor.toImmutable();
        queue.add(seed);
        visited.add(seed);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (!isTreePart(world, current)) continue;
            targets.add(current);
            if (targets.size() > maxBlocks) return rejected(ResultCode.OVER_LIMIT, targets.size());
            for (int[] delta : NEIGHBORS_26) {
                BlockPos next = current.add(delta[0], delta[1], delta[2]);
                if (next.getY() < 0 || next.getY() >= world.getHeight() || !visited.add(next)) continue;
                if (!world.isBlockLoaded(next)) return rejected(ResultCode.UNLOADED_CHUNK, targets.size());
                if (isTreePart(world, next)) queue.addLast(next.toImmutable());
            }
        }
        List<BlockPos> sorted = new ArrayList<BlockPos>(targets);
        Collections.sort(sorted, new Comparator<BlockPos>() {
            @Override public int compare(BlockPos first, BlockPos second) {
                int y = Integer.compare(second.getY(), first.getY());
                if (y != 0) return y;
                int x = Integer.compare(first.getX(), second.getX());
                return x != 0 ? x : Integer.compare(first.getZ(), second.getZ());
            }
        });
        return sorted.isEmpty() ? rejected(ResultCode.EMPTY, 0)
                : new Plan(ResultCode.READY, sorted, sorted.size());
    }

    private static boolean isCandidate(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        return !block.isAir(state, world, pos) && block.getBlockHardness(state, world, pos) >= 0.0F;
    }

    private static boolean isTreePart(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block.isAir(state, world, pos)) return false;
        return block.isWood(world, pos) || block.isLeaves(state, world, pos)
                || block == Blocks.BROWN_MUSHROOM_BLOCK || block == Blocks.RED_MUSHROOM_BLOCK;
    }

    private static int[] axisBounds(int anchor, int size, EnumFacing face, EnumFacing.Axis axis) {
        if (face.getAxis() == axis) {
            int step = face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? -1 : 1;
            int end = anchor + step * (size - 1);
            return new int[] { Math.min(anchor, end), Math.max(anchor, end) };
        }
        int min = anchor - (size - 1) / 2;
        return new int[] { min, min + size - 1 };
    }

    private static Plan rejected(ResultCode code, int discovered) {
        return new Plan(code, Collections.<BlockPos>emptyList(), Math.max(0, discovered));
    }

    private static int safeInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int[][] createNeighbors26() {
        List<int[]> neighbors = new ArrayList<int[]>(26);
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if (x != 0 || y != 0 || z != 0) neighbors.add(new int[] { x, y, z });
        }
        return neighbors.toArray(new int[neighbors.size()][]);
    }

    public static final class Plan {
        private final ResultCode code;
        private final List<BlockPos> targets;
        private final int discoveredTargets;

        public Plan(ResultCode code, List<BlockPos> targets, int discoveredTargets) {
            this.code = code == null ? ResultCode.INVALID_TARGET : code;
            List<BlockPos> copied = new ArrayList<BlockPos>();
            if (targets != null) for (BlockPos target : targets) if (target != null) copied.add(target.toImmutable());
            this.targets = Collections.unmodifiableList(copied);
            this.discoveredTargets = Math.max(discoveredTargets, copied.size());
        }

        public ResultCode code() { return code; }
        public List<BlockPos> targets() { return targets; }
        public int discoveredTargets() { return discoveredTargets; }
        public boolean ready() { return code == ResultCode.READY && !targets.isEmpty(); }
    }
}
