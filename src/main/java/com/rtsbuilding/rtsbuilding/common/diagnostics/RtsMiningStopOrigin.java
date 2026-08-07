package com.rtsbuilding.rtsbuilding.common.diagnostics;

/** 客户端结束持续挖掘状态的稳定来源；新增值只能追加在末尾。 */
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
