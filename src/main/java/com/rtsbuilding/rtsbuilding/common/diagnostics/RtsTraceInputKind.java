package com.rtsbuilding.rtsbuilding.common.diagnostics;

/** 客户端形成 RTS 操作时的输入设备类别。枚举只能在末尾追加，以保持网络值稳定。 */
public enum RtsTraceInputKind {
    UNKNOWN,
    MOUSE,
    KEYBOARD;

    public byte wireId() { return (byte) ordinal(); }

    public static RtsTraceInputKind fromWire(byte value) {
        int index = value & 0xff;
        return index < values().length ? values()[index] : UNKNOWN;
    }
}
