package com.rtsbuilding.rtsbuilding;

/**
 * 服务端配置默认值迁移的纯值逻辑。
 *
 * <p>这里只识别公开版本曾经使用过的默认值。玩家或服主主动设置的其他数值必须原样保留，
 * 实际配置读写仍由 {@link Config} 负责。</p>
 */
final class ServerConfigMigration {
    static final int CURRENT_REVISION = 1;
    static final int CURRENT_MINING_SLICE = 32;
    static final long CURRENT_TASK_BUDGET_NANOS = 8_000_000L;

    private ServerConfigMigration() {
    }

    static Values migrate(int revision, int miningSlice, long taskBudgetNanos) {
        if (revision >= CURRENT_REVISION) {
            return new Values(revision, miningSlice, taskBudgetNanos);
        }
        int migratedSlice = miningSlice == 8 || miningSlice == 16
                ? CURRENT_MINING_SLICE
                : miningSlice;
        long migratedBudget = taskBudgetNanos == 4_000_000L
                ? CURRENT_TASK_BUDGET_NANOS
                : taskBudgetNanos;
        return new Values(CURRENT_REVISION, migratedSlice, migratedBudget);
    }

    record Values(int revision, int miningSlice, long taskBudgetNanos) {
    }
}
