package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickBuildPreferenceStateTest {
    @Test
    void verticalPreferenceSupportsLineAndRoundShapesOnly() {
        QuickBuildPreferenceState preferences = new QuickBuildPreferenceState();

        preferences.vertical(BuildShape.LINE, true);
        preferences.vertical(BuildShape.CIRCLE, true);
        preferences.vertical(BuildShape.CYLINDER, true);
        preferences.vertical(BuildShape.BOX, true);

        assertTrue(preferences.vertical(BuildShape.LINE));
        assertTrue(preferences.vertical(BuildShape.CIRCLE));
        assertTrue(preferences.vertical(BuildShape.CYLINDER));
        assertFalse(preferences.vertical(BuildShape.BOX));
    }
}
