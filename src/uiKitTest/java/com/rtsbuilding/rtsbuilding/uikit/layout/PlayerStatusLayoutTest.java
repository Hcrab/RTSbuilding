package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PlayerStatusLayoutTest {
    @Test
    void anchorsRowsAtTheProductionTopRightPixels() {
        assertEquals(new UiRect(502, 60, 130, 10), PlayerStatusLayout.bar(640, 56, 0));
        assertEquals(new UiRect(502, 72, 130, 10), PlayerStatusLayout.bar(640, 56, 1));
        assertEquals(new UiRect(502, 96, 130, 10), PlayerStatusLayout.bar(640, 56, 3));
    }

    @Test
    void rejectsInvalidScreenAndRowInputs() {
        assertThrows(IllegalArgumentException.class, () -> PlayerStatusLayout.bar(0, 56, 0));
        assertThrows(IllegalArgumentException.class, () -> PlayerStatusLayout.bar(640, 56, -1));
    }
}
