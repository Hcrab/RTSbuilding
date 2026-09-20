package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostBlock;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlueprintGhostMeshCacheTest {
    private final List<BlueprintGhostBlock> geometry =
            List.of(new BlueprintGhostBlock(BlockPos.ZERO, null, false));
    private final BlueprintPreviewClip clip = new BlueprintPreviewClip(0, 9, 0, 9);
    private final Object world = new Object();
    private final List<BlueprintGhostMesh> meshes = new ArrayList<>();
    private final BlueprintGhostMeshCache cache = new BlueprintGhostMeshCache(input -> {
        BlueprintGhostMesh mesh = mock(BlueprintGhostMesh.class);
        when(mesh.sampleAnchor()).thenReturn(input.anchor());
        meshes.add(mesh);
        return mesh;
    });

    @Test
    void stationaryFramesMaterialChangesAndDraggingReuseFirstBakeWithoutStarvation() {
        cache.prepare(world, preview(BlockPos.ZERO, true), clip, 0);
        BlueprintGhostMesh initial = cache.visible();
        for (int i = 0; i < 120; i++) {
            cache.prepare(world, preview(new BlockPos(i, 2, i), i % 2 == 0), clip,
                    i * 20_000_000L);
            assertSame(initial, cache.visible());
            assertSame(initial, cache.building());
        }
        assertEquals(1, meshes.size());
        verify(initial, never()).close();
    }

    @Test
    void settledLocationRefreshKeepsOldPreviewAndDropsStaleReplacement() {
        cache.prepare(world, preview(BlockPos.ZERO, true), clip, 0);
        BlueprintGhostMesh initial = cache.visible();
        when(initial.complete()).thenReturn(true);
        BlockPos moved = new BlockPos(8, 1, 3);
        cache.prepare(world, preview(moved, true), clip, 1);
        cache.prepare(world, preview(moved, true), clip, 200_000_000);
        BlueprintGhostMesh refresh = cache.building();
        assertNotSame(initial, refresh);
        assertSame(initial, cache.visible());
        cache.prepare(world, preview(new BlockPos(9, 1, 3), true), clip, 210_000_000);
        verify(refresh).close();
        assertSame(initial, cache.visible());
        cache.prepare(world, preview(moved, true), clip, 220_000_000);
        cache.prepare(world, preview(moved, true), clip, 420_000_000);
        BlueprintGhostMesh accepted = cache.building();
        when(accepted.complete()).thenReturn(true);
        cache.prepare(world, preview(moved, true), clip, 430_000_000);
        assertSame(accepted, cache.visible());
        verify(initial).close();
    }

    @Test
    void changedWorldClipAndExplicitReleaseDisposeOwnedMeshes() {
        cache.prepare(world, preview(BlockPos.ZERO, true), clip, 0);
        BlueprintGhostMesh first = cache.visible();
        cache.prepare(new Object(), preview(BlockPos.ZERO, true), clip, 1);
        verify(first).close();
        BlueprintGhostMesh second = cache.visible();
        cache.prepare(world, preview(BlockPos.ZERO, true),
                new BlueprintPreviewClip(0, 8, 0, 9), 2);
        verify(second).close();
        BlueprintGhostMesh third = cache.visible();
        cache.close();
        cache.close();
        verify(third).close();
        assertNull(cache.visible());
    }

    @Test
    void fullInteriorMovementHasSameClipButCrossingBoundaryDoesNot() {
        AABB bounds = new AABB(-2, 0, -3, 5, 9, 7);
        BlueprintPreviewClip a = BlueprintPreviewClip.of(bounds, BlockPos.ZERO,
                -100, 99, -100, 99);
        BlueprintPreviewClip b = BlueprintPreviewClip.of(bounds, new BlockPos(10, 70, 20),
                -100, 99, -100, 99);
        assertFalse(a.isEmpty());
        assertEquals(a, b);
        BlueprintPreviewClip edge = BlueprintPreviewClip.of(bounds, new BlockPos(98, 0, 0),
                -100, 99, -100, 99);
        assertNotEquals(a, edge);
        assertTrue(edge.contains(new BlockPos(1, 80, 0)));
        assertFalse(edge.contains(new BlockPos(2, 80, 0)));
    }

    private BlueprintGhostPreview preview(BlockPos anchor, boolean ready) {
        return new BlueprintGhostPreview(geometry, ready, false, geometry, anchor,
                new AABB(0, 0, 0, 10, 10, 10));
    }
}
