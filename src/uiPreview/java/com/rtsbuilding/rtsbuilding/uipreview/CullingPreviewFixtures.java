package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiDirection;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiPhase;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiState;

/** 范围剔除窗口和世界手柄的 preview-only Core 快照。 */
final class CullingPreviewFixtures {
    private CullingPreviewFixtures() {}

    static boolean supports(UiPreviewScenario.Variant variant) {
        return variant == UiPreviewScenario.Variant.CULLING_EMPTY
                || variant == UiPreviewScenario.Variant.CULLING_DRAFT
                || variant == UiPreviewScenario.Variant.CULLING_SELECTED;
    }

    static CullingUiState forScenario(UiPreviewScenario scenario) {
        if (scenario.variant() == UiPreviewScenario.Variant.CULLING_DRAFT) {
            return new CullingUiState(true, true, "", CullingUiPhase.NEED_HEIGHT,
                    2, -1, 0, 0, 0, 12, -1, null, null);
        }
        if (scenario.variant() == UiPreviewScenario.Variant.CULLING_SELECTED) {
            return new CullingUiState(true, true, "", CullingUiPhase.IDLE,
                    3, 2, 24, 11, 18, 0, 2,
                    CullingUiDirection.EAST, CullingUiDirection.EAST);
        }
        return new CullingUiState(true, true, "", CullingUiPhase.IDLE,
                0, -1, 0, 0, 0, 0, -1, null, null);
    }
}
