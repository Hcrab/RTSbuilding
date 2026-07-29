package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class WorkflowResumeStyleTest {
    @Test
    void enabledActionsKeepSeparateStrategyColors() {
        WorkflowResumeStyle.ActionVisual resume =
                WorkflowResumeStyle.action(
                        WorkflowResumeStyle.ActionKind.RESUME,
                        true,
                        false);
        WorkflowResumeStyle.ActionVisual skip =
                WorkflowResumeStyle.action(
                        WorkflowResumeStyle.ActionKind.SKIP,
                        true,
                        false);
        WorkflowResumeStyle.ActionVisual overwrite =
                WorkflowResumeStyle.action(
                        WorkflowResumeStyle.ActionKind.OVERWRITE,
                        true,
                        false);

        assertNotEquals(resume.background, skip.background);
        assertNotEquals(skip.background, overwrite.background);
        assertEquals(
                WorkflowResumeStyle.PROGRESS_TEXT,
                overwrite.border);
    }

    @Test
    void disabledActionUsesMutedSharedText() {
        WorkflowResumeStyle.ActionVisual disabled =
                WorkflowResumeStyle.action(
                        WorkflowResumeStyle.ActionKind.RESUME,
                        false,
                        true);

        assertEquals(0xCC444444, disabled.background.toArgb());
        assertEquals(
                WorkflowResumeStyle.DISABLED_TEXT,
                disabled.text);
    }
}
