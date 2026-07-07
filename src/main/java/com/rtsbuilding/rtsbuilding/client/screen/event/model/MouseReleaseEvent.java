package com.rtsbuilding.rtsbuilding.client.screen.event.model;

/**
 * 鼠标释放事件。
 */
public record MouseReleaseEvent(double x, double y, int button, boolean consumed) implements InputEvent {
    public MouseReleaseEvent(double x, double y, int button) { this(x, y, button, false); }
    @Override public InputEvent consume() { return new MouseReleaseEvent(x, y, button, true); }
}
