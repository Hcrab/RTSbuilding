package com.rtsbuilding.rtsbuilding.platform.interaction;

/**
 * 保留共享交互协议里的主副手字段。1.7.10 实际执行只支持主手；收到副手值时由适配器明确降级。
 */
public enum EnumHand {
    MAIN_HAND,
    OFF_HAND
}
