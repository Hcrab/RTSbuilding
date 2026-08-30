package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WindowTextBoxLayoutTest {
    @Test
    void innerEditBoxAndPlaceholderShareFourPixelInset() {
        WindowTextBoxLayout.Geometry geometry = WindowTextBoxLayout.geometry(
                new UiRect(10, 20, 100, 14), 9, 30, false, false);

        assertEquals(new UiRect(14, 21, 92, 12), geometry.inner);
        assertEquals(14.0D, geometry.placeholderX);
        assertEquals(22.0D, geometry.textY);
        assertEquals(new UiRect(10, 20, 100, 1), geometry.topBorder);
        assertEquals(new UiRect(109, 20, 1, 14), geometry.rightBorder);
    }

    @Test
    void centeredActiveValueMovesEditCursorWithItsText() {
        WindowTextBoxLayout.Geometry geometry = WindowTextBoxLayout.geometry(
                new UiRect(10, 20, 100, 14), 9, 30, true, true);

        assertEquals(new UiRect(45, 21, 92, 12), geometry.inner);
        assertEquals(45.0D, geometry.placeholderX);
    }
}
