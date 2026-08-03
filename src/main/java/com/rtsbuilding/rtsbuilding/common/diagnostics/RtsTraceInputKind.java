package com.rtsbuilding.rtsbuilding.common.diagnostics;

/** 客户端形成 RTS 操作时的输入设备类别。 */
public enum RtsTraceInputKind {
    UNKNOWN,
    MOUSE,
    KEYBOARD;

    public byte wireId() {
        return (byte) ordinal();
    }

    public static RtsTraceInputKind fromWire(byte value) {
        int index = Byte.toUnsignedInt(value);
        return index < values().length ? values()[index] : UNKNOWN;
    }
}
