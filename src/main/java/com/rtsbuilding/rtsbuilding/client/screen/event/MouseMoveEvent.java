package com.rtsbuilding.rtsbuilding.client.screen.event;

/**
 * 鼠标移动事件。
 */
public record MouseMoveEvent(double x, double y, boolean consumed) implements InputEvent {
    public MouseMoveEvent(double x, double y) { this(x, y, false); }
    @Override public InputEvent consume() { return new MouseMoveEvent(x, y, true); }
}
