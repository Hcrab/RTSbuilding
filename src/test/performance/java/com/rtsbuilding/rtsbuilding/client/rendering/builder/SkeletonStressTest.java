package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SkeletonStressTest {

    @Test
    void solidCubeOverTenThousandBlocksRendersOnlyOuterEdges() {
        SkeletonScene scene = SkeletonSceneFixtures.solidCube(22);
        Set<SkeletonEdgeKey> actual = SkeletonEdgeInspector.actualVisibleEdges(scene.blocks());
        Set<SkeletonEdgeKey> expected = SkeletonEdgeOracle.expectedVisibleEdges(scene.blocks());

        assertEquals(10_648, scene.blocks().size());
        assertEquals(4 * (22 + 22 + 22), actual.size(), "solid cube should collapse to segmented outside edges");
        assertEquals(expected, actual);
    }

    @Test
    void diagonalTouchingBlocksAreNotMergedAcrossCorners() {
        List<BlockPos> blocks = List.of(
                new BlockPos(0, 0, 0),
                new BlockPos(1, 1, 1));

        Set<SkeletonEdgeKey> actual = SkeletonEdgeInspector.actualVisibleEdges(blocks);

        assertEquals(24, actual.size(), "corner-only contact must remain two independent block skeletons");
        assertEquals(SkeletonEdgeOracle.expectedVisibleEdges(blocks), actual);
    }

    @Test
    void chunkSeamStressSceneMatchesOracle() {
        assertSceneMatchesOracle(SkeletonSceneFixtures.chunkSeamTenK(), 10_000);
    }

    @Test
    void treeCanopyStressSceneMatchesOracle() {
        assertSceneMatchesOracle(SkeletonSceneFixtures.treeCanopyTenK(), 10_000);
    }

    @Test
    void preGeneratedWorldSpawnAreaSceneMatchesOracle() {
        assertSceneMatchesOracle(SkeletonSceneFixtures.worldSpawnAreaTenK(), 10_000);
    }

    @Test
    void erodedWorldSpawnAreaCheckpointsMatchOracle() {
        SkeletonScene scene = SkeletonSceneFixtures.worldSpawnAreaTenK();
        assertTrue(scene.blocks().size() >= 10_000, "world spawn scene should stay at 10k-block stress scale");

        int[] remainingCounts = {
                scene.blocks().size(),
                scene.blocks().size() * 3 / 4,
                scene.blocks().size() / 2,
                scene.blocks().size() / 4
        };
        for (int count : remainingCounts) {
            List<BlockPos> remaining = new ArrayList<>(scene.blocks().subList(0, count));
            Set<SkeletonEdgeKey> actual = SkeletonEdgeInspector.actualVisibleEdges(remaining);
            Set<SkeletonEdgeKey> expected = SkeletonEdgeOracle.expectedVisibleEdges(remaining);
            assertEquals(expected, actual, "eroded checkpoint with " + count + " remaining blocks");
        }
    }

    @Test
    void worldSpawnAreaRangeDestroyTickSequenceUsesActualTargetQueue() {
        SkeletonScene scene = SkeletonSceneFixtures.worldSpawnAreaTenK();
        List<BlockPos> lockedTargets = SkeletonMiningSequenceFixtures.actualRangeDestroyTargets(scene);
        assertTrue(lockedTargets.size() >= 10_000,
                "range destroy target lock should stay at 10k-block stress scale");
        assertActualTickSequenceMatchesSkeletonCache(lockedTargets, "range destroy");
    }

    @Test
    void worldSpawnAreaChainDestroyTickSequenceUsesActualCollectorAndTargetQueue() {
        SkeletonScene scene = SkeletonSceneFixtures.worldSpawnAreaTenK();
        List<BlockPos> lockedTargets = SkeletonMiningSequenceFixtures.actualUltimineTargets(scene);
        assertEquals(RtsMiningValidator.ULTIMINE_MAX_BLOCKS, lockedTargets.size(),
                "chain destroy should use the real ultimine hard cap");
        assertActualTickSequenceMatchesSkeletonCache(lockedTargets, "chain destroy");
    }

    private static void assertActualTickSequenceMatchesSkeletonCache(List<BlockPos> lockedTargets, String label) {
        List<List<BlockPos>> batches = SkeletonMiningSequenceFixtures.actualTickBatches(lockedTargets);
        SkeletonTickSequenceOracle.Model expected = SkeletonTickSequenceOracle.start(lockedTargets);

        SkeletonEdgeInspector.resetCachedSkeleton();
        ShapeDataRecords.GhostPreview preview = SkeletonEdgeInspector.preview(lockedTargets);
        Set<SkeletonEdgeKey> initialActual = SkeletonEdgeInspector.cachedVisibleEdges(preview);
        assertEdgesEqual(expected.visibleEdges(), initialActual, "tick 0");

        int tick = 0;
        for (List<BlockPos> batch : batches) {
            tick++;
            for (BlockPos pos : batch) {
                MergedSkeletonRenderer.markDestroyed(pos);
            }
            expected.applyTick(batch);
            Set<SkeletonEdgeKey> actual = SkeletonEdgeInspector.cachedVisibleEdges(preview);
            assertEdgesEqual(expected.visibleEdges(), actual,
                    label + " tick " + tick + ", destroyed=" + expected.destroyed()
                            + ", remaining=" + expected.remainingCount());
        }
    }

    private static void assertSceneMatchesOracle(SkeletonScene scene, int minimumBlocks) {
        assertTrue(scene.blocks().size() >= minimumBlocks,
                scene.name() + " should stay at " + minimumBlocks + "+ blocks, got " + scene.blocks().size());
        Set<SkeletonEdgeKey> actual = SkeletonEdgeInspector.actualVisibleEdges(scene.blocks());
        Set<SkeletonEdgeKey> expected = SkeletonEdgeOracle.expectedVisibleEdges(scene.blocks());
        assertFalse(actual.isEmpty(), scene.name() + " should produce visible skeleton edges");
        assertEquals(expected, actual, scene.name() + " merged skeleton mismatch");
    }

    private static void assertEdgesEqual(Set<SkeletonEdgeKey> expected, Set<SkeletonEdgeKey> actual, String label) {
        if (expected.equals(actual)) {
            return;
        }
        Set<SkeletonEdgeKey> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        Set<SkeletonEdgeKey> extra = new HashSet<>(actual);
        extra.removeAll(expected);
        fail(label + " skeleton mismatch: expected=" + expected.size()
                + ", actual=" + actual.size()
                + ", missing=" + missing.size() + " " + sample(missing)
                + ", extra=" + extra.size() + " " + sample(extra));
    }

    private static String sample(Set<SkeletonEdgeKey> edges) {
        return edges.stream().limit(5).toList().toString();
    }
}
