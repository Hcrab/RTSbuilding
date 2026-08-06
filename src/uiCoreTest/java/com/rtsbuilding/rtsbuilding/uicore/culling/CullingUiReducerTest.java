package com.rtsbuilding.rtsbuilding.uicore.culling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CullingUiReducerTest {
    @Test
    void deleteRequiresSelection() {
        assertEquals(CullingUiTransition.Command.NONE, CullingUiReducer.apply(
                state(CullingUiPhase.IDLE, -1),
                CullingUiAction.simple(CullingUiAction.Type.DELETE_SELECTED)).command);
        CullingUiTransition deleted = CullingUiReducer.apply(state(CullingUiPhase.IDLE, 4),
                CullingUiAction.simple(CullingUiAction.Type.DELETE_SELECTED));
        assertEquals(CullingUiTransition.Command.DELETE_SELECTED, deleted.command);
        assertEquals(1, deleted.state.boxCount);
    }

    @Test
    void heightAndConfirmRequireDraftPhases() {
        CullingUiState height = state(CullingUiPhase.NEED_HEIGHT, -1);
        assertEquals(4, CullingUiReducer.apply(height, CullingUiAction.height(4)).state.previewHeight);
        assertEquals(CullingUiTransition.Command.CONFIRM_DRAFT, CullingUiReducer.apply(height,
                CullingUiAction.simple(CullingUiAction.Type.CONFIRM_DRAFT)).command);
        assertEquals(CullingUiTransition.Command.NONE, CullingUiReducer.apply(
                state(CullingUiPhase.NEED_SECOND, -1),
                CullingUiAction.simple(CullingUiAction.Type.CONFIRM_DRAFT)).command);
    }

    @Test
    void handleResizeRequiresSelectionDirectionAndDelta() {
        assertEquals(CullingUiTransition.Command.RESIZE_HANDLE, CullingUiReducer.apply(
                state(CullingUiPhase.IDLE, 4),
                CullingUiAction.handle(CullingUiDirection.WEST, 2)).command);
        assertEquals(CullingUiTransition.Command.NONE, CullingUiReducer.apply(
                state(CullingUiPhase.IDLE, -1),
                CullingUiAction.handle(CullingUiDirection.WEST, 2)).command);
    }

    private static CullingUiState state(CullingUiPhase phase, int selected) {
        return new CullingUiState(true, true, "", phase, 2, selected,
                selected < 0 ? 0 : 12, selected < 0 ? 0 : 8, selected < 0 ? 0 : 6,
                0, -1, null, null);
    }
}
