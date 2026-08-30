package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickBuildUnlockPolicyTest {
    @Test
    void survivalDisabledKeepsEveryDestroyShapeAvailable() {
        assertTrue(QuickBuildUnlockPolicy.canUseDestroyShape(false, false, false, AreaMineShape.CHAIN));
        assertTrue(QuickBuildUnlockPolicy.canUseDestroyShape(false, false, false, AreaMineShape.BOX));
    }

    @Test
    void chainPluginOnlyUnlocksChainMining() {
        assertTrue(QuickBuildUnlockPolicy.canUseDestroyShape(true, true, false, AreaMineShape.CHAIN));
        assertFalse(QuickBuildUnlockPolicy.canUseDestroyShape(true, true, false, AreaMineShape.BLOCK));
        assertEquals(AreaMineShape.CHAIN,
                QuickBuildUnlockPolicy.firstAvailableDestroyShape(true, true, false));
    }

    @Test
    void areaAndHarvestPluginsUnlockNonChainShapesAndReplaceStaleChainDefault() {
        assertFalse(QuickBuildUnlockPolicy.canUseDestroyShape(true, false, true, AreaMineShape.CHAIN));
        assertTrue(QuickBuildUnlockPolicy.canUseDestroyShape(true, false, true, AreaMineShape.BLOCK));
        assertTrue(QuickBuildUnlockPolicy.canUseDestroyShape(true, false, true, AreaMineShape.BOX));
        assertEquals(AreaMineShape.BLOCK,
                QuickBuildUnlockPolicy.firstAvailableDestroyShape(true, false, true));
    }

    @Test
    void areaPluginWithoutHarvestTierUsesTheBaselineMiningLevel() {
        assertTrue(QuickBuildUnlockPolicy.canUseDestroyShape(
                true, false, true, AreaMineShape.BLOCK));
        assertTrue(QuickBuildUnlockPolicy.canUseAnyDestroyShape(true, false, true));
        assertEquals(AreaMineShape.BLOCK,
                QuickBuildUnlockPolicy.firstAvailableDestroyShape(true, false, true));
    }

    @Test
    void noDestroyPluginLeavesDestroyModeUnavailable() {
        assertFalse(QuickBuildUnlockPolicy.canUseAnyDestroyShape(true, false, false));
        assertNull(QuickBuildUnlockPolicy.firstAvailableDestroyShape(true, false, false));
    }
}
