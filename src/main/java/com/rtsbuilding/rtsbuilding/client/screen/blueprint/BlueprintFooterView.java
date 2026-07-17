package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

/**
 * 蓝图窗口 footer 的纯状态投影。
 *
 * <p>它只决定当前阶段唯一的主要动作、次要动作和是否等待，不直接调用
 * BlueprintPanel。这样旧版本可以复用状态机，同时保留各自的渲染 API。</p>
 */
public record BlueprintFooterView(
        Stage stage,
        Action primaryAction,
        boolean primaryEnabled,
        boolean primaryPending,
        Action secondaryAction,
        boolean secondaryEnabled) {

    public enum Stage {
        CAPTURE_EMPTY,
        CAPTURE_READY,
        CAPTURE_SUBMITTING,
        FOLLOW_PREVIEW,
        PINNED_PREVIEW
    }

    public enum Action {
        SAVE_CAPTURE,
        CANCEL_CAPTURE,
        BUILD_PREVIEW,
        CANCEL_PREVIEW
    }

    public static BlueprintFooterView capture(boolean complete, boolean saving) {
        Stage stage = saving
                ? Stage.CAPTURE_SUBMITTING
                : complete ? Stage.CAPTURE_READY : Stage.CAPTURE_EMPTY;
        return new BlueprintFooterView(
                stage,
                Action.SAVE_CAPTURE,
                complete && !saving,
                saving,
                Action.CANCEL_CAPTURE,
                !saving);
    }

    public static BlueprintFooterView placement(boolean pinned) {
        return new BlueprintFooterView(
                pinned ? Stage.PINNED_PREVIEW : Stage.FOLLOW_PREVIEW,
                Action.BUILD_PREVIEW,
                pinned,
                false,
                Action.CANCEL_PREVIEW,
                true);
    }
}
