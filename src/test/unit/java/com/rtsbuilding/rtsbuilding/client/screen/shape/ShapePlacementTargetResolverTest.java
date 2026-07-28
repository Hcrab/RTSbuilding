package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapePlacementTargetResolverTest {
    @Test
    void clickedTargetUsesOriginalCellWhenReplaceableAndAdjacentWhenOccupied() {
        FakeWorld world = new FakeWorld(
                true,
                pos -> true,
                (pos, face) -> pos.equals(BlockPos.ORIGIN));

        assertEquals(BlockPos.ORIGIN, ShapePlacementTargetResolver.resolveClickedTarget(
                BlockPos.ORIGIN,
                EnumFacing.UP,
                world));
        assertEquals(new BlockPos(1, 1, 0), ShapePlacementTargetResolver.resolveClickedTarget(
                new BlockPos(1, 0, 0),
                EnumFacing.UP,
                world));
    }

    @Test
    void unloadedCellKeepsClickedCoordinate() {
        BlockPos clicked = new BlockPos(4, 5, 6);
        FakeWorld world = new FakeWorld(
                true,
                pos -> false,
                (pos, face) -> false);

        assertEquals(clicked, ShapePlacementTargetResolver.resolveClickedTarget(
                clicked,
                EnumFacing.NORTH,
                world));
    }

    @Test
    void uniformPlanePlacementUsesAnchorOffsetAcrossEveryCell() {
        ShapeBuildTypes.Input input = input(BuildShape.LINE);
        List<BlockPos> targets = List.of(
                BlockPos.ORIGIN,
                new BlockPos(1, 0, 0),
                new BlockPos(2, 0, 0));
        FakeWorld world = new FakeWorld(
                true,
                pos -> true,
                (pos, face) -> false);

        assertEquals(List.of(
                        new BlockPos(0, 1, 0),
                        new BlockPos(1, 1, 0),
                        new BlockPos(2, 1, 0)),
                ShapePlacementTargetResolver.resolveTargets(input, targets, false, world));
    }

    @Test
    void nonUniformPlacementResolvesEachCellAgainstLocalReplacement() {
        ShapeBuildTypes.Input input = input(BuildShape.CIRCLE);
        BlockPos replaceable = BlockPos.ORIGIN;
        BlockPos occupied = new BlockPos(1, 0, 0);
        FakeWorld world = new FakeWorld(
                true,
                pos -> true,
                (pos, face) -> pos.equals(replaceable));

        assertEquals(List.of(replaceable, occupied.up()),
                ShapePlacementTargetResolver.resolveTargets(
                        input,
                        List.of(replaceable, occupied),
                        false,
                        world));
    }

    @Test
    void strictEmptyLockFiltersResolvedOccupiedTargets() {
        ShapeBuildTypes.Input input = input(BuildShape.CIRCLE);
        BlockPos keep = BlockPos.ORIGIN;
        BlockPos skip = new BlockPos(1, 0, 0);
        Set<BlockPos> replaceable = Set.of(keep);
        FakeWorld world = new FakeWorld(
                true,
                pos -> true,
                (pos, face) -> replaceable.contains(pos));

        assertEquals(List.of(keep),
                ShapePlacementTargetResolver.resolveTargets(
                        input,
                        List.of(keep, skip),
                        true,
                        world));
    }

    @Test
    void unavailableWorldFailsClosed() {
        FakeWorld world = new FakeWorld(
                false,
                pos -> true,
                (pos, face) -> true);

        assertEquals(List.of(), ShapePlacementTargetResolver.resolveTargets(
                input(BuildShape.LINE),
                List.of(BlockPos.ORIGIN),
                false,
                world));
        assertNull(ShapePlacementTargetResolver.resolveClickedTarget(
                BlockPos.ORIGIN,
                EnumFacing.UP,
                world));
    }

    @Test
    void invalidInputReturnsOrderedDistinctImmutableCoordinates() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(2, 0, 0);
        List<BlockPos> resolved = ShapePlacementTargetResolver.resolveTargets(
                null,
                List.of(BlockPos.ORIGIN, mutable, BlockPos.ORIGIN, mutable),
                false,
                null);

        assertEquals(List.of(BlockPos.ORIGIN, new BlockPos(2, 0, 0)), resolved);
        mutable.setPos(9, 9, 9);
        assertEquals(new BlockPos(2, 0, 0), resolved.get(1));
        assertThrows(UnsupportedOperationException.class, () -> resolved.add(BlockPos.ORIGIN));
    }

    @Test
    void overwriteKeepsExactGeometryCoordinatesWithoutAdjacentFaceShift() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(1, 0, 0);
        List<BlockPos> resolved = ShapePlacementTargetResolver.resolveOverwriteTargets(
                List.of(BlockPos.ORIGIN, mutable, BlockPos.ORIGIN));

        assertEquals(List.of(BlockPos.ORIGIN, new BlockPos(1, 0, 0)), resolved);
        mutable.setPos(8, 8, 8);
        assertEquals(new BlockPos(1, 0, 0), resolved.get(1));
    }

    @Test
    void uniformShapeCatalogMatchesProductionIntent() {
        assertTrue(ShapePlacementTargetResolver.usesUniformPlanePlacement(BuildShape.LINE));
        assertTrue(ShapePlacementTargetResolver.usesUniformPlanePlacement(BuildShape.SQUARE));
        assertTrue(ShapePlacementTargetResolver.usesUniformPlanePlacement(BuildShape.WALL));
        assertTrue(ShapePlacementTargetResolver.usesUniformPlanePlacement(BuildShape.CYLINDER));
        assertTrue(ShapePlacementTargetResolver.usesUniformPlanePlacement(BuildShape.BALL));
        assertTrue(ShapePlacementTargetResolver.usesUniformPlanePlacement(BuildShape.BOX));

        assertEquals(false, ShapePlacementTargetResolver.usesUniformPlanePlacement(BuildShape.BLOCK));
        assertEquals(false, ShapePlacementTargetResolver.usesUniformPlanePlacement(BuildShape.CIRCLE));
        assertEquals(false, ShapePlacementTargetResolver.usesUniformPlanePlacement(null));
    }

    private static ShapeBuildTypes.Input input(BuildShape shape) {
        return new ShapeBuildTypes.Input(
                shape,
                EnumFacing.UP,
                EnumFacing.UP,
                BlockPos.ORIGIN,
                new BlockPos(2, 0, 0),
                0,
                false);
    }

    private record FakeWorld(
            boolean available,
            Predicate<BlockPos> loaded,
            BiPredicate<BlockPos, EnumFacing> replaceable)
            implements ShapePlacementTargetResolver.PlacementWorld {
        @Override
        public boolean hasChunkAt(BlockPos pos) {
            return loaded.test(pos);
        }

        @Override
        public boolean canReplace(BlockPos pos, EnumFacing face) {
            return replaceable.test(pos, face);
        }
    }
}
