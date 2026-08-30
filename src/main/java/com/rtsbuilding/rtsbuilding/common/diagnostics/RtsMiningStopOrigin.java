package com.rtsbuilding.rtsbuilding.common.diagnostics;

/**
 * 客户端结束“按住挖掘”状态的稳定来源。
 *
 * <p>新增来源只能追加到末尾，避免已发布 v2 网络值改变含义。</p>
 */
public enum RtsMiningStopOrigin {
    NONE,
    POINTER_RELEASE,
    KEY_RELEASE,
    LIFECYCLE_MOUSE_NOT_DOWN,
    LIFECYCLE_KEY_NOT_DOWN,
    SCREEN_CLOSE,
    RTS_DISABLED,
    MODE_SWITCH,
    WINDOW_OPENED,
    PLACEMENT_WHEEL_OPENED,
    NEW_ACTION_REPLACED,
    EXPLICIT_CANCEL,
    CLIENT_RESET;

    public byte wireId() {
        return (byte) ordinal();
    }

    public static RtsMiningStopOrigin fromWire(byte value) {
        int index = Byte.toUnsignedInt(value);
        return index < values().length ? values()[index] : NONE;
    }
}
