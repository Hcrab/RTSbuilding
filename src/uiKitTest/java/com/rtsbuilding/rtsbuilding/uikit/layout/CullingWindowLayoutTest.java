package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CullingWindowLayoutTest {
    @Test
    void preservesProductionCompactRows() {
        assertEquals(218, CullingWindowLayout.DEFAULT_WIDTH);
        assertEquals(116, CullingWindowLayout.DEFAULT_HEIGHT);
        assertEquals(54, CullingWindowLayout.dimensionRowY(0));
        assertEquals(70, CullingWindowLayout.hintRowY(0));
    }

    @Test
    void deleteHitUsesHalfOpenBounds() {
        assertTrue(CullingWindowLayout.containsDelete(100, 34, 100, 33));
        assertFalse(CullingWindowLayout.containsDelete(156, 34, 100, 33));
    }
}
