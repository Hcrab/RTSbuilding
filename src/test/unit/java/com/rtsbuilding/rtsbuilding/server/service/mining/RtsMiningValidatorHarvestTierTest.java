package com.rtsbuilding.rtsbuilding.server.service.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsMiningValidatorHarvestTierTest {
    @Test
    void baselineTierMinesLevelZeroWhileWoodPluginUnlocksLevelOne() {
        assertTrue(RtsMiningValidator.canRangeMineRequiredLevel(true, false, 0, 0));
        assertFalse(RtsMiningValidator.canRangeMineRequiredLevel(true, false, 1, 0));
        assertTrue(RtsMiningValidator.canRangeMineRequiredLevel(
                true, false, 1, RangeMiningHarvestTier.STONE.maxRequiredLevel()));
    }

    @Test
    void reportsOnlyWhenAValidToolIsStoppedByThePluginTier() {
        assertTrue(RtsMiningValidator.isBlockedByRangeMiningHarvestTier(
                true, false, 3, RangeMiningHarvestTier.IRON.maxRequiredLevel()));
        assertFalse(RtsMiningValidator.isBlockedByRangeMiningHarvestTier(
                true, false, 3, RangeMiningHarvestTier.DIAMOND.maxRequiredLevel()));
        assertFalse(RtsMiningValidator.isBlockedByRangeMiningHarvestTier(
                true, true, 3, RangeMiningHarvestTier.STONE.maxRequiredLevel()));
    }

    @Test
    void wrongToolIsNotMisreportedAsAPluginTierProblem() {
        assertFalse(RtsMiningValidator.isBlockedByRangeMiningHarvestTier(
                false, false, 3, RangeMiningHarvestTier.IRON.maxRequiredLevel()));
    }
}
