package com.rtsbuilding.rtsbuilding.uicore.event;

/** 平台输入适配器写入、Core 路由器读取的鼠标/指针事件。 */
public final class UiPointerEvent {
    public enum Type {
        PRESS,
        MOVE,
        DRAG,
        RELEASE,
        SCROLL
    }

    public static final int NO_BUTTON = -1;

    private final Type type;
    private final double x;
    private final double y;
    private final int button;
    private final double deltaX;
    private final double deltaY;
    private final int modifiers;

    public UiPointerEvent(Type type, double x, double y, int button,
                          double deltaX, double deltaY, int modifiers) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        this.type = type;
        this.x = x;
        this.y = y;
        this.button = button;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.modifiers = modifiers;
    }

    public Type getType() {
        return type;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public int getButton() {
        return button;
    }

    public double getDeltaX() {
        return deltaX;
    }

    public double getDeltaY() {
        return deltaY;
    }

    public int getModifiers() {
        return modifiers;
    }
}
