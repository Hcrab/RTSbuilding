package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintFooterViewTest {
    @Test
    void captureSubmittingCannotSendDuplicateSaveOrCancel() {
        BlueprintFooterView view = BlueprintFooterView.capture(true, true);

        assertEquals(BlueprintFooterView.Stage.CAPTURE_SUBMITTING, view.stage());
        assertEquals(BlueprintFooterView.Action.SAVE_CAPTURE, view.primaryAction());
        assertTrue(view.primaryPending());
        assertFalse(view.primaryEnabled());
        assertFalse(view.secondaryEnabled());
    }

    @Test
    void pinnedPreviewHasOneBuildPrimaryAndExplicitCancelSecondary() {
        BlueprintFooterView view = BlueprintFooterView.placement(true);

        assertEquals(BlueprintFooterView.Action.BUILD_PREVIEW, view.primaryAction());
        assertTrue(view.primaryEnabled());
        assertEquals(BlueprintFooterView.Action.CANCEL_PREVIEW, view.secondaryAction());
    }

    @Test
    void cancelPreviewCanNeverBeMistakenForDeleteBlueprint() {
        BlueprintFooterView view = BlueprintFooterView.placement(false);

        assertEquals(BlueprintFooterView.Action.CANCEL_PREVIEW, view.secondaryAction());
        assertFalse(view.primaryEnabled());
    }
}
