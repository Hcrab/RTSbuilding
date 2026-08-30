package com.rtsbuilding.rtsbuilding.common.smartfill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmartFillPlannerTest {
    private static final SmartFillPlanner.Limits DEFAULT_LIMITS =
            new SmartFillPlanner.Limits(512, 16, 8192, 250_000);

    @Test
    void singleWallHoleFillsOnlyTheOpening() {
        SmartFillPlan plan = wallOpeningPlan(1, DEFAULT_LIMITS);

        assertTrue(plan.canSubmit());
        assertEquals(Set.of(new BlockPos(0, 0, 0)), Set.copyOf(plan.targets()));
    }

    @Test
    void threeByThreeWallHoleDoesNotEscapeIntoOpenAir() {
        SmartFillPlan plan = wallOpeningPlan(3, DEFAULT_LIMITS);

        assertTrue(plan.canSubmit());
        assertEquals(9, plan.targets().size());
        assertTrue(plan.targets().stream().allMatch(pos -> pos.getZ() == 0));
    }

    @Test
    void shallowFloorPitIsFilledButAirAboveItIsNot() {
        Function<BlockPos, SmartFillCell> floorPit = pos -> {
            if (pos.equals(new BlockPos(0, -2, 0))) {
                return SmartFillCell.BOUNDARY;
            }
            boolean insidePit = Math.abs(pos.getX()) <= 1 && Math.abs(pos.getZ()) <= 1;
            if (pos.getY() == -1 && insidePit) {
                return SmartFillCell.CANDIDATE;
            }
            if (pos.getY() == -1) {
                return SmartFillCell.BOUNDARY;
            }
            if (pos.getY() == -2) {
                return SmartFillCell.BOUNDARY;
            }
            return SmartFillCell.CANDIDATE;
        };

        SmartFillPlan plan = SmartFillPlanner.plan(
                new BlockPos(0, -2, 0), Direction.UP, DEFAULT_LIMITS, floorPit::apply);

        assertTrue(plan.canSubmit());
        assertEquals(9, plan.targets().size());
        assertTrue(plan.targets().stream().allMatch(pos -> pos.getY() == -1));
    }

    @Test
    void openPlainIsRejectedInsteadOfEscapingAcrossTheWorld() {
        SmartFillPlan plan = SmartFillPlanner.plan(
                BlockPos.ZERO,
                Direction.UP,
                DEFAULT_LIMITS,
                pos -> pos.getY() <= 0 ? SmartFillCell.BOUNDARY : SmartFillCell.CANDIDATE);

        assertFalse(plan.canSubmit());
        assertEquals(SmartFillPlan.Status.NO_TARGET, plan.status());
    }

    @Test
    void diagonalCandidateIsNotConnectedBySixWayBfs() {
        Set<BlockPos> holes = Set.of(new BlockPos(0, 0, 0), new BlockPos(1, 1, 0));
        SmartFillPlan plan = SmartFillPlanner.plan(
                new BlockPos(0, 0, 1), Direction.NORTH, DEFAULT_LIMITS,
                pos -> holes.contains(pos) ? SmartFillCell.CANDIDATE : SmartFillCell.BOUNDARY);

        assertEquals(Set.of(new BlockPos(0, 0, 0)), Set.copyOf(plan.targets()));
    }

    @Test
    void playerLimitReturnsStablePartialBfsOrder() {
        SmartFillPlanner.Limits limited = new SmartFillPlanner.Limits(4, 16, 8192, 250_000);
        SmartFillPlan first = wallOpeningPlan(3, limited);
        SmartFillPlan second = wallOpeningPlan(3, limited);

        assertEquals(SmartFillPlan.Status.USER_LIMIT_REACHED, first.status());
        assertEquals(4, first.targets().size());
        assertEquals(first.targets(), second.targets());
    }

    @Test
    void detectionDiameterClipsConnectedCandidatesInsideTheSpatialBoundary() {
        SmartFillPlanner.Limits narrow = new SmartFillPlanner.Limits(64, 4, 8192, 250_000);
        SmartFillPlan plan = SmartFillPlanner.plan(
                new BlockPos(0, 0, 1),
                Direction.NORTH,
                narrow,
                pos -> pos.getY() == 0 && pos.getZ() == 0 && Math.abs(pos.getX()) <= 3
                        ? SmartFillCell.CANDIDATE
                        : SmartFillCell.BOUNDARY);

        assertTrue(plan.canSubmit());
        assertEquals(SmartFillPlan.Status.DIAMETER_CLIPPED, plan.status());
        assertEquals(5, plan.targets().size());
        assertTrue(plan.targets().stream().allMatch(pos -> Math.abs(pos.getX()) <= 2));
    }

    @Test
    void hardLimitRejectsTheWholePlan() {
        SmartFillPlan plan = SmartFillPlanner.plan(
                BlockPos.ZERO,
                Direction.UP,
                new SmartFillPlanner.Limits(9, 8, 8, 1000),
                ignored -> SmartFillCell.CANDIDATE);

        assertEquals(SmartFillPlan.Status.HARD_LIMIT_REJECTED, plan.status());
        assertTrue(plan.targets().isEmpty());
    }

    @Test
    void unloadedProbeRejectsTheWholePlan() {
        SmartFillPlan plan = SmartFillPlanner.plan(
                new BlockPos(0, 0, 1), Direction.NORTH, DEFAULT_LIMITS,
                pos -> pos.equals(new BlockPos(0, 0, -1))
                        ? SmartFillCell.UNLOADED
                        : pos.equals(BlockPos.ZERO)
                        ? SmartFillCell.CANDIDATE
                        : SmartFillCell.BOUNDARY);

        assertEquals(SmartFillPlan.Status.UNLOADED_BOUNDARY, plan.status());
        assertTrue(plan.targets().isEmpty());
    }

    @Test
    void queryBudgetRejectsPathologicalScan() {
        SmartFillPlan plan = SmartFillPlanner.plan(
                new BlockPos(0, 0, 1), Direction.NORTH,
                new SmartFillPlanner.Limits(512, 32, 8192, 3),
                pos -> pos.equals(BlockPos.ZERO)
                        ? SmartFillCell.CANDIDATE
                        : SmartFillCell.BOUNDARY);

        assertEquals(SmartFillPlan.Status.QUERY_BUDGET_EXCEEDED, plan.status());
        assertTrue(plan.targets().isEmpty());
    }

    private static SmartFillPlan wallOpeningPlan(
            int size,
            SmartFillPlanner.Limits limits) {
        int radius = size / 2;
        BlockPos clickedBehindOpening = new BlockPos(0, 0, 1);
        Set<BlockPos> opening = new HashSet<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                opening.add(new BlockPos(x, y, 0));
            }
        }
        return SmartFillPlanner.plan(
                clickedBehindOpening,
                Direction.NORTH,
                limits,
                pos -> {
                    if (pos.equals(clickedBehindOpening)) {
                        return SmartFillCell.BOUNDARY;
                    }
                    if (pos.getZ() == 0) {
                        return opening.contains(pos)
                                ? SmartFillCell.CANDIDATE
                                : SmartFillCell.BOUNDARY;
                    }
                    return SmartFillCell.CANDIDATE;
                });
    }
}
