package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class WorkflowStyleTest {
    @Test
    void protectedStateOwnsTheRowFamilyWithoutLosingSuspendedDarkEdge() {
        WorkflowStyle.RowVisual visual =
                WorkflowStyle.row(true, true, false);

        assertEquals(WorkflowStyle.PROTECTED_BACKGROUND, visual.background);
        assertEquals(WorkflowStyle.PROTECTED_BORDER, visual.border);
        assertEquals(WorkflowStyle.SUSPENDED_DARK_BORDER, visual.darkBorder);
        assertEquals(
                WorkflowStyle.SUSPENDED_PROGRESS_TRACK,
                visual.progressTrack);
    }

    @Test
    void hoverOnlyChangesTheExpectedButtonSurface() {
        WorkflowStyle.ButtonVisual idle =
                WorkflowStyle.protect(false, false);
        WorkflowStyle.ButtonVisual hover =
                WorkflowStyle.protect(false, true);

        assertNotEquals(idle.background, hover.background);
        assertEquals(idle.border, hover.border);
        assertEquals(idle.darkBorder, hover.darkBorder);
        assertEquals(idle.text, hover.text);
    }

    @Test
    void suspendedAndPausedRowsUseResumeChromeButKeepLegacyDarkEdge() {
        WorkflowStyle.ButtonVisual suspended =
                WorkflowStyle.action(true, false, false);
        WorkflowStyle.ButtonVisual paused =
                WorkflowStyle.action(false, true, false);

        assertEquals(suspended.background, paused.background);
        assertEquals(suspended.border, paused.border);
        assertEquals(
                WorkflowStyle.SUSPENDED_RESUME_DARK_BORDER,
                suspended.darkBorder);
        assertEquals(
                WorkflowStyle.RESUME_DARK_BORDER,
                paused.darkBorder);
    }
}
