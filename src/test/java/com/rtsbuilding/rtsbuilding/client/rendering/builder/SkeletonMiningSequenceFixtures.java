package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningTargetQueue;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bridges large skeleton fixtures into the real mining target selectors.
 *
 * <p>The test world here owns only block lookup. It intentionally skips player,
 * storage, and tool concerns, because these skeleton tests focus on the locked
 * target queue and per-tick destruction sequence after validation has accepted
 * the blocks.</p>
 */
final class SkeletonMiningSequenceFixtures {

    private SkeletonMiningSequenceFixtures() {
    }

    static List<BlockPos> actualUltimineTargets(SkeletonScene scene) {
        Set<Long> blockKeys = blockKeys(scene);
        BlockPos seed = nearestCenterSeed(scene);
        return RtsUltimineCollector.collect(
                seed,
                RtsMiningValidator.ULTIMINE_MAX_BLOCKS,
                RtsUltimineCollector.DEFAULT_MAX_RADIUS,
                pos -> blockKeys.contains(pos.asLong()),
                (pos, present, seedPresent) -> Boolean.TRUE.equals(present));
    }

    static List<BlockPos> actualRangeDestroyTargets(SkeletonScene scene) {
        Set<Long> blockKeys = blockKeys(scene);
        List<BlockPos> rawPositions = new ArrayList<>(scene.blocks().size() + 2);
        rawPositions.addAll(scene.blocks());
        if (!scene.blocks().isEmpty()) {
            rawPositions.add(scene.blocks().get(0));
        }
        rawPositions.add(new BlockPos(1_000_000, 80, 1_000_000));
        rawPositions.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
        return List.copyOf(RtsMiningTargetQueue.collectExplicitDestroyTargets(
                rawPositions,
                pos -> true,
                pos -> blockKeys.contains(pos.asLong())));
    }

    static List<List<BlockPos>> actualTickBatches(List<BlockPos> lockedTargets) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(lockedTargets);
        List<List<BlockPos>> batches = new ArrayList<>();
        while (!queue.isEmpty()) {
            List<BlockPos> batch = new ArrayList<>(RtsMiningValidator.ULTIMINE_BLOCKS_PER_TICK);
            int processedThisTick = 0;
            while (RtsMiningTargetQueue.canProcessAnotherTargetThisTick(processedThisTick, queue)) {
                BlockPos target = RtsMiningTargetQueue.pollNextTarget(queue);
                if (target == null) {
                    break;
                }
                batch.add(target);
                processedThisTick++;
            }
            if (batch.isEmpty()) {
                break;
            }
            batches.add(batch);
        }
        return batches;
    }

    private static Set<Long> blockKeys(SkeletonScene scene) {
        Set<Long> keys = new HashSet<>(scene.blocks().size() * 2);
        for (BlockPos pos : scene.blocks()) {
            keys.add(pos.asLong());
        }
        return keys;
    }

    private static BlockPos nearestCenterSeed(SkeletonScene scene) {
        Bounds bounds = Bounds.of(scene.blocks());
        BlockPos center = new BlockPos(
                (bounds.minX() + bounds.maxX()) / 2,
                (bounds.minY() + bounds.maxY()) / 2,
                (bounds.minZ() + bounds.maxZ()) / 2);
        return scene.blocks().stream()
                .min(Comparator
                        .comparingLong((BlockPos pos) -> distanceSquared(center, pos))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .orElseThrow(() -> new IllegalArgumentException("scene has no blocks: " + scene.name()));
    }

    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        static Bounds of(List<BlockPos> blocks) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : blocks) {
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minY = Math.min(minY, pos.getY());
                maxY = Math.max(maxY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
        }
    }
}
