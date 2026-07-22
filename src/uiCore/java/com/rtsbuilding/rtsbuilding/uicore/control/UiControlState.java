package com.rtsbuilding.rtsbuilding.uicore.control;

/**
 * 控件的不可变展示状态。
 *
 * <p>业务面板只提供状态；颜色、图标和 Tooltip 由具体渲染器决定。禁用状态
 * 必须给出原因，以免玩家只看到一个无法解释的灰按钮。</p>
 */
public final class UiControlState {
    private final boolean enabled;
    private final boolean selected;
    private final boolean pending;
    private final boolean failed;
    private final String disabledReason;

    public UiControlState(boolean enabled, boolean selected, boolean pending,
                          boolean failed, String disabledReason) {
        String reason = disabledReason == null ? "" : disabledReason.trim();
        if (!enabled && reason.isEmpty()) {
            throw new IllegalArgumentException("disabled controls require a reason");
        }
        if (enabled && !reason.isEmpty()) {
            throw new IllegalArgumentException("enabled controls cannot have a disabled reason");
        }
        if (pending && failed) {
            throw new IllegalArgumentException("a control cannot be pending and failed together");
        }
        this.enabled = enabled;
        this.selected = selected;
        this.pending = pending;
        this.failed = failed;
        this.disabledReason = reason;
    }

    public static UiControlState enabled() {
        return new UiControlState(true, false, false, false, "");
    }

    public static UiControlState disabled(String reason) {
        return new UiControlState(false, false, false, false, reason);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSelected() {
        return selected;
    }

    public boolean isPending() {
        return pending;
    }

    public boolean isFailed() {
        return failed;
    }

    public String getDisabledReason() {
        return disabledReason;
    }
}
