package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtsCullingBoxTest {
    @Test
    void diagonalWithZeroHeightOffsetCreatesSinglePlane() {
        RtsCullingBox box = RtsCullingBox.fromDiagonal(
                1,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 64, 14),
                0);

        assertEquals(new BlockPos(10, 64, 10), box.min());
        assertEquals(new BlockPos(12, 64, 14), box.max());
        assertEquals(1, box.height());
    }

    @Test
    void positiveHeightOffsetGrowsUpwardFromFirstPoint() {
        RtsCullingBox box = RtsCullingBox.fromDiagonal(
                1,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 64, 14),
                3);

        assertEquals(64, box.min().getY());
        assertEquals(67, box.max().getY());
        assertEquals(4, box.height());
    }

    @Test
    void negativeHeightOffsetGrowsDownwardFromFirstPoint() {
        RtsCullingBox box = RtsCullingBox.fromDiagonal(
                1,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 64, 14),
                -3);

        assertEquals(61, box.min().getY());
        assertEquals(64, box.max().getY());
        assertEquals(4, box.height());
    }

    @Test
    void positiveHandleScrollDownOnSingleBlockStaysPinned() {
        RtsCullingBox box = RtsCullingBox.fromDiagonal(
                1,
                new BlockPos(10, 64, 10),
                new BlockPos(10, 64, 10),
                0);

        RtsCullingBox resized = box.resizeFromHandle(Direction.UP, -1);

        assertEquals(64, resized.min().getY());
        assertEquals(64, resized.max().getY());
        assertEquals(1, resized.height());
    }

    @Test
    void positiveHandleScrollDownShrinksPositiveFaceOnly() {
        RtsCullingBox box = new RtsCullingBox(
                1,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 64, 10));

        RtsCullingBox resized = box.resizeFromHandle(Direction.EAST, -3);

        assertEquals(10, resized.min().getX());
        assertEquals(10, resized.max().getX());
        assertEquals(1, resized.width());
    }

    @Test
    void negativeXHandleScrollUpExpandsNegativeFaceOnly() {
        RtsCullingBox box = new RtsCullingBox(
                1,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 64, 10));

        RtsCullingBox resized = box.resizeFromHandle(Direction.WEST, 2);

        assertEquals(8, resized.min().getX());
        assertEquals(12, resized.max().getX());
        assertEquals(5, resized.width());
    }

    @Test
    void negativeXHandleScrollDownShrinksNegativeFaceOnly() {
        RtsCullingBox box = new RtsCullingBox(
                1,
                new BlockPos(8, 64, 10),
                new BlockPos(12, 64, 10));

        RtsCullingBox resized = box.resizeFromHandle(Direction.WEST, -2);

        assertEquals(10, resized.min().getX());
        assertEquals(12, resized.max().getX());
        assertEquals(3, resized.width());
    }

    @Test
    void negativeZHandleScrollDownOnSingleBlockStaysPinned() {
        RtsCullingBox box = new RtsCullingBox(
                1,
                new BlockPos(10, 64, 10),
                new BlockPos(10, 64, 10));

        RtsCullingBox resized = box.resizeFromHandle(Direction.NORTH, -2);

        assertEquals(10, resized.min().getZ());
        assertEquals(10, resized.max().getZ());
        assertEquals(1, resized.depth());
    }

    @Test
    void shrinkingDirectionHandleToOneNeverMovesTheOppositeFace() {
        RtsCullingBox box = new RtsCullingBox(
                1,
                new BlockPos(10, 64, 20),
                new BlockPos(14, 68, 24));

        RtsCullingBox east = box.resizeFromHandle(Direction.EAST, -20);
        assertEquals(10, east.min().getX());
        assertEquals(10, east.max().getX());

        RtsCullingBox west = box.resizeFromHandle(Direction.WEST, -20);
        assertEquals(14, west.min().getX());
        assertEquals(14, west.max().getX());

        RtsCullingBox up = box.resizeFromHandle(Direction.UP, -20);
        assertEquals(64, up.min().getY());
        assertEquals(64, up.max().getY());

        RtsCullingBox down = box.resizeFromHandle(Direction.DOWN, -20);
        assertEquals(68, down.min().getY());
        assertEquals(68, down.max().getY());

        RtsCullingBox south = box.resizeFromHandle(Direction.SOUTH, -20);
        assertEquals(20, south.min().getZ());
        assertEquals(20, south.max().getZ());

        RtsCullingBox north = box.resizeFromHandle(Direction.NORTH, -20);
        assertEquals(24, north.min().getZ());
        assertEquals(24, north.max().getZ());
    }
}
