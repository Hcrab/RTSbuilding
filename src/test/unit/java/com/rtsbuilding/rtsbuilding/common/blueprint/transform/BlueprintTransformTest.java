package com.rtsbuilding.rtsbuilding.common.blueprint.transform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintTransformTest {
    @Test
    void normalizeStepsWrapsNegativeAndLargeValues() {
        assertEquals(0, BlueprintTransform.normalizeSteps(0));
        assertEquals(1, BlueprintTransform.normalizeSteps(5));
        assertEquals(3, BlueprintTransform.normalizeSteps(-1));
        assertEquals(2, BlueprintTransform.normalizeSteps(-6));
    }

    @Test
    void yRotationKeepsAllBlocksAndSwapsHorizontalFootprint() {
        Bounds bounds = boundsAfterRotation(new Vec3i(3, 2, 5), 1, 0, 0);

        assertEquals(new Vec3i(5, 2, 3), bounds.size());
        assertEquals(30, bounds.positions().size());
    }

    @Test
    void xRotationKeepsAllBlocksAndSwapsVerticalFootprint() {
        Bounds bounds = boundsAfterRotation(new Vec3i(3, 2, 5), 0, 1, 0);

        assertEquals(new Vec3i(3, 5, 2), bounds.size());
        assertEquals(30, bounds.positions().size());
    }

    @Test
    void invalidCenterOffsetFallsBackToZero() {
        assertEquals(BlockPos.ZERO, BlueprintTransform.centerRotationOffset(Vec3i.ZERO, 1, 1, 1));
        assertEquals(BlockPos.ZERO, BlueprintTransform.centerRotationOffset(new Vec3i(3, -1, 2), 1, 1, 1));
    }

    private static Bounds boundsAfterRotation(Vec3i size, int ySteps, int xSteps, int zSteps) {
        BlockPos centerOffset = BlueprintTransform.centerRotationOffset(size, ySteps, xSteps, zSteps);
        Set<BlockPos> positions = new HashSet<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos rotated = BlueprintTransform.rotateAroundCenter(
                            new BlockPos(x, y, z), ySteps, xSteps, zSteps, centerOffset);
                    positions.add(rotated);
                    minX = Math.min(minX, rotated.getX());
                    minY = Math.min(minY, rotated.getY());
                    minZ = Math.min(minZ, rotated.getZ());
                    maxX = Math.max(maxX, rotated.getX());
                    maxY = Math.max(maxY, rotated.getY());
                    maxZ = Math.max(maxZ, rotated.getZ());
                }
            }
        }
        return new Bounds(positions, new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1));
    }

    private record Bounds(Set<BlockPos> positions, Vec3i size) {
    }
}
