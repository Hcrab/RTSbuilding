package com.rtsbuilding.rtsbuilding.uicore.control;

/**
 * 控件的不可变展示状态。
 *
 * <p>业务面板只提供状态；颜色、图标和 Tooltip 由具体渲染器决定。禁用状态
 * 必须给出原因，以免玩家只看到一个无法解释的灰按钮。</p>
 */
public final class UiControlState {
    private final boolean visible;
    private final boolean enabled;
    private final boolean hovered;
    private final boolean focused;
    private final boolean pressed;
    private final boolean selected;
    private final boolean pending;
    private final boolean failed;
    private final String disabledReason;

    public UiControlState(boolean enabled, boolean selected, boolean pending,
                          boolean failed, String disabledReason) {
        this(true, enabled, false, false, false, selected, pending, failed, disabledReason);
    }

    /**
     * 完整的控件展示快照。业务状态与瞬时交互状态在同一个值中传给 renderer，
     * 但本类不拥有焦点、指针捕获或动画生命周期。
     */
    public UiControlState(boolean visible, boolean enabled, boolean hovered,
                          boolean focused, boolean pressed, boolean selected,
                          boolean pending, boolean failed, String disabledReason) {
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
        if (!visible && (hovered || focused || pressed)) {
            throw new IllegalArgumentException("hidden controls cannot be hovered, focused or pressed");
        }
        if (!enabled && pressed) {
            throw new IllegalArgumentException("disabled controls cannot be pressed");
        }
        this.visible = visible;
        this.enabled = enabled;
        this.hovered = hovered;
        this.focused = focused;
        this.pressed = pressed;
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

    public static UiControlState hidden() {
        return new UiControlState(false, true, false, false, false,
                false, false, false, "");
    }

    public UiControlState withInteraction(boolean hovered, boolean focused, boolean pressed) {
        return new UiControlState(visible, enabled, hovered, focused, pressed,
                selected, pending, failed, disabledReason);
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isHovered() {
        return hovered;
    }

    public boolean isFocused() {
        return focused;
    }

    public boolean isPressed() {
        return pressed;
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

    /** 与设计文档中的 error 状态同义；保留 isFailed 兼容现有调用。 */
    public boolean isError() {
        return failed;
    }

    public String getDisabledReason() {
        return disabledReason;
    }
}
