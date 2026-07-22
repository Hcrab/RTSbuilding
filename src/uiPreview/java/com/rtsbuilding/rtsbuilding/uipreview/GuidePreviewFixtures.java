package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiState;

/** 正式指南目录的 preview-only 选页状态。 */
final class GuidePreviewFixtures {
    private GuidePreviewFixtures() {
    }

    static boolean supports(UiPreviewScenario.Variant variant) {
        return variant == UiPreviewScenario.Variant.GUIDE_TOP
                || variant == UiPreviewScenario.Variant.GUIDE_BOTTOM
                || variant == UiPreviewScenario.Variant.GUIDE_SETTINGS;
    }

    static GuideUiState forScenario(UiPreviewScenario scenario) {
        if (scenario.variant() == UiPreviewScenario.Variant.GUIDE_BOTTOM) {
            return new GuideUiState(GuideUiContext.BOTTOM, 4, 0, 0, 7, 8, 11);
        }
        if (scenario.variant() == UiPreviewScenario.Variant.GUIDE_SETTINGS) {
            return new GuideUiState(GuideUiContext.SETTINGS, 1, 0, 0, 7, 8, 11);
        }
        return new GuideUiState(GuideUiContext.TOP, 3, 1, 0, 7, 8, 11);
    }
}
