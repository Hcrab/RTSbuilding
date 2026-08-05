package com.rtsbuilding.rtsbuilding.platform.interaction;

/** 统一旧版布尔交互结果与新版成功/放行/失败三态。 */
public enum EnumActionResult {
    SUCCESS,
    PASS,
    FAIL;

    public static EnumActionResult fromLegacyBoolean(boolean handled) {
        return handled ? SUCCESS : PASS;
    }
}
