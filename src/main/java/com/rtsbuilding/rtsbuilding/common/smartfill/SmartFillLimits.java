package com.rtsbuilding.rtsbuilding.common.smartfill;

/** 智能填洞的玩家参数范围与服务端保险上限。 */
public final class SmartFillLimits {
    public static final int MIN_BLOCKS = 1;
    public static final int MAX_BLOCKS = 1024;
    public static final int DEFAULT_BLOCKS = 512;
    public static final int MIN_DIAMETER = 3;
    public static final int MAX_DIAMETER = 32;
    public static final int DEFAULT_DIAMETER = 16;
    public static final int HARD_MAX_BLOCKS = 8192;
    public static final int QUERY_BUDGET = 250000;

    private SmartFillLimits() {
    }
}
