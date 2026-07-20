package com.rtsbuilding.rtsbuilding.server.service.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangeMiningHarvestTierTest {

    @Test
    void exposesTheFourPlayerFacingTiersInOrder() {
        assertEquals(1, RangeMiningHarvestTier.STONE.maxRequiredLevel());
        assertEquals(2, RangeMiningHarvestTier.IRON.maxRequiredLevel());
        assertEquals(3, RangeMiningHarvestTier.DIAMOND.maxRequiredLevel());
        assertTrue(RangeMiningHarvestTier.UNLIMITED.maxRequiredLevel() > 3);
        assertEquals(RangeMiningHarvestTier.IRON, RangeMiningHarvestTier.STONE.next());
        assertEquals(RangeMiningHarvestTier.STONE, RangeMiningHarvestTier.UNLIMITED.next());
    }
}
