package com.rtsbuilding.rtsbuilding.common.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionVolumeLimitTest {
    @Test
    void defaultAxesAndVolumeAreIndependent() {
        assertFalse(SelectionVolumeLimit.defaults().fits(80, 1, 1));
        assertTrue(new SelectionVolumeLimit(MiningLimits.DEFAULT_VOLUME, 80, 64, 64).fits(80, 1, 1));
        assertFalse(SelectionVolumeLimit.defaults().fits(64, 64, 64));
    }

    @Test
    void coordinateSpanDoesNotOverflow() {
        assertFalse(MiningSelectionBounds.between(Integer.MIN_VALUE, Integer.MAX_VALUE,
                0, 0, 0, 0).fits(SelectionVolumeLimit.defaults()));
    }
}
