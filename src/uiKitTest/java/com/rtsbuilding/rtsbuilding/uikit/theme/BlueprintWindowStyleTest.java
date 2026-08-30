package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class BlueprintWindowStyleTest {
    @Test
    void captureReadinessAndAxisAvailabilityUseDistinctSemantics() {
        assertEquals(BlueprintWindowStyle.WARNING_TEXT,
                BlueprintWindowStyle.captureState(false));
        assertEquals(BlueprintWindowStyle.READY_TEXT,
                BlueprintWindowStyle.captureState(true));
        assertEquals(BlueprintWindowStyle.DISABLED_TEXT,
                BlueprintWindowStyle.axisLabel(false));
        assertEquals(BlueprintWindowStyle.MUTED_TEXT,
                BlueprintWindowStyle.axisLabel(true));
    }

    @Test
    void enabledAndDisabledFieldsRemainVisuallyDistinct() {
        assertNotEquals(BlueprintWindowStyle.fieldBackground(true),
                BlueprintWindowStyle.fieldBackground(false));
        assertNotEquals(BlueprintWindowStyle.PRIMARY_ACTION_BACKGROUND,
                BlueprintWindowStyle.STATUS_BACKGROUND);
    }
}
