package com.rtsbuilding.rtsbuilding.common.smartfill;

/**
 * 智能填坑在客户端预览与服务端重规划之间共享的参数契约。
 *
 * <p>这里仅定义产品级软上限和服务端保险边界，不拥有扫描状态或执行逻辑。集中这些值可以避免
 * 客户端滑杆允许的范围与服务端实际接受的范围悄悄漂移。</p>
 */
public final class SmartFillLimits {
    public static final int MIN_BLOCKS = 1;
    public static final int MAX_BLOCKS = 1024;
    public static final int DEFAULT_BLOCKS = 512;
    public static final int MIN_DIAMETER = 3;
    public static final int MAX_DIAMETER = 32;
    public static final int DEFAULT_DIAMETER = 16;
    public static final int HARD_MAX_BLOCKS = 8192;
    public static final int QUERY_BUDGET = 250_000;

    private SmartFillLimits() {
    }
}

