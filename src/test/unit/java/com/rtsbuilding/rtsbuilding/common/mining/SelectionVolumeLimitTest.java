package com.rtsbuilding.rtsbuilding.common.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionVolumeLimitTest {
    private static final SelectionVolumeLimit DEFAULT = SelectionVolumeLimit.defaults();

    @Test
    void defaultAxesRejectEightyLongStripEvenWhenVolumeFits() {
        assertFalse(DEFAULT.fits(80, 1, 1));
        assertTrue(new SelectionVolumeLimit(MiningLimits.DEFAULT_VOLUME, 80, 64, 64).fits(80, 1, 1));
    }

    @Test
    void defaultVolumeRejectsFullSixtyFourCube() {
        assertFalse(DEFAULT.fits(64, 64, 64));
    }

    @Test
    void spansAndProductsUseLongArithmetic() {
        assertFalse(DEFAULT.fits((long) Integer.MAX_VALUE, 1, 1));
        assertFalse(MiningSelectionBounds.between(Integer.MIN_VALUE, Integer.MAX_VALUE,
                0, 0, 0, 0).fits(DEFAULT));
    }
}
