package com.rtsbuilding.rtsbuilding.uicore.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;

class BlueprintUiReducerTest {
    @Test
    void captureMustReachSecondPointBeforeSaveCanDispatch() {
        BlueprintUiState selecting = state(BlueprintUiState.Mode.CAPTURE_WAITING_FIRST, null);
        BlueprintUiTransition blocked = BlueprintUiReducer.apply(selecting,
                BlueprintUiAction.simple(BlueprintUiAction.Type.SAVE_CAPTURE));
        assertEquals(BlueprintUiTransition.Command.NONE, blocked.command);

        BlueprintUiState waitingSecond = BlueprintUiReducer.apply(selecting,
                BlueprintUiAction.simple(BlueprintUiAction.Type.ACCEPT_CAPTURE_POINT)).state;
        assertEquals(BlueprintUiState.Mode.CAPTURE_WAITING_SECOND, waitingSecond.mode);
        BlueprintUiState ready = BlueprintUiReducer.apply(waitingSecond,
                BlueprintUiAction.simple(BlueprintUiAction.Type.ACCEPT_CAPTURE_POINT)).state;
        assertEquals(BlueprintUiState.Mode.CAPTURE_READY, ready.mode);
        BlueprintUiTransition saving = BlueprintUiReducer.apply(ready,
                BlueprintUiAction.simple(BlueprintUiAction.Type.SAVE_CAPTURE));
        assertEquals(BlueprintUiState.Mode.CAPTURE_READY, saving.state.mode);
        assertTrue(saving.state.nameWindowOpen);
        BlueprintUiState named = BlueprintUiReducer.apply(saving.state,
                BlueprintUiAction.text(BlueprintUiAction.Type.SET_NAME_DRAFT, "Harbour")).state;
        BlueprintUiTransition confirmed = BlueprintUiReducer.apply(named,
                BlueprintUiAction.simple(BlueprintUiAction.Type.CONFIRM_NAME));
        assertEquals(BlueprintUiState.Mode.CAPTURE_SAVING, confirmed.state.mode);
        assertEquals(BlueprintUiTransition.Command.SAVE_CAPTURE, saving.command);
    }

    @Test
    void captureResizeKeepsProductionMinimums() {
        BlueprintUiState ready = state(BlueprintUiState.Mode.CAPTURE_READY, null);
        BlueprintUiState resized = BlueprintUiReducer.apply(ready,
                BlueprintUiAction.vector(BlueprintUiAction.Type.RESIZE_CAPTURE, -99, -99, -99)).state;
        assertEquals(new BlueprintInt3(1, 0, 1), resized.captureSize);
    }

    @Test
    void captureSizeTextCommitUsesAbsoluteProductionMinimums() {
        BlueprintUiState ready = state(BlueprintUiState.Mode.CAPTURE_READY, null);
        BlueprintUiTransition transition = BlueprintUiReducer.apply(ready,
                BlueprintUiAction.vector(BlueprintUiAction.Type.SET_CAPTURE_SIZE, 20, 0, -5));
        assertEquals(BlueprintUiTransition.Command.SET_CAPTURE_SIZE, transition.command);
        assertEquals(new BlueprintInt3(20, 1, 1), transition.state.captureSize);
    }

    @Test
    void placementMustBePinnedBeforeBuildDispatches() {
        BlueprintUiState selected = state(BlueprintUiState.Mode.PLACEMENT_SELECTED, null);
        assertEquals(BlueprintUiTransition.Command.NONE, BlueprintUiReducer.apply(selected,
                BlueprintUiAction.simple(BlueprintUiAction.Type.BUILD)).command);
        BlueprintUiState pinned = BlueprintUiReducer.apply(selected,
                BlueprintUiAction.vector(BlueprintUiAction.Type.PIN_PREVIEW, 10, 64, -3)).state;
        assertTrue(pinned.isPinned());
        assertEquals(BlueprintUiTransition.Command.BUILD, BlueprintUiReducer.apply(pinned,
                BlueprintUiAction.simple(BlueprintUiAction.Type.BUILD)).command);
    }

    @Test
    void nudgeAndRotationUsePinnedAnchorAndQuarterTurns() {
        BlueprintUiState pinned = state(BlueprintUiState.Mode.PLACEMENT_PINNED,
                new BlueprintInt3(10, 64, -3));
        BlueprintUiState moved = BlueprintUiReducer.apply(pinned,
                BlueprintUiAction.vector(BlueprintUiAction.Type.NUDGE_ANCHOR, -2, 1, 4)).state;
        assertEquals(new BlueprintInt3(8, 65, 1), moved.anchor);
        BlueprintUiState rotated = BlueprintUiReducer.apply(moved,
                BlueprintUiAction.vector(BlueprintUiAction.Type.ROTATE_Y, 0, -1, 0)).state;
        assertEquals(3, rotated.yRotationSteps);
    }

    @Test
    void allRotationAxesAndResetStayInTheSharedStateMachine() {
        BlueprintUiState state = state(BlueprintUiState.Mode.PLACEMENT_SELECTED, null);
        state = BlueprintUiReducer.apply(state,
                BlueprintUiAction.vector(BlueprintUiAction.Type.ROTATE_X, 1, 0, 0)).state;
        state = BlueprintUiReducer.apply(state,
                BlueprintUiAction.vector(BlueprintUiAction.Type.ROTATE_Y, 0, -1, 0)).state;
        state = BlueprintUiReducer.apply(state,
                BlueprintUiAction.vector(BlueprintUiAction.Type.ROTATE_Z, 0, 0, 2)).state;
        assertEquals(1, state.xRotationSteps);
        assertEquals(3, state.yRotationSteps);
        assertEquals(2, state.zRotationSteps);

        BlueprintUiTransition reset = BlueprintUiReducer.apply(state,
                BlueprintUiAction.simple(BlueprintUiAction.Type.RESET_ROTATION));
        assertEquals(BlueprintUiTransition.Command.RESET_ROTATION, reset.command);
        assertEquals(0, reset.state.xRotationSteps);
        assertEquals(0, reset.state.yRotationSteps);
        assertEquals(0, reset.state.zRotationSteps);
    }

    @Test
    void materialsAndNameWindowsRemainExplicitLayers() {
        BlueprintUiState selected = state(BlueprintUiState.Mode.PLACEMENT_SELECTED, null);
        BlueprintUiState materials = BlueprintUiReducer.apply(selected,
                BlueprintUiAction.simple(BlueprintUiAction.Type.OPEN_MATERIALS)).state;
        assertTrue(materials.materialWindowOpen);
        BlueprintUiState renamed = BlueprintUiReducer.apply(materials,
                BlueprintUiAction.text(BlueprintUiAction.Type.OPEN_NAME_RENAME, "Harbour")).state;
        assertTrue(renamed.nameWindowOpen);
        assertFalse(renamed.captureNameMode);
        assertEquals("Harbour", renamed.nameDraft);
        BlueprintUiState edited = BlueprintUiReducer.apply(renamed,
                BlueprintUiAction.text(BlueprintUiAction.Type.APPEND_NAME_CHAR, "N")).state;
        assertEquals("N", edited.nameDraft);
        edited = BlueprintUiReducer.apply(edited,
                BlueprintUiAction.text(BlueprintUiAction.Type.APPEND_NAME_CHAR, "ew")).state;
        assertEquals("New", edited.nameDraft);
        edited = BlueprintUiReducer.apply(edited,
                BlueprintUiAction.simple(BlueprintUiAction.Type.BACKSPACE_NAME)).state;
        assertEquals("Ne", edited.nameDraft);
        BlueprintUiState scrolled = BlueprintUiReducer.apply(materials,
                BlueprintUiAction.vector(BlueprintUiAction.Type.SCROLL_MATERIALS, 0, 3, 0)).state;
        assertEquals(3, scrolled.materialScroll);
    }

    @Test
    void clearReturnsToHiddenWithoutLeavingPinnedState() {
        BlueprintUiState pinned = state(BlueprintUiState.Mode.PLACEMENT_PINNED,
                new BlueprintInt3(1, 2, 3));
        BlueprintUiState cleared = BlueprintUiReducer.apply(pinned,
                BlueprintUiAction.simple(BlueprintUiAction.Type.CLEAR)).state;
        assertEquals(BlueprintUiState.Mode.HIDDEN, cleared.mode);
        assertFalse(cleared.isPinned());
    }

    private static BlueprintUiState state(BlueprintUiState.Mode mode, BlueprintInt3 anchor) {
        return new BlueprintUiState(mode, "Harbour", "32 x 18 x 24", 0, 4,
                new BlueprintInt3(4, 3, 5), null, null, 60L, "ready", 0xFFFFFFFF, anchor,
                0, 0, 0, false, 0, false, false, "", false,
                BlueprintMaterialUiState.EMPTY);
    }
}
