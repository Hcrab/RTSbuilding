package com.rtsbuilding.rtsbuilding.uicore.event;

/**
 * UI 事件的强语义返回值。
 *
 * <p>它明确区分“本控件处理”“停止 UI 冒泡”“阻断世界”“捕获指针”和
 * “请求键盘焦点”，避免平台层继续猜测一个 boolean 的含义。</p>
 */
public final class UiEventReply {
    public static final UiEventReply PASS = new UiEventReply(false, false, false, false, false);
    public static final UiEventReply CONSUMED = new UiEventReply(true, true, false, false, false);
    public static final UiEventReply CAPTURE_POINTER = new UiEventReply(true, true, true, true, false);
    public static final UiEventReply BLOCK_WORLD = new UiEventReply(true, true, true, false, false);
    public static final UiEventReply FOCUS_TEXT = new UiEventReply(true, true, true, false, true);

    private final boolean handled;
    private final boolean stopUiPropagation;
    private final boolean blockWorld;
    private final boolean capturePointer;
    private final boolean requestKeyboardFocus;

    public UiEventReply(boolean handled, boolean stopUiPropagation, boolean blockWorld,
                        boolean capturePointer, boolean requestKeyboardFocus) {
        if ((capturePointer || requestKeyboardFocus) && !handled) {
            throw new IllegalArgumentException("capture and focus require a handled event");
        }
        this.handled = handled;
        this.stopUiPropagation = stopUiPropagation;
        this.blockWorld = blockWorld;
        this.capturePointer = capturePointer;
        this.requestKeyboardFocus = requestKeyboardFocus;
    }

    public boolean isHandled() {
        return handled;
    }

    public boolean isStopUiPropagation() {
        return stopUiPropagation;
    }

    public boolean isBlockWorld() {
        return blockWorld;
    }

    public boolean isCapturePointer() {
        return capturePointer;
    }

    public boolean isRequestKeyboardFocus() {
        return requestKeyboardFocus;
    }

    /** 将两个处理结果按“只增加约束”的方式合并。 */
    public UiEventReply merge(UiEventReply other) {
        if (other == null) {
            return this;
        }
        return new UiEventReply(
                handled || other.handled,
                stopUiPropagation || other.stopUiPropagation,
                blockWorld || other.blockWorld,
                capturePointer || other.capturePointer,
                requestKeyboardFocus || other.requestKeyboardFocus);
    }
}
