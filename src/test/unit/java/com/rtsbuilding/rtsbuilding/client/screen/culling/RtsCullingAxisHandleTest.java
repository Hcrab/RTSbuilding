package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtsCullingAxisHandleTest {
    @Test
    void selectedBoxExposesOneHandleForEachFaceDirection() {
        RtsCullingBox box = new RtsCullingBox(
                1,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 66, 14));

        Set<Direction> directions = RtsCullingAxisHandle.handles(box).stream()
                .map(RtsCullingAxisHandle.Handle::direction)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                Direction.EAST,
                Direction.WEST,
                Direction.UP,
                Direction.DOWN,
                Direction.SOUTH,
                Direction.NORTH), directions);
    }

    @Test
    void negativeFaceHandlesCanBeHitFromOutsideTheBox() {
        RtsCullingBox box = new RtsCullingBox(
                1,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 66, 14));

        assertEquals(Direction.WEST, RtsCullingAxisHandle.nearestHit(
                box,
                new Vec3(8.0D, 65.5D, 12.5D),
                new Vec3(1.0D, 0.0D, 0.0D),
                8.0D).orElseThrow().direction());
        assertEquals(Direction.NORTH, RtsCullingAxisHandle.nearestHit(
                box,
                new Vec3(11.5D, 65.5D, 8.0D),
                new Vec3(0.0D, 0.0D, 1.0D),
                8.0D).orElseThrow().direction());
        assertEquals(Direction.DOWN, RtsCullingAxisHandle.nearestHit(
                box,
                new Vec3(11.5D, 62.0D, 12.5D),
                new Vec3(0.0D, 1.0D, 0.0D),
                8.0D).orElseThrow().direction());
    }
}
