package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.common.blueprint.transform.BlueprintTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintPreviewGeometryCacheTest {
    @Test
    void repeatedFramesAndMovementDoNotRescanSourceOrReplaceLocalGeometry() {
        AtomicInteger reads = new AtomicInteger();
        List<RtsBlueprintBlock> blocks = new AbstractList<>() {
            public RtsBlueprintBlock get(int index) {
                reads.incrementAndGet();
                return new RtsBlueprintBlock(new BlockPos(index, 0, 0), null, null);
            }

            public int size() {
                return 100_000;
            }
        };
        RtsBlueprint blueprint = blueprint(blocks);
        BlueprintPreviewGeometryCache cache = new BlueprintPreviewGeometryCache();
        cache.prepare(blueprint, 0, 0, 0, 100_000);
        BlueprintGhostPreview first = cache.preview(BlockPos.ZERO, true);
        int initialReads = reads.get();
        for (int frame = 0; frame < 120; frame++) {
            cache.prepare(blueprint, 0, 0, 0, 100_000);
            assertSame(first, cache.preview(BlockPos.ZERO, true));
        }
        BlueprintGhostPreview moved = cache.preview(new BlockPos(30, 40, 50), false);
        assertSame(first.localBlocks(), moved.localBlocks());
        assertEquals(initialReads, reads.get());
        assertEquals(new BlockPos(35, 40, 50), moved.blocks().get(5).pos());
        assertFalse(moved.materialsReady());
    }

    @Test
    void everyAxisRotationMatchesPointTransformAndContentGrabIgnoresTruncation() {
        List<RtsBlueprintBlock> blocks = List.of(
                new RtsBlueprintBlock(new BlockPos(-3, 2, 5), null, null, "missing:a"),
                new RtsBlueprintBlock(new BlockPos(8, 7, -2), null, null, "missing:b"));
        RtsBlueprint blueprint = blueprint(blocks);
        BlueprintPreviewGeometryCache cache = new BlueprintPreviewGeometryCache();
        BlockPos target = new BlockPos(100, 64, 200);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
            cache.prepare(blueprint, y, x, z, 1);
            BlockPos offset = BlueprintTransform.centerRotationOffset(blueprint.size(), y, x, z);
            BlockPos a = BlueprintTransform.rotateAroundCenter(blocks.get(0).relativePos(),
                    y, x, z, offset);
            BlockPos b = BlueprintTransform.rotateAroundCenter(blocks.get(1).relativePos(),
                    y, x, z, offset);
            int centerX = Math.min(a.getX(), b.getX()) + Math.abs(a.getX() - b.getX()) / 2;
            int centerZ = Math.min(a.getZ(), b.getZ()) + Math.abs(a.getZ() - b.getZ()) / 2;
            assertEquals(target.offset(-centerX, -Math.min(a.getY(), b.getY()), -centerZ),
                    cache.anchorForCursorTarget(target));
            BlueprintGhostPreview preview = cache.preview(target, true);
            assertEquals(new net.minecraft.world.phys.AABB(a), preview.localBounds());
            assertEquals(1, preview.blocks().size());
            assertTrue(preview.truncated());
        }
    }

    @Test
    void rotationLimitIdentityAndClearInvalidateButEquivalentTurnsDoNot() {
        RtsBlueprint blueprint = blueprint(List.of(
                new RtsBlueprintBlock(BlockPos.ZERO, null, null),
                new RtsBlueprintBlock(new BlockPos(1, 2, 3), null, null)));
        BlueprintPreviewGeometryCache cache = new BlueprintPreviewGeometryCache();
        cache.prepare(blueprint, 1, 0, 0, 2);
        var first = cache.preview(BlockPos.ZERO, true).localBlocks();
        cache.prepare(blueprint, 5, 4, 0, 2);
        assertSame(first, cache.preview(BlockPos.ZERO, false).localBlocks());
        cache.prepare(blueprint, 2, 0, 0, 2);
        assertNotSame(first, cache.preview(BlockPos.ZERO, false).localBlocks());
        cache.prepare(blueprint, 2, 0, 0, 1);
        var limited = cache.preview(BlockPos.ZERO, false).localBlocks();
        assertEquals(1, limited.size());
        cache.clear();
        cache.prepare(blueprint, 2, 0, 0, 1);
        assertNotSame(limited, cache.preview(BlockPos.ZERO, false).localBlocks());
        var beforeIdentity = cache.preview(BlockPos.ZERO, false).localBlocks();
        cache.prepare(blueprint(blueprint.blocks()), 2, 0, 0, 1);
        assertNotSame(beforeIdentity, cache.preview(BlockPos.ZERO, false).localBlocks());
    }

    private static RtsBlueprint blueprint(List<RtsBlueprintBlock> blocks) {
        return new RtsBlueprint("test", "test.nbt", BlueprintFormat.VANILLA_NBT,
                new Vec3i(12, 9, 8), blocks, Map.of());
    }
}
