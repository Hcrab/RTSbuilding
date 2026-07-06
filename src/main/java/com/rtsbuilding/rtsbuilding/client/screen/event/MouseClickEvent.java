package com.rtsbuilding.rtsbuilding.client.screen.event;

/**
 * 鼠标点击事件——记录点击位置和按键。
 */
public record MouseClickEvent(double x, double y, int button, boolean consumed) implements InputEvent {
    public MouseClickEvent(double x, double y, int button) { this(x, y, button, false); }
    @Override public InputEvent consume() { return new MouseClickEvent(x, y, button, true); }
}
