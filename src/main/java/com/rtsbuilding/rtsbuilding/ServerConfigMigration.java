package com.rtsbuilding.rtsbuilding;

/**
 * 服务器配置默认值迁移的纯值逻辑。
 *
 * <p>本类只识别曾经公开发布过的默认值，不读取 TOML，也不保存配置。玩家已经主动设置的
 * 其他数值必须原样保留；实际写回由 {@link Config} 在 NeoForge 完成配置加载后负责。</p>
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
