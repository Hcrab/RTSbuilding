package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCullingAxisHandleTest {
    @Test
    void selectedBoxExposesOneHandleForEachFaceDirection() {
        RtsCullingBox box = new RtsCullingBox(
                1,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 66, 14));

        Set<EnumFacing> directions = RtsCullingAxisHandle.handles(box).stream()
                .map(RtsCullingAxisHandle.Handle::direction)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                EnumFacing.EAST,
                EnumFacing.WEST,
                EnumFacing.UP,
                EnumFacing.DOWN,
                EnumFacing.SOUTH,
                EnumFacing.NORTH), directions);
    }

    @Test
    void negativeFaceHandlesCanBeHitFromOutsideTheBox() {
        RtsCullingBox box = new RtsCullingBox(
                1,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 66, 14));

        assertEquals(EnumFacing.WEST, RtsCullingAxisHandle.nearestHit(
                box,
                new Vec3d(8.0D, 65.5D, 12.5D),
                new Vec3d(1.0D, 0.0D, 0.0D),
                8.0D).orElseThrow().direction());
        assertEquals(EnumFacing.NORTH, RtsCullingAxisHandle.nearestHit(
                box,
                new Vec3d(11.5D, 65.5D, 8.0D),
                new Vec3d(0.0D, 0.0D, 1.0D),
                8.0D).orElseThrow().direction());
        assertEquals(EnumFacing.DOWN, RtsCullingAxisHandle.nearestHit(
                box,
                new Vec3d(11.5D, 62.0D, 12.5D),
                new Vec3d(0.0D, 1.0D, 0.0D),
                8.0D).orElseThrow().direction());
    }

    @Test
    void allowedDirectionsFilterRenderedAndRaycastHandles() {
        RtsCullingBox box = new RtsCullingBox(
                1,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 66, 14));
        Set<EnumFacing> allowed = Set.of(EnumFacing.EAST, EnumFacing.WEST);

        Set<EnumFacing> directions = RtsCullingAxisHandle.handles(box.asAabb(), allowed).stream()
                .map(RtsCullingAxisHandle.Handle::direction)
                .collect(Collectors.toSet());

        assertEquals(allowed, directions);
        assertTrue(RtsCullingAxisHandle.nearestHit(
                box,
                new Vec3d(11.5D, 68.0D, 12.5D),
                new Vec3d(0.0D, -1.0D, 0.0D),
                8.0D,
                allowed).isEmpty());
    }
}
