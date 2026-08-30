package com.rtsbuilding.rtsbuilding.uikit.theme;

import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class StorageWindowStyleTest {
    @Test
    void failedStateIsDistinctFromEmptyAndLoading() {
        assertEquals(
                StorageWindowStyle.STATUS_WARNING_TEXT,
                StorageWindowStyle.statusText(StorageUiStatus.EMPTY));
        assertEquals(
                StorageWindowStyle.STATUS_WARNING_TEXT,
                StorageWindowStyle.statusText(StorageUiStatus.LOADING));
        assertEquals(
                StorageWindowStyle.STATUS_FAILED_TEXT,
                StorageWindowStyle.statusText(StorageUiStatus.FAILED));
    }

    @Test
    void extractOnlyKeepsItsOwnSemanticFamilyDuringHover() {
        StorageWindowStyle.FrameVisual active =
                StorageWindowStyle.extract(true, false);
        StorageWindowStyle.FrameVisual hover =
                StorageWindowStyle.extract(true, true);
        StorageWindowStyle.FrameVisual idle =
                StorageWindowStyle.extract(false, false);

        assertNotEquals(active.background, hover.background);
        assertNotEquals(active.border, hover.border);
        assertEquals(active.text, hover.text);
        assertNotEquals(idle.background, active.background);
        assertNotEquals(idle.text, active.text);
    }

    @Test
    void unlinkHoverDoesNotLoseDestructiveTextOrDarkEdge() {
        StorageWindowStyle.FrameVisual idle =
                StorageWindowStyle.unlink(false);
        StorageWindowStyle.FrameVisual hover =
                StorageWindowStyle.unlink(true);

        assertNotEquals(idle.background, hover.background);
        assertNotEquals(idle.border, hover.border);
        assertEquals(idle.darkBorder, hover.darkBorder);
        assertEquals(idle.text, hover.text);
    }
}
