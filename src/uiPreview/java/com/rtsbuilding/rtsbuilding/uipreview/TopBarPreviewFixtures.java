package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButton;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButtonId;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiState;

import java.util.ArrayList;
import java.util.List;

/** 顶栏离屏场景只构造生产 Core 状态，不另造预览专用按钮逻辑。 */
final class TopBarPreviewFixtures {
    private TopBarPreviewFixtures() {
    }

    static TopBarUiState forScenario(UiPreviewScenario scenario) {
        UiPreviewScenario.Variant variant = scenario.variant();
        boolean quickOpen = variant == UiPreviewScenario.Variant.QUICK_BUILD_STATES;
        boolean questVisible = variant == UiPreviewScenario.Variant.PLUGIN_MANAGER_STATES;
        boolean rangeOpen = variant == UiPreviewScenario.Variant.WHEEL_WORLD_HANDLE_RESERVE
                || CullingPreviewFixtures.supports(variant);
        boolean guideOpen = false;
        boolean developerVisible = variant == UiPreviewScenario.Variant.DENSE_WORKFLOW_NARROW;
        boolean linked = variant != UiPreviewScenario.Variant.EMPTY_LOADING_FAILED_DISABLED;
        boolean locked = variant == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_WAITING
                || variant == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_READY
                || variant == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_SAVING;
        TopBarUiState.Mode mode = variant == UiPreviewScenario.Variant.TOP_CONTEXT_ACTIONBAR
                ? TopBarUiState.Mode.FUNNEL : TopBarUiState.Mode.INTERACT;
        if (locked) mode = TopBarUiState.Mode.INTERACT;

        List<TopBarUiButton> buttons = new ArrayList<TopBarUiButton>();
        buttons.add(button(TopBarUiButtonId.INTERACT, true, mode == TopBarUiState.Mode.INTERACT));
        buttons.add(button(TopBarUiButtonId.LINK, true, mode == TopBarUiState.Mode.LINK_STORAGE));
        buttons.add(button(TopBarUiButtonId.FUNNEL, true, mode == TopBarUiState.Mode.FUNNEL));
        buttons.add(button(TopBarUiButtonId.ROTATE, true, mode == TopBarUiState.Mode.ROTATE));
        buttons.add(button(TopBarUiButtonId.QUICK_BUILD, true, quickOpen));
        buttons.add(button(TopBarUiButtonId.QUEST_DETECT, questVisible, false));
        buttons.add(button(TopBarUiButtonId.CHUNK_VIEW, true,
                variant == UiPreviewScenario.Variant.JADE_MODES));
        buttons.add(button(TopBarUiButtonId.RANGE_CULLING, true, rangeOpen));
        buttons.add(button(TopBarUiButtonId.GUIDE, true, guideOpen));
        buttons.add(button(TopBarUiButtonId.DEVELOPER, developerVisible, false));
        buttons.add(button(TopBarUiButtonId.GEAR, true, true));
        return new TopBarUiState(buttons, mode, linked,
                scenario.language().startsWith("zh") ? "箱子" : "Chest",
                true, false, quickOpen ? "32 × 18 × 24" : "",
                variant == UiPreviewScenario.Variant.TOP_CONTEXT_ACTIONBAR ? 2 : -1,
                locked);
    }

    private static TopBarUiButton button(TopBarUiButtonId id, boolean visible, boolean active) {
        return new TopBarUiButton(id, visible, visible && active);
    }
}
