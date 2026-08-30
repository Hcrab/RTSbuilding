package com.rtsbuilding.rtsbuilding.client.screen.mode;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacementStateCombinationPlanTest {

    @Test
    void stairsProduceEightCompleteFacingAndHalfChoicesOnOnePage() {
        List<int[]> combinations = PlacementStateCombinationPlan.combinations(
                List.of(4, 2), 128);

        assertEquals(8, combinations.size());
        assertArrayEquals(new int[]{0, 0}, combinations.getFirst());
        assertArrayEquals(new int[]{3, 1}, combinations.getLast());
        assertEquals(1, PlacementStateCombinationPlan.pageCount(combinations.size(), 8));
    }

    @Test
    void sixteenAnglesProduceTwoPages() {
        assertEquals(2, PlacementStateCombinationPlan.pageCount(
                PlacementStateCombinationPlan.combinations(List.of(16), 128).size(),
                8));
    }

    @Test
    void pathologicalModdedPropertyProductIsBounded() {
        List<int[]> combinations = PlacementStateCombinationPlan.combinations(
                List.of(6, 3, 2, 16), 128);

        assertEquals(128, combinations.size());
        assertEquals(16, PlacementStateCombinationPlan.pageCount(combinations.size(), 8));
    }
}
