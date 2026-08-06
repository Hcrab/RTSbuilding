package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class BlueprintLibraryStyleTest {
    @Test
    void invalidSelectionDoesNotReuseHealthySelectionColor() {
        assertNotEquals(
                BlueprintLibraryStyle.rowBackground(
                        true,
                        false,
                        true,
                        false),
                BlueprintLibraryStyle.rowBackground(
                        false,
                        false,
                        true,
                        false));
        assertEquals(
                BlueprintLibraryStyle.ROW_INVALID_BACKGROUND,
                BlueprintLibraryStyle.rowBackground(
                        false,
                        false,
                        false,
                        true));
    }

    @Test
    void hoverOnlyOverridesHealthyUnselectedRows() {
        assertEquals(
                BlueprintLibraryStyle.ROW_HOVER_BACKGROUND,
                BlueprintLibraryStyle.rowBackground(
                        true,
                        true,
                        false,
                        true));
        assertEquals(
                BlueprintLibraryStyle.ROW_SELECTED_BACKGROUND,
                BlueprintLibraryStyle.rowBackground(
                        true,
                        true,
                        true,
                        true));
    }

    @Test
    void focusAndProgressKeepSeparateSemanticFamilies() {
        assertNotEquals(
                BlueprintLibraryStyle.search(false).background,
                BlueprintLibraryStyle.search(true).background);
        assertNotEquals(
                BlueprintLibraryStyle.progress(false),
                BlueprintLibraryStyle.progress(true));
        assertNotEquals(
                BlueprintLibraryStyle.STATUS_SUCCESS_TEXT,
                BlueprintLibraryStyle.STATUS_ERROR_TEXT);
        assertEquals(
                BlueprintLibraryStyle.SEARCH_TEXT,
                BlueprintLibraryStyle.search(true).text);
    }
}
