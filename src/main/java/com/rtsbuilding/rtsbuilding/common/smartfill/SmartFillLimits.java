package com.rtsbuilding.rtsbuilding.common.smartfill;

/**
 * 智能填坑在客户端预览与服务端重算之间共享的参数契约。
 *
 * <p>这里集中维护玩家可选择的范围与服务端扫描预算；它不负责加载区块、
 * 权限判断或任务提交。</p>
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
