package com.rtsbuilding.rtsbuilding.uikit.popup;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/**
 * 平台无关的弹窗开关与外部点击规则。
 *
 * <p>该模型不绘制弹窗，也不调用业务回调；它只保证打开的弹窗不会把同一次
 * 外部点击泄漏给下面窗口或世界。具体 renderer 负责把 bounds 映射到像素。</p>
 */
public final class UiPopupModel {
    public enum PressDecision {
        PASS,
        INSIDE_CONSUMED,
        OUTSIDE_BLOCKED,
        DISMISSED_AND_BLOCKED
    }

    private UiRect bounds;
    private final boolean modal;
    private final boolean dismissOnOutsidePress;
    private boolean open;

    public UiPopupModel(UiRect bounds, boolean modal, boolean dismissOnOutsidePress) {
        if (bounds == null || bounds.isEmpty()) {
            throw new IllegalArgumentException("popup bounds must be non-empty");
        }
        this.bounds = bounds;
        this.modal = modal;
        this.dismissOnOutsidePress = dismissOnOutsidePress;
    }

    public void open() {
        this.open = true;
    }

    public void close() {
        this.open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public UiRect getBounds() {
        return bounds;
    }

    public void setBounds(UiRect bounds) {
        if (bounds == null || bounds.isEmpty()) {
            throw new IllegalArgumentException("popup bounds must be non-empty");
        }
        this.bounds = bounds;
    }

    public PressDecision press(double x, double y) {
        if (!open) {
            return PressDecision.PASS;
        }
        if (bounds.contains(x, y)) {
            return PressDecision.INSIDE_CONSUMED;
        }
        if (dismissOnOutsidePress) {
            open = false;
            return PressDecision.DISMISSED_AND_BLOCKED;
        }
        return modal ? PressDecision.OUTSIDE_BLOCKED : PressDecision.PASS;
    }

    /** Escape 一次只关闭本弹窗；已关闭时返回 false 让下层继续处理。 */
    public boolean escape() {
        if (!open) {
            return false;
        }
        open = false;
        return true;
    }
}
