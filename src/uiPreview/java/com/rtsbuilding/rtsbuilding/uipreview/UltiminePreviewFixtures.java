package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiPhase;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiState;

/** 连锁破坏世界交互边界的 preview-only 正式 Core 快照。 */
final class UltiminePreviewFixtures {
    private UltiminePreviewFixtures() {}

    static boolean supports(UiPreviewScenario.Variant variant) {
        return variant == UiPreviewScenario.Variant.QUICK_BUILD_DESTROY_CHAIN
                || variant == UiPreviewScenario.Variant.QUICK_BUILD_PROGRESS;
    }

    static UltimineUiState forScenario(UiPreviewScenario scenario) {
        boolean running = scenario.variant() == UiPreviewScenario.Variant.QUICK_BUILD_PROGRESS;
        return new UltimineUiState(true, "",
                running ? UltimineUiPhase.RUNNING : UltimineUiPhase.PREVIEW,
                true, running ? 88 : 96, running ? 160 : 0,
                running ? 160 : 96, 1, 512,
                running ? 72 : -1, running ? 160 : 0);
    }
}
