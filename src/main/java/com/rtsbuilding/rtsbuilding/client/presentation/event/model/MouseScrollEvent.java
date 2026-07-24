package com.rtsbuilding.rtsbuilding.client.presentation.event.model;


public record MouseScrollEvent(double x, double y, double scrollX, double scrollY, boolean consumed) implements InputEvent {
    public MouseScrollEvent(double x, double y, double scrollX, double scrollY) { this(x, y, scrollX, scrollY, false); }
    @Override public InputEvent consume() { return new MouseScrollEvent(x, y, scrollX, scrollY, true); }
}
