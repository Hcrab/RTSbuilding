package com.rtsbuilding.rtsbuilding.client.screen.event.model;

/**
 * 鼠标拖拽事件。
 */
public record MouseDragEvent(double x, double y, int button, double dx, double dy, boolean consumed) implements InputEvent {
    public MouseDragEvent(double x, double y, int button, double dx, double dy) { this(x, y, button, dx, dy, false); }
    @Override public InputEvent consume() { return new MouseDragEvent(x, y, button, dx, dy, true); }
}
